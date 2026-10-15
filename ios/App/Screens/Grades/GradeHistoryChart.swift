import Charts
import SwiftUI

/// The class grade over the period, one point per day something was graded.
/// Drag a finger across it to read any day: its %, the change, and what was
/// graded. In what-if mode a dashed line runs from the last real point to the
/// what-if grade.
struct GradeHistoryChart: View {
    let history: [CourseGrade.Point]
    let cutoff: Double
    var whatIfPct: Double?

    @State private var selectedDate: Date?

    private struct Pt: Identifiable {
        let date: Date
        let pct: Double
        let items: [String]
        var undated = false
        var id: Date { date }
    }

    /// History ends with the work that has no due date (grademath sorts it last);
    /// it goes at today, so the line ends on the real class grade.
    private var points: [Pt] {
        var out: [Pt] = history.compactMap { p in
            guard let d = Fmt.wallDate(p.day), let v = p.pct else { return nil }
            return Pt(date: d, pct: v, items: p.items)
        }
        if let u = history.last(where: { $0.day == nil }), let v = u.pct {
            let today = Calendar.current.startOfDay(for: Date())
            let at = max(today, (out.last?.date ?? today).addingTimeInterval(86_400))
            out.append(Pt(date: at, pct: v, items: u.items, undated: true))
        }
        return out
    }

    /// The point nearest the finger.
    private var selected: Pt? {
        guard let s = selectedDate else { return nil }
        return points.min { abs($0.date.timeIntervalSince(s)) < abs($1.date.timeIntervalSince(s)) }
    }

    private var whatIfDate: Date? {
        guard whatIfPct != nil, let last = points.last?.date else { return nil }
        return max(Calendar.current.startOfDay(for: Date()), last.addingTimeInterval(3 * 86_400))
    }

    var body: some View {
        let pts = points
        if pts.count > 1 {
            VStack(alignment: .leading, spacing: Theme.Space.s) {
                readout(pts)
                chart(pts)
                    .frame(height: 170)
                Text(selected == nil ? "Drag across the graph to see any day." : " ")
                    .font(Theme.caption).foregroundStyle(Theme.tertiary)
                    .accessibilityHidden(true)
            }
            .padding(Theme.Space.l)
            .card()
            .sensoryFeedback(.selection, trigger: selected?.date)
        }
    }

    // ── The line above the graph: the picked day, or the latest ──────────

