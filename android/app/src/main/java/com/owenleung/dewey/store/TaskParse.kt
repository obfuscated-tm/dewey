package com.owenleung.dewey.store

import com.owenleung.dewey.engine.Course
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

/**
 * Quick add (To Do's +): reads a day, a time, a class and a kind out of what the
 * student types, e.g. "chem lab fri after school" → Fri · 3:30 pm · Chemistry H.
 * The date and time words leave the title; the class and kind words stay in it
 * ("Chem lab" is still the task). Pure, so tests can run it (TaskParseTest;
 * ios/App/Store/TaskParse.swift).
 */
object TaskParse {
    /** A time of day. */
    data class Clock(val hour: Int, val minute: Int)

    /** Before school and after school: the student's school day (Settings › To Do › School day). */
    val beforeSchool: Clock get() = SchoolDay.start
    val afterSchool: Clock get() = SchoolDay.end
    /** Due tonight: 11:59 pm, like Schoology's "due that day". */
    val TONIGHT = Clock(23, 59)

    data class ClassName(val sectionId: String, val title: String)

    data class Result(
        /** What's left once the date and time words are out (the typed text if nothing is left). */
        val title: String,
        val day: LocalDate?,
        val time: Clock?,
        val sectionId: String?,
        val type: ItemType?,
    )

    private val punct = Regex("""^[\p{Punct}&&[^:/]]+|[\p{Punct}&&[^:/]]+$""")

    fun parse(
        text: String, now: Date, classes: List<ClassName> = emptyList(), zone: ZoneId = Fmt.zone,
        week: WeekFields = WeekFields.of(Locale.getDefault()),
        before: Clock = beforeSchool, after: Clock = afterSchool,
    ): Result {
        val words = text.split(" ").filter { it.isNotEmpty() }
        val low = words.map { it.lowercase().replace(punct, "") }
        val used = HashSet<Int>()
        var day: LocalDate? = null
        var time: Clock? = null
        val today = now.toInstant().atZone(zone).toLocalDate()

        var i = 0
        while (i < low.size) {
            val w = low[i]
            val next = low.getOrNull(i + 1)
            // "on fri", "by tmrw", "due 10/24", "at 8am": the little word goes too, when it leads into one.
            fun take(n: Int) {
                for (k in i until i + n) used += k
                if (i > 0 && low[i - 1] in setOf("on", "by", "due", "at", "for") && (i - 1) !in used) used += i - 1
                i += n
            }
            if (day == null && w in setOf("today", "tdy")) { day = today; take(1); continue }
            if (day == null && w == "tonight") { day = today; time = time ?: TONIGHT; take(1); continue }
            if (day == null && w in setOf("tomorrow", "tmrw", "tmr", "tmw", "tomorow")) { day = today.plusDays(1); take(1); continue }
            if (day == null && w == "next" && next != null) {
                val wd = weekday(next, words[i + 1])
                if (wd != null) {
                    // "next tue": that day in next week's calendar week (on a Thursday, the coming Tuesday).
                    val u = upcoming(wd, today)
                    val startOf = { d: LocalDate -> d.with(TemporalAdjusters.previousOrSame(week.firstDayOfWeek)) }
                    day = if (startOf(u) == startOf(today)) u.plusDays(7) else u
                    take(2); continue
                }
            }
            if (day == null) {
                val wd = weekday(w, words[i])
                if (wd != null) { day = upcoming(wd, today); take(1); continue }
            }
            if (day == null && w == "in" && next != null && i + 2 < low.size && low[i + 2] in setOf("day", "days")) {
                val k = next.toIntOrNull()
                if (k != null) { day = today.plusDays(k.toLong()); take(3); continue }
            }
            if (day == null && w == "in" && (next == "a" || next == "1") && i + 2 < low.size && low[i + 2] == "week") { day = today.plusDays(7); take(3); continue }
            if (day == null) {
                val d = slashDate(w, today)
                if (d != null) { day = d; take(1); continue }
            }
            if (day == null && next != null) {
                val m = month(w); val dn = next.toIntOrNull()
                val date = if (m != null && dn != null) monthDay(m, dn, today) else null
                if (date != null) { day = date; take(2); continue }
            }
            if (time == null && (w == "before" || w == "after") && next == "school") { time = if (w == "before") before else after; take(2); continue }
            if (time == null && w == "noon") { time = Clock(12, 0); take(1); continue }
            if (time == null && w == "midnight") { time = TONIGHT; take(1); continue }
            if (time == null) {
                val c = clock(w, next)
                if (c != null) { time = c.first; take(c.second); continue }
            }
            // "at 3": a bare number counts only after "at".
            if (time == null && w == "at" && next != null) {
                val h = next.toIntOrNull()
                if (h != null && h in 1..12) { time = Clock(if (h <= 6) h + 12 else h, 0); used += i; used += i + 1; i += 2; continue }
            }
            i += 1
        }

        val kept = words.filterIndexed { k, _ -> k !in used }
        var title = kept.joinToString(" ").trim()
        if (title.isEmpty()) title = text.trim()
        return Result(title, day, time, matchClass(low, classes), kind(low))
    }

    /** The due date from a day and a time. A time alone is today, or tomorrow once it has passed; a day alone is by the end of it; neither is no date. */
    fun due(day: LocalDate?, time: Clock?, now: Date, zone: ZoneId = Fmt.zone): Date? {
        if (day == null && time == null) return null
        val t = time ?: TONIGHT
        var d = day ?: now.toInstant().atZone(zone).toLocalDate()
        var at = ZonedDateTime.of(d, LocalTime.of(t.hour, t.minute), zone).toInstant()
        if (day == null && !at.isAfter(now.toInstant())) {
            d = d.plusDays(1)
            at = ZonedDateTime.of(d, LocalTime.of(t.hour, t.minute), zone).toInstant()
        }
        return Date.from(at)
    }

