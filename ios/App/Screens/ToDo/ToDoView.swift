import SwiftUI

/// Settings → To Do (also the header's view menu): by class under the week strip
/// (the default), or one list with the most urgent first. Remembered between launches.
enum ToDoLayout: String, CaseIterable {
    case week, urgent
    static let key = "todoLayout"
    var title: String { self == .week ? "Week" : "Urgent first" }
    var icon: String { self == .week ? "calendar" : "flag" }
}

/// 1a · To Do: everything due, grouped by class in Course Dashboard order,
/// or (Urgent first) by how soon it's due.
struct ToDoView: View {
    @Environment(AppModel.self) private var model
    @AppStorage(ToDoLayout.key) private var layoutID = ToDoLayout.week.rawValue
    @State private var filter: DayFilter? = Demo.startDay
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var showOlder = false
    @State private var sheet: SheetTarget?
    @State private var showSettings = false
    @State private var path: [AssignmentRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            let items = model.todoItems()
            let now = Date()
            List {
                Section {
                    header(items: items, now: now)
                        .listRowInsets(EdgeInsets(top: Theme.Space.s, leading: 0, bottom: Theme.Space.m, trailing: 0))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
                content(items: items, now: now)
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .refreshable { await model.refreshTodo() }
            .navigationDestination(for: AssignmentRoute.self) { r in AssignmentView(id: r.id) }
            .toolbar(.hidden, for: .navigationBar)
            .sheet(item: $sheet) { SchoologySheet(target: $0) }
            .sheet(isPresented: $showSettings) { SettingsView() }
        }
        .onChange(of: model.deepLinkAssignment, initial: true) { _, id in
            guard let id else { return }
            path = [AssignmentRoute(id: id)]
            model.deepLinkAssignment = nil
        }
        // The day filter belongs to the week strip.
        .onChange(of: layoutID) { filter = nil }
    }

    private var layout: ToDoLayout { ToDoLayout(rawValue: layoutID) ?? .week }

    // ── Header: date, title, gear, week strip ───────────────────

    @ViewBuilder
    private func header(items: [ItemState], now: Date) -> some View {
        let open = items.filter { !model.isFinished($0) }
        let today = items.filter { TodoLogic.bucket(due: $0.due, now: now) == .today }
        let ripple = today.isEmpty ? nil : RippleProgress(done: today.filter { model.isFinished($0) }.count, total: today.count)
        VStack(alignment: .leading, spacing: Theme.Space.l) {
            ScreenHeader(kicker: Fmt.header.string(from: now), title: "To Do") {
                if !typeSize.isAccessibilitySize, let ripple { ripple.padding(.bottom, Theme.Space.xs) }
                Menu {
                    Picker("View", selection: $layoutID) {
                        ForEach(ToDoLayout.allCases, id: \.self) { Label($0.title, systemImage: $0.icon).tag($0.rawValue) }
                    }
                } label: {
                    Image(systemName: layout.icon).font(Theme.title.weight(.regular)).foregroundStyle(Theme.secondary)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("View: \(layout.title)")
                Button { showSettings = true } label: {
                    Image(systemName: "gearshape").font(Theme.title.weight(.regular)).foregroundStyle(Theme.secondary)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Settings")
            }
            if typeSize.isAccessibilitySize, let ripple { ripple.padding(.horizontal, Theme.Space.xs) }
            // Late, today and the next six days, a dot per item still to do; tap one to show only it.
            if layout == .week { WeekStrip(items: open, now: now, selected: $filter) }
            if let f = filter {
                HStack(spacing: Theme.Space.s) {
                    Text(filterTitle(f, now: now)).font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.secondary)
                    Spacer()
                    Button("Show all") { withAnimation(.snappy(duration: 0.2)) { filter = nil } }
                        .font(Theme.footnote.weight(.semibold))
                }
                .padding(.horizontal, 4)
            }
            // Offline note / refresh spinner: only takes room when there's something to show.
            if (model.todoAsOf != nil && model.todoError != nil) || model.refreshingTodo {
                HStack(spacing: 8) {
                    AsOfBanner(date: model.todoAsOf, error: model.todoError)
                    if model.refreshingTodo { ProgressView().controlSize(.small) }
                }
            }
        }
    }

    private func filterTitle(_ f: DayFilter, now: Date) -> String {
        switch f {
        case .late: return "Late"
        case .day(0): return "Today"
        case .day(1): return "Tomorrow"
        case .day(let k):
            let d = Calendar.current.date(byAdding: .day, value: k, to: Calendar.current.startOfDay(for: now))!
            return Fmt.header.string(from: d)
        }
    }

    // ── The groups ───────────────────────────────────────────────────────

    @ViewBuilder
    private func content(items: [ItemState], now: Date) -> some View {
        if model.todoAsOf == nil && items.isEmpty {
            if let e = model.todoError {
                Section { ErrorState(error: e, retry: { Task { await model.refreshTodo() } }) }
                    .listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            } else {
                Section { SkeletonRows() }.listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
        } else {
            let filtered = items.filter { it in
                guard let filter else { return true }
                return !model.isFinished(it) && filter.matches(it, now: now)
            }
            let older = filtered.filter { TodoLogic.isOlder(due: $0.due, now: now) && !model.isFinished($0) }
            let current = filtered.filter { !older.contains($0) }
            let groups = layout == .urgent ? urgentGroups(current, now: now) : grouped(current)
            if groups.isEmpty && older.isEmpty {
                Section {
                    EmptyState(icon: "checkmark.circle", title: filter == nil ? "Nothing due" : "Nothing here",
                               detail: filter == nil ? "Schoology lists nothing for you right now." : "Tap the day again to see everything.",
                               art: filter == nil ? "DeweyCaughtUp" : nil)
                }
                .listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            ForEach(groups, id: \.key) { g in
                Section {
                    ForEach(g.items, id: \.schoologyId) { it in row(it, now: now) }
                } header: {
                    groupHeader(g.title, g.items, now: now, sectionId: g.key)
                }
            }
            if !older.isEmpty {
                Section {
                    if showOlder {
                        ForEach(sortItems(older), id: \.schoologyId) { it in row(it, now: now) }
                    }
                } header: {
                    Button { withAnimation { showOlder.toggle() } } label: {
                        HStack {
                            Text("Older · \(older.count)").font(Theme.subhead.weight(.semibold)).foregroundStyle(Theme.text)
                            Text("over \(TodoLogic.olderDays) days late").font(Theme.footnote).foregroundStyle(Theme.tertiary)
                            Spacer()
                            Image(systemName: showOlder ? "chevron.up" : "chevron.down").foregroundStyle(Theme.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                    .textCase(nil)
                }
            }
        }
    }

    private struct Group { let key: String; let title: String; let items: [ItemState] }

    private func grouped(_ items: [ItemState]) -> [Group] {
        var bySection: [String: [ItemState]] = [:]
        for it in items { bySection[it.sectionId ?? "", default: []].append(it) }
        var out: [Group] = []
        for c in model.courses {
            if let list = bySection.removeValue(forKey: c.sectionId), !list.isEmpty {
                out.append(Group(key: c.sectionId, title: c.shortTitle, items: sortItems(list)))
            }
        }
        let rest = bySection.values.flatMap { $0 }
        if !rest.isEmpty { out.append(Group(key: "other", title: "Other", items: sortItems(rest))) }
        return out
    }

    /// Urgent first: Late, Today, Next 7 days, Later, then everything done (sorted last, as in Week).
    private func urgentGroups(_ items: [ItemState], now: Date) -> [Group] {
        let open = Dictionary(grouping: items.filter { !model.isFinished($0) }) { TodoLogic.bucket(due: $0.due, now: now) }
        var out: [Group] = []
        for (b, title) in [(TodoLogic.Bucket.overdue, "Late"), (.today, "Today"), (.week, "Next 7 days"), (.later, "Later")] {
            if let list = open[b], !list.isEmpty { out.append(Group(key: "\(b)", title: title, items: sortItems(list))) }
        }
        let finished = items.filter { model.isFinished($0) }
        if !finished.isEmpty { out.append(Group(key: "finished", title: "Done", items: sortItems(finished))) }
        return out
    }

    /// Unfinished first, then by due date (overdue oldest first, upcoming soonest first).
    private func sortItems(_ items: [ItemState]) -> [ItemState] {
        items.sorted { a, b in
            let fa = model.isFinished(a), fb = model.isFinished(b)
            if fa != fb { return !fa }
            return (a.due ?? .distantFuture) < (b.due ?? .distantFuture)
        }
    }

    private func groupHeader(_ title: String, _ items: [ItemState], now: Date, sectionId: String) -> some View {
        let open = items.filter { !model.isFinished($0) }
        // Urgent first already has a Today section.
        let dueToday = layout == .week && open.contains { TodoLogic.bucket(due: $0.due, now: now) == .today }
        return HStack(spacing: 8) {
            if let course = model.course(sectionId) { Circle().fill(model.classColor(course.sectionId)).frame(width: 10, height: 10) }
            Text(title).font(Theme.headline).foregroundStyle(Theme.text)
            if dueToday {
                Text("Due today").font(Theme.caption.weight(.semibold)).foregroundStyle(Theme.accent)
                    .padding(.horizontal, 7).padding(.vertical, 2).background(Theme.accentSoft, in: Capsule())
            }
            Spacer()
            Text("\(open.count)").font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary)
        }
        .textCase(nil)
    }

    // ── One row ──────────────────────────────────────────────────────────

    private func row(_ it: ItemState, now: Date) -> some View {
        TodoRow(item: it, now: now,
                // Urgent first mixes classes, so each row says its own.
                course: layout == .urgent ? model.courses.first { $0.sectionId == it.sectionId }?.shortTitle : nil,
                onSet: { model.setProgress(it, $0) },
                onSubmit: { sheet = SheetTarget(url: submitURL(it), assignmentId: it.schoologyId) },
                onOpen: { path.append(AssignmentRoute(id: it.schoologyId)) })
            // Every separator starts under the checkbox, whatever the row holds.
            .alignmentGuide(.listRowSeparatorLeading) { d in d[.leading] }
            // Grouped by whitespace and the class heading, not a white card.
            .listRowBackground(Color.clear)
            .listRowSeparatorTint(Theme.divider)
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                if it.turnedIn {
                    Button { model.setTurnedIn(it, false) } label: { Label("Not turned in", systemImage: "arrow.uturn.backward") }
                        .tint(Theme.secondary)
                } else {
                    Button { model.setTurnedIn(it, true) } label: { Label("Mark turned in", systemImage: "checkmark.circle") }
                        .tint(Theme.good)
                }
                if let lab = it.labURL.flatMap(URL.init(string:)) {
                    Button { sheet = SheetTarget(url: lab) } label: { Label("Lab site", systemImage: "arrow.up.right.square") }
                        .tint(Theme.accent)
                } else if it.hasDropbox && !it.submitted {
                    Button { sheet = SheetTarget(url: submitURL(it), assignmentId: it.schoologyId) } label: {
                        Label("Submit", systemImage: "arrow.up.right.square")
                    }
                    .tint(Theme.accent)
                }
            }
            .swipeActions(edge: .leading) {
                Button { model.setProgress(it, model.progress(it) == .todo ? .done : .todo) } label: {
                    Label(it.type == .exam ? (it.studied ? "Not studied" : "Studied") : (it.done ? "Not done" : "Done"),
                          systemImage: "checkmark")
                }
                .tint(Theme.accent)
            }
            .contextMenu {
                ForEach(AppModel.Progress.allCases, id: \.self) { p in
                    Button { model.setProgress(it, p) } label: {
                        Label(it.type == .exam && p == .done ? "Studied" : p.label,
                              systemImage: model.progress(it) == p ? "checkmark" : "circle")
                    }
                }
                Divider()
                Button { sheet = SheetTarget(url: assignmentURL(it), assignmentId: it.schoologyId) } label: {
                    Label("Open in Schoology", systemImage: "globe")
                }
                if !it.inSchoology {
                    Button(role: .destructive) { model.dismiss(it) } label: { Label("Remove from my list", systemImage: "trash") }
                }
            }
    }

    private func assignmentURL(_ it: ItemState) -> URL {
        URL(string: Engine.origin.absoluteString + (it.url ?? "/assignment/\(it.schoologyId)"))!
    }

    /// The assignment page: its Submit button opens Schoology's own dialog.
    private func submitURL(_ it: ItemState) -> URL {
        URL(string: "\(Engine.origin.absoluteString)/assignment/\(it.schoologyId)")!
    }
}

struct AssignmentRoute: Hashable { let id: String }

/// To do / Done (Studied) / Turned in as three big buttons. Used in a To Do
/// row (opens under it) and on the Assignment screen.
struct ProgressChips: View {
    let current: AppModel.Progress
    var isExam = false
    let onPick: (AppModel.Progress) -> Void
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        // Side by side; stacked at the accessibility text sizes, where three don't fit.
        let layout = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(spacing: 6)) : AnyLayout(HStackLayout(spacing: 6))
        layout {
            ForEach(AppModel.Progress.allCases, id: \.self) { p in
                let on = p == current
                let color = p == .turnedIn ? Theme.good : p == .done ? Theme.accent : Theme.secondary
                Button { onPick(p) } label: {
                    HStack(spacing: 5) {
                        Image(systemName: icon(p, on: on))
                        Text(title(p)).lineLimit(1).minimumScaleFactor(0.8)
                    }
                    .font(Theme.footnote.weight(on ? .bold : .semibold))
                    .frame(maxWidth: .infinity, minHeight: 40)
                    .foregroundStyle(on ? Theme.onAccent : color)
                    .background(on ? color : color.opacity(0.1), in: Capsule())
                    .overlay(Capsule().stroke(color.opacity(on ? 0 : 0.35)))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(title(p))
                .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
            }
        }
    }

    private func title(_ p: AppModel.Progress) -> String {
        switch p {
        case .todo: isExam ? "Not studied" : "To do"
        case .done: isExam ? "Studied" : "Done"
        case .turnedIn: "Turned in"
        }
    }

    private func icon(_ p: AppModel.Progress, on: Bool) -> String {
        switch p {
        case .todo: "circle"
        case .done: on ? "checkmark.circle.fill" : "checkmark.circle"
        case .turnedIn: on ? "checkmark.seal.fill" : "checkmark.seal"
        }
    }
}

struct TodoRow: View {
    let item: ItemState
    let now: Date
    /// The class, above the title (Urgent first only).
    var course: String?
    let onSet: (AppModel.Progress) -> Void
    let onSubmit: () -> Void
    /// Only the text opens the assignment, so the status buttons never navigate
    /// (a row-wide link caught taps after the list re-sorted under the finger).
    let onOpen: () -> Void
    @Environment(\.dynamicTypeSize) private var typeSize

    private var progress: AppModel.Progress {
        item.turnedIn ? .turnedIn : (item.type == .exam ? item.studied : item.done) ? .done : .todo
    }

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Space.m) {
            // The text decides the row's height (at least checkbox + type bar);
            // the left column (below) fills it.
            Color.clear.frame(width: 24, height: 24 + 4 + TypeBar.minHeight)
            VStack(alignment: .leading, spacing: 6) {
                if let course {
                    Text(course).font(Theme.caption.weight(.semibold)).foregroundStyle(Theme.secondary).oneLine()
                        .padding(.bottom, -4)
                }
                Text(item.title)
                    .font(Theme.body)
                    .foregroundStyle(Theme.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture(perform: onOpen)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityHint("Opens the assignment")
                    .accessibilityAction { onOpen() }
                // The title keeps the full width; the one-tap action (Submit / Turn in /
                // Undo) always sits at the right end of the due line, which wraps beside it.
                // At the largest text sizes it goes under the due line instead.
                if hasAction && !typeSize.isAccessibilitySize {
                    HStack(alignment: .center, spacing: Theme.Space.s) {
                        dueText.frame(maxWidth: .infinity, alignment: .leading)
                        action
                    }
                } else {
                    dueText
                    if hasAction { action }
                }
                if let (text, color, strong) = statusNote {
                    Text(text).font(Theme.footnote.weight(strong ? .bold : .medium)).foregroundStyle(color)
                }
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        // Left column: the checkbox, and the type bar under it, filling the row's
        // height (a short row shows the bar without its label instead of growing).
        .overlay(alignment: .topLeading) {
            VStack(spacing: 4) {
                // One tap: to do ↔ done (a turned-in row goes back to done), like the extension.
                Button {
                    onSet(progress == .todo ? .done : progress == .done ? .todo : .done)
                } label: {
                    CheckMark(item: item).frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(-10)
                .sensoryFeedback(.success, trigger: progress)
                .accessibilityLabel("Status: \(item.submitted ? "Submitted on Schoology" : label(progress))")
                .accessibilityHint(progress == .todo ? (item.type == .exam ? "Marks it studied" : "Marks it done") : "Marks it to do")
                TypeBar(type: item.type)
            }
            .frame(width: 24)
            .frame(maxHeight: .infinity, alignment: .top)
        }
        .padding(.vertical, Theme.Space.xs)
    }

    private var hasAction: Bool { !item.submitted && (item.turnedIn || item.hasDropbox || item.type != .exam) }

    @ViewBuilder
    private var action: some View {
        if item.submitted {
            EmptyView()
        } else if item.turnedIn {
            PillButton(title: "Undo", color: Theme.secondary) { onSet(.done) }
                .accessibilityLabel("Undo turned in for \(item.title)")
        } else if item.hasDropbox {
            PillButton(title: "Submit", external: true, action: onSubmit)
                .accessibilityLabel("Submit \(item.title) on Schoology")
        } else if item.type != .exam {
            PillButton(title: "Turn in", color: Theme.good) { onSet(.turnedIn) }
                .accessibilityLabel("Mark \(item.title) turned in")
                .accessibilityHint("Only in this app. Use it when you handed it in on paper or another site.")
        }
    }

    private func label(_ p: AppModel.Progress) -> String {
        item.type == .exam && p != .turnedIn ? (p == .done ? "Studied" : "Not studied") : p.label
    }

    /// "Mon Oct 5, 11:59 pm · 7 days late"; a test adds its countdown and studied mark.
    /// Each "·" part of the due line wraps as a whole ("9 days late" never splits).
    private func nb(_ s: String) -> String { s.replacingOccurrences(of: " ", with: "\u{00A0}") }

    private var dueText: Text {
        let late = item.submitted || item.turnedIn ? nil : TodoLogic.daysLate(due: item.due, now: now)
        var t = Text(nb(Fmt.due(item.due, now: now)))
        if let late, late > 0 { t = t + Text(" · " + nb("\(late) day\(late == 1 ? "" : "s") late")) } else if late == 0 { t = t + Text(" · late") }
        t = t.foregroundStyle(late != nil ? Theme.late : Theme.secondary)
        if item.type == .exam && !item.submitted && !item.turnedIn {
            // "Tomorrow, 11:59 pm" already says it: no "· tomorrow" after it.
            let dueWords = Fmt.due(item.due, now: now).lowercased()
            let countdown = Reminders.testCountdown(due: item.due, now: now)
                .map { $0.replacingOccurrences(of: "Test ", with: "") }
                .flatMap { dueWords.hasPrefix($0.lowercased()) ? nil : " · " + nb($0) } ?? ""
            t = t + Text("\(countdown) · \(nb(item.studied ? "studied" : "not studied"))")
                .foregroundStyle(item.studied ? Theme.accent : Theme.secondary)
        }
        return t.font(Theme.footnote.weight(late != nil ? .semibold : .regular).monospacedDigit())
    }

    /// The status, when it says more than "to do": its own line, never cut short.
    private var statusNote: (String, Color, Bool)? {
        if item.submitted { return ("Submitted on Schoology\(item.statusLate == true ? " · late" : "")", Theme.good, true) }
        if item.turnedIn { return ("Turned in · not on Schoology", Theme.good, true) }
        if item.type == .exam { return nil }
        if item.external { return (item.done ? "Done · check it in Schoology" : "Google Assignment · turn in on Schoology", item.done ? Theme.accent : Theme.secondary, false) }
        if item.done { return (item.hasDropbox ? "Done · not submitted yet" : "Done", Theme.accent, false) }
        if !item.inSchoology { return ("Not on Schoology anymore · tick when done", Theme.secondary, false) }
        return nil
    }
}

/// Accent ring with an accent check = done or studied (student) · solid green seal with
/// a check = turned in (submitted on Schoology, or marked): stamped, received. The seal's
/// outline keeps them apart in every theme, even where the accent and green are close.
/// Never a square: the type bar says it's a test.
/// The words beside it say which kind of turned in it is.
struct CheckMark: View {
    let item: ItemState
    var size: CGFloat = 24

    var body: some View {
        ZStack {
            if item.submitted || item.turnedIn {
                Image(systemName: "checkmark.seal.fill").resizable().scaledToFit()
                    .symbolRenderingMode(.palette).foregroundStyle(Theme.onAccent, Theme.good)
                    .fontWeight(.bold)
                    // The seal's points sit inside its box; this makes its body match the circles.
                    .scaleEffect(1.12)
            } else if item.type == .exam ? item.studied : item.done {
                Circle().fill(Theme.accentSoft)
                Circle().strokeBorder(Theme.accent, lineWidth: 2)
                check(Theme.accent)
            } else {
                Circle().stroke(item.neoPlanId == nil && NeoPlanClient().connected ? Theme.tertiary : Theme.secondary,
                                style: StrokeStyle(lineWidth: 1.6, dash: item.neoPlanId == nil && NeoPlanClient().connected ? [3, 2.5] : []))
            }
        }
        .frame(width: size, height: size)
    }

    private func check(_ c: Color) -> some View {
        Image(systemName: "checkmark").font(.system(size: size * 0.5, weight: .bold)).foregroundStyle(c)
    }
}
