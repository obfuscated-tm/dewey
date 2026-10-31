import SwiftUI

/// iPhone Duo, open (docs/ios/design/Duo.html): a regular-width window gets a rail of
/// tab icons instead of the tab bar, and To Do, Grades and Inbox show their list and
/// the open item side by side. Chosen by size class, never by "is it open", so the
/// folded screen and a narrow split keep the phone layout (MainTabs).
@Observable final class Split {
    /// 0 To Do · 1 Grades · 2 Inbox · 3 Classes · 4 Schoology (MainTabs' tags).
    var tab = 0
    /// What each tab's right pane shows: an AssignmentRoute, Course, MessageThread,
    /// AssignmentLink or GradeRoute. None: the tab's own pane (Plan, the quiet Inbox).
    var details: [Int: AnyHashable] = [:]
    /// To Do shows only this class (a class in the rail or sidebar).
    var classFilter: String?
    /// The full sidebar (names, counts, classes), slid over the list from the rail.
    var sidebar = false
    /// Grades' open class is Dewey's pick (the newest grade), not one the student tapped.
    var gradeAuto = false

    var detail: AnyHashable? { details[tab] }

    func open(_ v: AnyHashable, tab t: Int? = nil) {
        if let t { tab = t }
        details[t ?? tab] = v
        if (t ?? tab) == 1 { gradeAuto = false }
    }

    func close() { details[tab] = nil }

    func isOpen(_ v: AnyHashable) -> Bool { details[tab] == v }

    /// A tab or class picked in the rail or sidebar: To Do filters by the class,
    /// Grades opens it; the sidebar slides away.
    func pick(tab t: Int? = nil, course: Course? = nil) {
        if let t {
            if tab == t && t == 0 { classFilter = nil }
            tab = t
        }
        if let course {
            if tab == 1 {
                open(course)
            } else {
                tab = 0
                classFilter = classFilter == course.sectionId ? nil : course.sectionId
            }
        }
        withAnimation(.snappy(duration: 0.25)) { sidebar = false }
    }
}

private struct SplitKey: EnvironmentKey { static let defaultValue: Split? = nil }

extension EnvironmentValues {
    /// Set inside WideShell only: list rows open their item in the right pane instead of pushing it.
    var split: Split? {
        get { self[SplitKey.self] }
        set { self[SplitKey.self] = newValue }
    }
}

/// A NavigationLink on the phone; on the open Duo, a button that opens `value` in the
/// right pane, tinted while it's the one open. `inset`: how far the tint reaches past
/// the row (0 inside a card; nil for no tint, where something else marks it).
struct SplitLink<Value: Hashable, Label: View>: View {
    @Environment(\.split) private var split
    let value: Value
    var inset: CGFloat?
    @ViewBuilder var label: Label

    init(value: Value, inset: CGFloat? = -8, @ViewBuilder label: () -> Label) {
        self.value = value
        self.inset = inset
        self.label = label()
    }

