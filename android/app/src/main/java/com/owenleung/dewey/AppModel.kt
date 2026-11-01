package com.owenleung.dewey

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.owenleung.dewey.engine.Assignment
import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.engine.CourseGrade
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.EngineError
import com.owenleung.dewey.engine.EngineJson
import com.owenleung.dewey.engine.PlanResult
import com.owenleung.dewey.engine.QuizInfo
import com.owenleung.dewey.engine.SubmissionStatus
import com.owenleung.dewey.engine.TodoLists
import com.owenleung.dewey.engine.AccountInfo
import com.owenleung.dewey.neoplan.NeoPlanClient
import com.owenleung.dewey.neoplan.NeoPlanError
import com.owenleung.dewey.store.CalendarSync
import com.owenleung.dewey.store.PlanLogic
import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.SerialName
import com.owenleung.dewey.store.Cache
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.GradesLook
import com.owenleung.dewey.store.Inbox
import com.owenleung.dewey.store.ItemState
import com.owenleung.dewey.store.ItemStore
import com.owenleung.dewey.store.ItemType
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.store.Reminders
import com.owenleung.dewey.store.TodoLogic
import com.owenleung.dewey.web.WebConfig
import com.owenleung.dewey.widget.WidgetBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import java.util.Date

/**
 * App-wide state: the Schoology session, the engine, the class list, and the
 * To Do merge (Schoology's lists + the student's own saved list). One per
 * process (ios/App/AppModel.swift); the UI, widgets and background refresh share it.
 */
@SuppressLint("StaticFieldLeak") // holds the application context only
class AppModel internal constructor(val context: Context, /** The tour's pretend week (Practice): in memory, answered locally, no side effects. */ val practice: Boolean = false) {
    enum class Session { CHECKING, SIGNED_OUT, SIGNED_IN }

    companion object {
        @Volatile private var instance: AppModel? = null

        /** The process's model. First call must be on the main thread (it builds a WebView later). */
        fun get(ctx: Context): AppModel = instance ?: synchronized(this) {
            instance ?: run {
                check(Looper.myLooper() == Looper.getMainLooper()) { "AppModel.get first call off the main thread" }
                DeweyApp.setUp(ctx.applicationContext)
                AppModel(ctx.applicationContext).also { instance = it }
            }
        }

        /** The tour's pretend week: a second model, in memory, its engine answered by Practice. Call Practice.begin() first. */
        fun practice(ctx: Context): AppModel = AppModel(ctx.applicationContext, practice = true)

        /** "New" wears off after a week even if never opened. */
        const val NEW_GRADE_DAYS = 7.0
        /** Rows kept this many days after they're gone (prune). */
        const val KEEP_DAYS = 120L

        fun neoPlanProblemText(e: Throwable): String = when {
            e is NeoPlanError.Refused && e.status == 401 -> "Token refused"
            e is NeoPlanError.Refused -> "neo-plan refused a change"
            else -> "Can’t reach neo-plan"
        }

        fun isNew(e: GradeLogEntry, now: Date = Date()): Boolean {
            if (e.seen) return false
            val at = e.at ?: return false
            return now.time - at < NEW_GRADE_DAYS * 86_400_000
        }

        /** Returns the scores that are new (changed since `old`) and logs them unseen. */
        fun logGrades(log: MutableMap<String, GradeLogEntry>, old: List<CourseGrade.Item>?, new: List<CourseGrade.Item>,
                      now: Date = Date()): List<CourseGrade.Item> {
            val before = (old ?: emptyList()).associate { it.id to it.earned }
            val fresh = mutableListOf<CourseGrade.Item>()
            for (it in new) {
                if (it.earned == null) continue
                val e = log[it.id]
                if (e != null && e.earned == it.earned) continue
                val changed = old != null && before[it.id] != it.earned
                log[it.id] = GradeLogEntry(it.earned, if (changed) now.time else log[it.id]?.at, !changed)
                if (changed) fresh += it
            }
            return fresh
        }
    }

    /** When each score first appeared (null = already there before the app was watching) and whether it was seen. */
    @Serializable
    data class GradeLogEntry(val earned: Double? = null, val at: Long? = null, val seen: Boolean = false)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** The student's marks: the real file, or memory only for the tour's pretend week. */
    val store: ItemStore = if (practice) ItemStore.inMemory() else ItemStore.real
    val engine: Engine = Engine(context, practice = practice)
    val inbox = Inbox()

    var session by mutableStateOf(Session.CHECKING)
        private set

    private fun moveTo(s: Session) {
        val was = session
        session = s
        // The widgets say "Open Dewey to sign in" while signed out.
        if (s != was && s != Session.CHECKING && !practice) WidgetBridge.publish(this)
    }

    var courses by mutableStateOf<List<Course>>(emptyList())
    var coursesAsOf by mutableStateOf<Date?>(null)

