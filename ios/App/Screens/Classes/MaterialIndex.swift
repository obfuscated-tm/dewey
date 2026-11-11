import Foundation

/// Every class's Materials tree by name, like the MCP's find_material: each class walked
/// (folders through the engine, which paces requests) when searched or when its page is
/// opened, and again once its copy is a day old. Used by the Classes tab's search, a
/// class page's own search, and the NEW marks: each walk is compared with the class's
/// previous saved copy (`NewMaterials`).
@MainActor
@Observable
final class MaterialIndex {
    static let shared = MaterialIndex()

    /// A folder above a hit: its id (to open it) and name (for the path).
    struct Crumb: Codable, Hashable { var id: String; var title: String }
    struct Hit: Codable {
        var sectionId: String
        /// The folders above the row by name, " › " between ("" at the class's root).
        var path: String
        var row: MaterialRow
        /// The folders above the row, outermost first (nil in copies saved before 2026-11).
        var folders: [Crumb]?
    }

    /// Folders deeper than this aren't opened (a teacher's tree is rarely past 6).
    nonisolated static let maxDepth = 12
    /// Folders read per class at most, so one huge class can't keep the engine busy for long.
    nonisolated static let maxFolders = 400
    /// A class's copy is walked again once it is this old.
    nonisolated static let staleAfter: TimeInterval = 24 * 3600

    private(set) var hits: [Hit]
    /// When each class was last walked in full. A class with no entry has no previous
    /// copy to compare with, so its first walk marks nothing new.
    private(set) var builtAt: [String: Date]
    /// The class being walked now, the folders read so far, and what it has found.
    private(set) var walking: String?
    private(set) var foldersRead = 0
    private var partial: [Hit] = []
    @ObservationIgnored private var queue: [String] = []
    /// NEW marks: class → `MaterialRow.key` → when the walk found it.
    private(set) var marks: [String: [String: Date]]
    /// Class → item key → ids of the folders above it (for the folder dots).
    @ObservationIgnored private var ancestors: [String: [String: [String]]] = [:]

    /// Demo mode starts clean every launch (so its NEW row shows), and nothing in demo
    /// or the tour's practice is saved as the student's own.
    private var saves: Bool { !Demo.on && !Practice.active }

    init() {
        if Demo.on {
            hits = []; builtAt = [:]; marks = [:]
        } else {
            hits = Cache.load("materialIndex", as: [Hit].self)?.value ?? []
            builtAt = Cache.load("materialIndexBuilt", as: [String: Date].self)?.value ?? [:]
            marks = Cache.load("materialMarks", as: [String: [String: Date]].self)?.value ?? [:]
        }
        for sid in Set(hits.map(\.sectionId)) { ancestors[sid] = Self.ancestors(hits.filter { $0.sectionId == sid }) }
    }

    // ── Search ───────────────────────────────────────────────────────────

    /// Every class (files, pages and work; folders are found by opening them), or one
    /// class (folders too: in a class, a folder is often what you're looking for).
    func search(_ q: String, in sectionId: String? = nil) -> [Hit] {
        let words = q.lowercased().split(separator: " ").map(String.init)
        guard !words.isEmpty else { return [] }
        var pool = sectionId.map { s in hits.filter { $0.sectionId == s } } ?? hits
        // While a class is walked for the first time, what it has found so far.
        if let w = walking, sectionId == nil || sectionId == w {
            let have = Set(pool.filter { $0.sectionId == w }.map(\.row.key))
            pool += partial.filter { !have.contains($0.row.key) }
        }
        return pool.filter { h in
            (sectionId != nil || h.row.kind != "folder") && words.allSatisfy { h.row.title.lowercased().contains($0) }
        }
    }

    func isWalking(_ sectionId: String) -> Bool { walking == sectionId || queue.contains(sectionId) }
    var building: Bool { walking != nil }

    // ── Walking ──────────────────────────────────────────────────────────

    /// Walks every class whose copy is missing or a day old (or all, with `force`).
    func build(_ model: AppModel, force: Bool = false) {
        for c in model.classes { refresh(c.sectionId, model, force: force) }
    }

    /// Walks one class if its copy is missing or a day old. Classes wait their turn:
    /// one walk at a time, so the engine's pacing holds.
    func refresh(_ sectionId: String, _ model: AppModel, force: Bool = false) {
        guard !model.practice, !isWalking(sectionId) else { return }
        if !force, let at = builtAt[sectionId], Date().timeIntervalSince(at) < Self.staleAfter { return }
        queue.append(sectionId)
        if walking == nil { Task { await drain(model) } }
    }

