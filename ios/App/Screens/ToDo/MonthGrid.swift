import SwiftUI

/// The week strip, opened up to a month (To Do › List, "Month"): a dot per
/// open item on each day (black = test). Tap a day to list it and the day
/// after it (DayFilter.span); tap it again to show everything. The month shown is the
/// screen's (swiping sideways on it, CalendarPull, steps it too).
struct MonthGrid: View {
    let items: [ItemState]     // open items only
    let now: Date
    @Binding var selected: DayFilter?
    @Binding var month: Date
    /// Which way the last step went, so the days slide in from that side.
    @Binding var forward: Bool
    @Environment(\.dynamicTypeSize) private var typeSize

    private let cal = Calendar.current

    static func start(of d: Date) -> Date {
        let c = Calendar.current
        return c.date(from: c.dateComponents([.year, .month], from: d))!
    }

    var body: some View {
        VStack(spacing: Theme.Space.s) {
            HStack {
                Text(Self.monthTitle.string(from: month)).font(Theme.headline).foregroundStyle(Theme.text)
                Spacer()
                Button { step(-1) } label: { Image(systemName: "chevron.left").frame(width: 44, height: 36) }
                    .accessibilityLabel("Previous month")
                Button { step(1) } label: { Image(systemName: "chevron.right").frame(width: 44, height: 36) }
                    .accessibilityLabel("Next month")
            }
            .buttonStyle(.borderless)
            .font(Theme.subhead.weight(.semibold))
            let columns = Array(repeating: GridItem(.flexible(), spacing: 2), count: 7)
            LazyVGrid(columns: columns, spacing: 2) {
                ForEach(Array(weekdays.enumerated()), id: \.offset) { _, d in
                    Text(d).font(Theme.caption.weight(.semibold)).foregroundStyle(Theme.tertiary)
                        .accessibilityHidden(true)
                }
            }
            LazyVGrid(columns: columns, spacing: 2) {
                ForEach(Array(cells.enumerated()), id: \.offset) { _, day in
                    if let day { cell(day) } else { Color.clear.frame(height: 1) }
                }
            }
            .id(month)
            .transition(.push(from: forward ? .trailing : .leading))
        }
        .padding(.horizontal, Theme.Space.xs)
    }

    private func step(_ n: Int) {
        forward = n > 0
        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) { month = cal.date(byAdding: .month, value: n, to: month)! }
    }

    private var weekdays: [String] {
        let s = cal.veryShortStandaloneWeekdaySymbols
        let first = cal.firstWeekday - 1
        return Array(s[first...] + s[..<first])
    }

    /// The month's days, with blanks before the 1st so it starts on its weekday.
    private var cells: [Date?] {
        let range = cal.range(of: .day, in: .month, for: month)!
        let lead = (cal.component(.weekday, from: month) - cal.firstWeekday + 7) % 7
        return Array(repeating: nil, count: lead) + range.map { cal.date(byAdding: .day, value: $0 - 1, to: month)! }
    }

    private func cell(_ day: Date) -> some View {
        let its = items.filter { $0.due.map { cal.isDate($0, inSameDayAs: day) } ?? false }
            .sorted { ($0.type == .exam ? 0 : 1) < ($1.type == .exam ? 0 : 1) }
        let isToday = cal.isDate(day, inSameDayAs: now)
        let past = day < cal.startOfDay(for: now)
        let on: Bool = {
            guard case .span(let start) = selected else { return false }
            return cal.isDate(day, inSameDayAs: start) || cal.isDate(day, inSameDayAs: cal.date(byAdding: .day, value: 1, to: start)!)
        }()
        let pick: () -> Void = {
            withAnimation(.snappy(duration: 0.2)) {
                if case .span(let s) = selected, cal.isDate(s, inSameDayAs: day) { selected = nil } else { selected = .span(cal.startOfDay(for: day)) }
            }
        }
        // A tap, not a Button, so a pull or a sideways swipe can start on a day (CalendarPull).
        return VStack(spacing: 2) {
                Text("\(cal.component(.day, from: day))")
                    .font(Theme.subhead.weight(isToday ? .bold : .regular).monospacedDigit())
                    .foregroundStyle(isToday ? Theme.accent : past ? Theme.tertiary : Theme.text)
                HStack(spacing: 2) {
                    ForEach(its.prefix(3), id: \.schoologyId) { it in
                        Circle().fill(it.type == .exam ? Theme.text : Theme.tertiary.opacity(0.5)).frame(width: 5, height: 5)
                    }
                }
                .frame(height: 5)
            }
            .frame(maxWidth: .infinity, minHeight: typeSize.isAccessibilitySize ? 56 : 40)
            .background {
                if on { RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous).fill(Theme.accentSoft) }
                if isToday { RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous).stroke(Theme.accent, lineWidth: 1.5) }
            }
            .contentShape(Rectangle())
        .onTapGesture(perform: pick)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(a11y(day, its))
        .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
        .accessibilityAction { pick() }
        .accessibilityHint("Shows this day and the next")
    }

    private func a11y(_ day: Date, _ its: [ItemState]) -> String {
        let tests = its.filter { $0.type == .exam }.count
        var parts = [Self.longDay.string(from: day), its.isEmpty ? "nothing due" : "\(its.count) due"]
        if tests > 0 { parts.append(tests == 1 ? "1 test" : "\(tests) tests") }
        return parts.joined(separator: ", ")
    }

    private static func fmt(_ p: String) -> DateFormatter {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US"); f.dateFormat = p; return f
    }
    private static let monthTitle = fmt("MMMM yyyy")
    private static let longDay = fmt("EEEE, MMMM d")
}
