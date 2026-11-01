package com.owenleung.dewey.engine

import androidx.compose.runtime.mutableStateListOf
import com.owenleung.dewey.store.CallNumber
import com.owenleung.dewey.store.Cache
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.GradesLook
import com.owenleung.dewey.store.IdSet
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.store.ShelfLook
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * The tour's pretend week (ios/App/Engine/Practice.swift). While it runs, a second
 * AppModel (in memory, its engine answered from here) drives the real screens, and
 * nothing reaches Schoology, neo-plan, reminders, widgets or the saved copies: those
 * check `Practice.active` or `AppModel.practice`. Grade math still runs in engine.js
 * (the real engine page, math only). The Schoology sheet is a pretend dropbox.
 */
object Practice {
    /** On from the start of practice until it closes. */
    @Volatile var active = false
        private set
    /** Work "submitted" in the pretend dropbox; `status` answers from this. */
    val submitted = mutableStateListOf<String>()

    fun begin() {
        submitted.clear()
        // A practice the app was killed in the middle of left its settings behind.
        clearSettings()
        restoreView()
        saveView()
        active = true
        Cache.clearPractice()
    }

    fun end() {
        active = false
        submitted.clear()
        Cache.clearPractice()
        clearSettings()
        restoreView()
    }

    // ── How To Do looks ──────────────────────────────────────────────────
    // Shared with the real app (Prefs). The steps need List by class with the week
    // strip and Plan as it starts, so practice sets them and puts the student's own
    // back when it ends (or, after a practice the app was killed in, when the next begins).

    private const val SAVED_VIEW_KEY = "practiceSavedView"
    private val viewKeys: Map<String, Any> = mapOf(
        "todoMode" to "list", "todoLayout" to "week", "todoMonth" to false, "planCollapsed" to "done", "planHintClosed" to true,
    )

    private fun saveView() {
        val all = Prefs.sp.all
        // Keys that weren't set are saved as missing, so they go back to their defaults.
        val saved = viewKeys.keys.associateWith { k -> all[k]?.let { "${if (it is Boolean) "b" else "s"}:$it" } ?: "" }
        Prefs.set(SAVED_VIEW_KEY, kotlinx.serialization.json.Json.encodeToString(saved))
        for ((k, v) in viewKeys) Prefs.set(k, v)
    }

