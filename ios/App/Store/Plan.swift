import Foundation

/// Plan (To Do › Plan): the student's open work in three sections, To do,
/// Later and Done, in their own order. Pure, so tests can run it (PlanTests).
///
/// - Where an item goes until the student moves it (by due date): To do for
///   late work, work due today or tomorrow, a test in the next 3 days, and an
///   own task with no date; Later for the rest. Done holds what was finished today.
/// - Dragging a row into another section keeps it there (`planSection`);
///   dragging it into Done marks it done, out of Done marks it to do.
/// - Order inside a section: the student's ranks. Work not placed yet is
///   slotted in by due date: before the first placed item due later than it,
///   so something due tomorrow never lands under work placed last week.
enum PlanLogic {
    enum Bucket: String, CaseIterable { case todo, later, done
        var title: String { switch self { case .todo: "To do"; case .later: "Later"; case .done: "Done" } }
    }

    /// Where an item goes when the student hasn't put it anywhere.
    static func autoBucket(due: Date?, late: Bool, isTest: Bool, mine: Bool, now: Date, calendar: Calendar = .current) -> Bucket {
        if late { return .todo }
        guard let due else { return mine ? .todo : .later }
        let today = calendar.startOfDay(for: now)
        let days = isTest ? 4 : 2   // a test: today + the next 3 days, so there's time to study
        return due < calendar.date(byAdding: .day, value: days, to: today)! ? .todo : .later
    }

    struct Entry: Equatable {
        var id: String
        var rank: Double?
        var due: Date?
    }

    /// One section's order: placed items by rank, each unplaced one slotted in by due date.
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

    /// The list as shown: each section's heading, then its items. Headings are rows
    /// too, so dragging an item past one moves it into that section.
    enum Row: Hashable {
        case header(Bucket)
        case item(String)
    }

    static func rows(todo: [String], later: [String], done: [String]) -> [Row] {
        [.header(.todo)] + todo.map(Row.item) + [.header(.later)] + later.map(Row.item) + [.header(.done)] + done.map(Row.item)
    }

    /// A collapsed section's items aren't rows: `hidden` holds them by section,
    /// so a drop on a collapsed heading lands at the top of that section and the
    /// rest keep their order under it.
    typealias Hidden = [Bucket: [String]]

    /// Where every item ends up after a drag (List's onMove): its section, and its
    /// place in it (1…). An item dropped above the first heading is in To do.
    static func moved(_ rows: [Row], from: IndexSet, to: Int, hidden: Hidden = [:]) -> [String: (Bucket, Double)] {
        place(expand(moving(rows, from: from, to: to), hidden))
    }

    static func moving(_ rows: [Row], from: IndexSet, to: Int) -> [Row] {
        var list = rows
        let moving = from.map { list[$0] }
        for i in from.sorted(by: >) { list.remove(at: i) }
        let at = to - from.filter { $0 < to }.count
        list.insert(contentsOf: moving, at: max(0, min(at, list.count)))
        return list
    }

    /// One item to another section (a swipe, VoiceOver), at the top of it.
    static func moved(_ rows: [Row], id: String, to bucket: Bucket, hidden: Hidden = [:]) -> [String: (Bucket, Double)] {
        var list = rows.filter { $0 != .item(id) }
        let h = list.firstIndex(of: .header(bucket)) ?? list.count - 1
        list.insert(.item(id), at: h + 1)
        return place(expand(list, hidden))
    }

    /// One item up or down a row (VoiceOver, the grip), crossing a heading if it's next.
    /// Never above the first heading.
    static func nudging(_ rows: [Row], id: String, by step: Int) -> [Row] {
        guard let i = rows.firstIndex(of: .item(id)) else { return rows }
        var list = rows
        let j = max(1, min(list.count - 1, i + step))
        list.swapAt(i, j)
        return list
    }

    static func nudged(_ rows: [Row], id: String, by step: Int, hidden: Hidden = [:]) -> [String: (Bucket, Double)] {
        place(expand(nudging(rows, id: id, by: step), hidden))
    }

    /// The shown rows with each collapsed section's items put back at its end.
    static func expand(_ rows: [Row], _ hidden: Hidden) -> [Row] {
        guard !hidden.isEmpty else { return rows }
        var out: [Row] = []
        var open: Bucket?
        func flush() { if let b = open { out += (hidden[b] ?? []).map(Row.item) } }
        for r in rows {
            if case .header(let b) = r { flush(); open = b }
            out.append(r)
        }
        flush()
        return out
    }

    static func place(_ list: [Row]) -> [String: (Bucket, Double)] {
        var out: [String: (Bucket, Double)] = [:]
        var bucket = Bucket.todo
        var n = 0.0
        for r in list {
            switch r {
            case .header(let b): bucket = b; n = 0
            case .item(let id): n += 1; out[id] = (bucket, n)
            }
        }
        return out
    }
}
