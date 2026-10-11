import Foundation
@preconcurrency import UserNotifications

/// Local notifications only (free account: no push). Evening-before reminders
/// for open work; "new grade" alerts when a refresh finds one (best effort).
@MainActor
final class Reminders {
    static let shared = Reminders()

    static let enabledKey = "remindersOn"
    static let hourKey = "reminderHour"
    static let gradesKey = "gradeAlertsOn"

    var enabled: Bool { UserDefaults.standard.bool(forKey: Self.enabledKey) }
    var hour: Int { UserDefaults.standard.object(forKey: Self.hourKey) as? Int ?? 19 }
    var gradeAlerts: Bool { UserDefaults.standard.object(forKey: Self.gradesKey) as? Bool ?? true }

    func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    /// Replaces every due reminder: one per open item, at `hour` the evening before it's due.
    func schedule(items: [ItemState], courses: [Course]) {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { reqs in
            center.removePendingNotificationRequests(withIdentifiers: reqs.map(\.identifier).filter { $0.hasPrefix("due-") })
        }
        guard enabled else { return }
        let cal = Calendar.current
        let now = Date()
        for it in items where !(it.done || it.turnedIn || it.submitted || it.studied) {
            guard let due = it.due, due > now,
                  let dayBefore = cal.date(byAdding: .day, value: -1, to: cal.startOfDay(for: due)),
                  let fire = cal.date(bySettingHour: hour, minute: 0, second: 0, of: dayBefore), fire > now else { continue }
            let c = UNMutableNotificationContent()
            c.title = it.title
            let course = courses.first { $0.sectionId == it.sectionId }?.shortTitle ?? it.courseName ?? ""
            c.body = "\(course.isEmpty ? "" : course + " · ")Due \(Fmt.due(due, now: fire))"
            c.sound = .default
            c.userInfo = ["schoologyId": it.schoologyId]
            let trig = UNCalendarNotificationTrigger(dateMatching: cal.dateComponents([.year, .month, .day, .hour, .minute], from: fire), repeats: false)
            center.add(UNNotificationRequest(identifier: "due-\(it.schoologyId)", content: c, trigger: trig))
        }
    }

    func newGrades(course: String, items: [CourseGrade.Item]) {
        guard gradeAlerts, enabled else { return }
        let c = UNMutableNotificationContent()
        c.title = "New grade in \(course)"
        c.body = items.prefix(3).map { "\($0.title): \($0.grade)" }.joined(separator: "\n")
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "grade-\(UUID())", content: c, trigger: nil))
    }
}
