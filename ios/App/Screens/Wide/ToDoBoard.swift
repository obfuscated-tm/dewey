import SwiftUI

/// To Do's three views on the open Duo. Remembered between launches.
enum BoardView: String, CaseIterable, Identifiable {
    case classes, calendar, plan
    static let key = "duoTodoView"
    var id: String { rawValue }
    var title: String {
        switch self {
        case .classes: "By class"
        case .calendar: "Calendar"
        case .plan: "Plan"
        }
    }
}

/// To Do on the open Duo (docs/ios/design/DuoBoard.html): a board, one column per
/// class, each a catalog card (call number, class, at most one stamp, its work on ruled
/// lines). Calendar puts the month left of the hinge and the picked day's work right of
/// it; Plan is a second board, To do / Later / Done. A card's text opens the assignment
/// over the right half and its column slides into the left half; nothing opens by itself.
struct ToDoBoard: View {
    @Environment(AppModel.self) private var model
    @Environment(Split.self) private var split
    @AppStorage(BoardView.key) private var viewID = BoardView.classes.rawValue
    @AppStorage(CallNumber.key) private var callRaw = ""
    @AppStorage(CallNumber.showKey) private var showCallNumbers = true
    @State private var filter: DayFilter? = Demo.startDay
    /// Calendar: the day picked on the month (it and the day after are listed).
    @State private var calDay = Calendar.current.startOfDay(for: Date())
    @State private var shownMonth = MonthGrid.start(of: Date())
    @State private var monthForward = true
    @State private var sheet: SheetTarget?
    @State private var editing: TaskTarget?
    @ScaledMetric(relativeTo: .subheadline) private var columnWidth: CGFloat = 272
    /// The narrowest a Plan column gets before the three scroll sideways.
    @ScaledMetric(relativeTo: .subheadline) private var planColumnMin: CGFloat = 232
    @ScaledMetric(relativeTo: .subheadline) private var stripWidth: CGFloat = 336

    private var view: BoardView { BoardView(rawValue: viewID) ?? .classes }

