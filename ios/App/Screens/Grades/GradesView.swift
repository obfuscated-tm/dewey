import Charts
import SwiftUI

/// 2a · New grades (only when there are some), a card per class (letter + %,
/// history, distance to the A line, latest score and its effect), then
/// recently graded work across classes.
struct GradesView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    ScreenHeader(title: "Grades") { EmptyView() }
                        .padding(.top, 8)
                    newCard
                    if model.classes.isEmpty {
                        SkeletonRows(count: 4)
                    }
                    ForEach(model.classes) { c in
                        NavigationLink(value: c) { GradeCard(course: c) }
                            .buttonStyle(.plain)
                    }
                    recent
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(Theme.background)
            .refreshable { await loadAll(force: true) }
            .navigationDestination(for: Course.self) { CourseGradesView(course: $0) }
            .toolbar(.hidden, for: .navigationBar)
            .task { await loadAll(force: false) }
        }
    }

    private func loadAll(force: Bool) async {
        for c in model.classes { await model.loadGrades(c.sectionId, force: force) }
    }

    /// Scores that appeared since the student last looked, newest first.
    @ViewBuilder
    private var newCard: some View {
        let rows = model.newGrades()
        if !rows.isEmpty {
            HStack(alignment: .firstTextBaseline) {
                Text("New grades").font(Theme.font(20, .bold))
                Text("\(rows.count)").font(Theme.mono(13, .bold)).foregroundStyle(Theme.onAccent)
                    .padding(.horizontal, 7).padding(.vertical, 1).background(Theme.accent, in: Capsule())
                    .accessibilityHidden(true)
                Spacer()
                Button("Mark all seen") { withAnimation(.snappy(duration: 0.25)) { model.markAllGradesSeen() } }
                    .font(Theme.font(14, .semibold)).foregroundStyle(Theme.accent)
            }
            .accessibilityElement(children: .contain)
            gradeList(rows, showNew: false)
        }
    }

    /// Newest scores across all classes (by when the score appeared, then due date).
    @ViewBuilder
    private var recent: some View {
        let rows: [(Course, CourseGrade.Item)] = model.classes.flatMap { c in
            (model.grades[c.sectionId]?.items ?? []).filter { $0.earned != nil }.map { (c, $0) }
        }
        .sorted { model.gradedFirst($0.1, $1.1) }
        if !rows.isEmpty {
            Text("Recently graded").font(Theme.font(20, .bold)).padding(.top, 10)
            gradeList(Array(rows.prefix(8)), showNew: true)
        }
    }

    private func gradeList(_ rows: [(Course, CourseGrade.Item)], showNew: Bool) -> some View {
        VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.element.1.id) { i, r in
                NavigationLink {
                    GradeDetailView(sectionId: r.0.sectionId, itemId: r.1.id)
                } label: {
                    let isNew = showNew && model.isNewGrade(r.1.id)
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 6) {
                                Text(r.1.title).font(Theme.font(15, .semibold)).oneLine()
                                if isNew { NewTag() }
                            }
                            Text(r.0.shortTitle).font(Theme.font(13)).foregroundStyle(Theme.secondary)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 3) {
                            Text(scoreText(r.1)).font(Theme.mono(14, .semibold))
                            if let i = r.1.impact, abs(i) >= 0.005 { Text(Fmt.signed(i)).font(Theme.mono(12)).foregroundStyle(i < 0 ? Theme.late : Theme.good) }
                        }
                    }
                    .padding(14)
                    .accessibilityElement(children: .combine)
                    .overlay(alignment: .top) { if i > 0 { Theme.divider.frame(height: 1) } }
                }
                .buttonStyle(.plain)
            }
        }
        .card()
    }
}

/// The small "NEW" capsule on a score the student hasn't looked at.
struct NewTag: View {
    var body: some View {
        Text("NEW").font(Theme.mono(10, .bold)).foregroundStyle(Theme.onAccent)
            .padding(.horizontal, 5).padding(.vertical, 1).background(Theme.accent, in: Capsule())
    }
}

func scoreText(_ i: CourseGrade.Item) -> String {
    if let e = i.earned, let p = i.possible { return "\(Fmt.num(e)) / \(Fmt.num(p))" }
    // "—Collected", "Missing": show the word.
    let g = i.grade.trimmingCharacters(in: .whitespaces)
    return g.isEmpty ? "—" : g
}

