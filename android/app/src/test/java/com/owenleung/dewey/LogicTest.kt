package com.owenleung.dewey

import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.engine.CourseGrade
import com.owenleung.dewey.engine.EngineJson
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.Inbox
import com.owenleung.dewey.store.ItemType
import com.owenleung.dewey.store.Reminders
import com.owenleung.dewey.store.TodoLogic
import com.owenleung.dewey.web.WebConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

class LogicTest {
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Date =
        Date.from(LocalDateTime.of(y, mo, d, h, mi).atZone(ZoneId.systemDefault()).toInstant())

    @Test fun guessType() {
        assertEquals(ItemType.EXAM, TodoLogic.guessType("Unit 3 Test", null, false))
        assertEquals(ItemType.EXAM, TodoLogic.guessType("Chapter 4", "Summative Assessments", false))
        assertEquals(ItemType.EXAM, TodoLogic.guessType("Anything", null, true))
        assertEquals(ItemType.ASSIGNMENT, TodoLogic.guessType("Lab 4 writeup", "Labs", false))
        assertEquals(ItemType.ASSIGNMENT, TodoLogic.guessType("Contest entry", null, false))
    }

    @Test fun signedImpact() {
        assertEquals("+0.24%", Fmt.signed(0.2449))
        assertEquals("−1.10%", Fmt.signed(-1.1))
        // A change that rounds to nothing is "+0.00%", never "−0.00%".
        assertEquals("+0.00%", Fmt.signed(-0.004))
        assertEquals("+0.00%", Fmt.signed(0.0))
        assertTrue(Fmt.unmoved(-0.004))
        assertFalse(Fmt.unmoved(0.006))
    }

    @Test fun visibility() {
        fun s(inS: Boolean, done: Boolean = false, graded: Boolean = false, dismissed: Boolean = false) =
            TodoLogic.Snapshot(inS, done, false, false, graded, dismissed)
        assertTrue(TodoLogic.isVisible(s(true, done = true)))
        assertFalse(TodoLogic.isVisible(s(false, done = true)))
        assertTrue(TodoLogic.isVisible(s(false)))
        assertFalse(TodoLogic.isVisible(s(true, graded = true)))
        assertFalse(TodoLogic.isVisible(s(true, dismissed = true)))
    }

    @Test fun buckets() {
        val now = at(2026, 10, 14, 12)
        assertEquals(TodoLogic.Bucket.OVERDUE, TodoLogic.bucket(at(2026, 10, 13, 23), now))
        assertEquals(TodoLogic.Bucket.TODAY, TodoLogic.bucket(at(2026, 10, 14, 23, 59), now))
        assertEquals(TodoLogic.Bucket.WEEK, TodoLogic.bucket(at(2026, 10, 20, 8), now))
        assertEquals(TodoLogic.Bucket.LATER, TodoLogic.bucket(at(2026, 10, 21, 8), now))
        assertEquals(TodoLogic.Bucket.LATER, TodoLogic.bucket(null, now))
        assertEquals(1, TodoLogic.daysLate(at(2026, 10, 13, 23), now))
        assertNull(TodoLogic.daysLate(at(2026, 10, 15, 8), now))
        assertTrue(TodoLogic.isOlder(at(2026, 7, 1, 8), now))
    }

    @Test fun planDueReminders() {
        val now = at(2026, 10, 14, 12)
        val hw = Reminders.DueInput("1", "Essay", "English", at(2026, 10, 16, 23, 59), isTest = false, hasDropbox = true)
        val test = Reminders.DueInput("2", "Unit Test", "Chemistry", at(2026, 10, 16, 8), isTest = true, hasDropbox = false)
        val p = Reminders.plan(listOf(hw, test), Reminders.Settings(evening = true, hour = 19), now)
        assertEquals(listOf("due-1", "test-2", "soon-1").sorted(), p.map { it.id }.sorted())
        assertEquals(at(2026, 10, 15, 19).time, p.first { it.id == "test-2" }.fire)
        assertEquals(at(2026, 10, 16, 21, 59).time, p.first { it.id == "soon-1" }.fire)
        // Done homework still gets the 2-hour one while it's not submitted; studied test gets none.
        val p2 = Reminders.plan(listOf(hw.copy(done = true), test.copy(studied = true)), Reminders.Settings(evening = true), now)
        assertEquals(listOf("soon-1"), p2.map { it.id })
        // Morning summary only on days with something due.
        val p3 = Reminders.plan(listOf(hw, test), Reminders.Settings(soon = false, testNight = false, morning = true, morningHour = 7), now)
        assertEquals(1, p3.size)
        assertTrue(p3[0].title, p3[0].title.contains("1 due today") && p3[0].title.contains("test today"))
    }

