import SwiftUI

/// To Do's two views: every item (List: by class or urgent first), or Plan,
/// the student's own order. Remembered between launches.
enum ToDoMode: String, CaseIterable {
    case list, plan
    static let key = "todoMode"
    var title: String { self == .list ? "List" : "Plan" }
}

/// To Do › Plan: all open work in three sections, To do, Later and Done
/// (PlanLogic), in the student's order; To do is numbered 1…N. Drag a row by
/// its grip (at once) or by holding it anywhere else, within a section or into
/// another one (into Done marks it done). Later and Done fold away with a tap
/// on their heading and still take drops. Until it's moved, work sits where its
/// due date puts it.
struct PlanContent: View {
    @Environment(AppModel.self) private var model
    let now: Date
    let onOpen: (ItemState) -> Void
    let onSubmit: (ItemState) -> Void
    let onSet: (ItemState, AppModel.Progress) -> Void
    let onNewTask: () -> Void

    @AppStorage("planHintClosed") private var hintClosed = false
    /// Folded sections, e.g. "later,done". Done starts folded: it only grows through the day.
    @AppStorage("planCollapsed") private var collapsedRaw = "done"
    /// Bumped after a drop: the list's own rows are rebuilt from the plan. Left alone,
    /// the rows the list moved itself can stay out of step with the saved order
    /// (a heading drawn above work that is in To do).
    @State private var dropCount = 0
    /// A drag by the grip, while the finger is down: the rows as they're being reordered.
    @State private var grip: GripDrag?
    /// Where each row is on screen, so the grip knows when the finger has passed a neighbour's middle.
    @State private var frames: [PlanLogic.Row: CGRect] = [:]

    struct GripDrag {
        var id: String
        var start: [PlanLogic.Row]
        var rows: [PlanLogic.Row]
    }

    private var collapsed: Set<PlanLogic.Bucket> {
        Set(collapsedRaw.split(separator: ",").compactMap { PlanLogic.Bucket(rawValue: String($0)) })
    }

    private func toggle(_ b: PlanLogic.Bucket) {
        var c = collapsed
        if c.contains(b) { c.remove(b) } else { c.insert(b) }
        withAnimation(.snappy(duration: 0.2)) {
            collapsedRaw = PlanLogic.Bucket.allCases.filter(c.contains).map(\.rawValue).joined(separator: ",")
        }
    }

    var body: some View {
        let lists = model.planLists(now: now)
        let folded = collapsed
        let all: [PlanLogic.Bucket: [ItemState]] = [.todo: lists.todo, .later: lists.later, .done: lists.done]
        let shown = { (b: PlanLogic.Bucket) -> [String] in folded.contains(b) ? [] : (all[b] ?? []).map(\.schoologyId) }
        let base = PlanLogic.rows(todo: shown(.todo), later: shown(.later), done: shown(.done))
        let hidden: PlanLogic.Hidden = Dictionary(uniqueKeysWithValues: folded.map { ($0, (all[$0] ?? []).map(\.schoologyId)) })
        let rows = grip?.rows ?? base
        let byId = Dictionary((lists.todo + lists.later + lists.done).map { ($0.schoologyId, $0) }, uniquingKeysWith: { a, _ in a })
        let number = Self.numbers(rows)
        if lists.todo.isEmpty && lists.later.isEmpty && lists.done.isEmpty {
            Section {
                EmptyState(icon: "list.number", title: "Nothing to plan",
                           detail: "Nothing open on Schoology right now. Add a task of your own with +.", art: "DeweyCaughtUp")
            }
            .listRowBackground(Color.clear).listRowInsets(EdgeInsets())
        } else {
            // One list, headings included: dragging a row past a heading puts it in that section.
            Section {
                ForEach(rows, id: \.self) { row in
                    switch row {
                    case .header(let b):
                        heading(b, count: all[b]?.count ?? 0, first: b == .todo, folded: folded.contains(b))
                    case .item(let id):
                        if let it = byId[id] { planRow(it, number: number[id], rows: rows, hidden: hidden) }
                    }
                }
                .onMove { from, to in
                    guard grip == nil, let i = from.first, case .item(let id) = rows[i] else { return }
                    model.applyPlan(PlanLogic.moved(rows, from: from, to: to, hidden: hidden), moved: id)
                    dropCount += 1
                    hintClosed = true
                }
            } header: {
                if !hintClosed { hint.textCase(nil) }
            }
            .id(dropCount)
            .sensoryFeedback(.selection, trigger: grip?.rows)
            .sensoryFeedback(.impact(weight: .light), trigger: grip?.id) { _, new in new != nil }
        }
        Section {
            Button(action: onNewTask) { Label("New task", systemImage: "plus") }
        } footer: {
            Text("Until you move it, work goes where its due date puts it: late, due today or tomorrow, and tests in the next 3 days in To do; the rest in Later.")
        }
        .listRowBackground(Theme.card)
    }

