package com.owenleung.dewey

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.engine.CourseGrade
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.EngineError
import com.owenleung.dewey.engine.EngineJson
import com.owenleung.dewey.engine.PlanResult
import com.owenleung.dewey.engine.SubmissionStatus
import com.owenleung.dewey.engine.TodoLists
import com.owenleung.dewey.neoplan.NeoPlanClient
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

        /** "New" wears off after a week even if never opened. */
        const val NEW_GRADE_DAYS = 7.0

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
    val engine = Engine(context)
    val inbox = Inbox()

    var session by mutableStateOf(Session.CHECKING)
        private set

    private fun moveTo(s: Session) {
        val was = session
        session = s
        // The widgets say "Open Dewey to sign in" while signed out.
        if (s != was && s != Session.CHECKING) WidgetBridge.publish(this)
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
            if (session != Session.SIGNED_OUT) {
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
        refreshTodo()
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
        get() = courses.filter { it.isClass }.ifEmpty { courses }

    fun course(sectionId: String?): Course? = sectionId?.let { s -> courses.firstOrNull { it.sectionId == s } }

    fun cutoff(sectionId: String): Double = ItemStore.cutoff(sectionId) ?: 93.0

    // ── To Do ────────────────────────────────────────────────────────────

    fun item(id: String): ItemState? {
        revision.intValue
        return ItemStore.item(id)
    }

    /** The To Do list: visible items (TodoLogic.isVisible). */
    fun todoItems(): List<ItemState> {
        revision.intValue
        return ItemStore.all().filter(TodoLogic::isVisible)
    }

    /** Open (not finished) overdue work: the tab badge. */
    fun overdueCount(): Int {
        val now = Date()
        return todoItems().count { (it.due?.before(now) ?: false) && !isFinished(it) && !TodoLogic.isOlder(it.due, now) }
    }

    fun isFinished(it: ItemState) = it.submitted || it.turnedIn || (if (it.isExam) false else it.done)

    /** Pull Schoology's lists, merge, then check a few dropboxes and neo-plan. */
    suspend fun refreshTodo() {
        if (refreshingTodo) return
        refreshingTodo = true
        try {
            if (courses.isEmpty()) runCatching { loadCourses() }
            val lists: TodoLists = engine.call("todo")
            merge(lists)
            todoError = null
            todoAsOf = Date().also { Cache.saveDate("todoAsOf", it) }
            // Show the merged list now; dropbox checks and neo-plan fill in after.
            revision.intValue++
            checkStatuses()
            syncNeoPlan()
        } catch (e: EngineError) {
            todoError = e
        } catch (e: Exception) {
            todoError = EngineError.Js(e.message ?: "$e")
        } finally {
            refreshingTodo = false
        }
        revision.intValue++
        WidgetBridge.publish(this)
        Reminders.schedule(todoItems(), courses)
    }

    fun merge(lists: TodoLists, now: Date = Date()) {
        val seen = HashSet<String>()
        for (e in lists.overdue + lists.upcoming) {
            seen += e.schoologyId
            val it = ItemStore.getOrCreate(e.schoologyId, e.title)
            it.title = e.title
            it.sectionId = e.sectionId ?: it.sectionId
            it.courseName = e.course ?: it.courseName
            it.due = Fmt.date(e.due) ?: it.due
            it.url = e.url ?: it.url
            it.graded = e.graded ?: false
            it.inSchoology = true
            it.lastSeen = now.time
        }
        for (it in ItemStore.all()) if (it.schoologyId !in seen) it.inSchoology = false
        ItemStore.save()
    }

    /** status({id}) for up to 10 rows that might have a dropbox, not checked in 30 min. */
    suspend fun checkStatuses(cap: Int = 10) {
        val cutoff = System.currentTimeMillis() - 30 * 60_000
        val candidates = todoItems()
            .filter { !it.isQuiz && !it.external && it.statusState != "no_dropbox" && (it.statusCheckedAt ?: 0) < cutoff }
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
        val s: SubmissionStatus = try { engine.call("status", mapOf("id" to it.schoologyId)) } catch (e: Exception) { return null }
        val was = it.statusState
        it.statusState = if (s.quiz == true) "unknown" else s.state
        it.statusLate = s.late
        it.statusCheckedAt = System.currentTimeMillis()
        if (s.state == "submitted" && was != "submitted") it.neoPlanId?.let { id -> runCatching { NeoPlanClient.turnIn(id) } }
        ItemStore.save()
        revision.intValue++
        WidgetBridge.publish(this)
        return s
    }

    /** After the Schoology sheet closes: if Schoology now says submitted, say so and clear the row. */
    fun recheckAfterSheet(id: String) {
        scope.launch {
            val it = ItemStore.item(id) ?: run { revision.intValue++; return@launch }
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
        val ready = if (it.isExam) it.studied else it.done
        pushNeoPlan(it) { id -> NeoPlanClient.setWork(id, ready) }
    }

    fun setTurnedIn(it: ItemState, on: Boolean) {
        it.turnedIn = on
        if (on) it.done = true
        it.touch()
        save()
        pushNeoPlan(it) { id -> if (on) NeoPlanClient.turnIn(id) else NeoPlanClient.putBack(id) }
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
        pushNeoPlan(it) { id -> NeoPlanClient.setType(id, t) }
    }

    fun dismiss(it: ItemState) {
        it.dismissed = true
        it.touch()
        save()
    }

    fun save() {
        ItemStore.save()
        revision.intValue++
        WidgetBridge.publish(this)
        Reminders.schedule(todoItems(), courses)
    }

    // ── neo-plan ─────────────────────────────────────────────────────────

    /** Pending neo-plan writes made offline, by Schoology id; retried on refresh. */
    private val pendingPush = HashMap<String, suspend (String) -> Unit>()

    private fun pushNeoPlan(it: ItemState, op: suspend (String) -> Unit) {
        if (!NeoPlanClient.connected) return
        val sid = it.schoologyId
        scope.launch {
            val id = neoPlanId(sid) ?: return@launch
            try { op(id) } catch (e: Exception) { pendingPush[sid] = op }
        }
    }

    private suspend fun neoPlanId(sid: String): String? {
        val item = ItemStore.item(sid) ?: return null
        item.neoPlanId?.let { return it }
        runCatching { NeoPlanClient.items(listOf(sid)).firstOrNull()?.id }.getOrNull()?.let { found -> item.neoPlanId = found; return found }
        runCatching {
            NeoPlanClient.addItem(sid, item.sectionId, item.title, item.due, if (item.isQuiz) ItemType.EXAM else null)
        }.getOrNull()?.let { added -> item.neoPlanId = added.id; return added.id }
        return null
    }

    /** neo-plan is the shared copy: the newer edit wins. Pulls its state for every listed item. */
    suspend fun syncNeoPlan() {
        if (!NeoPlanClient.connected) return
        for ((sid, op) in pendingPush.toMap()) {
            val id = neoPlanId(sid) ?: continue
            if (runCatching { op(id) }.isSuccess) pendingPush.remove(sid)
        }
        val items = todoItems()
        val got = runCatching { NeoPlanClient.items(items.map { it.schoologyId }) }.getOrNull() ?: return
        val bySource = got.filter { it.sourceId != null }.associateBy { it.sourceId!! }
        val lastSync = Cache.loadDate("neoplanSyncedAt")?.time ?: 0
        for (it in items) {
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

    // ── Grades ───────────────────────────────────────────────────────────

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
            }
        }
        val at = gradesAsOf[sectionId]
        if (!force && at != null && Date().time - at.time < 30 * 60_000 && grades[sectionId] != null) return
        try {
            val raw = engine.callRaw("gradebook", com.owenleung.dewey.engine.jsonArgs(mapOf("sectionId" to sectionId))).toString()
            Cache.saveRaw("grades-$sectionId", raw)
            val g = summarize(raw)
            Cache.save(summaryKey(sectionId), CourseGrade.serializer(), g)
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

    suspend fun plan(sectionId: String, category: String, possible: Double, target: Double): PlanResult? {
        val raw = gradeRows(sectionId) ?: return null
        return runCatching {
            engine.call<PlanResult>("planTest", mapOf("rows" to EngineJson.json.parseToJsonElement(raw), "category" to category,
                "possible" to possible, "target" to target))
        }.getOrNull()
    }

    /** The gradebook category gives an item its type. */
    private fun learnFromGrades(g: CourseGrade) {
        var changed = false
        val byId = ItemStore.all().associateBy { it.schoologyId }
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
