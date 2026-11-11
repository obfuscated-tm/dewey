package com.owenleung.dewey

import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.ui.classes.FileKinds
import com.owenleung.dewey.ui.classes.MaterialStatus
import com.owenleung.dewey.ui.classes.word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

/** The same cases as ios/Tests MaterialStatusTests. */
class MaterialStatusTest {
    private val now = Date(1_760_000_000_000L)
    private val past = Date(now.time - 3 * 86_400_000L)
    private val future = Date(now.time + 2 * 86_400_000L)

    @Test fun scoreWinsOverLateAndKeepsTheDate() {
        val l = MaterialStatus.line("18 / 20", late = true, due = past, submitted = true, turnedIn = true, now = now)
        assertEquals(MaterialStatus.Line(Fmt.short(past) + " · 18 / 20"), l)
        assertEquals("18 / 20", MaterialStatus.line("18 / 20", false, null, false, false, now)?.text)
    }

    @Test fun lateShowsOnlyTheDate() {
        val l = MaterialStatus.line(null, late = true, due = past, submitted = false, turnedIn = false, now = now)
        assertEquals("Late · " + Fmt.short(past), l?.text)
        assertEquals(true, l?.late)
    }

    @Test fun submittedThenTurnedInWithTheDate() {
        val day = Fmt.short(future)
        assertEquals("$day · Submitted", MaterialStatus.line(null, false, future, submitted = true, turnedIn = true, now = now)?.text)
        assertEquals("$day · Turned in", MaterialStatus.line(null, false, future, submitted = false, turnedIn = true, now = now)?.text)
        assertEquals("Turned in", MaterialStatus.line(null, false, null, submitted = false, turnedIn = true, now = now)?.text)
    }

    @Test fun dueAndNothing() {
        assertEquals("Due " + Fmt.due(future, now), MaterialStatus.line(null, false, future, false, false, now)?.text)
        // Past, not late by Dewey's rule (work Schoology dropped): the date alone.
        assertEquals(Fmt.short(past), MaterialStatus.line(null, false, past, false, false, now)?.text)
        assertNull(MaterialStatus.line(null, false, null, false, false, now))
        assertNull(MaterialStatus.line("", false, null, false, false, now))
    }

    @Test fun lateWithoutDueFallsThrough() {
        assertEquals("Submitted", MaterialStatus.line(null, late = true, due = null, submitted = true, turnedIn = false, now = now)?.text)
    }

    @Test fun fileWords() {
        assertEquals("PDF", FileKinds.word("Notes.pdf", "/x"))
        assertEquals("Slides", FileKinds.word("a", "/f/a.pptx"))
        assertEquals("Doc", FileKinds.word("a", "/f/a.docx"))
    }
}