struct GradeCard: View {
    @Environment(AppModel.self) private var model
    let course: Course

    var body: some View {
        let g = model.grades[course.sectionId]
        let cutoff = model.courseState(course.sectionId).aCutoff ?? 93
        let newCount = model.newGradeCount(course.sectionId)
        VStack(alignment: .leading, spacing: 10) {
            AdaptiveStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(course.shortTitle).font(Theme.font(16, .semibold)).oneLine()
                    Text(course.periodLabel).font(Theme.font(12.5)).foregroundStyle(Theme.tertiary).oneLine()
                }
                Spacer(minLength: 0)
                if let g, g.pct != nil {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        if newCount > 0 {
                            Text("\(newCount) new").font(Theme.font(12, .bold)).foregroundStyle(Theme.onAccent)
                                .padding(.horizontal, 7).padding(.vertical, 2).background(Theme.accent, in: Capsule())
                        }
                        Text(g.letter ?? "").font(Theme.font(26, .bold))
                        Text(Fmt.pct(g.pct)).font(Theme.mono(15, .medium)).foregroundStyle(Theme.secondary)
                    }
                } else if model.gradeErrors[course.sectionId] != nil && g == nil {
                    Text("Couldn't load").font(Theme.font(13)).foregroundStyle(Theme.secondary)
                } else if g != nil {
                    Text("No grades yet").font(Theme.font(13)).foregroundStyle(Theme.secondary)
                } else {
                    ProgressView().controlSize(.small)
                }
            }
            if let g, let pct = g.pct {
                let vals = g.history.compactMap(\.pct)
                if vals.count > 1 {
                    Chart(Array(vals.enumerated()), id: \.offset) { i, v in
                        LineMark(x: .value("n", i), y: .value("%", v)).foregroundStyle(Theme.accent).interpolationMethod(.monotone)
                        RuleMark(y: .value("A", cutoff)).foregroundStyle(Theme.good.opacity(0.4)).lineStyle(StrokeStyle(lineWidth: 1, dash: [3, 3]))
                    }
                    .chartXAxis(.hidden)
                    .chartYAxis(.hidden)
                    .chartYScale(domain: max(0, min(vals.min() ?? 80, cutoff) - 3)...max(100, vals.max() ?? 100))
                    .frame(height: 38)
                    .accessibilityHidden(true)
                }
                AdaptiveStack {
                    let d = pct - cutoff
                    Text(d >= 0 ? "\(String(format: "%.1f", d)) above the A line" : "\(String(format: "%.1f", -d)) below the A line")
                        .font(Theme.font(13)).foregroundStyle(d >= 0 ? Theme.good : Theme.late)
                    Spacer(minLength: 0)
                    if let last = g.items.filter({ $0.earned != nil }).min(by: model.gradedFirst) {
                        Text("\(last.title) \(scoreText(last))").font(Theme.font(12.5)).foregroundStyle(Theme.secondary).oneLine()
                            .truncationMode(.middle).fixedSize(horizontal: false, vertical: true)
                        if let i = last.impact, abs(i) >= 0.005 { Text(Fmt.signed(i)).font(Theme.mono(12)).foregroundStyle(i < 0 ? Theme.late : Theme.good) }
                    }
                }
            }
        }
        .padding(16)
        .card()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(a11y(g, cutoff))
        .accessibilityHint("Shows every grade in this class")
    }

    private func a11y(_ g: CourseGrade?, _ cutoff: Double) -> String {
        var parts = [course.shortTitle]
        let n = model.newGradeCount(course.sectionId)
        if n > 0 { parts.append(n == 1 ? "1 new grade" : "\(n) new grades") }
        if let g, let p = g.pct {
            parts.append("\(g.letter ?? ""), \(String(format: "%.1f", p)) percent")
            let d = p - cutoff
            parts.append(String(format: d >= 0 ? "%.1f points above the A line" : "%.1f points below the A line", abs(d)))
        } else if model.gradeErrors[course.sectionId] != nil {
            parts.append("couldn't load")
        } else if g != nil { parts.append("no grades yet") } else { parts.append("loading") }
        return parts.joined(separator: ", ")
    }
}
