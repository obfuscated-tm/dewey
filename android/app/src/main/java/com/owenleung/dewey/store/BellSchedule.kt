package com.owenleung.dewey.store

import com.owenleung.dewey.engine.Course
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.UUID

/**
 * The student's bell schedule, typed once in Settings → Schedule (ios/Shared/BellSchedule.swift).
 * Schoology has no bell times, so this is the student's own: day types the student names (Block A…),
 * each a list of periods with a start and an end, and which type each weekday uses.
 * Immutable: every edit returns a new schedule.
 */
@Serializable
data class BellSchedule(
    val dayTypes: List<DayType> = emptyList(),
    /** Calendar weekday (1 = Sunday … 7 = Saturday, as on iOS) → day type id. A day left out has no school. */
    val week: Map<Int, String> = emptyMap(),
    /** Slot name → what it is. */
    val periods: Map<String, Period> = emptyMap(),
    val today: TodayOverride? = null,
) {
    /** One row of a day: "P3" 9:40–10:30, or "Lunch". Times are minutes after midnight. */
    @Serializable
    data class Slot(val id: String = newId(), val name: String, val start: Int, val end: Int)

    @Serializable
    data class DayType(val id: String = newId(), val name: String, val slots: List<Slot> = emptyList()) {
        /** The slots in time order, without ones that end before they start. */
        val ordered: List<Slot> get() = slots.filter { it.end > it.start }.sortedBy { it.start }
    }

    /** What a period is for this student: the class, where, and who teaches it. */
    @Serializable
    data class Period(val title: String = "", val room: String = "", val teacher: String = "", val color: Int? = null)

    /** "Today is different": another day type, or no periods (null), for one date (yyyy-MM-dd) only. */
    @Serializable
    data class TodayOverride(val day: String, val dayType: String? = null)

    val isEmpty: Boolean get() = dayTypes.all { it.slots.isEmpty() }

    // ── Reading it ───────────────────────────────────────────────────────

    /** The day type `date` runs on: today's override when it's for that date, else the weekday's. */
    fun dayType(on: Date, zone: ZoneId = ZoneId.systemDefault()): DayType? {
        val local = on.toInstant().atZone(zone).toLocalDate()
        val id = if (today != null && today.day == local.toString()) today.dayType else week[weekday(local)]
        return id?.let { i -> dayTypes.firstOrNull { it.id == i } }
    }

    /** A slot on a real day. */
    data class Block(val name: String, val period: Period, val start: Date, val end: Date) {
        /** The class's name, or the slot's own when nothing is set for it. */
        val title: String get() = period.title.ifEmpty { name }

        /**
         * How full a widget's ring or bar is drawn from `at` until its next redraw a minute later:
         * where the class will be at that redraw, so the last drawing is full when the bell rings.
         */
        fun progress(at: Date, redrawMs: Long = 60_000): Double {
            val length = (end.time - start.time).toDouble()
            if (length <= 0) return 1.0
            return ((at.time + redrawMs - start.time) / length).coerceIn(0.0, 1.0)
        }
    }

    fun blocks(on: Date, zone: ZoneId = ZoneId.systemDefault()): List<Block> {
        val type = dayType(on, zone) ?: return emptyList()
        val local = on.toInstant().atZone(zone).toLocalDate()
        fun at(m: Int) = Date.from(local.atTime(LocalTime.of(m.coerceIn(0, 1439) / 60, m.coerceIn(0, 1439) % 60)).atZone(zone).toInstant())
        return type.ordered.map { Block(it.name, periods[it.name] ?: Period(), at(it.start), at(it.end)) }
    }

    /** Where the day stands at one moment. */
    data class Moment(val now: Block?, val next: Block?, val dayStart: Date?, val dayEnd: Date?) {
        val isOver: Boolean get() = dayEnd != null && now == null && next == null
    }

    fun moment(at: Date, zone: ZoneId = ZoneId.systemDefault()): Moment {
        val all = blocks(at, zone)
        return Moment(all.firstOrNull { !it.start.after(at) && at.before(it.end) }, all.firstOrNull { it.start.after(at) },
            all.firstOrNull()?.start, all.maxOfOrNull { it.end })
    }

    /** Every moment the picture changes today (each start and end) after `at`. */
    fun changes(after: Date, zone: ZoneId = ZoneId.systemDefault()): List<Date> =
        blocks(after, zone).flatMap { listOf(it.start, it.end) }.distinct().filter { it.after(after) }.sorted()

    // ── Editing it ───────────────────────────────────────────────────────

    /** A new, unnamed day type. The first one takes the given weekdays, so one day type is already a whole week. */
    fun addingDayType(weekdays: List<Int>, id: String = newId()): BellSchedule {
        val w = if (dayTypes.isEmpty()) week + weekdays.associateWith { id } else week
        return copy(dayTypes = dayTypes + DayType(id, ""), week = w)
    }

    /** Dropping a day type also drops it from the week and from today's switch. */
    fun removingDayType(id: String) = copy(
        dayTypes = dayTypes.filter { it.id != id },
        week = week.filterValues { it != id },
        today = today?.takeIf { it.dayType != id },
    )

    /** Rename one day type; nothing happens when it's gone. */
    fun namingDayType(id: String, name: String) = copy(dayTypes = dayTypes.map { if (it.id == id) it.copy(name = name) else it })

    /** One day type's periods in time order, every one (even a half-typed one that ends before it starts). */
    fun slots(inType: String): List<Slot> = dayTypes.firstOrNull { it.id == inType }?.slots?.sortedBy { it.start } ?: emptyList()

    fun settingSlots(slots: List<Slot>, inType: String) =
        copy(dayTypes = dayTypes.map { if (it.id == inType) it.copy(slots = slots.sortedBy { s -> s.start }) else it })

    /** Change one period wherever it is; nothing happens when it's gone. */
    fun updatingSlot(id: String, change: (Slot) -> Slot) =
        copy(dayTypes = dayTypes.map { d -> d.copy(slots = d.slots.map { if (it.id == id) change(it) else it }) })

    fun slot(id: String): Slot? = dayTypes.asSequence().flatMap { it.slots }.firstOrNull { it.id == id }

    /**
     * Rename a period. Its class, room and teacher go with it unless the new name already has its own,
     * and names no period uses any more are forgotten.
     */
    fun renamingSlot(id: String, to: String): BellSchedule {
        val old = slot(id)?.name ?: return this
        if (old == to) return this
        val s = updatingSlot(id) { it.copy(name = to) }
        val p = if (s.periods[to] == null && s.periods[old] != null) s.periods + (to to s.periods.getValue(old)) else s.periods
        val used = s.dayTypes.flatMap { d -> d.slots.map { it.name } }.toSet()
        return s.copy(periods = p.filterKeys { it in used })
    }

    /** Drop a period by name from every day type, with its class, room and teacher. */
    fun removingPeriod(name: String) =
        copy(dayTypes = dayTypes.map { d -> d.copy(slots = d.slots.filter { it.name != name }) }, periods = periods - name)

    fun removingSlot(id: String) = copy(dayTypes = dayTypes.map { d -> d.copy(slots = d.slots.filter { it.id != id }) })

    fun settingPeriod(name: String, p: Period) = copy(periods = periods + (name to p))

    /** Every slot name in use, in first-seen order. */
    val periodNames: List<String> get() = dayTypes.flatMap { d -> d.ordered.map { it.name } }.distinct()

    /** A period nobody has described yet takes its class from Schoology: "P3" → the class whose section says P3. */
    fun filled(from: List<Course>): BellSchedule {
        val names = dayTypes.flatMap { d -> d.slots.map { it.name } }.toSet()
        var p = periods
        from.forEachIndexed { i, c ->
            if (c.periodLabel in names && p[c.periodLabel] == null)
                p = p + (c.periodLabel to Period(title = c.shortTitle, teacher = teacher(c.sectionTitle), color = i))
        }
        return if (p == periods) this else copy(periods = p)
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        /** 1 = Sunday … 7 = Saturday. */
        fun weekday(d: LocalDate): Int = d.dayOfWeek.value % 7 + 1

        fun dayKey(d: Date, zone: ZoneId = ZoneId.systemDefault()): String = d.toInstant().atZone(zone).toLocalDate().toString()

        /** A new slot after the last one: five minutes later, as long as the last (50 minutes for the first, at 8:00). */
        fun nextSlot(after: List<Slot>, name: String): Slot {
            val last = after.maxByOrNull { it.end } ?: return Slot(name = name, start = 8 * 60, end = 8 * 60 + 50)
            val length = maxOf(last.end - last.start, 5)
            val start = minOf(last.end + 5, 24 * 60 - 5)
            return Slot(name = name, start = start, end = minOf(start + length, 24 * 60 - 1))
        }

        /** The next unused period name in a day: "P1", "P2"… with no upper limit. */
        fun nextName(slots: List<Slot>): String {
            val used = slots.map { it.name }.toSet()
            var n = 1
            while ("P$n" in used) n++
            return "P$n"
        }

        /** The periods Schoology names for the student's classes ("P1"…), in order. */
        fun classPeriods(classes: List<Course>): List<String> =
            classes.map { it.periodLabel }.filter { it.isNotEmpty() }.distinct()
                .sortedWith(compareBy({ it.filter { c -> !c.isDigit() } }, { it.filter(Char::isDigit).toIntOrNull() ?: 0 }))

        /** "1404: GarciaJ p4 T1" → "GarciaJ"; "" when the section doesn't name anyone. */
        fun teacher(sectionTitle: String): String {
            val name = Regex(""":\s*([A-Za-z][A-Za-z'’\-]+)""").find(sectionTitle)?.groupValues?.get(1) ?: return ""
            return if (Regex("""(?i)^p(?:er(?:iod)?)?\d*$""").matches(name)) "" else name
        }

        /** "8:30 am" from minutes after midnight. */
        fun clock(minutes: Int): String = Fmt.clock((minutes / 60).coerceIn(0, 23), minutes % 60)

        /** "8:30 am – 3:50 pm", or "No periods". */
        fun summary(slots: List<Slot>): String {
            val first = slots.firstOrNull() ?: return "No periods"
            return "${clock(first.start)} – ${clock(slots.maxOf { it.end })}"
        }

        /**
         * A pretend half hour for trying the widget (debug builds only): the student's own periods,
         * a few minutes each from `now`, every day, so the bell comes quickly. Starts in class with 3
         * minutes left; a passing period, lunch and two more classes follow.
         */
        fun test(from: BellSchedule, at: Date, zone: ZoneId = ZoneId.systemDefault()): BellSchedule {
            val t = at.toInstant().atZone(zone)
            val m = minOf(t.hour * 60 + t.minute, 24 * 60 - 32)
            val names = from.periodNames.filter { it !in listOf("Lunch", "Brunch", "Tutorial") }.take(4)
            val n = if (names.size == 4) names else listOf("P1", "P2", "P3", "P4")
            val day = DayType(name = "Widget test", slots = listOf(
                Slot(name = n[0], start = m - 8, end = m + 3), Slot(name = n[1], start = m + 4, end = m + 9),
                Slot(name = "Lunch", start = m + 9, end = m + 12), Slot(name = n[2], start = m + 13, end = m + 20),
                Slot(name = n[3], start = m + 21, end = m + 30),
            ))
            return BellSchedule(dayTypes = listOf(day), week = (1..7).associateWith { day.id }, periods = from.periods)
        }

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

/** Where the schedule is kept (the App Group on iOS): app prefs, which the widget reads in the same process. */
object BellStore {
    private const val KEY = "bellSchedule"
    private const val TEST_KEY = "bellScheduleTest"

    fun load(): BellSchedule = runCatching {
        BellSchedule.json.decodeFromString(BellSchedule.serializer(), Prefs.string(KEY) ?: return BellSchedule())
    }.getOrDefault(BellSchedule())

    fun save(s: BellSchedule) = Prefs.set(KEY, BellSchedule.json.encodeToString(BellSchedule.serializer(), s))

    // A widget test (BellSchedule.test) runs beside the real schedule, never in place of it.
    @Serializable
    private data class Test(val schedule: BellSchedule, val until: Long)

    private val test: Test?
        get() = runCatching { BellSchedule.json.decodeFromString(Test.serializer(), Prefs.string(TEST_KEY) ?: return null) }.getOrNull()

    /** When the running test ends, null with none. */
    val testUntil: Date? get() = test?.until?.takeIf { it > System.currentTimeMillis() }?.let(::Date)

    fun startTest(s: BellSchedule, until: Date) =
        Prefs.set(TEST_KEY, BellSchedule.json.encodeToString(Test.serializer(), Test(s, until.time)))

    fun stopTest() = Prefs.set(TEST_KEY, null)

    /** What the widget shows: the test while one runs, else the real schedule. */
    fun widgetSchedule(now: Date = Date()): BellSchedule = test?.takeIf { it.until > now.time }?.schedule ?: load()
}
