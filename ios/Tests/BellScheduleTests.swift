import XCTest
@testable import Dewey

final class BellScheduleTests: XCTestCase {
    private var cal: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "America/Los_Angeles")!
        return c
    }()

    /// 2026-11-16 is a Monday.
    private func at(_ day: Int, _ h: Int, _ m: Int) -> Date {
        cal.date(from: DateComponents(year: 2026, month: 11, day: day, hour: h, minute: m))!
    }

    private func schedule() -> BellSchedule {
        var regular = BellSchedule.DayType(name: "Regular")
        regular.slots = [.init(name: "P2", start: 9 * 60, end: 9 * 60 + 50), .init(name: "P1", start: 8 * 60, end: 8 * 60 + 50)]
        var block = BellSchedule.DayType(name: "Block")
        block.slots = [.init(name: "P3", start: 8 * 60, end: 9 * 60 + 30)]
        var s = BellSchedule(dayTypes: [regular, block])
        s.week = [2: regular.id, 3: block.id]
        s.periods["P1"] = .init(title: "Chemistry H", room: "B12")
        return s
    }

    func testNowAndNextInTimeOrder() {
        let m = schedule().moment(at: at(16, 8, 10), calendar: cal)
        XCTAssertEqual(m.now?.title, "Chemistry H")
        XCTAssertEqual(m.now?.period.room, "B12")
        XCTAssertEqual(m.next?.title, "P2") // a period nobody described shows its own name
        XCTAssertEqual(m.dayEnd, at(16, 9, 50))
    }

    func testPassingPeriodAndAfterSchool() {
        let s = schedule()
        let gap = s.moment(at: at(16, 8, 55), calendar: cal)
        XCTAssertNil(gap.now)
        XCTAssertEqual(gap.next?.name, "P2")
        let after = s.moment(at: at(16, 15, 0), calendar: cal)
        XCTAssertTrue(after.isOver)
        // The end of a period is not in it.
        XCTAssertNil(s.moment(at: at(16, 8, 50), calendar: cal).now)
    }

    func testWeekdayPicksTheDayTypeAndWeekendHasNone() {
        let s = schedule()
        XCTAssertEqual(s.moment(at: at(17, 8, 10), calendar: cal).now?.name, "P3") // Tuesday: Block
        let sunday = s.moment(at: at(15, 8, 10), calendar: cal)
        XCTAssertNil(sunday.dayEnd)
        XCTAssertFalse(sunday.isOver)
    }

    func testTodayIsDifferentOnlyForThatDate() {
        var s = schedule()
        s.today = .init(day: "2026-11-16", dayType: s.dayTypes[1].id)
        XCTAssertEqual(s.moment(at: at(16, 8, 10), calendar: cal).now?.name, "P3")
        s.today = .init(day: "2026-11-16", dayType: nil) // no school
        XCTAssertNil(s.moment(at: at(16, 8, 10), calendar: cal).dayEnd)
        s.today = .init(day: "2026-11-15", dayType: nil) // yesterday's switch has worn off
        XCTAssertEqual(s.moment(at: at(16, 8, 10), calendar: cal).now?.name, "P1")
    }

    func testChangesAreEveryBellStillToCome() {
        let c = schedule().changes(after: at(16, 8, 10), calendar: cal)
        XCTAssertEqual(c, [at(16, 8, 50), at(16, 9, 0), at(16, 9, 50)])
    }

    func testNextSlotFollowsTheLastOne() {
        let first = BellSchedule.nextSlot(after: [], name: "P1")
        XCTAssertEqual([first.start, first.end], [480, 530])
        let second = BellSchedule.nextSlot(after: [first], name: "P2")
        XCTAssertEqual([second.start, second.end], [535, 585])
        XCTAssertEqual(BellSchedule.nextName(in: [first, second]), "P3")
    }

    func testAddingAndRemovingDayTypes() {
        var s = BellSchedule()
        let a = s.addDayType(weekdays: [2, 3, 4, 5, 6])
        XCTAssertEqual(s.dayTypes.map(\.name), [""]) // the student names it; nothing is called Regular
        XCTAssertEqual(Set(s.week.values), [a]) // the first one is the whole week
        let b = s.addDayType(weekdays: [2, 3, 4, 5, 6])
        XCTAssertEqual(s.week[3], a) // a second one takes no days by itself
        s.week[3] = b
        s.today = .init(day: "2026-11-16", dayType: b)
        s.remove(dayType: b)
        XCTAssertEqual(s.dayTypes.map(\.id), [a])
        XCTAssertNil(s.week[3])
        XCTAssertNil(s.today)
        // Gone is gone: nothing below crashes or brings it back.
        s.name(dayType: b, "Block")
        s.setSlots([.init(name: "P1", start: 480, end: 530)], in: b)
        XCTAssertEqual(s.slots(in: b), [])
        XCTAssertEqual(s.dayTypes.count, 1)
    }

    func testRenamingAPeriodKeepsItsClass() {
        var s = schedule()
        let p1 = s.slots(in: s.dayTypes[0].id)[0].id
        s.rename(slot: p1, to: "Zero")
        XCTAssertEqual(s.periods["Zero"]?.title, "Chemistry H")
        XCTAssertNil(s.periods["P1"]) // nothing is called P1 any more
        // A name that already has a class keeps its own.
        s.periods["P3"] = .init(title: "Physics")
        s.rename(slot: p1, to: "P3")
        XCTAssertEqual(s.periods["P3"]?.title, "Physics")
        // A period that was deleted is left alone, not crashed on.
        s.remove(slot: p1)
        s.rename(slot: p1, to: "Gone")
        s.update(slot: p1) { $0.start = 0 }
        XCTAssertNil(s.slot(p1))
        XCTAssertEqual(s.slots(in: s.dayTypes[0].id).map(\.name), ["P2"])
    }

    func testAsManyPeriodsAsYouLikeAndDeletingOne() {
        var slots: [BellSchedule.Slot] = []
        for _ in 0..<14 { slots.append(BellSchedule.nextSlot(after: slots, name: BellSchedule.nextName(in: slots))) }
        XCTAssertEqual(Set(slots.map(\.name)).count, 14) // P13, P14: no two share a name
        var s = schedule()
        s.remove(period: "P1")
        XCTAssertNil(s.periods["P1"])
        XCTAssertEqual(s.periodNames, ["P2", "P3"])
    }

    func testFremontUnionTemplates() throws {
        XCTAssertEqual(BellTemplate.all.count, 5)
        for t in BellTemplate.all { try check(t.schedule, t.school) }
        let mv = try XCTUnwrap(BellTemplate.all.first { $0.school == "Monta Vista High" }).schedule
        XCTAssertEqual(mv.moment(at: at(16, 11, 42), calendar: cal).now?.name, "Brunch") // until 11:45 there
    }

    private func check(_ s: BellSchedule, _ school: String) throws {
        XCTAssertEqual(s.dayTypes.count, 3)
        XCTAssertEqual(s.week[3], s.week[5]) // Tuesday and Thursday share one
        XCTAssertEqual(s.week[4], s.week[6])
        XCTAssertNil(s.week[1])
        // Wednesday 1:00 pm is lunch, P6 next at 1:35.
        let m = s.moment(at: at(18, 13, 0), calendar: cal)
        XCTAssertEqual(m.now?.name, "Lunch")
        XCTAssertEqual(m.next?.start, at(18, 13, 35))
        // Every period ends after it starts and none overlap, every day.
        for d in s.dayTypes {
            let o = d.ordered
            XCTAssertEqual(o.count, d.slots.count, "\(school) \(d.name)")
            for (x, y) in zip(o, o.dropFirst()) { XCTAssertLessThanOrEqual(x.end, y.start, "\(school) \(d.name) \(x.name)") }
        }
        XCTAssertEqual(s.periodNames.filter { $0.hasPrefix("P") }.sorted(), (1...7).map { "P\($0)" })
    }

    func testWidgetTestDay() {
        let now = at(15, 14, 7) // a Sunday afternoon
        let t = BellSchedule.test(from: schedule(), at: now, calendar: cal)
        let m = t.moment(at: now, calendar: cal)
        XCTAssertEqual(m.now?.end, at(15, 14, 10)) // in class, 3 minutes left
        XCTAssertEqual(m.next?.start, at(15, 14, 11))
        XCTAssertEqual(t.moment(at: at(15, 14, 17), calendar: cal).now?.name, "Lunch")
        XCTAssertTrue(t.moment(at: at(15, 14, 40), calendar: cal).isOver)
        XCTAssertEqual(t.periods["P1"]?.title, "Chemistry H") // the student's own classes
        XCTAssertEqual(t.dayTypes[0].ordered.count, 5)
    }

    func testTheRingIsFullWhenTheBellRings() throws {
        let p1 = try XCTUnwrap(schedule().moment(at: at(16, 8, 0), calendar: cal).now) // 8:00–8:50
        XCTAssertEqual(p1.progress(at: at(16, 8, 49)), 1) // the last drawing before the bell
        XCTAssertEqual(p1.progress(at: at(16, 8, 0)), 1.0 / 50, accuracy: 0.0001)
        XCTAssertEqual(p1.progress(at: at(16, 8, 24)), 0.5, accuracy: 0.0001)
        // A 5-minute test period ends full too, not at 4/5.
        let t = BellSchedule.test(from: schedule(), at: at(15, 14, 7), calendar: cal)
        let p2 = try XCTUnwrap(t.moment(at: at(15, 14, 11), calendar: cal).now) // 14:11–14:16
        XCTAssertEqual(p2.progress(at: at(15, 14, 15)), 1)
    }

    func testTeacherFromTheSectionTitle() {
        XCTAssertEqual(ScheduleSettings.teacher("1404: GarciaJ p4 T1"), "GarciaJ")
        XCTAssertEqual(ScheduleSettings.teacher("Section 2"), "")
        XCTAssertEqual(ScheduleSettings.teacher("1404: P4"), "")
    }

    func testSavedCopyRoundTrips() throws {
        let s = schedule()
        XCTAssertEqual(try JSONDecoder().decode(BellSchedule.self, from: JSONEncoder().encode(s)), s)
    }
}
