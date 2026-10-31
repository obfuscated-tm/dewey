import Foundation

// Codable mirrors of the engine's results (docs/ios/ENGINE.md, "Engine API").
// Decoded with JSONDecoder.engine (snake_case → camelCase). Unknown fields are
// ignored; anything Schoology might leave out is optional.

struct Course: Codable, Hashable, Identifiable {
    var sectionId: String
    var title: String
    var sectionTitle: String
    var id: String { sectionId }

    /// "Chemistry H - 12345: P3" → "Chemistry H" (the course code goes).
    var shortTitle: String { Self.short(title) }

    /// The same for any Schoology course name, e.g. a notification's "Spanish 2 - 1404: GarciaJ p4 T1" → "Spanish 2".
    static func short(_ title: String) -> String {
        let t = title.replacingOccurrences(of: #"\s*-\s*\d{3,6}\b.*$"#, with: "", options: .regularExpression)
        return t.isEmpty ? title : t
    }

    /// "P3" from "P3", "Per 3", "Period 3" … in the section title, else "" (districts name
    /// sections differently, and a raw title like "1404: GarciaJ T1" is noise next to a class).
    var periodLabel: String {
        if let m = sectionTitle.firstMatch(of: /(?i)\b(?:p(?:er(?:iod)?)?\.?\s*)(\d)\b/) { return "P\(m.1)" }
        return ""
    }

    /// A class, not a hub or advisory: the student's choice in Settings, else the guess from the name.
    var isClass: Bool { CourseKinds.choice(sectionId) ?? looksLikeClass }

    /// The guess: "Name - 12345: …" (overlays/todo.js isClassCard). Other schools may name classes differently.
    var looksLikeClass: Bool {
        "\(title): \(sectionTitle)".firstMatch(of: /^.+ - \d{3,5}:/) != nil || title.firstMatch(of: /- \d{3,5}$/) != nil
    }
}

/// Class or group, when the student picked one (Settings → Classes and groups).
/// A class gets a book, grades and To Do; a group's posts go to School updates.
enum CourseKinds {
    static let classKey = "courseKindClass", groupKey = "courseKindGroup"

    static func ids(_ key: String) -> Set<String> {
        Set((UserDefaults.standard.string(forKey: key) ?? "").split(separator: ",").map(String.init))
    }

    /// true: a class, false: a group, nil: go by the name.
    static func choice(_ sectionId: String) -> Bool? {
        if ids(classKey).contains(sectionId) { return true }
        if ids(groupKey).contains(sectionId) { return false }
        return nil
    }

    /// Saves the choice; one that matches the name's guess isn't kept.
    static func set(_ c: Course, isClass: Bool) {
        var cls = ids(classKey), grp = ids(groupKey)
        cls.remove(c.sectionId); grp.remove(c.sectionId)
        if isClass != c.looksLikeClass { if isClass { cls.insert(c.sectionId) } else { grp.insert(c.sectionId) } }
        UserDefaults.standard.set(cls.sorted().joined(separator: ","), forKey: classKey)
        UserDefaults.standard.set(grp.sorted().joined(separator: ","), forKey: groupKey)
    }
}

struct TodoLists: Codable {
    var overdue: [EventItem]
    var upcoming: [EventItem]
}

/// A row of /v2/events/{list} (reader/parse/sync.js parseEventsJson).
struct EventItem: Codable, Hashable {
    var schoologyId: String
    var title: String
    var course: String?
    var sectionId: String?
    var due: String?
    var graded: Bool?
    var url: String?

