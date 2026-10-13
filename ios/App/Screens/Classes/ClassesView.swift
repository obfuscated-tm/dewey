import SwiftUI

/// 3a · A tile per class (grade + open work), search across every class's
/// materials, and recently opened materials.
struct ClassesView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var query = ""
    @State private var index = MaterialIndex.shared

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if query.isEmpty {
                        tiles
                        recent
                    } else {
                        results
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(Theme.background)
            .navigationTitle("Classes")
            .searchable(text: $query, prompt: "Search every class's materials")
            .onChange(of: query) { _, q in if !q.isEmpty { index.build(model) } }
            .navigationDestination(for: Course.self) { MaterialsView(sectionId: $0.sectionId, folderId: nil, title: $0.shortTitle, trail: []) }
            .navigationDestination(for: AssignmentRoute.self) { AssignmentView(id: $0.id) }
            .refreshable { try? await model.loadCourses() }
        }
    }

    private var tiles: some View {
        // Two columns; one at the accessibility text sizes so class names fit.
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: typeSize.isAccessibilitySize ? 1 : 2), spacing: 12) {
            ForEach(Array(model.classes.enumerated()), id: \.element.sectionId) { i, c in
                NavigationLink(value: c) { ClassTile(course: c, colorIndex: i) }
                    .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private var recent: some View {
        let r = RecentMaterials.list()
        if !r.isEmpty {
            Text("Recently opened").font(Theme.title).padding(.top, 8)
            VStack(spacing: 0) {
                ForEach(Array(r.prefix(6).enumerated()), id: \.offset) { i, e in
                    MaterialLink(sectionId: e.sectionId, row: e.row, subtitle: model.course(e.sectionId)?.shortTitle)
                        .overlay(alignment: .top) { if i > 0 { Theme.divider.frame(height: 1) } }
                }
            }
            .card()
        }
    }

    @ViewBuilder
    private var results: some View {
        let hits = index.search(query)
        if index.building {
            HStack(spacing: 8) {
                ProgressView().controlSize(.small)
                Text("Reading materials… \(index.foldersRead) folders").font(Theme.footnote).foregroundStyle(Theme.secondary)
            }
        }
        if hits.isEmpty && !index.building {
            EmptyState(icon: "magnifyingglass", title: "Nothing called that", detail: "Searches material names in every class.")
        }
        if !hits.isEmpty {
            VStack(spacing: 0) {
                ForEach(Array(hits.prefix(60).enumerated()), id: \.offset) { i, h in
                    let sub = [model.course(h.sectionId)?.shortTitle ?? "", h.path].filter { !$0.isEmpty }.joined(separator: " › ")
                    MaterialLink(sectionId: h.sectionId, row: h.row, subtitle: sub)
                        .overlay(alignment: .top) { if i > 0 { Theme.divider.frame(height: 1) } }
                }
            }
            .card()
        }
    }
}

struct ClassTile: View {
    @Environment(AppModel.self) private var model
    let course: Course
    let colorIndex: Int
    static let colors = ["#1D5EA8", "#23794A", "#6A4BA8", "#A8324A", "#B86E0F", "#16707A", "#5E6670", "#8A4B1D"]
    static func color(_ i: Int) -> Color { Color(hex: colors[i % colors.count]) }

    var body: some View {
        let open = model.todoItems().filter { $0.sectionId == course.sectionId && !model.isFinished($0) }.count
        let g = model.grades[course.sectionId]
        // Every tile the same height: the name reserves two lines, the grade row sits at the bottom.
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Capsule().fill(Self.color(colorIndex)).frame(width: 24, height: 4)
                Spacer(minLength: Theme.Space.s)
                Text(course.periodLabel).font(Theme.footnote).foregroundStyle(Theme.tertiary).oneLine()
            }
            Text(course.shortTitle).font(Theme.headline).foregroundStyle(Theme.text)
                .multilineTextAlignment(.leading)
                .lineLimit(2, reservesSpace: true)
                .padding(.top, Theme.Space.s)
            Spacer(minLength: Theme.Space.s)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(g?.letter ?? "–").font(Theme.body.weight(.bold))
                Text(g?.pct.map { Fmt.pct($0) } ?? "No grade").font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary)
                Spacer(minLength: 0)
                if open > 0 {
                    Text("\(open) open").font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.accent)
                }
            }
        }
        .padding(Theme.Space.m)
        .frame(maxWidth: .infinity, minHeight: 128, alignment: .topLeading)
        .card()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([course.shortTitle, course.periodLabel, g?.letter.map { "grade \($0)" }, open > 0 ? "\(open) open" : nil]
            .compactMap { $0 }.joined(separator: ", "))
        .accessibilityHint("Opens this class's materials")
    }
}

/// A row that opens a material where it belongs (3b's table).
struct MaterialLink: View {
    let sectionId: String
    let row: MaterialRow
    var subtitle: String?
    /// Class name, then each folder above this row: the path shown at the top of a folder.
    var trail: [String] = []
    @State private var sheet: SheetTarget?
    @State private var file: FileTarget?

    var body: some View {
        content
            .buttonStyle(.plain)
            .sheet(item: $sheet) { SchoologySheet(target: $0) }
            .sheet(item: $file) { FileViewer(target: $0) }
    }

