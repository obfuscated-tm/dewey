package com.owenleung.dewey

import com.owenleung.dewey.engine.MaterialRow
import com.owenleung.dewey.ui.classes.FolderPath
import com.owenleung.dewey.ui.classes.MaterialIndex
import com.owenleung.dewey.ui.classes.NewMaterials
import com.owenleung.dewey.ui.classes.containingFolder
import com.owenleung.dewey.ui.classes.trail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same cases as ios/Tests NewMaterialsTests (times in epoch ms). */
class NewMaterialsTest {
    private val now = 1_760_000_000_000L
    private fun daysAgo(d: Double) = now - (d * 86_400_000).toLong()

    // ── What counts as new ───────────────────────────────────────────────

    @Test fun firstCopyEverHasNothingNew() = assertEquals(emptyList<String>(), NewMaterials.fresh(null, listOf("a", "b")))

    @Test fun onlyKeysNotInThePreviousCopy() =
        assertEquals(listOf("c", "d"), NewMaterials.fresh(setOf("a", "b"), listOf("a", "c", "b", "d")))

    // A class with nothing in it last time: everything it has now is new.
    @Test fun emptyPreviousCopyStillCounts() = assertEquals(listOf("a"), NewMaterials.fresh(emptySet(), listOf("a")))

    @Test fun repeatedKeyOnce() = assertEquals(listOf("a"), NewMaterials.fresh(emptySet(), listOf("a", "a")))

    @Test fun movedOrRenamedRowKeepsItsKey() {
        // The key is kind + Schoology id, so a rename or a move isn't new.
        val before = MaterialRow("document", "Notes.pdf", "77", "/u/1")
        val after = MaterialRow("document", "Unit 2 Notes.pdf", "77", "/u/2")
        assertEquals(emptyList<String>(), NewMaterials.fresh(setOf(before.key), listOf(after.key)))
    }

    // ── Marks: found, seen, a week old ───────────────────────────────────

    @Test fun freshKeysGetMarkedNow() =
        assertEquals(mapOf("c" to now), NewMaterials.update(emptyMap(), listOf("c"), setOf("a", "c"), now))

    @Test fun aMarkKeepsItsFirstDate() =
        assertEquals(daysAgo(2.0), NewMaterials.update(mapOf("c" to daysAgo(2.0)), listOf("c"), setOf("c"), now)["c"])

    @Test fun goneItemsAndOldMarksDrop() {
        val m = NewMaterials.update(mapOf("gone" to daysAgo(1.0), "old" to daysAgo(8.0), "kept" to daysAgo(6.0)), emptyList(), setOf("old", "kept"), now)
        assertEquals(mapOf("kept" to daysAgo(6.0)), m)
    }

    @Test fun newForAWeek() {
        assertTrue(NewMaterials.isNew(daysAgo(6.9), now))
        assertFalse(NewMaterials.isNew(daysAgo(7.0), now))
    }

    @Test fun seenClearsOnlyThoseKeys() =
        assertEquals(mapOf("b" to now), NewMaterials.seen(mapOf("a" to now, "b" to now), listOf("a", "x")))

    @Test fun folderDotForAnythingNewAtAnyDepth() {
        val ancestors = mapOf("deep" to listOf("u3", "labs", "data"), "top" to emptyList(), "other" to listOf("u2"))
        assertTrue(NewMaterials.folderHasNew("u3", mapOf("deep" to now), ancestors, now))
        assertTrue(NewMaterials.folderHasNew("data", mapOf("deep" to now), ancestors, now))
        assertFalse(NewMaterials.folderHasNew("u2", mapOf("deep" to now), ancestors, now))
        // Aged out inside: no dot.
        assertFalse(NewMaterials.folderHasNew("u3", mapOf("deep" to daysAgo(8.0)), ancestors, now))
        // Seen: no dot.
        assertFalse(NewMaterials.folderHasNew("u3", NewMaterials.seen(mapOf("deep" to now), listOf("deep")), ancestors, now))
    }

    // ── The folder path ──────────────────────────────────────────────────

    @Test fun pathAllOnStackPopsBack() {
        // Class › Unit 3 › Labs, viewing Data: "Unit 3" is two screens back.
        assertEquals(FolderPath.Jump(2, false), FolderPath.jump(1, listOf(true, true, true)))
        assertEquals(FolderPath.Jump(3, false), FolderPath.jump(0, listOf(true, true, true)))
        assertEquals(FolderPath.Jump(1, false), FolderPath.jump(2, listOf(true, true, true)))
    }

    @Test fun pathFromSearchPushesTheMissingLevel() {
        // Opened from the class page's search: only the class page is underneath.
        assertEquals(FolderPath.Jump(1, true), FolderPath.jump(2, listOf(true, false, false)))
        assertEquals(FolderPath.Jump(1, false), FolderPath.jump(0, listOf(true, false, false)))
    }

    @Test fun pathFromSearchThenDeeper() {
        // Search › Data (levels 1–2 missing), then into Past years: trail is Class, U3, Labs, Data.
        assertEquals(FolderPath.Jump(1, false), FolderPath.jump(3, listOf(true, false, false, true)))
        assertEquals(FolderPath.Jump(2, true), FolderPath.jump(1, listOf(true, false, false, true)))
    }

    @Test fun searchHitTrail() {
        val h = MaterialIndex.Hit("s", "Unit 3 › Labs", MaterialRow("document", "x.pdf", "1", null),
            listOf(MaterialIndex.Crumb("u3", "Unit 3"), MaterialIndex.Crumb("labs", "Labs")))
        val t = h.trail("Bio")
        assertEquals(listOf("Bio", "Unit 3", "Labs"), t.names)
        assertEquals(listOf(null, "u3", "labs"), t.ids)
        assertEquals(listOf(true, false, false), t.onStack)
        val f = h.containingFolder("Bio")
        assertEquals("labs", f?.folderId)
        assertEquals(listOf("Bio", "Unit 3"), f?.trail)
        assertEquals(listOf(true, false), f?.onStack)
    }
}
