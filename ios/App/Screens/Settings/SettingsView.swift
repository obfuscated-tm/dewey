import SwiftUI
import UserNotifications

/// 4b · Account, neo-plan, reminders, look, login
/// compatibility, this build's expiry, and the Developer screen.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var tokenField = ""
    @State private var tokenError: String?
    @State private var connected = NeoPlanClient().connected
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
    @AppStorage(Inbox.markReadKey) private var markRead = true
    private let theme = ThemeStore.shared
    @AppStorage(Theme.appearanceKey) private var appearance = "system"
    @AppStorage(ToDoLayout.key) private var todoLayout = ToDoLayout.week.rawValue
    @AppStorage(GradesLook.reportCardKey) private var reportCard = true
    @AppStorage(WebConfig.compatKey) private var compat = false
    @State private var confirmSignOut = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Schoology") {
                    LabeledContent("Signed in to", value: Engine.host)
                    Button("Sign out", role: .destructive) { confirmSignOut = true }
                }

                Section {
                    if connected {
                        LabeledContent("Token", value: NeoPlanClient.mask(model.neoPlan.token))
                        Button("Sync now") { Task { await model.syncNeoPlan() } }
                        Button("Disconnect", role: .destructive) {
                            model.neoPlan.setToken(nil)
                            connected = false
                        }
                    } else {
                        SecureField("np_…", text: $tokenField)
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                            .font(Theme.subhead.monospacedDigit())
                        if let tokenError { Text(tokenError).font(.footnote).foregroundStyle(Theme.late) }
                        Button("Connect") {
                            let t = tokenField.trimmingCharacters(in: .whitespacesAndNewlines)
                            guard NeoPlanClient.validToken(t) else { tokenError = "That doesn't look like a neo-plan token (np_…)."; return }
                            model.neoPlan.setToken(t)
                            tokenField = ""
                            tokenError = nil
                            connected = true
                            Task { await model.syncNeoPlan() }
                        }
                        .disabled(tokenField.isEmpty)
                    }
                } header: { Text("neo-plan") } footer: {
                    Text(connected ? "Done, turned in and type changes are shared with neo-plan; the newer edit wins."
                                   : "Optional. Copy the token from neo-plan's extension settings. Kept in the Keychain.")
                }

                Section {
                    Toggle("2 hours before, if not submitted", isOn: $dueSoon)
                        .onChange(of: dueSoon) { _, on in dueChanged(on) }
                    Toggle("Night before a test", isOn: $testNight)
                        .onChange(of: testNight) { _, on in dueChanged(on) }
                    Toggle("Homework the evening before", isOn: $reminders)
                        .onChange(of: reminders) { _, on in dueChanged(on) }
                    if reminders || testNight {
                        Picker("Evening time", selection: $hour) {
                            ForEach(15..<23) { h in Text(hourText(h)).tag(h) }
                        }
                        .onChange(of: hour) { _, _ in dueChanged(false) }
                    }
                    Toggle("Morning summary", isOn: $morning)
                        .onChange(of: morning) { _, on in dueChanged(on) }
                    if morning {
                        Picker("Morning time", selection: $morningHour) {
                            ForEach(5..<11) { h in Text(hourText(h)).tag(h) }
                        }
                        .onChange(of: morningHour) { _, _ in dueChanged(false) }
                    }
                } header: { Text("Due reminders") } footer: {
                    Text("A test reminder stops once you mark it Studied; the others stop once it's submitted, turned in or done. The 2-hour one is for Schoology dropboxes only and still comes if you marked it done but haven't submitted.")
                }

                Section {
                    Toggle("New grades", isOn: $gradeAlerts)
                        .onChange(of: gradeAlerts) { _, on in askIfOn(on) }
                    Toggle("Messages from teachers", isOn: $messageAlerts)
                        .onChange(of: messageAlerts) { _, on in askIfOn(on) }
                    Toggle("New materials in your classes", isOn: $materialAlerts)
                        .onChange(of: materialAlerts) { _, on in askIfOn(on) }
                    Toggle("Signed out of Schoology", isOn: $signedOutAlert)
                        .onChange(of: signedOutAlert) { _, on in askIfOn(on) }
                } header: { Text("Notifications") } footer: {
                    Text("Grade, message, material and signed-out alerts come when the app refreshes in the background, so they can be late. Schoology signs you out about once a day.")
                }

                Section {
                    Toggle("Mark messages read when opened", isOn: $markRead)
                } header: { Text("Inbox") } footer: {
                    Text(markRead ? "Opening a message in the app marks it read on Schoology, like opening it there."
                                  : "Messages show their preview only, so they stay unread on Schoology until you open them there.")
                }

                Section {
                    Picker("View", selection: $todoLayout) {
                        ForEach(ToDoLayout.allCases, id: \.self) { Text($0.title).tag($0.rawValue) }
                    }
                } header: { Text("To Do") } footer: {
                    Text(todoLayout == ToDoLayout.urgent.rawValue
                         ? "One list, late and due today at the top, each row with its class."
                         : "Work grouped by class, under a strip of days you can tap to filter.")
                }

                Section {
                    Toggle("Letters across the top", isOn: $reportCard)
                    NavigationLink("Classes on the shelf") { ShelfSettings() }
                } header: { Text("Grades and Classes") } footer: {
                    Text(reportCard ? "Grades starts with every class's letter in one row, like a report card."
                                    : "Grades starts straight with the list; each class shows its letter.")
                }

                Section("Look") {
                    Picker("Appearance", selection: $appearance) {
                        Text("System").tag("system")
                        Text("Light").tag("light")
                        Text("Dark").tag("dark")
                    }
                    .pickerStyle(.segmented)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: Theme.Space.m) {
                            ForEach(Palette.all) { p in
                                PaletteTile(palette: p, selected: theme.paletteID == p.id)
                                    .onTapGesture { theme.paletteID = p.id; WidgetBridge.publish(model) }
                            }
                        }
                        .padding(.vertical, Theme.Space.xs)
                    }
                    if theme.palette.accent == nil {
                        HStack(spacing: 14) {
                            ForEach(Theme.accentOptions, id: \.self) { hex in
                                Circle().fill(Color(hex: hex)).frame(width: 30, height: 30)
                                    .overlay(Circle().stroke(Theme.text, lineWidth: theme.accentHex == hex ? 2 : 0).padding(-3))
                                    .onTapGesture { theme.accentHex = hex; WidgetBridge.publish(model) }
                                    .accessibilityLabel(["Blue", "Green", "Purple", "Red"][Theme.accentOptions.firstIndex(of: hex) ?? 0])
                                    .accessibilityAddTraits(theme.accentHex == hex ? [.isButton, .isSelected] : .isButton)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }

                Section {
                    Toggle("Login compatibility mode", isOn: $compat)
                        .onChange(of: compat) { _, _ in model.engine.reload() }
                } footer: {
                    Text("Signs in with Safari's user agent. Turns on by itself if Google refuses the app's web view.")
                }

                Section("This build") {
                    LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")
                    LabeledContent("Expires", value: BuildInfo.expiryText)
                    NavigationLink("Developer") { DeveloperView() }
                }
            }
            .preferredColorScheme(Theme.colorScheme(appearance))
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
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

    private func hourText(_ h: Int) -> String {
        Fmt.time.string(from: Calendar.current.date(bySettingHour: h, minute: 0, second: 0, of: Date())!)
    }

    /// A due-reminder switch or time changed: ask for permission when turning one on, then reschedule.
    private func dueChanged(_ on: Bool) {
        Task {
            if on { _ = await Reminders.shared.requestPermission() }
            Reminders.shared.schedule(items: model.todoItems(), courses: model.courses)
        }
    }

    private func askIfOn(_ on: Bool) {
        if on { Task { _ = await Reminders.shared.requestPermission() } }
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

/// A theme in Settings → Look: a tiny To Do card in its colours, following light or dark mode.
private struct PaletteTile: View {
    let palette: Palette
    let selected: Bool

    var body: some View {
        let accent = palette.accent.map { Color(light: $0.light, dark: $0.dark) } ?? Theme.accent
        VStack(spacing: Theme.Space.xs) {
            VStack(alignment: .leading, spacing: 5) {
                Capsule().fill(palette.color("text")).frame(width: 34, height: 5)
                Capsule().fill(palette.color("tertiary")).frame(width: 22, height: 4)
                Capsule().fill(accent).frame(width: 40, height: 9).padding(.top, 2)
            }
            .padding(Theme.Space.s)
            .frame(width: 64, height: 50, alignment: .topLeading)
            .background(palette.color("card"), in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
            .padding(Theme.Space.s)
            .background(palette.color("background"), in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous)
                .stroke(selected ? Theme.accent : Theme.border, lineWidth: selected ? 2 : 1))
            Text(palette.name).font(Theme.caption).foregroundStyle(selected ? Theme.text : Theme.secondary)
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(palette.name + " theme")
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
