import SwiftUI
import UserNotifications

/// A page in Settings. The gear on To Do, Grades and Classes opens Settings on its own page.
enum SettingsPage: Hashable {
    case schoology, notifications, todo, grades, classes, inbox, help, about
}

/// 4b · A short list of pages: Schoology, Notifications, one page per tab
/// (To Do, Grades, Classes, Inbox), Help and About, plus the Appearance picker.
/// Each row shows its current value.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    /// Held by the gear, not here: changing Appearance rebuilds the sheet's contents,
    /// and a path kept in this view would jump back to the start page.
    @Binding var path: [SettingsPage]
    @State private var connected = NeoPlanClient().connected
    @State private var confirmSignOut = false
    @AppStorage(Theme.appearanceKey) private var appearance = "system"
    @AppStorage(ToDoLayout.key) private var todoLayout = ToDoLayout.week.rawValue
    @AppStorage(GradesLook.reportCardKey) private var reportCard = true
    @AppStorage(Shelf.offKey) private var offShelf = ""
    @AppStorage(Reminders.enabledKey) private var reminders = false
    @AppStorage(Reminders.soonKey) private var dueSoon = true
    @AppStorage(Reminders.testNightKey) private var testNight = true
    @AppStorage(Reminders.morningKey) private var morning = false
    @AppStorage(Reminders.gradesKey) private var gradeAlerts = true
    @AppStorage(Reminders.signedOutKey) private var signedOutAlert = true
    @AppStorage(Reminders.messagesKey) private var messageAlerts = true
    @AppStorage(Reminders.materialsKey) private var materialAlerts = true

    var body: some View {
        NavigationStack(path: $path) {
            Form {
                Section {
                    row(.schoology, "graduationcap", 5, "Schoology", subtitle: Engine.host)
                }
                Section {
                    row(.notifications, "bell", 3, "Notifications", value: notificationsOn ? "On" : "Off")
                    Picker(selection: $appearance) {
                        Text("System").tag("system")
                        Text("Light").tag("light")
                        Text("Dark").tag("dark")
                    } label: {
                        HStack(spacing: Theme.Space.m) {
                            icon("circle.lefthalf.filled", 2)
                            Text("Appearance")
                        }
                    }
                    .tint(Theme.secondary)
                }
                Section {
                    row(.todo, "checklist", 1, "To Do", value: (ToDoLayout(rawValue: todoLayout) ?? .week).title)
                    row(.grades, "chart.bar", 0, "Grades", value: reportCard ? "Letters" : "List")
                    row(.classes, "books.vertical", 4, "Classes", value: "\(onShelf) on shelf")
                    row(.inbox, "envelope", 7, "Inbox")
                }
                Section {
                    row(.help, "questionmark", 6, "Help", value: connected ? "neo-plan on" : nil)
                    row(.about, "info", 6, "About", value: BuildInfo.daysLeftText)
                }
            }
            .navigationDestination(for: SettingsPage.self) { page($0) }
            .preferredColorScheme(Theme.colorScheme(appearance))
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { done }
            .confirmationDialog("Sign out of Schoology?", isPresented: $confirmSignOut, titleVisibility: .visible) {
                Button("Sign out", role: .destructive) {
                    dismiss()
                    Task { await model.signOut() }
                }
            } message: {
                Text("Clears the Schoology session and saved pages. Your Done marks stay.")
            }
        }
    }

    private var done: some ToolbarContent {
        ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
    }

    @ViewBuilder
    private func page(_ p: SettingsPage) -> some View {
        Group {
            switch p {
            case .schoology: SchoologySettings(confirmSignOut: $confirmSignOut)
            case .notifications: NotificationSettings()
            case .todo: ToDoSettings()
            case .grades: GradesSettings()
            case .classes: ShelfSettings(title: "Classes")
            case .inbox: InboxSettings()
            case .help: HelpSettings(connected: $connected)
            case .about: AboutSettings()
            }
        }
        .toolbar { done }
    }

    /// An iOS Settings-style row: a white symbol on a class-colour square, the title, and its value.
    private func row(_ p: SettingsPage, _ symbol: String, _ color: Int, _ title: String,
                     value: String? = nil, subtitle: String? = nil) -> some View {
        NavigationLink(value: p) {
            HStack(spacing: Theme.Space.m) {
                icon(symbol, color)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title)
                    if let subtitle { Text(subtitle).font(Theme.caption).foregroundStyle(Theme.secondary) }
                }
                Spacer()
                if let value { Text(value).foregroundStyle(Theme.secondary) }
            }
        }
    }

    private func icon(_ symbol: String, _ color: Int) -> some View {
        Image(systemName: symbol)
            .font(Theme.footnote.weight(.semibold)).foregroundStyle(.white)
            .frame(width: 29, height: 29)
            .background(Theme.classColor(color), in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
            .accessibilityHidden(true)
    }

    private var notificationsOn: Bool {
        [reminders, dueSoon, testNight, morning, gradeAlerts, signedOutAlert, messageAlerts, materialAlerts].contains(true)
    }

    private var onShelf: Int {
        let off = Shelf.off(offShelf)
        return model.classes.filter { !off.contains($0.sectionId) }.count
    }
}