    var body: some View {
        let now = Date()
        let items = model.todoItems()
        VStack(spacing: 0) {
            header(open: items.filter { model.isPending($0, now: now) }, now: now)
            if model.todoAsOf == nil && items.isEmpty {
                ScrollView {
                    if let e = model.todoError {
                        ErrorState(error: e, retry: { Task { await model.refreshTodo() } })
                    } else {
                        SkeletonRows()
                    }
                }
                .padding(Theme.Space.l)
            } else {
                switch view {
                case .classes: classBoard(items: items, now: now)
                case .calendar: calendar(items: items, now: now)
                case .plan: planBoard(now: now)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Theme.background)
        .sheet(item: $sheet) { SchoologySheet(target: $0) }
        .sheet(item: $editing) { t in
            if let task = t.task { TaskEditor(task: task) } else { QuickTask(sectionId: t.sectionId) }
        }
        .onChange(of: viewID) { filter = nil }
    }

    // ── Header: title, the three views, the week, +, gear ───────────────

    private func header(open: [ItemState], now: Date) -> some View {
        let title = Text("To Do").font(Theme.display).foregroundStyle(Theme.text).lineLimit(1).fixedSize()
            .accessibilityAddTraits(.isHeader)
        let picker = Picker("View", selection: $viewID.animation(.snappy(duration: 0.2))) {
            ForEach(BoardView.allCases) { Text($0.title).tag($0.rawValue) }
        }
        .pickerStyle(.segmented)
        .frame(width: 232)
        // Late, today and the next six days, a dot per item still to do; tap one to show only it.
        let strip = WeekStrip(items: open, now: now, selected: $filter).frame(width: min(stripWidth, 520))
        let buttons = HStack(spacing: 0) {
            Button { editing = TaskTarget() } label: {
                Image(systemName: "plus").font(Theme.title.weight(.regular)).foregroundStyle(Theme.accent)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("New task")
            ScreenOptions(page: .todo)
        }
        let showStrip = view == .classes
        return VStack(alignment: .leading, spacing: Theme.Space.s) {
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .center, spacing: Theme.Space.m) {
                    title; picker
                    if showStrip { strip }
                    Spacer(minLength: 0)
                    buttons
                }
                VStack(alignment: .leading, spacing: Theme.Space.s) {
                    HStack(alignment: .center, spacing: Theme.Space.l) { title; picker; Spacer(minLength: 0); buttons }
                    // The open Duo keeps a strip down its right edge for the clock, so the week
                    // gets its own row there, under the title.
                    if showStrip { strip }
                }
                VStack(alignment: .leading, spacing: Theme.Space.s) {
                    HStack(alignment: .center) { title; Spacer(minLength: 0); buttons }
                    picker
                    if showStrip { strip }
                }
            }
            // Offline note / refresh spinner: only takes room when there's something to show.
            if (model.todoAsOf != nil && model.todoError != nil) || model.refreshingTodo {
                HStack(spacing: 8) {
                    AsOfBanner(date: model.todoAsOf, error: model.todoError)
                    if model.refreshingTodo { ProgressView().controlSize(.small) }
                }
            }
        }
        .padding(.horizontal, Theme.Space.l)
        .padding(.top, Theme.Space.s)
        .padding(.bottom, Theme.Space.xs)
    }

    // ── By class ────────────────────────────────────────────────────────

    private func classBoard(items: [ItemState], now: Date) -> some View {
        let filtered = items.filter { it in
            guard let filter else { return true }
            return model.isPending(it, now: now) && filter.matches(it, now: now)
        }
        let older = filtered.filter { TodoLogic.isOlder(due: $0.due, now: now) && !model.isFinished($0) }
        let current = filtered.filter { !older.contains($0) }
        var groups = model.todoGroups(current)
        if !older.isEmpty { groups.append(TodoGroup(key: "older", title: "Older", items: model.todoSorted(older))) }
        let quiet = filter == nil ? model.classes.filter { c in !groups.contains { $0.key == c.sectionId } } : []
        return ScrollView(.vertical) {
            if groups.isEmpty {
                EmptyState(icon: "checkmark.circle", title: filter == nil ? "Nothing due" : "Nothing here",
                           detail: filter == nil ? "Schoology lists nothing for you right now." : "Tap the day again to see everything.",
                           art: filter == nil ? "DeweyCaughtUp" : nil)
                    .padding(Theme.Space.l)
            } else {
                ScrollViewReader { proxy in
                    ScrollView(.horizontal) {
                        HStack(alignment: .top, spacing: Theme.Space.m) {
                            ForEach(groups, id: \.key) { g in
                                column(g, now: now).id(g.key)
                            }
                            if !quiet.isEmpty {
                                Text("Nothing open: " + quiet.map(\.shortTitle).joined(separator: ", "))
                                    .font(Theme.footnote).foregroundStyle(Theme.tertiary)
                                    .frame(width: 150, alignment: .leading)
                                    .padding(.top, Theme.Space.m)
                            }
                        }
                        .padding(.vertical, Theme.Space.m)
                    }
                    .contentMargins(.horizontal, Theme.Space.l, for: .scrollContent)
                    .scrollIndicators(.hidden)
                    // The opened item's column slides to the left edge, clear of the item: after
                    // the item has slid in and the board has its new width.
                    .onChange(of: split.details[0], initial: true) { _, opened in
                        guard !split.upright, let r = opened?.base as? AssignmentRoute,
                              let key = groups.first(where: { g in g.items.contains { $0.schoologyId == r.id } })?.key
                        else { return }
                        Task { @MainActor in
                            try? await Task.sleep(for: .milliseconds(320))
                            withAnimation(.snappy(duration: 0.25)) { proxy.scrollTo(key, anchor: .leading) }
                        }
                    }
                }
            }
        }
        .refreshable { await model.refreshTodo(quick: true) }
    }

    /// One class: a catalog card as wide as a column.
    private func column(_ g: TodoGroup, now: Date) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            columnHeading(g, now: now)
            ForEach(Array(g.items.enumerated()), id: \.element.schoologyId) { i, it in
                card(it, now: now, showClass: g.key == "older" || g.key == "other")
                    .overlay(alignment: .top) { if i > 0 { Theme.catalogLine.frame(height: 1).padding(.horizontal, Theme.Space.m) } }
            }
        }
        .padding(.bottom, Theme.Space.xs)
        .card()
        .frame(width: columnWidth)
    }

    /// The top of the card on one line: call number (or a dot) in the class colour, the
    /// class and its period, at most one stamp; the rule under it is red only for late work.
    private func columnHeading(_ g: TodoGroup, now: Date) -> some View {
        let course = model.course(g.key)
        let number = showCallNumbers ? course.flatMap { CallNumber.number(for: $0, raw: callRaw) } : nil
        let stamp = model.cardStamp(g.items, now: now)
        let color = course.map { model.classColor($0.sectionId) } ?? Theme.text
        let period = course.flatMap { (1...3).contains($0.periodLabel.count) ? $0.periodLabel : nil }
        return HStack(alignment: .center, spacing: 6) {
            if let number {
                Text(number).font(Theme.callNumber.monospacedDigit()).foregroundStyle(color)
                    .accessibilityLabel("Call number \(number)")
            } else if course != nil {
                Circle().fill(color).frame(width: 9, height: 9).accessibilityHidden(true)
            }
            (Text(g.title).font(Theme.headline).foregroundStyle(Theme.text)
                + Text(period.map { "  \($0)" } ?? "").font(Theme.footnote).foregroundStyle(Theme.secondary))
                .lineLimit(1).minimumScaleFactor(0.8)
            Spacer(minLength: 0)
            if let stamp { CardStampView(stamp: stamp) }
        }
        .frame(minHeight: 28)
        .padding(.horizontal, Theme.Space.m)
        .padding(.top, Theme.Space.s).padding(.bottom, 6)
        .overlay(alignment: .bottom) {
            (stamp == .late ? Theme.late : color).frame(height: 2).padding(.horizontal, Theme.Space.m)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    // ── One card ────────────────────────────────────────────────────────

    private func card(_ it: ItemState, now: Date, showClass: Bool) -> some View {
        let course = showClass ? model.course(it.sectionId) : nil
        let isOpen = split.isOpen(AssignmentRoute(id: it.schoologyId, sectionId: it.sectionId))
        return TodoRow(item: it, now: now,
                       course: showClass ? course?.shortTitle ?? (it.mine ? "My task" : it.courseName.map(Course.short)) : nil,
                       onSet: { model.setProgress(it, $0) },
                       onSubmit: { sheet = SheetTarget(url: submitURL(it), assignmentId: it.schoologyId) },
                       onOpen: { open(it) },
                       board: true,
                       courseColor: course.map { model.classColor($0.sectionId) })
            .background(isOpen ? Theme.accentSoft : .clear)
            .accessibilityAddTraits(isOpen ? .isSelected : [])
            .contextMenu {
                if it.mine {
                    Button { editing = TaskTarget(task: it) } label: { Label("Edit task", systemImage: "pencil") }
                    Button(role: .destructive) { model.deleteTask(it) } label: { Label("Delete task", systemImage: "trash") }
                } else {
                    if let lab = it.labURL.flatMap(URL.init(string:)) {
                        Button { sheet = SheetTarget(url: lab) } label: { Label("Lab site", systemImage: "arrow.up.right.square") }
                    }
                    Button { sheet = SheetTarget(url: assignmentURL(it), assignmentId: it.schoologyId) } label: {
                        Label("Open in Schoology", systemImage: "globe")
                    }
                }
                if !it.inSchoology && !it.mine {
                    Button(role: .destructive) { model.dismiss(it) } label: { Label("Remove from my list", systemImage: "trash") }
                }
            }
    }

    /// A Schoology item opens over the right half; the student's own task, its editor.
    private func open(_ it: ItemState) {
        if it.mine {
            editing = TaskTarget(task: it)
        } else {
            split.open(AssignmentRoute(id: it.schoologyId, sectionId: it.sectionId))
        }
    }

    private func assignmentURL(_ it: ItemState) -> URL {
        URL(string: Engine.origin.absoluteString + (it.url ?? "/assignment/\(it.schoologyId)"))!
    }

    /// The assignment page: its Submit button opens Schoology's own dialog.
    private func submitURL(_ it: ItemState) -> URL {
        URL(string: "\(Engine.origin.absoluteString)/assignment/\(it.schoologyId)")!
    }

    // ── Calendar ────────────────────────────────────────────────────────

    /// The month left of the hinge, the picked day and the day after right of it (stacked
    /// when there's one column: upright, or beside an opened item).
    @ViewBuilder
    private func calendar(items: [ItemState], now: Date) -> some View {
        let open = items.filter { model.isPending($0, now: now) }
        let picked = Binding<DayFilter?>(
            get: { .span(calDay) },
            set: { if case .span(let d)? = $0 { calDay = d } })
        let month = MonthGrid(items: open, now: now, selected: picked, month: $shownMonth, forward: $monthForward)
            .padding(Theme.Space.m)
            .card()
        if split.twoColumns(0) {
            HStack(alignment: .top, spacing: 0) {
                ScrollView { month.padding(Theme.Space.l) }
                    .frame(width: split.listWidth)
                ScrollView { days(open, now: now).padding(Theme.Space.l) }
            }
        } else {
            ScrollView {
                VStack(spacing: Theme.Space.l) {
                    month
                    days(open, now: now)
                }
                .padding(Theme.Space.l)
            }
        }
    }

    private func days(_ open: [ItemState], now: Date) -> some View {
        let cal = Calendar.current
        let today = cal.isDate(calDay, inSameDayAs: now)
        let late = today ? open.filter { DayFilter.late.matches($0, now: now) } : []
        let next = cal.date(byAdding: .day, value: 1, to: calDay)!
        func due(on day: Date) -> [ItemState] {
            model.todoSorted(open.filter { it in
                guard let d = it.due, cal.isDate(d, inSameDayAs: day) else { return false }
                return !late.contains(it)
            })
        }
        return VStack(spacing: Theme.Space.m) {
            if !late.isEmpty { dayCard("Late", model.todoSorted(late), now: now, late: true) }
            dayCard(dayTitle(calDay, now: now), due(on: calDay), now: now)
            dayCard(dayTitle(next, now: now), due(on: next), now: now)
        }
    }

    private func dayCard(_ title: String, _ items: [ItemState], now: Date, late: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text(title).font(Theme.callNumber).foregroundStyle(late ? Theme.late : Theme.text)
                Spacer()
                Text("\(items.count)").font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary)
                    .accessibilityLabel(items.count == 1 ? "1 item" : "\(items.count) items")
            }
            .padding(.horizontal, Theme.Space.m)
            .padding(.top, Theme.Space.m).padding(.bottom, Theme.Space.s)
            .overlay(alignment: .bottom) {
                (late ? Theme.late : Theme.catalogRule).frame(height: late ? 2 : 1.5).padding(.horizontal, Theme.Space.m)
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            if items.isEmpty {
                Text("Nothing due").font(Theme.subhead).foregroundStyle(Theme.secondary)
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    .padding(.horizontal, Theme.Space.m)
            }
            ForEach(Array(items.enumerated()), id: \.element.schoologyId) { i, it in
                card(it, now: now, showClass: true)
                    .overlay(alignment: .top) { if i > 0 { Theme.catalogLine.frame(height: 1).padding(.horizontal, Theme.Space.m) } }
            }
        }
        .padding(.bottom, Theme.Space.xs)
        .card()
    }

    private func dayTitle(_ day: Date, now: Date) -> String {
        let cal = Calendar.current
        if cal.isDate(day, inSameDayAs: now) { return "Today" }
        if let t = cal.date(byAdding: .day, value: 1, to: now), cal.isDate(day, inSameDayAs: t) { return "Tomorrow" }
        return Fmt.header.string(from: day)
    }

    // ── Plan ────────────────────────────────────────────────────────────

    /// The student's own order as three columns. Hold a card and drag it onto another
    /// (it goes above that one) or into a column (it goes to the top); into Done marks it done.
    private func planBoard(now: Date) -> some View {
        let lists = model.planLists(now: now)
        let rows = PlanLogic.rows(todo: lists.todo.map(\.schoologyId), later: lists.later.map(\.schoologyId),
                                  done: lists.done.map(\.schoologyId))
        let columns: [(PlanLogic.Bucket, [ItemState])] = [(.todo, lists.todo), (.later, lists.later), (.done, lists.done)]
        return GeometryReader { geo in
            // All three across on the open Duo (about 246 pt each); they scroll sideways when narrower.
            let width = max(planColumnMin, (geo.size.width - 2 * Theme.Space.l - 2 * Theme.Space.m) / 3)
            ScrollView(.vertical) {
                ScrollView(.horizontal) {
                    HStack(alignment: .top, spacing: Theme.Space.m) {
                        ForEach(columns, id: \.0) { b, items in
                            VStack(spacing: 0) {
                                planColumn(b, items, rows: rows, now: now)
                                // Under a column's last card, down to the bottom of the screen: its end.
                                Color.clear.frame(minHeight: 44, maxHeight: .infinity)
                                    .contentShape(Rectangle())
                                    .dropDestination(for: String.self) { ids, _ in drop(ids, endOf: b, rows: rows) }
                            }
                            .frame(width: width)
                        }
                    }
                    .frame(minHeight: geo.size.height - 2 * Theme.Space.m, alignment: .top)
                    .padding(.vertical, Theme.Space.m)
                }
                .contentMargins(.horizontal, Theme.Space.l, for: .scrollContent)
                .scrollIndicators(.hidden)
            }
            .refreshable { await model.refreshTodo(quick: true) }
        }
    }

    private func planColumn(_ b: PlanLogic.Bucket, _ items: [ItemState], rows: [PlanLogic.Row], now: Date) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text(b.title).font(Theme.callNumber).foregroundStyle(b == .done ? Theme.secondary : Theme.text)
                Spacer()
                Text(b == .done ? "\(items.count) today" : "\(items.count)")
                    .font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary)
            }
            .padding(.horizontal, Theme.Space.m)
            .padding(.top, Theme.Space.m).padding(.bottom, Theme.Space.s)
            .overlay(alignment: .bottom) {
                (b == .todo ? Theme.catalogRule : Theme.catalogLine).frame(height: 1.5).padding(.horizontal, Theme.Space.m)
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            // Each drop target is its own row (never one inside another): the heading is the
            // top of the section, a card takes that card's place, under the last one is the end.
            .contentShape(Rectangle())
            .dropDestination(for: String.self) { ids, _ in drop(ids, topOf: b, rows: rows) }
            if items.isEmpty {
                Text(b == .done ? "Drag work here when it's done." : "Drag work here.")
                    .font(Theme.footnote).foregroundStyle(Theme.tertiary)
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    .padding(.horizontal, Theme.Space.m)
                    .contentShape(Rectangle())
                    .dropDestination(for: String.self) { ids, _ in drop(ids, topOf: b, rows: rows) }
            }
            ForEach(Array(items.enumerated()), id: \.element.schoologyId) { i, it in
                planCard(it, number: b == .todo ? i + 1 : nil, bucket: b, rows: rows, now: now)
                    .overlay(alignment: .top) { if i > 0 { Theme.catalogLine.frame(height: 1).padding(.horizontal, Theme.Space.m) } }
            }
        }
        .padding(.bottom, Theme.Space.xs)
        // Its own height for this width, so the card is never taller than its rows.
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .card()
    }

    private func drop(_ ids: [String], topOf b: PlanLogic.Bucket, rows: [PlanLogic.Row]) -> Bool {
        guard let id = ids.first, rows.contains(.item(id)) else { return false }
        move(id, PlanLogic.moved(rows, id: id, to: b))
        return true
    }

    private func drop(_ ids: [String], endOf b: PlanLogic.Bucket, rows: [PlanLogic.Row]) -> Bool {
        guard let id = ids.first, let from = rows.firstIndex(of: .item(id)),
              let heading = rows.firstIndex(of: .header(b)) else { return false }
        let end = rows[(heading + 1)...].firstIndex(where: Self.isHeading) ?? rows.count
        move(id, PlanLogic.moved(rows, from: IndexSet(integer: from), to: end))
        return true
    }

    /// Dropped on a card: it takes that card's place. Dragged down its own section the card
    /// moves up to make room; from above or another section it moves down.
    private func drop(_ ids: [String], on target: String, rows: [PlanLogic.Row]) -> Bool {
        guard let id = ids.first, id != target,
              let from = rows.firstIndex(of: .item(id)), let to = rows.firstIndex(of: .item(target)) else { return false }
        let downItsSection = from < to && !rows[from...to].contains(where: Self.isHeading)
        move(id, PlanLogic.moved(rows, from: IndexSet(integer: from), to: downItsSection ? to + 1 : to))
        return true
    }

    private static func isHeading(_ r: PlanLogic.Row) -> Bool {
        if case .header = r { true } else { false }
    }

    private func planCard(_ it: ItemState, number: Int?, bucket b: PlanLogic.Bucket, rows: [PlanLogic.Row], now: Date) -> some View {
        let id = it.schoologyId
        return card(it, now: now, showClass: true)
            .opacity(b == .done ? 0.6 : 1)
            // To do is numbered: the order to do it in.
            .overlay(alignment: .topTrailing) {
                if let number {
                    Text("\(number)").font(Theme.callNumber.monospacedDigit())
                        .foregroundStyle(number == 1 ? Theme.accent : Theme.tertiary)
                        .padding(.top, Theme.Space.s).padding(.trailing, Theme.Space.m)
                        .accessibilityLabel("Number \(number)")
                }
            }
            .contentShape(.dragPreview, RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
            .draggable(id)
            .dropDestination(for: String.self) { ids, _ in drop(ids, on: id, rows: rows) }
            // The same moves without a drag (VoiceOver, Switch Control).
            .accessibilityAction(named: "Move up") { move(id, PlanLogic.nudged(rows, id: id, by: -1)) }
            .accessibilityAction(named: "Move down") { move(id, PlanLogic.nudged(rows, id: id, by: 1)) }
            .accessibilityAction(named: b == .later ? "Move to To do" : "Move to Later") {
                move(id, PlanLogic.moved(rows, id: id, to: b == .later ? .todo : .later))
            }
    }

    private func move(_ id: String, _ placed: [String: (PlanLogic.Bucket, Double)]) {
        withAnimation(.snappy(duration: 0.2)) { model.applyPlan(placed, moved: id) }
    }
}