    @ViewBuilder
    private var content: some View {
        switch row.kind {
        case "folder":
            NavigationLink { MaterialsView(sectionId: sectionId, folderId: row.id, title: row.title, trail: trail) } label: { label("folder", "›") }
        case "assignment":
            if let id = row.id {
                NavigationLink(value: AssignmentRoute(id: id)) { label("doc.text", "›") }
                    .simultaneousGesture(TapGesture().onEnded { RecentMaterials.add(sectionId, row) })
            } else { webButton }
        case "page", "other":
            if let url = absURL, Self.isMaterialPage(url) {
                NavigationLink { MaterialView(row: row) } label: { label(row.kind == "page" ? "doc.plaintext" : "link", "›") }
                    .simultaneousGesture(TapGesture().onEnded { RecentMaterials.add(sectionId, row) })
            } else { webButton }
        case "document":
            Button {
                RecentMaterials.add(sectionId, row)
                switch row.url.flatMap({ FileKinds.target(title: row.title, url: $0) }) {
                case .file(let f): file = f
                case .web(let u): LinkOpener.open(u) { sheet = $0 }
                case nil: break
                }
            } label: {
                let l = row.url.map { FileKinds.label(title: row.title, url: $0) }
                label(l?.icon ?? "doc", l?.where ?? "")
            }
        default:
            webButton
        }
    }

    private var absURL: URL? { row.url.flatMap { URL(string: $0, relativeTo: Engine.origin)?.absoluteURL } }

    static func isMaterialPage(_ u: URL) -> Bool {
        guard u.host == Engine.host else { return false }
        let p = u.path
        return p.firstMatch(of: /^\/page\/\d+$/) != nil || p.firstMatch(of: /^\/course\/\d+\/materials\/(gp|link\/view)\/\d+$/) != nil
    }

    /// Quizzes, discussions, anything else: the real page, only on tap.
    private var webButton: some View {
        Button {
            guard let url = absURL else { return }
            RecentMaterials.add(sectionId, row)
            LinkOpener.open(url) { sheet = $0 }
        } label: {
            label(row.kind == "quiz" ? "checklist" : row.kind == "discussion" ? "bubble.left.and.bubble.right" : "safari",
                  absURL?.host == Engine.host ? "Schoology ↗\u{FE0E}" : "\(absURL?.host ?? "") ↗\u{FE0E}")
        }
    }

    private func label(_ icon: String, _ trailing: String) -> some View {
        HStack(spacing: Theme.Space.m) {
            Image(systemName: icon).frame(width: 24).foregroundStyle(row.kind == "folder" ? Theme.secondary : Theme.accent)
                .accessibilityLabel(row.kind == "folder" ? "Folder" : row.kind == "quiz" ? "Quiz" : "")
            VStack(alignment: .leading, spacing: 2) {
                Text(row.title).font(Theme.subhead.weight(.medium)).foregroundStyle(Theme.text).maxLines(2).multilineTextAlignment(.leading)
                if let subtitle, !subtitle.isEmpty { Text(subtitle).font(Theme.footnote).foregroundStyle(Theme.secondary).oneLine() }
            }
            Spacer(minLength: Theme.Space.s)
            if trailing == "›" {
                Image(systemName: "chevron.right").font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.tertiary)
                    .accessibilityHidden(true)
            } else {
                Text(trailing).font(Theme.footnote).foregroundStyle(Theme.tertiary)
            }
        }
        .accessibilityElement(children: .combine)
        .padding(.horizontal, Theme.Space.l).padding(.vertical, Theme.Space.m).frame(minHeight: 50)
        .contentShape(Rectangle())
    }
}

/// Recently opened materials (local only).
enum RecentMaterials {
    struct Entry: Codable { var sectionId: String; var row: MaterialRow }
    static func list() -> [Entry] { Cache.load("recentMaterials", as: [Entry].self)?.value ?? [] }
    static func add(_ sectionId: String, _ row: MaterialRow) {
        var l = list().filter { $0.row.key != row.key }
        l.insert(Entry(sectionId: sectionId, row: row), at: 0)
        Cache.save("recentMaterials", Array(l.prefix(20)))
    }
}

/// Every class's Materials tree by name, like the MCP's find_material: walked
/// on the first search (folders through the engine, which paces requests),
/// cached for a day.
@MainActor
@Observable
final class MaterialIndex {
    static let shared = MaterialIndex()
    struct Hit: Codable { var sectionId: String; var path: String; var row: MaterialRow }

    private(set) var hits: [Hit]
    private(set) var building = false
    private(set) var foldersRead = 0
    private var builtAt: Date?

    init() {
        let c = Cache.load("materialIndex", as: [Hit].self)
        hits = c?.value ?? []
        builtAt = c?.savedAt
    }

    func search(_ q: String) -> [Hit] {
        let words = q.lowercased().split(separator: " ").map(String.init)
        return hits.filter { h in h.row.kind != "folder" && words.allSatisfy { h.row.title.lowercased().contains($0) } }
    }

    func build(_ model: AppModel, force: Bool = false) {
        guard !building else { return }
        if !force, let at = builtAt, Date().timeIntervalSince(at) < 24 * 3600, !hits.isEmpty { return }
        building = true
        foldersRead = 0
        Task {
            var out: [Hit] = []
            for c in model.classes {
                var queue: [(String?, String, Int)] = [(nil, "", 0)]
                while !queue.isEmpty {
                    let (fid, path, depth) = queue.removeFirst()
                    var args: [String: Any] = ["sectionId": c.sectionId]
                    if let fid { args["folderId"] = fid }
                    guard let rows: [MaterialRow] = try? await model.engine.call("materials", args) else { continue }
                    foldersRead += 1
                    for r in rows {
                        out.append(Hit(sectionId: c.sectionId, path: path, row: r))
                        if r.kind == "folder", let id = r.id, depth < 4 {
                            queue.append((id, path.isEmpty ? r.title : "\(path) › \(r.title)", depth + 1))
                        }
                    }
                    hits = out
                }
            }
            hits = out
            builtAt = Date()
            Cache.save("materialIndex", out)
            building = false
        }
    }
}
