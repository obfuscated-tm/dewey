import SwiftUI

/// 3b · One Materials folder. Every row says where it opens. "Only open work"
/// keeps folders and assignments still on the To Do list. The class switch
/// (Materials / Grades / Updates) sits at the top of the class's root.
struct MaterialsView: View {
    @Environment(AppModel.self) private var model
    let sectionId: String
    let folderId: String?
    let title: String
    /// The class name and the folders above this one (empty at the class's root).
    var trail: [String] = []
    @State private var rows: [MaterialRow]?
    @State private var error: EngineError?
    @State private var asOf: Date?
    @State private var onlyOpen = false
    @State private var sheet: SheetTarget?

    private var cacheKey: String { "materials-\(sectionId)-\(folderId ?? "root")" }
    private var pageURL: URL {
        URL(string: "\(Engine.origin.absoluteString)/course/\(sectionId)/materials\(folderId.map { "?f=\($0)" } ?? "")")!
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.m) {
                heading
                if folderId == nil, let c = model.course(sectionId) { classSwitch(c) }
                HStack {
                    AsOfBanner(date: asOf, error: error)
                    Spacer()
                    Toggle("Only open work", isOn: $onlyOpen).toggleStyle(.button).font(Theme.footnote.weight(.medium))
                }
                if let rows {
                    let shown = onlyOpen ? rows.filter(isOpen) : rows
                    if shown.isEmpty {
                        EmptyState(icon: "folder", title: onlyOpen ? "No open work here" : "Empty folder")
                    } else {
                        VStack(spacing: 0) {
                            ForEach(Array(shown.enumerated()), id: \.element.key) { i, r in
                                MaterialLink(sectionId: sectionId, row: r, subtitle: subtitle(r), trail: trail + [title])
                                    .overlay(alignment: .top) { if i > 0 { Theme.divider.frame(height: 1) } }
                            }
                        }
                        .card()
                    }
                } else if let error {
                    ErrorState(error: error, schoologyURL: pageURL, retry: { Task { await load() } }, open: { sheet = SheetTarget(url: $0) })
                } else {
                    SkeletonRows()
                }
            }
            .padding(Theme.Space.l)
        }
        .background(Theme.background)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // The title is in the page (it wraps instead of being cut off); the bar stays clear.
            ToolbarItem(placement: .principal) { Text("").accessibilityHidden(true) }
            ToolbarItem(placement: .primaryAction) {
                Button { sheet = SheetTarget(url: pageURL) } label: { Label("Open in Schoology", systemImage: "globe").labelStyle(.iconOnly) }
            }
        }
        .refreshable { await load() }
        .sheet(item: $sheet) { SchoologySheet(target: $0) }
        .task {
            if rows == nil, let c = Cache.load(cacheKey, as: [MaterialRow].self) { rows = c.value; asOf = c.savedAt }
            await load()
        }
    }

    /// Where you are: the class dot and the path above this folder, then the folder's
    /// name in full. Deep in nested folders this is what keeps you oriented.
    private var heading: some View {
        VStack(alignment: .leading, spacing: Theme.Space.xs) {
            if !trail.isEmpty {
                HStack(spacing: 6) {
                    Circle().fill(classColor).frame(width: 8, height: 8)
                    Text(trail.joined(separator: " › ")).font(Theme.footnote).foregroundStyle(Theme.secondary)
                        .lineLimit(1).truncationMode(.head)
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel("In " + trail.joined(separator: ", "))
            }
            HStack(spacing: Theme.Space.s) {
                if trail.isEmpty { Circle().fill(classColor).frame(width: 10, height: 10) }
                Text(title).font(Theme.title).foregroundStyle(Theme.text)
                    .accessibilityAddTraits(.isHeader)
            }
        }
    }

    private var classColor: Color {
        ClassTile.color(model.classes.firstIndex { $0.sectionId == sectionId } ?? 0)
    }

    private func load() async {
        var args: [String: Any] = ["sectionId": sectionId]
        if let folderId { args["folderId"] = folderId }
        do {
            let r: [MaterialRow] = try await model.engine.call("materials", args)
            rows = r
            asOf = Date()
            error = nil
            Cache.save(cacheKey, r)
        } catch let e as EngineError {
            error = e
        } catch {}
    }

    /// The To Do row for an assignment, if there is one.
    private func todo(_ r: MaterialRow) -> ItemState? {
        guard r.kind == "assignment", let id = r.id else { return nil }
        return model.item(id)
    }

    private func isOpen(_ r: MaterialRow) -> Bool {
        if r.kind == "folder" { return true }
        guard let it = todo(r) else { return false }
        return !model.isFinished(it) && !it.graded
    }

    private func subtitle(_ r: MaterialRow) -> String? {
        if let id = r.id, r.kind == "assignment", let gi = model.grades[sectionId]?.items.first(where: { $0.id == id }), gi.earned != nil {
            return scoreText(gi)
        }
        guard let it = todo(r) else { return nil }
        if it.submitted { return "Submitted" }
        if it.turnedIn { return "Turned in" }
        if let d = it.due { return "Due \(Fmt.due(d))" }
        return nil
    }

    private func classSwitch(_ c: Course) -> some View {
        HStack(spacing: 8) {
            Text("Materials").font(Theme.footnote.weight(.semibold))
                .padding(.horizontal, 12).frame(minHeight: 30)
                .foregroundStyle(Theme.onAccent).background(Theme.accent, in: Capsule())
            NavigationLink { CourseGradesView(course: c) } label: {
                Text("Grades").font(Theme.footnote.weight(.semibold))
                    .padding(.horizontal, 12).frame(minHeight: 30)
                    .foregroundStyle(Theme.accent).background(Theme.accentSoft, in: Capsule())
            }
            PillButton(title: "Updates", external: true) { sheet = SheetTarget(url: URL(string: "\(Engine.origin.absoluteString)/course/\(sectionId)/updates")!) }
            Spacer()
        }
    }
}