/// Reminder switches shared by Settings → Notifications and Settings → To Do.
@MainActor
enum ReminderSettings {
    static func hourText(_ h: Int) -> String {
        Fmt.time.string(from: Calendar.current.date(bySettingHour: h, minute: 0, second: 0, of: Date())!)
    }

    /// A due-reminder switch or time changed: ask for permission when turning one on, then reschedule.
    static func dueChanged(_ model: AppModel, _ on: Bool) {
        Task {
            if on { _ = await Reminders.shared.requestPermission() }
            Reminders.shared.schedule(items: model.todoItems(), courses: model.courses)
        }
    }

    static func askIfOn(_ on: Bool) {
        if on { Task { _ = await Reminders.shared.requestPermission() } }
    }
}

private struct SchoologySettings: View {
    @Environment(AppModel.self) private var model
    @Binding var confirmSignOut: Bool
    @AppStorage(WebConfig.compatKey) private var compat = false

    var body: some View {
        Form {
            Section {
                LabeledContent("Signed in to", value: Engine.host)
                Button("Sign out", role: .destructive) { confirmSignOut = true }
            }
            Section {
                Toggle("Login compatibility mode", isOn: $compat)
                    .onChange(of: compat) { _, _ in model.engine.reload() }
            } footer: {
                Text("Signs in with Safari's user agent. Turns on by itself if Google refuses the app's web view.")
            }
        }
        .navigationTitle("Schoology")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct NotificationSettings: View {
    @Environment(AppModel.self) private var model
    @AppStorage(Reminders.enabledKey) private var reminders = false
    @AppStorage(Reminders.hourKey) private var hour = 19
    @AppStorage(Reminders.soonKey) private var dueSoon = true
    @AppStorage(Reminders.testNightKey) private var testNight = true
    @AppStorage(Reminders.morningKey) private var morning = false
    @AppStorage(Reminders.morningHourKey) private var morningHour = 7
    @AppStorage(Reminders.gradesKey) private var gradeAlerts = true
    @AppStorage(Reminders.signedOutKey) private var signedOutAlert = true
    @AppStorage(Reminders.messagesKey) private var messageAlerts = true
    @AppStorage(Reminders.materialsKey) private var materialAlerts = true

    var body: some View {
        Form {
            Section {
                Toggle("2 hours before, if not submitted", isOn: $dueSoon)
                    .onChange(of: dueSoon) { _, on in ReminderSettings.dueChanged(model, on) }
                Toggle("Night before a test", isOn: $testNight)
                    .onChange(of: testNight) { _, on in ReminderSettings.dueChanged(model, on) }
                Toggle("Homework the evening before", isOn: $reminders)
                    .onChange(of: reminders) { _, on in ReminderSettings.dueChanged(model, on) }
                if reminders || testNight {
                    Picker("Evening time", selection: $hour) {
                        ForEach(15..<23) { h in Text(ReminderSettings.hourText(h)).tag(h) }
                    }
                    .onChange(of: hour) { _, _ in ReminderSettings.dueChanged(model, false) }
                }
                Toggle("Morning summary", isOn: $morning)
                    .onChange(of: morning) { _, on in ReminderSettings.dueChanged(model, on) }
                if morning {
                    Picker("Morning time", selection: $morningHour) {
                        ForEach(5..<11) { h in Text(ReminderSettings.hourText(h)).tag(h) }
                    }
                    .onChange(of: morningHour) { _, _ in ReminderSettings.dueChanged(model, false) }
                }
            } header: { Text("Due work") } footer: {
                Text("Each stops once the work is done, turned in or (for a test) studied.")
            }

            Section {
                Toggle("New grades", isOn: $gradeAlerts)
                    .onChange(of: gradeAlerts) { _, on in ReminderSettings.askIfOn(on) }
                Toggle("Messages from teachers", isOn: $messageAlerts)
                    .onChange(of: messageAlerts) { _, on in ReminderSettings.askIfOn(on) }
                Toggle("New materials in your classes", isOn: $materialAlerts)
                    .onChange(of: materialAlerts) { _, on in ReminderSettings.askIfOn(on) }
                Toggle("Signed out of Schoology", isOn: $signedOutAlert)
                    .onChange(of: signedOutAlert) { _, on in ReminderSettings.askIfOn(on) }
            } header: { Text("Alerts") } footer: {
                Text("These come when the app refreshes in the background, so they can be late.")
            }
        }
        .navigationTitle("Notifications")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// The To Do gear lands here: view, call numbers, and the two reminders about due work.
private struct ToDoSettings: View {
    @Environment(AppModel.self) private var model
    @AppStorage(ToDoLayout.key) private var todoLayout = ToDoLayout.week.rawValue
    @AppStorage(Reminders.soonKey) private var dueSoon = true
    @AppStorage(Reminders.testNightKey) private var testNight = true

    var body: some View {
        Form {
            Section {
                Picker("View", selection: $todoLayout) {
                    ForEach(ToDoLayout.allCases, id: \.self) { Text($0.title).tag($0.rawValue) }
                }
                .pickerStyle(.segmented)
                NavigationLink("Call numbers") { CallNumberSettings() }
            } footer: {
                Text(todoLayout == ToDoLayout.urgent.rawValue
                     ? "One list, late and due today at the top."
                     : "Work grouped by class, under a strip of days.")
            }
            Section {
                Toggle("2 hours before, if not submitted", isOn: $dueSoon)
                    .onChange(of: dueSoon) { _, on in ReminderSettings.dueChanged(model, on) }
                Toggle("Night before a test", isOn: $testNight)
                    .onChange(of: testNight) { _, on in ReminderSettings.dueChanged(model, on) }
            } header: { Text("Reminders") } footer: {
                Text("More in Settings › Notifications.")
            }
        }
        .navigationTitle("To Do")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct GradesSettings: View {
    @AppStorage(GradesLook.reportCardKey) private var reportCard = true

    var body: some View {
        Form {
            Section {
                Toggle("Letters across the top", isOn: $reportCard)
            } footer: {
                Text(reportCard ? "Grades starts with every class's letter in one row, like a report card."
                                : "Grades starts straight with the list; each class shows its letter.")
            }
        }
        .navigationTitle("Grades")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct InboxSettings: View {
    @AppStorage(Inbox.markReadKey) private var markRead = true

    var body: some View {
        Form {
            Section {
                Toggle("Mark messages read when opened", isOn: $markRead)
            } footer: {
                Text(markRead ? "Opening a message here marks it read on Schoology."
                              : "Messages stay unread on Schoology until you open them there.")
            }
        }
        .navigationTitle("Inbox")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct HelpSettings: View {
    @Binding var connected: Bool

    var body: some View {
        Form {
            Section {
                NavigationLink("How statuses work") { StatusGuideView() }
                NavigationLink { NeoPlanView(connected: $connected) } label: {
                    LabeledContent("neo-plan", value: connected ? "Connected" : "Not connected")
                }
            } footer: {
                Text(connected ? NeoPlanView.statusText() : "neo-plan is an optional planner board. Most people don't need it.")
            }
        }
        .navigationTitle("Help")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct AboutSettings: View {
    var body: some View {
        Form {
            Section {
                LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")
                LabeledContent("Expires", value: BuildInfo.expiryText)
            } footer: {
                Text("A free-account build stops opening on this date; build it again from Xcode.")
            }
            Section {
                NavigationLink("Developer") { DeveloperView() }
            }
        }
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// When a free-account build stops opening: the provisioning profile's ExpirationDate.
enum BuildInfo {
    static var expiry: Date? {
        guard let url = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
              let data = try? Data(contentsOf: url),
              let s = String(data: data, encoding: .isoLatin1),
              let start = s.range(of: "<?xml"), let end = s.range(of: "</plist>") else { return nil }
        let xml = Data(s[start.lowerBound..<end.upperBound].utf8)
        let plist = try? PropertyListSerialization.propertyList(from: xml, format: nil) as? [String: Any]
        return plist?["ExpirationDate"] as? Date
    }

    static var expiryText: String {
        guard let d = expiry else { return "No profile (Simulator)" }
        let days = Calendar.current.dateComponents([.day], from: Date(), to: d).day ?? 0
        return "\(Fmt.short.string(from: d)) (\(days) day\(days == 1 ? "" : "s"))"
    }

    /// "5 days" for the About row; nothing in the Simulator.
    static var daysLeftText: String? {
        guard let d = expiry else { return nil }
        let days = Calendar.current.dateComponents([.day], from: Date(), to: d).day ?? 0
        return "\(days) day\(days == 1 ? "" : "s")"
    }
}

/// Run any engine op and see its JSON (M1/M2 checks).
struct DeveloperView: View {
    @Environment(AppModel.self) private var model
    @State private var op = "courses"
    @State private var args = "{}"
    @State private var output = ""
    @State private var running = false
    @State private var scheduled: [UNNotificationRequest] = []

    static let examples: [String: String] = [
        "courses": "{}",
        "todo": "{}",
        "status": #"{"id":""}"#,
        "assignment": #"{"id":""}"#,
        "gradebook": #"{"sectionId":""}"#,
        "dropbox": #"{"url":"/assignment/ID/dropbox/view/N"}"#,
        "rubric": #"{"rubricId":"","enrollmentId":"","itemId":""}"#,
        "materials": #"{"sectionId":""}"#,
        "material": #"{"path":"/page/ID"}"#,
        "feed": #"{"sectionId":"","page":0}"#,
        "notifications": #"{"page":0}"#,
        "messages": #"{"page":0}"#,
    ]

    var body: some View {
        Form {
            Section("Scheduled reminders (\(scheduled.count))") {
                ForEach(scheduled, id: \.identifier) { r in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(r.content.title).font(Theme.subhead.weight(.semibold))
                        if let d = (r.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate() {
                            Text(Fmt.dayTime.string(from: d)).font(Theme.caption.monospacedDigit()).foregroundStyle(Theme.secondary)
                        }
                    }
                }
            }
            .task {
                scheduled = await UNUserNotificationCenter.current().pendingNotificationRequests()
                    .sorted { ($0.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate() ?? .distantFuture
                        < ($1.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate() ?? .distantFuture }
            }
            Section {
                Picker("Op", selection: $op) {
                    ForEach(Self.examples.keys.sorted(), id: \.self) { Text($0).tag($0) }
                }
                .onChange(of: op) { _, o in args = fill(Self.examples[o] ?? "{}") }
                TextField("args (JSON)", text: $args, axis: .vertical)
                    .font(Theme.code).textInputAutocapitalization(.never).autocorrectionDisabled()
                Button(running ? "Running…" : "Run") { Task { await run() } }.disabled(running)
                Button("Reload engine") { model.engine.reload() }
            } footer: {
                Text("engine.js \(model.engine.hasScript ? "bundled" : "MISSING"). GET only; quiz pages are refused.")
            }
            if !output.isEmpty {
                Section("Result") {
                    Text(output).font(Theme.code).textSelection(.enabled)
                    Button("Copy") { UIPasteboard.general.string = output }
                }
            }
        }
        .navigationTitle("Developer")
        .navigationBarTitleDisplayMode(.inline)
    }

    /// Prefill a class's section id so the first run is one tap.
    private func fill(_ s: String) -> String {
        guard let sid = model.classes.first?.sectionId else { return s }
        return s.replacingOccurrences(of: #""sectionId":"""#, with: #""sectionId":"\#(sid)""#)
    }

    private func run() async {
        running = true
        defer { running = false }
        let t0 = Date()
        guard let d = args.data(using: .utf8), let a = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any] else {
            output = "args isn't a JSON object"
            return
        }
        let out = await model.engine.debugCall(op, a)
        output = String(format: "%.2fs\n", Date().timeIntervalSince(t0)) + out
    }
}

