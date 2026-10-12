import SwiftUI

/// 2b · Every item of the current period by category, facts first. What-if
/// mode (off by default, cleared on leaving) gives ungraded rows an input and
/// holds "Plan an upcoming test".
struct CourseGradesView: View {
    @Environment(AppModel.self) private var model
    let course: Course
    @State private var whatIfOn = false
    @State private var whatIf: [String: String] = [:]
    @State private var preview: CourseGrade?
    @State private var planCategory = ""
    @State private var planPoints = "100"
    @State private var plan: PlanResult?
    @State private var sheet: SheetTarget?
    /// Categories opened (by title). Everything starts collapsed, like the extension; remembered per class.
    @AppStorage private var expandedRaw: String

    init(course: Course) {
        self.course = course
        _expandedRaw = AppStorage(wrappedValue: "", "expandedCategories-\(course.sectionId)")
    }

    private var expanded: Set<String> { Set(expandedRaw.split(separator: "\n").map(String.init)) }
    private func toggle(_ title: String) {
        var c = expanded
        if c.contains(title) { c.remove(title) } else { c.insert(title) }
        withAnimation(.snappy(duration: 0.25)) { expandedRaw = c.sorted().joined(separator: "\n") }
    }

    private var real: CourseGrade? { model.grades[course.sectionId] }
    private var shown: CourseGrade? { whatIfOn ? (preview ?? real) : real }
    private var cutoff: Double { model.courseState(course.sectionId).aCutoff ?? 93 }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                summary
                if whatIfOn { planner }
                if let g = shown {
                    if g.categories.count > 1 && !whatIfOn {
                        let allShut = g.categories.allSatisfy { !expanded.contains($0.title) }
                        HStack {
                            Text("Categories").font(Theme.font(13, .semibold)).foregroundStyle(Theme.secondary)
                            Spacer()
                            Button(allShut ? "Expand all" : "Collapse all") {
                                withAnimation(.snappy(duration: 0.25)) {
                                    expandedRaw = allShut ? g.categories.map(\.title).joined(separator: "\n") : ""
                                }
                            }
                            .font(Theme.font(13, .semibold))
                        }
                        .padding(.horizontal, 4)
                    }
                    ForEach(g.categories, id: \.title) { cat in categoryBlock(cat, g) }
                    if g.categories.isEmpty { EmptyState(icon: "tray", title: "No grades yet") }
                } else if let e = model.gradeErrors[course.sectionId] {
                    ErrorState(error: e, schoologyURL: gradesURL, retry: { Task { await model.loadGrades(course.sectionId, force: true) } },
                               open: { sheet = SheetTarget(url: $0) })
                } else {
                    SkeletonRows()
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle(course.shortTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button(whatIfOn ? "Done" : "What-if") {
                    whatIfOn.toggle()
                    if !whatIfOn { whatIf = [:]; preview = nil; plan = nil }
                }
                .fontWeight(.semibold)
            }
        }
        .refreshable { await model.loadGrades(course.sectionId, force: true) }
        .task { await model.loadGrades(course.sectionId) }
        .sheet(item: $sheet) { SchoologySheet(target: $0) }
        .onDisappear { whatIf = [:]; preview = nil }
    }

    private var gradesURL: URL { URL(string: "\(Engine.origin.absoluteString)/course/\(course.sectionId)/student_grades")! }

    // ── Summary ──────────────────────────────────────────────────────────

    private var summary: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 4) {
                Text(course.shortTitle).font(Theme.font(20, .bold)).foregroundStyle(Theme.text)
                Text([course.periodLabel, shown?.period ?? "Current period"].filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(Theme.font(13, .semibold)).foregroundStyle(Theme.secondary)
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(shown?.letter ?? "—").font(Theme.font(40, .bold))
                    Text(Fmt.pct(shown?.pct, digits: 2)).font(Theme.mono(20, .medium))
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(shown?.pct.map { "Grade \(shown?.letter ?? ""), \(String(format: "%.2f", $0)) percent" } ?? "No grade yet")
                if whatIfOn, let p = preview?.pct, let r = real?.pct {
                    Text("\(Fmt.signed(p - r)) from your real \(Fmt.pct(r)) (\(real?.letter ?? ""))")
                        .font(Theme.font(14, .semibold)).foregroundStyle(p < r ? Theme.late : Theme.good)
                    Button("Reset what-if") { whatIf = [:]; preview = nil }.font(Theme.font(14, .medium))
                } else if whatIfOn {
                    Text("Type a score in any box below to see your grade change. Nothing changes on Schoology.")
                        .font(Theme.font(13)).foregroundStyle(Theme.secondary)
                }
                if !whatIfOn, let g = shown {
                    Text(g.mode == "weighted" ? "Weighted by category" : "Total points").font(Theme.font(13)).foregroundStyle(Theme.tertiary)
                }
            }
            Spacer()
            if let at = model.gradesAsOf[course.sectionId] {
                Text(Fmt.ago(at)).font(Theme.font(12)).foregroundStyle(Theme.tertiary)
            }
        }
        .padding(16)
        .card()
    }

    // ── A category and its items ─────────────────────────────────────────

    private func categoryBlock(_ cat: CourseGrade.Category, _ g: CourseGrade) -> some View {
        let items = g.items.filter { $0.category == cat.title }
        let open = expanded.contains(cat.title) || whatIfOn
        return VStack(alignment: .leading, spacing: 0) {
            Button { toggle(cat.title) } label: {
                HStack(alignment: .firstTextBaseline) {
                    Image(systemName: "chevron.right")
                        .font(Theme.font(13, .semibold)).foregroundStyle(Theme.secondary)
                        .rotationEffect(.degrees(open ? 90 : 0))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(cat.title).font(Theme.font(16, .semibold)).foregroundStyle(Theme.text)
                        Text(open ? floorText(cat) : "\(items.count) item\(items.count == 1 ? "" : "s")\(floorText(cat).isEmpty ? "" : " · " + floorText(cat))")
                            .font(Theme.font(12.5)).foregroundStyle(cat.floorStatus == "out" ? Theme.late : Theme.secondary)
                            .multilineTextAlignment(.leading)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(Fmt.pct(cat.pct)).font(Theme.mono(15, .semibold)).foregroundStyle(Theme.text)
                        if let w = cat.weight { Text("\(Fmt.num(w))% of grade").font(Theme.font(12)).foregroundStyle(Theme.tertiary) }
                    }
                }
                .padding(14)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(whatIfOn)
            .accessibilityElement(children: .combine)
            .accessibilityValue(open ? "Expanded" : "Collapsed")
            .accessibilityHint(whatIfOn ? "" : open ? "Collapses this category" : "Shows this category's items")
            if open { ForEach(items) { it in
                Theme.divider.frame(height: 1)
                if whatIfOn && it.possible != nil {
                    whatIfRow(it)
                } else {
                    NavigationLink { GradeDetailView(sectionId: course.sectionId, itemId: it.id) } label: { itemRow(it) }
                        .buttonStyle(.plain)
                }
            } }
        }
        .card()
        .clipped()
    }

    private func floorText(_ c: CourseGrade.Category) -> String {
        switch c.floorStatus {
        case "any": return "Any score here keeps your A"
        case "out": return "This category alone can't reach the A line"
        case "need": return "A holds down to \(Fmt.pct(c.floor)) here"
        default: return c.possible > 0 ? "" : "No grades yet"
        }
    }

    private func itemRow(_ it: CourseGrade.Item) -> some View {
        HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text(it.title).font(Theme.font(15, .medium)).foregroundStyle(Theme.text).maxLines(2)
                HStack(spacing: 6) {
                    if let d = Fmt.wallDate(it.due) { Text(Fmt.short.string(from: d)).font(Theme.mono(12)).foregroundStyle(Theme.tertiary) }
                    if !it.comment.isEmpty { Label("Comment", systemImage: "text.bubble").font(Theme.font(11.5)).foregroundStyle(Theme.accent) }
                    if it.rubric != nil { Label("Rubric", systemImage: "tablecells").font(Theme.font(11.5)).foregroundStyle(Theme.accent) }
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                Text(scoreText(it)).font(Theme.mono(14, .semibold)).foregroundStyle(it.earned == nil ? Theme.tertiary : Theme.text)
                HStack(spacing: 6) {
                    if let p = it.pct { Text(Fmt.pct(p, digits: 0)).font(Theme.mono(12)).foregroundStyle(Theme.secondary) }
                    if let i = it.impact, abs(i) >= 0.005 { Text(Fmt.signed(i)).font(Theme.mono(12)).foregroundStyle(i < 0 ? Theme.late : Theme.good) }
                }
            }
        }
        .padding(14)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    /// Every item with points gets a box: blank = keep the real score (or leave it ungraded).
    private func whatIfRow(_ it: CourseGrade.Item) -> some View {
        let changed = !(whatIf[it.id] ?? "").isEmpty
        return HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(it.title).font(Theme.font(15, .medium)).foregroundStyle(Theme.text).maxLines(2)
                Text(it.earned != nil ? "Real: \(scoreText(it))" : "Not graded yet")
                    .font(Theme.font(12.5)).foregroundStyle(Theme.secondary)
            }
            Spacer()
            TextField(it.earned.map { Fmt.num($0) } ?? "—", text: Binding(get: { whatIf[it.id] ?? "" }, set: { whatIf[it.id] = $0; recompute() }))
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .font(Theme.mono(15, .semibold))
                .frame(width: 64)
                .padding(8)
                .background(changed ? Theme.accentSoft : Theme.tagSoft, in: RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(changed ? Theme.accent : Theme.border))
                .accessibilityLabel("What-if score for \(it.title), out of \(Fmt.num(it.possible))")
            Text("/ \(Fmt.num(it.possible))").font(Theme.mono(14)).foregroundStyle(Theme.secondary)
        }
        .padding(14)
    }

    private func recompute() {
        guard let rows = model.gradeRows(course.sectionId), let g = real else { return }
        var w: [String: [String: Double]] = [:]
        for (id, s) in whatIf {
            guard let v = Double(s), let it = g.items.first(where: { $0.id == id }), let p = it.possible else { continue }
            w[id] = ["earned": v, "possible": p]
        }
        Task { preview = try? await model.summarize(rows: rows, whatIf: w) }
    }

    // ── Plan an upcoming test (needFor) ──────────────────────────────────

    private var planner: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Plan an upcoming test").font(Theme.font(16, .semibold))
            Picker("Category", selection: $planCategory) {
                ForEach(real?.categories.map(\.title) ?? [], id: \.self) { Text($0).tag($0) }
            }
            .pickerStyle(.menu)
            HStack {
                Text("Worth")
                TextField("100", text: $planPoints).keyboardType(.decimalPad).font(Theme.mono(15)).frame(width: 70)
                    .padding(6).background(Theme.accentSoft, in: RoundedRectangle(cornerRadius: 8))
                Text("points")
                Spacer()
                PillButton(title: "Work it out", filled: true) {
                    Task {
                        plan = await model.plan(sectionId: course.sectionId, category: planCategory,
                                                possible: Double(planPoints) ?? 0, target: cutoff)
                    }
                }
            }
            if let plan {
                Text(planText(plan)).font(Theme.font(14, .medium))
                    .foregroundStyle(plan.status == "out" ? Theme.late : Theme.good)
            }
        }
        .padding(16)
        .card()
        .onAppear { if planCategory.isEmpty { planCategory = real?.categories.first(where: { $0.title.localizedCaseInsensitiveContains("test") })?.title ?? real?.categories.first?.title ?? "" } }
    }

    private func planText(_ p: PlanResult) -> String {
        let pts = Double(planPoints) ?? 0
        switch p.status {
        case "any": return "Even a zero keeps you at \(Fmt.num(cutoff))%."
        case "out": return "A full score still leaves you under \(Fmt.num(cutoff))%."
        case "need":
            let need = (p.need ?? 0).rounded(.up)
            return "You need \(Fmt.num(need)) / \(Fmt.num(pts)) (\(Fmt.pct(pts > 0 ? need / pts * 100 : nil, digits: 0))) to stay at \(Fmt.num(cutoff))%."
        default: return "Pick a category with grades."
        }
    }
}
