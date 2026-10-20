import SwiftUI
import VisionKit

/// The student's ID number, for the barcode (StudentIDCard). Kept in the
/// Keychain on this phone only: never sent anywhere, never in the widgets.
enum StudentID {
    private static let key = "studentId"
    /// FUHSD's student numbers are 7 digits (the card, and the Schoology username, 2026-10-20).
    static let usualLength = 7

    static var number: String? {
        get { Keychain.get(key) }
        set { if let v = newValue, !v.isEmpty { Keychain.set(key, v) } else { Keychain.delete(key) } }
    }

    /// Digits only: anything else typed or pasted is dropped.
    static func clean(_ s: String) -> String { String(s.filter { $0.isASCII && $0.isNumber }.prefix(12)) }

    /// A note under the number when it doesn't look like an FUHSD ID (never a block: older IDs may differ).
    static func warning(_ s: String) -> String? {
        if s.isEmpty || s.count == usualLength { return nil }
        return "FUHSD student IDs are \(usualLength) digits. This has \(s.count): check it against your card."
    }
}

extension Notification.Name {
    /// Siri, the widget or the Control Center button: show the ID card.
    static let showStudentID = Notification.Name("classwork.showStudentID")
}

/// The ID card, full screen: the Code 39 barcode at full brightness for the
/// library or attendance scanner, and the number under it.
struct StudentIDCard: View {
    @Environment(\.dismiss) private var dismiss
    @State private var number = StudentID.number
    @State private var editing = false
    @State private var savedBrightness: CGFloat?

    var body: some View {
        NavigationStack {
            VStack(spacing: Theme.Space.xl) {
                if let number, Code39.canEncode(number) {
                    VStack(spacing: Theme.Space.m) {
                        Barcode(value: number)
                            .frame(height: 130)
                            .accessibilityLabel("Barcode of your student ID")
                        Text(number)
                            .font(.system(.largeTitle, design: .monospaced, weight: .semibold))
                            .tracking(4)
                            .foregroundStyle(.black)
                            .textSelection(.enabled)
                            .accessibilityLabel("Student ID \(number.map(String.init).joined(separator: " "))")
                    }
                    .padding(.vertical, Theme.Space.xl)
                    .padding(.horizontal, Theme.Space.m)
                    .frame(maxWidth: .infinity)
                    // Always black on white, whatever the theme: that's what scanners read.
                    .background(Color.white, in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous).stroke(Theme.border))
                    Text("The screen is at full brightness while this is open. Hold the phone flat under the scanner.")
                        .font(Theme.footnote).foregroundStyle(Theme.secondary).multilineTextAlignment(.center)
                    Button("Change number") { editing = true }.font(Theme.subhead.weight(.semibold))
                } else {
                    EmptyState(icon: "person.text.rectangle", title: "No student ID yet",
                               detail: "Add your number once and Dewey shows its barcode here.")
                    WideButton(title: "Add my student ID", systemImage: "plus") { editing = true }
                }
                Spacer()
            }
            .padding(Theme.Space.l)
            .padding(.top, Theme.Space.xl)
            .background(Theme.background)
            .navigationTitle("Student ID")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .sheet(isPresented: $editing, onDismiss: { number = StudentID.number }) {
                NavigationStack { StudentIDSetup(onSaved: { editing = false }) }
            }
        }
        .onAppear {
            UIApplication.shared.isIdleTimerDisabled = true
            if let screen = Self.screen {
                savedBrightness = screen.brightness
                screen.brightness = 1
            }
        }
        .onDisappear {
            UIApplication.shared.isIdleTimerDisabled = false
            if let b = savedBrightness { Self.screen?.brightness = b }
        }
    }

    private static var screen: UIScreen? {
        (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.screen
    }
}

/// Code 39 bars, each a whole number of pixels wide so the edges stay sharp.
struct Barcode: View {
    let value: String
    @Environment(\.displayScale) private var scale

    var body: some View {
        Canvas { ctx, size in
            guard let m = Code39.modules(value) else { return }
            let px = max(1, floor(size.width * scale / CGFloat(m.count)))
            let module = px / scale
            let x0 = ((size.width - module * CGFloat(m.count)) / 2 * scale).rounded() / scale
            var i = 0
            while i < m.count {
                guard m[i] else { i += 1; continue }
                var j = i
                while j < m.count && m[j] { j += 1 }
                ctx.fill(Path(CGRect(x: x0 + CGFloat(i) * module, y: 0, width: CGFloat(j - i) * module, height: size.height)), with: .color(.black))
                i = j
            }
        }
        .background(Color.white)
    }
}

