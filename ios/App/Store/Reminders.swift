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
    static let signedOutKey = "signedOutAlertOn"
    static let messagesKey = "messageAlertsOn"
    static let materialsKey = "materialAlertsOn"
    /// Set when Schoology ends the session; background refreshes stop until sign-in.
    static let sessionEndedKey = "sessionEnded"

    var enabled: Bool { UserDefaults.standard.bool(forKey: Self.enabledKey) }
    var hour: Int { UserDefaults.standard.object(forKey: Self.hourKey) as? Int ?? 19 }
    var gradeAlerts: Bool { UserDefaults.standard.object(forKey: Self.gradesKey) as? Bool ?? true }
    var signedOutAlert: Bool { UserDefaults.standard.object(forKey: Self.signedOutKey) as? Bool ?? true }
    var sessionEnded: Bool { UserDefaults.standard.bool(forKey: Self.sessionEndedKey) }
    var messageAlerts: Bool { UserDefaults.standard.object(forKey: Self.messagesKey) as? Bool ?? true }
    var materialAlerts: Bool { UserDefaults.standard.object(forKey: Self.materialsKey) as? Bool ?? true }

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

    func newGrades(course: String, items: [CourseGrade.Item], from: Double?, to: Double?) {
        guard gradeAlerts else { return }
        let c = UNMutableNotificationContent()
        c.title = "New grade in \(course)"
        c.body = Self.gradeAlertBody(items: items, from: from, to: to)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "grade-\(UUID())", content: c, trigger: nil))
    }

    /// "Lab 4: 18 / 20 · 92.1% → 92.6%"; with more items, the class change goes on its own line.
    nonisolated static func gradeAlertBody(items: [CourseGrade.Item], from: Double?, to: Double?) -> String {
        var change = ""
        if let to {
            change = from.map { abs($0 - to) >= 0.05 ? "\(Fmt.pct($0)) → \(Fmt.pct(to))" : "still \(Fmt.pct(to))" } ?? Fmt.pct(to)
        }
        var lines = items.prefix(3).map { "\($0.title): \($0.grade)" }
        if items.count > 3 { lines.append("+\(items.count - 3) more") }
        if change.isEmpty { return lines.joined(separator: "\n") }
        if lines.count == 1 { return "\(lines[0]) · \(change)" }
        return (lines + ["Class: \(change)"]).joined(separator: "\n")
    }

    /// One alert per new message from a teacher: sender, subject, preview.
    func newMessages(_ threads: [MessageThread]) {
        guard messageAlerts else { return }
        for t in threads.prefix(3) {
            let c = UNMutableNotificationContent()
            c.title = t.from.map { "Message from \($0)" } ?? "New message"
            c.body = [t.subject, t.preview].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: "\n")
            c.sound = .default
            c.userInfo = ["messageId": t.id]
            UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "message-\(t.id)", content: c, trigger: nil))
        }
    }

    /// New materials posted in a class: one alert per Schoology notification ("French 2 posted …").
    func newMaterials(_ notices: [Notice]) {
        guard materialAlerts else { return }
        for n in notices.prefix(3) {
            let c = UNMutableNotificationContent()
            c.title = n.course.map { "New in \(Course.short($0))" } ?? "New in your classes"
            c.body = n.items.prefix(3).map(\.title).joined(separator: "\n") + (n.items.count > 3 ? "\n+\(n.items.count - 3) more" : "")
            if c.body.isEmpty { c.body = n.text }
            UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "notice-\(n.key.hashValue)", content: c, trigger: nil))
        }
    }

    /// Schoology ended the session (about a day of normal use). One alert per sign-out;
    /// `notify: false` when the student signed out on purpose.
    func sessionDidEnd(notify: Bool) {
        guard !sessionEnded else { return }
        UserDefaults.standard.set(true, forKey: Self.sessionEndedKey)
        guard notify, signedOutAlert else { return }
        let c = UNMutableNotificationContent()
        c.title = "Signed out of Schoology"
        c.body = "Open Classwork to sign in. Until then your list and widgets won't update."
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "signed-out", content: c, trigger: nil))
    }

    func sessionDidStart() {
        UserDefaults.standard.set(false, forKey: Self.sessionEndedKey)
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["signed-out"])
    }
}
