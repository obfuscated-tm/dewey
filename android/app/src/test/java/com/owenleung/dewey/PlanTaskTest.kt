package com.owenleung.dewey

import com.owenleung.dewey.store.CalendarSync
import com.owenleung.dewey.store.Code39
import com.owenleung.dewey.store.ItemState
import com.owenleung.dewey.store.ItemType
import com.owenleung.dewey.store.PlanHidden
import com.owenleung.dewey.store.PlanLogic
import com.owenleung.dewey.store.PlanLogic.Bucket
import com.owenleung.dewey.store.StudentID
import com.owenleung.dewey.store.TaskParse
import com.owenleung.dewey.store.TodoLogic
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Date

/** Plan's sections and order (PlanLogic), and the student's own tasks on To Do (iOS PlanTests). */
class PlanTest {
    private val now = Date.from(java.time.Instant.parse("2026-10-20T15:00:00Z"))
    private fun day(d: Int) = now.time + d * 86_400_000L
    private fun ids(e: List<PlanLogic.Entry>) = e.map { it.id }

    @Test fun unplacedGoByDue() {
        val e = listOf(PlanLogic.Entry("c", null, day(3)), PlanLogic.Entry("a", null, day(-1)), PlanLogic.Entry("b", null, day(1)))
        assertEquals(listOf("a", "b", "c"), ids(PlanLogic.order(e)))
    }

    @Test fun placedKeepTheStudentsOrder() {
        val e = listOf(PlanLogic.Entry("x", 2.0, day(1)), PlanLogic.Entry("y", 1.0, day(5)))
        assertEquals(listOf("y", "x"), ids(PlanLogic.order(e)))
    }

    @Test fun newWorkSlotsInBeforeLaterPlacedWork() {
        val e = listOf(PlanLogic.Entry("y", 1.0, day(5)), PlanLogic.Entry("x", 2.0, day(2)),
            PlanLogic.Entry("new1", null, day(1)), PlanLogic.Entry("new6", null, day(6)), PlanLogic.Entry("nodate", null, null))
        assertEquals(listOf("new1", "y", "x", "new6", "nodate"), ids(PlanLogic.order(e)))
    }

    @Test fun dragWithinAndAcrossSections() {
        val rows = PlanLogic.rows(listOf("a", "b"), listOf("c"), listOf("d"))
        // [H todo, a, b, H later, c, H done, d]: b to the top of To do.
        val up = PlanLogic.moved(rows, 2, 1)
        assertEquals(Bucket.TODO, up["b"]?.bucket); assertEquals(1.0, up["b"]!!.rank, 0.0)
        assertEquals(2.0, up["a"]!!.rank, 0.0)
        // a dragged under the Later heading.
        val down = PlanLogic.moved(rows, 1, 4)
        assertEquals(Bucket.LATER, down["a"]?.bucket); assertEquals(1.0, down["a"]!!.rank, 0.0)
        assertEquals(2.0, down["c"]!!.rank, 0.0)
        // c dragged to the end, into Done.
        assertEquals(Bucket.DONE, PlanLogic.moved(rows, 4, 7)["c"]?.bucket)
        // Dropped above the first heading: still To do.
        assertEquals(Bucket.TODO, PlanLogic.moved(rows, 4, 0)["c"]?.bucket)
        // Swipe: to the top of Later; TalkBack nudge across a heading.
        assertEquals(Bucket.LATER, PlanLogic.moved(rows, "a", Bucket.LATER)["a"]?.bucket)
        assertEquals(Bucket.LATER, PlanLogic.nudged(rows, "b", 1)["b"]?.bucket)
    }

    @Test fun dropOnCollapsedSection() {
        val rows = PlanLogic.rows(listOf("a", "b"), emptyList(), listOf("d"))
        val hidden: PlanHidden = mapOf(Bucket.LATER to listOf("c", "e"))
        val p = PlanLogic.moved(rows, 1, 4, hidden)
        assertEquals(Bucket.LATER, p["a"]?.bucket)
        assertEquals(1.0, p["a"]!!.rank, 0.0)
        assertEquals(2.0, p["c"]!!.rank, 0.0)
        assertEquals(3.0, p["e"]!!.rank, 0.0)
        assertEquals(1.0, p["b"]!!.rank, 0.0)
        assertEquals(3.0, PlanLogic.moved(rows, "b", Bucket.LATER, hidden)["e"]!!.rank, 0.0)
        // The grip never lifts work above To do's heading.
        assertEquals(rows, PlanLogic.nudging(rows, "a", -1))
    }

