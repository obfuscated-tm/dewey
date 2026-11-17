import BackgroundTasks
import os
import SwiftUI
import TipKit

@main
struct DeweyApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var phase

    init() {
        AppLog.note("launch")
        Theme.styleNavigationBars()
        try? Tips.configure()
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.refreshTask, using: nil) { task in
            // Exactly once: when the work ends, or when iOS says time is up (it kills an app that doesn't answer).
            let finished = OSAllocatedUnfairLock(initialState: false)
            let complete = { (ok: Bool) in
                if finished.withLock({ was in defer { was = true }; return !was }) { task.setTaskCompleted(success: ok) }
            }
            let work = Task { @MainActor in
                Self.scheduleRefresh()
                // Signed out: nothing to fetch until the student signs in again.
                guard !Reminders.shared.sessionEnded else { complete(true); return }
                // The app only suspended: refresh its own model (its engine page is in the window,
                // and a second model would race it on the same store). Otherwise a fresh one.
                let m: AppModel
                if let live = AppModel.live {
                    m = live
                } else {
                    m = AppModel()
                    await m.boot()
                }
                if m.session == .signedIn {
                    await m.refreshTodo()
                    if !Task.isCancelled { await m.refreshSomeGrades() }
                    if !Task.isCancelled { await m.inbox.refresh(m, notify: true) }
                }
                await WidgetBridge.publishNow(m)
                complete(!Task.isCancelled)
            }
            task.expirationHandler = {
                work.cancel()
                complete(false)
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
            AppLog.note("app \(p)")
            if p == .active { WidgetBridge.publish(model) }
            if p == .background {
                Self.scheduleRefresh()
                Task { await WebConfig.keepSession() }
            }
        }
    }
}

/// Portrait on a phone-sized screen, as always; any way up on a screen at least
/// 600 pt both ways (the open iPhone Duo).
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        guard let b = window?.windowScene?.screen.bounds else { return .portrait }
        return min(b.width, b.height) >= 600 ? .allButUpsideDown : .portrait
    }
}

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.scenePhase) private var phase
    /// Which layout is showing. It follows the width only while the app is on screen: iOS can
    /// redraw a backgrounded app at other sizes for the app switcher, and swapping layouts then
    /// would rebuild every tab and drop the student's place.
    @State private var wide: Bool?
    @AppStorage(Theme.appearanceKey) private var appearance = "system"

    var body: some View {
        @Bindable var model = model
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
                // The open iPhone Duo (regular width): sidebar, list and detail side by side.
                if wide ?? (sizeClass == .regular) { WideShell() } else { MainTabs() }
            }
        }
        .onChange(of: sizeClass, initial: true) { _, c in
            AppLog.note("width \(c == .regular ? "wide" : "narrow") while \(phase)")
            if phase != .background || wide == nil { wide = c == .regular }
        }
        .onChange(of: wide) { o, n in AppLog.note("layout \(n == true ? "open Duo" : "tabs")\(o == nil ? "" : " (switched)")") }
        .onChange(of: model.session) { _, s in AppLog.note("session \(s)") }
        .onChange(of: phase) { _, p in
            if p == .active { wide = sizeClass == .regular }
        }
        .task { await model.boot() }
        .sheet(item: $model.sheet) { SchoologySheet(target: $0) }
        .onChange(of: model.sheet) { _, t in AppLog.note(t == nil ? "sheet closed" : "sheet open") }
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
        .tint(Theme.text)  // ink: links, toggles, the selected tab; teal is kept for the main action
        .preferredColorScheme(Theme.colorScheme(appearance))
    }
}

struct MainTabs: View {
    @Environment(AppModel.self) private var model
    @State private var tab: Int
    @State private var schoologyURL: URL?
    @AppStorage(TourView.shownKey) private var tourShown = false
    /// The tour's "Take me there" opens a tab fresh: its screens are rebuilt (practice only).
    @State private var resets = [0, 0, 0, 0, 0]
    @State private var showTour = false
    @State private var showID = false
    @Environment(\.scenePhase) private var phase

    /// Starts on the tab the open layout was on (the Duo just closed).
    init() { _tab = State(initialValue: AppModel.live?.tab ?? Demo.startTab) }

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
        .onChange(of: tab, initial: true) { _, t in AppLog.note("tab \(t)"); model.tab = t }
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
                await model.refreshTodo(quick: true)
            }
        }
        // Back from the background: a list older than 10 minutes refreshes by itself.
        .onChange(of: phase) { _, p in
            guard p == .active, !model.practice, Date().timeIntervalSince(model.todoAsOf ?? .distantPast) > 600 else { return }
            Task { await model.refreshTodo(quick: true) }
        }
    }
}
