import SwiftUI

/// 1b · One assignment: the Submission card (exactly one of five), instructions,
/// type switch, what-if, and the main action.
struct AssignmentView: View {
    @Environment(AppModel.self) private var model
    let id: String
    @State private var a: Assignment?
    @State private var status: SubmissionStatus?
    @State private var checkedAt: Date?
    @State private var error: EngineError?
    @State private var sheet: SheetTarget?
    @State private var file: FileTarget?
    @State private var whatIf = false
    @State private var counting = false

    private var it: ItemState? { _ = model.revision; return model.item(id) }
    private var pageURL: URL { URL(string: "\(Engine.origin.absoluteString)/assignment/\(id)")! }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                titleBlock
                if let a {
                    submissionCard(a)
                    if let it { progressCard(it) }
                    gradeCard
                    instructions(a)
                    typeSwitch
                    whatIfButton
                } else if let error {
                    ErrorState(error: error, schoologyURL: pageURL, retry: { Task { await load() } }, open: { sheet = SheetTarget(url: $0, assignmentId: id) })
                } else {
                    SkeletonRows(count: 3)
                }
            }
            .padding(16)
            .padding(.bottom, 80)
        }
        .background(Theme.background)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { sheet = SheetTarget(url: pageURL, assignmentId: id) } label: { Label("Open in Schoology", systemImage: "globe").labelStyle(.iconOnly) }
                    .accessibilityLabel("Open in Schoology")
            }
        }
        .safeAreaInset(edge: .bottom) { bottomBar }
        .sheet(item: $sheet) { SchoologySheet(target: $0) }
        .sheet(item: $file) { FileViewer(target: $0) }
        .sheet(isPresented: $whatIf) { WhatIfSheet(itemId: id, sectionId: it?.sectionId) }
        .task { await load() }
        .onChange(of: model.revision) { _, _ in
            if let s = it?.statusState { status = SubmissionStatus(state: s, late: it?.statusLate); checkedAt = it?.statusCheckedAt }
        }
        .refreshable { await load() }
    }

    private func load() async {
        do {
            let got: Assignment = try await model.engine.call("assignment", ["id": id])
            a = got
            error = nil
            if let it {
                // Save only real changes: a save redraws the To Do list under this screen.
                let ext = got.type == "external_tool", lab = labLink(got)?.absoluteString
                var changed = it.external != ext || it.labURL != lab
                it.external = ext
                it.labURL = lab
                if let s = got.submission, s.state != it.statusState || s.late != it.statusLate {
                    it.statusState = s.state
                    it.statusLate = s.late
                    changed = true
                }
                if got.submission != nil { it.statusCheckedAt = Date() }
                if changed { model.save() }
            }
            status = got.submission
            checkedAt = Date()
        } catch let e as EngineError {
            error = e
        } catch {}
        if let s = it?.sectionId { await model.loadGrades(s) }
    }

    // ── Header ───────────────────────────────────────────────────────────

    private var titleBlock: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let c = model.course(it?.sectionId) {
                Text(c.shortTitle).font(Theme.font(13, .semibold)).foregroundStyle(Theme.accent)
            }
            Text(a?.title ?? it?.title ?? "Assignment").font(Theme.font(26, .bold))
            HStack(spacing: 8) {
                if let it { TypeTag(type: it.type) }
                let due = Fmt.date(a?.due) ?? it?.due
                Text(due.map { "Due " + Fmt.dayTime.string(from: $0) } ?? "No due date").font(Theme.mono(13))
                    .foregroundStyle(Theme.secondary)
                if let g = gradeItem, let p = g.possible { Text("· \(Fmt.num(p)) pts").font(Theme.mono(13)).foregroundStyle(Theme.secondary) }
            }
        }
    }

    private var gradeItem: CourseGrade.Item? {
        guard let s = it?.sectionId else { return nil }
        return model.grades[s]?.items.first { $0.id == id }
    }

    // ── Submission card: exactly one of five ─────────────────────────────

    enum Kind { case dropbox(Bool), outside(URL?), quiz, google, unknown }

    private func kind(_ a: Assignment) -> Kind {
        if a.type == "quiz" || (it?.isQuiz ?? false) { return .quiz }
        if a.type == "external_tool" { return .google }
        let state = status?.state ?? a.submission?.state
        switch state {
        case "submitted": return .dropbox(true)
        case "not_submitted": return .dropbox(false)
        case "no_dropbox": return .outside(labLink(a))
        default: return .unknown
        }
    }

    @ViewBuilder
    private func submissionCard(_ a: Assignment) -> some View {
        let k = kind(a)
        VStack(alignment: .leading, spacing: 6) {
            Text("SUBMISSION").font(Theme.font(12, .semibold)).tracking(0.5).foregroundStyle(Theme.tertiary)
            switch k {
            case .dropbox(let done):
                Label(done ? "Submitted on Schoology" : "Not submitted on Schoology",
                      systemImage: done ? "checkmark.circle.fill" : "xmark.circle")
                    .font(Theme.font(17, .semibold))
                    .foregroundStyle(done ? Theme.good : Theme.late)
                if done, status?.late == true { Text("Late").font(Theme.font(13)).foregroundStyle(Theme.late) }
                if let checkedAt { Text("Checked \(Fmt.ago(checkedAt))").font(Theme.font(13)).foregroundStyle(Theme.secondary) }
            case .outside(let url):
                Label("Handed in outside Schoology", systemImage: "arrow.up.right.square").font(Theme.font(17, .semibold))
                Text(url != nil ? "This class takes it on its own site (\(url!.host ?? "")). Schoology can't see it." : "No Schoology dropbox. Hand it in the way your teacher asked, then mark it turned in.")
                    .font(Theme.font(13)).foregroundStyle(Theme.secondary)
            case .quiz:
                Label("Test / quiz on Schoology", systemImage: "list.bullet.clipboard").font(Theme.font(17, .semibold))
                Text("It only opens when you tap Take on Schoology.").font(Theme.font(13)).foregroundStyle(Theme.secondary)
            case .google:
                Label("Google Assignment", systemImage: "doc.richtext").font(Theme.font(17, .semibold))
                Text("Turned in inside Schoology's Google frame. Schoology never tells the app whether you did, so check it there.")
                    .font(Theme.font(13)).foregroundStyle(Theme.secondary)
            case .unknown:
                Label("Checking Schoology…", systemImage: "hourglass").font(Theme.font(17, .semibold)).foregroundStyle(Theme.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .card()
    }

    // The main action for each kind.
    private var mainAction: (String, () -> Void)? {
        guard let a else { return nil }
        switch kind(a) {
        case .dropbox: return ("Submit on Schoology ↗", { sheet = SheetTarget(url: pageURL, assignmentId: id) })
        case .outside(let url?): return ("Turn in on \(url.host ?? "lab site") ↗", { sheet = SheetTarget(url: url) })
        case .quiz: return ("Take on Schoology ↗", { sheet = SheetTarget(url: URL(string: Engine.origin.absoluteString + (it?.url ?? "/assignment/\(id)"))!, assignmentId: id) })
        case .google: return ("Open in Schoology ↗", { sheet = SheetTarget(url: pageURL, assignmentId: id, google: true) })
        default: return nil
        }
    }

    /// An outside submit page linked from the instructions (AP CS's lab site).
    private func labLink(_ a: Assignment) -> URL? {
        guard let md = a.instructionsMd else { return nil }
        for m in md.matches(of: /\]\((https?:\/\/[^)\s]+)\)/) {
            guard let u = URL(string: String(m.1)), let h = u.host else { continue }
            if h.hasSuffix("schoology.com") || h.hasSuffix("google.com") || h.hasSuffix("youtube.com") { continue }
            return u
        }
        return nil
    }

    // ── Your status: To do / Done / Turned in ────────────────────────────

    private func progressCard(_ it: ItemState) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("YOUR STATUS").font(Theme.font(12, .semibold)).tracking(0.5).foregroundStyle(Theme.tertiary)
            ProgressChips(current: model.progress(it), isExam: it.type == .exam) { p in
                withAnimation(.snappy(duration: 0.2)) { model.setProgress(it, p) }
            }
            Text(it.submitted ? "Schoology also shows it submitted." : "Only you see this. Turned in means you handed it in, on paper or another site.")
                .font(Theme.font(13)).foregroundStyle(Theme.secondary)
            if counting || (WidgetBridge.canCountDown(it) && !model.isFinished(it) && WidgetBridge.activitiesAllowed) {
                Theme.divider.frame(height: 1).padding(.vertical, 4)
                Toggle(isOn: Binding(get: { counting }, set: { on in
                    counting = on
                    if on { try? WidgetBridge.startCountdown(model, it) } else { Task { await WidgetBridge.stopCountdown(it.schoologyId) } }
                })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Countdown on Lock Screen").font(Theme.font(15, .medium)).foregroundStyle(Theme.text)
                        Text("Shows the time left in the Dynamic Island. Ends when you mark it done.")
                            .font(Theme.font(12.5)).foregroundStyle(Theme.secondary)
                    }
                }
                .tint(Theme.accent)
            }
        }
        .onAppear { counting = WidgetBridge.activity(for: it.schoologyId) != nil }
        .padding(16)
        .card()
    }

    // ── Grade, instructions, type, what-if ───────────────────────────────

    @ViewBuilder
    private var gradeCard: some View {
        if let g = gradeItem, g.earned != nil || !g.comment.isEmpty {
            NavigationLink {
                GradeDetailView(sectionId: it?.sectionId ?? "", itemId: id)
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("GRADED").font(Theme.font(12, .semibold)).tracking(0.5).foregroundStyle(Theme.tertiary)
                        Text(g.grade).font(Theme.mono(17, .semibold)).foregroundStyle(Theme.text)
                        if !g.comment.isEmpty { Text(g.comment).font(Theme.font(13)).foregroundStyle(Theme.secondary).maxLines(2) }
                    }
                    Spacer()
                    if let i = g.impact, abs(i) >= 0.005 { Text(Fmt.signed(i)).font(Theme.mono(13)).foregroundStyle(i < 0 ? Theme.late : Theme.good) }
                    Image(systemName: "chevron.right").foregroundStyle(Theme.tertiary)
                }
                .padding(16)
                .card()
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private func instructions(_ a: Assignment) -> some View {
        let md = (a.instructionsMd ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !md.isEmpty || !a.attachments.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("INSTRUCTIONS").font(Theme.font(12, .semibold)).tracking(0.5).foregroundStyle(Theme.tertiary)
                if !md.isEmpty {
                    MarkdownText(md: md) { url in LinkOpener.open(url) { sheet = $0 } }
                }
                ForEach(a.attachments, id: \.url) { att in
                    AttachmentRow(title: att.title, url: att.url) { target in
                        switch target {
                        case .file(let f): file = f
                        case .web(let u): LinkOpener.open(u) { sheet = $0 }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .card()
        }
    }

    @ViewBuilder
    private var typeSwitch: some View {
        if let it {
            VStack(alignment: .leading, spacing: 8) {
                Text(model.neoPlan.connected ? "IN NEO-PLAN AS" : "TYPE").font(Theme.font(12, .semibold)).tracking(0.5).foregroundStyle(Theme.tertiary)
                Picker("Type", selection: Binding(get: { it.type }, set: { model.setType(it, $0) })) {
                    ForEach(ItemType.allCases, id: \.self) { Text($0.name).tag($0) }
                }
                .pickerStyle(.segmented)
            }
            .padding(16)
            .card()
        }
    }

    @ViewBuilder
    private var whatIfButton: some View {
        if let g = gradeItem, g.earned == nil, let p = g.possible {
            Button { whatIf = true } label: {
                Text("What if I get __ / \(Fmt.num(p))?")
                    .font(Theme.font(15, .semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.accent, style: StrokeStyle(lineWidth: 1.2, dash: [5, 4])))
            }
            .buttonStyle(.plain)
            .foregroundStyle(Theme.accent)
        }
    }

    // ── Bottom bar: Done + the main action ───────────────────────────────

    @ViewBuilder
    private var bottomBar: some View {
        if let (title, action) = mainAction {
            WideButton(title: title, action: action)
                .padding(.horizontal, 16).padding(.vertical, 8)
                .background(.bar)
        }
    }
}

/// Markdown from the engine (instructions, pages, posts), links tappable.
struct MarkdownText: View {
    let md: String
    let onLink: (URL) -> Void
    var body: some View {
        let attr = (try? AttributedString(markdown: md, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(md)
        Text(attr)
            .font(Theme.font(15))
            .foregroundStyle(Theme.text)
            .textSelection(.enabled)
            .environment(\.openURL, OpenURLAction { url in onLink(url); return .handled })
    }
}
