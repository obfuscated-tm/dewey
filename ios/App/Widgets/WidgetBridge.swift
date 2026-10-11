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
        pending?.cancel()
        pending = Task {
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            write(m)
            await syncActivities(m)
        }
    }

    private static func write(_ m: AppModel) {
        let now = Date()
        let open = m.todoItems()
            .filter { !m.isFinished($0) && !TodoLogic.isOlder(due: $0.due, now: now) }
            .sorted { ($0.due ?? .distantFuture) < ($1.due ?? .distantFuture) }
            .prefix(12)
        let snap = WidgetSnapshot(
            items: open.map { WidgetItem(id: $0.schoologyId, title: $0.title, course: course(m, $0), due: $0.due, isTest: $0.type == .exam) },
            updatedAt: m.todoAsOf ?? now,
            signedIn: m.session == .signedIn,
            accentHex: Theme.accentHex)
        // Compare without the timestamp so a no-change refresh doesn't spend the reload budget.
        var key = snap
        key.updatedAt = .distantPast
        let data = try? JSONEncoder().encode(key)
        guard data != lastData else { return }
        lastData = data
        SharedStore.save(snap)
        WidgetCenter.shared.reloadAllTimelines()
    }

    static func course(_ m: AppModel, _ it: ItemState) -> String {
        it.sectionId.flatMap { m.course($0)?.shortTitle } ?? it.courseName ?? ""
    }

    // ── Live Activities ──────────────────────────────────────────────────

    static var activitiesAllowed: Bool { ActivityAuthorizationInfo().areActivitiesEnabled }

    /// A countdown makes sense for work due in the next 12 hours (an activity lives 8 h, then 4 h on the Lock Screen).
    static func canCountDown(_ it: ItemState, now: Date = Date()) -> Bool {
        guard let d = it.due else { return false }
        return d > now && d.timeIntervalSince(now) < 12 * 3600
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
