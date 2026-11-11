package com.owenleung.dewey

import com.owenleung.dewey.store.TurnInSite
import com.owenleung.dewey.ui.assignment.countdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

/** Where off-Schoology work is turned in: a saved class site beats the first-link guess (iOS TurnInSiteTests). */
class TurnInSiteTest {
    private val md = "Read [the docs](https://docs.oracle.com/javase/8/docs/api/String.html), watch [this](https://www.youtube.com/watch?v=1), then do [Lab 10.7](https://apcs.tinocs.com/lab/10.7)."

    @Test fun guessIsFirstOutsideLink() {
        assertEquals("docs.oracle.com", TurnInSite.pick(md, null)?.let(TurnInSite::host))
    }

    @Test fun savedSiteWinsOverReferenceLinkListedFirst() {
        assertEquals("https://apcs.tinocs.com/lab/10.7", TurnInSite.pick(md, "https://apcs.tinocs.com"))
    }

    @Test fun savedSiteNotLinkedOpensTheSite() {
        assertEquals("https://apcs.tinocs.com", TurnInSite.pick("Use [the reference](https://docs.oracle.com/x).", "https://apcs.tinocs.com"))
    }

    @Test fun noLinksMeansNoSiteEvenWhenSaved() {
        assertNull(TurnInSite.pick("Hand in the worksheet in class.", "https://apcs.tinocs.com"))
    }

    @Test fun hostsSkipSchoologyGoogleYouTubeAndWww() {
        val m = "[a](https://fuhsd.schoology.com/x) [b](https://docs.google.com/y) [c](https://www.lab.example.org/1) [d](https://lab.example.org/2)"
        assertEquals(listOf("lab.example.org"), TurnInSite.hosts(m))
    }
}

/** The Due row's "5 h 12 min left": only in the last 12 hours of work still owed. */
class DueCountdownTest {
    private val now = Date(1_800_000_000_000)
    private fun inMin(m: Long) = Date(now.time + m * 60_000)

    @Test fun showsHoursAndMinutes() = assertEquals("5 h 12 min left", countdown(inMin(312), now, owed = true))
    @Test fun underAnHour() = assertEquals("45 min left", countdown(inMin(45), now, owed = true))
    @Test fun roundsUpPartMinutes() = assertEquals("1 min left", countdown(Date(now.time + 10_000), now, owed = true))
    @Test fun notBeforeTwelveHours() = assertNull(countdown(inMin(12 * 60 + 1), now, owed = true))
    @Test fun notWhenHandedIn() = assertNull(countdown(inMin(30), now, owed = false))
    @Test fun notPastDue() = assertNull(countdown(inMin(-5), now, owed = true))
    @Test fun noDueDate() = assertNull(countdown(null, now, owed = true))
}
