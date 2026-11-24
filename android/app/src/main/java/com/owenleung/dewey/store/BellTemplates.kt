package com.owenleung.dewey.store

/**
 * Bell schedules of real schools, to start Settings → Schedule from (ios/App/Screens/Settings/BellTemplates.swift).
 * Typed from each school's published weekly bell schedule; staff-only blocks (collaboration) are left out.
 * Add a school only from its own published schedule, never a guess.
 */
data class BellTemplate(val district: String, val school: String, val year: String, val days: List<Day>) {
    /** `slots`: (period name, "8:30", "9:15"), afternoon times written 12-hour as on the sheet. */
    data class Day(val name: String, val weekdays: List<Int>, val slots: List<Triple<String, String, String>>)

    /** A whole schedule: one day type per Day, the weekdays set, nothing else. */
    val schedule: BellSchedule
        get() {
            val types = days.map { d ->
                BellSchedule.DayType(name = d.name, slots = d.slots.map { BellSchedule.Slot(name = it.first, start = minutes(it.second), end = minutes(it.third)) })
            }
            val week = days.zip(types).flatMap { (d, t) -> d.weekdays.map { it to t.id } }.toMap()
            return BellSchedule(dayTypes = types, week = week)
        }

    companion object {
        /** "1:30" on a school bell sheet is 1:30 pm: hours 1 to 6 are afternoon. */
        fun minutes(clock: String): Int {
            val (h0, m) = clock.split(":").map { it.toInt() }
            val h = if (h0 < 7) h0 + 12 else h0
            return h * 60 + m
        }

        /**
         * The Fremont Union weekly schedule, 2026–27: the same class times at every school
         * (chs, fhs, hhs, lhs, mvhs.fuhsd.org bell schedule pages, checked 2026-11-15). Monta
         * Vista's sheet runs brunch and lunch up to the next bell, so it passes its own ends.
         */
        private fun fuhsd(brunch: List<String>, lunch: List<String>): List<Day> = listOf(
            Day("Monday", listOf(2), listOf(
                Triple("P1", "8:30", "9:15"), Triple("P2", "9:20", "10:05"), Triple("Tutorial", "10:10", "10:35"), Triple("P3", "10:40", "11:25"),
                Triple("Brunch", "11:25", brunch[0]), Triple("P4", "11:50", "12:35"), Triple("P5", "12:40", "1:25"), Triple("Lunch", "1:25", lunch[0]),
                Triple("P6", "2:15", "3:00"), Triple("P7", "3:05", "3:50"),
            )),
            Day("Tuesday & Thursday", listOf(3, 5), listOf(
                Triple("P1", "8:30", "10:00"), Triple("P2", "10:05", "11:35"), Triple("Brunch", "11:35", brunch[1]), Triple("P3", "12:00", "1:30"),
                Triple("Lunch", "1:30", lunch[1]), Triple("P7", "2:20", "3:50"),
            )),
            Day("Wednesday & Friday", listOf(4, 6), listOf(
                Triple("P4", "8:30", "10:05"), Triple("Tutorial", "10:10", "10:50"), Triple("Brunch", "10:50", brunch[2]), Triple("P5", "11:15", "12:45"),
                Triple("Lunch", "12:45", lunch[2]), Triple("P6", "1:35", "3:05"),
            )),
        )

        val all: List<BellTemplate> by lazy {
            val common = fuhsd(listOf("11:40", "11:50", "11:05"), listOf("2:05", "2:10", "1:25"))
            val mv = fuhsd(listOf("11:45", "11:55", "11:10"), listOf("2:10", "2:15", "1:30"))
            listOf("Cupertino High", "Fremont High", "Homestead High", "Lynbrook High").map {
                BellTemplate("Fremont Union", it, "2026–27", common)
            } + BellTemplate("Fremont Union", "Monta Vista High", "2026–27", mv)
        }
    }
}