    /// To do's numbers, 1… down to the Later heading.
    private static func numbers(_ rows: [PlanLogic.Row]) -> [String: Int] {
        var out: [String: Int] = [:]
        for r in rows.dropFirst() {
            guard case .item(let id) = r else { break }
            out[id] = out.count + 1
        }
        return out
    }

    // ── Pieces ───────────────────────────────────────────────────────────

    /// A section's heading: a row like the others, so drags can cross it, but never dragged
    /// itself. Later and Done fold with a tap; a folded one still takes drops.
    private func heading(_ b: PlanLogic.Bucket, count: Int, first: Bool, folded: Bool) -> some View {
        let folds = b != .todo
        return VStack(alignment: .leading, spacing: Theme.Space.xs) {
            HStack(alignment: .firstTextBaseline) {
                Text(b.title).font(Theme.headline).foregroundStyle(b == .done ? Theme.secondary : Theme.text)
                Spacer()
                Text(b == .done ? "\(count) today" : "\(count)").font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary)
                if folds {
                    Image(systemName: "chevron.down")
                        .font(Theme.footnote.weight(.semibold))
                        .foregroundStyle(Theme.secondary)
                        .rotationEffect(.degrees(folded ? -90 : 0))
                }
            }
            if count == 0 {
                Text(b == .done ? "Drag work here when it's done." : "Drag work here.")
                    .font(Theme.footnote).foregroundStyle(Theme.tertiary)
            } else if folded {
                Text("Folded. Tap to show, or drag work here.")
                    .font(Theme.footnote).foregroundStyle(Theme.tertiary)
            }
        }
        .padding(.bottom, Theme.Space.s)
        .overlay(alignment: .bottom) { (b == .todo ? Theme.catalogRule : Theme.catalogLine).frame(height: 1.5) }
        .contentShape(Rectangle())
        .onTapGesture { if folds { toggle(b) } }
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { frames[.header(b)] = $0 }
        .listRowInsets(EdgeInsets(top: first ? Theme.Space.m : Theme.Space.xl, leading: Theme.Space.l, bottom: 0, trailing: Theme.Space.l))
        .listRowBackground(Theme.card)
        .listRowSeparator(.hidden)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(folds ? [.isHeader, .isButton] : .isHeader)
        .accessibilityValue(folds ? (folded ? "Folded" : "Shown") : "")
        .accessibilityAction { if folds { toggle(b) } }
        .moveDisabled(true)
    }

    private var hint: some View {
        HStack(alignment: .top, spacing: Theme.Space.s) {
            Image(systemName: "hand.draw").foregroundStyle(Theme.accent).accessibilityHidden(true)
            Text("Put these in the order you'll do them: drag a row by its dots, even into Later or Done.")
                .font(Theme.footnote).foregroundStyle(Theme.text)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button { withAnimation { hintClosed = true } } label: {
                Image(systemName: "xmark").font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.secondary)
                    .frame(width: 30, height: 30).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close tip")
        }
        .padding(Theme.Space.m)
        .background(Theme.accentSoft, in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
    }

    private func courseLine(_ it: ItemState) -> String {
        if let c = model.course(it.sectionId) { return c.shortTitle }
        return it.mine ? "My task" : (it.courseName.map(Course.short) ?? "")
    }

    private func bucket(_ id: String, _ rows: [PlanLogic.Row]) -> PlanLogic.Bucket {
        var b = PlanLogic.Bucket.todo
        for r in rows {
            if case .header(let h) = r { b = h }
            if r == .item(id) { return b }
        }
        return b
    }

    /// Finishing something here keeps it in its row (ticked, dimmed) until Plan closes,
    /// so it doesn't drop into Done at the bottom, out of reach (AppModel.planKeep).
    private func hold(_ it: ItemState, _ b: PlanLogic.Bucket) {
        if b != .done { model.planKeep.insert(it.schoologyId) }
    }

    private func move(_ it: ItemState, _ placed: [String: (PlanLogic.Bucket, Double)]) {
        withAnimation(.snappy(duration: 0.2)) { model.applyPlan(placed, moved: it.schoologyId) }
        hintClosed = true
    }

    /// The grip: the row follows the finger from the first touch (no hold), swapping
    /// with each row once the finger is past that row's middle. Saved when the finger lifts.
    private func gripDrag(_ id: String, rows: [PlanLogic.Row], hidden: PlanLogic.Hidden) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .global)
            .onChanged { v in
                var g = grip ?? GripDrag(id: id, start: rows, rows: rows)
                guard g.id == id else { return }
                let y = v.location.y
                while let i = g.rows.firstIndex(of: .item(id)) {
                    if i + 1 < g.rows.count, let f = frames[g.rows[i + 1]], y > f.midY {
                        g.rows = PlanLogic.nudging(g.rows, id: id, by: 1)
                    } else if i - 1 >= 1, let f = frames[g.rows[i - 1]], y < f.midY {
                        g.rows = PlanLogic.nudging(g.rows, id: id, by: -1)
                    } else { break }
                }
                if g.rows != grip?.rows, grip != nil {
                    withAnimation(.snappy(duration: 0.18)) { grip = g }
                } else {
                    grip = g
                }
            }
            .onEnded { _ in
                guard let g = grip, g.id == id else { return }
                grip = nil
                guard g.rows != g.start else { return }
                model.applyPlan(PlanLogic.place(PlanLogic.expand(g.rows, hidden)), moved: id)
                dropCount += 1
                hintClosed = true
            }
    }

    private func planRow(_ it: ItemState, number: Int?, rows: [PlanLogic.Row], hidden: PlanLogic.Hidden) -> some View {
        let b = bucket(it.schoologyId, rows)
        let lifted = grip?.id == it.schoologyId
        return HStack(alignment: .top, spacing: Theme.Space.s) {
            // To do is numbered; every row has the grip under its number (or where it would be).
            VStack(spacing: Theme.Space.s) {
                Text(number.map(String.init) ?? " ")
                    .font(Theme.callNumber.monospacedDigit())
                    .foregroundStyle(number == 1 ? Theme.accent : Theme.secondary)
                Grip(active: lifted)
                    .gesture(gripDrag(it.schoologyId, rows: rows, hidden: hidden))
            }
            .frame(minWidth: 22)
            .padding(.top, Theme.Space.xs + 1)
            .accessibilityHidden(true)
            TodoRow(item: it, now: now, course: courseLine(it),
                    onSet: { hold(it, b); onSet(it, $0) }, onSubmit: { hold(it, b); onSubmit(it) }, onOpen: { onOpen(it) })
                .opacity(b == .done || model.planFinished(it) ? 0.6 : 1)
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(number.map { "Number \($0)" } ?? b.title)
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { frames[.item(it.schoologyId)] = $0 }
        .alignmentGuide(.listRowSeparatorLeading) { d in d[.leading] }
        .listRowBackground(lifted ? Theme.accentSoft : Theme.card)
        .listRowSeparatorTint(Theme.catalogLine)
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            if b == .todo {
                Button { move(it, PlanLogic.moved(rows, id: it.schoologyId, to: .later, hidden: hidden)) } label: { Label("Later", systemImage: "arrow.down.to.line") }
                    .tint(Theme.secondary)
            } else if b == .later {
                Button { move(it, PlanLogic.moved(rows, id: it.schoologyId, to: .todo, hidden: hidden)) } label: { Label("To do", systemImage: "arrow.up.to.line") }
                    .tint(Theme.accent)
            }
        }
        .swipeActions(edge: .leading) {
            if b != .done {
                Button { hold(it, b); onSet(it, .done) } label: { Label(it.type == .exam ? "Studied" : "Done", systemImage: "checkmark") }
                    .tint(Theme.accent)
            }
        }
        // No hold menu here: holding a row starts the drag (a menu would take the hold instead).
        // VoiceOver: the same moves without a drag.
        .accessibilityAction(named: "Move up") { move(it, PlanLogic.nudged(rows, id: it.schoologyId, by: -1, hidden: hidden)) }
        .accessibilityAction(named: "Move down") { move(it, PlanLogic.nudged(rows, id: it.schoologyId, by: 1, hidden: hidden)) }
        .accessibilityAction(named: b == .later ? "Move to To do" : "Move to Later") {
            move(it, PlanLogic.moved(rows, id: it.schoologyId, to: b == .later ? .todo : .later, hidden: hidden))
        }
    }
}

/// The drag grip under a plan row's number: two columns of three dots.
private struct Grip: View {
    var active = false
    var body: some View {
        Grid(horizontalSpacing: 3, verticalSpacing: 3) {
            ForEach(0..<3, id: \.self) { _ in
                GridRow {
                    Circle().frame(width: 4, height: 4)
                    Circle().frame(width: 4, height: 4)
                }
            }
        }
        .foregroundStyle(active ? Theme.accent : Theme.tertiary.opacity(0.7))
        .frame(width: 32, height: 32)
        .contentShape(Rectangle())
    }
}
