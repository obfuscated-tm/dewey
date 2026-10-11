import SwiftUI

/// 4b · Account, neo-plan, reminders, per-class turn-in, look, login
/// compatibility, this build's expiry, and the Developer screen.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var tokenField = ""
    @State private var tokenError: String?
    @State private var connected = NeoPlanClient().connected
    @AppStorage(Reminders.enabledKey) private var reminders = false
    @AppStorage(Reminders.hourKey) private var hour = 19
    @AppStorage(Reminders.gradesKey) private var gradeAlerts = true
    @AppStorage(Theme.accentKey) private var accent = Theme.accentOptions[0]
    @AppStorage(Theme.appearanceKey) private var appearance = "system"
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
                            .font(Theme.mono(14))
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
                    Toggle("Evening-before reminders", isOn: $reminders)
                        .onChange(of: reminders) { _, on in
                            Task {
                                if on, !(await Reminders.shared.requestPermission()) { reminders = false }
                                Reminders.shared.schedule(items: model.todoItems(), courses: model.courses)
                            }
                        }
                    if reminders {
                        Picker("Time", selection: $hour) {
                            ForEach(15..<23) { h in Text(Fmt.time.string(from: Calendar.current.date(bySettingHour: h, minute: 0, second: 0, of: Date())!)).tag(h) }
                        }
                        .onChange(of: hour) { _, _ in Reminders.shared.schedule(items: model.todoItems(), courses: model.courses) }
                        Toggle("New grades", isOn: $gradeAlerts)
                    }
                } header: { Text("Reminders") } footer: {
                    Text("New-grade alerts only come when the app refreshes in the background, so they can be late.")
                }

                Section {
                    ForEach(model.classes) { c in
                        ClassTurnInRow(course: c, state: model.courseState(c.sectionId))
                    }
                } header: { Text("How each class turns work in") } footer: {
                    Text("Auto learns from grades: a graded item with an empty dropbox means the class takes paper.")
                }

                Section("Look") {
                    Picker("Appearance", selection: $appearance) {
                        Text("System").tag("system")
                        Text("Light").tag("light")
                        Text("Dark").tag("dark")
                    }
                    .pickerStyle(.segmented)
                    HStack(spacing: 14) {
                        ForEach(Theme.accentOptions, id: \.self) { hex in
                            Circle().fill(Color(hex: hex)).frame(width: 30, height: 30)
                                .overlay(Circle().stroke(Theme.text, lineWidth: accent == hex ? 2 : 0).padding(-3))
                                .onTapGesture { accent = hex; WidgetBridge.publish(model) }
                                .accessibilityLabel(["Blue", "Green", "Purple", "Red"][Theme.accentOptions.firstIndex(of: hex) ?? 0])
                                .accessibilityAddTraits(accent == hex ? [.isButton, .isSelected] : .isButton)
                        }
                    }
                    .padding(.vertical, 4)
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
}

struct ClassTurnInRow: View {
    @Environment(AppModel.self) private var model
    let course: Course
    @Bindable var state: CourseState

    var body: some View {
        Picker(selection: $state.turnIn) {
            Text(state.paperEvidence > 0 ? "Auto (paper)" : "Auto").tag("auto")
            Text("Dropbox").tag("dropbox")
            Text("Paper").tag("paper")
            Text("Lab site").tag("lab")
        } label: {
            VStack(alignment: .leading, spacing: 1) {
                Text(course.shortTitle).lineLimit(1)
                Text(course.periodLabel).font(.caption).foregroundStyle(Theme.tertiary)
            }
        }
        .onChange(of: state.turnIn) { _, _ in model.save() }
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
    ]

    var body: some View {
        Form {
            Section {
                Picker("Op", selection: $op) {
                    ForEach(Self.examples.keys.sorted(), id: \.self) { Text($0).tag($0) }
                }
                .onChange(of: op) { _, o in args = fill(Self.examples[o] ?? "{}") }
                TextField("args (JSON)", text: $args, axis: .vertical)
                    .font(Theme.mono(13)).textInputAutocapitalization(.never).autocorrectionDisabled()
                Button(running ? "Running…" : "Run") { Task { await run() } }.disabled(running)
                Button("Reload engine") { model.engine.reload() }
            } footer: {
                Text("engine.js \(model.engine.hasScript ? "bundled" : "MISSING"). GET only; quiz pages are refused.")
            }
            if !output.isEmpty {
                Section("Result") {
                    Text(output).font(Theme.mono(11)).textSelection(.enabled)
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
