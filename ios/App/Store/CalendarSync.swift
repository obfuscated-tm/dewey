import EventKit
import Foundation

/// Settings › To Do › "Show in the Calendar app": a "Dewey" calendar on the
/// phone holding open work, tests and the student's own tasks. Dewey only ever
/// touches that one calendar: events are matched by their classwork:// link,
/// added, moved or removed on each refresh, and work marked done drops off.
/// Nothing reaches Schoology.
@MainActor
final class CalendarSync {
    static let shared = CalendarSync()
    static let enabledKey = "calendarSyncOn"
    private static let calendarKey = "calendarSyncId"

    private let store = EKEventStore()
    private var pending: Task<Void, Never>?

    var enabled: Bool { UserDefaults.standard.bool(forKey: Self.enabledKey) }

    /// Asks for calendar access (full: write-only access can't move or remove events later).
    func requestAccess() async -> Bool {
        (try? await store.requestFullAccessToEvents()) ?? false
    }

    var hasAccess: Bool { EKEventStore.authorizationStatus(for: .event) == .fullAccess }

    /// Coalesces bursts of saves into one pass.
    func schedule(_ m: AppModel) {
        guard enabled, !m.practice, hasAccess else { return }
        pending?.cancel()
        pending = Task {
            try? await Task.sleep(for: .seconds(1))
            guard !Task.isCancelled else { return }
            sync(m)
        }
    }

    // ── What goes in ─────────────────────────────────────────────────────

    struct Spec: Equatable {
        var id: String
        var title: String
        var location: String
        var start: Date
        var end: Date
        var allDay: Bool
    }

    struct Input {
        var id: String
        var title: String
        var course: String
        var due: Date?
        var isTest: Bool
        var finished: Bool
    }

    /// Two weeks back to three months ahead.
    nonisolated static let back: TimeInterval = 14 * 86400, ahead: TimeInterval = 90 * 86400

    /// The event for one item, or nil when it stays out (no date, finished, out of range).
    /// A test is all day on its day (Schoology's time is often when it closes); so is work
    /// due at 11:59 pm. Other work is a 30-minute block ending when it's due.
    nonisolated static func spec(_ i: Input, now: Date, calendar: Calendar = .current) -> Spec? {
        guard let due = i.due, !i.finished, due > now - back, due < now + ahead else { return nil }
        let c = calendar.dateComponents([.hour, .minute], from: due)
        let allDay = i.isTest || (c.hour == 23 && c.minute == 59)
        let start = allDay ? calendar.startOfDay(for: due) : due.addingTimeInterval(-30 * 60)
        let end = allDay ? calendar.date(byAdding: .day, value: 1, to: start)! : due
        return Spec(id: i.id, title: i.isTest ? "Test: \(i.title)" : i.title, location: i.course, start: start, end: end, allDay: allDay)
    }

    // ── Writing ──────────────────────────────────────────────────────────

    func sync(_ m: AppModel) {
        guard enabled, hasAccess, let cal = deweyCalendar(create: true) else { return }
        let now = Date()
        let wanted = m.todoItems().compactMap { it in
            Self.spec(.init(id: it.schoologyId, title: it.title, course: WidgetBridge.course(m, it), due: it.due,
                            isTest: it.type == .exam,
                            // A test stays until its day, studied or not.
                            finished: it.type == .exam ? (it.submitted || it.turnedIn) : m.isFinished(it)),
                      now: now)
        }
        let byId = Dictionary(wanted.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let range = store.predicateForEvents(withStart: now - Self.back - 86400, end: now + Self.ahead + 86400, calendars: [cal])
        var seen = Set<String>()
        for e in store.events(matching: range) {
            guard let id = e.url.flatMap(AppLink.assignmentId), let s = byId[id], !seen.contains(id) else {
                try? store.remove(e, span: .thisEvent, commit: false)
                continue
            }
            seen.insert(id)
            if e.title != s.title || e.location != s.location || e.startDate != s.start || e.endDate != s.end || e.isAllDay != s.allDay {
                fill(e, s)
                try? store.save(e, span: .thisEvent, commit: false)
            }
        }
        for s in wanted where !seen.contains(s.id) {
            let e = EKEvent(eventStore: store)
            e.calendar = cal
            fill(e, s)
            try? store.save(e, span: .thisEvent, commit: false)
        }
        try? store.commit()
    }

    private func fill(_ e: EKEvent, _ s: Spec) {
        e.title = s.title
        e.location = s.location.isEmpty ? nil : s.location
        e.isAllDay = s.allDay
        e.startDate = s.start
        e.endDate = s.end
        e.url = AppLink.assignment(s.id)
        e.notes = "From Dewey. Tap the link to open it."
    }

    /// Turning it off takes the whole Dewey calendar away.
    func disable() {
        pending?.cancel()
        UserDefaults.standard.set(false, forKey: Self.enabledKey)
        if hasAccess, let cal = deweyCalendar(create: false) { try? store.removeCalendar(cal, commit: true) }
        UserDefaults.standard.removeObject(forKey: Self.calendarKey)
    }

    private func deweyCalendar(create: Bool) -> EKCalendar? {
        if let id = UserDefaults.standard.string(forKey: Self.calendarKey), let c = store.calendar(withIdentifier: id) { return c }
        guard create else { return nil }
        let c = EKCalendar(for: .event, eventStore: store)
        c.title = "Dewey"
        c.cgColor = CGColor(srgbRed: 0x0F / 255, green: 0x6E / 255, blue: 0x56 / 255, alpha: 1)
        // The default calendar's account (iCloud when it's on), else the phone itself.
        guard let source = store.defaultCalendarForNewEvents?.source ?? store.sources.first(where: { $0.sourceType == .local }) else { return nil }
        c.source = source
        do {
            try store.saveCalendar(c, commit: true)
        } catch {
            // Some accounts refuse new calendars: fall back to the phone.
            guard let local = store.sources.first(where: { $0.sourceType == .local }), local != source else { return nil }
            c.source = local
            guard (try? store.saveCalendar(c, commit: true)) != nil else { return nil }
        }
        UserDefaults.standard.set(c.calendarIdentifier, forKey: Self.calendarKey)
        return c
    }
}
