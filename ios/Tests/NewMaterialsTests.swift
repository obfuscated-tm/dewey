import XCTest
@testable import Dewey

final class NewMaterialsTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_760_000_000)
    private func daysAgo(_ d: Double) -> Date { now.addingTimeInterval(-d * 86_400) }

    // ── What counts as new ───────────────────────────────────────────────

    func testFirstCopyEverHasNothingNew() {
        XCTAssertEqual(NewMaterials.fresh(known: nil, current: ["a", "b"]), [])
    }

    func testOnlyKeysNotInThePreviousCopy() {
        XCTAssertEqual(NewMaterials.fresh(known: ["a", "b"], current: ["a", "c", "b", "d"]), ["c", "d"])
    }

    func testEmptyPreviousCopyStillCounts() {
        // A class with nothing in it last time: everything it has now is new.
        XCTAssertEqual(NewMaterials.fresh(known: [], current: ["a"]), ["a"])
    }

    func testRepeatedKeyOnce() {
        XCTAssertEqual(NewMaterials.fresh(known: [], current: ["a", "a"]), ["a"])
    }

    func testMovedOrRenamedRowKeepsItsKey() {
        // The key is kind + Schoology id, so a rename or a move isn't new.
        let before = MaterialRow(kind: "document", title: "Notes.pdf", id: "77", url: "/u/1")
        let after = MaterialRow(kind: "document", title: "Unit 2 Notes.pdf", id: "77", url: "/u/2")
        XCTAssertEqual(NewMaterials.fresh(known: [before.key], current: [after.key]), [])
    }

    // ── Marks: found, seen, a week old ───────────────────────────────────

    func testFreshKeysGetMarkedNow() {
        let m = NewMaterials.update([:], fresh: ["c"], present: ["a", "c"], now: now)
        XCTAssertEqual(m, ["c": now])
    }

    func testAMarkKeepsItsFirstDate() {
        let m = NewMaterials.update(["c": daysAgo(2)], fresh: ["c"], present: ["c"], now: now)
        XCTAssertEqual(m["c"], daysAgo(2))
    }

    func testGoneItemsAndOldMarksDrop() {
        let m = NewMaterials.update(["gone": daysAgo(1), "old": daysAgo(8), "kept": daysAgo(6)], fresh: [], present: ["old", "kept"], now: now)
        XCTAssertEqual(m, ["kept": daysAgo(6)])
    }

    func testNewForAWeek() {
        XCTAssertTrue(NewMaterials.isNew(foundAt: daysAgo(6.9), now: now))
        XCTAssertFalse(NewMaterials.isNew(foundAt: daysAgo(7), now: now))
    }

    func testSeenClearsOnlyThoseKeys() {
        XCTAssertEqual(NewMaterials.seen(["a": now, "b": now], ["a", "x"]), ["b": now])
    }

    func testFolderDotForAnythingNewAtAnyDepth() {
        let ancestors = ["deep": ["u3", "labs", "data"], "top": [], "other": ["u2"]]
        XCTAssertTrue(NewMaterials.folderHasNew("u3", marks: ["deep": now], ancestors: ancestors, now: now))
        XCTAssertTrue(NewMaterials.folderHasNew("data", marks: ["deep": now], ancestors: ancestors, now: now))
        XCTAssertFalse(NewMaterials.folderHasNew("u2", marks: ["deep": now], ancestors: ancestors, now: now))
        // Aged out inside: no dot.
        XCTAssertFalse(NewMaterials.folderHasNew("u3", marks: ["deep": daysAgo(8)], ancestors: ancestors, now: now))
        // Seen: no dot.
        XCTAssertFalse(NewMaterials.folderHasNew("u3", marks: NewMaterials.seen(["deep": now], ["deep"]), ancestors: ancestors, now: now))
    }

    // ── The folder path ──────────────────────────────────────────────────

    func testPathAllOnStackPopsBack() {
        // Class › Unit 3 › Labs, viewing Data: "Unit 3" is two screens back.
        XCTAssertEqual(FolderPath.jump(to: 1, onStack: [true, true, true]), .init(pop: 2, push: false))
        XCTAssertEqual(FolderPath.jump(to: 0, onStack: [true, true, true]), .init(pop: 3, push: false))
        XCTAssertEqual(FolderPath.jump(to: 2, onStack: [true, true, true]), .init(pop: 1, push: false))
    }

    func testPathFromSearchPushesTheMissingLevel() {
        // Opened from the class page's search: only the class page is underneath.
        XCTAssertEqual(FolderPath.jump(to: 2, onStack: [true, false, false]), .init(pop: 1, push: true))
        XCTAssertEqual(FolderPath.jump(to: 0, onStack: [true, false, false]), .init(pop: 1, push: false))
    }

    func testPathFromSearchThenDeeper() {
        // Search › Data (levels 1–2 missing), then into Past years: trail is Class, U3, Labs, Data.
        XCTAssertEqual(FolderPath.jump(to: 3, onStack: [true, false, false, true]), .init(pop: 1, push: false))
        XCTAssertEqual(FolderPath.jump(to: 1, onStack: [true, false, false, true]), .init(pop: 2, push: true))
    }

    func testSearchHitTrail() {
        let h = MaterialIndex.Hit(sectionId: "s", path: "Unit 3 › Labs", row: MaterialRow(kind: "document", title: "x.pdf", id: "1", url: nil),
                                  folders: [.init(id: "u3", title: "Unit 3"), .init(id: "labs", title: "Labs")])
        let t = h.trail(classTitle: "Bio")
        XCTAssertEqual(t.names, ["Bio", "Unit 3", "Labs"])
        XCTAssertEqual(t.ids, [nil, "u3", "labs"])
        XCTAssertEqual(t.onStack, [true, false, false])
        let f = h.containingFolder(classTitle: "Bio")
        XCTAssertEqual(f?.folderId, "labs")
        XCTAssertEqual(f?.trail, ["Bio", "Unit 3"])
        XCTAssertEqual(f?.onStack, [true, false])
    }
}
