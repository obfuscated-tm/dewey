import Foundation

/// What the teacher added since the student last looked, as plain functions (tested in
/// NewMaterialsTests). One definition of "new" for the whole app: a key that wasn't in the
/// previous saved copy, where the first copy ever saved has nothing new in it. The Inbox's
/// "new materials" alert (Schoology notification keys) and the NEW marks in Materials (the
/// class's walked tree, keyed by `MaterialRow.key`) both use `fresh`.
enum NewMaterials {
    /// Keys in `current` that `known` didn't have, in `current`'s order. `known == nil`
    /// means nothing was ever saved (first launch, a new sign-in, a wiped cache): nothing is new.
    static func fresh(known: Set<String>?, current: [String]) -> [String] {
        guard let known else { return [] }
        var out: [String] = [], added = Set<String>()
        for k in current where !known.contains(k) && added.insert(k).inserted { out.append(k) }
        return out
    }

    /// A NEW mark wears off after a week even if never opened, like new grades.
    static let days: Double = AppModel.newGradeDays

    /// Still marked: found less than a week ago.
    static func isNew(foundAt: Date, now: Date = Date()) -> Bool {
        now.timeIntervalSince(foundAt) < days * 86_400
    }

    /// One class's marks after a walk: add what's fresh (found now), drop marks for items
    /// that are gone from the tree and marks that have aged out.
    static func update(_ marks: [String: Date], fresh: [String], present: Set<String>, now: Date = Date()) -> [String: Date] {
        var m = marks.filter { present.contains($0.key) && isNew(foundAt: $0.value, now: now) }
        for k in fresh where m[k] == nil { m[k] = now }
        return m
    }

    /// Opened, or its folder was opened and left: no longer new.
    static func seen(_ marks: [String: Date], _ keys: some Sequence<String>) -> [String: Date] {
        var m = marks
        for k in keys { m[k] = nil }
        return m
    }

    /// A folder gets a dot when anything inside it, at any depth, is still new.
    /// `ancestors` maps each item key to the ids of the folders above it, outermost first.
    static func folderHasNew(_ folderId: String, marks: [String: Date], ancestors: [String: [String]], now: Date = Date()) -> Bool {
        marks.contains { k, at in isNew(foundAt: at, now: now) && (ancestors[k]?.contains(folderId) ?? false) }
    }
}

/// Going back to a level of the tappable folder path. A folder opened from search sits
/// right on top of the class page: the levels between were never pushed. A tap on one
/// of those pops back to the nearest level that is on the stack and pushes the one tapped,
/// so a path name always lands on that folder.
enum FolderPath {
    struct Jump: Equatable {
        /// Screens to pop, counting this one.
        var pop: Int
        /// The tapped level wasn't on the stack: push it after popping.
        var push: Bool
    }

    /// `onStack[i]`: level i of the trail (0 = the class page) is a screen under this one.
    static func jump(to level: Int, onStack: [Bool]) -> Jump {
        guard level >= 0, level < onStack.count else { return Jump(pop: 0, push: false) }
        // The nearest level at or above the one tapped that is on the stack (the class page always is).
        let base = (0...level).last { onStack[$0] } ?? 0
        let above = onStack[(base + 1)...].filter { $0 }.count
        return Jump(pop: above + 1, push: base != level)
    }
}