    var isQuiz: Bool { (url ?? "").contains("/assessment") }
}

struct SubmissionStatus: Codable, Equatable {
    var state: String           // submitted | not_submitted | no_dropbox | unknown
    var late: Bool?
    var quiz: Bool?
}

struct Attachment: Codable, Hashable {
    var url: String
    var title: String
}

struct Assignment: Codable {
    var id: String
    var title: String?
    var type: String?           // assignment | quiz | external_tool
    var due: String?
    var instructionsMd: String?
    var attachments: [Attachment]
    var submission: SubmissionStatus?
    var dropboxUrl: String?
    var quiz: QuizInfo?         // a newer quiz: its landing page's facts
}

/// reader/parse/quiz.js quizInfo(): what a newer quiz's landing page says.
struct QuizInfo: Codable, Equatable {
    struct Attempt: Codable, Equatable, Hashable {
        var n: Int
        var finished: Bool
        var at: String?
        var minutes: Double?
        var canView: Bool
    }
    var title: String?
    var instructions: String?
    var attemptsAllowed: Int?   // nil: no limit
    var attemptsTaken: Int
    var timeLimitMin: Double?
    var opensAt: String?
    var closesAt: String?
    var overdue: Bool?
    var score: Double?
    var maxPoints: Double?
    var letter: String?
    var state: String           // in_progress | submitted | not_started
    var attempts: [Attempt]
}

struct RubricRef: Codable, Hashable {
    var rubricId: String?
    var enrollmentId: String?
}

/// One parseGrades row.
struct GradeRow: Codable, Hashable {
    var level: String
    var title: String
    var due: String?
    var grade: String?
    var comment: String?
    var weight: Double?
    var id: String?
    var rubric: RubricRef?
}

/// engine.js courseGrade().
struct CourseGrade: Codable {
    struct Category: Codable, Hashable {
        var title: String
        var weight: Double?
        var earned: Double
        var possible: Double
        var pct: Double?
        var floor: Double?
        var floorStatus: String   // none | any | out | need
    }
    struct Item: Codable, Hashable, Identifiable {
        var id: String
        var category: String
        var title: String
        var due: String?          // 'YYYY-MM-DDTHH:MM', wall clock
        var grade: String
        var comment: String
        var rubric: RubricRef?
        var earned: Double?
        var possible: Double?
        var pct: Double?
        var impact: Double?
    }
    struct Point: Codable, Hashable {
        var day: String?
        var pct: Double?
        var items: [String]
    }
    var period: String?
    var pct: Double?
    var letter: String?
    var mode: String
    var categories: [Category]
    var items: [Item]
    var history: [Point]
    var periods: [String]
}

struct PlanResult: Codable {
    var status: String            // any | out | need | none
    var need: Double?
}

struct Rubric: Codable {
    struct Criterion: Codable, Hashable {
        var title: String
        var max: Double?
        var earned: Double?
        var level: String?
        var comment: String
    }
    var title: String
    var total: Double?
    var earned: Double?
    var criteria: [Criterion]
}

struct DropboxComment: Codable, Hashable {
    var author: String?
    var text: String
}

struct DropboxInfo: Codable {
    var grade: String?
    var comments: [DropboxComment]

    /// Without Schoology's own lines ("Revision 1 submitted").
    var teacherComments: [DropboxComment] {
        comments.filter { $0.text.firstMatch(of: /(?i)^revision \d+ (submitted|resubmitted)/) == nil }
    }
}

/// A Materials folder row (reader/parse/mcp.js materialsRows).
struct MaterialRow: Codable, Hashable {
    var kind: String              // folder | document | assignment | page | discussion | quiz | other
    var title: String
    var id: String?
    var url: String?
    /// For ForEach: rows have no guaranteed id.
    var key: String { "\(kind):\(id ?? url ?? title)" }
}

struct FileLink: Codable, Hashable {
    var title: String
    var url: String
    var ext: String?
}

struct Material: Codable {
    var url: String?
    var kind: String
    var title: String?
    var bodyMd: String?
    var target: String?
    var files: [FileLink]
    var links: [FileLink]
}

struct FeedPost: Codable, Hashable {
    var id: String?
    var author: String?
    var date: String?
    var time: String?
    var bodyMd: String?
}

// ── Inbox (reader/parse/inbox.js) ────────────────────────────────────────

/// messages({ page }).
struct MessageList: Codable {
    var threads: [MessageThread]
    var unreadCount: Int?
    var hasMore: Bool?
}

/// A row of the messages list.
/// `group` is the "[Course: section]" prefix Schoology adds, nil for a direct message.
struct MessageThread: Codable, Hashable, Identifiable {
    var id: String
    var subject: String
    var group: String?
    var preview: String?
    var from: String?
    var fromId: String?
    var date: String?
    var unread: Bool?

    var when: Date? { Fmt.date(date) }
}

/// message({ id }): the whole thread. Fetching it marks it read on Schoology.
struct MessageDetail: Codable {
    struct Message: Codable, Hashable {
        var author: String?
        var authorId: String?
        var date: String?
        var bodyMd: String?
        var attachments: [Attachment]
    }
    var id: String
    var subject: String
    var group: String?
    var participants: [String]
    var messages: [Message]
}

/// notifications({ page }).
struct NoticeList: Codable {
    var items: [Notice]
    var hasMore: Bool?
}

/// A /home/notifications row: posted materials (`material`) or new grades (`grade`).
struct Notice: Codable, Hashable, Identifiable {
    struct Item: Codable, Hashable {
        var title: String
        var url: String
        var kind: String          // assignment | assessment | page | discussion | event | material | other
    }
    var key: String
    var type: String
    var course: String?
    var sectionId: String?
    var text: String
    var date: String?
    var items: [Item]
    var id: String { key }

    var when: Date? { Fmt.date(date) }
}
