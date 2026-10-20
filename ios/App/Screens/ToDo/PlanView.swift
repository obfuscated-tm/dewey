import SwiftUI

/// To Do's two views: every item (List: by class or urgent first), or Plan,
/// the student's own order. Remembered between launches.
enum ToDoMode: String, CaseIterable {
    case list, plan
    static let key = "todoMode"
    var title: String { self == .list ? "List" : "Plan" }
}

/// To Do › Plan: all open work in three sections, To do, Later and Done
/// (PlanLogic), in the student's order; To do is numbered 1…N. Hold a row and
/// drag it, within a section or into another one (into Done marks it done).
/// Until it's moved, work sits where its due date puts it.
struct PlanContent: View {
    @Environment(AppModel.self) private var model
    let now: Date
    let onOpen: (ItemState) -> Void
    let onSubmit: (ItemState) -> Void
    let onSet: (ItemState, AppModel.Progress) -> Void
    let onNewTask: () -> Void

    @AppStorage("planHintClosed") private var hintClosed = false
    /// Bumped after a drop: the list's own rows are rebuilt from the plan. Left alone,
    /// the rows the list moved itself can stay out of step with the saved order
    /// (a heading drawn above work that is in To do).
    @State private var dropCount = 0

    var body: some View {
        let lists = model.planLists(now: now)
        let rows = lists.rows
        let byId = Dictionary((lists.todo + lists.later + lists.done).map { ($0.schoologyId, $0) }, uniquingKeysWith: { a, _ in a })
        let number = Dictionary(lists.todo.enumerated().map { ($1.schoologyId, $0 + 1) }, uniquingKeysWith: { a, _ in a })
        let counts: [PlanLogic.Bucket: Int] = [.todo: lists.todo.count, .later: lists.later.count, .done: lists.done.count]
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
                        heading(b, count: counts[b] ?? 0, first: b == .todo)
                    case .item(let id):
                        if let it = byId[id] { planRow(it, number: number[id], rows: rows) }
                    }
                }
                .onMove { from, to in
                    guard let i = from.first, case .item(let id) = rows[i] else { return }
                    model.applyPlan(PlanLogic.moved(rows, from: from, to: to), moved: id)
                    dropCount += 1
                    hintClosed = true
                }
            } header: {
                if !hintClosed { hint.textCase(nil) }
            }
            .id(dropCount)
        }
        Section {
            Button(action: onNewTask) { Label("New task", systemImage: "plus") }
        } footer: {
            Text("Until you move it, work goes where its due date puts it: late, due today or tomorrow, and tests in the next 3 days in To do; the rest in Later.")
        }
        .listRowBackground(Theme.card)
    }

    // ── Pieces ───────────────────────────────────────────────────────────

    /// A section's heading: a row like the others, so drags can cross it, but never dragged itself.
    private func heading(_ b: PlanLogic.Bucket, count: Int, first: Bool) -> some View {
        VStack(alignment: .leading, spacing: Theme.Space.xs) {
            HStack(alignment: .firstTextBaseline) {
                Text(b.title).font(Theme.headline).foregroundStyle(b == .done ? Theme.secondary : Theme.text)
                Spacer()
                Text(b == .done ? "\(count) today" : "\(count)").font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary)
            }
            if count == 0 {
                Text(b == .done ? "Drag work here when it's done." : "Drag work here.")
                    .font(Theme.footnote).foregroundStyle(Theme.tertiary)
            }
        }
        .padding(.bottom, Theme.Space.s)
        .overlay(alignment: .bottom) { (b == .todo ? Theme.catalogRule : Theme.catalogLine).frame(height: 1.5) }
        .listRowInsets(EdgeInsets(top: first ? Theme.Space.m : Theme.Space.xl, leading: Theme.Space.l, bottom: 0, trailing: Theme.Space.l))
        .listRowBackground(Theme.card)
        .listRowSeparator(.hidden)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
        .moveDisabled(true)
    }

    private var hint: some View {
        HStack(alignment: .top, spacing: Theme.Space.s) {
            Image(systemName: "hand.draw").foregroundStyle(Theme.accent).accessibilityHidden(true)
            Text("Put these in the order you'll do them: hold the dots (or anywhere on a row), then drag, even into Later or Done.")
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

    private func move(_ it: ItemState, _ placed: [String: (PlanLogic.Bucket, Double)]) {
        withAnimation(.snappy(duration: 0.2)) { model.applyPlan(placed, moved: it.schoologyId) }
        hintClosed = true
    }

    private func planRow(_ it: ItemState, number: Int?, rows: [PlanLogic.Row]) -> some View {
        let b = bucket(it.schoologyId, rows)
        return HStack(alignment: .top, spacing: Theme.Space.s) {
            // To do is numbered; every row has the grip under its number (or where it would be).
            VStack(spacing: Theme.Space.s) {
                Text(number.map(String.init) ?? " ")
                    .font(Theme.callNumber.monospacedDigit())
                    .foregroundStyle(number == 1 ? Theme.accent : Theme.secondary)
                Grip()
            }
            .frame(minWidth: 22)
            .padding(.top, Theme.Space.xs + 1)
            .accessibilityHidden(true)
            TodoRow(item: it, now: now, course: courseLine(it),
                    onSet: { onSet(it, $0) }, onSubmit: { onSubmit(it) }, onOpen: { onOpen(it) })
                .opacity(b == .done ? 0.6 : 1)
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(number.map { "Number \($0)" } ?? b.title)
        .alignmentGuide(.listRowSeparatorLeading) { d in d[.leading] }
        .listRowBackground(Theme.card)
        .listRowSeparatorTint(Theme.catalogLine)
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            if b == .todo {
                Button { move(it, PlanLogic.moved(rows, id: it.schoologyId, to: .later)) } label: { Label("Later", systemImage: "arrow.down.to.line") }
                    .tint(Theme.secondary)
            } else if b == .later {
                Button { move(it, PlanLogic.moved(rows, id: it.schoologyId, to: .todo)) } label: { Label("To do", systemImage: "arrow.up.to.line") }
                    .tint(Theme.accent)
            }
        }
        .swipeActions(edge: .leading) {
            if b != .done {
                Button { onSet(it, .done) } label: { Label(it.type == .exam ? "Studied" : "Done", systemImage: "checkmark") }
                    .tint(Theme.accent)
            }
        }
        // No hold menu here: holding a row starts the drag (a menu would take the hold instead).
        // VoiceOver: the same moves without a drag.
        .accessibilityAction(named: "Move up") { move(it, PlanLogic.nudged(rows, id: it.schoologyId, by: -1)) }
        .accessibilityAction(named: "Move down") { move(it, PlanLogic.nudged(rows, id: it.schoologyId, by: 1)) }
        .accessibilityAction(named: b == .later ? "Move to To do" : "Move to Later") {
            move(it, PlanLogic.moved(rows, id: it.schoologyId, to: b == .later ? .todo : .later))
        }
    }
}

/// The drag grip under a plan row's number: two columns of three dots.
private struct Grip: View {
    var body: some View {
        Grid(horizontalSpacing: 3, verticalSpacing: 3) {
            ForEach(0..<3, id: \.self) { _ in
                GridRow {
                    Circle().frame(width: 4, height: 4)
                    Circle().frame(width: 4, height: 4)
                }
            }
        }
        .foregroundStyle(Theme.tertiary.opacity(0.7))
        .frame(width: 22, height: 22)
        .contentShape(Rectangle())
    }
}