/// Setting the number: from Schoology (its username is the ID at FUHSD), by
/// scanning the card, or typed. Digits only; a length other than 7 gets a note.
struct StudentIDSetup: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    var onSaved: () -> Void = {}
    /// In Settings: a button to the card itself.
    var showsCard = false
    @State private var text = StudentID.number ?? ""
    @State private var scanning = false
    @State private var loading = false
    @State private var note: String?
    @State private var showingCard = false
    @FocusState private var typing: Bool

    var body: some View {
        DewForm {
            if showsCard && StudentID.number != nil {
                Section {
                    // From here, not the tab view: Settings is already a sheet over it.
                    Button { showingCard = true } label: { Label("Show my barcode", systemImage: "barcode") }
                } footer: {
                    Text("Faster: say \u{201C}Show my student ID in Dewey\u{201D}, or add the Student ID widget or Control Center button.")
                }
            }
            Section {
                TextField("Student ID", text: $text)
                    .keyboardType(.numberPad)
                    .textContentType(.none)
                    .font(.system(.title2, design: .monospaced, weight: .semibold))
                    .focused($typing)
                    .onChange(of: text) { _, t in
                        let c = StudentID.clean(t)
                        if c != t { text = c }
                    }
                // Too short only once typing stops; too long at once.
                if let w = StudentID.warning(text), !typing || text.count > StudentID.usualLength {
                    Label(w, systemImage: "exclamationmark.triangle").font(Theme.footnote).foregroundStyle(Theme.late)
                }
                if let note { Text(note).font(Theme.footnote).foregroundStyle(Theme.secondary) }
            } header: { Text("Your student number") } footer: {
                Text("The number printed under the barcode on your ID card. Only on this phone: Dewey never sends it anywhere.")
            }
            Section {
                Button { Task { await fillFromSchoology() } } label: {
                    HStack {
                        Label("Fill in from Schoology", systemImage: "graduationcap")
                        if loading { Spacer(); ProgressView() }
                    }
                }
                .disabled(loading)
                if DataScannerViewController.isSupported {
                    Button { scanning = true } label: { Label("Scan my ID card", systemImage: "barcode.viewfinder") }
                }
            } footer: {
                Text(DataScannerViewController.isSupported
                     ? "Schoology: your username, which at FUHSD is your student ID. Scan: point the camera at the barcode on your card."
                     : "Your Schoology username, which at FUHSD is your student ID.")
            }
            if StudentID.number != nil {
                Section {
                    Button("Remove student ID", role: .destructive) {
                        StudentID.number = nil
                        text = ""
                        onSaved()
                        dismiss()
                    }
                }
            }
        }
        .navigationTitle("Student ID")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !showsCard {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    StudentID.number = text
                    onSaved()
                    dismiss()
                }
                .fontWeight(.semibold)
                .disabled(text.isEmpty)
            }
        }
        .fullScreenCover(isPresented: $showingCard) { StudentIDCard() }
        .sheet(isPresented: $scanning) {
            CardScanner { result in
                scanning = false
                switch result {
                case .number(let n): text = n; note = "Read from your card."
                case .otherKind: note = "That barcode isn't the kind on an FUHSD ID card (Code 39). Try the one above your name."
                case .notDigits: note = "That barcode has letters in it, so it isn't a student number."
                }
            }
            .ignoresSafeArea()
        }
    }

    private func fillFromSchoology() async {
        loading = true
        defer { loading = false }
        do {
            let a: AccountInfo = try await model.engine.call("account")
            if let id = a.studentId {
                text = id
                note = "From your Schoology username."
            } else {
                note = "Your Schoology username isn't a number, so it can't be your ID. Scan your card or type the number."
            }
        } catch {
            note = "Couldn't read Schoology just now. Scan your card or type the number."
        }
    }
}

/// The engine's account op (/settings/account).
struct AccountInfo: Decodable {
    var username: String?
    var studentId: String?
}

/// The camera, looking for one barcode (VisionKit's data scanner).
struct CardScanner: UIViewControllerRepresentable {
    enum Result { case number(String), otherKind, notDigits }
    let onResult: (Result) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let vc = DataScannerViewController(recognizedDataTypes: [.barcode()], qualityLevel: .accurate,
                                           recognizesMultipleItems: false, isHighlightingEnabled: true)
        vc.delegate = context.coordinator
        try? vc.startScanning()
        return vc
    }

    func updateUIViewController(_ vc: DataScannerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onResult: onResult) }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let onResult: (Result) -> Void
        private var done = false
        init(onResult: @escaping (Result) -> Void) { self.onResult = onResult }

        func dataScanner(_ scanner: DataScannerViewController, didAdd items: [RecognizedItem], allItems: [RecognizedItem]) {
            guard !done else { return }
            for item in items {
                guard case .barcode(let b) = item, let payload = b.payloadStringValue else { continue }
                done = true
                scanner.stopScanning()
                if b.observation.symbology != .code39 { onResult(.otherKind); return }
                let digits = StudentID.clean(payload)
                onResult(digits.isEmpty || digits.count != payload.count ? .notDigits : .number(digits))
                return
            }
        }
    }
}
