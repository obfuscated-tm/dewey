package com.owenleung.dewey.store

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.util.Date

enum class ItemType(val raw: String, val label: String, val display: String) {
    ASSIGNMENT("assignment", "HW", "HW"),
    EXAM("exam", "TEST", "Test"),
    CLASSWORK("classwork", "CW", "CW"),
    TASK("task", "TASK", "Task");

    companion object {
        fun of(raw: String?): ItemType? = entries.firstOrNull { it.raw == raw }
    }
}

/**
 * The student's own state for one Schoology item, plus what the app last saw
 * of it on Schoology. Keyed by the Schoology id. Survives relaunch; keeps
 * paper work Schoology dropped from its lists (docs/ios/FINDINGS.md).
 * A task the student added (`mine`) is a row too, keyed `mine-<uuid>`: it
 * never reaches Schoology or neo-plan, and lives only on the phone.
 * Dates are epoch milliseconds so the file stays plain JSON.
 */
@Serializable
class ItemState(
    val schoologyId: String,
    var title: String,
    var sectionId: String? = null,
    var courseName: String? = null,
    var dueAt: Long? = null,
    var url: String? = null,
    // The student's marks.
    var done: Boolean = false,
    var studied: Boolean = false,
    var turnedIn: Boolean = false,
    var typeOverride: String? = null,
    var dismissed: Boolean = false,
    // What Schoology said last.
    var inSchoology: Boolean = true,
    var graded: Boolean = false,
    var statusState: String? = null,       // submitted | not_submitted | no_dropbox | unknown
    var statusLate: Boolean? = null,
    var statusCheckedAt: Long? = null,
    var categoryHint: String? = null,      // gradebook category, for the type rule
    var labUrl: String? = null,            // an outside submit page from the instructions
    var external: Boolean = false,         // a Google Assignment
    var neoPlanId: String? = null,
    // The student's own task (TaskEditor), never on Schoology.
    var mine: Boolean = false,
    var notes: String? = null,
    // Plan (PlanLogic): the section the student dragged it to (null = by due date)
    // and its place there (null = not placed yet, slotted in by due date).
    var planSection: String? = null,
    var planRank: Double? = null,
    var firstSeen: Long = System.currentTimeMillis(),
    var lastSeen: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
) {
    var due: Date?
        get() = dueAt?.let(::Date)
        set(v) { dueAt = v?.time }

    val isQuiz: Boolean get() = (url ?: "").contains("/assessment")
    /** Schoology work neo-plan can take: not the student's own, not an event. */
    val isSchoologyAssignment: Boolean get() = !mine && !(url ?: "").contains("/event/")
    val submitted: Boolean get() = statusState == "submitted"
    val hasDropbox: Boolean get() = statusState == "submitted" || statusState == "not_submitted"

    var type: ItemType
        get() = ItemType.of(typeOverride) ?: TodoLogic.guessType(title, categoryHint, isQuiz)
        set(v) { typeOverride = v.raw }

    val isExam: Boolean get() = type == ItemType.EXAM

    fun touch() { updatedAt = System.currentTimeMillis() }

    companion object {
        const val MINE_PREFIX = "mine-"
        fun isMine(id: String) = id.startsWith(MINE_PREFIX)

        /** A new task of the student's own. */
        fun mine(title: String) = ItemState(schoologyId = MINE_PREFIX + java.util.UUID.randomUUID().toString().uppercase(), title = title).apply {
            mine = true
            typeOverride = ItemType.TASK.raw
        }
    }
}

/** The letters a goal can be, from grademath's SCALE (A down to C-). */
object Goal {
    val scale = listOf("A" to 93.0, "A-" to 90.0, "B+" to 87.0, "B" to 83.0, "B-" to 80.0, "C+" to 77.0, "C" to 73.0, "C-" to 70.0)

    fun letter(pct: Double): String = scale.firstOrNull { pct >= it.second - 1e-9 }?.first ?: "D"
}