    // ── Pieces ───────────────────────────────────────────────────────────

    /** All-caps "SAT" is the test, not Saturday. */
    private fun weekday(w: String, original: String): DayOfWeek? {
        if (original.startsWith("SAT")) return null
        return when (w) {
            "sun", "sunday" -> DayOfWeek.SUNDAY
            "mon", "monday" -> DayOfWeek.MONDAY
            "tue", "tues", "tuesday" -> DayOfWeek.TUESDAY
            "wed", "weds", "wednesday" -> DayOfWeek.WEDNESDAY
            "thu", "thur", "thurs", "thursday" -> DayOfWeek.THURSDAY
            "fri", "friday" -> DayOfWeek.FRIDAY
            "sat", "saturday" -> DayOfWeek.SATURDAY
            else -> null
        }
    }

    /** The next such weekday, today included ("quiz fri" on a Friday is today's). */
    private fun upcoming(wd: DayOfWeek, today: LocalDate): LocalDate = today.with(TemporalAdjusters.nextOrSame(wd))

    private val monthShort = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val monthFull = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")

    private fun month(w: String): Int? {
        monthFull.indexOf(w).takeIf { it >= 0 }?.let { return it + 1 }
        if (w == "sept") return 9
        monthShort.indexOf(w).takeIf { it >= 0 }?.let { return it + 1 }
        return null
    }

    /** A month and day, this year, or next year if it has passed. */
    private fun monthDay(m: Int, d: Int, today: LocalDate): LocalDate? {
        if (d !in 1..31) return null
        var date = runCatching { LocalDate.of(today.year, m, d) }.getOrNull() ?: return null
        if (date.isBefore(today)) date = date.plusYears(1)
        return date
    }

    /** "10/24" or "10/24/26". */
    private fun slashDate(w: String, today: LocalDate): LocalDate? {
        val parts = w.split("/")
        val p = parts.mapNotNull { it.toIntOrNull() }
        if (p.size < 2 || p.size != parts.size || p[0] !in 1..12) return null
        if (p.size == 3) return runCatching { LocalDate.of(if (p[2] < 100) 2000 + p[2] else p[2], p[0], p[1]) }.getOrNull()
        return monthDay(p[0], p[1], today)
    }

    private val clockRe = Regex("""(\d{1,2})(?::(\d{2}))?(am|pm|a|p)?""")

    /** "8am", "8:30", "3:30pm", "8 am". Without am or pm, 1 to 6 o'clock is the afternoon. */
    private fun clock(w: String, next: String?): Pair<Clock, Int>? {
        val m = clockRe.matchEntire(w) ?: return null
        val h0 = m.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minStr = m.groups[2]?.value
        val minute = minStr?.toIntOrNull() ?: 0
        if (minute >= 60) return null
        var mark = m.groups[3]?.value
        var words = 1
        if (mark == null && next != null && next in setOf("am", "pm")) { mark = next; words = 2 }
        // A bare number ("2 problems") isn't a time; it needs a colon or am/pm.
        if (mark == null && minStr == null) return null
        var h = h0
        if (mark != null) {
            if (h0 !in 1..12) return null
            if (mark.startsWith("p") && h < 12) h += 12
            if (mark.startsWith("a") && h == 12) h = 0
        } else if (h in 1..6) {
            h += 12
        }
        return Clock(h, minute) to words
    }

    private fun kind(low: List<String>): ItemType? {
        if (low.any { it in setOf("test", "quiz", "exam", "midterm", "final", "finals") }) return ItemType.EXAM
        if (low.any { it in setOf("hw", "homework") }) return ItemType.ASSIGNMENT
        return null
    }

    /** Words that start many class names or are just part of the task. */
    private val notClasses = setOf(
        "the", "and", "for", "with", "study", "studies", "read", "reading", "write", "writing", "work",
        "lab", "test", "quiz", "review", "finish", "notes", "project", "practice", "unit", "chapter",
    )

    /** A class named by the start of one of its words ("chem", "bio", "precalc", "spanish"), when it fits exactly one class. */
    private fun matchClass(low: List<String>, classes: List<ClassName>): String? {
        val split = Regex("[^\\p{L}]+")
        val keys = classes.map { c ->
            val t = Course.short(c.title).lowercase()
            val joined = t.replace("-", "")
            val parts = t.split(split) + joined.split(split)
            c.sectionId to parts.filter { it.length >= 4 }
        }
        for (w in low) {
            if (w.length < 3 || w in notClasses || !w.all { it.isLetter() }) continue
            val hits = keys.filter { (_, parts) -> parts.any { it.startsWith(w) } }.map { it.first }.toSet()
            if (hits.size == 1) return hits.first()
        }
        return null
    }
}

/** When school starts and ends, as the student set it (Settings › To Do › School day). Before school is the start, after school the end. */
object SchoolDay {
    const val START_KEY = "schoolStart"
    const val END_KEY = "schoolEnd"
    /** The guess until it's set: 8:00 am and 3:30 pm, as minutes after midnight. */
    const val DEFAULT_START = 8 * 60
    const val DEFAULT_END = 15 * 60 + 30

    val start: TaskParse.Clock get() = clock(START_KEY, DEFAULT_START)
    val end: TaskParse.Clock get() = clock(END_KEY, DEFAULT_END)

    private fun clock(key: String, fallback: Int): TaskParse.Clock {
        val m = if (Prefs.ready && Prefs.has(key)) Prefs.int(key) else fallback
        return TaskParse.Clock(m / 60, m % 60)
    }
}