/// A Schoology page / link material, read through the engine: its text, files and links.
struct MaterialView: View {
    @Environment(AppModel.self) private var model
    let row: MaterialRow
    @State private var m: Material?
    @State private var error: EngineError?
    @State private var sheet: SheetTarget?
    @State private var file: FileTarget?

    private var url: URL? { row.url.flatMap { URL(string: $0, relativeTo: Engine.origin)?.absoluteURL } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if let m {
                    Text(m.title ?? row.title).font(Theme.title)
                    if let t = m.target, let u = URL(string: t) {
                        WideButton(title: "Open \(u.host ?? "link")", systemImage: "safari", external: true) { LinkOpener.open(u) { sheet = $0 } }
                    }
                    if let md = m.bodyMd, !md.isEmpty {
                        MarkdownText(md: md) { u in LinkOpener.open(u) { sheet = $0 } }.padding(16).frame(maxWidth: .infinity, alignment: .leading).card()
                    }
                    let all = m.files + m.links
                    if !all.isEmpty {
                        VStack(spacing: 0) {
                            ForEach(Array(all.enumerated()), id: \.offset) { i, f in
                                AttachmentRow(title: f.title, url: f.url) { t in
                                    switch t { case .file(let x): file = x; case .web(let u): LinkOpener.open(u) { sheet = $0 } }
                                }
                                .padding(.horizontal, 14)
                                .overlay(alignment: .top) { if i > 0 { Theme.divider.frame(height: 1) } }
                            }
                        }
                        .card()
                    }
                } else if let error {
                    ErrorState(error: error, schoologyURL: url, retry: { Task { await load() } }, open: { sheet = SheetTarget(url: $0) })
                } else {
                    SkeletonRows(count: 3)
                }
            }
            .padding(Theme.Space.l)
        }
        .background(Theme.background)
        .navigationTitle(row.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let url {
                ToolbarItem(placement: .primaryAction) { Button { sheet = SheetTarget(url: url) } label: { Label("Open in Schoology", systemImage: "globe").labelStyle(.iconOnly) } }
            }
        }
        .sheet(item: $sheet) { SchoologySheet(target: $0) }
        .sheet(item: $file) { FileViewer(target: $0) }
        .task { await load() }
    }

    private func load() async {
        guard let url else { error = .badArgs; return }
        do {
            m = try await model.engine.call("material", ["path": url.path])
            error = nil
        } catch let e as EngineError {
            error = e
        } catch {}
    }
}
