import ActivityKit
import Foundation
import WidgetKit

/// Hands the open To Do rows to the widgets and keeps Live Activity
/// countdowns in step with the student's marks and Schoology's status.
@MainActor
enum WidgetBridge {
    private static var lastData: Data?
    private static var pending: Task<Void, Never>?

    /// Coalesces bursts of saves into one write and one reload.
    static func publish(_ m: AppModel) {
        // The tour's pretend week never reaches the widgets (or takes their marks).
        guard !m.practice else { return }
        pending?.cancel()
        pending = Task {
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            m.applyWidgetMarks()
            write(m)
            await syncActivities(m)
        }
    }

    /// Writes now: a background refresh may be suspended before a debounced write runs.
    static func publishNow(_ m: AppModel) async {
        guard !m.practice else { return }
        pending?.cancel()
        m.applyWidgetMarks()
        write(m)
        await syncActivities(m)
    }

    private static func write(_ m: AppModel) {
        let now = Date()
        let open = m.todoItems()
            .filter { !m.isFinished($0) && !TodoLogic.isOlder(due: $0.due, now: now) }
            .sorted { ($0.due ?? .distantFuture) < ($1.due ?? .distantFuture) }
        // The list's first 20, plus the next 3 tests even if they're further out.
        var rows = Array(open.prefix(20))
        let today = Calendar.current.startOfDay(for: now)
        for t in open.dropFirst(20).filter({ $0.type == .exam && ($0.due ?? .distantFuture) >= today }).prefix(3) { rows.append(t) }
        let snap = WidgetSnapshot(
            items: rows.map { WidgetItem(id: $0.schoologyId, title: $0.title, course: course(m, $0), due: $0.due,
                                         isTest: $0.type == .exam, marked: $0.type == .exam ? $0.studied : $0.done) },
            updatedAt: m.todoAsOf ?? now,
            signedIn: m.session == .signedIn,
            accentHex: Theme.accentHex,
            grades: latestGrades(m, keeping: SharedStore.load().grades ?? []))
        // Compare without the timestamp so a no-change refresh doesn't spend the reload budget.
        var key = snap
        key.updatedAt = .distantPast
        let data = try? JSONEncoder().encode(key)
        guard data != lastData else { return }
        lastData = data
        SharedStore.save(snap)
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// The 3 most recently graded items across classes. Classes whose gradebook
    /// isn't loaded this run keep what the last snapshot had.
    static func latestGrades(_ m: AppModel, keeping old: [WidgetGrade]) -> [WidgetGrade] {
        var all = old.filter { m.grades[$0.sectionId] == nil }
        for (sid, g) in m.grades {
            var day: [String: String] = [:]
            for p in g.history { if let d = p.day { for id in p.items { day[id] = d } } }
            let name = m.course(sid)?.shortTitle ?? ""
            all += g.items.filter { $0.earned != nil }.map {
                WidgetGrade(id: $0.id, sectionId: sid, title: $0.title, course: name, grade: $0.grade,
                            day: day[$0.id] ?? $0.due.map { String($0.prefix(10)) })
            }
        }
        return Array(all.sorted { ($0.day ?? "") > ($1.day ?? "") }.prefix(3))
    }

    static func course(_ m: AppModel, _ it: ItemState) -> String {
        it.sectionId.flatMap { m.course($0)?.shortTitle } ?? it.courseName ?? ""
    }

    // ── Live Activities ──────────────────────────────────────────────────

    static var activitiesAllowed: Bool { ActivityAuthorizationInfo().areActivitiesEnabled }

    /// iOS keeps an activity 8 h, then 4 h on the Lock Screen, so a countdown can only start 12 h out.
    nonisolated static let countdownLead: TimeInterval = 12 * 3600

    enum CountdownWindow: Equatable {
        case none                // no due time, or already past it
        case opens(Date)         // due later; the countdown can start from this time
        case open
    }

    nonisolated static func countdownWindow(due: Date?, now: Date = Date()) -> CountdownWindow {
        guard let d = due, d > now else { return .none }
        return d.timeIntervalSince(now) < countdownLead ? .open : .opens(d.addingTimeInterval(-countdownLead))
    }

    static func activity(for id: String) -> Activity<DueActivityAttributes>? {
        Activity<DueActivityAttributes>.activities.first { $0.attributes.id == id && $0.activityState == .active }
    }

    static func startCountdown(_ m: AppModel, _ it: ItemState) throws {
        guard let due = it.due, activity(for: it.schoologyId) == nil else { return }
        let attrs = DueActivityAttributes(id: it.schoologyId, title: it.title, course: course(m, it),
                                          isTest: it.type == .exam, accentHex: Theme.accentHex, startedAt: Date())
        let state = DueActivityAttributes.ContentState(due: due, finished: false)
        _ = try Activity.request(attributes: attrs, content: .init(state: state, staleDate: due.addingTimeInterval(3600)))
    }

    /// Why iOS refused to start one, in the student's words.
    static func countdownErrorMessage(_ error: Error) -> String {
        switch error as? ActivityAuthorizationError {
        case .denied?: return "Live Activities are off for Dewey. Turn them on in Settings › Dewey."
        case .globalMaximumExceeded?, .targetMaximumExceeded?:
            return "Too many Live Activities are running. End one and try again."
        case .visibility?: return "Couldn't start the countdown while Dewey was in the background. Try again."
        default: return "Couldn't start the countdown. Try again."
        }
    }

    static func stopCountdown(_ id: String) async {
        for a in Activity<DueActivityAttributes>.activities where a.attributes.id == id {
            await a.end(nil, dismissalPolicy: .immediate)
        }
    }

    /// Finished or gone from the list → show "Done" briefly, then end. Due date moved → update.
    private static func syncActivities(_ m: AppModel) async {
        for a in Activity<DueActivityAttributes>.activities {
            let it = m.item(a.attributes.id)
            if it == nil || m.isFinished(it!) || it!.dismissed {
                let done = DueActivityAttributes.ContentState(due: a.content.state.due, finished: true)
                await a.end(.init(state: done, staleDate: nil), dismissalPolicy: .after(Date().addingTimeInterval(15 * 60)))
            } else if let due = it!.due, due != a.content.state.due {
                await a.update(.init(state: .init(due: due, finished: false), staleDate: due.addingTimeInterval(3600)))
            }
        }
    }
}
