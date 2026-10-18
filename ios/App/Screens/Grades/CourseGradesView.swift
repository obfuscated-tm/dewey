import SwiftUI

/// 2b · Every item of the current period by category, facts first, with the
/// grade-over-time graph. What-if mode (off by default, cleared on leaving)
/// puts a score box on every row, lets the student drop a row or add a made-up
/// one per category, keeps real → what-if pinned at the top, and holds "Plan an
/// upcoming test".
struct CourseGradesView: View {
    @Environment(AppModel.self) private var model
    let course: Course
    @State private var whatIfOn = false
    @State private var whatIf: [String: String] = [:]
    /// "Out of" typed for ungraded rows whose points Schoology doesn't show.
    @State private var whatIfOutOf: [String: String] = [:]
    @State private var dropped: Set<String> = []
    @State private var extras: [Extra] = []
    @State private var preview: CourseGrade?
    @State private var previewGen = 0
    @State private var planCategory = ""
    @State private var planPoints = "100"
    @State private var plan: PlanResult?
    @State private var sheet: SheetTarget?
    @FocusState private var focused: String?
    /// Scores that were new when the screen opened; they keep their tag until it closes.
    @State private var newIds: Set<String> = []
    /// Categories opened (by title). Everything starts collapsed, like the extension; remembered per class.
    @AppStorage private var expandedRaw: String

