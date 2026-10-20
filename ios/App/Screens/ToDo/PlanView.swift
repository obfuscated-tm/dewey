import SwiftUI

/// To Do's two views: every item (List: by class or urgent first), or Plan,
/// the student's own order. Remembered between launches.
enum ToDoMode: String, CaseIterable {
    case list, plan
    static let key = "todoMode"
    var title: String { self == .list ? "List" : "Plan" }
}

/// To Do › Plan: the work in front of the student in the order they'll do it
/// (PlanLogic), numbered 1…N. Hold and drag to reorder; swipe left for Not
/// today; done work drops into Done today. Sections of To Do's own list.
struct PlanContent: View {
    @Environment(AppModel.self) private var model
    let now: Date
    let onOpen: (ItemState) -> Void
    let onSubmit: (ItemState) -> Void
    let onSet: (ItemState, AppModel.Progress) -> Void
    let onNewTask: () -> Void
    /// The Due later sheet, shown by To Do (a sheet inside a list section never presents).
    let onAddLater: () -> Void

    @AppStorage("planHintClosed") private var hintClosed = false
    @AppStorage("planShowNotToday") private var showNotToday = true
    @AppStorage("planShowDone") private var showDone = false

    var body: some View {
        let lists = model.planLists(now: now)
        let ids = lists.next.map(\.schoologyId)
        if lists.next.isEmpty {
            Section {
                if lists.doneToday.isEmpty && lists.notToday.isEmpty {
                    EmptyState(icon: "list.number", title: "Nothing to plan",
                               detail: "No late work and nothing due this week. Add a task of your own with +.",
                               art: "DeweyCaughtUp")
                } else {
                    EmptyState(icon: "checkmark.circle", title: lists.notToday.isEmpty ? "All done for now" : "Nothing left for today",
                               detail: lists.notToday.isEmpty ? "Everything in your plan is done." : "What you moved to Not today comes back tomorrow.",
                               art: "DeweyCaughtUp")
                }
            }
            .listRowBackground(Color.clear).listRowInsets(EdgeInsets())
        } else {
            // Only the rows in this section: a heading or tip among them would take drops and
            // put the list's own order out of step with the numbers.
            Section {
                ForEach(Array(lists.next.enumerated()), id: \.element.schoologyId) { i, it in
                    planRow(it, number: i + 1, ids: ids)
                }
                .onMove { from, to in
                    withAnimation(.snappy(duration: 0.2)) { model.setPlanRanks(PlanLogic.moved(ids, from: from, to: to)) }
                    hintClosed = true
                }
            } header: {
                VStack(alignment: .leading, spacing: Theme.Space.s) {
                    heading(count: lists.next.count)
                    if !hintClosed { hint }
                }
                .textCase(nil)
            }
        }
        if !lists.notToday.isEmpty {
            Section {
                folder("Not today", count: lists.notToday.count, detail: "back tomorrow", open: $showNotToday)
                if showNotToday {
                    ForEach(lists.notToday, id: \.schoologyId) { it in sideRow(it, putBack: true) }
                }
            }
        }
        if !lists.doneToday.isEmpty {
            Section {
                folder("Done today", count: lists.doneToday.count, detail: nil, open: $showDone)
                if showDone {
                    ForEach(lists.doneToday, id: \.schoologyId) { it in sideRow(it, putBack: false) }
                }
            }
        }
        Section {
            Button(action: onNewTask) { Label("New task", systemImage: "plus") }
            if !lists.later.isEmpty {
                Button(action: onAddLater) {
                    LabeledContent { Text("\(lists.later.count)").monospacedDigit() } label: {
                        Label("Add work due later", systemImage: "calendar.badge.plus")
                    }
                }
            }
        } footer: {
            Text("Plan holds late work, everything due in the next 7 days, and your own tasks.")
        }
        .listRowBackground(Theme.card)
    }

    // ── Pieces ───────────────────────────────────────────────────────────

