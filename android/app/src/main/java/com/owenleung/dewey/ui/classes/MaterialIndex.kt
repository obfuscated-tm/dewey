package com.owenleung.dewey.ui.classes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.engine.MaterialRow
import com.owenleung.dewey.engine.Practice
import com.owenleung.dewey.store.Cache
import com.owenleung.dewey.ui.Route
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Every class's Materials tree by name, like the MCP's find_material: each class walked
 * (folders through the engine, which paces requests) when searched or when its page is
 * opened, and again once its copy is a day old. Used by the Classes tab's search, a
 * class page's own search, and the NEW marks: each walk is compared with the class's
 * previous saved copy (`NewMaterials`). (iOS MaterialIndex.swift)
 */
object MaterialIndex {
    /** A folder above a hit: its id (to open it) and name (for the path). */
    @Serializable
    data class Crumb(val id: String, val title: String)

    @Serializable
    data class Hit(
        val sectionId: String,
        /** The folders above the row by name, " › " between ("" at the class's root). */
        val path: String,
        val row: MaterialRow,
        /** The folders above the row, outermost first (null in copies saved before 2026-11). */
        val folders: List<Crumb>? = null,
    )

    /** Folders deeper than this aren't opened (a teacher's tree is rarely past 6). */
    const val MAX_DEPTH = 12
    /** Folders read per class at most, so one huge class can't keep the engine busy for long. */
    const val MAX_FOLDERS = 400
    /** A class's copy is walked again once it is this old. */
    const val STALE_AFTER_MS = 24 * 3600_000L

    private val hitsS = ListSerializer(Hit.serializer())
    private val builtS = MapSerializer(String.serializer(), Long.serializer())
    private val marksS = MapSerializer(String.serializer(), MapSerializer(String.serializer(), Long.serializer()))

    var hits by mutableStateOf<List<Hit>>(emptyList()); private set
    /**
     * When each class was last walked in full. A class with no entry has no previous
     * copy to compare with, so its first walk marks nothing new.
     */
    private var builtAt: Map<String, Long> = emptyMap()
    /** The class being walked now, the folders read so far, and what it has found. */
    var walking by mutableStateOf<String?>(null); private set
    var foldersRead by mutableIntStateOf(0); private set
    private var partial by mutableStateOf<List<Hit>>(emptyList())
    private val queue = ArrayDeque<String>()
    /** NEW marks: class → `MaterialRow.key` → when the walk found it (epoch ms). */
    var marks by mutableStateOf<Map<String, Map<String, Long>>>(emptyMap()); private set
    /** Class → item key → ids of the folders above it (for the folder dots). */
    private var ancestors: Map<String, Map<String, List<String>>> = emptyMap()
    private var loaded = false

    /** Nothing in the tour's practice is saved as the student's own (Cache writes there go to its own folder). */
    private val saves get() = !Practice.active

    private fun loadCached() {
        if (loaded || Practice.active) return
        loaded = true
        hits = Cache.load("materialIndex", hitsS)?.first ?: emptyList()
        builtAt = Cache.load("materialIndexBuilt", builtS)?.first ?: emptyMap()
        marks = Cache.load("materialMarks", marksS)?.first ?: emptyMap()
        ancestors = hits.groupBy { it.sectionId }.mapValues { ancestorsOf(it.value) }
    }

    // ── Search ───────────────────────────────────────────────────────────

    /**
     * Every class (files, pages and work; folders are found by opening them), or one
     * class (folders too: in a class, a folder is often what you're looking for).
     */
    fun search(q: String, sectionId: String? = null): List<Hit> {
        loadCached()
        val words = q.lowercase().split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        var pool = if (sectionId != null) hits.filter { it.sectionId == sectionId } else hits
        // While a class is walked for the first time, what it has found so far.
        val w = walking
        if (w != null && (sectionId == null || sectionId == w)) {
            val have = pool.filter { it.sectionId == w }.map { it.row.key }.toSet()
            pool = pool + partial.filter { it.row.key !in have }
        }
        return pool.filter { h ->
            (sectionId != null || h.row.kind != "folder") && words.all { h.row.title.lowercase().contains(it) }
        }
    }

    fun isWalking(sectionId: String) = walking == sectionId || sectionId in queue
    val building get() = walking != null

    // ── Walking ──────────────────────────────────────────────────────────

    /** Walks every class whose copy is missing or a day old (or all, with `force`). */
    fun build(model: AppModel, force: Boolean = false) {
        for (c in model.classes) refresh(c.sectionId, model, force)
    }

    /**
     * Walks one class if its copy is missing or a day old. Classes wait their turn:
     * one walk at a time, so the engine's pacing holds.
     */
    fun refresh(sectionId: String, model: AppModel, force: Boolean = false) {
        if (model.practice || isWalking(sectionId)) return
        loadCached()
        val at = builtAt[sectionId]
        if (!force && at != null && System.currentTimeMillis() - at < STALE_AFTER_MS) return
        queue.addLast(sectionId)
        if (walking == null) model.scope.launch { drain(model) }
    }

