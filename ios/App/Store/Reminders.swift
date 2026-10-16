import Foundation
@preconcurrency import UserNotifications

/// Local notifications only (free account: no push). Due reminders (R3: 2 h
/// before an unsubmitted dropbox, the night before a test, mornings, evenings);
/// grade, message, material and signed-out alerts when a refresh finds one.
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
        lastPlan = nil
        return (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    /// Every due reminder, worked out from the To Do list and replaced as a set
    /// (fixed ids, so a reschedule never doubles one). Debounced: saves call it often.
    func schedule(items: [ItemState], courses: [Course]) {
        let inputs = items.map { it in
            DueInput(id: it.schoologyId, title: it.title,
                     course: courses.first { $0.sectionId == it.sectionId }?.shortTitle ?? it.courseName ?? "",
                     due: it.due, isTest: it.type == .exam, hasDropbox: it.hasDropbox,
                     done: it.done, studied: it.studied, turnedIn: it.turnedIn, submitted: it.submitted)
        }
        let plan = Self.plan(inputs, settings: settings, now: Date())
        // Most saves change no reminder: skip re-adding the same set.
        guard plan != lastPlan else { return }
        pending?.cancel()
        pending = Task {
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            lastPlan = plan
            let center = UNUserNotificationCenter.current()
            let keep = Set(plan.map(\.id))
            let stale = await center.pendingNotificationRequests().map(\.identifier)
                .filter { id in Self.duePrefixes.contains { id.hasPrefix($0) } && !keep.contains(id) }
            center.removePendingNotificationRequests(withIdentifiers: stale)
            let cal = Calendar.current
            for p in plan {
                let c = UNMutableNotificationContent()
                c.title = p.title
                c.body = p.body
                c.sound = .default
                if let sid = p.schoologyId { c.userInfo = ["schoologyId": sid] }
                let trig = UNCalendarNotificationTrigger(dateMatching: cal.dateComponents([.year, .month, .day, .hour, .minute], from: p.fire), repeats: false)
                try? await center.add(UNNotificationRequest(identifier: p.id, content: c, trigger: trig))
            }
        }
    }
    private var pending: Task<Void, Never>?
    /// The set last handed to iOS (nil after a permission prompt, so it's added again).
    private var lastPlan: [Planned]?

    static let soonKey = "dueSoonOn"
    static let testNightKey = "testNightOn"
    static let morningKey = "morningSummaryOn"
    static let morningHourKey = "morningHour"
    nonisolated static let duePrefixes = ["due-", "soon-", "test-", "morning-"]

    var settings: Settings {
        let d = UserDefaults.standard
        return Settings(evening: enabled, hour: hour,
                        soon: d.object(forKey: Self.soonKey) as? Bool ?? true,
                        testNight: d.object(forKey: Self.testNightKey) as? Bool ?? true,
                        morning: d.bool(forKey: Self.morningKey),
                        morningHour: d.object(forKey: Self.morningHourKey) as? Int ?? 7)
    }

    /// What the due reminders need from an item (a plain copy, so the plan is testable).
    struct DueInput {
        let id: String
        let title: String
        let course: String
        let due: Date?
        let isTest: Bool
        let hasDropbox: Bool
        var done = false
        var studied = false
        var turnedIn = false
        var submitted = false

        /// Nothing left to remind about: handed in, or done / studied.
        var finished: Bool { turnedIn || submitted || (isTest ? studied : done) }
    }

    struct Settings {
        var evening = false
        var hour = 19
        var soon = true
        var testNight = true
        var morning = false
        var morningHour = 7
    }

    struct Planned: Equatable {
        let id: String
        let fire: Date
        let title: String
        let body: String
        var schoologyId: String?
    }

    /// The reminders to have pending at `now`, soonest first (iOS keeps 64; this keeps 60):
    /// - evening before, at `hour`: homework not done (`evening`, off by default);
    /// - night before a test, at `hour`: not marked Studied (`testNight`);
    /// - 2 h before: dropbox work not submitted on Schoology, even if marked done (`soon`);
    /// - mornings, next 7 days at `morningHour`: what's due that day, skipped when nothing is (`morning`).
    nonisolated static func plan(_ items: [DueInput], settings s: Settings, now: Date, calendar cal: Calendar = .current) -> [Planned] {
        var out: [Planned] = []
        func nightBefore(_ due: Date) -> Date? {
            cal.date(byAdding: .day, value: -1, to: cal.startOfDay(for: due))
                .flatMap { cal.date(bySettingHour: s.hour, minute: 0, second: 0, of: $0) }
        }
        func with(_ course: String, _ rest: String) -> String { course.isEmpty ? rest : "\(course) · \(rest)" }
        for it in items {
            guard let due = it.due, due > now else { continue }
            if !it.isTest, s.evening, !it.finished, let fire = nightBefore(due), fire > now {
                out.append(Planned(id: "due-\(it.id)", fire: fire, title: it.title,
                                   body: with(it.course, "Due \(Fmt.due(due, now: fire))"), schoologyId: it.id))
            }
            if it.isTest, s.testNight, !it.finished, let fire = nightBefore(due), fire > now {
                out.append(Planned(id: "test-\(it.id)", fire: fire, title: "Test tomorrow: \(it.title)",
                                   body: with(it.course, "Not marked Studied yet"), schoologyId: it.id))
            }
            let soon = due.addingTimeInterval(-2 * 3600)
            if s.soon, it.hasDropbox, !it.submitted, !it.turnedIn, soon > now {
                out.append(Planned(id: "soon-\(it.id)", fire: soon, title: "Due in 2 hours: \(it.title)",
                                   body: with(it.course, "Not submitted on Schoology yet"), schoologyId: it.id))
            }
        }
        if s.morning {
            let today = cal.startOfDay(for: now)
            for d in 0..<7 {
                guard let day = cal.date(byAdding: .day, value: d, to: today),
                      let fire = cal.date(bySettingHour: s.morningHour, minute: 0, second: 0, of: day), fire > now else { continue }
                let open = items.filter { !$0.finished && $0.due.map { cal.isDate($0, inSameDayAs: day) && $0 > fire } ?? false }
                    .sorted { $0.due! < $1.due! }
                guard !open.isEmpty else { continue }
                let tests = open.filter(\.isTest)
                let hw = open.filter { !$0.isTest }
                var title = hw.isEmpty ? "" : "\(hw.count) due today"
                if !tests.isEmpty { title += (title.isEmpty ? "" : " · ") + (tests.count == 1 ? "test today" : "\(tests.count) tests today") }
                var lines = open.prefix(4).map { ($0.isTest ? "Test: " : "") + $0.title }
                if open.count > 4 { lines.append("+\(open.count - 4) more") }
                let key = cal.dateComponents([.year, .month, .day], from: day)
                out.append(Planned(id: "morning-\(key.year!)-\(key.month!)-\(key.day!)", fire: fire,
                                   title: title.prefix(1).uppercased() + title.dropFirst(), body: lines.joined(separator: "\n")))
            }
        }
        return Array(out.sorted { $0.fire < $1.fire }.prefix(60))
    }

    /// "Test today", "Test tomorrow", "Test in 3 days"; nil when it's past.
    nonisolated static func testCountdown(due: Date?, now: Date, calendar cal: Calendar = .current) -> String? {
        guard let due, due > now else { return nil }
        let days = cal.dateComponents([.day], from: cal.startOfDay(for: now), to: cal.startOfDay(for: due)).day ?? 0
        switch days {
        case 0: return "Test today"
        case 1: return "Test tomorrow"
        default: return "Test in \(days) days"
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
            UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "notice-\(n.key)", content: c, trigger: nil))
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
        c.body = "Open Dewey to sign in. Until then your list and widgets won't update."
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "signed-out", content: c, trigger: nil))
    }

    func sessionDidStart() {
        UserDefaults.standard.set(false, forKey: Self.sessionEndedKey)
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["signed-out"])
    }
}
