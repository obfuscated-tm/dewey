import SwiftUI

/// Which part of the To Do list is showing: overdue work, or one day of the
/// coming week (0 = today).
enum DayFilter: Hashable {
    case late
    case day(Int)

    func matches(_ it: ItemState, now: Date, calendar: Calendar = .current) -> Bool {
        let b = TodoLogic.bucket(due: it.due, now: now, calendar: calendar)
        switch self {
        case .late:
            return b == .overdue && !TodoLogic.isOlder(due: it.due, now: now)
                && TodoLogic.showsLate(type: it.type, isQuiz: it.isQuiz, done: it.type == .exam ? it.studied : it.done, turnedIn: it.turnedIn, submitted: it.submitted, inSchoology: it.inSchoology)
        case .day(let k):
            guard let due = it.due, b != .overdue,
                  let d = calendar.date(byAdding: .day, value: k, to: calendar.startOfDay(for: now)) else { return false }
            return calendar.isDate(due, inSameDayAs: d)
        }
    }
}

/// The To Do header: Late, then today and the next six days, as bare type (no
/// boxes). Each item still to do is a dot in its type's colour (black = test,
/// grey = the rest, like the type bar on each row); tap a day to show only it,
/// and it sits in a drop.
struct WeekStrip: View {
    let items: [ItemState]     // open items only
    let now: Date
    @Binding var selected: DayFilter?
    @Environment(\.dynamicTypeSize) private var typeSize

    private let calendar = Calendar.current

    var body: some View {
        let tiles: [DayFilter] = [.late] + (0..<7).map { .day($0) }
        let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: typeSize.isAccessibilitySize ? 4 : 8)
        LazyVGrid(columns: columns, spacing: 4) {
            ForEach(tiles, id: \.self) { f in tile(f) }
        }
    }

    private func date(_ k: Int) -> Date { calendar.date(byAdding: .day, value: k, to: calendar.startOfDay(for: now))! }

    private func tile(_ f: DayFilter) -> some View {
        let its = items.filter { f.matches($0, now: now) }
            .sorted { ($0.type == .exam ? 0 : 1) < ($1.type == .exam ? 0 : 1) }   // tests first, so they show when it overflows
        let on = selected == f
        let isLate = f == .late
        let isToday = f == .day(0)
        let label: String = {
            switch f {
            case .late: return "Late"
            case .day(0): return "Today"
            case .day(let k): return Self.weekday.string(from: date(k))
            }
        }()
        let sub: String = {
            if case .day(let k) = f { return Self.dayNumber.string(from: date(k)) }
            return "\(its.count)"
        }()
        let labelColor: Color = on ? Theme.onAccent : isLate ? Theme.late : isToday ? Theme.accent : Theme.secondary
        return Button {
            withAnimation(.snappy(duration: 0.2)) { selected = on ? nil : f }
        } label: {
            VStack(spacing: 3) {
                Text(label).font(Theme.caption.weight(.semibold)).foregroundStyle(isLate ? Theme.late : isToday ? Theme.accent : Theme.secondary)
                    .lineLimit(1).minimumScaleFactor(0.7)
                // Bare type; the chosen day sits in a drop, the logo's shape.
                Text(sub).font(Theme.subhead.weight(.semibold).monospacedDigit())
                    .foregroundStyle(on ? Theme.onAccent : isLate ? Theme.late : Theme.text)
                    .frame(minWidth: 32, minHeight: 30)
                    .padding(.top, 6)
                    .background {
                        if on { DropShape().fill(isLate ? Theme.late : Theme.accent).padding(.horizontal, 1) }
                    }
                dots(its, on: false)
            }
            .padding(.vertical, 4)
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(a11y(f, label: label, its))
        .accessibilityAddTraits(on ? .isSelected : [])
        .accessibilityHint(on ? "Shows the whole list again" : "Shows only these")
    }

    /// Up to six dots in two rows of three; more than six → five dots and "+N".
    private func dots(_ its: [ItemState], on: Bool) -> some View {
        let shown = its.count > 6 ? Array(its.prefix(5)) : its
        let extra = its.count - shown.count
        let rows = stride(from: 0, to: max(shown.count, 1), by: 3).map { Array(shown[$0..<min($0 + 3, shown.count)]) }
        return VStack(spacing: 3) {
            ForEach(Array(rows.enumerated()), id: \.offset) { r, row in
                HStack(spacing: 3) {
                    ForEach(row, id: \.schoologyId) { it in
                        Circle().fill(dotColor(it, on: on)).frame(width: 6, height: 6)
                    }
                    if r == rows.count - 1 && extra > 0 {
                        Text("+\(extra)").font(Theme.caption.weight(.bold).monospacedDigit())
                            .foregroundStyle(on ? Theme.onAccent : Theme.secondary).fixedSize()
                    }
                }
            }
        }
        .frame(height: 17, alignment: .top)
        .accessibilityHidden(true)
    }

    private func dotColor(_ it: ItemState, on: Bool) -> Color {
        if on { return it.type == .exam ? Theme.onAccent : Theme.onAccent.opacity(0.55) }
        // Light grey like the HW bar, so the black test dots stand out.
        return it.type == .exam ? Theme.text : Theme.tertiary.opacity(0.45)
    }

    private func a11y(_ f: DayFilter, label: String, _ its: [ItemState]) -> String {
        let tests = its.filter { $0.type == .exam }.count
        var name = label
        if case .day(let k) = f, k > 0 { name = Self.longDay.string(from: date(k)) }
        var parts = [name, its.isEmpty ? "nothing due" : "\(its.count) \(f == .late ? "overdue" : "due")"]
        if tests > 0 { parts.append(tests == 1 ? "1 test" : "\(tests) tests") }
        return parts.joined(separator: ", ")
    }

    private static func fmt(_ p: String) -> DateFormatter {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US"); f.dateFormat = p; return f
    }
    private static let weekday = fmt("EEE")
    private static let dayNumber = fmt("d")
    private static let longDay = fmt("EEEE, MMM d")
}
