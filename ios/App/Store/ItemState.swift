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
/// A task the student added (`mine`) is a row too, keyed `mine-<uuid>`: it
/// never reaches Schoology or neo-plan, and lives only on the phone.
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

    // The student's own task (TaskEditor), never on Schoology.
    var mine: Bool = false
    var notes: String?

    // Plan (PlanLogic): the section the student dragged it to (nil = by due date)
    // and its place there (nil = not placed yet, slotted in by due date).
    var planSection: String?
    var planRank: Double?

    var firstSeen: Date = Date()
    var lastSeen: Date = Date()
    var updatedAt: Date = Date()

    init(schoologyId: String, title: String) {
        self.schoologyId = schoologyId
        self.title = title
    }

    static let minePrefix = "mine-"
    static func isMine(_ id: String) -> Bool { id.hasPrefix(minePrefix) }

    /// A new task of the student's own.
    convenience init(mineTitle title: String) {
        self.init(schoologyId: Self.minePrefix + UUID().uuidString, title: title)
        mine = true
        typeOverride = ItemType.task.rawValue
    }

    var isQuiz: Bool { (url ?? "").contains("/assessment") }
    /// Schoology work neo-plan can take: not the student's own, not an event.
    var isSchoologyAssignment: Bool { !mine && !(url ?? "").contains("/event/") }
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
    /// The grade the student aims for (a % from `Goal.scale`); nil = the A line.
    var goal: Double?

    init(sectionId: String) { self.sectionId = sectionId }
}

/// The letters a goal can be, from grademath's SCALE (A down to C-).
enum Goal {
    static let scale: [(letter: String, pct: Double)] = [("A", 93), ("A-", 90), ("B+", 87), ("B", 83), ("B-", 80), ("C+", 77), ("C", 73), ("C-", 70)]

    static func letter(_ pct: Double) -> String { scale.first { pct >= $0.pct - 1e-9 }?.letter ?? "D" }
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
        /// The student's own task, and (for one) whether it was finished today.
        var mine = false
        var finishedToday = false
    }

    /// On the list: everything Schoology still lists (done rows stay until it
    /// drops them), plus work it dropped that the student hasn't finished.
    /// The student's own tasks stay until done, and a done one until the day ends.
    static func isVisible(_ s: Snapshot) -> Bool {
        if s.mine { return !s.dismissed && (!(s.done || s.turnedIn) || s.finishedToday) }
        if s.dismissed || s.graded { return false }
        if s.inSchoology { return true }
        return !(s.done || s.turnedIn || s.submitted)
    }

    /// Whether a past-due row may say "late": only while Schoology still lists it
    /// as overdue (work it dropped, like paper or lab-site work, may well be
    /// handed in), and not once it's handed in or marked done. A test is late only
    /// as an online quiz not marked studied: Schoology keeps quizzes given in class
    /// on its overdue list, and a past test you studied for is one you took.
    /// The student's own tasks always count as listed (`inSchoology: true`).
    static func showsLate(type: ItemType, isQuiz: Bool, done: Bool, turnedIn: Bool, submitted: Bool, inSchoology: Bool = true) -> Bool {
        if submitted || turnedIn || !inSchoology { return false }
        if type == .exam { return isQuiz && !done }
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