    @Test fun whereWorkGoesByDueDate() {
        val today = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        fun at(d: Long) = Date.from(today.plusDays(d).atTime(20, 0).atZone(ZoneId.systemDefault()).toInstant())
        assertEquals(Bucket.TODO, PlanLogic.autoBucket(at(-2), true, false, false, now))
        assertEquals(Bucket.TODO, PlanLogic.autoBucket(at(0), false, false, false, now))
        assertEquals(Bucket.TODO, PlanLogic.autoBucket(at(1), false, false, false, now))
        assertEquals(Bucket.LATER, PlanLogic.autoBucket(at(2), false, false, false, now))
        assertEquals(Bucket.TODO, PlanLogic.autoBucket(at(2), false, true, false, now))
        assertEquals(Bucket.TODO, PlanLogic.autoBucket(at(3), false, true, false, now))
        assertEquals(Bucket.LATER, PlanLogic.autoBucket(at(4), false, true, false, now))
        assertEquals(Bucket.TODO, PlanLogic.autoBucket(null, false, false, true, now))
        assertEquals(Bucket.LATER, PlanLogic.autoBucket(null, false, false, false, now))
    }

    @Test fun ownTasksStayUntilDoneThenUntilTheDayEnds() {
        fun s(done: Boolean, today: Boolean) = TodoLogic.Snapshot(true, done, false, false, false, false, mine = true, finishedToday = today)
        assertTrue(TodoLogic.isVisible(s(done = false, today = false)))
        assertTrue(TodoLogic.isVisible(s(done = true, today = true)))
        assertFalse(TodoLogic.isVisible(s(done = true, today = false)))
        assertTrue(ItemState.isMine("mine-ABC"))
        assertFalse(ItemState.isMine("8610387535"))
    }

    @Test fun neoPlanOpsSurviveSaving() {
        val ops = listOf(AppModel.NeoPlanOp.Work(true), AppModel.NeoPlanOp.TurnedIn(false), AppModel.NeoPlanOp.Type("exam"))
        val s = ListSerializer(AppModel.NeoPlanOp.serializer())
        assertEquals(ops, Json.decodeFromString(s, Json.encodeToString(s, ops)))
    }
}

/** What the Dewey calendar holds (CalendarSync.spec), the Code 39 barcode and the student number. */
class CalendarAndIDTest {
    private val zone = ZoneId.systemDefault()
    private fun at(h: Int, m: Int, days: Long = 1): Date =
        Date.from(LocalDate.now(zone).plusDays(days).atTime(h, m).atZone(zone).toInstant())
    private fun input(due: Date?, test: Boolean = false, finished: Boolean = false) =
        CalendarSync.Input("1", "Lab 4", "Chemistry H", due, test, finished)

    @Test fun eventShapes() {
        val now = Date()
        val midnight = CalendarSync.spec(input(at(23, 59)), now)!!
        assertTrue(midnight.allDay)
        assertEquals(LocalDate.now(zone).plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(), midnight.start)
        val timed = CalendarSync.spec(input(at(8, 30)), now)!!
        assertFalse(timed.allDay)
        assertEquals(at(8, 30).time, timed.end)
        assertEquals(at(8, 0).time, timed.start)
        assertEquals("Chemistry H", timed.location)
        val test = CalendarSync.spec(input(at(10, 5), test = true), now)!!
        assertTrue(test.allDay)
        assertEquals("Test: Lab 4", test.title)
    }

    @Test fun whatStaysOut() {
        val now = Date()
        assertNull(CalendarSync.spec(input(null), now))
        assertNull(CalendarSync.spec(input(at(9, 0), finished = true), now))
        assertNull(CalendarSync.spec(input(at(9, 0, 120)), now))
        assertNull(CalendarSync.spec(input(at(9, 0, -30)), now))
    }