    /// A made-up assignment, only in what-if.
    struct Extra: Identifiable, Hashable {
        let id = UUID()
        var category: String
        var title = ""
        var earned = ""
        var possible = "100"
    }

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
    private func possibleOf(_ it: CourseGrade.Item) -> Double? {
        it.possible ?? Double(whatIfOutOf[it.id] ?? "").flatMap { $0 > 0 ? $0 : nil }
    }
    private var changeCount: Int {
        (real?.items ?? []).filter { Double(whatIf[$0.id] ?? "") != nil && possibleOf($0) != nil }.count + dropped.count + extras.filter { Double($0.earned) != nil }.count
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if !whatIfOn { summary }
                if let g = real {
                    GradeHistoryChart(history: g.history, cutoff: cutoff,
                                      whatIfPct: whatIfOn && changeCount > 0 ? preview?.pct : nil)
                }
                if let g = shown {
                    if g.categories.count > 1 && !whatIfOn {
                        let allShut = g.categories.allSatisfy { !expanded.contains($0.title) }
                        HStack {
                            Text("Categories").font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.secondary)
                            Spacer()
                            Button(allShut ? "Expand all" : "Collapse all") {
                                withAnimation(.snappy(duration: 0.25)) {
                                    expandedRaw = allShut ? g.categories.map(\.title).joined(separator: "\n") : ""
                                }
                            }
                            .font(Theme.footnote.weight(.semibold))
                        }
                        .padding(.horizontal, 4)
                    }
                    ForEach(g.categories, id: \.title) { cat in categoryBlock(cat) }
                    if whatIfOn && !g.categories.isEmpty { planner }
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
        .scrollDismissesKeyboard(.interactively)
        .background(Theme.background)
        .safeAreaInset(edge: .top, spacing: 0) { if whatIfOn { whatIfBar } }
        .navigationTitle(course.shortTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button(whatIfOn ? "Done" : "What-if") { setWhatIf(!whatIfOn) }
                    .fontWeight(.semibold)
            }
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { focused = nil }.fontWeight(.semibold)
            }
        }
        .refreshable { await model.loadGrades(course.sectionId, force: true) }
        .task {
            await model.loadGrades(course.sectionId)
            // Opening the class counts as seeing its new scores.
            let fresh = (real?.items ?? []).map(\.id).filter(model.isNewGrade)
            newIds.formUnion(fresh)
            model.markGradesSeen(fresh)
        }
        .sheet(item: $sheet) { SchoologySheet(target: $0) }
        .onDisappear { clearWhatIf() }
    }

    private var gradesURL: URL { URL(string: "\(Engine.origin.absoluteString)/course/\(course.sectionId)/student_grades")! }

    private func setWhatIf(_ on: Bool) {
        focused = nil
        withAnimation(.snappy(duration: 0.25)) {
            whatIfOn = on
            if !on { clearWhatIf(); plan = nil }
        }
    }

    private func clearWhatIf() {
        whatIf = [:]; whatIfOutOf = [:]; dropped = []; extras = []; preview = nil
    }

    // ── Summary ──────────────────────────────────────────────────────────

    private var summary: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 4) {
                Text(course.shortTitle).font(Theme.title).foregroundStyle(Theme.text)
                Text([course.periodLabel, real?.period ?? "Current period"].filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.secondary)
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(real?.letter ?? "—").font(Theme.display)
                    Text(Fmt.pct(real?.pct, digits: 2)).font(Theme.title.weight(.medium).monospacedDigit())
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(real?.pct.map { "Grade \(real?.letter ?? ""), \(String(format: "%.2f", $0)) percent" } ?? "No grade yet")
                if let g = real {
                    Text(g.mode == "weighted" ? "Weighted by category" : "Total points").font(Theme.footnote).foregroundStyle(Theme.tertiary)
                }
                if !whatIfOn, real?.pct != nil {
                    PillButton(title: "Try what-if scores") { setWhatIf(true) }
                        .padding(.top, 4)
                }
            }
            Spacer()
            if let at = model.gradesAsOf[course.sectionId] {
                Text(Fmt.ago(at)).font(Theme.caption).foregroundStyle(Theme.tertiary)
            }
        }
        .padding(16)
        .card()
    }

    /// Pinned under the nav bar in what-if mode: real → what-if, always in view while typing.
    private var whatIfBar: some View {
        let r = real?.pct
        let p = changeCount > 0 ? preview?.pct : nil
        return HStack(alignment: .center, spacing: Theme.Space.m) {
            VStack(alignment: .leading, spacing: 1) {
                Text("WHAT-IF").font(Theme.caption.weight(.bold)).foregroundStyle(Theme.accent)
                Text(changeCount == 0 ? "Type a score on any row" : "\(changeCount) change\(changeCount == 1 ? "" : "s") · preview only")
                    .font(Theme.caption).foregroundStyle(Theme.secondary).oneLine()
            }
            Spacer(minLength: 0)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                if p != nil {
                    Text("\(real?.letter ?? "") \(Fmt.pct(r))").font(Theme.subhead.monospacedDigit()).foregroundStyle(Theme.secondary)
                    Image(systemName: "arrow.right").font(Theme.caption.weight(.bold)).foregroundStyle(Theme.tertiary)
                }
                VStack(alignment: .trailing, spacing: 0) {
                    Text("\(p == nil ? (real?.letter ?? "") : (preview?.letter ?? "")) \(Fmt.pct(p ?? r))")
                        .font(Theme.headline.monospacedDigit()).foregroundStyle(Theme.text)
                    if let p, let r {
                        Text(Fmt.signed(p - r)).font(Theme.caption.weight(.semibold).monospacedDigit())
                            .foregroundStyle(p < r - 0.005 ? Theme.late : Theme.good)
                    }
                }
            }
            if changeCount > 0 {
                Button { withAnimation(.snappy(duration: 0.2)) { clearWhatIf() } } label: {
                    Image(systemName: "arrow.counterclockwise").fontWeight(.semibold)
                }
                .accessibilityLabel("Reset all what-if scores")
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .background(.bar)
        .overlay(alignment: .bottom) { Theme.divider.frame(height: 1) }
        .accessibilityElement(children: .combine)
    }

    // ── A category and its items ─────────────────────────────────────────

    private func categoryBlock(_ cat: CourseGrade.Category) -> some View {
        let items = (real?.items ?? []).filter { $0.category == cat.title }
        let open = expanded.contains(cat.title) || whatIfOn
        let realCat = real?.categories.first { $0.title == cat.title }
        let moved = whatIfOn && changeCount > 0 && abs((cat.pct ?? -1) - (realCat?.pct ?? -1)) >= 0.005
        return VStack(alignment: .leading, spacing: 0) {
            Button { toggle(cat.title) } label: {
                HStack(alignment: .firstTextBaseline) {
                    if !whatIfOn {
                        Image(systemName: "chevron.right")
                            .font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.secondary)
                            .rotationEffect(.degrees(open ? 90 : 0))
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text(cat.title).font(Theme.headline).foregroundStyle(Theme.text)
                            let n = items.filter { newIds.contains($0.id) }.count
                            if n > 0 && !open {
                                Text("\(n) new").font(Theme.caption.weight(.bold)).foregroundStyle(Theme.onAccent)
                                    .padding(.horizontal, 6).padding(.vertical, 1).background(Theme.accent, in: Capsule())
                            }
                        }
                        Text(open ? floorText(cat) : "\(items.count) item\(items.count == 1 ? "" : "s")\(floorText(cat).isEmpty ? "" : " · " + floorText(cat))")
                            .font(Theme.footnote).foregroundStyle(cat.floorStatus == "out" ? Theme.late : Theme.secondary)
                            .multilineTextAlignment(.leading)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        HStack(spacing: 4) {
                            if moved {
                                Text(Fmt.pct(realCat?.pct)).font(Theme.caption.monospacedDigit()).foregroundStyle(Theme.tertiary).strikethrough()
                            }
                            Text(Fmt.pct(cat.pct)).font(Theme.subhead.weight(.semibold).monospacedDigit())
                                .foregroundStyle(moved ? Theme.accent : Theme.text)
                        }
                        if let w = cat.weight { Text("\(Fmt.num(w))% of grade").font(Theme.caption).foregroundStyle(Theme.tertiary) }
                    }
                }
                .padding(14)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .allowsHitTesting(!whatIfOn)
            .accessibilityElement(children: .combine)
            .accessibilityValue(whatIfOn ? "" : open ? "Expanded" : "Collapsed")
            .accessibilityHint(whatIfOn ? "" : open ? "Collapses this category" : "Shows this category's items")
            if open {
                ForEach(items) { it in
                    Theme.divider.frame(height: 1)
                    if whatIfOn {
                        whatIfRow(it)
                    } else {
                        NavigationLink { GradeDetailView(sectionId: course.sectionId, itemId: it.id) } label: { itemRow(it) }
                            .buttonStyle(.plain)
                    }
                }
                if whatIfOn {
                    ForEach(extras.filter { $0.category == cat.title }) { x in
                        Theme.divider.frame(height: 1)
                        extraRow(x)
                    }
                    Theme.divider.frame(height: 1)
                    Button {
                        let x = Extra(category: cat.title)
                        withAnimation(.snappy(duration: 0.2)) { extras.append(x) }
                        focused = "extra-\(x.id)"
                    } label: {
                        Label("Add an assignment", systemImage: "plus.circle.fill")
                            .font(Theme.subhead.weight(.semibold)).foregroundStyle(Theme.accent)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(14).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
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
                HStack(spacing: 6) {
                    Text(it.title).font(Theme.subhead.weight(.medium)).foregroundStyle(Theme.text).maxLines(2)
                    if newIds.contains(it.id) { NewTag() }
                }
                HStack(spacing: 6) {
                    if let d = Fmt.wallDate(it.due) { Text(Fmt.short.string(from: d)).font(Theme.caption.monospacedDigit()).foregroundStyle(Theme.tertiary) }
                    if !it.comment.isEmpty { Label("Comment", systemImage: "text.bubble").font(Theme.caption).foregroundStyle(Theme.accent) }
                    if it.rubric != nil { Label("Rubric", systemImage: "tablecells").font(Theme.caption).foregroundStyle(Theme.accent) }
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                Text(scoreText(it)).font(Theme.subhead.weight(.semibold).monospacedDigit()).foregroundStyle(it.earned == nil ? Theme.tertiary : Theme.text)
                HStack(spacing: 6) {
                    if let p = it.pct { Text(Fmt.pct(p, digits: 0)).font(Theme.caption.monospacedDigit()).foregroundStyle(Theme.secondary) }
                    if let i = it.impact, abs(i) >= 0.005 { Text(Fmt.signed(i)).font(Theme.caption.monospacedDigit()).foregroundStyle(i < 0 ? Theme.late : Theme.good) }
                }
            }
        }
        .padding(14)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    // ── What-if rows ─────────────────────────────────────────────────────

    /// A real item: a score box (blank = keep the real score), a menu of quick
    /// scores, and Drop. A changed row gets an accent edge and shows real → new.
    private func whatIfRow(_ it: CourseGrade.Item) -> some View {
        let possible = possibleOf(it) ?? 0
        let typed = Double(whatIf[it.id] ?? "")
        let isDropped = dropped.contains(it.id)
        let changed = typed != nil || isDropped
        return HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text(it.title).font(Theme.subhead.weight(.medium))
                    .foregroundStyle(isDropped ? Theme.tertiary : Theme.text).strikethrough(isDropped).maxLines(2)
                HStack(spacing: 4) {
                    if isDropped {
                        Text("Left out of the grade").foregroundStyle(Theme.secondary)
                    } else if let t = typed {
                        Text(it.earned != nil ? scoreText(it) : "Not graded").strikethrough(it.earned != nil).foregroundStyle(Theme.tertiary)
                        Image(systemName: "arrow.right").imageScale(.small).foregroundStyle(Theme.tertiary)
                        Text(possible > 0 ? Fmt.pct(t / possible * 100, digits: 0) : "—").fontWeight(.semibold)
                            .foregroundStyle(possible > 0 && t / possible * 100 >= cutoff ? Theme.good : Theme.text)
                    } else {
                        Text(it.earned != nil ? "Real \(scoreText(it))" + (it.pct.map { " · \(Fmt.pct($0, digits: 0))" } ?? "") : "Not graded yet" + (Fmt.wallDate(it.due).map { " · due \(Fmt.short.string(from: $0))" } ?? ""))
                            .foregroundStyle(Theme.secondary)
                    }
                }
                .font(Theme.footnote.monospacedDigit())
            }
            Spacer(minLength: 4)
            if isDropped {
                PillButton(title: "Count it") { dropped.remove(it.id); recompute() }
            } else {
                scoreBox(id: it.id, placeholder: it.earned.map { Fmt.num($0) } ?? "—", changed: typed != nil,
                         text: Binding(get: { whatIf[it.id] ?? "" }, set: { whatIf[it.id] = $0; recompute() }),
                         label: "What-if score for \(it.title), out of \(Fmt.num(possible))")
                if it.possible != nil {
                    Text("/ \(Fmt.num(possible))").font(Theme.subhead.monospacedDigit()).foregroundStyle(Theme.secondary)
                } else {
                    // Schoology doesn't show the points of ungraded work: the student types them.
                    Text("/").font(Theme.subhead).foregroundStyle(Theme.secondary)
                    scoreBox(id: "outOf-\(it.id)", placeholder: "pts", changed: false,
                             text: Binding(get: { whatIfOutOf[it.id] ?? "" }, set: { whatIfOutOf[it.id] = $0; recompute() }),
                             label: "Points \(it.title) is out of")
                }
            }
            if possible > 0 || isDropped { Menu {
                ForEach([100.0, 90, 80, 70], id: \.self) { pc in
                    Button("\(Int(pc))%  ·  \(Fmt.num((possible * pc / 100 * 100).rounded() / 100)) / \(Fmt.num(possible))") {
                        dropped.remove(it.id)
                        whatIf[it.id] = Fmt.num((possible * pc / 100 * 100).rounded() / 100)
                        recompute()
                    }
                }
                Divider()
                if !isDropped {
                    Button("Leave out of grade", systemImage: "minus.circle") { dropped.insert(it.id); whatIf[it.id] = nil; recompute() }
                }
                if changed {
                    Button("Back to real score", systemImage: "arrow.counterclockwise") { dropped.remove(it.id); whatIf[it.id] = nil; recompute() }
                }
            } label: {
                Image(systemName: "ellipsis.circle").font(.title3).foregroundStyle(Theme.accent)
                    .frame(width: 32, height: 32).contentShape(Rectangle())
            }
            .accessibilityLabel("More what-if options for \(it.title)") }
        }
        .padding(14)
        .background(changed ? Theme.accentSoft : .clear)
        .overlay(alignment: .leading) { if changed { Theme.accent.frame(width: 3) } }
    }

    /// One field of a made-up assignment, found by id: a text field still
    /// ending its edit after the row is gone reads "" instead of crashing.
    private func extraField(_ id: UUID, _ key: WritableKeyPath<Extra, String>, recomputes: Bool = true) -> Binding<String> {
        Binding(get: { extras.first { $0.id == id }?[keyPath: key] ?? "" },
                set: { v in
                    guard let i = extras.firstIndex(where: { $0.id == id }) else { return }
                    extras[i][keyPath: key] = v
                    if recomputes { recompute() }
                })
    }

    /// A made-up assignment: name, score / out of, delete.
    private func extraRow(_ x: Extra) -> some View {
        let e = Double(x.earned), p = Double(x.possible)
        return HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                TextField("New assignment", text: extraField(x.id, \.title, recomputes: false))
                    .font(Theme.subhead.weight(.medium))
                Text(e != nil && (p ?? 0) > 0 ? "Made up · \(Fmt.pct(e! / p! * 100, digits: 0))" : "Made up · type a score")
                    .font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary)
            }
            Spacer(minLength: 4)
            scoreBox(id: "extra-\(x.id)", placeholder: "—", changed: e != nil,
                     text: extraField(x.id, \.earned),
                     label: "Score for the made-up assignment")
            Text("/").font(Theme.subhead).foregroundStyle(Theme.secondary)
            scoreBox(id: "extraP-\(x.id)", placeholder: "100", changed: false,
                     text: extraField(x.id, \.possible),
                     label: "Points the made-up assignment is out of")
            Button {
                let id = x.id
                focused = nil
                withAnimation(.snappy(duration: 0.2)) { extras.removeAll { $0.id == id } }
                recompute()
            } label: {
                Image(systemName: "trash").foregroundStyle(Theme.late).frame(width: 32, height: 32).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remove the made-up assignment")
        }
        .padding(14)
        .background(Theme.accentSoft)
        .overlay(alignment: .leading) { Theme.accent.frame(width: 3) }
    }

    private func scoreBox(id: String, placeholder: String, changed: Bool, text: Binding<String>, label: String) -> some View {
        TextField(placeholder, text: text)
            .keyboardType(.decimalPad)
            .focused($focused, equals: id)
            .multilineTextAlignment(.center)
            .font(Theme.body.weight(.semibold).monospacedDigit())
            .frame(width: 58)
            .padding(.vertical, 8).padding(.horizontal, 4)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous)
                .stroke(focused == id || changed ? Theme.accent : Theme.border, lineWidth: focused == id ? 2 : 1))
            .accessibilityLabel(label)
    }

    private func recompute() {
        guard let rows = model.gradeRows(course.sectionId), let g = real else { return }
        var w: [String: [String: Double]] = [:]
        for (id, s) in whatIf {
            guard let v = Double(s), let it = g.items.first(where: { $0.id == id }), let p = possibleOf(it) else { continue }
            w[id] = ["earned": v, "possible": p]
        }
        let extra: [[String: Any]] = extras.compactMap { x in
            guard let e = Double(x.earned), let p = Double(x.possible), p > 0 else { return nil }
            return ["category": x.category, "earned": e, "possible": p]
        }
        previewGen += 1
        let gen = previewGen
        let drop = Array(dropped)
        Task {
            let r = try? await model.summarize(rows: rows, whatIf: w, extra: extra, drop: drop)
            if gen == previewGen { preview = r }
        }
    }

    // ── Plan an upcoming test (needFor) ──────────────────────────────────

    private var planner: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Plan an upcoming test").font(Theme.headline)
            Picker("Category", selection: $planCategory) {
                ForEach(real?.categories.map(\.title) ?? [], id: \.self) { Text($0).tag($0) }
            }
            .pickerStyle(.menu)
            HStack {
                Text("Worth")
                TextField("100", text: $planPoints).keyboardType(.decimalPad).font(Theme.subhead.monospacedDigit()).frame(width: 70)
                    .padding(6).background(Theme.accentSoft, in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
                Text("points")
                Spacer()
                PillButton(title: "Work it out", filled: true) {
                    focused = nil
                    Task {
                        plan = await model.plan(sectionId: course.sectionId, category: planCategory,
                                                possible: Double(planPoints) ?? 0, target: cutoff)
                    }
                }
            }
            if let plan {
                Text(planText(plan)).font(Theme.subhead.weight(.medium))
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
