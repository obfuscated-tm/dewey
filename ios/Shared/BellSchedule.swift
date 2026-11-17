import Foundation

/// The student's bell schedule, typed once in Settings → Schedule. Schoology has
/// no bell times, so this is the student's own: day types the student names (Block A…),
/// each a list of periods with a start and an end, and which type each weekday uses.
/// Compiled into the app and the widget extension; kept in the App Group.
struct BellSchedule: Codable, Equatable {
    /// One row of a day: "P3" 9:40–10:30, or "Lunch".
    struct Slot: Codable, Equatable, Identifiable {
        var id = UUID()
        /// The period's name, the key into `periods` ("P1", "Tutorial", "Lunch").
        var name: String
        /// Minutes after midnight.
        var start: Int
        var end: Int
    }

    struct DayType: Codable, Equatable, Identifiable {
        var id = UUID()
        var name: String
        var slots: [Slot] = []

        /// The slots in time order, without ones that end before they start.
        var ordered: [Slot] { slots.filter { $0.end > $0.start }.sorted { $0.start < $1.start } }
    }

    /// What a period is for this student: the class, where, and who teaches it.
    struct Period: Codable, Equatable {
        var title = ""
        var room = ""
        var teacher = ""
        /// The class colour's index (Theme.classColors), nil for lunch and the like.
        var color: Int?
    }

    /// "Today is different": another day type, or no periods, for one date only.
    struct Override: Codable, Equatable {
        /// yyyy-MM-dd, in the phone's time zone.
        var day: String
        /// nil = no school today.
        var dayType: UUID?
    }

    var dayTypes: [DayType] = []
    /// Calendar weekday (1 = Sunday … 7 = Saturday) → day type. A day left out has no school.
    var week: [Int: UUID] = [:]
    /// Slot name → what it is.
    var periods: [String: Period] = [:]
    var today: Override?

    var isEmpty: Bool { dayTypes.allSatisfy { $0.slots.isEmpty } }

    // ── Reading it ───────────────────────────────────────────────────────

    static func dayKey(_ date: Date, calendar: Calendar = .current) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    /// The day type `date` runs on: today's override when it's for that date, else the weekday's.
    func dayType(on date: Date, calendar: Calendar = .current) -> DayType? {
        let id: UUID?
        if let o = today, o.day == Self.dayKey(date, calendar: calendar) { id = o.dayType } else { id = week[calendar.component(.weekday, from: date)] }
        return dayTypes.first { $0.id == id }
    }

    /// A slot on a real day.
    struct Block: Equatable, Identifiable {
        var name: String
        var period: Period
        var start: Date
        var end: Date
        var id: Date { start }
        /// The class's name, or the slot's own when nothing is set for it.
        var title: String { period.title.isEmpty ? name : period.title }

        /// How full a widget's ring or bar is drawn from `date` until its next redraw a minute later.
        /// It shows where the class will be at that redraw, so the drawing made in the last minute is
        /// full when the bell rings (drawing where it is now would stop at 49/50, or 4/5 in a short period).
        func progress(at date: Date, redraw: TimeInterval = 60) -> Double {
            let length = end.timeIntervalSince(start)
            guard length > 0 else { return 1 }
            return min(1, max(0, (date.addingTimeInterval(redraw).timeIntervalSince(start)) / length))
        }
    }

    func blocks(on date: Date, calendar: Calendar = .current) -> [Block] {
        guard let type = dayType(on: date, calendar: calendar) else { return [] }
        let midnight = calendar.startOfDay(for: date)
        return type.ordered.map {
            Block(name: $0.name, period: periods[$0.name] ?? Period(),
                  start: midnight.addingTimeInterval(TimeInterval($0.start * 60)), end: midnight.addingTimeInterval(TimeInterval($0.end * 60)))
        }
    }

    /// Where the day stands at one moment.
    struct Moment: Equatable {
        /// The period in session, if any.
        var now: Block?
        /// The next one to start today.
        var next: Block?
        /// The day's first start and last end, nil with no school.
        var dayStart: Date?
        var dayEnd: Date?
        var isOver: Bool { dayEnd.map { _ in now == nil && next == nil } ?? false }
    }

    func moment(at date: Date, calendar: Calendar = .current) -> Moment {
        let all = blocks(on: date, calendar: calendar)
        return Moment(now: all.first { $0.start <= date && date < $0.end }, next: all.first { $0.start > date },
                      dayStart: all.first?.start, dayEnd: all.map(\.end).max())
    }

    /// Every moment the picture changes today (each start and end), from `date` on, for a widget timeline.
    func changes(after date: Date, calendar: Calendar = .current) -> [Date] {
        let all = blocks(on: date, calendar: calendar)
        return Array(Set(all.flatMap { [$0.start, $0.end] })).filter { $0 > date }.sorted()
    }

    // ── Editing it ───────────────────────────────────────────────────────

    /// A new slot after the last one: five minutes later, as long as the last (50 minutes for the first, at 8:00).
    static func nextSlot(after slots: [Slot], name: String) -> Slot {
        guard let last = slots.max(by: { $0.end < $1.end }) else { return Slot(name: name, start: 8 * 60, end: 8 * 60 + 50) }
        let length = max(last.end - last.start, 5)
        let start = min(last.end + 5, 24 * 60 - 5)
        return Slot(name: name, start: start, end: min(start + length, 24 * 60 - 1))
    }

    /// The next unused period name in a day: "P1", "P2"… with no upper limit.
    static func nextName(in slots: [Slot]) -> String {
        let used = Set(slots.map(\.name))
        var n = 1
        while used.contains("P\(n)") { n += 1 }
        return "P\(n)"
    }

