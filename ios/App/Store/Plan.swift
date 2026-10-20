import Foundation

/// Plan (To Do › Plan): the student's own order for the work in front of them,
/// 1 to N, plus Not today. Pure, so tests can run it (DeweyTests PlanTests).
///
/// - In the plan: open work that is late, due in the next 7 days, the student's
///   own tasks, and anything added by hand.
/// - Order: the student's ranks. Work they haven't placed yet (a new item) is
///   slotted in by due date: before the first placed item due later than it, so
///   something due tomorrow never lands under work placed last week.
/// - Not today: skipped for the day it was put there; back in its old place the
///   next morning.
enum PlanLogic {
    static let days = 7

    struct Entry: Equatable {
        var id: String
        var rank: Double?
        var due: Date?
    }

    /// In the plan's pool (before Not today and Done are split off).
    static func inPool(due: Date?, mine: Bool, added: Bool, late: Bool, older: Bool, now: Date, calendar: Calendar = .current) -> Bool {
        if mine || added { return true }
        if older { return false }
        if late { return true }
        guard let due else { return false }
        let end = calendar.date(byAdding: .day, value: days, to: calendar.startOfDay(for: now))!
        return due >= now && due < end
    }

    /// Skipped today: Not today holds it until the day ends.
    static func isSkipped(_ skipDay: Date?, now: Date, calendar: Calendar = .current) -> Bool {
        skipDay.map { calendar.isDate($0, inSameDayAs: now) } ?? false
    }

    /// The plan's order: placed items by rank, each unplaced one slotted in by due date.
    static func order(_ entries: [Entry]) -> [Entry] {
        let byDue: (Entry, Entry) -> Bool = { a, b in
            let x = a.due ?? .distantFuture, y = b.due ?? .distantFuture
            return x != y ? x < y : a.id < b.id
        }
        var out = entries.filter { $0.rank != nil }.sorted { a, b in a.rank! != b.rank! ? a.rank! < b.rank! : byDue(a, b) }
        for u in entries.filter({ $0.rank == nil }).sorted(by: byDue) {
            let due = u.due ?? .distantFuture
            let i = out.firstIndex { $0.rank != nil && ($0.due ?? .distantFuture) > due } ?? out.count
            out.insert(u, at: i)
        }
        return out
    }

    /// New ranks after a drag (List's onMove): every shown item gets its place,
    /// 1…N, so what the student sees is what is kept.
    static func moved(_ ids: [String], from: IndexSet, to: Int) -> [String: Double] {
        var list = ids
        let moving = from.map { list[$0] }
        for i in from.sorted(by: >) { list.remove(at: i) }
        let at = to - from.filter { $0 < to }.count
        list.insert(contentsOf: moving, at: max(0, min(at, list.count)))
        return ranks(list)
    }

    /// One item to a new place (Move to top / up / down / to bottom).
    static func moved(_ ids: [String], id: String, toIndex: Int) -> [String: Double] {
        var list = ids.filter { $0 != id }
        list.insert(id, at: max(0, min(toIndex, list.count)))
        return ranks(list)
    }

    static func ranks(_ ids: [String]) -> [String: Double] {
        Dictionary(uniqueKeysWithValues: ids.enumerated().map { ($1, Double($0 + 1)) })
    }
}
