package com.owenleung.dewey.store

/** A set of section ids kept in one comma-separated setting (hidden classes, books off the shelf). */
object IdSet {
    fun parse(raw: String?): Set<String> = raw?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    fun toggled(raw: String?, id: String): String {
        val s = parse(raw).toMutableSet()
        if (!s.remove(id)) s += id
        return s.sorted().joinToString(",")
    }
}

/** Settings → Grades (ios/App/Screens/Grades/GradesView.swift GradesLook). */
object GradesLook {
    /** Show every class's letter in one row at the top of Grades (on by default). */
    const val REPORT_CARD_KEY = "gradesReportCard"
    /** Section ids of classes hidden from Grades, comma-separated (a class with nothing worth tracking). */
    const val HIDDEN_KEY = "gradesHidden"

    val reportCard: Boolean get() = Prefs.bool(REPORT_CARD_KEY, true)
    fun hidden(): Set<String> = IdSet.parse(Prefs.string(HIDDEN_KEY))
    fun toggle(id: String) = Prefs.set(HIDDEN_KEY, IdSet.toggled(Prefs.string(HIDDEN_KEY), id))

    /** "Algebra 2" → "ALGE 2", "AP Biology" → "AP BIOL", "US History" → "US HIST": short enough for six across. */
    fun abbreviation(name: String): String =
        name.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { if (it.length > 4) it.take(4) else it }.uppercase()
}
