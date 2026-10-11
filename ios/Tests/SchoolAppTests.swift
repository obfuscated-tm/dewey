import XCTest
@testable import SchoolApp

/// Decoding engine JSON (snake_case) and the pure To Do rules.
final class DecodingTests: XCTestCase {
    func testCourse() throws {
        let json = #"[{"section_id":"7001","title":"AP Physics 1 - 12345","section_title":"P3"}]"#
        let c = try JSONDecoder.engine.decode([Course].self, from: Data(json.utf8))
        XCTAssertEqual(c[0].sectionId, "7001")
        XCTAssertEqual(c[0].shortTitle, "AP Physics 1")
        XCTAssertEqual(c[0].periodLabel, "P3")
        XCTAssertTrue(c[0].isClass)
    }

    func testTodoLists() throws {
        let json = #"{"overdue":[{"schoology_id":"1","title":"Lab","section_id":"7001","due":"2026-10-01T23:59:00-07:00","graded":false,"url":"/assignment/1"}],"upcoming":[{"schoology_id":"2","title":"Quiz 3","url":"/course/7001/assessments/2"}]}"#
        let t = try JSONDecoder.engine.decode(TodoLists.self, from: Data(json.utf8))
        XCTAssertEqual(t.overdue.first?.schoologyId, "1")
        XCTAssertFalse(t.overdue[0].isQuiz)
        XCTAssertTrue(t.upcoming[0].isQuiz)
    }

    func testCourseGrade() throws {
        let json = #"""
        {"period":"S1","pct":94.2,"letter":"A","mode":"weighted",
         "categories":[{"title":"Tests","weight":60,"earned":90,"possible":100,"pct":90,"floor":null,"floor_status":"need"}],
         "items":[{"id":"55","category":"Tests","title":"Unit 1","due":"2026-09-20T08:00","grade":"90 / 100","comment":"",
                   "rubric":{"rubric_id":"9","enrollment_id":"8"},"earned":90,"possible":100,"pct":90,"impact":-1.2}],
         "history":[{"day":"2026-09-20","pct":94.2,"items":["55"]}],"periods":["S1"]}
        """#
        let g = try JSONDecoder.engine.decode(CourseGrade.self, from: Data(json.utf8))
        XCTAssertEqual(g.categories[0].floorStatus, "need")
        XCTAssertEqual(g.items[0].rubric?.rubricId, "9")
        XCTAssertEqual(g.items[0].impact, -1.2)
    }

    func testDropboxFiltersSchoologyLines() throws {
        let json = #"{"grade":null,"comments":[{"author":"Me","text":"Revision 1 submitted"},{"author":"Ms. K","text":"Nice work"}]}"#
        let d = try JSONDecoder.engine.decode(DropboxInfo.self, from: Data(json.utf8))
        XCTAssertEqual(d.teacherComments.map(\.text), ["Nice work"])
    }
}

final class TodoLogicTests: XCTestCase {
    func testGuessType() {
        XCTAssertEqual(TodoLogic.guessType(title: "Ch 3 problems", category: nil, isQuiz: false), .assignment)
        XCTAssertEqual(TodoLogic.guessType(title: "Unit 2 Test", category: nil, isQuiz: false), .exam)
        XCTAssertEqual(TodoLogic.guessType(title: "Reading", category: "Summative", isQuiz: false), .exam)
        XCTAssertEqual(TodoLogic.guessType(title: "Anything", category: nil, isQuiz: true), .exam)
        XCTAssertEqual(TodoLogic.guessType(title: "Contest prep", category: nil, isQuiz: false), .assignment)
    }

    func testVisibility() {
        let base = TodoLogic.Snapshot(inSchoology: true, done: false, turnedIn: false, submitted: false, graded: false, dismissed: false)
        XCTAssertTrue(TodoLogic.isVisible(base))
        var s = base; s.graded = true
        XCTAssertFalse(TodoLogic.isVisible(s))
        s = base; s.inSchoology = false
        XCTAssertTrue(TodoLogic.isVisible(s), "dropped by Schoology but not finished: stays")
        s.done = true
        XCTAssertFalse(TodoLogic.isVisible(s))
        s = base; s.dismissed = true
        XCTAssertFalse(TodoLogic.isVisible(s))
    }

    func testDaysLateAndBuckets() {
        let cal = Calendar(identifier: .gregorian)
        let now = cal.date(from: DateComponents(year: 2026, month: 10, day: 10, hour: 12))!
        let due = cal.date(from: DateComponents(year: 2026, month: 10, day: 8, hour: 23))!
        XCTAssertEqual(TodoLogic.daysLate(due: due, now: now, calendar: cal), 2)
        XCTAssertNil(TodoLogic.daysLate(due: now.addingTimeInterval(60), now: now, calendar: cal))
        XCTAssertEqual(TodoLogic.bucket(due: due, now: now, calendar: cal), .overdue)
        XCTAssertEqual(TodoLogic.bucket(due: now.addingTimeInterval(3600), now: now, calendar: cal), .today)
        XCTAssertEqual(TodoLogic.bucket(due: now.addingTimeInterval(3 * 86400), now: now, calendar: cal), .week)
        XCTAssertEqual(TodoLogic.bucket(due: now.addingTimeInterval(30 * 86400), now: now, calendar: cal), .later)
        XCTAssertTrue(TodoLogic.isOlder(due: now.addingTimeInterval(-70 * 86400), now: now))
    }

    func testNativeRoutes() {
        XCTAssertEqual(WebConfig.nativeRoute(for: URL(string: "https://fuhsd.schoology.com/assignment/123")), .assignment("123"))
        XCTAssertEqual(WebConfig.nativeRoute(for: URL(string: "https://fuhsd.schoology.com/course/7/student_grades")), .grades("7"))
        XCTAssertNil(WebConfig.nativeRoute(for: URL(string: "https://example.com/assignment/1")))
    }
}

final class WidgetDataTests: XCTestCase {
    func testAppLink() {
        XCTAssertEqual(AppLink.assignmentId(AppLink.assignment("8601825630")), "8601825630")
        XCTAssertNil(AppLink.assignmentId(AppLink.todo))
        XCTAssertNil(AppLink.assignmentId(URL(string: "https://fuhsd.schoology.com/assignment/1")!))
    }

    func testSnapshotBuckets() {
        let cal = Calendar(identifier: .gregorian)
        let now = cal.date(from: DateComponents(year: 2026, month: 10, day: 10, hour: 12))!
        let s = WidgetSnapshot(items: [
            WidgetItem(id: "a", title: "Late", course: "X", due: now.addingTimeInterval(-3600), isTest: false),
            WidgetItem(id: "b", title: "Tonight", course: "X", due: now.addingTimeInterval(6 * 3600), isTest: false),
            WidgetItem(id: "c", title: "Monday", course: "X", due: now.addingTimeInterval(3 * 86400), isTest: true),
        ], updatedAt: now, signedIn: true, accentHex: "#1D5EA8")
        XCTAssertEqual(s.overdue(at: now).map(\.id), ["a"])
        XCTAssertEqual(s.upcoming(at: now).map(\.id), ["b", "c"])
        XCTAssertEqual(s.dueToday(at: now, calendar: cal).map(\.id), ["b"])
        // An hour after "Tonight" is due it moves to overdue.
        XCTAssertEqual(s.overdue(at: now.addingTimeInterval(7 * 3600)).map(\.id), ["a", "b"])
    }
}
