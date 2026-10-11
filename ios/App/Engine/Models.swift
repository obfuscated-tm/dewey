import Foundation

// Codable mirrors of the engine's results (docs/ios/ENGINE.md, "Engine API").
// Decoded with JSONDecoder.engine (snake_case → camelCase). Unknown fields are
// ignored; anything Schoology might leave out is optional.

struct Course: Codable, Hashable, Identifiable {
    var sectionId: String
    var title: String
    var sectionTitle: String
    var id: String { sectionId }

    /// "AP Physics 1 - 12345: P3" → "AP Physics 1" (the course code goes).
    var shortTitle: String {
        let t = title.replacingOccurrences(of: #"\s*-\s*\d{3,6}\s*$"#, with: "", options: .regularExpression)
        return t.isEmpty ? title : t
    }

    /// "P3", "Period 3" … from the section title, or the section title itself.
    var periodLabel: String {
        if let m = sectionTitle.firstMatch(of: /(?i)\b(?:p(?:er(?:iod)?)?\.?\s*)(\d)\b/) { return "P\(m.1)" }
        return sectionTitle
    }

    /// A class, not a hub or advisory: "Name - 12345: …" (overlays/todo.js isClassCard).
    var isClass: Bool {
        "\(title): \(sectionTitle)".firstMatch(of: /^.+ - \d{3,5}:/) != nil || title.firstMatch(of: /- \d{3,5}$/) != nil
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
    var author: String?
    var date: String?
    var bodyMd: String?
}
