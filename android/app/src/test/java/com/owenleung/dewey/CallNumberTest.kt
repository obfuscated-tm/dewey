package com.owenleung.dewey

import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.store.CallNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The same cases as ios/Tests CallNumberTests. */
class CallNumberTest {
    @Test fun clearNamesGetTheirNumber() {
        val cases = mapOf(
            "AP Biology" to "570", "AP Comp Sci A" to "005", "AP Physics 1" to "530", "Algebra 2_Trig" to "512",
            "World History" to "909", "US History" to "973", "APUSH" to "973", "English 10" to "820", "Spanish 2" to "460",
            "Chemistry H" to "540", "AP Spanish Literature" to "860", "AP Lang" to "808", "AP Lit" to "820",
            "Computer Science Principles" to "005", "AP CSP" to "005", "Political Science" to "320", "AP Gov" to "320",
            "Economics" to "330", "AP Psych" to "150", "Concert Band" to "780", "Ceramics 1" to "738", "PE 9" to "796",
            "Physical Education" to "796", "Health" to "613", "Intro to Engineering Design" to "620", "Biotech 1" to "660",
            "Geometry H" to "516", "AP Calculus BC" to "515", "Precalculus" to "515", "AP Statistics" to "519",
            "Integrated Math 2" to "510", "ELD 2" to "420", "AP Euro" to "940", "Yearbook" to "070", "French 3" to "440",
        )
        for ((name, n) in cases) assertEquals(name, n, CallNumber.guess(name))
    }

    @Test fun vagueNamesGetTheMainClass() {
        assertEquals("500", CallNumber.guess("Integrated Science 1"))
        assertEquals("300", CallNumber.guess("Social Studies 9"))
        assertEquals("700", CallNumber.guess("Art 1"))
        assertEquals("900", CallNumber.guess("History of the Americas"))
    }

    @Test fun nonSubjectsAndUnknownsGetNothing() {
        for (name in listOf("Advisory", "Homeroom 214", "Study Hall", "School Hub", "Freshman Seminar", "AP Seminar",
            "Tutorial", "Leadership", "Teacher Aide", "Link Crew", "Flex", "Microbe Club", "Europa")) {
            assertNull(name, CallNumber.guess(name))
        }
    }

    @Test fun handSetNumbersWin() {
        val c = Course("9", "Leadership - 1234", "P4")
        assertNull(CallNumber.number(c, ""))
        var raw = CallNumber.setting("", "9", "300")
        assertEquals("300", CallNumber.number(c, raw))
        raw = CallNumber.setting(raw, "9", "")
        assertNull("None hides it", CallNumber.number(c, raw))
        assertEquals("570", CallNumber.number(Course("7", "AP Biology - 1101", "P2"), raw))
        raw = CallNumber.setting(raw, "9", null)
        assertEquals(emptyMap<String, String>(), CallNumber.overrides(raw))
    }
}
