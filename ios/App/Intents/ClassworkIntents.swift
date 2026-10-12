import AppIntents
import WidgetKit

// R4 · Siri and Shortcuts. Everything answers from the widget snapshot (the
// open To Do list the app last saved), so nothing here touches Schoology:
// "Mark … done" changes the app's own Done / Studied mark only, like the
// widget's circle.

/// An open item on the To Do list.
struct TodoEntity: AppEntity {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Classwork item"
    static let defaultQuery = TodoQuery()

    let id: String
    let title: String
    let course: String
    let due: Date?
    let isTest: Bool

    init(_ it: WidgetItem) {
        id = it.id
        title = it.title
        course = it.course
        due = it.due
        isTest = it.isTest
    }

    var displayRepresentation: DisplayRepresentation {
        let sub = [course, due.map { Fmt.due($0) }].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
        return DisplayRepresentation(title: "\(title)", subtitle: "\(sub)")
    }
}

struct TodoQuery: EntityStringQuery {
    /// Unmarked items, coming up first (soonest on top), then the overdue ones.
    private func open() -> [WidgetItem] {
        let now = Date()
        let items = SharedStore.load().items.filter { !$0.isMarked }
        return items.filter { ($0.due ?? .distantFuture) >= now } + items.filter { ($0.due ?? .distantFuture) < now }
    }

    func entities(for identifiers: [String]) async throws -> [TodoEntity] {
        SharedStore.load().items.filter { identifiers.contains($0.id) }.map(TodoEntity.init)
    }

    func entities(matching string: String) async throws -> [TodoEntity] {
        let q = string.lowercased()
        return open().filter { $0.title.lowercased().contains(q) || $0.course.lowercased().contains(q) }.map(TodoEntity.init)
    }

    func suggestedEntities() async throws -> [TodoEntity] { open().map(TodoEntity.init) }
}

enum DueDay: String, AppEnum {
    case today, tomorrow, week

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Day"
    static let caseDisplayRepresentations: [DueDay: DisplayRepresentation] = [
        .today: "today", .tomorrow: "tomorrow", .week: "this week",
    ]
}

/// "What's due tomorrow": spoken and shown, from the open list.
struct WhatsDueIntent: AppIntent {
    static let title: LocalizedStringResource = "What's Due"
    static let description = IntentDescription("Lists what's still open on your Classwork To Do list for a day.")

    @Parameter(title: "Day", default: .tomorrow) var day: DueDay

    func perform() async throws -> some IntentResult & ReturnsValue<[TodoEntity]> & ProvidesDialog {
        let s = SharedStore.load()
        let now = Date()
        let items = Self.due(s.items, day: day, now: now)
        return .result(value: items.map(TodoEntity.init),
                       dialog: "\(Self.summary(items, day: day, now: now, asOf: s.updatedAt, signedIn: s.signedIn))")
    }

    /// Open items due in `day`, soonest first. "This week" is today through 6 days on.
    nonisolated static func due(_ items: [WidgetItem], day: DueDay, now: Date, calendar cal: Calendar = .current) -> [WidgetItem] {
        let today = cal.startOfDay(for: now)
        let (from, days): (Date, Int) = switch day {
        case .today: (today, 1)
        case .tomorrow: (cal.date(byAdding: .day, value: 1, to: today)!, 1)
        case .week: (today, 7)
        }
        let to = cal.date(byAdding: .day, value: days, to: from)!
        return items.filter { !$0.isMarked && ($0.due.map { $0 >= from && $0 < to } ?? false) }
            .sorted { $0.due! < $1.due! }
    }

