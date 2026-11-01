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

    val reportCard: Boolean get() = Prefs.bool(REPORT_CARD_KEY, false)
    fun hidden(): Set<String> = IdSet.parse(Prefs.string(HIDDEN_KEY))
    fun toggle(id: String) = Prefs.set(HIDDEN_KEY, IdSet.toggled(Prefs.string(HIDDEN_KEY), id))

    /** "Algebra 2" → "ALGE 2", "AP Biology" → "AP BIOL", "US History" → "US HIST": short enough for six across. */
    fun abbreviation(name: String): String =
        name.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { if (it.length > 4) it.take(4) else it }.uppercase()
}

/** Settings → To Do → View: by class under the week strip (the default), or one list with the most urgent first. */
enum class ToDoLayout(val raw: String, val title: String) {
    WEEK("week", "Week"), URGENT("urgent", "Urgent first");

    companion object {
        const val KEY = "todoLayout"
        val current: ToDoLayout get() = entries.firstOrNull { it.raw == Prefs.string(KEY) } ?: WEEK
    }
}

/** The Classes shelf: section ids taken off it (they lie flat under it), comma-separated. */
/**
 * Class or group, when the student picked one (Settings → Classes and groups).
 * A class gets a book and grades; a group's posts go to School updates.
 */
object CourseKinds {
    const val CLASS_KEY = "courseKindClass"
    const val GROUP_KEY = "courseKindGroup"

    /** true: a class, false: a group, null: go by the name. */
    fun choice(sectionId: String): Boolean? {
        if (!Prefs.ready) return null
        if (sectionId in IdSet.parse(Prefs.string(CLASS_KEY))) return true
        if (sectionId in IdSet.parse(Prefs.string(GROUP_KEY))) return false
        return null
    }

    /** Saves the choice; one that matches the name's guess isn't kept. */
    fun set(c: com.owenleung.dewey.engine.Course, isClass: Boolean) {
        val cls = IdSet.parse(Prefs.string(CLASS_KEY)) - c.sectionId
        val grp = IdSet.parse(Prefs.string(GROUP_KEY)) - c.sectionId
        val keep = isClass != c.looksLikeClass
        Prefs.set(CLASS_KEY, (if (keep && isClass) cls + c.sectionId else cls).sorted().joinToString(","))
        Prefs.set(GROUP_KEY, (if (keep && !isClass) grp + c.sectionId else grp).sorted().joinToString(","))
    }
}

object ShelfLook {
    const val OFF_KEY = "offShelf"
    fun off(): Set<String> = IdSet.parse(Prefs.string(OFF_KEY))
    fun toggle(id: String) = Prefs.set(OFF_KEY, IdSet.toggled(Prefs.string(OFF_KEY), id))
}
