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
                VStack(alignment: .leading, spacing: Theme.Space.m) {
                    ScreenHeader(title: "Grades") { EmptyView() }
                        .padding(.top, Theme.Space.s)
                    newCard
                    // Its own heading and a gap, so the classes never read as more new grades.
                    Text("Classes").font(Theme.title)
                        .padding(.top, model.newGrades().isEmpty ? 0 : Theme.Space.l)
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
        await model.twoAtATime(model.classes) { await model.loadGrades($0.sectionId, force: force) }
    }

    /// Scores that appeared since the student last looked, newest first.
    @ViewBuilder
    private var newCard: some View {
        let rows = model.newGrades()
        if !rows.isEmpty {
            HStack(alignment: .firstTextBaseline) {
                Text("New grades").font(Theme.title)
                Text("\(rows.count)").font(Theme.footnote.weight(.bold).monospacedDigit()).foregroundStyle(Theme.onAccent)
                    .padding(.horizontal, 7).padding(.vertical, 1).background(Theme.accent, in: Capsule())
                    .accessibilityHidden(true)
                Spacer()
                Button("Mark all seen") { withAnimation(.snappy(duration: 0.25)) { model.markAllGradesSeen() } }
                    .font(Theme.subhead.weight(.semibold)).foregroundStyle(Theme.accent)
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
            Text("Recently graded").font(Theme.title).padding(.top, Theme.Space.l)
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
                                Text(r.1.title).font(Theme.subhead.weight(.semibold)).oneLine()
                                if isNew { NewTag() }
                            }
                            Text(r.0.shortTitle).font(Theme.footnote).foregroundStyle(Theme.secondary)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 3) {
                            Text(scoreText(r.1)).font(Theme.subhead.weight(.semibold).monospacedDigit())
                            if let i = r.1.impact, abs(i) >= 0.005 { Text(Fmt.signed(i)).font(Theme.caption.monospacedDigit()).foregroundStyle(i < 0 ? Theme.late : Theme.good) }
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
        Text("NEW").font(Theme.caption.weight(.bold).monospacedDigit()).foregroundStyle(Theme.onAccent)
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
        let d = g?.pct.map { $0 - cutoff }
        VStack(alignment: .leading, spacing: Theme.Space.s) {
            // Name and distance to the A line · trend · letter and %.
            HStack(alignment: .center, spacing: Theme.Space.m) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(course.shortTitle).font(Theme.headline).oneLine()
                        if newCount > 0 {
                            Text("\(newCount) new").font(Theme.caption.weight(.semibold)).foregroundStyle(Theme.onAccent)
                                .padding(.horizontal, 6).padding(.vertical, 1).background(Theme.accent, in: Capsule())
                        }
                    }
                    if let d {
                        (Text(String(format: d >= 0 ? "+%.1f over A" : "%.1f under A", d)).fontWeight(.semibold)
                            .foregroundStyle(d >= 0 ? Theme.good : Theme.late)
                         + Text(" · \(course.periodLabel)").foregroundStyle(Theme.tertiary))
                            .font(Theme.footnote.monospacedDigit()).oneLine()
                    } else {
                        Text(course.periodLabel).font(Theme.footnote).foregroundStyle(Theme.tertiary).oneLine()
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let g, g.pct != nil {
                    let vals = g.history.compactMap(\.pct)
                    if vals.count > 1 {
                        Chart(Array(vals.enumerated()), id: \.offset) { i, v in
                            LineMark(x: .value("n", i), y: .value("%", v)).foregroundStyle(Theme.accent).interpolationMethod(.monotone)
                            RuleMark(y: .value("A", cutoff)).foregroundStyle(Theme.good.opacity(0.5)).lineStyle(StrokeStyle(lineWidth: 1, dash: [3, 3]))
                        }
                        .chartXAxis(.hidden)
                        .chartYAxis(.hidden)
                        .chartYScale(domain: max(0, min(vals.min() ?? 80, cutoff) - 3)...max(100, vals.max() ?? 100))
                        .frame(width: 96, height: 28)
                        .accessibilityHidden(true)
                    }
                    VStack(alignment: .trailing, spacing: 0) {
                        Text(g.letter ?? "").font(Theme.displaySmall)
                        Text(Fmt.pct(g.pct)).font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary)
                    }
                    // Same width on every card, so the trend lines line up down the screen.
                    .frame(minWidth: 56, alignment: .trailing)
                } else if model.gradeErrors[course.sectionId] != nil && g == nil {
                    Text("Couldn't load").font(Theme.footnote).foregroundStyle(Theme.secondary)
                } else if g != nil {
                    Text("No grades yet").font(Theme.footnote).foregroundStyle(Theme.secondary)
                } else {
                    ProgressView().controlSize(.small)
                }
            }
            // The latest score and what it did to the grade.
            if let g, g.pct != nil, let last = g.items.filter({ $0.earned != nil }).min(by: model.gradedFirst) {
                Theme.divider.frame(height: 1)
                HStack(spacing: Theme.Space.s) {
                    Text(last.title).foregroundStyle(Theme.secondary).oneLine().frame(maxWidth: .infinity, alignment: .leading)
                    Text(scoreText(last)).foregroundStyle(Theme.secondary)
                    if let i = last.impact, abs(i) >= 0.005 {
                        Text(Fmt.signed(i)).fontWeight(.semibold).foregroundStyle(i < 0 ? Theme.late : Theme.good)
                    }
                }
                .font(Theme.footnote.monospacedDigit())
            }
        }
        .padding(.horizontal, Theme.Space.l).padding(.vertical, Theme.Space.m)
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