    /// A new, unnamed day type. The first one takes the given weekdays, so one day type is already a whole week.
    mutating func addDayType(weekdays: [Int]) -> UUID {
        let type = DayType(name: "")
        if dayTypes.isEmpty { for wd in weekdays { week[wd] = type.id } }
        dayTypes.append(type)
        return type.id
    }

    /// Dropping a day type also drops it from the week and from today's switch.
    mutating func remove(dayType id: UUID) {
        dayTypes.removeAll { $0.id == id }
        week = week.filter { $0.value != id }
        if today?.dayType == id { today = nil }
    }

    /// Rename one day type; nothing happens when it's gone.
    mutating func name(dayType id: UUID, _ name: String) {
        guard let i = dayTypes.firstIndex(where: { $0.id == id }) else { return }
        dayTypes[i].name = name
    }

    /// One day type's periods in time order, every one (even a half-typed one that ends before it starts).
    func slots(in id: UUID) -> [Slot] {
        (dayTypes.first { $0.id == id }?.slots ?? []).sorted { $0.start < $1.start }
    }

    mutating func setSlots(_ slots: [Slot], in id: UUID) {
        guard let i = dayTypes.firstIndex(where: { $0.id == id }) else { return }
        dayTypes[i].slots = slots.sorted { $0.start < $1.start }
    }

    /// Change one period wherever it is; nothing happens when it's gone (deleted while its editor was open).
    mutating func update(slot id: UUID, _ change: (inout Slot) -> Void) {
        for d in dayTypes.indices {
            guard let i = dayTypes[d].slots.firstIndex(where: { $0.id == id }) else { continue }
            change(&dayTypes[d].slots[i])
            return
        }
    }

    func slot(_ id: UUID) -> Slot? { dayTypes.lazy.flatMap(\.slots).first { $0.id == id } }

    /// Rename a period. Its class, room and teacher go with it unless the new name already has its own
    /// (P3 on another day), and names no period uses any more are forgotten.
    mutating func rename(slot id: UUID, to name: String) {
        guard let old = slot(id)?.name, old != name else { return }
        update(slot: id) { $0.name = name }
        if periods[name] == nil { periods[name] = periods[old] }
        let used = Set(dayTypes.flatMap { $0.slots.map(\.name) })
        periods = periods.filter { used.contains($0.key) }
    }

    /// Drop a period by name from every day type, with its class, room and teacher.
    mutating func remove(period name: String) {
        for d in dayTypes.indices { dayTypes[d].slots.removeAll { $0.name == name } }
        periods[name] = nil
    }

    mutating func remove(slot id: UUID) {
        for d in dayTypes.indices { dayTypes[d].slots.removeAll { $0.id == id } }
    }

    /// Every slot name in use, in first-seen order.
    var periodNames: [String] {
        var seen = Set<String>(), out: [String] = []
        for n in dayTypes.flatMap({ $0.ordered.map(\.name) }) where seen.insert(n).inserted { out.append(n) }
        return out
    }

}

extension BellSchedule {
    /// A pretend half hour for trying the widget (Xcode builds only): the student's own periods,
    /// a few minutes each from `now`, every day of the week, so the bell comes quickly. Starts
    /// in class with 3 minutes left; a passing period, lunch and two more classes follow.
    static func test(from real: BellSchedule, at now: Date, calendar: Calendar = .current) -> BellSchedule {
        let c = calendar.dateComponents([.hour, .minute], from: now)
        let m = min((c.hour ?? 0) * 60 + (c.minute ?? 0), 24 * 60 - 32)
        let names = Array(real.periodNames.filter { !["Lunch", "Brunch", "Tutorial"].contains($0) }.prefix(4))
        let n = names.count == 4 ? names : ["P1", "P2", "P3", "P4"]
        var day = DayType(name: "Widget test")
        day.slots = [
            Slot(name: n[0], start: m - 8, end: m + 3), Slot(name: n[1], start: m + 4, end: m + 9),
            Slot(name: "Lunch", start: m + 9, end: m + 12), Slot(name: n[2], start: m + 13, end: m + 20),
            Slot(name: n[3], start: m + 21, end: m + 30),
        ]
        var s = BellSchedule(dayTypes: [day], periods: real.periods)
        for wd in 1...7 { s.week[wd] = day.id }
        return s
    }
}

/// The schedule's place in the App Group, so the widget reads what the app saved.
enum BellStore {
    private static let key = "bellSchedule"
    private static var defaults: UserDefaults? { UserDefaults(suiteName: SharedStore.group) }

    static func load() -> BellSchedule {
        guard let d = defaults?.data(forKey: key), let s = try? JSONDecoder().decode(BellSchedule.self, from: d) else { return BellSchedule() }
        return s
    }

    static func save(_ s: BellSchedule) {
        guard let d = try? JSONEncoder().encode(s) else { return }
        defaults?.set(d, forKey: key)
    }

    // A widget test (BellSchedule.test) runs beside the real schedule, never in place of it.
    private struct Test: Codable { var schedule: BellSchedule; var until: Date }
    private static let testKey = "bellScheduleTest"

    private static var test: Test? {
        defaults?.data(forKey: testKey).flatMap { try? JSONDecoder().decode(Test.self, from: $0) }
    }

    /// When the running test ends, nil with none.
    static var testUntil: Date? { test.map(\.until).flatMap { $0 > Date() ? $0 : nil } }

    static func startTest(_ s: BellSchedule, until: Date) {
        defaults?.set(try? JSONEncoder().encode(Test(schedule: s, until: until)), forKey: testKey)
    }

    static func stopTest() { defaults?.removeObject(forKey: testKey) }

    /// What the widget shows: the test while one runs, else the real schedule.
    static func widgetSchedule(now: Date = Date()) -> BellSchedule {
        if let t = test, t.until > now { return t.schedule }
        return load()
    }
}