    @Test fun code39() {
        val m = Code39.modules("1234567")!!
        // 9 characters of 15 modules, 8 gaps, two quiet zones of 10.
        assertEquals(9 * 15 + 8 + 20, m.size)
        assertFalse(m.take(Code39.QUIET).contains(true))
        assertTrue(m[Code39.QUIET])
        assertNull(Code39.modules("12A4"))
        assertNull(Code39.modules(""))
    }

    @Test fun studentIDDigitsOnly() {
        assertEquals("5251103", StudentID.clean(" 525-1103x"))
        assertNull(StudentID.warning("5251103"))
        assertNull(StudentID.warning(""))
        assertNotNull(StudentID.warning("52511"))
        assertNotNull(StudentID.warning("52511030"))
    }
}

/** Quick add: the day, time, class and kind read from what the student types (TaskParse). */
class TaskParseTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    /** Wednesday, Oct 21 2026, 4 pm. */
    private val now = Date.from(LocalDateTime.of(2026, 10, 21, 16, 0).atZone(zone).toInstant())
    private val classes = listOf(
        TaskParse.ClassName("chem", "Chemistry H - 12345: P3"),
        TaskParse.ClassName("bio", "AP Biology - 23456: P2"),
        TaskParse.ClassName("pre", "Pre-Calculus H - 34567: P1"),
        TaskParse.ClassName("us", "US History - 45678: P4"),
    )
    private fun day(d: Int) = LocalDate.of(2026, 10, d)
    private fun parse(s: String) = TaskParse.parse(s, now, classes, zone, WeekFields.SUNDAY_START)

    @Test fun daysAndTimesLeaveTheTitle() {
        val r = parse("Chem lab report fri after school")
        assertEquals("Chem lab report", r.title)
        assertEquals(day(23), r.day)
        assertEquals(TaskParse.afterSchool, r.time)
        assertEquals("chem", r.sectionId)
    }

    @Test fun dayWords() {
        assertEquals(day(22), parse("essay tmrw").day)
        assertEquals("essay", parse("essay due tomorrow").title)
        assertEquals(TaskParse.TONIGHT, parse("read ch 5 tonight").time)
        assertEquals(day(21), parse("quiz wed").day)          // today is Wednesday
        assertEquals(day(28), parse("quiz next wed").day)
        assertEquals(day(26), parse("quiz next mon").day)     // next week's Monday, not Nov 2
        assertEquals(day(24), parse("vocab in 3 days").day)
        assertEquals(day(30), parse("project 10/30").day)
        assertEquals(day(30), parse("project oct 30").day)
        assertNull(parse("SAT practice").day)                  // the test, not Saturday
    }

    @Test fun times() {
        assertEquals(TaskParse.Clock(8, 0), parse("call 8am").time)
        assertEquals(TaskParse.Clock(15, 30), parse("lab 3:30").time)
        assertEquals(TaskParse.Clock(15, 0), parse("meet at 3").time)
        assertNull(parse("do 2 problems").time)
        assertEquals("do 2 problems", parse("do 2 problems").title)
    }

    @Test fun classAndKind() {
        assertEquals("bio", parse("bio quiz").sectionId)
        assertEquals(ItemType.EXAM, parse("bio quiz").type)
        assertEquals("pre", parse("precalc hw").sectionId)
        assertEquals(ItemType.ASSIGNMENT, parse("precalc hw").type)
        assertEquals("us", parse("history notes").sectionId)
        assertNull(parse("study for finals").sectionId)
    }

    @Test fun due() {
        // A day alone: by the end of it. A time alone: today, or tomorrow once it's passed.
        fun local(d: Date) = d.toInstant().atZone(zone)
        assertEquals(23, local(TaskParse.due(day(23), null, now, zone)!!).hour)
        assertEquals(22, local(TaskParse.due(null, TaskParse.beforeSchool, now, zone)!!).dayOfMonth)
        assertEquals(21, local(TaskParse.due(null, TaskParse.TONIGHT, now, zone)!!).dayOfMonth)
        assertNull(TaskParse.due(null, null, now, zone))
    }
}
