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
                    Button("What if this were different?") { whatIf = true }
                        .font(Theme.subhead.weight(.medium))
                        .padding(.top, 4)
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
                Text(abs(i) < 0.005 ? "Didn't move your course grade" : "Moved your course grade \(Fmt.signed(i)) points")
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

/// "What if I get __ / N?" for one item: the course grade with that score.
struct WhatIfSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let itemId: String
    let sectionId: String?
    @State private var score = ""
    @State private var result: CourseGrade?

    private var item: CourseGrade.Item? { sectionId.flatMap { model.grades[$0]?.items.first { $0.id == itemId } } }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                if let it = item, let sid = sectionId {
                    Text(it.title).font(Theme.body.weight(.semibold))
                    HStack {
                        TextField("score", text: $score).keyboardType(.decimalPad).font(Theme.title.weight(.semibold).monospacedDigit())
                            .frame(width: 90).padding(8).background(Theme.accentSoft, in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
                        Text("/ \(Fmt.num(it.possible))").font(Theme.title.weight(.regular).monospacedDigit())
                    }
                    .onChange(of: score) { _, v in
                        guard let e = Double(v), let p = it.possible, let rows = model.gradeRows(sid) else { result = nil; return }
                        Task { result = try? await model.summarize(rows: rows, whatIf: [itemId: ["earned": e, "possible": p]]) }
                    }
                    if let r = result, let real = model.grades[sid]?.pct {
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(r.letter ?? "").font(Theme.largeTitle)
                            Text(Fmt.pct(r.pct, digits: 2)).font(Theme.body.monospacedDigit())
                            if let p = r.pct { Text(Fmt.signed(p - real)).font(Theme.subhead.monospacedDigit()).foregroundStyle(p < real ? Theme.late : Theme.good) }
                        }
                        Text("Your real grade stays \(Fmt.pct(real, digits: 2)). Nothing changes on Schoology.")
                            .font(Theme.footnote).foregroundStyle(Theme.secondary)
                    }
                } else {
                    Text("Load this class's grades first.").foregroundStyle(Theme.secondary)
                }
                Spacer()
            }
            .padding(20)
            .navigationTitle("What if")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .presentationDetents([.medium])
    }
}
