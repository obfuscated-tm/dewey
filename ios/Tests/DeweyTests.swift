import XCTest
import SwiftUI
@testable import Dewey

/// Decoding engine JSON (snake_case) and the pure To Do rules.
final class DecodingTests: XCTestCase {
    func testCourse() throws {
        let json = #"[{"section_id":"7001","title":"Chemistry H - 12345","section_title":"P3"}]"#
        let c = try JSONDecoder.engine.decode([Course].self, from: Data(json.utf8))
        XCTAssertEqual(c[0].sectionId, "7001")
        XCTAssertEqual(c[0].shortTitle, "Chemistry H")
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

    func testGradeAlertBody() {
        func item(_ t: String, _ g: String) -> CourseGrade.Item {
            CourseGrade.Item(id: t, category: "Labs", title: t, due: nil, grade: g, comment: "", rubric: nil,
                             earned: 1, possible: 1, pct: nil, impact: nil)
        }
        let one = [item("Lab 4", "18 / 20")]
        XCTAssertEqual(Reminders.gradeAlertBody(items: one, from: 92.1, to: 92.6), "Lab 4: 18 / 20 · 92.1% → 92.6%")
        XCTAssertEqual(Reminders.gradeAlertBody(items: one, from: 92.6, to: 92.62), "Lab 4: 18 / 20 · still 92.6%")
        XCTAssertEqual(Reminders.gradeAlertBody(items: one, from: nil, to: nil), "Lab 4: 18 / 20")
        let five = ["A", "B", "C", "D", "E"].map { item($0, "1 / 1") }
        XCTAssertEqual(Reminders.gradeAlertBody(items: five, from: 80, to: 81),
                       "A: 1 / 1\nB: 1 / 1\nC: 1 / 1\n+2 more\nClass: 80.0% → 81.0%")
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

    func testQuizAssignment() throws {
        // engine.js assignment op on a newer quiz's landing page
        let json = #"{"id":"8610387535","title":"Worksheet 2.4 Quiz","type":"quiz","due":null,"instructions_md":"","attachments":[],"submission":null,"dropboxUrl":null,"quiz":{"title":"Worksheet 2.4 Quiz","instructions":null,"attempts_allowed":2,"attempts_taken":1,"time_limit_min":null,"opens_at":null,"closes_at":null,"overdue":false,"score":10,"max_points":10,"letter":"A","state":"submitted","attempts":[{"n":1,"finished":true,"at":"2026-10-14T23:16:00.000Z","minutes":1,"can_view":true}]}}"#
        let a = try JSONDecoder.engine.decode(Assignment.self, from: Data(json.utf8))
        XCTAssertEqual(a.quiz?.state, "submitted")
        XCTAssertEqual(a.quiz?.attemptsAllowed, 2)
        XCTAssertEqual(a.quiz?.attempts.first?.canView, true)
        XCTAssertNotNil(Fmt.date(a.quiz?.attempts.first?.at))
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

    func testShowsLate() {
        func late(_ t: ItemType, quiz: Bool = false, done: Bool = false, turnedIn: Bool = false, submitted: Bool = false, listed: Bool = true) -> Bool {
            TodoLogic.showsLate(type: t, isQuiz: quiz, done: done, turnedIn: turnedIn, submitted: submitted, inSchoology: listed)
        }
        XCTAssertFalse(late(.assignment, listed: false))  // Schoology dropped it: maybe handed in elsewhere
        XCTAssertTrue(late(.assignment))
        XCTAssertFalse(late(.assignment, done: true))
        XCTAssertFalse(late(.assignment, turnedIn: true))
        XCTAssertFalse(late(.assignment, submitted: true))
        XCTAssertFalse(late(.exam))                       // a test on paper: nothing to hand in after it
        XCTAssertTrue(late(.exam, quiz: true))            // an online quiz not taken yet
        XCTAssertFalse(late(.exam, quiz: true, done: true)) // studied: given in class and taken
        XCTAssertFalse(late(.exam, quiz: true, submitted: true))
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

    func testSnapshotTests() {
        let cal = Calendar(identifier: .gregorian)
        let now = cal.date(from: DateComponents(year: 2026, month: 10, day: 10, hour: 12))!
        let s = WidgetSnapshot(items: [
            WidgetItem(id: "old", title: "Last week", course: "X", due: now.addingTimeInterval(-3 * 86400), isTest: true),
            WidgetItem(id: "am", title: "This morning", course: "X", due: now.addingTimeInterval(-3 * 3600), isTest: true),
            WidgetItem(id: "hw", title: "HW", course: "X", due: now.addingTimeInterval(3600), isTest: false),
            WidgetItem(id: "mon", title: "Monday", course: "X", due: now.addingTimeInterval(2 * 86400), isTest: true, marked: true),
        ], updatedAt: now, signedIn: true, accentHex: "#1D5EA8")
        // Tests from the start of today: a test earlier today still counts, an older one doesn't.
        XCTAssertEqual(s.tests(at: now, calendar: cal).map(\.id), ["am", "mon"])
        XCTAssertTrue(s.items[3].isMarked)
        XCTAssertFalse(s.items[0].isMarked)
    }

    /// A snapshot saved before `marked` and `grades` existed still loads.
    func testOldSnapshotDecodes() throws {
        let json = ##"{"items":[{"id":"1","title":"HW","course":"X","isTest":false}],"updatedAt":0,"signedIn":true,"accentHex":"#1D5EA8"}"##
        let s = try JSONDecoder().decode(WidgetSnapshot.self, from: Data(json.utf8))
        XCTAssertEqual(s.items.first?.isMarked, false)
        XCTAssertNil(s.grades)
    }
}

final class InboxTests: XCTestCase {
    func testKind() {
        let classes = [Course(sectionId: "1", title: "AP Biology - 1101", sectionTitle: "RiveraM p2 T1")]
        XCTAssertEqual(Inbox.kind(group: "AP Biology - 1101: RiveraM p2 T1", classes: classes), .teacher)
        XCTAssertEqual(Inbox.kind(group: nil, classes: classes), .teacher, "a direct message")
        XCTAssertEqual(Inbox.kind(group: "School Hub: Gr10", classes: classes), .school)
        XCTAssertEqual(Course.short("Spanish 2 - 1404: GarciaJ p4 T1"), "Spanish 2")
        XCTAssertEqual(Course.short("Algebra 2 - 1202"), "Algebra 2")
    }

    func testDecode() throws {
        let json = #"{"threads":[{"id":"9","subject":"Lab","group":null,"preview":"Hi","from":"Ms. K","fromId":"5","date":"2026-10-05T16:20:00.000Z","unread":false}],"unreadCount":0,"hasMore":true}"#
        let l = try JSONDecoder.engine.decode(MessageList.self, from: Data(json.utf8))
        XCTAssertEqual(l.threads.first?.from, "Ms. K")
        XCTAssertNotNil(l.threads.first?.when)
        let n = #"{"items":[{"key":"material|x|y","type":"material","course":"Spanish 2 - 1404: GarciaJ p4 T1","sectionId":"7","text":"posted","date":null,"items":[{"title":"Quiz","url":"https://fuhsd.schoology.com/assignment/1","kind":"assignment"}]}],"hasMore":false}"#
        XCTAssertEqual(try JSONDecoder.engine.decode(NoticeList.self, from: Data(n.utf8)).items.first?.items.first?.kind, "assignment")
        let m = #"{"id":"9","subject":"Lab","group":null,"participants":["Ms. K"],"messages":[{"author":"Ms. K","author_id":"5","date":null,"body_md":"Hello","attachments":[]}]}"#
        XCTAssertEqual(try JSONDecoder.engine.decode(MessageDetail.self, from: Data(m.utf8)).messages.first?.bodyMd, "Hello")
    }
}

final class DueReminderTests: XCTestCase {
    let cal: Calendar = { var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "America/Los_Angeles")!; return c }()
    func at(_ d: Int, _ h: Int, _ m: Int = 0) -> Date { cal.date(from: DateComponents(year: 2026, month: 10, day: d, hour: h, minute: m))! }

    func testPlan() {
        let now = at(12, 8)   // Mon 8 am
        let items = [
            Reminders.DueInput(id: "lab", title: "Lab 4", course: "Chem", due: at(12, 23, 59), isTest: false, hasDropbox: true, done: true),
            Reminders.DueInput(id: "sub", title: "Essay", course: "Eng", due: at(12, 22), isTest: false, hasDropbox: true, submitted: true),
            Reminders.DueInput(id: "test", title: "Unit 3 Test", course: "Math", due: at(14, 9), isTest: true, hasDropbox: false),
            Reminders.DueInput(id: "done-test", title: "Quiz", course: "Fr", due: at(14, 10), isTest: true, hasDropbox: false, studied: true),
            Reminders.DueInput(id: "hw", title: "Reading", course: "", due: at(13, 23, 59), isTest: false, hasDropbox: false),
            Reminders.DueInput(id: "soon", title: "Exit ticket", course: "Bio", due: at(12, 9), isTest: false, hasDropbox: true),
        ]
        var s = Reminders.Settings()
        var p = Reminders.plan(items, settings: s, now: now, calendar: cal)
        // Defaults: 2 h before the unsubmitted lab (marked done still counts), night before the unstudied test.
        // The exit ticket is due in 1 h: too late for a 2 h alert. The essay is submitted.
        XCTAssertEqual(p.map(\.id), ["soon-lab", "test-test"])
        XCTAssertEqual(p[0].fire, at(12, 21, 59))
        XCTAssertEqual(p[1].fire, at(13, 19))
        XCTAssertEqual(p[1].title, "Test tomorrow: Unit 3 Test")

        s.evening = true
        s.morning = true
        p = Reminders.plan(items, settings: s, now: now, calendar: cal)
        XCTAssertTrue(p.contains { $0.id == "due-hw" && $0.fire == at(12, 19) })
        XCTAssertFalse(p.contains { $0.id == "due-lab" }, "marked done")
        // No morning today (7 am is past); tomorrow's lists the reading; Wednesday's the test, not the studied quiz.
        let mornings = p.filter { $0.id.hasPrefix("morning-") }
        XCTAssertEqual(mornings.map(\.id), ["morning-2026-10-13", "morning-2026-10-14"])
        XCTAssertEqual(mornings[0].title, "1 due today")
        XCTAssertEqual(mornings[1].title, "Test today")
        XCTAssertEqual(mornings[1].body, "Test: Unit 3 Test")
        // Sorted by time, and once each.
        XCTAssertEqual(p.map(\.fire), p.map(\.fire).sorted())
        XCTAssertEqual(Set(p.map(\.id)).count, p.count)
    }

    func testCountdown() {
        let now = at(12, 8)
        XCTAssertEqual(Reminders.testCountdown(due: at(12, 10), now: now, calendar: cal), "Test today")
        XCTAssertEqual(Reminders.testCountdown(due: at(13, 8), now: now, calendar: cal), "Test tomorrow")
        XCTAssertEqual(Reminders.testCountdown(due: at(15, 8), now: now, calendar: cal), "Test in 3 days")
        XCTAssertNil(Reminders.testCountdown(due: at(12, 7), now: now, calendar: cal))
    }

    func testCountdownWindow() {
        let now = at(12, 8)
        XCTAssertEqual(WidgetBridge.countdownWindow(due: nil, now: now), .none)
        XCTAssertEqual(WidgetBridge.countdownWindow(due: at(12, 7), now: now), .none)
        XCTAssertEqual(WidgetBridge.countdownWindow(due: at(12, 19), now: now), .open)
        XCTAssertEqual(WidgetBridge.countdownWindow(due: at(13, 23, 59), now: now), .opens(at(13, 11, 59)))
    }
}

final class SiriTests: XCTestCase {
    func testWhatsDue() {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "America/Los_Angeles")!
        func at(_ d: Int, _ h: Int) -> Date { cal.date(from: DateComponents(year: 2026, month: 10, day: d, hour: h))! }
        let now = at(12, 8)
        let items = [
            WidgetItem(id: "lab", title: "Lab 4", course: "Chemistry", due: at(13, 23), isTest: false),
            WidgetItem(id: "t", title: "Unit 3", course: "Math", due: at(13, 9), isTest: true),
            WidgetItem(id: "x", title: "Done one", course: "Math", due: at(13, 10), isTest: false, marked: true),
            WidgetItem(id: "w", title: "Essay", course: "", due: at(16, 23), isTest: false),
        ]
        let tomorrow = WhatsDueIntent.due(items, day: .tomorrow, now: now, calendar: cal)
        XCTAssertEqual(tomorrow.map(\.id), ["t", "lab"])
        XCTAssertEqual(WhatsDueIntent.summary(tomorrow, day: .tomorrow, now: now, asOf: now, signedIn: true),
                       "Tomorrow: 2 things. The test Unit 3 for Math, and Lab 4 for Chemistry.")
        XCTAssertEqual(WhatsDueIntent.due(items, day: .week, now: now, calendar: cal).map(\.id), ["t", "lab", "w"])
        let week = WhatsDueIntent.due(items, day: .week, now: now, calendar: cal)
        XCTAssertTrue(WhatsDueIntent.summary(week, day: .week, now: now, asOf: now, signedIn: true)
            .hasPrefix("This week: 3 things. 1 for Math, 1 for Chemistry, 1 for other classes. Test: Unit 3 on "))
        XCTAssertEqual(WhatsDueIntent.summary([], day: .today, now: now, asOf: now.addingTimeInterval(-86400), signedIn: true),
                       "Nothing open is due today. As of \(Fmt.ago(now.addingTimeInterval(-86400), now: now)).")
    }
}

final class ThemeTests: XCTestCase {
    /// WCAG relative luminance.
    func lum(_ c: UIColor) -> Double {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        c.getRed(&r, green: &g, blue: &b, alpha: &a)
        func f(_ v: CGFloat) -> Double { let v = Double(v); return v <= 0.03928 ? v / 12.92 : pow((v + 0.055) / 1.055, 2.4) }
        return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
    }
    func ratio(_ a: UIColor, _ b: UIColor) -> Double {
        let (x, y) = (lum(a), lum(b))
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }
    func ui(_ hex: String) -> UIColor { UIColor(Color(hex: hex)) }

    /// Every text colour is readable (≥ 4.5:1) on every surface, in light and dark,
    /// and the accent on a card and under its button text.
    func testContrast() {
        let p = Palette.dew
        for fg in ["text", "secondary", "tertiary", "late", "good", "accent"] {
            for bg in ["card", "background", "tagSoft"] where !(fg == "accent" && bg == "tagSoft") {
                let (f, b) = (p.hex[fg]!, p.hex[bg]!)
                XCTAssertGreaterThanOrEqual(ratio(ui(f.light), ui(b.light)), 4.5, "light \(fg) on \(bg)")
                XCTAssertGreaterThanOrEqual(ratio(ui(f.dark), ui(b.dark)), 4.5, "dark \(fg) on \(bg)")
            }
        }
        // Text on a filled accent button: white in light mode, near-black in dark.
        let a = p.hex["accent"]!
        XCTAssertGreaterThanOrEqual(ratio(ui(a.light), .white), 4.5, "on-accent")
        XCTAssertGreaterThanOrEqual(ratio(ui(a.dark), ui("#101418")), 4.5, "dark on-accent")
    }

    /// White class names read on every class colour (the Classes shelf), and the
    /// colour itself reads as text (Grades letters, the catalog line) on the cards.
    func testClassColors() {
        let card = Palette.dew.hex["card"]!, bg = Palette.dew.hex["background"]!
        for hex in Theme.classColors {
            XCTAssertGreaterThanOrEqual(ratio(ui(hex), .white), 4.5, hex)
            XCTAssertGreaterThanOrEqual(ratio(ui(hex), ui(card.light)), 4.5, "\(hex) on card")
            XCTAssertGreaterThanOrEqual(ratio(ui(hex), ui(bg.light)), 4.5, "\(hex) on background")
            XCTAssertGreaterThanOrEqual(ratio(Accent.dark(ui(hex)), ui(card.dark)), 4.5, "\(hex) on dark card")
            XCTAssertGreaterThanOrEqual(ratio(Accent.dark(ui(hex)), ui(bg.dark)), 4.5, "\(hex) on dark background")
        }
    }

    /// Screens use the seven text styles in Theme, never a point size or a code
    /// font: one stray `.system(size: 12.5)` is how the type scale drifts again.
    func testScreensUseTheTypeScale() throws {
        let app = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("App")
        let banned = [/\.system\(size: *[0-9]/, /\.monospaced\(\)/, /Font\.custom\(/, /RoundedRectangle\(cornerRadius: *[0-9]/]
        var hits: [String] = []
        let files = FileManager.default.enumerator(at: app, includingPropertiesForKeys: nil)!
        for case let url as URL in files where url.pathExtension == "swift" && url.lastPathComponent != "Theme.swift" {
            for (n, line) in try String(contentsOf: url, encoding: .utf8).components(separatedBy: "\n").enumerated() {
                if banned.contains(where: { line.contains($0) }) { hits.append("\(url.lastPathComponent):\(n + 1)") }
            }
        }
        XCTAssertEqual(hits, [], "use Theme.largeTitle…caption and Theme.Radius instead")
    }
}

/// Call numbers: right for clear names, the main class for vague ones, and
/// nothing (never a wrong number) for names that aren't subjects.
final class CallNumberTests: XCTestCase {
    func testClearNamesGetTheirNumber() {
        let cases = [
            "AP Biology": "570", "AP Comp Sci A": "005", "AP Physics 1": "530", "Algebra 2_Trig": "512",
            "World History": "909", "US History": "973", "APUSH": "973", "English 10": "820", "Spanish 2": "460",
            "Chemistry H": "540", "AP Spanish Literature": "860", "AP Lang": "808", "AP Lit": "820",
            "Computer Science Principles": "005", "AP CSP": "005", "Political Science": "320", "AP Gov": "320",
            "Economics": "330", "AP Psych": "150", "Concert Band": "780", "Ceramics 1": "738", "PE 9": "796",
            "Physical Education": "796", "Health": "613", "Intro to Engineering Design": "620", "Biotech 1": "660",
            "Geometry H": "516", "AP Calculus BC": "515", "Precalculus": "515", "AP Statistics": "519",
            "Integrated Math 2": "510", "ELD 2": "420", "AP Euro": "940", "Yearbook": "070", "French 3": "440",
        ]
        for (name, number) in cases { XCTAssertEqual(CallNumber.guess(name), number, name) }
    }

    func testVagueNamesGetTheMainClass() {
        XCTAssertEqual(CallNumber.guess("Integrated Science 1"), "500")
        XCTAssertEqual(CallNumber.guess("Social Studies 9"), "300")
        XCTAssertEqual(CallNumber.guess("Art 1"), "700")
        XCTAssertEqual(CallNumber.guess("History of the Americas"), "900")
    }

    func testNonSubjectsAndUnknownsGetNothing() {
        for name in ["Advisory", "Homeroom 214", "Study Hall", "School Hub", "Freshman Seminar", "AP Seminar",
                     "Tutorial", "Leadership", "Teacher Aide", "Link Crew", "Flex", "Microbe Club", "Europa"] {
            XCTAssertNil(CallNumber.guess(name), name)
        }
    }

    func testHandSetNumbersWin() {
        let c = Course(sectionId: "9", title: "Leadership - 1234", sectionTitle: "P4")
        XCTAssertNil(CallNumber.number(for: c, raw: ""))
        var raw = CallNumber.setting("", "9", "300")
        XCTAssertEqual(CallNumber.number(for: c, raw: raw), "300")
        raw = CallNumber.setting(raw, "9", "")
        XCTAssertNil(CallNumber.number(for: c, raw: raw), "None hides it")
        let bio = Course(sectionId: "7", title: "AP Biology - 1101", sectionTitle: "P2")
        XCTAssertEqual(CallNumber.number(for: bio, raw: raw), "570")
        raw = CallNumber.setting(raw, "9", nil)
        XCTAssertEqual(CallNumber.overrides(raw), [:], "automatic again")
    }
}

final class NewGradeTests: XCTestCase {
    private func item(_ id: String, _ earned: Double?) -> CourseGrade.Item {
        CourseGrade.Item(id: id, category: "Labs", title: id, due: nil, grade: "", comment: "", rubric: nil,
                         earned: earned, possible: 10, pct: nil, impact: nil)
    }

    func testFirstGradebookIsAlreadySeen() {
        var log: [String: AppModel.GradeLogEntry] = [:]
        let fresh = AppModel.logGrades(&log, old: nil, new: [item("a", 9), item("b", nil)])
        XCTAssertTrue(fresh.isEmpty)
        XCTAssertEqual(log["a"]?.seen, true)
        XCTAssertNil(log["a"]?.at)
        XCTAssertNil(log["b"], "ungraded work isn't logged")
    }

    func testChangedScoresAreNewUntilSeenOrAWeekOld() {
        let now = Date()
        var log: [String: AppModel.GradeLogEntry] = [:]
        _ = AppModel.logGrades(&log, old: nil, new: [item("a", 9), item("b", nil)], now: now)
        let fresh = AppModel.logGrades(&log, old: [item("a", 9), item("b", nil)],
                                       new: [item("a", 9), item("b", 7), item("c", 10)], now: now)
        XCTAssertEqual(fresh.map(\.id), ["b", "c"])
        XCTAssertTrue(AppModel.isNew(log["b"]!, now: now))
        XCTAssertFalse(AppModel.isNew(log["a"]!, now: now))
        XCTAssertFalse(AppModel.isNew(log["b"]!, now: now.addingTimeInterval(8 * 86400)))
        log["b"]?.seen = true
        XCTAssertFalse(AppModel.isNew(log["b"]!, now: now))
    }

    func testAppUpdateWithSavedGradebookDoesNotFlagEverything() {
        // An existing install has a saved gradebook but no log yet.
        var log: [String: AppModel.GradeLogEntry] = [:]
        let fresh = AppModel.logGrades(&log, old: [item("a", 9)], new: [item("a", 9)])
        XCTAssertTrue(fresh.isEmpty)
        XCTAssertEqual(log["a"]?.seen, true)
    }

    func testRegradeIsNewAgain() {
        var log: [String: AppModel.GradeLogEntry] = ["a": .init(earned: 7, at: nil, seen: true)]
        let fresh = AppModel.logGrades(&log, old: [item("a", 7)], new: [item("a", 9)])
        XCTAssertEqual(fresh.map(\.id), ["a"])
        XCTAssertEqual(log["a"]?.seen, false)
    }
}

/// Plan's sections and order (PlanLogic), and the student's own tasks on To Do.
final class PlanTests: XCTestCase {
    let now = ISO8601DateFormatter().date(from: "2026-10-20T15:00:00Z")!
    func day(_ d: Int) -> Date { now.addingTimeInterval(Double(d) * 86400) }
    func ids(_ e: [PlanLogic.Entry]) -> [String] { e.map(\.id) }

    func testUnplacedGoByDue() {
        let e: [PlanLogic.Entry] = [.init(id: "c", rank: nil, due: day(3)), .init(id: "a", rank: nil, due: day(-1)), .init(id: "b", rank: nil, due: day(1))]
        XCTAssertEqual(ids(PlanLogic.order(e)), ["a", "b", "c"])
    }

    func testPlacedKeepTheStudentsOrder() {
        let e: [PlanLogic.Entry] = [.init(id: "x", rank: 2, due: day(1)), .init(id: "y", rank: 1, due: day(5))]
        XCTAssertEqual(ids(PlanLogic.order(e)), ["y", "x"])
    }

    func testNewWorkSlotsInBeforeLaterPlacedWork() {
        // The student put y (due in 5 days) first. New work due tomorrow goes above it,
        // not under everything they placed; new work due in 6 days goes after it.
        let e: [PlanLogic.Entry] = [.init(id: "y", rank: 1, due: day(5)), .init(id: "x", rank: 2, due: day(2)),
                                    .init(id: "new1", rank: nil, due: day(1)), .init(id: "new6", rank: nil, due: day(6)),
                                    .init(id: "nodate", rank: nil, due: nil)]
        XCTAssertEqual(ids(PlanLogic.order(e)), ["new1", "y", "x", "new6", "nodate"])
    }

    func testDragWithinAndAcrossSections() {
        let rows = PlanLogic.rows(todo: ["a", "b"], later: ["c"], done: ["d"])
        // [H todo, a, b, H later, c, H done, d]: b to the top of To do.
        let up = PlanLogic.moved(rows, from: IndexSet(integer: 2), to: 1)
        XCTAssertEqual(up["b"]?.0, .todo); XCTAssertEqual(up["b"]?.1, 1)
        XCTAssertEqual(up["a"]?.1, 2)
        // a dragged under the Later heading.
        let down = PlanLogic.moved(rows, from: IndexSet(integer: 1), to: 4)
        XCTAssertEqual(down["a"]?.0, .later); XCTAssertEqual(down["a"]?.1, 1)
        XCTAssertEqual(down["c"]?.1, 2)
        // c dragged to the end, into Done.
        XCTAssertEqual(PlanLogic.moved(rows, from: IndexSet(integer: 4), to: 7)["c"]?.0, .done)
        // Dropped above the first heading: still To do.
        XCTAssertEqual(PlanLogic.moved(rows, from: IndexSet(integer: 4), to: 0)["c"]?.0, .todo)
        // Swipe: to the top of Later; VoiceOver nudge across a heading.
        XCTAssertEqual(PlanLogic.moved(rows, id: "a", to: .later)["a"]?.0, .later)
        XCTAssertEqual(PlanLogic.nudged(rows, id: "b", by: 1)["b"]?.0, .later)
    }

    func testDropOnCollapsedSection() {
        // Later is collapsed: its items aren't rows, but a drop under its heading joins them at the top.
        let rows = PlanLogic.rows(todo: ["a", "b"], later: [], done: ["d"])
        let hidden: PlanLogic.Hidden = [.later: ["c", "e"]]
        let p = PlanLogic.moved(rows, from: IndexSet(integer: 1), to: 4, hidden: hidden)
        XCTAssertEqual(p["a"]?.0, .later)
        XCTAssertEqual(p["a"]?.1, 1)
        XCTAssertEqual(p["c"]?.1, 2)
        XCTAssertEqual(p["e"]?.1, 3)
        XCTAssertEqual(p["b"]?.1, 1)
        XCTAssertEqual(PlanLogic.moved(rows, id: "b", to: .later, hidden: hidden)["e"]?.1, 3)
        // The grip never lifts work above To do's heading.
        XCTAssertEqual(PlanLogic.nudging(rows, id: "a", by: -1), rows)
    }

    func testWhereWorkGoesByDueDate() {
        let cal = Calendar.current
        let today = cal.startOfDay(for: now)
        func at(_ d: Int) -> Date { cal.date(byAdding: .hour, value: 20, to: cal.date(byAdding: .day, value: d, to: today)!)! }
        XCTAssertEqual(PlanLogic.autoBucket(due: at(-2), late: true, isTest: false, mine: false, now: now), .todo)
        XCTAssertEqual(PlanLogic.autoBucket(due: at(0), late: false, isTest: false, mine: false, now: now), .todo)
        XCTAssertEqual(PlanLogic.autoBucket(due: at(1), late: false, isTest: false, mine: false, now: now), .todo)
        XCTAssertEqual(PlanLogic.autoBucket(due: at(2), late: false, isTest: false, mine: false, now: now), .later)
        XCTAssertEqual(PlanLogic.autoBucket(due: at(2), late: false, isTest: true, mine: false, now: now), .todo)
        XCTAssertEqual(PlanLogic.autoBucket(due: at(3), late: false, isTest: true, mine: false, now: now), .todo)
        XCTAssertEqual(PlanLogic.autoBucket(due: at(4), late: false, isTest: true, mine: false, now: now), .later)
        XCTAssertEqual(PlanLogic.autoBucket(due: nil, late: false, isTest: false, mine: true, now: now), .todo)
        XCTAssertEqual(PlanLogic.autoBucket(due: nil, late: false, isTest: false, mine: false, now: now), .later)
    }

    func testOwnTasksStayUntilDoneThenUntilTheDayEnds() {
        func s(done: Bool, today: Bool) -> TodoLogic.Snapshot {
            .init(inSchoology: true, done: done, turnedIn: false, submitted: false, graded: false, dismissed: false, mine: true, finishedToday: today)
        }
        XCTAssertTrue(TodoLogic.isVisible(s(done: false, today: false)))
        XCTAssertTrue(TodoLogic.isVisible(s(done: true, today: true)))
        XCTAssertFalse(TodoLogic.isVisible(s(done: true, today: false)))
        XCTAssertTrue(ItemState.isMine("mine-ABC"))
        XCTAssertFalse(ItemState.isMine("8610387535"))
    }
}

/// What the Dewey calendar holds (CalendarSync.spec) and the Code 39 barcode.
final class CalendarAndIDTests: XCTestCase {
    let cal = Calendar.current
    func at(_ h: Int, _ m: Int, days: Int = 1) -> Date {
        cal.date(bySettingHour: h, minute: m, second: 0, of: cal.date(byAdding: .day, value: days, to: Date())!)!
    }
    func input(_ due: Date?, test: Bool = false, finished: Bool = false) -> CalendarSync.Input {
        .init(id: "1", title: "Lab 4", course: "Chemistry H", due: due, isTest: test, finished: finished)
    }

    func testEventShapes() {
        let now = Date()
        let midnight = CalendarSync.spec(input(at(23, 59)), now: now)!
        XCTAssertTrue(midnight.allDay)
        XCTAssertEqual(midnight.start, cal.startOfDay(for: at(23, 59)))
        let timed = CalendarSync.spec(input(at(8, 30)), now: now)!
        XCTAssertFalse(timed.allDay)
        XCTAssertEqual(timed.end, at(8, 30))
        XCTAssertEqual(timed.start, at(8, 0))
        XCTAssertEqual(timed.location, "Chemistry H")
        let test = CalendarSync.spec(input(at(10, 5), test: true), now: now)!
        XCTAssertTrue(test.allDay)
        XCTAssertEqual(test.title, "Test: Lab 4")
    }

    func testWhatStaysOut() {
        let now = Date()
        XCTAssertNil(CalendarSync.spec(input(nil), now: now))
        XCTAssertNil(CalendarSync.spec(input(at(9, 0), finished: true), now: now))
        XCTAssertNil(CalendarSync.spec(input(at(9, 0, days: 120)), now: now))
        XCTAssertNil(CalendarSync.spec(input(at(9, 0, days: -30)), now: now))
    }

    func testCode39() {
        let m = Code39.modules("1234567")!
        // 9 characters of 15 modules, 8 gaps, two quiet zones of 10.
        XCTAssertEqual(m.count, 9 * 15 + 8 + 20)
        XCTAssertFalse(m.prefix(Code39.quiet).contains(true))
        XCTAssertTrue(m[Code39.quiet])
        XCTAssertNil(Code39.modules("12A4"))
        XCTAssertNil(Code39.modules(""))
    }

    func testStudentIDDigitsOnly() {
        XCTAssertEqual(StudentID.clean(" 525-1103x"), "5251103")
        XCTAssertNil(StudentID.warning("5251103"))
        XCTAssertNil(StudentID.warning(""))
        XCTAssertNotNil(StudentID.warning("52511"))
        XCTAssertNotNil(StudentID.warning("52511030"))
    }
}
