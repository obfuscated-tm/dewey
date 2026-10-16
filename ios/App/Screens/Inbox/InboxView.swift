import SwiftUI

/// R2 · Inbox: new school messages first (until opened), messages from teachers
/// (dot for new), new materials and grades in the student's classes, then School
/// (hub messages and the rest). Each is a box that folds away (School starts
/// folded) and has its own "Show older" at the bottom. Every new message counts
/// in the tab badge. Writing a message happens in the Schoology sheet.
struct InboxView: View {
    @Environment(AppModel.self) private var model
    @State private var sheet: SheetTarget?
    /// Folded boxes, by id, kept across launches.
    @AppStorage("inboxFolded") private var foldedRaw = "school"
    /// Rows shown per box; "Show older" adds a step.
    @State private var shown: [String: Int] = [:]
    @State private var fetching: Set<String> = []

    private static let step = 10

    var body: some View {
        let inbox = model.inbox
        let classes = model.classes
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    ScreenHeader(title: "Inbox") {
                        Button { sheet = SheetTarget(url: Engine.origin.appendingPathComponent("messages/new")) } label: {
                            Image(systemName: "square.and.pencil").font(Theme.title.weight(.regular)).foregroundStyle(Theme.secondary)
                                .frame(width: 44, height: 44)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("New message in Schoology")
                    }
                    .padding(.top, 8)
                    AsOfBanner(date: inbox.asOf, error: inbox.error)

                    if inbox.asOf == nil {
                        if let e = inbox.error {
                            ErrorState(error: e, retry: { Task { await inbox.refresh(model) } })
                        } else {
                            SkeletonRows(count: 4)
                        }
                    } else {
                        // A new school message sits at the top until it's opened, then goes back under School.
                        let newSchool = inbox.schoolThreads(classes).filter(inbox.isNew)
                        if !newSchool.isEmpty {
                            box("newSchool", "New from school", sources: [],
                                rows: { _ in newSchool.map(Row.thread) })
                        }
                        box("teachers", "From teachers", empty: "No messages from teachers", sources: [.messages],
                            rows: { i in i.teacherThreads(classes).map(Row.thread) })
                        box("classes", "New in your classes", empty: "Nothing new", sources: [.notices],
                            rows: { i in i.classNotices(classes).map(Row.notice) })
                        box("school", "School", empty: "Nothing from school", sources: [.messages, .notices],
                            rows: { i in
                                i.schoolThreads(classes).filter { !i.isNew($0) }.map(Row.thread)
                                    + i.otherNotices(classes).map(Row.notice)
                            })
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(Theme.background)
            .refreshable {
                await inbox.refresh(model)
                shown = [:]
            }
            .navigationDestination(for: MessageThread.self) { MessageView(thread: $0) }
            .navigationDestination(for: AssignmentLink.self) { AssignmentView(id: $0.id) }
            .toolbar(.hidden, for: .navigationBar)
            .sheet(item: $sheet) { SchoologySheet(target: $0) }
            .task {
                if inbox.asOf == nil || Date().timeIntervalSince(inbox.asOf!) > 120 { await inbox.refresh(model) }
            }
        }
    }

    // ── Boxes ────────────────────────────────────────────────────────────

    private enum Row: Identifiable {
        case thread(MessageThread)
        case notice(Notice)
        var id: String {
            switch self {
            case .thread(let t): return "m\(t.id)"
            case .notice(let n): return "n\(n.key)"
            }
        }
    }

    /// The Schoology lists a box's rows come from; "Show older" loads their next page.
    private enum Source { case messages, notices }

    private func isFolded(_ id: String) -> Bool { foldedRaw.split(separator: ",").contains(Substring(id)) }

    private func toggleFold(_ id: String) {
        var set = foldedRaw.split(separator: ",").map(String.init)
        if let i = set.firstIndex(of: id) { set.remove(at: i) } else { set.append(id) }
        withAnimation(.snappy) { foldedRaw = set.joined(separator: ",") }
    }

    private func hasOlder(_ sources: Set<Source>) -> Bool {
        (sources.contains(.messages) && model.inbox.hasOlderMessages)
            || (sources.contains(.notices) && model.inbox.hasOlderNotices)
    }

    @ViewBuilder
    private func box(_ id: String, _ title: String, empty: String? = nil, sources: Set<Source>,
                     rows: @escaping (Inbox) -> [Row]) -> some View {
        let all = rows(model.inbox)
        let folded = isFolded(id)
        let limit = shown[id] ?? Self.step
        let busy = fetching.contains(id)
        VStack(spacing: 0) {
            Button { toggleFold(id) } label: {
                HStack(spacing: 8) {
                    Text(title).font(Theme.headline)
                    Text("\(all.count)").font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.tertiary)
                    Spacer()
                    Image(systemName: "chevron.down")
                        .font(Theme.footnote.weight(.semibold))
                        .rotationEffect(.degrees(folded ? -90 : 0))
                        .foregroundStyle(Theme.tertiary)
                }
                .padding(.horizontal, 14)
                .frame(minHeight: 48)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(.isHeader)
            .accessibilityValue(folded ? "Folded" : "Open")
            .accessibilityHint(folded ? "Shows \(title)" : "Hides \(title)")

            if !folded {
                Theme.divider.frame(height: 1)
                if all.isEmpty, let empty { emptyRow(empty) }
                ForEach(Array(all.prefix(limit).enumerated()), id: \.element.id) { i, row in
                    switch row {
                    case .thread(let t): threadRow(t, divider: i > 0)
                    case .notice(let n): NoticeRow(notice: n, divider: i > 0) { sheet = $0 }
                    }
                }
                if all.count > limit || hasOlder(sources) {
                    Theme.divider.frame(height: 1)
                    Button { showOlder(id, sources: sources, rows: rows) } label: {
                        HStack(spacing: 8) {
                            if busy { ProgressView().controlSize(.small) }
                            Text(busy ? "Loading…" : "Show older")
                        }
                        .font(Theme.subhead.weight(.semibold))
                        .foregroundStyle(Theme.accent)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(busy)
                }
            }
        }
        .card()
    }

    /// Shows the next rows already loaded; if there are none, loads older pages
    /// (up to 3, in case a page holds nothing for this box).
    private func showOlder(_ id: String, sources: Set<Source>, rows: @escaping (Inbox) -> [Row]) {
        let limit = shown[id] ?? Self.step
        let inbox = model.inbox
        if rows(inbox).count > limit {
            withAnimation { shown[id] = limit + Self.step }
            return
        }
        fetching.insert(id)
        Task {
            for _ in 0..<3 where rows(inbox).count <= limit && hasOlder(sources) {
                if sources.contains(.messages), inbox.hasOlderMessages { await inbox.loadOlderMessages(model) }
                if sources.contains(.notices), inbox.hasOlderNotices { await inbox.loadOlderNotices(model) }
                if inbox.error != nil { break }
            }
            fetching.remove(id)
            withAnimation { shown[id] = limit + Self.step }
        }
    }

    private func emptyRow(_ s: String) -> some View {
        Text(s).font(Theme.subhead).foregroundStyle(Theme.secondary)
            .frame(maxWidth: .infinity, alignment: .leading).padding(14)
    }

    private func threadRow(_ t: MessageThread, divider: Bool) -> some View {
        NavigationLink(value: t) {
            ThreadRow(thread: t, isNew: model.inbox.isNew(t))
                .overlay(alignment: .top) { if divider { Theme.divider.frame(height: 1) } }
        }
        .buttonStyle(.plain)
    }
}

/// Opens AssignmentView from a notification.
struct AssignmentLink: Hashable { let id: String }

struct ThreadRow: View {
    let thread: MessageThread
    let isNew: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Circle().fill(isNew ? Theme.accent : .clear).frame(width: 8, height: 8).padding(.top, 6)
            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .firstTextBaseline) {
                    Text(thread.from ?? "Schoology").font(Theme.subhead.weight(isNew ? .bold : .semibold)).oneLine()
                    Spacer()
                    if let d = thread.when { Text(Fmt.ago(d)).font(Theme.footnote).foregroundStyle(Theme.tertiary) }
                }
                Text(thread.subject).font(Theme.subhead.weight(isNew ? .semibold : .regular)).oneLine()
                if let g = thread.group { Text(Course.short(g)).font(Theme.footnote).foregroundStyle(Theme.secondary).oneLine() }
                if let p = thread.preview, !p.isEmpty {
                    Text(p).font(Theme.footnote).foregroundStyle(Theme.secondary).maxLines(2)
                }
            }
        }
        .padding(14)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(isNew ? "New. " : "")\(thread.from ?? ""), \(thread.subject)")
    }
}

