import SwiftUI

/// 1a · To Do: everything due, grouped by class in Course Dashboard order.
struct ToDoView: View {
    @Environment(AppModel.self) private var model
    @State private var filter: TodoLogic.Bucket?
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
                        .listRowInsets(EdgeInsets(top: 8, leading: 20, bottom: 12, trailing: 20))
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
    }

    // ── Header: date, title, gear, three count buttons ───────────────────

    @ViewBuilder
    private func header(items: [ItemState], now: Date) -> some View {
        let open = items.filter { !model.isFinished($0) }
        let overdue = open.filter { TodoLogic.bucket(due: $0.due, now: now) == .overdue && !TodoLogic.isOlder(due: $0.due, now: now) }.count
        let today = open.filter { TodoLogic.bucket(due: $0.due, now: now) == .today }.count
        let week = open.filter { [.today, .week].contains(TodoLogic.bucket(due: $0.due, now: now)) }.count
        VStack(alignment: .leading, spacing: 14) {
            ScreenHeader(kicker: Fmt.header.string(from: now), title: "To Do") {
                Button { showSettings = true } label: {
                    Image(systemName: "gearshape").font(Theme.font(20)).foregroundStyle(Theme.secondary)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Settings")
            }
            HStack(spacing: 8) {
                countButton(overdue, "Overdue", .overdue, Theme.late)
                countButton(today, "Due today", .today, Theme.accent)
                countButton(week, "This week", .week, Theme.text)
            }
            HStack(spacing: 8) {
                AsOfBanner(date: model.todoAsOf, error: model.todoError)
                if model.refreshingTodo { ProgressView().controlSize(.small) }
            }
        }
    }

    private func countButton(_ n: Int, _ label: String, _ b: TodoLogic.Bucket, _ color: Color) -> some View {
        let on = filter == b
        return Button {
            filter = on ? nil : b
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(n)").font(Theme.mono(20, .semibold)).foregroundStyle(on ? .white : color)
                Text(label).font(Theme.font(12.5, .medium)).foregroundStyle(on ? .white : Theme.secondary)
            }
            .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
            .padding(.horizontal, 12)
            .background(on ? color : Theme.card, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(on ? Color.clear : Theme.border))
        }
        .buttonStyle(.plain)
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
                let b = TodoLogic.bucket(due: it.due, now: now)
                if model.isFinished(it) { return false }
                switch filter {
                case .overdue: return b == .overdue && !TodoLogic.isOlder(due: it.due, now: now)
                case .today: return b == .today
                case .week: return b == .today || b == .week
                case .later: return true
                }
            }
            let older = filtered.filter { TodoLogic.isOlder(due: $0.due, now: now) && !model.isFinished($0) }
            let current = filtered.filter { !older.contains($0) }
            let groups = grouped(current)
            if groups.isEmpty && older.isEmpty {
                Section {
                    EmptyState(icon: "checkmark.circle", title: filter == nil ? "Nothing due" : "Nothing here",
                               detail: filter == nil ? "Schoology lists nothing for you right now." : "Tap the count again to see everything.")
                }
                .listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            ForEach(groups, id: \.key) { g in
                Section {
                    ForEach(g.items, id: \.schoologyId) { it in row(it, now: now) }
                } header: {
                    groupHeader(g.title, g.items, now: now)
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
                            Text("Older · \(older.count)").font(Theme.font(15, .semibold)).foregroundStyle(Theme.text)
                            Text("over \(TodoLogic.olderDays) days late").font(Theme.font(13)).foregroundStyle(Theme.tertiary)
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

    /// Unfinished first, then by due date (overdue oldest first, upcoming soonest first).
    private func sortItems(_ items: [ItemState]) -> [ItemState] {
        items.sorted { a, b in
            let fa = model.isFinished(a), fb = model.isFinished(b)
            if fa != fb { return !fa }
            return (a.due ?? .distantFuture) < (b.due ?? .distantFuture)
        }
    }

    private func groupHeader(_ title: String, _ items: [ItemState], now: Date) -> some View {
        let open = items.filter { !model.isFinished($0) }
        let dueToday = open.contains { TodoLogic.bucket(due: $0.due, now: now) == .today }
        return HStack(spacing: 8) {
            Text(title).font(Theme.font(15, .semibold)).foregroundStyle(Theme.text)
            if dueToday {
                Text("Due today").font(Theme.font(11.5, .semibold)).foregroundStyle(Theme.accent)
                    .padding(.horizontal, 7).padding(.vertical, 2).background(Theme.accentSoft, in: Capsule())
            }
            Spacer()
            Text("\(open.count)").font(Theme.mono(13, .medium)).foregroundStyle(Theme.secondary)
        }
        .textCase(nil)
    }

    // ── One row ──────────────────────────────────────────────────────────

    private func row(_ it: ItemState, now: Date) -> some View {
        TodoRow(item: it, now: now,
                onSet: { model.setProgress(it, $0) },
                onSubmit: { sheet = SheetTarget(url: submitURL(it), assignmentId: it.schoologyId) },
                onOpen: { path.append(AssignmentRoute(id: it.schoologyId)) })
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                if it.turnedIn {
                    Button { model.setTurnedIn(it, false) } label: { Label("Not turned in", systemImage: "arrow.uturn.backward") }
                        .tint(Theme.secondary)
                } else {
                    Button { model.setTurnedIn(it, true) } label: { Label("Mark turned in", systemImage: "checkmark.circle") }
                        .tint(Theme.good)
                }
                if let lab = it.labURL.flatMap(URL.init(string:)) {
                    Button { sheet = SheetTarget(url: lab) } label: { Label("Lab site ↗", systemImage: "arrow.up.right.square") }
                        .tint(Theme.accent)
                } else if it.hasDropbox && !it.submitted {
                    Button { sheet = SheetTarget(url: submitURL(it), assignmentId: it.schoologyId) } label: {
                        Label("Submit ↗", systemImage: "arrow.up.right.square")
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

    var body: some View {
        HStack(spacing: 6) {
            ForEach(AppModel.Progress.allCases, id: \.self) { p in
                let on = p == current
                let color = p == .turnedIn ? Theme.good : p == .done ? Theme.accent : Theme.secondary
                Button { onPick(p) } label: {
                    HStack(spacing: 5) {
                        Image(systemName: icon(p, on: on))
                        Text(title(p)).lineLimit(1).minimumScaleFactor(0.8)
                    }
                    .font(Theme.font(13.5, on ? .bold : .semibold))
                    .frame(maxWidth: .infinity, minHeight: 40)
                    .foregroundStyle(on ? Theme.onAccent : color)
                    .background(on ? color : color.opacity(0.1), in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(color.opacity(on ? 0 : 0.35)))
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
        case .turnedIn: on ? "checkmark.circle.fill" : "tray.and.arrow.down"
        }
    }
}

struct TodoRow: View {
    let item: ItemState
    let now: Date
    let onSet: (AppModel.Progress) -> Void
    let onSubmit: () -> Void
    /// Only the text opens the assignment, so the status buttons never navigate
    /// (a row-wide link caught taps after the list re-sorted under the finger).
    let onOpen: () -> Void

    private var progress: AppModel.Progress {
        item.turnedIn ? .turnedIn : (item.type == .exam ? item.studied : item.done) ? .done : .todo
    }

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            // One tap: to do ↔ done (a turned-in row goes back to done), like the extension.
            Button {
                onSet(progress == .todo ? .done : progress == .done ? .todo : .done)
            } label: {
                CheckMark(item: item).frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.leading, -10).padding(.top, -10)
            .sensoryFeedback(.success, trigger: progress)
            .accessibilityLabel("Status: \(item.submitted ? "Submitted on Schoology" : label(progress))")
            .accessibilityHint(progress == .todo ? (item.type == .exam ? "Marks it studied" : "Marks it done") : "Marks it to do")
            VStack(alignment: .leading, spacing: 4) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.title)
                        .font(.system(size: 16, weight: item.type == .exam ? .bold : .semibold))
                        .foregroundStyle(Theme.text)
                        .lineLimit(2)
                    HStack(spacing: 6) {
                        TypeTag(type: item.type)
                        dueText
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
                .onTapGesture(perform: onOpen)
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isButton)
                .accessibilityHint("Opens the assignment")
                .accessibilityAction { onOpen() }
                // Status words on the left, one-tap action on the right (extension: Done / Turn in / Undo).
                HStack(alignment: .center, spacing: 8) {
                    statusLine
                    Spacer(minLength: 4)
                    action
                }
            }
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var action: some View {
        if item.submitted {
            EmptyView()
        } else if item.turnedIn {
            PillButton(title: "Undo", color: Theme.secondary) { onSet(.done) }
                .accessibilityLabel("Undo turned in for \(item.title)")
        } else if item.hasDropbox {
            PillButton(title: "Submit ↗", action: onSubmit)
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

    private var dueText: some View {
        let late = item.submitted || item.turnedIn ? nil : TodoLogic.daysLate(due: item.due, now: now)
        return HStack(spacing: 0) {
            Text(Fmt.due(item.due, now: now))
            if let late, late > 0 { Text(" · \(late) d late") } else if late == 0 { Text(" · late") }
        }
        .font(Theme.mono(12.5))
        .foregroundStyle(late != nil ? Theme.late : Theme.secondary)
        .lineLimit(1)
    }

    @ViewBuilder
    private var statusLine: some View {
        let (text, color): (String, Color) = {
            if item.submitted { return ("Submitted on Schoology\(item.statusLate == true ? " · late" : "")", Theme.good) }
            if item.turnedIn { return ("Turned in · not on Schoology", Theme.good) }
            if item.type == .exam { return (item.studied ? "Studied" : "Not studied", item.studied ? Theme.accent : Theme.text) }
            if item.external { return (item.done ? "Done · check it in Schoology" : "Google Assignment · turn in on Schoology", item.done ? Theme.accent : Theme.secondary) }
            if item.done { return (item.hasDropbox ? "Done · not submitted yet" : "Done", Theme.accent) }
            if !item.inSchoology { return ("Schoology dropped this · still yours to tick", Theme.secondary) }
            return ("To do", Theme.secondary)
        }()
        Text(text).font(Theme.font(13, item.submitted || item.turnedIn ? .bold : .medium)).foregroundStyle(color)
    }
}

/// Blue filled = done (student) · green filled, heavy check = turned in
/// (submitted on Schoology, or marked) · square = a test (studied / not).
/// The words beside it say which kind of turned in it is.
struct CheckMark: View {
    let item: ItemState
    var size: CGFloat = 22

    var body: some View {
        ZStack {
            if item.type == .exam && !item.submitted && !item.turnedIn {
                RoundedRectangle(cornerRadius: 5)
                    .fill(item.studied ? Theme.accent : Color.clear)
                    .overlay(RoundedRectangle(cornerRadius: 5).stroke(item.studied ? Theme.accent : Theme.text, lineWidth: 1.6))
                if item.studied { check(Theme.onAccent) }
            } else if item.submitted || item.turnedIn {
                Circle().fill(Theme.good)
                check(Theme.onAccent, weight: .black)
            } else if item.done {
                Circle().fill(Theme.accent)
                check(Theme.onAccent)
            } else {
                Circle().stroke(item.neoPlanId == nil && NeoPlanClient().connected ? Theme.tertiary : Theme.secondary,
                                style: StrokeStyle(lineWidth: 1.6, dash: item.neoPlanId == nil && NeoPlanClient().connected ? [3, 2.5] : []))
            }
        }
        .frame(width: size, height: size)
    }

    private func check(_ c: Color, weight: Font.Weight = .bold) -> some View {
        Image(systemName: "checkmark").font(.system(size: size * 0.5, weight: weight)).foregroundStyle(c)
    }
}