    @Test fun countdown() {
        val now = at(2026, 10, 14, 12)
        assertEquals("Test today", Reminders.testCountdown(at(2026, 10, 14, 15), now))
        assertEquals("Test tomorrow", Reminders.testCountdown(at(2026, 10, 15, 8), now))
        assertEquals("Test in 3 days", Reminders.testCountdown(at(2026, 10, 17, 8), now))
        assertNull(Reminders.testCountdown(at(2026, 10, 14, 8), now))
    }

    @Test fun gradeAlert() {
        val one = listOf(CourseGrade.Item("1", title = "Lab 4", grade = "18 / 20"))
        val body = Reminders.gradeAlertBody(one, 92.1, 92.6)
        assertTrue(body, body.startsWith("Lab 4: 18 / 20 · ") && body.contains("→"))
        val many = (1..5).map { CourseGrade.Item("$it", title = "Q$it", grade = "1 / 1") }
        val b2 = Reminders.gradeAlertBody(many, 90.0, 90.01)
        assertTrue(b2, b2.contains("+2 more") && b2.contains("Class: still"))
    }

    @Test fun inboxKind() {
        val classes = listOf(Course("1", "AP Biology - 1101", "RiveraM p2 T1"))
        assertEquals(Inbox.Companion.Kind.TEACHER, Inbox.kind(null, classes))
        assertEquals(Inbox.Companion.Kind.TEACHER, Inbox.kind("AP Biology - 1101: RiveraM p2 T1", classes))
        assertEquals(Inbox.Companion.Kind.SCHOOL, Inbox.kind("School Hub: Gr10", classes))
    }

    @Test fun camelKeys() {
        assertEquals("sectionId", EngineJson.camelKey("section_id"))
        assertEquals("bodyMd", EngineJson.camelKey("body_md"))
        assertEquals("_private", EngineJson.camelKey("_private"))
        assertEquals("already", EngineJson.camelKey("already"))
    }

    @Test fun chromeUserAgent() {
        val wv = "Mozilla/5.0 (Linux; Android 15; Pixel 8; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.0.0 Mobile Safari/537.36"
        assertEquals("Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
            WebConfig.chromeUserAgent(wv))
    }

    @Test fun courseNames() {
        val c = Course("1", "Chemistry H - 12345", "Period 3")
        assertEquals("P3", c.periodLabel)
        assertTrue(c.isClass)
        assertFalse(Course("2", "School Hub", "Gr10").isClass)
    }

    @Test fun showsLate() {
        fun late(t: ItemType, quiz: Boolean = false, done: Boolean = false, turnedIn: Boolean = false, submitted: Boolean = false) =
            TodoLogic.showsLate(t, quiz, done, turnedIn, submitted)
        assertTrue(late(ItemType.ASSIGNMENT))
        assertFalse(late(ItemType.ASSIGNMENT, done = true))
        assertFalse(late(ItemType.ASSIGNMENT, turnedIn = true))
        assertFalse(late(ItemType.ASSIGNMENT, submitted = true))
        assertFalse(late(ItemType.EXAM))                    // a test on paper: nothing to hand in after it
        assertTrue(late(ItemType.EXAM, quiz = true))        // an online quiz not taken yet
        assertFalse(late(ItemType.EXAM, quiz = true, submitted = true))
    }
}
