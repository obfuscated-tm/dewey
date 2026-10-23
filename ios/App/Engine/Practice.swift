import Foundation

/// The tour's pretend week. While it runs, a second AppModel (in memory, its
/// engine answered from here) drives the real screens, and nothing reaches
/// Schoology, neo-plan, reminders, widgets or the saved copies: those check
/// `Practice.active`. The Schoology sheet shows a pretend dropbox instead
/// (`PracticeDropbox`).
enum Practice {
    /// On from the start of practice until it closes.
    nonisolated(unsafe) static var active = false
    /// Work "submitted" in the pretend dropbox; `status` answers from this.
    nonisolated(unsafe) static var submitted: Set<String> = []

    @MainActor
    static func begin() {
        submitted = []
        // A practice the app was quit in the middle of left its settings behind.
        clearSettings()
        restoreView()
        saveView()
        active = true
        Cache.clearPractice()
    }

    @MainActor
    static func end() {
        active = false
        submitted = []
        Cache.clearPractice()
        clearSettings()
        restoreView()
    }

    // ── How To Do looks ──────────────────────────────────────────────────
    // Shared with the real app (AppStorage). The steps need List by class with the
    // week strip and Plan as it starts, so practice sets them and puts the student's
    // own back when it ends (or, after a practice the app was quit in, when the next begins).

    private static let savedViewKey = "practiceSavedView"
    private static let viewKeys: [String: Any] = [
        ToDoMode.key: ToDoMode.list.rawValue, ToDoLayout.key: ToDoLayout.week.rawValue,
        "todoMonth": false, "planCollapsed": "done", "planHintClosed": true,
    ]

    private static func saveView() {
        let d = UserDefaults.standard
        var saved: [String: Any] = [:]
        for (k, v) in viewKeys {
            if let old = d.object(forKey: k) { saved[k] = old }
            d.set(v, forKey: k)
        }
        // Keys that weren't set are saved as missing, so they go back to their defaults.
        saved["_keys"] = Array(viewKeys.keys)
        d.set(saved, forKey: savedViewKey)
    }

    static func restoreView() {
        let d = UserDefaults.standard
        guard let saved = d.dictionary(forKey: savedViewKey) else { return }
        for k in saved["_keys"] as? [String] ?? [] {
            // One the student changed during practice (Settings › To Do › View) is theirs now.
            guard (d.object(forKey: k) as? NSObject) == (viewKeys[k] as? NSObject) else { continue }
            if let v = saved[k] { d.set(v, forKey: k) } else { d.removeObject(forKey: k) }
        }
        d.removeObject(forKey: savedViewKey)
    }

    @MainActor
    private static func clearSettings() {
        // Settings the pretend classes left behind: shelf and opened categories.
        let ids = Set(courses.map(\.id))
        let d = UserDefaults.standard
        let off = Shelf.off(d.string(forKey: Shelf.offKey) ?? "").subtracting(ids)
        d.set(off.sorted().joined(separator: ","), forKey: Shelf.offKey)
        for id in ids { d.removeObject(forKey: "expandedCategories-\(id)") }
        let hidden = GradesLook.hidden(d.string(forKey: GradesLook.hiddenKey) ?? "").subtracting(ids)
        d.set(hidden.sorted().joined(separator: ","), forKey: GradesLook.hiddenKey)
    }

    // ── The pretend week: three classes, seven things due ────────────────

    static let courses = [Demo.course("AP Bio"), Demo.course("Chem"), Demo.course("English")]

    /// The ids the tour's steps point at.
    static let lab = "9206", worksheet = "9502", quiz = "9503", flameTest = "9504"
    /// Plan's steps: two things due tomorrow (reorder one, send one to Later).
    static let reading = "9702", problems = "9701"
    static let chem = Demo.course("Chem").id, english = Demo.course("English").id

    static let events: [Demo.Event] = [
        Demo.Event(id: lab, title: "Lab 2.2 Cell Size and Diffusion", course: Demo.course("AP Bio"), day: 0, hour: 23, status: "not_submitted"),
        Demo.Event(id: worksheet, title: "Stoichiometry Worksheet", course: Demo.course("Chem"), day: -1, hour: 8, minute: 30),
        Demo.Event(id: quiz, title: "Unit 2 Quiz", course: Demo.course("Chem"), day: 3, hour: 9, minute: 40),
        Demo.Event(id: "9304", title: "Essay Draft: The Crucible", course: Demo.course("English"), day: 2, hour: 23, status: "submitted"),
        Demo.Event(id: reading, title: "Reading Notes 3.1", course: Demo.course("AP Bio"), day: 1, hour: 8, minute: 0),
        Demo.Event(id: problems, title: "Ch. 4 Practice Problems", course: Demo.course("Chem"), day: 1, hour: 23),
        Demo.Event(id: "9703", title: "Vocab Unit 5", course: Demo.course("English"), day: 5, hour: 23),
    ]

    /// The op's `data`, or nil to let engine.js run it (pure ops only).
    static func answer(_ op: String, _ args: [String: Any]) throws -> Any? {
        let id = args["id"] as? String ?? ""
        switch op {
        case "courses":
            return courses.map { ["sectionId": $0.id, "title": $0.title, "sectionTitle": $0.section] }
        case "todo":
            return ["overdue": events.filter { $0.day < 0 }.map(\.json), "upcoming": events.filter { $0.day >= 0 }.map(\.json)]
        case "status":
            if submitted.contains(id) { return ["state": "submitted"] }
            return ["state": events.first { $0.id == id }?.status ?? "no_dropbox"]
        case "messages": return ["threads": [] as [Any], "unreadCount": 0, "hasMore": false]
        case "notifications": return ["items": [] as [Any], "hasMore": false]
        default:
            return try Demo.answer(op, args)
        }
    }
}