    private func heading(count: Int) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text("Up next").font(Theme.headline).foregroundStyle(Theme.text)
            Spacer()
            Text("\(count) to do").font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary)
        }
        .padding(.horizontal, Theme.Space.xs)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    private var hint: some View {
        HStack(alignment: .top, spacing: Theme.Space.s) {
            Image(systemName: "hand.draw").foregroundStyle(Theme.accent).accessibilityHidden(true)
            Text("Put these in the order you'll do them: hold a row, then drag it up or down. Swipe left for Not today.")
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

    private func planRow(_ it: ItemState, number: Int, ids: [String]) -> some View {
        HStack(alignment: .top, spacing: Theme.Space.s) {
            Text("\(number)")
                .font(Theme.callNumber.monospacedDigit())
                .foregroundStyle(number == 1 ? Theme.accent : Theme.secondary)
                .frame(minWidth: 22, alignment: .trailing)
                .padding(.top, Theme.Space.xs + 1)
                .accessibilityHidden(true)
            TodoRow(item: it, now: now, course: courseLine(it),
                    onSet: { onSet(it, $0) }, onSubmit: { onSubmit(it) }, onOpen: { onOpen(it) })
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Number \(number)")
        .alignmentGuide(.listRowSeparatorLeading) { d in d[.leading] }
        .listRowBackground(Theme.card)
        .listRowSeparatorTint(Theme.catalogLine)
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button { withAnimation { model.setNotToday(it, true) } } label: { Label("Not today", systemImage: "moon.zzz") }
                .tint(Theme.secondary)
            if it.planAdded && !it.mine {
                Button { withAnimation { model.setInPlan(it, false) } } label: { Label("Take out", systemImage: "minus.circle") }
                    .tint(Theme.tertiary)
            }
        }
        .swipeActions(edge: .leading) {
            Button { onSet(it, .done) } label: { Label(it.type == .exam ? "Studied" : "Done", systemImage: "checkmark") }
                .tint(Theme.accent)
        }
        // No hold menu here: holding a row starts the drag (a menu would take the hold instead).
        // VoiceOver: the moves without a drag.
        .accessibilityAction(named: "Move up") { if let i = ids.firstIndex(of: it.schoologyId), i > 0 { move(it, ids, i - 1) } }
        .accessibilityAction(named: "Move down") { if let i = ids.firstIndex(of: it.schoologyId), i < ids.count - 1 { move(it, ids, i + 1) } }
        .accessibilityAction(named: "Not today") { model.setNotToday(it, true) }
    }

    private func move(_ it: ItemState, _ ids: [String], _ to: Int) {
        withAnimation(.snappy(duration: 0.2)) { model.setPlanRanks(PlanLogic.moved(ids, id: it.schoologyId, toIndex: to)) }
        hintClosed = true
    }

    /// Not today and Done today: a short row (class · due), its circle, and Put back for a skipped one.
    private func sideRow(_ it: ItemState, putBack: Bool) -> some View {
        let progress = model.progress(it)
        return HStack(alignment: .center, spacing: Theme.Space.m) {
            Button { onSet(it, progress == .todo ? .done : .todo) } label: {
                CheckMark(item: it).frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(-10)
            .accessibilityLabel(progress == .todo ? "Not done" : (it.type == .exam ? "Studied" : "Done"))
            .accessibilityHint(progress == .todo ? "Marks it done" : "Marks it to do")
            VStack(alignment: .leading, spacing: 2) {
                Text(it.title).font(Theme.body).foregroundStyle(putBack ? Theme.text : Theme.secondary)
                    .strikethrough(!putBack && progress != .todo, color: Theme.tertiary)
                Text([courseLine(it), Fmt.due(it.due, now: now)].filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(Theme.footnote).foregroundStyle(Theme.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture { onOpen(it) }
            if putBack {
                PillButton(title: "Put back", color: Theme.accent) { withAnimation { model.setNotToday(it, false) } }
                    .accessibilityLabel("Put \(it.title) back in the plan")
            }
        }
        .padding(.vertical, Theme.Space.xs)
        .listRowBackground(Theme.card)
        .listRowSeparatorTint(Theme.catalogLine)
        .moveDisabled(true)
    }

    private func folder(_ title: String, count: Int, detail: String?, open: Binding<Bool>) -> some View {
        Button { withAnimation { open.wrappedValue.toggle() } } label: {
            HStack(spacing: Theme.Space.s) {
                Text("\(title) · \(count)").font(Theme.subhead.weight(.semibold)).foregroundStyle(Theme.text)
                if let detail { Text(detail).font(Theme.footnote).foregroundStyle(Theme.tertiary) }
                Spacer()
                Image(systemName: open.wrappedValue ? "chevron.up" : "chevron.down").foregroundStyle(Theme.secondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint(open.wrappedValue ? "Hides them" : "Shows them")
        .listRowBackground(Theme.card)
        .moveDisabled(true)
    }
}

/// Work due after the plan's 7 days (or with no date): add any of it to the plan.
struct AddLaterSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let now: Date

    var body: some View {
        NavigationStack {
            let later = model.planLists(now: now).later
            List {
                if later.isEmpty {
                    Text("Everything open is already in your plan.").foregroundStyle(Theme.secondary)
                }
                ForEach(later, id: \.schoologyId) { it in
                    HStack(spacing: Theme.Space.m) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(it.title).font(Theme.body).foregroundStyle(Theme.text)
                            Text([model.course(it.sectionId)?.shortTitle, Fmt.due(it.due, now: now)].compactMap { $0 }.joined(separator: " · "))
                                .font(Theme.footnote).foregroundStyle(Theme.secondary)
                        }
                        Spacer()
                        PillButton(title: "Add", color: Theme.accent, filled: true) { withAnimation { model.setInPlan(it, true) } }
                            .accessibilityLabel("Add \(it.title) to the plan")
                    }
                    .listRowBackground(Theme.card)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Due later")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}