/**
 * Every ItemState and per-class setting, kept in memory and written to one
 * JSON file on save (SwiftData on iOS). `real` is process-wide: the app, the
 * widgets' taps and the background refresh share it. The tour's pretend week
 * gets its own in-memory store that is never written.
 */
class ItemStore private constructor(private val file: File?, private val courseFile: File?, private val goalFile: File? = null) {
    private val items = LinkedHashMap<String, ItemState>()
    /** Per class: the A line the student set (CourseState.aCutoff on iOS). */
    private val cutoffs = HashMap<String, Double>()
    /** Per class: the grade the student aims for (CourseState.goal on iOS). */
    private val goals = HashMap<String, Double>()
    private val lock = Any()

    companion object {
        lateinit var real: ItemStore
            private set

        fun init(dir: File) {
            if (this::real.isInitialized) return
            real = ItemStore(File(dir, "items.json"), File(dir, "courses-state.json"), File(dir, "goals-state.json")).also { it.load() }
        }

        /** The tour's pretend week: in memory only. */
        fun inMemory() = ItemStore(null, null)
    }

    private fun load() {
        runCatching {
            Cache.json.decodeFromString(ListSerializer(ItemState.serializer()), file!!.readText()).forEach { items[it.schoologyId] = it }
        }
        runCatching { cutoffs.putAll(Cache.json.decodeFromString(MapSerializer(String.serializer(), Double.serializer()), courseFile!!.readText())) }
        runCatching { goals.putAll(Cache.json.decodeFromString(MapSerializer(String.serializer(), Double.serializer()), goalFile!!.readText())) }
    }

    fun item(id: String): ItemState? = synchronized(lock) { items[id] }
    fun all(): List<ItemState> = synchronized(lock) { items.values.toList() }

    fun getOrCreate(id: String, title: String): ItemState = synchronized(lock) {
        items.getOrPut(id) { ItemState(schoologyId = id, title = title) }
    }

    fun insert(it: ItemState) = synchronized(lock) { items[it.schoologyId] = it }
    fun remove(id: String) = synchronized(lock) { items.remove(id) }

    /** A different student signed in: every row and class setting goes. */
    fun clearAll() = synchronized(lock) {
        items.clear()
        cutoffs.clear()
        goals.clear()
        courseFile?.delete()
        goalFile?.delete()
        Unit
    }

    fun save() = synchronized(lock) {
        val f = file ?: return@synchronized
        runCatching { Cache.atomicWrite(f, Cache.json.encodeToString(ListSerializer(ItemState.serializer()), items.values.toList())) }
    }

    fun cutoff(sectionId: String): Double? = synchronized(lock) { cutoffs[sectionId] }
    fun setCutoff(sectionId: String, v: Double?) = synchronized(lock) {
        if (v == null) cutoffs.remove(sectionId) else cutoffs[sectionId] = v
        val f = courseFile ?: return@synchronized
        runCatching { Cache.atomicWrite(f, Cache.json.encodeToString(MapSerializer(String.serializer(), Double.serializer()), cutoffs)) }
    }

    fun goal(sectionId: String): Double? = synchronized(lock) { goals[sectionId] }
    fun setGoal(sectionId: String, v: Double) = synchronized(lock) {
        goals[sectionId] = v
        val f = goalFile ?: return@synchronized
        runCatching { Cache.atomicWrite(f, Cache.json.encodeToString(MapSerializer(String.serializer(), Double.serializer()), goals)) }
    }
}

/** Pure To Do rules, separate from storage so tests can run them. */
object TodoLogic {
    const val OLDER_DAYS = 60

    private val categoryTest = Regex("""(?i)\b(test|quiz|exam|summative|assessment)""")
    private val titleTest = Regex("""(?i)\b(exam|quiz|interro|test|midterm|final exam)\b""")

    /** TEST if the gradebook category says Test/Quiz/Exam/Summative, the title says Exam/Quiz/Test/Interro, or it's a quiz page; else HW. */
    fun guessType(title: String, category: String?, isQuiz: Boolean): ItemType {
        if (isQuiz) return ItemType.EXAM
        if (category != null && categoryTest.containsMatchIn(category)) return ItemType.EXAM
        if (titleTest.containsMatchIn(title)) return ItemType.EXAM
        return ItemType.ASSIGNMENT
    }

