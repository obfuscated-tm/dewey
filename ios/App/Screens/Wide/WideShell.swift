import SwiftUI

/// iPhone Duo, open (docs/ios/design/DuoBoard.html): a regular-width window gets a rail
/// of the five tabs (a tab bar when it's held upright) and each tab the whole width.
/// Nothing opens by itself: a tapped item slides over the right half, from the hinge,
/// and the list or board stays live on the left. Chosen by size class, never by "is it
/// open", so the folded screen and a narrow split keep the phone layout (MainTabs).
@Observable final class Split {
    /// 0 To Do · 1 Grades · 2 Inbox · 3 Classes · 4 Schoology (MainTabs' tags).
    var tab = 0
    /// What each tab has open over its right half: an AssignmentRoute, Course,
    /// MessageThread, AssignmentLink, GradeRoute, or a class page, folder or material
    /// (Classes). None: the tab has the whole width.
    var details: [Int: AnyHashable] = [:]
    /// Held upright (about 669 pt wide): a tab bar instead of the rail, and an opened
    /// item covers the whole screen above it.
    var upright = false
    /// The width left of the hinge, after the rail: what a tab keeps while an item is open.
    var listWidth: CGFloat = 400

    var detail: AnyHashable? { details[tab] }

    func open(_ v: AnyHashable, tab t: Int? = nil) {
        if let t { tab = t }
        withAnimation(.snappy(duration: 0.28)) { details[t ?? tab] = v }
    }

    func close() {
        withAnimation(.snappy(duration: 0.28)) { details[tab] = nil }
    }

    func isOpen(_ v: AnyHashable) -> Bool { details[tab] == v }

    /// Side by side: a tab's second column shows (wide, and nothing open over it).
    func twoColumns(_ t: Int) -> Bool { !upright && details[t] == nil }
}

private struct SplitKey: EnvironmentKey { static let defaultValue: Split? = nil }

extension EnvironmentValues {
    /// Set by WideShell for the tabs' own screens only (not inside an opened item):
    /// their rows open over the right half instead of pushing.
    var split: Split? {
        get { self[SplitKey.self] }
        set { self[SplitKey.self] = newValue }
    }
}

/// A NavigationLink on the phone; on the open Duo, a button that opens `value` over the
/// right half, tinted while it's the one open. `inset`: how far the tint reaches past
/// the row (0 inside a card; nil for no tint, where something else marks it).
struct SplitLink<Value: Hashable, Label: View>: View {
    @Environment(\.split) private var split
    let value: Value
    /// What the Duo opens instead, where the same value means another screen there
    /// (a class is its grades on Grades, its page on Classes).
    var wide: AnyHashable?
    var inset: CGFloat?
    @ViewBuilder var label: Label

    init(value: Value, as wide: AnyHashable? = nil, inset: CGFloat? = -8, @ViewBuilder label: () -> Label) {
        self.value = value
        self.wide = wide
        self.inset = inset
        self.label = label()
    }

    var body: some View {
        if let split {
            let v = wide ?? AnyHashable(value)
            Button { split.open(v) } label: {
                label.background { if let inset, split.isOpen(v) { SelectedRow(inset: inset) } }
            }
            .accessibilityAddTraits(split.isOpen(v) ? .isSelected : [])
        } else {
            NavigationLink(value: value) { label }
        }
    }
}

/// The open row's tint: soft accent with an accent bar on its leading edge,
/// reaching a little past the row so text doesn't touch it.
struct SelectedRow: View {
    var inset: CGFloat = -8
    var body: some View {
        Theme.accentSoft
            .overlay(alignment: .leading) { Theme.accent.frame(width: 3) }
            .padding(.horizontal, inset)
    }
}

/// A class's page (its materials), opened from the Classes shelf. `Course` alone opens
/// its grades (the Grades tab).
struct ClassPageRoute: Hashable { let course: Course }

struct WideShell: View {
    @Environment(AppModel.self) private var model
    @State private var split = Split()
    /// Tabs opened so far: each is built the first time and kept, like a tab bar's.
    @State private var built: Set<Int> = [Demo.startTab]
    @State private var showID = false
    @State private var showTour = false
    @AppStorage(TourView.shownKey) private var tourShown = false
    @Environment(\.scenePhase) private var phase

    static let railWidth: CGFloat = 72
    static let barHeight: CGFloat = 56
    /// Narrower than this, the window is upright: tab bar, opened items full screen.
    static let uprightBelow: CGFloat = 800

