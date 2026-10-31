import SwiftUI

/// 2c · Graded work: score, the teacher's comment, the rubric, dropbox
/// comments, and the effect on the course grade.
struct GradeDetailView: View {
    @Environment(AppModel.self) private var model
    let sectionId: String
    let itemId: String
    @State private var rubric: Rubric?
    @State private var rubricError: EngineError?
    @State private var dropbox: DropboxInfo?
    @State private var assignment: Assignment?
    @State private var sheet: SheetTarget?
    @State private var file: FileTarget?
    @State private var whatIf = false

    private var item: CourseGrade.Item? { model.grades[sectionId]?.items.first { $0.id == itemId } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if let it = item {
                    scoreCard(it)
                    if !it.comment.isEmpty { section("TEACHER'S COMMENT") { Text(it.comment).font(Theme.subhead).textSelection(.enabled) } }
                    if it.rubric != nil { rubricCard }
                    if let d = dropbox, !d.teacherComments.isEmpty {
                        section("COMMENTS ON YOUR SUBMISSION") {
                            ForEach(d.teacherComments, id: \.self) { c in
                                VStack(alignment: .leading, spacing: 2) {
                                    if let a = c.author, !a.isEmpty { Text(a).font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.secondary) }
                                    Text(c.text).font(Theme.subhead).textSelection(.enabled)
                                }
                            }
                        }
                    }
                    if let a = assignment, !a.attachments.isEmpty {
                        section("ATTACHED") {
                            ForEach(a.attachments, id: \.url) { att in
                                AttachmentRow(title: att.title, url: att.url) { t in
                                    switch t { case .file(let f): file = f; case .web(let u): LinkOpener.open(u) { sheet = $0 } }
                                }
                            }
                        }
                    }
                    if it.possible != nil {
                        WideButton(title: "Try a different score", systemImage: "slider.horizontal.3", filled: false) { whatIf = true }
                            .padding(.top, 4)
                    }
                    Button { sheet = SheetTarget(url: URL(string: "\(Engine.origin.absoluteString)/assignment/\(itemId)")!, assignmentId: itemId) } label: {
                        Label("Open in Schoology", systemImage: "globe").font(Theme.subhead.weight(.medium))
                    }
                } else {
                    SkeletonRows(count: 3)
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle(item?.title ?? "Graded work")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $sheet) { SchoologySheet(target: $0) }
        .sheet(item: $file) { FileViewer(target: $0) }
        .sheet(isPresented: $whatIf) { WhatIfSheet(itemId: itemId, sectionId: sectionId) }
        .task { await load() }
    }

    private func load() async {
        if item == nil { await model.loadGrades(sectionId) }
        model.markGradesSeen([itemId])
        guard let it = item else { return }
        if let r = it.rubric, let rid = r.rubricId, let eid = r.enrollmentId {
            do {
                rubric = try await model.engine.call("rubric", ["rubricId": rid, "enrollmentId": eid, "itemId": itemId])
            } catch let e as EngineError { rubricError = e } catch {}
        }
        if let a: Assignment = try? await model.engine.call("assignment", ["id": itemId]) {
            assignment = a
            if let url = a.dropboxUrl, a.submission?.state == "submitted" {
                dropbox = try? await model.engine.call("dropbox", ["url": url])
            }
        }
    }

    private func scoreCard(_ it: CourseGrade.Item) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(it.category.uppercased()).font(Theme.caption.weight(.semibold)).tracking(0.5).foregroundStyle(Theme.tertiary)
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(scoreText(it)).font(Theme.largeTitle.monospacedDigit())
                if let p = it.pct { Text(Fmt.pct(p)).font(Theme.body.monospacedDigit()).foregroundStyle(Theme.secondary) }
            }
            if let i = it.impact {
                Text(abs(i) < 0.005 ? "Didn't move your course grade" : "Moved your course grade \(Fmt.signed(i))")
                    .font(Theme.subhead.weight(.medium))
                    .foregroundStyle(abs(i) < 0.005 ? Theme.secondary : i < 0 ? Theme.late : Theme.good)
            }
            if let d = Fmt.wallDate(it.due) { Text("Due \(Fmt.dayTime.string(from: d))").font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .card()
    }

    @ViewBuilder
    private var rubricCard: some View {
        if let r = rubric {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(r.title.isEmpty ? "Rubric" : r.title).font(Theme.headline)
                    Spacer()
                    Text("\(Fmt.num(r.earned)) / \(Fmt.num(r.total))").font(Theme.subhead.weight(.semibold).monospacedDigit())
                }
                .padding(14)
                ForEach(r.criteria, id: \.self) { c in
                    Theme.divider.frame(height: 1)
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(c.title).font(Theme.subhead.weight(.medium))
                            Spacer()
                            Text("\(Fmt.num(c.earned)) / \(Fmt.num(c.max))").font(Theme.subhead.weight(.semibold).monospacedDigit())
                                .foregroundStyle(c.earned != nil && c.earned == c.max ? Theme.good : Theme.text)
                        }
                        if let l = c.level, !l.isEmpty { Text(l).font(Theme.footnote).foregroundStyle(Theme.secondary) }
                        if !c.comment.isEmpty { Text(c.comment).font(Theme.footnote).foregroundStyle(Theme.accent) }
                    }
                    .padding(14)
                }
            }
            .card()
        } else if let e = rubricError {
            section("RUBRIC") { Text(e.message).font(Theme.subhead).foregroundStyle(Theme.secondary) }
        } else {
            section("RUBRIC") { ProgressView() }
        }
    }

    private func section<C: View>(_ title: String, @ViewBuilder _ c: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(Theme.caption.weight(.semibold)).tracking(0.5).foregroundStyle(Theme.tertiary)
            c()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .card()
    }
}

