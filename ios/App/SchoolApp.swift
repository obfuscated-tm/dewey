import BackgroundTasks
import SwiftUI

@main
struct SchoolApp: App {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var phase

    init() {
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
                .tint(Theme.accent)
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
                    .font(Theme.font(14, .medium))
                    .foregroundStyle(Theme.card)
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    .background(Theme.text, in: RoundedRectangle(cornerRadius: 12))
                    .padding(.bottom, 90).padding(.horizontal, 20)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut, value: model.toast)
        .preferredColorScheme(Theme.colorScheme(appearance))
    }
}

struct MainTabs: View {
    @Environment(AppModel.self) private var model
    @State private var tab = 0
    @State private var schoologyURL: URL?

    var body: some View {
        TabView(selection: $tab) {
            ToDoView()
                .tabItem { Label("To Do", systemImage: "checklist") }
                .badge(model.overdueCount())
                .tag(0)
            GradesView()
                .tabItem { Label("Grades", systemImage: "chart.bar") }
                .tag(1)
            InboxView()
                .tabItem { Label("Inbox", systemImage: "tray") }
                .badge(model.inbox.newCount(model.classes))
                .tag(2)
            ClassesView()
                .tabItem { Label("Classes", systemImage: "square.grid.2x2") }
                .tag(3)
            SchoologyTabView()
                .tabItem { Label("Schoology", systemImage: "globe") }
                .tag(4)
        }
        .onOpenURL { url in
            tab = 0
            if let id = AppLink.assignmentId(url) { model.deepLinkAssignment = id }
        }
        .task {
            if model.todoAsOf == nil || Date().timeIntervalSince(model.todoAsOf!) > 120 {
                await model.refreshTodo()
            }
        }
    }
}
