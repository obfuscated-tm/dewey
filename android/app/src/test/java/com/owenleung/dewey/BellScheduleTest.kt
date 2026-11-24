package com.owenleung.dewey

import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.store.BellSchedule
import com.owenleung.dewey.store.BellTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/** ios/Tests/BellScheduleTests.swift. */
class BellScheduleTest {
    private val zone = ZoneId.of("America/Los_Angeles")

    /** 2026-11-16 is a Monday. */
    private fun at(day: Int, h: Int, m: Int): Date = Date.from(LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant())

    private fun schedule(): BellSchedule {
        val regular = BellSchedule.DayType(name = "Regular", slots = listOf(
            BellSchedule.Slot(name = "P2", start = 9 * 60, end = 9 * 60 + 50), BellSchedule.Slot(name = "P1", start = 8 * 60, end = 8 * 60 + 50)))
        val block = BellSchedule.DayType(name = "Block", slots = listOf(BellSchedule.Slot(name = "P3", start = 8 * 60, end = 9 * 60 + 30)))
        return BellSchedule(dayTypes = listOf(regular, block), week = mapOf(2 to regular.id, 3 to block.id),
            periods = mapOf("P1" to BellSchedule.Period(title = "Chemistry H", room = "B12")))
    }

    @Test fun nowAndNextInTimeOrder() {
        val m = schedule().moment(at(16, 8, 10), zone)
        assertEquals("Chemistry H", m.now?.title)
        assertEquals("B12", m.now?.period?.room)
        assertEquals("P2", m.next?.title) // a period nobody described shows its own name
        assertEquals(at(16, 9, 50), m.dayEnd)
    }

    @Test fun passingPeriodAndAfterSchool() {
        val s = schedule()
        val gap = s.moment(at(16, 8, 55), zone)
        assertNull(gap.now)
        assertEquals("P2", gap.next?.name)
        assertTrue(s.moment(at(16, 15, 0), zone).isOver)
        // The end of a period is not in it.
        assertNull(s.moment(at(16, 8, 50), zone).now)
    }

    @Test fun weekdayPicksTheDayTypeAndWeekendHasNone() {
        val s = schedule()
        assertEquals("P3", s.moment(at(17, 8, 10), zone).now?.name) // Tuesday: Block
        val sunday = s.moment(at(15, 8, 10), zone)
        assertNull(sunday.dayEnd)
        assertFalse(sunday.isOver)
    }

    @Test fun todayIsDifferentOnlyForThatDate() {
        var s = schedule()
        s = s.copy(today = BellSchedule.TodayOverride("2026-11-16", s.dayTypes[1].id))
        assertEquals("P3", s.moment(at(16, 8, 10), zone).now?.name)
        s = s.copy(today = BellSchedule.TodayOverride("2026-11-16", null)) // no school
        assertNull(s.moment(at(16, 8, 10), zone).dayEnd)
        s = s.copy(today = BellSchedule.TodayOverride("2026-11-15", null)) // yesterday's switch has worn off
        assertEquals("P1", s.moment(at(16, 8, 10), zone).now?.name)
    }

    @Test fun changesAreEveryBellStillToCome() {
        assertEquals(listOf(at(16, 8, 50), at(16, 9, 0), at(16, 9, 50)), schedule().changes(at(16, 8, 10), zone))
    }

    @Test fun nextSlotFollowsTheLastOne() {
        val first = BellSchedule.nextSlot(emptyList(), "P1")
        assertEquals(listOf(480, 530), listOf(first.start, first.end))
        val second = BellSchedule.nextSlot(listOf(first), "P2")
        assertEquals(listOf(535, 585), listOf(second.start, second.end))
        assertEquals("P3", BellSchedule.nextName(listOf(first, second)))
    }

    @Test fun addingAndRemovingDayTypes() {
        var s = BellSchedule().addingDayType(listOf(2, 3, 4, 5, 6), "a")
        assertEquals(listOf(""), s.dayTypes.map { it.name }) // the student names it; nothing is called Regular
        assertEquals(setOf("a"), s.week.values.toSet()) // the first one is the whole week
        s = s.addingDayType(listOf(2, 3, 4, 5, 6), "b")
        assertEquals("a", s.week[3]) // a second one takes no days by itself
        s = s.copy(week = s.week + (3 to "b"), today = BellSchedule.TodayOverride("2026-11-16", "b"))
        s = s.removingDayType("b")
        assertEquals(listOf("a"), s.dayTypes.map { it.id })
        assertNull(s.week[3])
        assertNull(s.today)
        // Gone is gone: nothing below brings it back.
        s = s.namingDayType("b", "Block").settingSlots(listOf(BellSchedule.Slot(name = "P1", start = 480, end = 530)), "b")
        assertEquals(emptyList<BellSchedule.Slot>(), s.slots("b"))
        assertEquals(1, s.dayTypes.size)
    }