    var todoAsOf by mutableStateOf<Date?>(null)
    var todoError by mutableStateOf<EngineError?>(null)
    var refreshingTodo by mutableStateOf(false)
    /** Bumped whenever ItemState rows change, so lists re-read. */
    val revision = mutableIntStateOf(0)
    /** Set by a widget, notification or shortcut; MainScreen opens it. */
    var deepLink by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)

    /** Course grades, by section (cached ≤ 30 min). */
    val grades = mutableStateMapOf<String, CourseGrade>()
    val gradesAsOf = mutableStateMapOf<String, Date>()
    val gradeErrors = mutableStateMapOf<String, EngineError>()
    val gradeLog = mutableStateMapOf<String, GradeLogEntry>()

    private val logSerializer = MapSerializer(String.serializer(), GradeLogEntry.serializer())

    init {
        Cache.load("courses", ListSerializer(Course.serializer()))?.let { courses = it.first; coursesAsOf = it.second }
        todoAsOf = Cache.loadDate("todoAsOf")
        Cache.load("gradeLog", logSerializer)?.let { gradeLog.putAll(it.first) }
        engine.onSignedOut = {
            if (!practice && session != Session.SIGNED_OUT) {
                moveTo(Session.SIGNED_OUT)
                Reminders.sessionDidEnd(notify = true)
            }
        }
    }

    // ── Session ──────────────────────────────────────────────────────────

    private var booted = false

    /** On launch: try the course list; `login` → Sign in, anything else → in (offline uses saved copies). */
    suspend fun boot() {
        if (booted && session != Session.CHECKING) return
        booted = true
        if (practice) {
            runCatching { loadCourses() }
            moveTo(Session.SIGNED_IN)
            return
        }
        engine.start()
        // Saved copies and no sign-out on record: show them now instead of a
        // spinner; a `login` below still sends the app to Sign in.
        if (session == Session.CHECKING && courses.isNotEmpty() && !Reminders.sessionEnded) moveTo(Session.SIGNED_IN)
        try {
            loadCourses()
            moveTo(Session.SIGNED_IN)
            Reminders.sessionDidStart()
            WebConfig.keepSession()
        } catch (e: EngineError.Login) {
            moveTo(Session.SIGNED_OUT)
            Reminders.sessionDidEnd(notify = true)
        } catch (e: Exception) {
            moveTo(if (courses.isEmpty() && todoAsOf == null) Session.SIGNED_OUT else Session.SIGNED_IN)
        }
    }

    /** The sign-in web view reached a signed-in Schoology page. */
    suspend fun signedIn() {
        WebConfig.keepSession()
        engine.reload()
        moveTo(Session.SIGNED_IN)
        Reminders.sessionDidStart()
        if (Reminders.signedOutAlert || Reminders.gradeAlerts) Reminders.requestPermission()
        runCatching { loadCourses() }
        forgetOtherAccount()
        refreshTodo()
    }

    /** A different student signed in on this phone: the last one's rows and marks go. */
    suspend fun forgetOtherAccount() {
        if (practice) return
        val a: AccountInfo = runCatching { engine.call<AccountInfo>("account") }.getOrNull() ?: return
        val user = a.username?.takeIf { it.isNotEmpty() } ?: return
        val key = "signedInUser"
        val was = Prefs.string(key)
        Prefs.set(key, user)
        if (was == null || was == user) return
        store.clearAll()
        pendingPush.clear()
        savePending()
        save()
    }

    suspend fun signOut() {
        WebConfig.signOut()
        Cache.clear()
        inbox.clear()
        grades.clear()
        gradesAsOf.clear()
        gradeLog.clear()
        courses = emptyList()
        moveTo(Session.SIGNED_OUT)
        Reminders.sessionDidEnd(notify = false)
        engine.reload()
    }

    // ── Courses ──────────────────────────────────────────────────────────

    suspend fun loadCourses() {
        val list: List<Course> = engine.call("courses")
        courses = list
        coursesAsOf = Date()
        Cache.save("courses", ListSerializer(Course.serializer()), list)
    }

    val classes: List<Course>
        get() {
            Prefs.revision.intValue  // a course switched between class and group (Settings)
            return courses.filter { it.isClass }.ifEmpty { courses }
        }

    /** Class or group for one course (Settings); fetches a new group's updates. */
    fun setKind(c: Course, isClass: Boolean) {
        com.owenleung.dewey.store.CourseKinds.set(c, isClass)
        revision.intValue++
        if (!isClass) scope.launch { inbox.refresh(this@AppModel) }
    }

    fun course(sectionId: String?): Course? = sectionId?.let { s -> courses.firstOrNull { it.sectionId == s } }

    fun cutoff(sectionId: String): Double = store.cutoff(sectionId) ?: 93.0

    // ── To Do ────────────────────────────────────────────────────────────

    fun item(id: String): ItemState? {
        revision.intValue
        return store.item(id)
    }

    /** The To Do list: visible items (TodoLogic.isVisible). */
    fun todoItems(): List<ItemState> {
        revision.intValue
        val now = Date()
        return store.all().filter { TodoLogic.isVisible(it, now) }
    }

    /** Open (not finished) overdue work: the tab badge. */
    fun overdueCount(): Int {
        val now = Date()
        return todoItems().count { isOverdue(it, now) }
    }

    /** Past due, not finished, and not old enough to sit in the Older group. */
    fun isOverdue(it: ItemState, now: Date = Date()) = (it.due?.before(now) ?: false) && showsLate(it) && !TodoLogic.isOlder(it.due, now)

    /** A class's work still to do: late work (as on To Do), then everything due later that isn't finished, soonest first. Past tests and Older work are left out. */
    fun openWork(sectionId: String, now: Date = Date()): List<ItemState> =
        todoItems()
            .filter { it.sectionId == sectionId }
            .filter { if (it.due?.before(now) == true) isOverdue(it, now) else !isFinished(it) }
            .sortedBy { it.dueAt ?: Long.MAX_VALUE }

    /** Past due counts as late (TodoLogic.showsLate). */
    fun showsLate(it: ItemState) = TodoLogic.showsLate(it.type, it.isQuiz, if (it.isExam) it.studied else it.done, it.turnedIn, it.submitted, it.inSchoology)

    fun isFinished(it: ItemState) = it.submitted || it.turnedIn || (if (it.isExam) false else it.done)

    /**
     * Pull Schoology's lists, merge, then check a few dropboxes and neo-plan.
     * `quick`: return once the list is merged (pull to refresh) and let the checks fill in after.
     */
    suspend fun refreshTodo(quick: Boolean = false) {
        if (refreshingTodo) return
        refreshingTodo = true
        try {
            if (courses.isEmpty()) runCatching { loadCourses() }
            val lists: TodoLists = engine.call("todo")
            merge(lists)
            todoError = null
            todoAsOf = Date().also { Cache.saveDate("todoAsOf", it) }
        } catch (e: EngineError) {
            todoError = e
        } catch (e: Exception) {
            todoError = EngineError.Js(e.message ?: "$e")
        } finally {
            refreshingTodo = false
        }
        revision.intValue++
        publishTodo()
        if (todoError != null) return
        if (quick) scope.launch { checkAfterRefresh() } else checkAfterRefresh()
    }

    private var checking = false

    /** Dropbox checks and neo-plan, after a refresh merged Schoology's lists. */
    private suspend fun checkAfterRefresh() {
        if (checking) return
        checking = true
        try {
            checkStatuses()
            syncNeoPlan()
        } finally {
            checking = false
        }
        revision.intValue++
        publishTodo()
    }

    private fun publishTodo() {
        WidgetBridge.publish(this)
        Reminders.schedule(todoItems(), courses)
        CalendarSync.schedule(this)
    }

    fun merge(lists: TodoLists, now: Date = Date()) {
        val seen = HashSet<String>()
        for (e in lists.overdue + lists.upcoming) {
            seen += e.schoologyId
            val it = store.getOrCreate(e.schoologyId, e.title)
            it.title = e.title
            it.sectionId = e.sectionId ?: it.sectionId
            it.courseName = e.course ?: it.courseName
            it.due = Fmt.date(e.due) ?: it.due
            it.url = e.url ?: it.url
            it.graded = e.graded ?: false
            it.inSchoology = true
            it.lastSeen = now.time
        }
        // The student's own tasks are never on Schoology's lists.
        for (it in store.all()) if (it.schoologyId !in seen && !it.mine) it.inSchoology = false
        prune(now)
        store.save()
    }

    /**
     * Rows nobody will see again: Schoology work it stopped listing months ago, and the
     * student's own tasks finished months ago. Keeps the saved list from growing all year.
     */
    private fun prune(now: Date) {
        val cutoff = now.time - KEEP_DAYS * 86_400_000L
        for (it in store.all()) {
            val old = if (it.mine) (it.done || it.turnedIn || it.dismissed) && it.updatedAt < cutoff
                      else !it.inSchoology && it.lastSeen < cutoff
            if (old) store.remove(it.schoologyId)
        }
    }

    /** status({id}) for up to 10 rows that might have a dropbox, not checked in 30 min. A quiz is checked on its landing page instead, until it says submitted. */
    suspend fun checkStatuses(cap: Int = 10) {
        val cutoff = System.currentTimeMillis() - 30 * 60_000
        val candidates = todoItems()
            .filter { !it.mine && !it.external && it.statusState != "no_dropbox" && (it.statusCheckedAt ?: 0) < cutoff }
            .filter { !it.isQuiz || !it.submitted }
            .sortedBy { it.statusCheckedAt ?: 0 }
            .take(cap)
        twoAtATime(candidates) { checkStatus(it) }
    }

    /** Runs `body` over `list`, two at a time: the engine lets 2 requests run at once. */
    suspend fun <T> twoAtATime(list: List<T>, body: suspend (T) -> Unit) = coroutineScope {
        val gate = Semaphore(2)
        list.map { x -> launch { gate.withPermit { body(x) } } }.joinAll()
    }

    suspend fun checkStatus(it: ItemState): SubmissionStatus? {
        if (it.isQuiz) { checkQuiz(it); return null }
        val s: SubmissionStatus = try { engine.call("status", mapOf("id" to it.schoologyId)) } catch (e: Exception) { return null }
        val was = it.statusState
        it.statusState = if (s.quiz == true) "unknown" else s.state
        it.statusLate = s.late
        it.statusCheckedAt = System.currentTimeMillis()
        if (s.state == "submitted" && was != "submitted") it.neoPlanId?.let { id -> runCatching { NeoPlanClient.turnIn(id) } }
        store.save()
        revision.intValue++
        WidgetBridge.publish(this)
        return s
    }

    /** A quiz's own status op is refused (it redirects), but its landing page says whether an attempt was handed in. Taken = submitted, so it isn't late. */
    suspend fun checkQuiz(it: ItemState) {
        val a: Assignment = try { engine.call("assignment", mapOf("id" to it.schoologyId)) } catch (e: Exception) { return }
        it.statusCheckedAt = System.currentTimeMillis()
        a.quiz?.let { q -> noteQuiz(it, q) }
        store.save()
    }

    /** Records a quiz landing page's state on the row (submitted, or still to take). */
    fun noteQuiz(it: ItemState, q: QuizInfo) {
        val state = if (q.state == "submitted") "submitted" else "unknown"
        if (state == it.statusState) return
        val was = it.statusState
        it.statusState = state
        if (state == "submitted" && was != "submitted") it.neoPlanId?.let { id -> scope.launch { runCatching { NeoPlanClient.turnIn(id) } } }
        revision.intValue++
        WidgetBridge.publish(this)
    }

    /** After the Schoology sheet closes: if Schoology now says submitted, say so and clear the row. */
    fun recheckAfterSheet(id: String) {
        scope.launch {
            val it = store.item(id) ?: run { revision.intValue++; return@launch }
            val was = it.statusState
            it.statusCheckedAt = null
            val s = checkStatus(it)
            if (s?.state == "submitted" && was != "submitted") {
                showToast(if (NeoPlanClient.connected) "Submitted on Schoology · Cleared from To Do and neo-plan" else "Submitted on Schoology · Cleared from To Do")
            }
        }
    }

    // ── The student's marks ──────────────────────────────────────────────

    enum class Progress(val label: String) { TODO("To do"), DONE("Done"), TURNED_IN("Turned in") }

    fun progress(it: ItemState): Progress =
        if (it.turnedIn) Progress.TURNED_IN else if (if (it.isExam) it.studied else it.done) Progress.DONE else Progress.TODO

    fun toggleDone(it: ItemState) {
        if (it.isExam) it.studied = !it.studied else it.done = !it.done
        it.touch()
        save()
        pushNeoPlan(it, NeoPlanOp.Work(if (it.isExam) it.studied else it.done))
    }

    fun setTurnedIn(it: ItemState, on: Boolean) {
        it.turnedIn = on
        if (on) it.done = true
        it.touch()
        save()
        pushNeoPlan(it, NeoPlanOp.TurnedIn(on))
    }

    /** One switch for the student's own state: to do → done (worked on) → turned in (handed in). */
    fun setProgress(it: ItemState, p: Progress) {
        val was = progress(it)
        if (p == was) return
        if (was == Progress.TURNED_IN) setTurnedIn(it, false)
        val ready = p != Progress.TODO
        if ((if (it.isExam) it.studied else it.done) != ready) toggleDone(it)
        if (p == Progress.TURNED_IN) setTurnedIn(it, true)
    }

    fun setType(it: ItemState, t: ItemType) {
        it.type = t
        it.touch()
        save()
        pushNeoPlan(it, NeoPlanOp.Type(t.raw))
    }

    fun dismiss(it: ItemState) {
        it.dismissed = true
        it.touch()
        save()
    }

    fun save() {
        store.save()
        revision.intValue++
        publishTodo()
    }

    // ── The student's own tasks ──────────────────────────────────────────

    /** Adds a task of the student's own (TaskEditor, quick add). Never sent anywhere. */
    fun addTask(title: String, type: ItemType = ItemType.TASK, sectionId: String? = null, due: Date? = null, notes: String? = null): ItemState {
        val it = ItemState.mine(title)
        it.typeOverride = type.raw
        it.sectionId = sectionId
        it.courseName = course(sectionId)?.shortTitle
        it.due = due
        it.notes = notes
        store.insert(it)
        save()
        return it
    }

    fun updateTask(it: ItemState, title: String, type: ItemType, sectionId: String?, due: Date?, notes: String?) {
        it.title = title
        it.typeOverride = type.raw
        it.sectionId = sectionId
        it.courseName = course(sectionId)?.shortTitle
        it.due = due
        it.notes = notes
        it.touch()
        save()
    }

    fun deleteTask(it: ItemState) {
        if (!it.mine) return
        store.remove(it.schoologyId)
        save()
    }

    // ── Plan ─────────────────────────────────────────────────────────────

    data class PlanLists(val todo: List<ItemState>, val later: List<ItemState>, /** Finished today. */ val done: List<ItemState>) {
        val rows: List<PlanLogic.Row> get() = PlanLogic.rows(todo.map { it.schoologyId }, later.map { it.schoologyId }, done.map { it.schoologyId })
    }

    /** For a plan row: finished, as Plan counts it (a test you studied for is done). */
    fun planFinished(it: ItemState) = isFinished(it) || (it.isExam && it.studied)

    /**
     * Work finished while Plan is open (a tick, a swipe, Submit): it stays where it
     * was, ticked, instead of dropping into Done at the bottom, until Plan closes.
     */
    val planKeep = mutableStateListOf<String>()

    /** Every open item in To do or Later (PlanLogic), plus what was finished today. */
    fun planLists(now: Date = Date()): PlanLists {
        val todo = mutableListOf<ItemState>(); val later = mutableListOf<ItemState>(); val done = mutableListOf<ItemState>()
        for (it in todoItems()) {
            val kept = it.schoologyId in planKeep
            if (planFinished(it) && !kept) {
                if (Fmt.sameDay(Date(it.updatedAt), now)) done += it
                continue
            }
            val late = isOverdue(it, now)
            if (!kept) {
                if (TodoLogic.isOlder(it.due, now)) continue
                // A past test that isn't an online quiz isn't work any more (TodoLogic.showsLate).
                if ((it.due?.before(now) == true) && !late && !it.mine) continue
            }
            val auto = PlanLogic.autoBucket(it.due, late, it.isExam, it.mine, now)
            val b = PlanLogic.Bucket.of(it.planSection) ?: auto
            if (b == PlanLogic.Bucket.LATER) later += it else todo += it
        }
        fun ordered(list: List<ItemState>): List<ItemState> {
            val byId = list.associateBy { it.schoologyId }
            return PlanLogic.order(list.map { PlanLogic.Entry(it.schoologyId, it.planRank, it.dueAt) }).mapNotNull { byId[it.id] }
        }
        return PlanLists(ordered(todo), ordered(later), done.sortedBy { it.updatedAt })
    }

    /**
     * Saves a drag (PlanLogic.place): every item's place, and for the item that
     * changed section, that section. Into Done = done; out of Done = to do again.
     */
    fun applyPlan(placed: Map<String, PlanLogic.Place>, moved: String?, now: Date = Date()) {
        val lists = planLists(now)
        val was = lists.todo.associate { it.schoologyId to PlanLogic.Bucket.TODO } + lists.later.associate { it.schoologyId to PlanLogic.Bucket.LATER } +
            lists.done.associate { it.schoologyId to PlanLogic.Bucket.DONE }
        for (it in lists.todo + lists.later + lists.done) {
            val p = placed[it.schoologyId] ?: continue
            if (p.bucket != PlanLogic.Bucket.DONE) it.planRank = p.rank
            val from = was[it.schoologyId]
            if (it.schoologyId != moved || from == null || from == p.bucket) continue
            when (p.bucket) {
                PlanLogic.Bucket.DONE -> {
                    planKeep.remove(it.schoologyId)
                    setProgress(it, Progress.DONE)
                }
                else -> {
                    if (from == PlanLogic.Bucket.DONE) setProgress(it, Progress.TODO)
                    // By due date it would be here anyway: leave it to follow its due date.
                    val auto = PlanLogic.autoBucket(it.due, isOverdue(it, now), it.isExam, it.mine, now)
                    it.planSection = if (p.bucket == auto) null else p.bucket.raw
                }
            }
        }
        save()
    }

    // ── neo-plan ─────────────────────────────────────────────────────────

    /** One change to send to neo-plan. */
    @Serializable
    sealed class NeoPlanOp {
        abstract val kind: Int
        @Serializable @SerialName("work") data class Work(val ready: Boolean) : NeoPlanOp() { override val kind get() = 0 }
        @Serializable @SerialName("turnedIn") data class TurnedIn(val on: Boolean) : NeoPlanOp() { override val kind get() = 1 }
        @Serializable @SerialName("type") data class Type(@SerialName("value") val type: String) : NeoPlanOp() { override val kind get() = 2 }

        suspend fun run(id: String) {
            when (this) {
                is Work -> NeoPlanClient.setWork(id, ready)
                is TurnedIn -> if (on) NeoPlanClient.turnIn(id) else NeoPlanClient.putBack(id)
                is Type -> ItemType.of(type)?.let { NeoPlanClient.setType(id, it) }
            }
        }
    }

    private val pendingSerializer = MapSerializer(String.serializer(), ListSerializer(NeoPlanOp.serializer()))

    /** neo-plan writes that failed (offline), by Schoology id, the newest of each kind; saved, so a quit doesn't lose them, and retried on refresh. */
    private val pendingPush: MutableMap<String, List<NeoPlanOp>> =
        (if (practice) null else Cache.load("neoplanPending", pendingSerializer)?.first)?.toMutableMap() ?: mutableMapOf()

    private fun savePending() { if (!practice) Cache.save("neoplanPending", pendingSerializer, pendingPush.toMap()) }

    private fun queueNeoPlan(sid: String, op: NeoPlanOp) {
        pendingPush[sid] = (pendingPush[sid] ?: emptyList()).filter { it.kind != op.kind } + op
        savePending()
    }

    private fun pushNeoPlan(it: ItemState, op: NeoPlanOp) {
        // neo-plan's extension API only takes Schoology items: the student's own tasks stay here.
        if (!NeoPlanClient.connected || it.mine) return
        val sid = it.schoologyId
        // Behind earlier failed writes: keep the order.
        if (pendingPush[sid] != null) { queueNeoPlan(sid, op); return }
        scope.launch {
            val id = neoPlanId(sid) ?: run { queueNeoPlan(sid, op); return@launch }
            try { op.run(id) } catch (e: Exception) { queueNeoPlan(sid, op) }
        }
    }

    private suspend fun neoPlanId(sid: String): String? {
        val item = store.item(sid) ?: return null
        item.neoPlanId?.let { return it }
        runCatching { NeoPlanClient.items(listOf(sid)).firstOrNull()?.id }.getOrNull()?.let { found -> item.neoPlanId = found; return found }
        runCatching {
            NeoPlanClient.addItem(sid, item.sectionId, item.title, item.due, if (item.isQuiz) ItemType.EXAM else null)
        }.getOrNull()?.let { added -> item.neoPlanId = added.id; return added.id }
        return null
    }

    /** What went wrong in the last neo-plan sync, for Settings → neo-plan; null when all went through. */
    var neoPlanProblem by mutableStateOf<String?>(null)

    /**
     * neo-plan is the shared copy: the newer edit wins. Tells it the courses
     * (so it files work by class, and a new account gets a column per class),
     * adds new work it doesn't have, then pulls its state for every listed item.
     */
    suspend fun syncNeoPlan(byHand: Boolean = false) {
        if (!NeoPlanClient.connected) return
        var problem: String? = null
        val items = todoItems().filter { !it.mine }
        // Not on every return to the app: every 10 minutes, or when asked.
        val enrichedAt = Cache.loadDate("neoplanEnrichedAt")?.time ?: 0
        if (courses.isNotEmpty() && (byHand || System.currentTimeMillis() - enrichedAt > 10 * 60_000)) {
            val work = items.filter { it.isSchoologyAssignment }.mapNotNull { it.sectionId?.let { s -> it.schoologyId to s } }
            try {
                NeoPlanClient.enrich(courses, work, byHand)
                Cache.saveDate("neoplanEnrichedAt", Date())
            } catch (e: Exception) { problem = neoPlanProblemText(e) }
        }
        for ((sid, ops) in pendingPush.toMap()) {
            val id = neoPlanId(sid) ?: continue
            val left = ops.toMutableList()
            while (left.isNotEmpty() && runCatching { left.first().run(id) }.isSuccess) left.removeAt(0)
            if (left.isEmpty()) pendingPush.remove(sid) else pendingPush[sid] = left
        }
        savePending()
        val got = try { NeoPlanClient.items(items.map { it.schoologyId }) } catch (e: Exception) {
            neoPlanProblem = neoPlanProblemText(e)
            return
        }
        val (added, addProblem) = addNewWork(items, got.mapNotNull { it.sourceId }.toSet())
        neoPlanProblem = addProblem ?: problem
        val bySource = got.filter { it.sourceId != null }.associateBy { it.sourceId!! }
        val lastSync = Cache.loadDate("neoplanSyncedAt")?.time ?: 0
        for (it in items) {
            if (it.schoologyId in added) continue
            val g = bySource[it.schoologyId] ?: continue
            it.neoPlanId = g.id
            // Local edits since the last sync win; otherwise neo-plan's state.
            if (it.updatedAt > lastSync && !pendingPush.containsKey(it.schoologyId)) continue
            g.work?.let { w -> if (it.isExam) it.studied = w == "ready" else it.done = w == "ready" }
            if (g.cleared == true && !it.submitted) it.turnedIn = true
            ItemType.of(g.type)?.let { t -> it.typeOverride = t.raw }
            if (g.removed == true) it.dismissed = true
        }
        Cache.saveDate("neoplanSyncedAt", Date())
        save()
    }

    /**
     * Open Schoology work neo-plan has no item for goes onto the board, as the
     * extension's sync does (sync.js step 5b): not overdue by more than two
     * weeks, at most 25 a sync, and once only (an item it removed comes back
     * in `have`). What the student already marked here goes with it. Returns
     * the ids added, which the pull leaves alone, and a problem to show.
     */
    private suspend fun addNewWork(items: List<ItemState>, have: Set<String>): Pair<Set<String>, String?> {
        val cutoff = System.currentTimeMillis() - 14 * 86_400_000L
        val new = items.filter {
            it.isSchoologyAssignment && it.neoPlanId == null && it.schoologyId !in have &&
                !it.submitted && !it.dismissed && (it.dueAt?.let { d -> d >= cutoff } ?: true)
        }
        val added = HashSet<String>()
        var noColumn = 0
        var problem: String? = null
        for (it in new.take(25)) {
            try {
                val item = NeoPlanClient.addItem(it.schoologyId, it.sectionId, it.title, it.due, if (it.isQuiz) ItemType.EXAM else null) ?: continue
                it.neoPlanId = item.id
                added += it.schoologyId
                if (if (it.isExam) it.studied else it.done) runCatching { NeoPlanClient.setWork(item.id, true) }
                if (it.turnedIn) runCatching { NeoPlanClient.turnIn(item.id) }
            } catch (e: com.owenleung.dewey.neoplan.NeoPlanError.Refused) {
                if (e.status == 409 && e.word == "no_column") noColumn += 1 else problem = problem ?: neoPlanProblemText(e)
            } catch (e: Exception) {
                problem = problem ?: neoPlanProblemText(e)
            }
        }
        if (noColumn > 0) problem = "$noColumn not added: no column for its class"
        return added to problem
    }

    // ── Grades ───────────────────────────────────────────────────────────

    /** The grade the student aims for in a class (the Goal chip): saved, and the A line until they pick one. */
    fun goal(sectionId: String): Double {
        revision.intValue
        return store.goal(sectionId) ?: cutoff(sectionId)
    }

    fun setGoal(sectionId: String, pct: Double) {
        store.setGoal(sectionId, pct)
        revision.intValue++
    }

    fun gradeRows(sectionId: String): String? = Cache.loadRaw("grades-$sectionId")?.first

    /** The course's gradebook (cached ≤ 30 min unless `force`) → courseGrade. */
    suspend fun loadGrades(sectionId: String, force: Boolean = false) {
        if (grades[sectionId] == null) {
            Cache.loadRaw("grades-$sectionId")?.let { (raw, at) ->
                gradesAsOf[sectionId] = at
                // The saved summary shows at once; working it out again waits for the engine page.
                val saved = Cache.load(summaryKey(sectionId), CourseGrade.serializer())
                if (saved != null && saved.second.time >= at.time - 5_000) grades[sectionId] = saved.first
                else runCatching { summarize(raw) }.getOrNull()?.let { grades[sectionId] = it }
                    // Offline, or the engine page won't load: an older build's summary beats nothing.
                    ?: Cache.load(anySummaryKey(sectionId), CourseGrade.serializer())?.let { grades[sectionId] = it.first }
            }
        }
        val at = gradesAsOf[sectionId]
        if (!force && at != null && Date().time - at.time < 30 * 60_000 && grades[sectionId] != null) return
        try {
            val raw = engine.callRaw("gradebook", com.owenleung.dewey.engine.jsonArgs(mapOf("sectionId" to sectionId))).toString()
            Cache.saveRaw("grades-$sectionId", raw)
            val g = summarize(raw)
            Cache.save(summaryKey(sectionId), CourseGrade.serializer(), g)
            Cache.save(anySummaryKey(sectionId), CourseGrade.serializer(), g)
            noteNewGrades(sectionId, grades[sectionId], g)
            grades[sectionId] = g
            gradesAsOf[sectionId] = Date()
            gradeErrors.remove(sectionId)
            learnFromGrades(g)
        } catch (e: EngineError) {
            gradeErrors[sectionId] = e
        } catch (e: Exception) {
            gradeErrors[sectionId] = EngineError.Js(e.message ?: "$e")
        }
    }

    /** Per build, so new grade math never shows an old summary. */
    private fun summaryKey(sectionId: String) = "gradeSummary-$sectionId-$buildCode"
    /** The last summary whatever the build: the offline fallback. */
    private fun anySummaryKey(sectionId: String) = "gradeSummaryLast-$sectionId"

    private val buildCode: Long by lazy {
        runCatching { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)) }
            .getOrDefault(0L)
    }

    suspend fun summarize(rows: String, whatIf: Map<String, Map<String, Double>> = emptyMap(),
                          extra: List<Map<String, Any>> = emptyList(), drop: List<String> = emptyList(),
                          weights: Map<String, Double> = emptyMap()): CourseGrade {
        val rowsEl: JsonElement = EngineJson.json.parseToJsonElement(rows)
        val opts = mutableMapOf<String, Any>("whatIf" to whatIf, "extra" to extra, "drop" to drop)
        if (weights.isNotEmpty()) opts["weights"] = weights
        return engine.call("courseGrade", mapOf("rows" to rowsEl, "opts" to opts))
    }

    /** The score a planned item needs, counting the what-if scores on screen (same opts as `summarize`). */
    suspend fun plan(sectionId: String, category: String, possible: Double, target: Double,
                     whatIf: Map<String, Map<String, Double>> = emptyMap(), extra: List<Map<String, Any>> = emptyList(),
                     drop: List<String> = emptyList()): PlanResult? {
        val raw = gradeRows(sectionId) ?: return null
        return runCatching {
            engine.call<PlanResult>("planTest", mapOf("rows" to EngineJson.json.parseToJsonElement(raw), "category" to category,
                "possible" to possible, "target" to target, "opts" to mapOf("whatIf" to whatIf, "extra" to extra, "drop" to drop)))
        }.getOrNull()
    }

    /** The gradebook category gives an item its type. */
    private fun learnFromGrades(g: CourseGrade) {
        var changed = false
        val byId = store.all().associateBy { it.schoologyId }
        for (gi in g.items) {
            val it = byId[gi.id] ?: continue
            if (it.categoryHint != gi.category) { it.categoryHint = gi.category; changed = true }
        }
        if (changed) save()
    }

    fun isNewGrade(id: String) = gradeLog[id]?.let { isNew(it) } ?: false

    /** The classes Grades shows: every class but the ones hidden there (Settings → Grades). */
    val gradeClasses: List<Course>
        get() {
            Prefs.revision.intValue
            val hidden = GradesLook.hidden()
            return classes.filter { it.sectionId !in hidden }
        }

    /** Unseen new scores, newest first. */
    fun newGrades(): List<Pair<Course, CourseGrade.Item>> =
        gradeClasses.flatMap { c -> (grades[c.sectionId]?.items ?: emptyList()).filter { isNewGrade(it.id) }.map { c to it } }
            .sortedWith { a, b -> gradedOrder(a.second, b.second) }

    /** Unseen new scores in one class, or everywhere (from the saved log, right before any gradebook loads). */
    fun newGradeCount(sectionId: String? = null): Int {
        if (sectionId == null) {
            // Hidden classes don't count (their items are known once a gradebook is saved).
            Prefs.revision.intValue
            val skip = GradesLook.hidden().flatMap { s -> (grades[s]?.items ?: emptyList()).map { it.id } }.toSet()
            return gradeLog.keys.count { isNewGrade(it) && it !in skip }
        }
        return (grades[sectionId]?.items ?: emptyList()).count { isNewGrade(it.id) }
    }

    /** Order: when the score appeared, then due date (both newest first). Negative = a first. */
    fun gradedOrder(a: CourseGrade.Item, b: CourseGrade.Item): Int {
        val x = gradeLog[a.id]?.at ?: Long.MIN_VALUE
        val y = gradeLog[b.id]?.at ?: Long.MIN_VALUE
        if (x != y) return y.compareTo(x)
        return (b.due ?: "").compareTo(a.due ?: "")
    }

    fun markGradesSeen(ids: Iterable<String>) {
        var changed = false
        for (id in ids) {
            val e = gradeLog[id] ?: continue
            if (!e.seen) { gradeLog[id] = e.copy(seen = true); changed = true }
        }
        if (changed) Cache.save("gradeLog", logSerializer, gradeLog.toMap())
    }

    fun markAllGradesSeen() = markGradesSeen(gradeLog.keys.toList())

    /** Logs scores that changed since the last saved gradebook; the first gradebook for a class only records what is there. */
    private fun noteNewGrades(sectionId: String, old: CourseGrade?, new: CourseGrade) {
        val log = gradeLog.toMutableMap()
        val fresh = logGrades(log, old?.items, new.items)
        gradeLog.putAll(log)
        Cache.save("gradeLog", logSerializer, gradeLog.toMap())
        val c = course(sectionId)
        if (fresh.isNotEmpty() && c != null && sectionId !in GradesLook.hidden()) Reminders.newGrades(c.shortTitle, fresh, old?.pct, new.pct)
    }

    /** Background refresh: a few gradebooks per run, in turn, so new-grade alerts come without opening the app. */
    suspend fun refreshSomeGrades(count: Int = 3) {
        val list = classes.map { it.sectionId }
        if (list.isEmpty()) return
        val key = "gradeRotation"
        val start = Prefs.int(key) % list.size
        val n = minOf(count, list.size)
        twoAtATime((0 until n).map { list[(start + it) % list.size] }) { loadGrades(it) }
        Prefs.set(key, start + n)
    }

    fun showToast(s: String) {
        toast = s
        scope.launch {
            delay(3000)
            if (toast == s) toast = null
        }
    }
}
