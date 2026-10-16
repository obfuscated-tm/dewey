import SwiftUI

/// 1b · One assignment: the Submission card (exactly one of five), instructions,
/// type switch, what-if, and the main action.
struct AssignmentView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let id: String
    @State private var a: Assignment?
    @State private var status: SubmissionStatus?
    @State private var checkedAt: Date?
    @State private var error: EngineError?
    @State private var sheet: SheetTarget?
    @State private var file: FileTarget?
    @State private var whatIf = false
    @State private var counting = false
    @State private var countdownError: String?

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
                    // 403: the teacher removed it or made it private. The saved row would stay forever otherwise.
                    if error == .http(403), let it, !it.dismissed {
                        Button(role: .destructive) {
                            model.dismiss(it)
                            dismiss()
                        } label: {
                            Label("Remove from my list", systemImage: "trash")
                                .font(Theme.subhead.weight(.semibold))
                                .frame(maxWidth: .infinity, minHeight: 44)
                        }
                        .buttonStyle(.bordered)
                        .tint(Theme.late)
                    }
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
                Text(c.shortTitle).font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.accent)
            }
            Text(a?.title ?? it?.title ?? "Assignment").font(Theme.title)
            HStack(spacing: 8) {
                if let it { TypeTag(type: it.type) }
                let due = Fmt.date(a?.due) ?? it?.due
                Text(due.map { "Due " + Fmt.dayTime.string(from: $0) } ?? "No due date").font(Theme.footnote.monospacedDigit())
                    .foregroundStyle(Theme.secondary)
                if let g = gradeItem, let p = g.possible { Text("· \(Fmt.num(p)) pts").font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary) }
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
            Text("SUBMISSION").font(Theme.caption.weight(.semibold)).tracking(0.5).foregroundStyle(Theme.tertiary)
            switch k {
            case .dropbox(let done):
                Label(done ? "Submitted on Schoology" : "Not submitted on Schoology",
                      systemImage: done ? "checkmark.circle.fill" : "xmark.circle")
                    .font(Theme.body.weight(.semibold))
                    .foregroundStyle(done ? Theme.good : Theme.late)
                if done, status?.late == true { Text("Late").font(Theme.footnote).foregroundStyle(Theme.late) }
                if let checkedAt { Text("Checked \(Fmt.ago(checkedAt))").font(Theme.footnote).foregroundStyle(Theme.secondary) }
            case .outside(let url):
                Label("Handed in outside Schoology", systemImage: "arrow.up.right.square").font(Theme.body.weight(.semibold))
                Text(url != nil ? "This class takes it on its own site (\(url!.host ?? "")). Schoology can't see it." : "No Schoology dropbox. Hand it in the way your teacher asked, then mark it turned in.")
                    .font(Theme.footnote).foregroundStyle(Theme.secondary)
            case .quiz:
                if let q = a.quiz { quizFacts(q) } else {
                    Label("Test / quiz on Schoology", systemImage: "list.bullet.clipboard").font(Theme.body.weight(.semibold))
                    Text("It only opens when you tap Take on Schoology.").font(Theme.footnote).foregroundStyle(Theme.secondary)
                }
            case .google:
                Label("Google Assignment", systemImage: "doc.richtext").font(Theme.body.weight(.semibold))
                Text("Turned in inside Schoology's Google frame. Schoology never tells the app whether you did, so check it there.")
                    .font(Theme.footnote).foregroundStyle(Theme.secondary)
            case .unknown:
                Label("Checking Schoology…", systemImage: "hourglass").font(Theme.body.weight(.semibold)).foregroundStyle(Theme.secondary)
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
        case .dropbox: return ("Submit on Schoology", { sheet = SheetTarget(url: pageURL, assignmentId: id) })
        case .outside(let url?): return ("Turn in on \(url.host ?? "lab site")", { sheet = SheetTarget(url: url) })
        case .quiz: return (quizAction(a.quiz), { sheet = SheetTarget(url: URL(string: Engine.origin.absoluteString + (it?.url ?? "/assignment/\(id)"))!, assignmentId: id) })
        case .google: return ("Open in Schoology", { sheet = SheetTarget(url: pageURL, assignmentId: id, google: true) })
        default: return nil
        }
    }

    // ── A newer quiz: read from its landing page, never from the quiz itself ──

    @ViewBuilder
    private func quizFacts(_ q: QuizInfo) -> some View {
        switch q.state {
        case "in_progress":
            Label("Attempt in progress", systemImage: "clock").font(Theme.body.weight(.semibold)).foregroundStyle(Theme.late)
            Text("Started on Schoology but not turned in. Resume it there.").font(Theme.footnote).foregroundStyle(Theme.secondary)
        case "submitted":
            Label("Submitted on Schoology", systemImage: "checkmark.circle.fill").font(Theme.body.weight(.semibold)).foregroundStyle(Theme.good)
        default:
            Label("Not started", systemImage: "list.bullet.clipboard").font(Theme.body.weight(.semibold))
            Text("Test / quiz on Schoology. It only opens when you tap Take on Schoology.").font(Theme.footnote).foregroundStyle(Theme.secondary)
        }
        VStack(alignment: .leading, spacing: 4) {
            if let s = q.score {
                let max = q.maxPoints.map { " / \(Fmt.num($0))" } ?? ""
                quizRow("Score", "\(Fmt.num(s))\(max)" + (q.letter.map { " · \($0)" } ?? ""))
            }
            quizRow("Attempts", q.attemptsAllowed.map { "\(q.attemptsTaken) of \($0)" } ?? "\(q.attemptsTaken), no limit")
            if let t = q.timeLimitMin { quizRow("Time limit", "\(Fmt.num(t)) min") }
            if let d = Fmt.date(q.opensAt) { quizRow("Opens", Fmt.dayTime.string(from: d)) }
            if let d = Fmt.date(q.closesAt) { quizRow("Closes", Fmt.dayTime.string(from: d)) }
            if q.overdue == true { Text("Overdue").font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.late) }
        }
        .padding(.top, 4)
        if !q.attempts.isEmpty {
            Theme.divider.frame(height: 1).padding(.vertical, 4)
            ForEach(q.attempts, id: \.self) { t in
                HStack(spacing: 8) {
                    Image(systemName: t.finished ? "checkmark.circle" : "clock").foregroundStyle(t.finished ? Theme.good : Theme.late)
                    Text("Attempt \(t.n)").font(Theme.subhead.weight(.medium)).foregroundStyle(Theme.text)
                    Spacer()
                    Text(attemptDetail(t)).font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary)
                }
                .accessibilityElement(children: .combine)
            }
            if q.attempts.contains(where: \.canView) {
                Text("Your answers are on Schoology: tap Review on Schoology, then View on the attempt.")
                    .font(Theme.footnote).foregroundStyle(Theme.secondary)
            }
        }
    }

    private func quizRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).foregroundStyle(Theme.secondary)
            Spacer()
            Text(value).foregroundStyle(Theme.text).monospacedDigit()
        }
        .font(Theme.subhead)
        .accessibilityElement(children: .combine)
    }

    private func attemptDetail(_ t: QuizInfo.Attempt) -> String {
        var parts: [String] = []
        if let d = Fmt.date(t.at) { parts.append(Fmt.dayTime.string(from: d)) }
        if let m = t.minutes { parts.append("\(Fmt.num(m)) min") }
        if !t.finished { parts.append("in progress") }
        return parts.joined(separator: " · ")
    }

    // Opening the quiz is always the student's tap, in the Schoology sheet.
    private func quizAction(_ q: QuizInfo?) -> String {
        switch q?.state {
        case "in_progress": return "Resume on Schoology"
        case "submitted": return q?.attempts.contains(where: \.canView) == true ? "Review on Schoology" : "Open on Schoology"
        default: return "Take on Schoology"
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
            Text("YOUR STATUS").font(Theme.caption.weight(.semibold)).tracking(0.5).foregroundStyle(Theme.tertiary)
            ProgressChips(current: model.progress(it), isExam: it.type == .exam) { p in
                withAnimation(.snappy(duration: 0.2)) { model.setProgress(it, p) }
            }
            Text(it.submitted ? "Schoology also shows it submitted." : "Only you see this. Turned in means you handed it in, on paper or another site.")
                .font(Theme.footnote).foregroundStyle(Theme.secondary)
            let window = WidgetBridge.countdownWindow(due: it.due)
            if counting || (window != .none && !model.isFinished(it) && WidgetBridge.activitiesAllowed) {
                Theme.divider.frame(height: 1).padding(.vertical, 4)
                Toggle(isOn: Binding(get: { counting }, set: { on in
                    countdownError = nil
                    guard on else {
                        counting = false
                        Task { await WidgetBridge.stopCountdown(it.schoologyId) }
                        return
                    }
                    do {
                        try WidgetBridge.startCountdown(model, it)
                        counting = true
                    } catch {
                        counting = false
                        countdownError = WidgetBridge.countdownErrorMessage(error)
                    }
                })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Countdown on Lock Screen").font(Theme.subhead.weight(.medium)).foregroundStyle(Theme.text)
                        Group {
                            if case .opens(let from) = window, !counting {
                                Text("Available from \(from.formatted(date: .abbreviated, time: .shortened)), 12 hours before it's due.")
                            } else {
                                Text("Shows the time left in the Dynamic Island. Ends when you mark it done.")
                            }
                        }
                        .font(Theme.footnote).foregroundStyle(Theme.secondary)
                    }
                }
                .tint(Theme.accent)
                .disabled(!counting && window != .open)
                if let countdownError {
                    Text(countdownError).font(Theme.footnote).foregroundStyle(Theme.late)
                }
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
                        Text("GRADED").font(Theme.caption.weight(.semibold)).tracking(0.5).foregroundStyle(Theme.tertiary)
                        Text(g.grade).font(Theme.body.weight(.semibold).monospacedDigit()).foregroundStyle(Theme.text)
                        if !g.comment.isEmpty { Text(g.comment).font(Theme.footnote).foregroundStyle(Theme.secondary).maxLines(2) }
                    }
                    Spacer()
                    if let i = g.impact, abs(i) >= 0.005 { Text(Fmt.signed(i)).font(Theme.footnote.monospacedDigit()).foregroundStyle(i < 0 ? Theme.late : Theme.good) }
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
                Text("INSTRUCTIONS").font(Theme.caption.weight(.semibold)).tracking(0.5).foregroundStyle(Theme.tertiary)
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
                Text(model.neoPlan.connected ? "IN NEO-PLAN AS" : "TYPE").font(Theme.caption.weight(.semibold)).tracking(0.5).foregroundStyle(Theme.tertiary)
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
                    .font(Theme.subhead.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .overlay(RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous).stroke(Theme.accent, style: StrokeStyle(lineWidth: 1.2, dash: [5, 4])))
            }
            .buttonStyle(.plain)
            .foregroundStyle(Theme.accent)
        }
    }

    // ── Bottom bar: Done + the main action ───────────────────────────────

    @ViewBuilder
    private var bottomBar: some View {
        if let (title, action) = mainAction {
            WideButton(title: title, external: true, action: action)
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
            .font(Theme.subhead)
            .foregroundStyle(Theme.text)
            .textSelection(.enabled)
            .environment(\.openURL, OpenURLAction { url in onLink(url); return .handled })
    }
}