    private func drain(_ model: AppModel) async {
        while !queue.isEmpty {
            let sid = queue.removeFirst()
            walking = sid
            foldersRead = 0
            partial = []
            if let walked = await walk(sid, model) { commit(sid, walked) }
            partial = []
            walking = nil
        }
    }

    /// The class's whole tree, breadth first, or nil if any folder couldn't be read: a
    /// half-read tree would make everything it missed look new on the next walk.
    private func walk(_ sid: String, _ model: AppModel) async -> [Hit]? {
        var queue: [(id: String?, crumbs: [Crumb])] = [(nil, [])]
        var out: [Hit] = []
        var seenFolders = Set<String>()
        while !queue.isEmpty, foldersRead < Self.maxFolders {
            let (fid, crumbs) = queue.removeFirst()
            var args: [String: Any] = ["sectionId": sid]
            if let fid { args["folderId"] = fid }
            guard let rows: [MaterialRow] = try? await model.engine.call("materials", args) else { return nil }
            foldersRead += 1
            for r in rows {
                let hit = Hit(sectionId: sid, path: crumbs.map(\.title).joined(separator: " › "), row: r, folders: crumbs)
                out.append(hit)
                partial.append(hit)
                // A folder linked from two places (or into itself) is read once.
                if r.kind == "folder", let id = r.id, crumbs.count < Self.maxDepth, seenFolders.insert(id).inserted {
                    queue.append((id, crumbs + [Crumb(id: id, title: r.title)]))
                }
            }
        }
        return out
    }

    /// Replaces the class's copy and compares it with the previous one for NEW marks.
    private func commit(_ sid: String, _ walked: [Hit]) {
        let before = hits.filter { $0.sectionId == sid }
        let keys = walked.map(\.row.key)
        var fresh = NewMaterials.fresh(known: builtAt[sid] == nil ? nil : Set(before.map(\.row.key)), current: keys)
        if Demo.on, builtAt[sid] == nil { fresh = Demo.newMaterialKeys[sid] ?? [] }
        marks[sid] = NewMaterials.update(marks[sid] ?? [:], fresh: fresh, present: Set(keys))
        hits = hits.filter { $0.sectionId != sid } + walked
        builtAt[sid] = Date()
        ancestors[sid] = Self.ancestors(walked)
        guard saves else { return }
        Cache.save("materialIndex", hits)
        Cache.save("materialIndexBuilt", builtAt)
        Cache.save("materialMarks", marks)
    }

    private static func ancestors(_ hits: [Hit]) -> [String: [String]] {
        Dictionary(hits.map { ($0.row.key, ($0.folders ?? []).map(\.id)) }, uniquingKeysWith: { a, _ in a })
    }

    // ── NEW marks ────────────────────────────────────────────────────────

    func isNew(_ sectionId: String, _ row: MaterialRow, now: Date = Date()) -> Bool {
        marks[sectionId]?[row.key].map { NewMaterials.isNew(foundAt: $0, now: now) } ?? false
    }

    /// Something new anywhere inside this folder.
    func folderHasNew(_ sectionId: String, _ folderId: String?, now: Date = Date()) -> Bool {
        guard let folderId, let m = marks[sectionId], !m.isEmpty else { return false }
        return NewMaterials.folderHasNew(folderId, marks: m, ancestors: ancestors[sectionId] ?? [:], now: now)
    }

    /// Opened, or its folder was opened and left.
    func markSeen(_ sectionId: String, _ keys: some Sequence<String>) {
        guard let m = marks[sectionId] else { return }
        let after = NewMaterials.seen(m, keys)
        guard after.count != m.count else { return }
        marks[sectionId] = after
        if saves { Cache.save("materialMarks", marks) }
    }

    /// Signed out: the next student's first walk starts from nothing.
    func reset() {
        hits = []; builtAt = [:]; marks = [:]; ancestors = [:]; queue = []
    }
}

extension MaterialIndex.Hit {
    /// The path to this hit as seen from a search on the class page: the class, then each
    /// folder above it. Only the class page is on the stack under anything opened from here.
    func trail(classTitle: String) -> (names: [String], ids: [String?], onStack: [Bool]) {
        let crumbs = folders ?? []
        return ([classTitle] + crumbs.map(\.title), [nil] + crumbs.map(\.id), [true] + crumbs.map { _ in false })
    }

    /// The folder this hit is in, opened straight from search (nil at the class's root).
    func containingFolder(classTitle: String) -> FolderRoute? {
        guard let crumbs = folders, let last = crumbs.last else { return nil }
        let t = trail(classTitle: classTitle)
        return FolderRoute(sectionId: sectionId, folderId: last.id, title: last.title,
                           trail: Array(t.names.dropLast()), trailIds: Array(t.ids.dropLast()), onStack: Array(t.onStack.dropLast()))
    }
}
