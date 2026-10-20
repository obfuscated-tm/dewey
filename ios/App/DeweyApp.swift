import BackgroundTasks
import SwiftUI

@main
struct DeweyApp: App {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var phase

    init() {
        Theme.styleNavigationBars()
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.refreshTask, using: nil) { task in
            Task { @MainActor in
                Self.scheduleRefresh()
                // Signed out: nothing to fetch until the student signs in again.
                guard !Reminders.shared.sessionEnded else { task.setTaskCompleted(success: true); return }
                // The model lives in the scene; a background launch refreshes from a fresh one.
                let m = AppModel()
                await m.boot()
                if m.session == .signedIn {
                    await m.refreshTodo()
                    await m.refreshSomeGrades()
                    await m.inbox.refresh(m, notify: true)
                }
                await WidgetBridge.publishNow(m)
                task.setTaskCompleted(success: true)
            }
        }
    }

    static let refreshTask = "com.owenleung.classwork.refresh"

    static func scheduleRefresh() {
        let r = BGAppRefreshTaskRequest(identifier: refreshTask)
        r.earliestBeginDate = Date().addingTimeInterval(2 * 3600)
        try? BGTaskScheduler.shared.submit(r)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .modelContainer(model.container)
                .onAppear { AppModel.live = model }
        }
        .onChange(of: phase) { _, p in
            if p == .active { WidgetBridge.publish(model) }
            if p == .background {
                Self.scheduleRefresh()
                Task { await WebConfig.keepSession() }
            }
        }
    }
}

struct RootView: View {
    @Environment(AppModel.self) private var model
    @AppStorage(Theme.appearanceKey) private var appearance = "system"

    var body: some View {
        Group {
            switch model.session {
            case .checking:
                ZStack {
                    Theme.background.ignoresSafeArea()
                    ProgressView()
                }
            case .signedOut:
                SignInView()
            case .signedIn:
                MainTabs()
            }
        }
        .task { await model.boot() }
        .overlay(alignment: .bottom) {
            if let t = model.toast {
                Text(t)
                    .font(Theme.subhead.weight(.medium))
                    .foregroundStyle(Theme.card)
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    .background(Theme.text, in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
                    .padding(.bottom, 90).padding(.horizontal, 20)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut, value: model.toast)
        .tint(Theme.accent)
        .preferredColorScheme(Theme.colorScheme(appearance))
    }
}

struct MainTabs: View {
    @Environment(AppModel.self) private var model
    @State private var tab = Demo.startTab
    @State private var schoologyURL: URL?
    @AppStorage(TourView.shownKey) private var tourShown = false
    /// The tour's "Take me there" opens a tab fresh: its screens are rebuilt (practice only).
    @State private var resets = [0, 0, 0, 0, 0]
    @State private var showTour = false
    @State private var showID = false

    var body: some View {
        TabView(selection: $tab) {
            ToDoView()
                .id(resets[0])
                .tabItem { Label("To Do", systemImage: "checklist") }
                .badge(model.overdueCount())
                .tag(0)
            GradesView()
                .id(resets[1])
                .tabItem { Label("Grades", systemImage: "chart.bar") }
                .badge(model.newGradeCount())
                .tag(1)
            InboxView()
                .id(resets[2])
                .tabItem { Label("Inbox", systemImage: "tray") }
                .badge(model.inbox.newCount(model.classes))
                .tag(2)
            ClassesView()
                .id(resets[3])
                .tabItem { Label("Classes", systemImage: "square.grid.2x2") }
                .tag(3)
            SchoologyTabView()
                .id(resets[4])
                .tabItem { Label("Schoology", systemImage: "globe") }
                .tag(4)
        }
        .onReceive(NotificationCenter.default.publisher(for: .openTodo)) { _ in tab = 0 }
        .onReceive(NotificationCenter.default.publisher(for: .showStudentID)) { _ in showID = true }
        .fullScreenCover(isPresented: $showID) { StudentIDCard() }
        .onOpenURL { url in
            if AppLink.isStudentID(url) { showID = true; return }
            tab = 0
            if let id = AppLink.assignmentId(url) { model.deepLinkAssignment = id }
        }
        .fullScreenCover(isPresented: $showTour) { TourView() }
        .onChange(of: Coach.shared.jump) {
            guard model.practice else { return }
            let t = Coach.shared.jumpTab
            resets[t] += 1
            tab = t
        }
        .task {
            if !tourShown && Demo.showTour && !model.practice { showTour = true }
            if let id = Demo.startAssignment { model.deepLinkAssignment = id }
            // Demo rows live only in memory, so a quick relaunch has none to show yet.
            if Demo.on || model.todoAsOf == nil || Date().timeIntervalSince(model.todoAsOf!) > 120 {
                await model.refreshTodo()
            }
        }
    }
}