    private suspend fun drain(model: AppModel) {
        while (queue.isNotEmpty()) {
            val sid = queue.removeFirst()
            walking = sid
            foldersRead = 0
            partial = emptyList()
            walk(sid, model)?.let { commit(sid, it) }
            partial = emptyList()
            walking = null
        }
    }

    /**
     * The class's whole tree, breadth first, or null if any folder couldn't be read: a
     * half-read tree would make everything it missed look new on the next walk.
     */
    private suspend fun walk(sid: String, model: AppModel): List<Hit>? {
        val todo = ArrayDeque(listOf<Pair<String?, List<Crumb>>>(null to emptyList()))
        val out = mutableListOf<Hit>()
        val seenFolders = mutableSetOf<String>()
        while (todo.isNotEmpty() && foldersRead < MAX_FOLDERS) {
            val (fid, crumbs) = todo.removeFirst()
            val args = mutableMapOf<String, Any?>("sectionId" to sid)
            if (fid != null) args["folderId"] = fid
            val rows = runCatching { model.engine.call<List<MaterialRow>>("materials", args) }.getOrNull() ?: return null
            foldersRead++
            val found = rows.map { r -> Hit(sid, crumbs.joinToString(" › ") { it.title }, r, crumbs) }
            out += found
            partial = partial + found
            for (r in rows) {
                // A folder linked from two places (or into itself) is read once.
                if (r.kind == "folder" && r.id != null && crumbs.size < MAX_DEPTH && seenFolders.add(r.id)) {
                    todo.addLast(r.id to crumbs + Crumb(r.id, r.title))
                }
            }
        }
        return out
    }

    /** Replaces the class's copy and compares it with the previous one for NEW marks. */
    private fun commit(sid: String, walked: List<Hit>) {
        val before = hits.filter { it.sectionId == sid }
        val keys = walked.map { it.row.key }
        val fresh = NewMaterials.fresh(if (builtAt[sid] == null) null else before.map { it.row.key }.toSet(), keys)
        marks = marks + (sid to NewMaterials.update(marks[sid] ?: emptyMap(), fresh, keys.toSet()))
        hits = hits.filter { it.sectionId != sid } + walked
        builtAt = builtAt + (sid to System.currentTimeMillis())
        ancestors = ancestors + (sid to ancestorsOf(walked))
        if (!saves) return
        Cache.save("materialIndex", hitsS, hits)
        Cache.save("materialIndexBuilt", builtS, builtAt)
        Cache.save("materialMarks", marksS, marks)
    }

    private fun ancestorsOf(hits: List<Hit>): Map<String, List<String>> {
        val m = mutableMapOf<String, List<String>>()
        for (h in hits) m.putIfAbsent(h.row.key, (h.folders ?: emptyList()).map { it.id })
        return m
    }

    // ── NEW marks ────────────────────────────────────────────────────────

    fun isNew(sectionId: String, row: MaterialRow, now: Long = System.currentTimeMillis()): Boolean {
        loadCached()
        return marks[sectionId]?.get(row.key)?.let { NewMaterials.isNew(it, now) } ?: false
    }

    /** Something new anywhere inside this folder. */
    fun folderHasNew(sectionId: String, folderId: String?, now: Long = System.currentTimeMillis()): Boolean {
        loadCached()
        val m = marks[sectionId]
        if (folderId == null || m.isNullOrEmpty()) return false
        return NewMaterials.folderHasNew(folderId, m, ancestors[sectionId] ?: emptyMap(), now)
    }

    /** Opened, or its folder was opened and left. */
    fun markSeen(sectionId: String, keys: Iterable<String>) {
        val m = marks[sectionId] ?: return
        val after = NewMaterials.seen(m, keys)
        if (after.size == m.size) return
        marks = marks + (sectionId to after)
        if (saves) Cache.save("materialMarks", marksS, marks)
    }

    /** Signed out: the next student's first walk starts from nothing. */
    fun reset() {
        hits = emptyList(); builtAt = emptyMap(); marks = emptyMap(); ancestors = emptyMap(); queue.clear()
        loaded = true
    }
}

/**
 * The path to this hit as seen from a search on the class page: the class, then each
 * folder above it. Only the class page is on the stack under anything opened from here.
 */
data class HitTrail(val names: List<String>, val ids: List<String?>, val onStack: List<Boolean>)

fun MaterialIndex.Hit.trail(classTitle: String): HitTrail {
    val crumbs = folders ?: emptyList()
    return HitTrail(listOf(classTitle) + crumbs.map { it.title }, listOf<String?>(null) + crumbs.map { it.id },
        listOf(true) + crumbs.map { false })
}

/** The folder this hit is in, opened straight from search (null at the class's root). */
fun MaterialIndex.Hit.containingFolder(classTitle: String): Route.Materials? {
    val last = folders?.lastOrNull() ?: return null
    val t = trail(classTitle)
    return Route.Materials(sectionId, last.id, last.title, t.names.dropLast(1), t.ids.dropLast(1), t.onStack.dropLast(1))
}