    data class Snapshot(
        val inSchoology: Boolean, val done: Boolean, val turnedIn: Boolean,
        val submitted: Boolean, val graded: Boolean, val dismissed: Boolean,
        /** The student's own task, and (for one) whether it was finished today. */
        val mine: Boolean = false, val finishedToday: Boolean = false,
    )

    /**
     * On the list: everything Schoology still lists, plus work it dropped that the student hasn't finished.
     * The student's own tasks stay until done, and a done one until the day ends.
     */
    fun isVisible(s: Snapshot): Boolean {
        if (s.mine) return !s.dismissed && (!(s.done || s.turnedIn) || s.finishedToday)
        if (s.dismissed || s.graded) return false
        if (s.inSchoology) return true
        return !(s.done || s.turnedIn || s.submitted)
    }

    fun isVisible(it: ItemState, now: Date = Date()) = isVisible(Snapshot(it.inSchoology, it.done, it.turnedIn, it.submitted, it.graded, it.dismissed,
        it.mine, Fmt.sameDay(Date(it.updatedAt), now)))

    /**
     * Whether a past-due row may say "late": only while Schoology still lists it
     * as overdue (work it dropped, like paper or lab-site work, may well be
     * handed in), and not once it's handed in or marked done. A test is late only
     * as an online quiz not marked studied: Schoology keeps quizzes given in class
     * on its overdue list, and a past test you studied for is one you took.
     * The student's own tasks always count as listed (`inSchoology: true`).
     */
    fun showsLate(type: ItemType, isQuiz: Boolean, done: Boolean, turnedIn: Boolean, submitted: Boolean, inSchoology: Boolean = true): Boolean {
        if (submitted || turnedIn || !inSchoology) return false
        if (type == ItemType.EXAM) return isQuiz && !done
        return !done
    }

    /** Calendar days late (≥ 0) or null when not overdue. */
    fun daysLate(due: Date?, now: Date = Date()): Int? {
        if (due == null || !due.before(now)) return null
        return maxOf(0, Fmt.daysBetween(due, now))
    }

    fun isOlder(due: Date?, now: Date = Date()) = (daysLate(due, now) ?: 0) > OLDER_DAYS

    enum class Bucket { OVERDUE, TODAY, WEEK, LATER }

    fun bucket(due: Date?, now: Date = Date()): Bucket {
        if (due == null) return Bucket.LATER
        if (due.before(now)) return Bucket.OVERDUE
        if (Fmt.sameDay(due, now)) return Bucket.TODAY
        if (due.before(Fmt.addDays(Fmt.startOfDay(now), 7))) return Bucket.WEEK
        return Bucket.LATER
    }
}

/** Which part of the To Do list is showing: overdue work, one day of the coming week (0 = today), or a day picked on the month and the day after it. */
sealed class DayFilter {
    data object Late : DayFilter()
    data class Day(val k: Int) : DayFilter()
    /** `start`: a day's start, epoch ms. */
    data class Span(val start: Long) : DayFilter()

    fun matches(it: ItemState, now: Date): Boolean {
        val b = TodoLogic.bucket(it.due, now)
        return when (this) {
            Late -> b == TodoLogic.Bucket.OVERDUE && !TodoLogic.isOlder(it.due, now) &&
                TodoLogic.showsLate(it.type, it.isQuiz, if (it.isExam) it.studied else it.done, it.turnedIn, it.submitted, it.inSchoology)
            is Day -> {
                val due = it.due ?: return false
                b != TodoLogic.Bucket.OVERDUE && Fmt.sameDay(due, Fmt.addDays(Fmt.startOfDay(now), k))
            }
            is Span -> {
                val due = it.due ?: return false
                val s = Fmt.startOfDay(Date(start))
                !due.before(s) && due.before(Fmt.addDays(s, 2))
            }
        }
    }
}