    var body: some View {
        GeometryReader { geo in
            let upright = geo.size.width < Self.uprightBelow
            // The hinge is the middle of the screen, whatever the safe areas are.
            let hinge = (geo.size.width + geo.safeAreaInsets.leading + geo.safeAreaInsets.trailing) / 2 - geo.safeAreaInsets.leading
            let pane = upright ? geo.size.width : geo.size.width - hinge
            ZStack(alignment: upright ? .bottom : .leading) {
                ZStack {
                    ForEach(0..<5, id: \.self) { t in
                        if built.contains(t) {
                            tab(t, pane: pane, upright: upright)
                                .opacity(split.tab == t ? 1 : 0)
                                .allowsHitTesting(split.tab == t)
                                .accessibilityHidden(split.tab != t)
                        }
                    }
                }
                .padding(.leading, upright ? 0 : Self.railWidth + 1)
                .padding(.bottom, upright ? Self.barHeight + 1 : 0)
                WideRail(upright: upright)
            }
            .onChange(of: geo.size, initial: true) {
                split.upright = upright
                split.listWidth = max(280, hinge - Self.railWidth - 1)
            }
        }
        .background(Theme.background)
        .environment(split)
        .onChange(of: split.tab, initial: true) { _, t in built.insert(t) }
        .onReceive(NotificationCenter.default.publisher(for: .openTodo)) { _ in split.tab = 0 }
        .onReceive(NotificationCenter.default.publisher(for: .showStudentID)) { _ in showID = true }
        .fullScreenCover(isPresented: $showID) { StudentIDCard() }
        .fullScreenCover(isPresented: $showTour) { TourView() }
        .onOpenURL { url in
            if AppLink.isStudentID(url) { showID = true; return }
            split.tab = 0
            if let id = AppLink.assignmentId(url) { openAssignment(id) }
        }
        // A deep link set elsewhere (a notification, Siri) opens over the right half.
        .onChange(of: model.deepLinkAssignment, initial: true) { _, id in
            guard let id else { return }
            openAssignment(id)
            model.deepLinkAssignment = nil
        }
        .task {
            if !tourShown && Demo.showTour && !model.practice { showTour = true }
            split.tab = Demo.startTab
            if Demo.on || model.todoAsOf == nil || Date().timeIntervalSince(model.todoAsOf!) > 120 {
                await model.refreshTodo(quick: true)
            }
            if let id = Demo.startAssignment { openAssignment(id) }
        }
        .onChange(of: phase) { _, p in
            guard p == .active, !model.practice, Date().timeIntervalSince(model.todoAsOf ?? .distantPast) > 600 else { return }
            Task { await model.refreshTodo(quick: true) }
        }
    }

    /// With its class when To Do knows it: the same route its card opens, so the card is tinted.
    private func openAssignment(_ id: String) {
        let section = model.todoItems().first { $0.schoologyId == id }?.sectionId
        split.open(AssignmentRoute(id: id, sectionId: section), tab: 0)
    }

    /// One tab: its screen, and the opened item over its right half (the whole of it, upright).
    private func tab(_ t: Int, pane: CGFloat, upright: Bool) -> some View {
        let open = split.details[t] != nil
        return ZStack(alignment: .trailing) {
            screen(t)
                .environment(\.split, split)
                .padding(.trailing, open && !upright ? pane : 0)
            if open {
                DetailPane(tab: t)
                    .frame(width: pane)
                    .background(Theme.background)
                    .overlay(alignment: .leading) { if !upright { Theme.border.frame(width: 1).ignoresSafeArea() } }
                    .transition(.move(edge: .trailing))
                    .zIndex(1)
            }
        }
    }

    @ViewBuilder
    private func screen(_ t: Int) -> some View {
        switch t {
        case 0: ToDoBoard()
        case 1: GradesView()
        case 2: InboxView()
        case 3: ClassesView()
        default: SchoologyTabView()
        }
    }
}

/// The five tabs as icons over their names, with the tab bar's badges: down the left
/// edge under the mark, or along the bottom when the Duo is upright. Nothing else:
/// each screen has its own gear.
struct WideRail: View {
    @Environment(AppModel.self) private var model
    @Environment(Split.self) private var split
    var upright = false

    static let tabs: [(String, String)] = [
        ("To Do", "checklist"), ("Grades", "chart.bar"), ("Inbox", "tray"),
        ("Classes", "square.grid.2x2"), ("Schoology", "globe"),
    ]

    var body: some View {
        if upright {
            HStack(spacing: 0) {
                ForEach(Array(Self.tabs.enumerated()), id: \.offset) { i, t in
                    tabButton(i, t.0, t.1).frame(maxWidth: .infinity)
                }
            }
            .frame(height: WideShell.barHeight)
            .background(Theme.tagSoft.ignoresSafeArea())
            .overlay(alignment: .top) { Theme.border.frame(height: 1) }
        } else {
            VStack(spacing: Theme.Space.xs) {
                Image("DeweyMark").resizable().scaledToFit().frame(width: 30, height: 30)
                    .frame(width: 52, height: 48)
                    .padding(.bottom, Theme.Space.xs)
                    .accessibilityHidden(true)
                ForEach(Array(Self.tabs.enumerated()), id: \.offset) { i, t in tabButton(i, t.0, t.1) }
            }
            .padding(.vertical, Theme.Space.s)
            .frame(width: WideShell.railWidth)
            .frame(maxHeight: .infinity, alignment: .top)
            .background(Theme.tagSoft.ignoresSafeArea())
            .overlay(alignment: .trailing) { Theme.border.frame(width: 1).ignoresSafeArea().offset(x: 1) }
        }
    }

