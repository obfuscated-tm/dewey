import XCTest
@testable import Dewey

final class MaterialStatusTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_760_000_000)
    private var past: Date { now.addingTimeInterval(-3 * 86_400) }
    private var future: Date { now.addingTimeInterval(2 * 86_400) }

    func testScoreWinsOverLateAndKeepsTheDate() {
        let l = MaterialStatus.line(score: "18 / 20", late: true, due: past, submitted: true, turnedIn: true, now: now)
        XCTAssertEqual(l, MaterialStatus.Line(text: Fmt.short.string(from: past) + " · 18 / 20"))
        XCTAssertEqual(MaterialStatus.line(score: "18 / 20", late: false, due: nil, submitted: false, turnedIn: false, now: now)?.text, "18 / 20")
    }

    func testLateShowsOnlyTheDate() {
        let l = MaterialStatus.line(score: nil, late: true, due: past, submitted: false, turnedIn: false, now: now)
        XCTAssertEqual(l?.text, "Late · " + Fmt.short.string(from: past))
        XCTAssertEqual(l?.late, true)
    }

    func testSubmittedThenTurnedInWithTheDate() {
        let day = Fmt.short.string(from: future)
        XCTAssertEqual(MaterialStatus.line(score: nil, late: false, due: future, submitted: true, turnedIn: true, now: now)?.text, day + " · Submitted")
        XCTAssertEqual(MaterialStatus.line(score: nil, late: false, due: future, submitted: false, turnedIn: true, now: now)?.text, day + " · Turned in")
        XCTAssertEqual(MaterialStatus.line(score: nil, late: false, due: nil, submitted: false, turnedIn: true, now: now)?.text, "Turned in")
    }

    func testDueAndNothing() {
        XCTAssertEqual(MaterialStatus.line(score: nil, late: false, due: future, submitted: false, turnedIn: false, now: now)?.text, "Due " + Fmt.due(future, now: now))
        // Past, not late by Dewey's rule (work Schoology dropped): the date alone.
        XCTAssertEqual(MaterialStatus.line(score: nil, late: false, due: past, submitted: false, turnedIn: false, now: now)?.text, Fmt.short.string(from: past))
        XCTAssertNil(MaterialStatus.line(score: nil, late: false, due: nil, submitted: false, turnedIn: false, now: now))
        XCTAssertNil(MaterialStatus.line(score: "", late: false, due: nil, submitted: false, turnedIn: false, now: now))
    }

    func testLateWithoutDueFallsThrough() {
        XCTAssertEqual(MaterialStatus.line(score: nil, late: true, due: nil, submitted: true, turnedIn: false, now: now)?.text, "Submitted")
    }

    func testFileWords() {
        XCTAssertEqual(FileKinds.word(title: "Notes.pdf", url: "/x"), "PDF")
        XCTAssertEqual(FileKinds.word(title: "a", url: "/f/a.pptx"), "Slides")
        XCTAssertEqual(FileKinds.word(title: "a", url: "/f/a.docx"), "Doc")
    }
}
