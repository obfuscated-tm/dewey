import ActivityKit
import Foundation

/// What the app hands the widgets: the open To Do rows, soonest first.
/// Compiled into the app and the widget extension. The app writes it after
/// every refresh or mark; widgets only read (they can't reach Schoology).
struct WidgetItem: Codable, Hashable, Identifiable {
    let id: String
    let title: String
    let course: String
    let due: Date?
    let isTest: Bool
    /// Done (Studied for a test), in the app or from the widget's circle.
    var marked: Bool?

    var isMarked: Bool { marked ?? false }
}

/// A graded item for "Latest grades". `day` is when it moved the grade (YYYY-MM-DD).
struct WidgetGrade: Codable, Hashable, Identifiable {
    let id: String
    let sectionId: String
    let title: String
    let course: String
    let grade: String
    let day: String?
}

struct WidgetSnapshot: Codable {
    var items: [WidgetItem]
    var updatedAt: Date
    var signedIn: Bool
    var accentHex: String
    var grades: [WidgetGrade]?

    static let empty = WidgetSnapshot(items: [], updatedAt: .distantPast, signedIn: false, accentHex: "#1D5EA8")

    /// Overdue rows at `now` (the app already left out ones older than 60 days).
    func overdue(at now: Date) -> [WidgetItem] { items.filter { ($0.due ?? .distantFuture) < now } }
    /// Not yet due at `now`, soonest first.
    func upcoming(at now: Date) -> [WidgetItem] { items.filter { ($0.due ?? .distantFuture) >= now } }
    func dueToday(at now: Date, calendar: Calendar = .current) -> [WidgetItem] {
        upcoming(at: now).filter { $0.due.map { calendar.isDate($0, inSameDayAs: now) } ?? false }
    }
    /// Tests from today on, soonest first.
    func tests(at now: Date, calendar: Calendar = .current) -> [WidgetItem] {
        let start = calendar.startOfDay(for: now)
        return items.filter { $0.isTest && ($0.due ?? .distantFuture) >= start }
    }
}

/// The App Group both targets share. A free Apple account can sign App
/// Groups for development builds (docs/ios/FINDINGS.md).
enum SharedStore {
    static let group = "group.com.owenleung.classwork"
    private static let key = "widgetSnapshot"

    static var defaults: UserDefaults? { UserDefaults(suiteName: group) }

    static func save(_ s: WidgetSnapshot) {
        guard let d = try? JSONEncoder().encode(s) else { return }
        defaults?.set(d, forKey: key)
    }

    static func load() -> WidgetSnapshot {
        guard let d = defaults?.data(forKey: key), let s = try? JSONDecoder().decode(WidgetSnapshot.self, from: d) else { return .empty }
        return s
    }

    // Marks made from a Home Screen widget, waiting for the app to apply them (id → done).
    private static let marksKey = "widgetMarks"

    /// The widget's circle: flip the row in the snapshot and queue the mark for the app.
    static func toggleFromWidget(_ id: String) {
        var s = load()
        guard let i = s.items.firstIndex(where: { $0.id == id }) else { return }
        s.items[i].marked = !s.items[i].isMarked
        save(s)
        var marks = defaults?.dictionary(forKey: marksKey) as? [String: Bool] ?? [:]
        marks[id] = s.items[i].isMarked
        defaults?.set(marks, forKey: marksKey)
    }

    /// The queued marks, emptied.
    static func takeWidgetMarks() -> [String: Bool] {
        let marks = defaults?.dictionary(forKey: marksKey) as? [String: Bool] ?? [:]
        if !marks.isEmpty { defaults?.removeObject(forKey: marksKey) }
        return marks
    }
}

/// Deep links from widgets and the Live Activity.
enum AppLink {
    static let scheme = "classwork"
    static func assignment(_ id: String) -> URL { URL(string: "\(scheme)://assignment/\(id)")! }
    static let todo = URL(string: "\(scheme)://todo")!

    /// classwork://assignment/123 → "123"
    static func assignmentId(_ url: URL) -> String? {
        guard url.scheme == scheme, url.host == "assignment" else { return nil }
        let id = url.lastPathComponent
        return id.isEmpty || id == "/" ? nil : id
    }
}

/// A Lock Screen / Dynamic Island countdown to one due time.
struct DueActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var due: Date
        var finished: Bool
    }
    let id: String
    let title: String
    let course: String
    let isTest: Bool
    let accentHex: String
    let startedAt: Date
}
