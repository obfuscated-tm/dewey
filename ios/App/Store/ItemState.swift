import Foundation
import SwiftData

enum ItemType: String, Codable, CaseIterable {
    case assignment, exam, classwork, task

    var label: String {
        switch self {
        case .assignment: return "HW"
        case .exam: return "TEST"
        case .classwork: return "CW"
        case .task: return "TASK"
        }
    }
    var name: String {
        switch self {
        case .assignment: return "HW"
        case .exam: return "Test"
        case .classwork: return "CW"
        case .task: return "Task"
        }
    }
}

/// The student's own state for one Schoology item, plus what the app last saw
/// of it on Schoology. Keyed by the Schoology id. Survives relaunch; keeps
/// paper work Schoology dropped from its lists (docs/ios/FINDINGS.md).
@Model
final class ItemState {
    @Attribute(.unique) var schoologyId: String
    var sectionId: String?
    var title: String
    var courseName: String?
    var due: Date?
    var url: String?

    // The student's marks.
    var done: Bool = false
    var studied: Bool = false
    var turnedIn: Bool = false
    var typeOverride: String?
    var dismissed: Bool = false

    // What Schoology said last.
    var inSchoology: Bool = true
    var graded: Bool = false
    var statusState: String?          // submitted | not_submitted | no_dropbox | unknown
    var statusLate: Bool?
    var statusCheckedAt: Date?
    var categoryHint: String?         // gradebook category, for the type rule
    var labURL: String?               // an outside submit page from the instructions
    var external: Bool = false        // a Google Assignment

    var neoPlanId: String?
    var firstSeen: Date = Date()
    var lastSeen: Date = Date()
    var updatedAt: Date = Date()

    init(schoologyId: String, title: String) {
        self.schoologyId = schoologyId
        self.title = title
    }

    var isQuiz: Bool { (url ?? "").contains("/assessment") }
    var submitted: Bool { statusState == "submitted" }
    var hasDropbox: Bool { statusState == "submitted" || statusState == "not_submitted" }

    var type: ItemType {
        get { ItemType(rawValue: typeOverride ?? "") ?? TodoLogic.guessType(title: title, category: categoryHint, isQuiz: isQuiz) }
        set { typeOverride = newValue.rawValue }
    }

    func touch() { updatedAt = Date() }
}

/// Per class settings.
@Model
final class CourseState {
    @Attribute(.unique) var sectionId: String
    var aCutoff: Double?

    init(sectionId: String) { self.sectionId = sectionId }
}

/// Pure To Do rules, separate from SwiftData so tests can run them.
enum TodoLogic {
    static let olderDays = 60

    /// TEST if the gradebook category says Test/Quiz/Exam/Summative, the title says
    /// Exam/Quiz/Test/Interro, or it's a quiz page; else HW.
    static func guessType(title: String, category: String?, isQuiz: Bool) -> ItemType {
        if isQuiz { return .exam }
        if let c = category, c.firstMatch(of: /(?i)\b(test|quiz|exam|summative|assessment)/) != nil { return .exam }
        if title.firstMatch(of: /(?i)\b(exam|quiz|interro|test|midterm|final exam)\b/) != nil { return .exam }
        return .assignment
    }

    struct Snapshot {
        var inSchoology: Bool
        var done: Bool
        var turnedIn: Bool
        var submitted: Bool
        var graded: Bool
        var dismissed: Bool
    }

    /// On the list: everything Schoology still lists (done rows stay until it
    /// drops them), plus work it dropped that the student hasn't finished.
    static func isVisible(_ s: Snapshot) -> Bool {
        if s.dismissed || s.graded { return false }
        if s.inSchoology { return true }
        return !(s.done || s.turnedIn || s.submitted)
    }

    /// Whether a past-due row may say "late": not once it's handed in, marked
    /// done, or a test that isn't an online quiz (nothing to hand in after it).
    static func showsLate(type: ItemType, isQuiz: Bool, done: Bool, turnedIn: Bool, submitted: Bool) -> Bool {
        if submitted || turnedIn { return false }
        if type == .exam { return isQuiz }
        return !done
    }

    /// Calendar days late (≥ 0) or nil when not overdue.
    static func daysLate(due: Date?, now: Date = Date(), calendar: Calendar = .current) -> Int? {
        guard let due, due < now else { return nil }
        let d = calendar.dateComponents([.day], from: calendar.startOfDay(for: due), to: calendar.startOfDay(for: now)).day ?? 0
        return max(0, d)
    }

    static func isOlder(due: Date?, now: Date = Date()) -> Bool {
        (daysLate(due: due, now: now) ?? 0) > olderDays
    }

    enum Bucket { case overdue, today, week, later }

    static func bucket(due: Date?, now: Date = Date(), calendar: Calendar = .current) -> Bucket {
        guard let due else { return .later }
        if due < now { return .overdue }
        if calendar.isDate(due, inSameDayAs: now) { return .today }
        if due < calendar.date(byAdding: .day, value: 7, to: calendar.startOfDay(for: now))! { return .week }
        return .later
    }
}