    fun restoreView() {
        val raw = Prefs.string(SAVED_VIEW_KEY) ?: return
        val saved = runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(raw) }.getOrNull() ?: emptyMap()
        val all = Prefs.sp.all
        for ((k, v) in saved) {
            // One the student changed during practice (Settings › To Do › View) is theirs now.
            if (all[k] != viewKeys[k]) continue
            when {
                v.isEmpty() -> Prefs.set(k, null)
                v.startsWith("b:") -> Prefs.set(k, v.removePrefix("b:").toBoolean())
                else -> Prefs.set(k, v.removePrefix("s:"))
            }
        }
        Prefs.set(SAVED_VIEW_KEY, null)
    }

    /** Settings the pretend classes left behind: shelf, hidden in Grades, call numbers. */
    private fun clearSettings() {
        val ids = courses.map { it.id }.toSet()
        Prefs.set(ShelfLook.OFF_KEY, (IdSet.parse(Prefs.string(ShelfLook.OFF_KEY)) - ids).sorted().joinToString(","))
        Prefs.set(GradesLook.HIDDEN_KEY, (IdSet.parse(Prefs.string(GradesLook.HIDDEN_KEY)) - ids).sorted().joinToString(","))
        var raw = Prefs.string(CallNumber.KEY)
        for (id in ids) raw = CallNumber.setting(raw, id, null)
        Prefs.set(CallNumber.KEY, raw)
    }

    // ── The pretend week: three classes, seven things due ────────────────

    data class PCourse(val id: String, val title: String, val section: String)

    val courses = listOf(
        PCourse("7000001101", "AP Biology - 1101", "RiveraM p2 T1"),
        PCourse("7000001505", "Chemistry H - 1505", "NguyenK p5 T1"),
        PCourse("7000001303", "English 10 - 1303", "PatelS p3 T1"),
    )
    private fun course(short: String) = courses.first { it.title.startsWith(short) }

    /** The ids the tour's steps point at. */
    const val LAB = "9206"
    const val WORKSHEET = "9502"
    const val QUIZ = "9503"
    const val FLAME_TEST = "9504"
    /** Plan's steps: two things due tomorrow (reorder one, send one to Later). */
    const val READING = "9702"
    const val PROBLEMS = "9701"
    val chem = course("Chem").id
    val english = course("English").id

    private data class Event(val id: String, val title: String, val course: PCourse, val day: Int, val hour: Int, val minute: Int = 59,
                             val status: String = "no_dropbox")

    private val events = listOf(
        Event(LAB, "Lab 2.2 Cell Size and Diffusion", course("AP Bio"), 0, 23, status = "not_submitted"),
        Event(WORKSHEET, "Stoichiometry Worksheet", course("Chem"), -1, 8, 30),
        Event(QUIZ, "Unit 2 Quiz", course("Chem"), 3, 9, 40),
        Event("9304", "Essay Draft: The Crucible", course("English"), 2, 23, status = "submitted"),
        Event(READING, "Reading Notes 3.1", course("AP Bio"), 1, 8, 0),
        Event(PROBLEMS, "Ch. 4 Practice Problems", course("Chem"), 1, 23),
        Event("9703", "Vocab Unit 5", course("English"), 5, 23),
    )

    /** Today at `hour:minute`, `day` days away. */
    private fun at(day: Int, hour: Int, minute: Int): Date = Fmt.atHour(Fmt.addDays(Date(), day), hour, minute)

    private fun eventJson(e: Event) = mapOf("schoologyId" to e.id, "title" to e.title, "course" to e.course.title, "sectionId" to e.course.id,
        "due" to Fmt.iso(at(e.day, e.hour, e.minute)), "graded" to false, "url" to "/assignment/${e.id}")

    // ── Gradebooks: (id, title, day, earned, possible, comment); earned null = not graded yet ──

    private data class G(val id: String, val title: String, val day: Int, val earned: Double?, val possible: Double, val comment: String = "")

    private val books: Map<String, List<Triple<String, Double, List<G>>>> = mapOf(
        "AP Biology" to listOf(
            Triple("Labs & Homework", 30.0, listOf(G("9201", "Lab Safety Contract", -58, 5.0, 5.0), G("9210", "Lab 1.1 Measuring pH", -49, 10.0, 10.0),
                G("9212", "Lab 1.2 Enzyme Activity", -40, 19.0, 20.0, "Great graph. Label your axis units next time."),
                G("9214", "Lab 2.1 Osmosis in Potatoes", -28, 20.0, 20.0), G(LAB, "Lab 2.2 Cell Size and Diffusion", 0, null, 20.0))),
            Triple("Tests & Quizzes", 70.0, listOf(G("9202", "Unit 1 Quiz", -44, 18.0, 20.0), G("9203", "Unit 1 Test", -33, 46.5, 50.0),
                G("9205", "Unit 2 Test", -2, 47.0, 50.0, "Nice work on the FRQ."))),
        ),
        "Chemistry H" to listOf(
            Triple("Labs", 25.0, listOf(G("9501", "Lab: Density", -40, 19.0, 20.0), G(FLAME_TEST, "Lab: Flame Test", -20, 18.0, 20.0))),
            Triple("Homework", 15.0, listOf(G("9505", "Ch. 1 Problems", -45, 10.0, 10.0), G("9506", "Ch. 2 Problems", -30, 8.0, 10.0),
                G(WORKSHEET, "Stoichiometry Worksheet", -1, null, 10.0))),
            Triple("Tests", 60.0, listOf(G("9507", "Unit 1 Test", -25, 42.0, 50.0), G(QUIZ, "Unit 2 Quiz", 3, null, 25.0))),
        ),
        "English 10" to listOf(
            Triple("Writing", 40.0, listOf(G("9301", "Personal Narrative", -40, 46.0, 50.0, "Strong voice. Tighten the ending."),
                G("9302", "Literary Analysis Paragraph", -6, 18.0, 20.0), G("9304", "Essay Draft: The Crucible", 2, null, 50.0))),
            Triple("Reading", 30.0, listOf(G("9303", "Reading Check: Act 1", -12, 9.0, 10.0))),
            Triple("Participation", 30.0, listOf(G("9307", "Participation, weeks 1-6", -3, 30.0, 30.0))),
        ),
    )

    private val wall = DateTimeFormatter.ofPattern("M/d/yy h:mma", Locale.US)
    private fun fmt(x: Double) = if (x == Math.rint(x)) x.toLong().toString() else x.toString()

    /** parseGrades-shaped rows: course, period, then each category and its items. */
    private fun gradebook(c: PCourse): List<Map<String, Any?>> {
        val book = books[Course.short(c.title)] ?: return emptyList()
        val rows = mutableListOf<Map<String, Any?>>(
            mapOf("level" to "course", "title" to "Course Grade", "due" to "", "grade" to "", "comment" to ""),
            mapOf("level" to "period", "title" to "26-27 T1", "due" to "", "grade" to "", "comment" to ""),
        )
        for ((cat, weight, items) in book) {
            rows += mapOf("level" to "category", "title" to cat, "due" to "", "grade" to "", "comment" to "", "weight" to weight)
            for (g in items) {
                val due = LocalDate.now(Fmt.zone).plusDays(g.day.toLong()).atTime(23, 59)
                rows += mapOf("level" to "item", "title" to g.title, "due" to wall.format(due), "comment" to g.comment, "id" to g.id,
                    "grade" to (g.earned?.let { "${fmt(it)} / ${fmt(g.possible)}" } ?: "—"))
            }
        }
        return rows
    }

    private fun assignment(id: String): Map<String, Any?> {
        val e = events.firstOrNull { it.id == id }
        val title = e?.title ?: books.values.flatten().flatMap { it.third }.firstOrNull { it.id == id }?.title ?: "Assignment"
        val a = mutableMapOf<String, Any?>("id" to id, "title" to title, "type" to "assignment", "attachments" to emptyList<Any>(),
            "instructionsMd" to "Work through the handout, then hand in **one PDF** with your answers.\n\nQuestions? Come to tutorial on Wednesday.")
        if (e != null) a["due"] = Fmt.iso(at(e.day, e.hour, e.minute))
        val s = if (id in submitted) "submitted" else e?.status
        if (s != null && s != "no_dropbox") a["submission"] = mapOf("state" to s)
        return a
    }

    private fun materials(sectionId: String, folderId: String?): List<Map<String, Any?>> {
        if (folderId != null || courses.none { it.id == sectionId }) return emptyList()
        return listOf("Course Information", "Unit 1", "Unit 2").mapIndexed { i, t -> mapOf("kind" to "folder", "title" to t, "id" to "f$i") }
    }

    /** Ops that only do math in engine.js; everything else is answered here. */
    val pureOps = setOf("courseGrade", "planTest", "annotate", "math")

    /** The op's `data`, or null to let engine.js run it (pure ops only). Anything else is a pretend 404. */
    fun answer(op: String, args: JsonObject): JsonElement? {
        if (op in pureOps) return null
        fun arg(k: String) = (args[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val id = arg("id") ?: ""
        val v: Any? = when (op) {
            "courses" -> courses.map { mapOf("sectionId" to it.id, "title" to it.title, "sectionTitle" to it.section) }
            "todo" -> mapOf("overdue" to events.filter { it.day < 0 }.map(::eventJson), "upcoming" to events.filter { it.day >= 0 }.map(::eventJson))
            "status" -> mapOf("state" to if (id in submitted) "submitted" else events.firstOrNull { it.id == id }?.status ?: "no_dropbox")
            "gradebook" -> courses.firstOrNull { it.id == arg("sectionId") }?.let(::gradebook) ?: throw EngineError.Http(404)
            "assignment" -> assignment(id)
            "dropbox" -> mapOf("grade" to null, "comments" to emptyList<Any>())
            "materials" -> materials(arg("sectionId") ?: "", arg("folderId"))
            "messages" -> mapOf("threads" to emptyList<Any>(), "unreadCount" to 0, "hasMore" to false)
            "notifications" -> mapOf("items" to emptyList<Any>(), "hasMore" to false)
            "feed" -> emptyList<Any>()
            else -> throw EngineError.Http(404)
        }
        return toJson(v)
    }
}
