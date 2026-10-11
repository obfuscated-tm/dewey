import SwiftUI

/// 3a · A tile per class (grade + open work), search across every class's
/// materials, and recently opened materials.
struct ClassesView: View {
    @Environment(AppModel.self) private var model
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
            .navigationDestination(for: Course.self) { MaterialsView(sectionId: $0.sectionId, folderId: nil, title: $0.shortTitle) }
            .navigationDestination(for: AssignmentRoute.self) { AssignmentView(id: $0.id) }
            .refreshable { try? await model.loadCourses() }
        }
    }

    private var tiles: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
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
            Text("Recently opened").font(Theme.font(20, .bold)).padding(.top, 8)
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
                Text("Reading materials… \(index.foldersRead) folders").font(Theme.font(13)).foregroundStyle(Theme.secondary)
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

    var body: some View {
        let open = model.todoItems().filter { $0.sectionId == course.sectionId && !model.isFinished($0) }.count
        let g = model.grades[course.sectionId]
        VStack(alignment: .leading, spacing: 8) {
            RoundedRectangle(cornerRadius: 3).fill(Color(hex: Self.colors[colorIndex % Self.colors.count])).frame(width: 28, height: 5)
            Text(course.shortTitle).font(Theme.font(15, .semibold)).foregroundStyle(Theme.text)
                .lineLimit(2).multilineTextAlignment(.leading).frame(maxWidth: .infinity, minHeight: 40, alignment: .topLeading)
            Text(course.periodLabel).font(Theme.font(12)).foregroundStyle(Theme.tertiary).lineLimit(1)
            HStack {
                if let l = g?.letter { Text(l).font(Theme.font(15, .bold)) }
                if let p = g?.pct { Text(Fmt.pct(p)).font(Theme.mono(12)).foregroundStyle(Theme.secondary) }
                Spacer()
                if open > 0 {
                    Text("\(open) open").font(Theme.font(12, .medium)).foregroundStyle(Theme.accent)
                }
            }
        }
        .padding(14)
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
            NavigationLink { MaterialsView(sectionId: sectionId, folderId: row.id, title: row.title) } label: { label("folder", "›") }
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
                  absURL?.host == Engine.host ? "Schoology ↗" : "\(absURL?.host ?? "") ↗")
        }
    }

    private func label(_ icon: String, _ trailing: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).frame(width: 24).foregroundStyle(row.kind == "folder" ? Theme.secondary : Theme.accent)
                .accessibilityLabel(row.kind == "folder" ? "Folder" : row.kind == "quiz" ? "Quiz" : "")
            VStack(alignment: .leading, spacing: 2) {
                Text(row.title).font(Theme.font(15, .medium)).foregroundStyle(Theme.text).lineLimit(2).multilineTextAlignment(.leading)
                if let subtitle, !subtitle.isEmpty { Text(subtitle).font(Theme.font(12.5)).foregroundStyle(Theme.secondary).lineLimit(1) }
            }
            Spacer()
            Text(trailing).font(Theme.font(12.5)).foregroundStyle(Theme.tertiary)
                .accessibilityHidden(trailing == "›")
        }
        .accessibilityElement(children: .combine)
        .padding(.horizontal, 14).frame(minHeight: 50)
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
