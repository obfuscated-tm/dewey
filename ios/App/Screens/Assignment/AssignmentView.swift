import SwiftUI

/// 1b · One assignment: one header (class, title, the stamp, due, one status line that
/// merges what Schoology says with your own mark, the grade), then the instructions as the
/// page, then the bottom bar: your circle + the main action. Type, what-if, the Lock Screen
/// countdown and Open in Schoology live in the ⋯ menu (2026-11-07).
struct AssignmentView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let id: String
    var sectionId: String? = nil
    @State private var a: Assignment?
    @State private var status: SubmissionStatus?
    @State private var checkedAt: Date?
    @State private var error: EngineError?
    @State private var sheet: SheetTarget?
    @State private var file: FileTarget?
    @State private var whatIf = false
    @State private var counting = false
    @State private var countdownError: String?
    @Environment(\.dynamicTypeSize) private var typeSize

    private var it: ItemState? { _ = model.revision; return model.item(id) }
    /// The class: the saved row's, else the link's, else whichever loaded gradebook has this item.
    private var section: String? {
        it?.sectionId ?? sectionId ?? model.grades.first { $0.value.items.contains { $0.id == id } }?.key
    }
    private var pageURL: URL { URL(string: "\(Engine.origin.absoluteString)/assignment/\(id)")! }

    var body: some View {
        // The student's own task (from a widget, a reminder or a class page): never Schoology's.
        if ItemState.isMine(id) { MineTaskPage(id: id) } else { assignment }
    }

    private var assignment: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if let a {
                    header(a)
                    instructions(a)
                    if case .quiz = kind(a), let q = a.quiz { quizDetails(q) }
                } else if let error {
                    titleBlock
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
                    titleBlock
                    SkeletonRows(count: 3)
                }
            }
            .padding(16)
            .padding(.bottom, 80)
        }
        .background(Theme.background)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) { moreMenu }
        }
        .safeAreaInset(edge: .bottom) { bottomBar }
        .sheet(item: $sheet) { SchoologySheet(target: $0) }
        .sheet(item: $file) { FileViewer(target: $0) }
        .sheet(isPresented: $whatIf) { WhatIfSheet(itemId: id, sectionId: section) }
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
                if let q = got.quiz {
                    let before = it.statusState
                    model.noteQuiz(it, q)
                    changed = changed || it.statusState != before
                }
                if got.submission != nil || got.quiz != nil { it.statusCheckedAt = Date() }
                if changed { model.save() }
            }
            status = got.submission
            checkedAt = Date()
        } catch let e as EngineError {
            error = e
        } catch {}
        if let s = section { await model.loadGrades(s) }
    }

    // ── Header ───────────────────────────────────────────────────────────

    private var titleBlock: some View {
        VStack(alignment: .leading, spacing: 6) {
            // A library catalog line: class · period · term · category, in the class colour.
            if let c = model.course(section) {
                Text(catalogLine(c)).font(Theme.footnote.weight(.semibold))
                    .foregroundStyle(model.classColor(c.sectionId)).maxLines(2)
            }
            Text(a?.title ?? it?.title ?? "Assignment").font(Theme.displaySmall)
            HStack(spacing: 8) {
                // The type is a guess from Schoology's words, so it's one tap to fix, right where it shows.
                if let it {
                    Menu {
                        Picker(model.neoPlan.connected ? "In neo-plan as" : "Type", selection: Binding(get: { it.type }, set: { model.setType(it, $0) })) {
                            ForEach(ItemType.allCases, id: \.self) { Text($0.name).tag($0) }
                        }
                        .pickerStyle(.inline)
                    } label: {
                        TypeTag(type: it.type, changeable: true).contentShape(Rectangle()).padding(.vertical, 6)
                    }
                    .padding(.vertical, -6)
                    .sensoryFeedback(.selection, trigger: it.type)
                    .accessibilityLabel("Type: \(it.type.name)")
                    .accessibilityHint("Changes what kind of work this is")
                }
                // Once the assignment loads, the due date moves onto the checkout card.
                if a == nil {
                    let due = it?.due
                    Text(due.map { "Due " + Fmt.dayTime.string(from: $0) } ?? "No due date").font(Theme.footnote.monospacedDigit())
                        .foregroundStyle(Theme.secondary)
                }
                if let g = gradeItem, let p = g.possible { Text("\(Fmt.num(p)) points").font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary) }
            }
        }
    }

    /// "AP Biology · Labs & Homework": the class and where the grade goes.
    private func catalogLine(_ c: Course) -> String {
        [c.shortTitle, gradeItem?.category]
            .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
    }

    private var gradeItem: CourseGrade.Item? {
        guard let s = section else { return nil }
        return model.grades[s]?.items.first { $0.id == id }
    }

    // ── Submission card: exactly one of five ─────────────────────────────

    enum Kind { case dropbox(Bool), outside(URL?), quiz, google, unknown }

    private func kind(_ a: Assignment) -> Kind {
        if a.type == "quiz" || (it?.isQuiz ?? false) { return .quiz }
        if a.type == "external_tool" { return .google }
        // The saved row's last check, until this page's own check answers.
        let state = status?.state ?? a.submission?.state ?? it?.statusState
        switch state {
        case "submitted": return .dropbox(true)
        case "not_submitted": return .dropbox(false)
        case "no_dropbox": return .outside(labLink(a))
        default: return .unknown
        }
    }

    /// The header: class and title, the big tilted stamp overlapping its top (due, N days
    /// late, submitted, turned in; exactly as before, 2026-11-07), the due line (with a live
    /// countdown in its last 12 hours), where it's turned in, one status line, and the grade.
    @ViewBuilder
    private func header(_ a: Assignment) -> some View {
        let k = kind(a)
        let due = Fmt.date(a.due) ?? it?.due
        let s = stamp(k, due: due)
        VStack(alignment: .leading, spacing: 0) {
            if typeSize.isAccessibilitySize, let s { SlammingStamp(stamp: s).padding(.bottom, Theme.Space.m) }
            titleBlock
            Theme.divider.frame(height: 1).padding(.vertical, 12)
            // Where it's turned in is part of the status line ("Not submitted on Schoology",
            // "Hand it in on lab.site"), so the old place column is gone.
            dueLine(due)
            statusLine(k).padding(.top, 6)
            if let g = gradeItem, g.earned != nil || !g.comment.isEmpty {
                Theme.divider.frame(height: 1).padding(.vertical, 12)
                gradeRow(g)
            }
        }
        .padding(16)
        .card()
        .overlay(alignment: .topTrailing) {
            if !typeSize.isAccessibilitySize, let s { SlammingStamp(stamp: s).offset(x: -4, y: -44) }
        }
        .padding(.top, typeSize.isAccessibilitySize ? 0 : 36)
    }

    /// "Today, 11:59 pm · in 2 h 14 min": the countdown only in the last 12 hours of work
    /// still owed, ticking once a minute (no seconds: it's seen every time the page opens).
    private func dueLine(_ due: Date?) -> some View {
        TimelineView(.everyMinute) { ctx in
            let left = countdown(due, now: ctx.date)
            (Text(Fmt.due(due, now: ctx.date)) + Text(left.map { " · " + $0 } ?? ""))
                .font(Theme.subhead.weight(.semibold).monospacedDigit())
                .foregroundStyle(Theme.text)
                .contentTransition(.numericText(countsDown: true))
        }
    }

    private func countdown(_ due: Date?, now: Date) -> String? {
        guard let due, let it, model.isOwed(it), due > now, due.timeIntervalSince(now) <= 12 * 3600 else { return nil }
        let m = Int((due.timeIntervalSince(now) / 60).rounded(.up))
        return m >= 60 ? "in \(m / 60) h \(m % 60) min" : "in \(m) min"
    }

    /// One line for Schoology's fact and your own mark together ("Not submitted on Schoology ·
    /// you marked it done"). Red only while it's late.
    @ViewBuilder
    private func statusLine(_ k: Kind) -> some View {
        let (text, color) = statusWords(k)
        VStack(alignment: .leading, spacing: 2) {
            Text(text).font(Theme.subhead.weight(.semibold)).foregroundStyle(color)
            if case .dropbox = k, let checkedAt, Date().timeIntervalSince(checkedAt) > 3600 {
                Text("Checked \(Fmt.ago(checkedAt))").font(Theme.footnote).foregroundStyle(Theme.secondary)
            }
            // Kept: without it a student would trust a status Dewey can't see.
            if case .google = k {
                Text("Schoology doesn't tell Dewey if it's turned in.").font(Theme.footnote).foregroundStyle(Theme.secondary)
            }
        }
    }

    private func statusWords(_ k: Kind) -> (String, Color) {
        let mark = it.map { model.progress($0) } ?? .todo
        let isExam = it?.type == .exam
        let late = it.map { model.isOverdue($0) } ?? false
        let owedColor = late ? Theme.late : Theme.text
        let yours: String? = mark == .turnedIn ? "you marked it turned in" : mark == .done ? (isExam ? "studied" : "you marked it done") : nil
        switch k {
        case .dropbox(true):
            return (status?.late == true ? "Submitted on Schoology, late" : "Submitted on Schoology", Theme.good)
        case .dropbox(false):
            return (["Not submitted on Schoology", yours].compactMap { $0 }.joined(separator: " · "), owedColor)
        case .outside(let url):
            if mark == .turnedIn { return ("Turned in · marked by you", Theme.good) }
            let whereTo = url?.host.map { "on " + $0.replacingOccurrences(of: "www.", with: "") } ?? "the way your teacher asked"
            return (mark == .done ? "Done · hand it in \(whereTo)" : "Hand it in \(whereTo)", owedColor)
        case .quiz:
            switch a?.quiz?.state {
            case "submitted": return ("Submitted on Schoology", Theme.good)
            case "in_progress": return ("Attempt in progress · resume it on Schoology", Theme.late)
            default: return (mark == .done ? "Not started · studied" : "Not started · opens only when you tap Take", late ? Theme.late : Theme.secondary)
            }
        case .google:
            return (mark == .turnedIn ? "Google Assignment · you marked it turned in" : "Google Assignment", mark == .turnedIn ? Theme.good : Theme.text)
        case .unknown:
            return ("Checking Schoology…", Theme.secondary)
        }
    }

    private func gradeRow(_ g: CourseGrade.Item) -> some View {
        NavigationLink {
            GradeDetailView(sectionId: section ?? "", itemId: id)
        } label: {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Grade  ").font(Theme.subhead).foregroundStyle(Theme.secondary)
                        + Text(g.grade).font(Theme.subhead.weight(.semibold).monospacedDigit()).foregroundStyle(Theme.text)
                    if !g.comment.isEmpty { Text(g.comment).font(Theme.footnote).foregroundStyle(Theme.secondary).maxLines(2) }
                }
                Spacer()
                if let i = g.impact { Text(Fmt.signed(i)).font(Theme.footnote.monospacedDigit()).foregroundStyle(Fmt.impactColor(i)) }
                Image(systemName: "chevron.right").font(Theme.footnote).foregroundStyle(Theme.tertiary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func stamp(_ k: Kind, due: Date?) -> DueStamp.Stamp? {
        let submitted: Bool = {
            if case .dropbox(true) = k { return true }
            if case .quiz = k, a?.quiz?.state == "submitted" { return true }
            return false
        }()
        if submitted { return .init(top: "SCHOOLOGY", main: "SUBMITTED", color: Theme.good, spoken: "Submitted on Schoology") }
        if let it, it.turnedIn { return .init(top: "MARKED", main: "TURNED IN", color: Theme.good, spoken: "Marked turned in") }
        guard let due else { return nil }
        let finished = it.map { !model.showsLate($0) } ?? false
        if !finished, let n = TodoLogic.daysLate(due: due) {
            let main = n == 0 ? "LATE" : n == 1 ? "1 DAY LATE" : "\(n) DAYS LATE"
            return .init(top: "DUE " + Fmt.short.string(from: due).uppercased(), main: main, color: Theme.late,
                         spoken: "Due \(Fmt.header.string(from: due)), \(main.lowercased())")
        }
        return .init(top: "DUE", main: Fmt.short.string(from: due).uppercased(), color: Theme.text,
                     spoken: "Due \(Fmt.header.string(from: due))")
    }

    // The main action for each kind.
    /// (title, leaves Dewey, action). Paper work's main action is Mark turned in.
    private var mainAction: (String, Bool, () -> Void)? {
        guard let a else { return nil }
        switch kind(a) {
        case .dropbox: return ("Submit on Schoology", true, { sheet = SheetTarget(url: pageURL, assignmentId: id) })
        case .outside(let url?): return ("Turn in on \(url.host ?? "lab site")", true, { sheet = SheetTarget(url: url) })
        case .outside(nil):
            guard let it, !it.turnedIn else { return nil }
            return ("Mark turned in", false, { withAnimation(.snappy(duration: 0.2)) { model.setProgress(it, .turnedIn) } })
        case .quiz: return (quizAction(a.quiz), true, { sheet = SheetTarget(url: URL(string: Engine.origin.absoluteString + (it?.url ?? "/assignment/\(id)"))!, assignmentId: id) })
        case .google: return ("Open in Schoology", true, { sheet = SheetTarget(url: pageURL, assignmentId: id, google: true) })
        case .unknown: return nil
        }
    }

    // ── A newer quiz: read from its landing page, never from the quiz itself ──

    /// Score, attempts and times, under the instructions (the header says the state).
    @ViewBuilder
    private func quizDetails(_ q: QuizInfo) -> some View {
        VStack(alignment: .leading, spacing: 8) {
        Text("Quiz").font(Theme.headline).accessibilityAddTraits(.isHeader)
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

    /// An outside submit page linked from the instructions (AP Bio's lab site).
    private func labLink(_ a: Assignment) -> URL? {
        guard let md = a.instructionsMd else { return nil }
        for m in md.matches(of: /\]\((https?:\/\/[^)\s]+)\)/) {
            guard let u = URL(string: String(m.1)), let h = u.host else { continue }
            if h.hasSuffix("schoology.com") || h.hasSuffix("google.com") || h.hasSuffix("youtube.com") { continue }
            return u
        }
        return nil
    }

    // ── Instructions: the page ───────────────────────────────────────────

    @ViewBuilder
    private func instructions(_ a: Assignment) -> some View {
        let md = (a.instructionsMd ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        VStack(alignment: .leading, spacing: 14) {
            if !md.isEmpty {
                // Body size, unboxed, no heading: the reading is the page.
                MarkdownText(md: md, font: Theme.body) { url in LinkOpener.open(url) { sheet = $0 } }
                    .lineSpacing(3)
            } else if a.attachments.isEmpty {
                Text("No instructions on Schoology.").font(Theme.subhead).foregroundStyle(Theme.secondary)
            }
            ForEach(Array(a.attachments.enumerated()), id: \.element.url) { n, att in
                AttachmentCard(title: att.title, url: att.url, thumbnail: n < 4) { target in
                    switch target {
                    case .file(let f): file = f
                    case .web(let u): LinkOpener.open(u) { sheet = $0 }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
    }

    // ── ⋯ menu: Open in Schoology, what-if, Lock Screen countdown (type: the tag) ──

    private var moreMenu: some View {
        Menu {
            Button { sheet = SheetTarget(url: pageURL, assignmentId: id) } label: { Label("Open in Schoology", systemImage: "globe") }
            if let g = gradeItem, g.earned == nil, let p = g.possible {
                Button { whatIf = true } label: { Label("What if I get __ / \(Fmt.num(p))?", systemImage: "plus.forwardslash.minus") }
            }
            if let it, counting || (WidgetBridge.countdownWindow(due: it.due) == .open && !model.isFinished(it) && WidgetBridge.activitiesAllowed) {
                Button { toggleCountdown(it) } label: {
                    Label(counting ? "Stop Lock Screen countdown" : "Countdown on Lock Screen", systemImage: "timer")
                }
            }
        } label: {
            Label("More", systemImage: "ellipsis").labelStyle(.iconOnly)
        }
        .accessibilityLabel("More")
        .onAppear { counting = WidgetBridge.activity(for: id) != nil }
    }

    private func toggleCountdown(_ it: ItemState) {
        if counting {
            counting = false
            Task { await WidgetBridge.stopCountdown(it.schoologyId) }
            return
        }
        do {
            try WidgetBridge.startCountdown(model, it)
            counting = true
        } catch {
            model.toast = WidgetBridge.countdownErrorMessage(error)
        }
    }

    // ── Bottom bar: Done + the main action ───────────────────────────────

    /// Your circle, the same one as on To Do (tap: to do ↔ done; hold for every status),
    /// then the main action: the one teal thing on the screen.
    @ViewBuilder
    private var bottomBar: some View {
        let action = mainAction
        if a != nil, it != nil || action != nil {
            HStack(spacing: 12) {
                if let it { statusCircle(it) }
                if let (title, external, run) = action {
                    WideButton(title: title, external: external, action: run)
                } else {
                    Spacer(minLength: 0)
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
            .background(.bar)
        }
    }

    private func statusCircle(_ it: ItemState) -> some View {
        let p = model.progress(it)
        let word = it.type == .exam && p == .done ? "Studied" : p.label
        return Button {
            withAnimation(.snappy(duration: 0.2)) { model.setProgress(it, p == .todo ? .done : p == .done ? .todo : .done) }
        } label: {
            HStack(spacing: 8) {
                CheckMark(item: it, size: 28).doneRipple(p != .todo)
                    .frame(width: 28, height: 28)
                Text(word).font(Theme.subhead.weight(.semibold)).foregroundStyle(Theme.text)
                    .fixedSize()
            }
            .frame(minHeight: 48)
            .padding(.horizontal, 4)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle())
        .sensoryFeedback(.success, trigger: p)
        .contextMenu {
            ForEach(AppModel.Progress.allCases, id: \.self) { q in
                Button {
                    withAnimation(.snappy(duration: 0.2)) { model.setProgress(it, q) }
                } label: {
                    Label(it.type == .exam && q == .done ? "Studied" : q.label, systemImage: q == p ? "checkmark" : "")
                }
            }
        }
        .accessibilityLabel("Your status: \(word)")
        .accessibilityHint(p == .todo ? (it.type == .exam ? "Marks it studied" : "Marks it done") : "Marks it to do. Hold for every status.")
    }
}

/// Markdown from the engine (instructions, pages, posts), links tappable.
struct MarkdownText: View {
    let md: String
    var font: Font = Theme.subhead
    let onLink: (URL) -> Void
    var body: some View {
        let attr = (try? AttributedString(markdown: md, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(md)
        Text(attr)
            .font(font)
            .foregroundStyle(Theme.text)
            .textSelection(.enabled)
            .environment(\.openURL, OpenURLAction { url in onLink(url); return .handled })
    }
}

/// The rubber stamp on the checkout card: clean bold type in a rounded frame,
/// tilted a little. VoiceOver reads `spoken`.
struct DueStamp: View {
    struct Stamp { let top: String; let main: String; let color: Color; let spoken: String }
    let stamp: Stamp
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        VStack(spacing: 0) {
            Text(stamp.top).font(Theme.caption.weight(.heavy)).tracking(1.4)
            Text(stamp.main).font(Theme.title.weight(.heavy)).tracking(0.4).monospacedDigit()
        }
        .lineLimit(1)
        .foregroundStyle(stamp.color)
        .padding(.horizontal, 10).padding(.vertical, 4)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
        .background(stamp.color.opacity(0.08), in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous).stroke(stamp.color, lineWidth: 2.5))
        .rotationEffect(.degrees(typeSize.isAccessibilitySize ? 0 : -7))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(stamp.spoken)
    }
}

/// The stamp, slammed down again when what it says changes while you're looking (you come
/// back from the Schoology sheet and it's SUBMITTED, or you mark it turned in): in big, a
/// heavy thud, settled in about 300 ms. Never on opening the page; just a thud with Reduce Motion.
struct SlammingStamp: View {
    let stamp: DueStamp.Stamp
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var slam = 0
    @State private var thud = 0

    private struct Pose { var scale = 1.0; var opacity = 1.0 }

    var body: some View {
        DueStamp(stamp: stamp)
            .keyframeAnimator(initialValue: Pose(), trigger: slam) { view, pose in
                view.scaleEffect(pose.scale).opacity(pose.opacity)
            } keyframes: { _ in
                KeyframeTrack(\.scale) {
                    MoveKeyframe(1.8)
                    CubicKeyframe(0.94, duration: 0.14)
                    CubicKeyframe(1.0, duration: 0.14)
                }
                KeyframeTrack(\.opacity) {
                    MoveKeyframe(0)
                    LinearKeyframe(1, duration: 0.07)
                }
            }
            .sensoryFeedback(.impact(weight: .heavy), trigger: thud)
            .onChange(of: stamp.top + stamp.main) { _, _ in
                if !reduceMotion { slam += 1 }
                // Lands as the stamp hits the card.
                Task { try? await Task.sleep(for: .milliseconds(reduceMotion ? 0 : 130)); thud += 1 }
            }
    }
}
