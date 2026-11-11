package com.owenleung.dewey.ui.classes

import com.owenleung.dewey.AppModel

/**
 * What the teacher added since the student last looked, as plain functions (tested in
 * NewMaterialsTest). One definition of "new" for the whole app: a key that wasn't in the
 * previous saved copy, where the first copy ever saved has nothing new in it. The Inbox's
 * "new materials" alert (Schoology notification keys) and the NEW marks in Materials (the
 * class's walked tree, keyed by `MaterialRow.key`) both use `fresh`. Times are epoch ms.
 * (iOS NewMaterials.swift)
 */
object NewMaterials {
    /**
     * Keys in `current` that `known` didn't have, in `current`'s order. `known == null`
     * means nothing was ever saved (first launch, a new sign-in, a wiped cache): nothing is new.
     */
    fun fresh(known: Set<String>?, current: List<String>): List<String> {
        known ?: return emptyList()
        val out = mutableListOf<String>()
        val added = mutableSetOf<String>()
        for (k in current) if (k !in known && added.add(k)) out += k
        return out
    }

    /** A NEW mark wears off after a week even if never opened, like new grades. */
    const val DAYS = AppModel.NEW_GRADE_DAYS

    /** Still marked: found less than a week ago. */
    fun isNew(foundAt: Long, now: Long = System.currentTimeMillis()): Boolean = now - foundAt < DAYS * 86_400_000

    /**
     * One class's marks after a walk: add what's fresh (found now), drop marks for items
     * that are gone from the tree and marks that have aged out.
     */
    fun update(marks: Map<String, Long>, fresh: List<String>, present: Set<String>, now: Long = System.currentTimeMillis()): Map<String, Long> {
        val m = marks.filter { (k, at) -> k in present && isNew(at, now) }.toMutableMap()
        for (k in fresh) if (k !in m) m[k] = now
        return m
    }

    /** Opened, or its folder was opened and left: no longer new. */
    fun seen(marks: Map<String, Long>, keys: Iterable<String>): Map<String, Long> = marks - keys.toSet()

    /**
     * A folder gets a dot when anything inside it, at any depth, is still new.
     * `ancestors` maps each item key to the ids of the folders above it, outermost first.
     */
    fun folderHasNew(folderId: String, marks: Map<String, Long>, ancestors: Map<String, List<String>>, now: Long = System.currentTimeMillis()): Boolean =
        marks.any { (k, at) -> isNew(at, now) && ancestors[k]?.contains(folderId) == true }
}

/**
 * Going back to a level of the tappable folder path. A folder opened from search sits
 * right on top of the class page: the levels between were never pushed. A tap on one
 * of those pops back to the nearest level that is on the stack and pushes the one tapped,
 * so a path name always lands on that folder.
 */
object FolderPath {
    /** `pop`: screens to pop, counting this one. `push`: the tapped level wasn't on the stack, push it after popping. */
    data class Jump(val pop: Int, val push: Boolean)

    /** `onStack[i]`: level i of the trail (0 = the class page) is a screen under this one. */
    fun jump(level: Int, onStack: List<Boolean>): Jump {
        if (level < 0 || level >= onStack.size) return Jump(0, false)
        // The nearest level at or above the one tapped that is on the stack (the class page always is).
        val base = (0..level).lastOrNull { onStack[it] } ?: 0
        val above = onStack.drop(base + 1).count { it }
        return Jump(above + 1, base != level)
    }
}