    /// "Tomorrow: 2 things. Lab 4 for Chemistry, and the Unit 3 test for Math."
    nonisolated static func summary(_ items: [WidgetItem], day: DueDay, now: Date, asOf: Date, signedIn: Bool) -> String {
        let when = ["today": "today", "tomorrow": "tomorrow", "week": "this week"][day.rawValue]!
        var out: String
        let count = items.count == 1 ? "1 thing" : "\(items.count) things"
        let capWhen = when.prefix(1).uppercased() + when.dropFirst()
        if items.isEmpty {
            out = "Nothing open is due \(when)."
        } else if day == .week {
            // A week is too long to read out item by item: counts per class, then the tests by name.
            var perCourse: [(String, Int)] = []
            for it in items {
                let c = it.course.isEmpty ? "other classes" : it.course
                if let i = perCourse.firstIndex(where: { $0.0 == c }) { perCourse[i].1 += 1 } else { perCourse.append((c, 1)) }
            }
            out = "\(capWhen): \(count). " + perCourse.sorted { $0.1 > $1.1 }.map { "\($0.1) for \($0.0)" }.joined(separator: ", ") + "."
            let tests = items.filter(\.isTest)
            if !tests.isEmpty {
                let day = DateFormatter()
                day.dateFormat = "EEEE"
                out += " " + (tests.count == 1 ? "Test: " : "Tests: ")
                    + tests.prefix(3).map { "\($0.title)\($0.due.map { " on " + day.string(from: $0) } ?? "")" }.joined(separator: "; ") + "."
            }
        } else {
            let names = items.prefix(5).map { it in
                let what = it.isTest ? "the test \(it.title)" : it.title
                return it.course.isEmpty ? what : "\(what) for \(it.course)"
            }
            var list = names.count > 1 ? names.dropLast().joined(separator: ", ") + ", and " + names.last! : names[0]
            if items.count > 5 { list += ", plus \(items.count - 5) more" }
            list = list.prefix(1).uppercased() + list.dropFirst()
            out = "\(capWhen): \(count). \(list)."
        }
        if !signedIn { out += " Classwork is signed out, so this may be out of date." }
        else if now.timeIntervalSince(asOf) > 12 * 3600 { out += " As of \(Fmt.ago(asOf, now: now))." }
        return out
    }
}

/// "Mark Lab 4 done": Done (Studied for a test) in the app only, never on Schoology.
struct MarkDoneIntent: AppIntent {
    static let title: LocalizedStringResource = "Mark Done"
    static let description = IntentDescription("Marks a To Do item done, or a test studied, in Classwork only. Nothing changes on Schoology.")

    @Parameter(title: "Item") var item: TodoEntity

    static var parameterSummary: some ParameterSummary { Summary("Mark \(\.$item) done") }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let word = item.isTest ? "studied" : "done"
        guard SharedStore.mark(item.id, on: true) else {
            return .result(dialog: "\(item.title) is already marked \(word).")
        }
        WidgetCenter.shared.reloadAllTimelines()
        await MainActor.run { AppModel.live?.applyWidgetMarks() }
        return .result(dialog: "Marked \(item.title) \(word). Nothing was changed on Schoology.")
    }
}

/// Opens the app on To Do.
struct OpenTodoIntent: AppIntent {
    static let title: LocalizedStringResource = "Open To Do"
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        NotificationCenter.default.post(name: .openTodo, object: nil)
        return .result()
    }
}

extension Notification.Name {
    static let openTodo = Notification.Name("classwork.openTodo")
}

struct ClassworkShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: WhatsDueIntent(), phrases: [
            "What's due \(\.$day) in \(.applicationName)",
            "What's due in \(.applicationName)",
            "What do I have due in \(.applicationName)",
            "\(.applicationName) what's due \(\.$day)",
        ], shortTitle: "What's Due", systemImageName: "checklist")
        AppShortcut(intent: MarkDoneIntent(), phrases: [
            "Mark \(\.$item) done in \(.applicationName)",
            "Mark done in \(.applicationName)",
        ], shortTitle: "Mark Done", systemImageName: "checkmark.circle")
        AppShortcut(intent: OpenTodoIntent(), phrases: [
            "Open \(.applicationName) To Do",
            "Open my To Do in \(.applicationName)",
        ], shortTitle: "Open To Do", systemImageName: "list.bullet")
    }
}