    var body: some View {
        if let split {
            Button { split.open(value) } label: {
                label.background { if let inset, split.isOpen(value) { SelectedRow(inset: inset) } }
            }
            .accessibilityAddTraits(split.isOpen(value) ? .isSelected : [])
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

    var body: some View {
        GeometryReader { geo in
            // Rail + list fill the left half and the detail the right, so nothing sits on the hinge.
            let list = max(300, geo.size.width / 2 - Self.railWidth - 1)
            HStack(spacing: 0) {
                WideRail().frame(width: Self.railWidth)
                Theme.border.frame(width: 1).ignoresSafeArea()
                ZStack {
                    ForEach(0..<5, id: \.self) { t in
                        if built.contains(t) {
                            pane(t, list: list)
                                .opacity(split.tab == t ? 1 : 0)
                                .allowsHitTesting(split.tab == t)
                                .accessibilityHidden(split.tab != t)
                        }
                    }
                }
            }
            .overlay(alignment: .leading) {
                if split.sidebar {
                    ZStack(alignment: .leading) {
                        Color.black.opacity(0.18).ignoresSafeArea()
                            .onTapGesture { withAnimation(.snappy(duration: 0.25)) { split.sidebar = false } }
                            .accessibilityHidden(true)
                        WideSidebar()
                            .frame(width: 220)
                            .shadow(color: .black.opacity(0.15), radius: 12, x: 4)
                            .transition(.move(edge: .leading))
                    }
                }
            }
        }
        .background(Theme.background)
        .environment(\.split, split)
        .environment(split)
        .onChange(of: split.tab, initial: true) { _, t in built.insert(t) }
        .onReceive(NotificationCenter.default.publisher(for: .openTodo)) { _ in split.tab = 0 }
        .onReceive(NotificationCenter.default.publisher(for: .showStudentID)) { _ in showID = true }
        .fullScreenCover(isPresented: $showID) { StudentIDCard() }
        .fullScreenCover(isPresented: $showTour) { TourView() }
        .onOpenURL { url in
            if AppLink.isStudentID(url) { showID = true; return }
            split.tab = 0
            if let id = AppLink.assignmentId(url) { split.open(AssignmentRoute(id: id), tab: 0) }
        }
        // A deep link set elsewhere (a notification, Siri) opens on the right.
        .onChange(of: model.deepLinkAssignment, initial: true) { _, id in
            guard let id else { return }
            split.open(AssignmentRoute(id: id), tab: 0)
            model.deepLinkAssignment = nil
        }
        .task {
            if !tourShown && Demo.showTour && !model.practice { showTour = true }
            split.tab = Demo.startTab
            if let id = Demo.startAssignment { split.open(AssignmentRoute(id: id), tab: 0) }
            if Demo.on || model.todoAsOf == nil || Date().timeIntervalSince(model.todoAsOf!) > 120 {
                await model.refreshTodo(quick: true)
            }
        }
        .onChange(of: phase) { _, p in
            guard p == .active, !model.practice, Date().timeIntervalSince(model.todoAsOf ?? .distantPast) > 600 else { return }
            Task { await model.refreshTodo(quick: true) }
        }
    }

    @ViewBuilder
    private func pane(_ t: Int, list: CGFloat) -> some View {
        switch t {
        case 0, 1, 2:
            HStack(spacing: 0) {
                SwiftUI.Group {
                    if t == 0 { ToDoView() } else if t == 1 { GradesView() } else { InboxView() }
                }
                .frame(width: list)
                Theme.border.frame(width: 1).ignoresSafeArea()
                DetailPane(tab: t)
                    .frame(maxWidth: .infinity)
            }
        case 3: ClassesView()
        default: SchoologyTabView()
        }
    }
}

/// The five tabs as icons over their names, with the tab bar's badges, then a dot
/// per class (filters To Do, opens it on Grades), Settings at the bottom. The mark
/// at the top slides the full sidebar over the list.
struct WideRail: View {
    @Environment(AppModel.self) private var model
    @Environment(Split.self) private var split
    @State private var showSettings = false
    @State private var settingsPath: [SettingsPage] = []

    static let tabs: [(String, String)] = [
        ("To Do", "checklist"), ("Grades", "chart.bar"), ("Inbox", "tray"),
        ("Classes", "square.grid.2x2"), ("Schoology", "globe"),
    ]

    var body: some View {
        VStack(spacing: Theme.Space.xs) {
            Button { withAnimation(.snappy(duration: 0.25)) { split.sidebar = true } } label: {
                Image("DeweyMark").resizable().scaledToFit().frame(width: 30, height: 30)
                    .frame(width: 52, height: 48)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Show sidebar")
            .accessibilityHint("Tab and class names with their counts")
            .padding(.bottom, Theme.Space.xs)
            ForEach(Array(Self.tabs.enumerated()), id: \.offset) { i, t in tabButton(i, t.0, t.1) }
            Theme.divider.frame(width: 32, height: 1).padding(.vertical, Theme.Space.s)
            ScrollView {
                VStack(spacing: 2) {
                    ForEach(model.classes) { c in classDot(c) }
                }
            }
            .scrollBounceBehavior(.basedOnSize)
            Button { settingsPath = []; showSettings = true } label: {
                Image(systemName: "gearshape").font(Theme.title.weight(.regular)).foregroundStyle(Theme.secondary)
                    .frame(width: 52, height: 48)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Settings")
        }
        .padding(.vertical, Theme.Space.s)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(Theme.tagSoft.ignoresSafeArea())
        .sheet(isPresented: $showSettings) { SettingsView(path: $settingsPath) }
    }

    private func tabButton(_ i: Int, _ title: String, _ icon: String) -> some View {
        let on = split.tab == i
        let badge = WideSidebar.badge(model, i)
        return Button { split.pick(tab: i) } label: {
            VStack(spacing: 3) {
                Image(systemName: icon).font(Theme.headline)
                    .foregroundStyle(on ? Theme.onAccent : Theme.accent)
                    .frame(width: 48, height: 30)
                    .background(on ? Theme.accent : .clear, in: Capsule())
                    .overlay(alignment: .topTrailing) {
                        if let badge {
                            Text(badge).font(Theme.caption.weight(.bold).monospacedDigit()).foregroundStyle(.white)
                                .padding(.horizontal, 5)
                                .frame(minWidth: 18, minHeight: 18)
                                .background(i == 1 ? Theme.accent : Theme.late, in: Capsule())
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
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([title, badge.map { i == 1 ? "\($0) new" : $0 }].compactMap { $0 }.joined(separator: ", "))
        .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
    }

    private func classDot(_ c: Course) -> some View {
        let on = WideSidebar.isPicked(split, c)
        let color = model.classColor(c.sectionId)
        // The report card's short name ("AP BIOL"): a colour alone doesn't say which class.
        return Button { split.pick(course: c) } label: {
            HStack(spacing: 4) {
                Circle().fill(color).frame(width: 7, height: 7)
                Text(GradesLook.abbreviation(c.shortTitle))
                    .font(Theme.caption.weight(on ? .bold : .semibold))
                    .foregroundStyle(on ? color : Theme.secondary)
                    .lineLimit(1).minimumScaleFactor(0.7)
            }
            .padding(.horizontal, 5)
            .frame(width: 66, height: 30)
            .background(on ? color.opacity(0.15) : .clear, in: Capsule())
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(c.shortTitle)
        .accessibilityHint(split.tab == 1 ? "Opens its grades" : "Shows only this class on To Do")
        .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
    }
}

/// The full sidebar, slid over the list from the rail: the five tabs with their
/// counts, then the classes with what's open in each.
struct WideSidebar: View {
    @Environment(AppModel.self) private var model
    @Environment(Split.self) private var split

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

    static func isPicked(_ split: Split, _ c: Course) -> Bool {
        split.tab == 0 && split.classFilter == c.sectionId || split.tab == 1 && split.detail == AnyHashable(c)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: Theme.Space.s) {
                    Image("DeweyMark").resizable().scaledToFit().frame(width: 26, height: 26).accessibilityHidden(true)
                    Text("Dewey").font(Theme.displaySmall).foregroundStyle(Theme.text)
                    Spacer(minLength: 0)
                    Button { withAnimation(.snappy(duration: 0.25)) { split.sidebar = false } } label: {
                        Image(systemName: "sidebar.left").font(Theme.body).foregroundStyle(Theme.secondary)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Hide sidebar")
                }
                .padding(.leading, Theme.Space.s)
                .padding(.bottom, Theme.Space.s)
                ForEach(Array(WideRail.tabs.enumerated()), id: \.offset) { i, t in navRow(i, t.0, t.1) }
                Text("CLASSES").font(Theme.caption.weight(.semibold)).tracking(0.6).foregroundStyle(Theme.tertiary)
                    .padding(.horizontal, 10).padding(.top, Theme.Space.m).padding(.bottom, Theme.Space.xs)
                    .accessibilityAddTraits(.isHeader)
                ForEach(model.classes) { c in classRow(c) }
            }
            .padding(.horizontal, Theme.Space.s)
            .padding(.top, Theme.Space.s)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(Theme.tagSoft.ignoresSafeArea())
    }

    private func navRow(_ i: Int, _ title: String, _ icon: String) -> some View {
        let on = split.tab == i
        return Button { split.pick(tab: i) } label: {
            HStack(spacing: 10) {
                Image(systemName: icon).font(Theme.body).frame(width: 22)
                    .foregroundStyle(on ? Theme.onAccent : Theme.accent)
                Text(title).font(Theme.subhead.weight(.medium)).oneLine()
                Spacer(minLength: 0)
                if let b = Self.badge(model, i) {
                    if i == 1 {
                        Text("\(b) new").font(Theme.caption.weight(.semibold).monospacedDigit())
                            .foregroundStyle(on ? Theme.onAccent.opacity(0.85) : Theme.tertiary)
                    } else {
                        Text(b).font(Theme.caption.weight(.bold).monospacedDigit()).foregroundStyle(.white)
                            .padding(.horizontal, 7).padding(.vertical, 1)
                            .background(Theme.late, in: Capsule())
                    }
                }
            }
            .foregroundStyle(on ? Theme.onAccent : Theme.text)
            .padding(.horizontal, 10)
            .frame(minHeight: 40)
            .background(on ? Theme.accent : .clear, in: RoundedRectangle(cornerRadius: Theme.Radius.card - 4, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func classRow(_ c: Course) -> some View {
        let open = model.openWork(c.sectionId).count
        let on = Self.isPicked(split, c)
        return Button { split.pick(course: c) } label: {
            HStack(spacing: 10) {
                Circle().fill(model.classColor(c.sectionId)).frame(width: 9, height: 9)
                Text(c.shortTitle).font(Theme.subhead).foregroundStyle(on ? Theme.text : Theme.secondary).oneLine()
                Spacer(minLength: 0)
                if open > 0 {
                    Text("\(open)").font(Theme.caption.monospacedDigit()).foregroundStyle(Theme.tertiary)
                }
            }
            .padding(.horizontal, 10)
            .frame(minHeight: 34)
            .background(on ? Theme.accentSoft : .clear, in: RoundedRectangle(cornerRadius: Theme.Radius.tag + 2, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(c.shortTitle), \(open) open")
        .accessibilityHint(split.tab == 1 ? "Opens its grades" : "Shows only this class on To Do")
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// The right half: the open item, with its own navigation (graded work from a
/// class, the assignment a message is about), or the tab's own pane: Plan on
/// To Do, the class with the newest grade on Grades, the quiet Inbox.
struct DetailPane: View {
    @Environment(AppModel.self) private var model
    @Environment(Split.self) private var split
    @AppStorage(GradesLook.hiddenKey) private var hiddenRaw = ""
    let tab: Int

    var body: some View {
        let d = split.details[tab]
        NavigationStack {
            content(d)
                .toolbar {
                    // Back to Plan / the quiet Inbox. Grades always has a class open.
                    if d != nil && tab != 1 {
                        ToolbarItem(placement: .topBarLeading) {
                            Button { split.close() } label: {
                                Label(tab == 0 ? "Plan" : "Inbox", systemImage: "chevron.backward").labelStyle(.titleAndIcon)
                            }
                        }
                    }
                }
                .navigationDestination(for: AssignmentRoute.self) { AssignmentView(id: $0.id, sectionId: $0.sectionId) }
                .navigationDestination(for: AssignmentLink.self) { AssignmentView(id: $0.id) }
                .navigationDestination(for: Course.self) { CourseGradesView(course: $0) }
                .navigationDestination(for: MessageThread.self) { MessageView(thread: $0) }
                .navigationDestination(for: GradeRoute.self) { GradeDetailView(sectionId: $0.sectionId, itemId: $0.itemId) }
        }
        // A new item starts its own stack: what was pushed under the last one goes.
        .id(d)
        .onChange(of: tab == 1 ? NewestGradeClass.pick(model, hidden: hiddenRaw)?.sectionId : nil, initial: true) { _, _ in
            guard tab == 1, split.details[1] == nil || split.gradeAuto,
                  let c = NewestGradeClass.pick(model, hidden: hiddenRaw) else { return }
            split.details[1] = c
            // A class opened for a new grade stays put (seeing the grade mustn't move it).
            split.gradeAuto = !model.newGrades().contains { $0.0.sectionId == c.sectionId }
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
        default:
            switch tab {
            case 0: PlanPane().toolbar(.hidden, for: .navigationBar)
            case 1: NewestGradeClass()
            default: InboxPane().toolbar(.hidden, for: .navigationBar)
            }
        }
    }
}