    @ViewBuilder
    private func readout(_ pts: [Pt]) -> some View {
        let p = selected ?? pts.last!
        let i = pts.firstIndex { $0.date == p.date } ?? 0
        let change = i > 0 ? p.pct - pts[i - 1].pct : nil
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .firstTextBaseline, spacing: Theme.Space.s) {
                Text(selected == nil ? "Grade over time" : p.undated ? "No due date" : Fmt.header.string(from: p.date))
                    .font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.secondary)
                Spacer()
                if selected == nil, let w = whatIfPct {
                    Text("What-if \(Fmt.pct(w, digits: 2))").font(Theme.footnote.weight(.semibold).monospacedDigit())
                        .foregroundStyle(Theme.accent)
                }
            }
            HStack(alignment: .firstTextBaseline, spacing: Theme.Space.s) {
                Text(Fmt.pct(p.pct, digits: 2)).font(Theme.title.monospacedDigit()).foregroundStyle(Theme.text)
                if let c = change, abs(c) >= 0.005 {
                    Text(Fmt.signed(c)).font(Theme.subhead.weight(.semibold).monospacedDigit())
                        .foregroundStyle(c < 0 ? Theme.late : Theme.good)
                }
            }
            Text(p.items.joined(separator: ", "))
                .font(Theme.footnote).foregroundStyle(Theme.secondary)
                .lineLimit(2, reservesSpace: true)
        }
        .accessibilityElement(children: .combine)
    }

    // ── The graph ────────────────────────────────────────────────────────

    private func chart(_ pts: [Pt]) -> some View {
        let vals = pts.map(\.pct) + [whatIfPct].compactMap { $0 }
        let lo = max(0, (min(vals.min() ?? 80, cutoff) - 2).rounded(.down))
        let hi = max(100, (vals.max() ?? 100).rounded(.up))
        return Chart {
            RuleMark(y: .value("A line", cutoff))
                .foregroundStyle(Theme.good.opacity(0.6))
                .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                .annotation(position: .top, alignment: .leading, spacing: 2) {
                    Text("A \(Fmt.num(cutoff))%").font(Theme.caption.weight(.semibold)).foregroundStyle(Theme.good)
                }
            ForEach(pts) { p in
                AreaMark(x: .value("Day", p.date), yStart: .value("Low", lo), yEnd: .value("Grade", p.pct))
                    .foregroundStyle(LinearGradient(colors: [Theme.accent.opacity(0.22), Theme.accent.opacity(0.02)],
                                                    startPoint: .top, endPoint: .bottom))
                    .interpolationMethod(.monotone)
                LineMark(x: .value("Day", p.date), y: .value("Grade", p.pct), series: .value("Line", "real"))
                    .foregroundStyle(Theme.accent)
                    .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round))
                    .interpolationMethod(.monotone)
            }
            if let w = whatIfPct, let d = whatIfDate, let last = pts.last {
                LineMark(x: .value("Day", last.date), y: .value("Grade", last.pct), series: .value("Line", "what-if"))
                    .foregroundStyle(Theme.accent.opacity(0.7))
                    .lineStyle(StrokeStyle(lineWidth: 2, dash: [5, 4]))
                LineMark(x: .value("Day", d), y: .value("Grade", w), series: .value("Line", "what-if"))
                    .foregroundStyle(Theme.accent.opacity(0.7))
                    .lineStyle(StrokeStyle(lineWidth: 2, dash: [5, 4]))
                PointMark(x: .value("Day", d), y: .value("Grade", w))
                    .symbol(.circle).symbolSize(50)
                    .foregroundStyle(Theme.card)
                    .annotation(position: .top, spacing: 2) {
                        Text("What-if").font(Theme.caption.weight(.semibold)).foregroundStyle(Theme.accent)
                    }
                PointMark(x: .value("Day", d), y: .value("Grade", w))
                    .symbol(.circle).symbolSize(22)
                    .foregroundStyle(Theme.accent)
            }
            if let s = selected {
                RuleMark(x: .value("Day", s.date))
                    .foregroundStyle(Theme.secondary.opacity(0.5))
                    .lineStyle(StrokeStyle(lineWidth: 1))
                PointMark(x: .value("Day", s.date), y: .value("Grade", s.pct))
                    .symbolSize(110).foregroundStyle(Theme.card)
                PointMark(x: .value("Day", s.date), y: .value("Grade", s.pct))
                    .symbolSize(45).foregroundStyle(Theme.accent)
            } else if let last = pts.last {
                PointMark(x: .value("Day", last.date), y: .value("Grade", last.pct))
                    .symbolSize(40).foregroundStyle(Theme.accent)
            }
        }
        .chartXSelection(value: $selectedDate)
        .chartYScale(domain: lo...hi)
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 4)) { _ in
                AxisGridLine().foregroundStyle(Theme.divider)
                AxisValueLabel(format: .dateTime.month(.abbreviated).day()).foregroundStyle(Theme.tertiary)
            }
        }
        .chartYAxis {
            AxisMarks(position: .trailing, values: .automatic(desiredCount: 4)) { v in
                AxisGridLine().foregroundStyle(Theme.divider)
                AxisValueLabel { if let d = v.as(Double.self) { Text("\(Fmt.num(d))%") } }.foregroundStyle(Theme.tertiary)
            }
        }
        .accessibilityLabel("Grade over time")
        .accessibilityValue(pts.map { "\(Fmt.short.string(from: $0.date)) \(Fmt.pct($0.pct))" }.joined(separator: ", "))
    }
}