    @Test fun renamingAPeriodKeepsItsClass() {
        var s = schedule()
        val p1 = s.slots(s.dayTypes[0].id)[0].id
        s = s.renamingSlot(p1, "Zero")
        assertEquals("Chemistry H", s.periods["Zero"]?.title)
        assertNull(s.periods["P1"]) // nothing is called P1 any more
        // A name that already has a class keeps its own.
        s = s.settingPeriod("P3", BellSchedule.Period(title = "Physics"))
        s = s.renamingSlot(p1, "P3")
        assertEquals("Physics", s.periods["P3"]?.title)
        // A period that was deleted is left alone.
        s = s.removingSlot(p1).renamingSlot(p1, "Gone").updatingSlot(p1) { it.copy(start = 0) }
        assertNull(s.slot(p1))
        assertEquals(listOf("P2"), s.slots(s.dayTypes[0].id).map { it.name })
    }

    @Test fun asManyPeriodsAsYouLikeAndDeletingOne() {
        val slots = mutableListOf<BellSchedule.Slot>()
        repeat(14) { slots += BellSchedule.nextSlot(slots, BellSchedule.nextName(slots)) }
        assertEquals(14, slots.map { it.name }.toSet().size) // P13, P14: no two share a name
        val s = schedule().removingPeriod("P1")
        assertNull(s.periods["P1"])
        assertEquals(listOf("P2", "P3"), s.periodNames)
    }

    @Test fun fremontUnionTemplates() {
        assertEquals(5, BellTemplate.all.size)
        for (t in BellTemplate.all) check(t.schedule, t.school)
        val mv = BellTemplate.all.first { it.school == "Monta Vista High" }.schedule
        assertEquals("Brunch", mv.moment(at(16, 11, 42), zone).now?.name) // until 11:45 there
    }

    private fun check(s: BellSchedule, school: String) {
        assertEquals(3, s.dayTypes.size)
        assertEquals(s.week[3], s.week[5]) // Tuesday and Thursday share one
        assertEquals(s.week[4], s.week[6])
        assertNull(s.week[1])
        // Wednesday 1:00 pm is lunch, P6 next at 1:35.
        val m = s.moment(at(18, 13, 0), zone)
        assertEquals("Lunch", m.now?.name)
        assertEquals(at(18, 13, 35), m.next?.start)
        // Every period ends after it starts and none overlap, every day.
        for (d in s.dayTypes) {
            val o = d.ordered
            assertEquals("$school ${d.name}", d.slots.size, o.size)
            for ((x, y) in o.zipWithNext()) assertTrue("$school ${d.name} ${x.name}", x.end <= y.start)
        }
        assertEquals((1..7).map { "P$it" }, s.periodNames.filter { it.startsWith("P") }.sorted())
    }

    @Test fun widgetTestDay() {
        val now = at(15, 14, 7) // a Sunday afternoon
        val t = BellSchedule.test(schedule(), now, zone)
        val m = t.moment(now, zone)
        assertEquals(at(15, 14, 10), m.now?.end) // in class, 3 minutes left
        assertEquals(at(15, 14, 11), m.next?.start)
        assertEquals("Lunch", t.moment(at(15, 14, 17), zone).now?.name)
        assertTrue(t.moment(at(15, 14, 40), zone).isOver)
        assertEquals("Chemistry H", t.periods["P1"]?.title) // the student's own classes
        assertEquals(5, t.dayTypes[0].ordered.size)
    }

    @Test fun theRingIsFullWhenTheBellRings() {
        val p1 = schedule().moment(at(16, 8, 0), zone).now
        assertNotNull(p1) // 8:00–8:50
        assertEquals(1.0, p1!!.progress(at(16, 8, 49)), 0.0) // the last drawing before the bell
        assertEquals(1.0 / 50, p1.progress(at(16, 8, 0)), 0.0001)
        assertEquals(0.5, p1.progress(at(16, 8, 24)), 0.0001)
        // A 5-minute test period ends full too, not at 4/5.
        val t = BellSchedule.test(schedule(), at(15, 14, 7), zone)
        val p2 = t.moment(at(15, 14, 11), zone).now!! // 14:11–14:16
        assertEquals(1.0, p2.progress(at(15, 14, 15)), 0.0)
    }

    @Test fun teacherFromTheSectionTitle() {
        assertEquals("GarciaJ", BellSchedule.teacher("1404: GarciaJ p4 T1"))
        assertEquals("", BellSchedule.teacher("Section 2"))
        assertEquals("", BellSchedule.teacher("1404: P4"))
    }

    @Test fun classesFillPeriodsOnlyOnce() {
        val classes = listOf(Course("1", "Chemistry H - 1234", "1404: GarciaJ P1"), Course("2", "Precalc - 2345", "Per 2"))
        val s = schedule().filled(classes)
        assertEquals("Chemistry H", s.periods["P1"]?.title) // already set: left alone (room stays)
        assertEquals("B12", s.periods["P1"]?.room)
        assertEquals("Precalc", s.periods["P2"]?.title)
        assertEquals(1, s.periods["P2"]?.color)
        assertEquals(listOf("P1", "P2"), BellSchedule.classPeriods(classes.reversed()))
    }

    @Test fun savedCopyRoundTrips() {
        val s = schedule().copy(today = BellSchedule.TodayOverride("2026-11-16", null))
        val text = BellSchedule.json.encodeToString(BellSchedule.serializer(), s)
        assertEquals(s, BellSchedule.json.decodeFromString(BellSchedule.serializer(), text))
    }
}