/// "Spanish 2 posted Prueba de pronombres …": each item opens (assignments natively, the rest in the sheet).
struct NoticeRow: View {
    let notice: Notice
    let divider: Bool
    let open: (SheetTarget) -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: notice.type == "grade" ? "checkmark.seal" : "doc.badge.plus")
                .font(Theme.subhead).foregroundStyle(notice.type == "grade" ? Theme.good : Theme.accent)
                .frame(width: 22).padding(.top, 1)
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .firstTextBaseline) {
                    Text(notice.course.map(Course.short) ?? (notice.type == "grade" ? "New grade" : "Schoology"))
                        .font(Theme.subhead.weight(.semibold)).oneLine()
                    Spacer()
                    if let d = notice.when { Text(Fmt.ago(d)).font(Theme.footnote).foregroundStyle(Theme.tertiary) }
                }
                ForEach(notice.items.prefix(4), id: \.url) { item in itemLink(item) }
                if notice.items.count > 4 {
                    Text("+\(notice.items.count - 4) more").font(Theme.footnote).foregroundStyle(Theme.tertiary)
                }
                if notice.items.isEmpty { Text(notice.text).font(Theme.footnote).foregroundStyle(Theme.secondary) }
            }
        }
        .padding(14)
        .overlay(alignment: .top) { if divider { Theme.divider.frame(height: 1) } }
    }

    @ViewBuilder
    private func itemLink(_ item: Notice.Item) -> some View {
        let label = Text(item.title).font(Theme.subhead).foregroundStyle(Theme.accent).maxLines(2)
            .multilineTextAlignment(.leading)
        if case .assignment(let id)? = WebConfig.nativeRoute(for: URL(string: item.url)) {
            NavigationLink(value: AssignmentLink(id: id)) { label }.buttonStyle(.plain)
        } else if let u = URL(string: item.url) {
            // Quizzes and everything else open in Schoology, by the student's tap.
            Button { open(SheetTarget(url: u)) } label: { label }.buttonStyle(.plain)
        }
    }
}