/// "What if I get __ / N?" for one item: type a score, tap a quick one, or
/// slide; the course grade shows now → with this score, and an ungraded item
/// also shows the score that keeps the A.
struct WhatIfSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let itemId: String
    let sectionId: String?
    @State private var score = ""
    @State private var result: CourseGrade?
    @State private var needForA: PlanResult?
    @State private var gen = 0
    @FocusState private var typing: Bool

    private var item: CourseGrade.Item? { sectionId.flatMap { model.grades[$0]?.items.first { $0.id == itemId } } }
    /// The class's goal (Course grades' Goal chip).
    private var cutoff: Double { sectionId.map { model.goal($0) } ?? 93 }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    if let it = item, let sid = sectionId, let possible = it.possible, possible > 0 {
                        content(it, sid, possible)
                    } else {
                        Text("Load this class's grades first.").foregroundStyle(Theme.secondary)
                    }
                }
                .padding(20)
            }
            .background(Theme.background)
            .navigationTitle("What if")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .task { await loadNeed() }
        }
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder
    private func content(_ it: CourseGrade.Item, _ sid: String, _ possible: Double) -> some View {
        let real = model.grades[sid]
        let typed = Double(score)
        VStack(alignment: .leading, spacing: 4) {
            Text(it.title).font(Theme.headline)
            Text(it.earned != nil ? "Real score \(scoreText(it))\(it.pct.map { " · " + Fmt.pct($0, digits: 0) } ?? "")" : "Not graded yet")
                .font(Theme.footnote).foregroundStyle(Theme.secondary)
        }

        // The score: box, quick picks, slider.
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                TextField(it.earned.map { Fmt.num($0) } ?? "score", text: $score)
                    .keyboardType(.decimalPad).focused($typing)
                    .font(Theme.largeTitle.monospacedDigit()).multilineTextAlignment(.center)
                    .frame(width: 110).padding(.vertical, 6)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous).stroke(Theme.accent, lineWidth: typing ? 2 : 1))
                    .accessibilityLabel("What-if score, out of \(Fmt.num(possible))")
                Text("/ \(Fmt.num(possible))").font(Theme.title.weight(.regular).monospacedDigit()).foregroundStyle(Theme.secondary)
                Spacer()
                if let t = typed { Text(Fmt.pct(t / possible * 100, digits: 0)).font(Theme.title.monospacedDigit()).foregroundStyle(Theme.accent) }
            }
            HStack(spacing: 6) {
                ForEach([100.0, 90, 80, 70, 60], id: \.self) { pc in
                    let v = (possible * pc / 100 * 100).rounded() / 100
                    let on = typed == v
                    Button { typing = false; score = Fmt.num(v) } label: {
                        Text("\(Int(pc))%").font(Theme.footnote.weight(.semibold).monospacedDigit())
                            .frame(maxWidth: .infinity, minHeight: 32)
                            .foregroundStyle(on ? Theme.onAccent : Theme.accent)
                            .background(on ? Theme.accent : Theme.accentSoft, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(Int(pc)) percent, \(Fmt.num(v)) points")
                }
            }
            Slider(value: Binding(get: { min(typed ?? it.earned ?? 0, possible) },
                                  set: { score = Fmt.num(($0 * 2).rounded() / 2) }),
                   in: 0...possible)
                .accessibilityLabel("What-if score")
        }
        .padding(16)
        .card()
        .onChange(of: score) { _, _ in recompute(it, sid, possible) }

        // Now → with this score.
        HStack(spacing: 0) {
            gradeColumn("NOW", real?.letter, real?.pct, color: Theme.secondary)
            Image(systemName: "arrow.right").font(Theme.headline).foregroundStyle(Theme.tertiary).frame(width: 36)
            if let r = result, typed != nil {
                gradeColumn("WITH THIS SCORE", r.letter, r.pct, color: Theme.accent, delta: r.pct.flatMap { p in real?.pct.map { p - $0 } })
            } else {
                gradeColumn("WITH THIS SCORE", nil, nil, color: Theme.tertiary)
            }
        }
        .padding(16)
        .card()

        if it.earned == nil, let n = needForA {
            Text(needText(n, possible)).font(Theme.subhead.weight(.medium))
                .foregroundStyle(n.status == "out" ? Theme.late : Theme.good)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16).card()
        }
        Text("Only a preview. Nothing changes on Schoology.").font(Theme.footnote).foregroundStyle(Theme.tertiary)
    }

    private func gradeColumn(_ label: String, _ letter: String?, _ pct: Double?, color: Color, delta: Double? = nil) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(Theme.caption.weight(.bold)).foregroundStyle(color)
            Text(letter ?? "—").font(Theme.display).foregroundStyle(pct == nil ? Theme.tertiary : Theme.text)
            HStack(spacing: 6) {
                Text(Fmt.pct(pct, digits: 2)).font(Theme.subhead.monospacedDigit()).foregroundStyle(Theme.secondary)
                if let d = delta, abs(d) >= 0.005 {
                    Text(Fmt.signed(d)).font(Theme.footnote.weight(.semibold).monospacedDigit()).foregroundStyle(d < 0 ? Theme.late : Theme.good)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    private func recompute(_ it: CourseGrade.Item, _ sid: String, _ possible: Double) {
        guard let e = Double(score), let rows = model.gradeRows(sid) else { result = nil; return }
        gen += 1
        let g = gen
        Task {
            let r = try? await model.summarize(rows: rows, whatIf: [itemId: ["earned": e, "possible": possible]])
            if g == gen { result = r }
        }
    }

    /// An ungraded item counts like a new one in its category, so planTest gives the score that keeps the A.
    private func loadNeed() async {
        guard let it = item, it.earned == nil, let p = it.possible, let sid = sectionId else { return }
        needForA = await model.plan(sectionId: sid, category: it.category, possible: p, target: cutoff)
    }

    private func needText(_ n: PlanResult, _ possible: Double) -> String {
        let name = "\(Goal.letter(cutoff)) (\(Fmt.num(cutoff))%)"
        let reach = (sectionId.flatMap { model.grades[$0]?.pct } ?? 0) < cutoff
        switch n.status {
        case "any": return reach ? "Any score here gets you to \(name)." : "Any score here keeps your \(name)."
        case "out": return "Even \(Fmt.num(possible)) / \(Fmt.num(possible)) leaves you under \(name)."
        case "need":
            let need = min((n.need ?? 0).rounded(.up), possible)
            return "You need \(Fmt.num(need)) / \(Fmt.num(possible)) (\(Fmt.pct(need / possible * 100, digits: 0))) to \(reach ? "reach" : "keep") \(name)."
        default: return ""
        }
    }
}