    /// The tab bar's badge: late work, new grades, new messages.
    static func badge(_ model: AppModel, _ tab: Int) -> String? {
        let n: Int
        switch tab {
        case 0: n = model.overdueCount()
        case 1: n = model.newGradeCount()
        case 2: n = model.inbox.newCount(model.classes)
        default: n = 0
        }
        return n > 0 ? "\(n)" : nil
    }

    private func tabButton(_ i: Int, _ title: String, _ icon: String) -> some View {
        let on = split.tab == i
        let badge = Self.badge(model, i)
        return Button { split.tab = i } label: {
            VStack(spacing: 3) {
                Image(systemName: icon).font(Theme.headline)
                    .foregroundStyle(on ? Theme.onAccent : Theme.accent)
                    .frame(width: 48, height: 30)
                    .background(on ? Theme.accent : .clear, in: Capsule())
                    .overlay(alignment: .topTrailing) {
                        if let badge {
                            // Red means late: only To Do's count is red.
                            Text(badge).font(Theme.caption.weight(.bold).monospacedDigit()).foregroundStyle(.white)
                                .padding(.horizontal, 5)
                                .frame(minWidth: 18, minHeight: 18)
                                .background(i == 0 ? Theme.late : Theme.accent, in: Capsule())
                                .offset(x: 6, y: -5)
                        }
                    }
                Text(title).font(Theme.caption.weight(on ? .semibold : .regular))
                    .foregroundStyle(on ? Theme.text : Theme.secondary)
                    .lineLimit(1).minimumScaleFactor(0.7)
            }
            .frame(width: 68, height: 54)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // Like the system tab bar: the rail keeps its size, and a long press shows it large.
        .dynamicTypeSize(...DynamicTypeSize.xLarge)
        .accessibilityShowsLargeContentViewer { Label(title, systemImage: icon) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([title, badge.map { i == 0 ? "\($0) late" : "\($0) new" }].compactMap { $0 }.joined(separator: ", "))
        .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
    }
}

/// An opened item, over the right half: its own navigation (graded work from a class,
/// a folder inside a class, the assignment a message is about). The X closes it; pages
/// pushed inside it get the usual back arrow.
struct DetailPane: View {
    @Environment(Split.self) private var split
    @State private var path = NavigationPath()
    let tab: Int

    var body: some View {
        let d = split.details[tab]
        NavigationStack(path: $path) {
            content(d)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button { split.close() } label: {
                            Image(systemName: split.upright ? "chevron.backward" : "xmark")
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .accessibilityLabel(split.upright ? "Back" : "Close")
                    }
                }
                .classDestinations()
                .navigationDestination(for: AssignmentLink.self) { AssignmentView(id: $0.id) }
                .navigationDestination(for: Course.self) { CourseGradesView(course: $0) }
                .navigationDestination(for: MessageThread.self) { MessageView(thread: $0) }
                .navigationDestination(for: GradeRoute.self) { GradeDetailView(sectionId: $0.sectionId, itemId: $0.itemId) }
        }
        // A new item starts its own stack: what was pushed under the last one goes.
        .id(d)
        .onChange(of: d) { path = NavigationPath() }
        // Links inside an opened item push here, on its own stack.
        .environment(\.split, nil)
        .environment(\.popFolders) { n, push in
            path.removeLast(min(n, path.count))
            if let push { path.append(push) }
        }
    }

    @ViewBuilder
    private func content(_ d: AnyHashable?) -> some View {
        switch d?.base {
        case let r as AssignmentRoute: AssignmentView(id: r.id, sectionId: r.sectionId)
        case let l as AssignmentLink: AssignmentView(id: l.id)
        case let c as Course: CourseGradesView(course: c)
        case let t as MessageThread: MessageView(thread: t)
        case let g as GradeRoute: GradeDetailView(sectionId: g.sectionId, itemId: g.itemId)
        case let c as ClassPageRoute:
            MaterialsView(sectionId: c.course.sectionId, folderId: nil, title: c.course.shortTitle, trail: [])
        case let f as FolderRoute:
            MaterialsView(sectionId: f.sectionId, folderId: f.folderId, title: f.title, trail: f.trail, trailIds: f.trailIds, onStack: f.onStack)
        case let m as MaterialPageRoute: MaterialView(row: m.row)
        default: Color.clear
        }
    }
}
