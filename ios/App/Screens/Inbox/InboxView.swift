import SwiftUI

/// R2 · Inbox: messages from teachers (dot for new, counted in the tab badge),
/// new materials and grades in the student's classes, then School (hub
/// messages and the rest, collapsed, not counted). Writing a message happens
/// in the Schoology sheet.
struct InboxView: View {
    @Environment(AppModel.self) private var model
    @State private var sheet: SheetTarget?
    @State private var showSchool = false

    var body: some View {
        let inbox = model.inbox
        let classes = model.classes
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    ScreenHeader(title: "Inbox") {
                        Button { sheet = SheetTarget(url: Engine.origin.appendingPathComponent("messages/new")) } label: {
                            Image(systemName: "square.and.pencil").font(Theme.font(20)).foregroundStyle(Theme.secondary)
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
                        section("From teachers") {
                            let t = inbox.teacherThreads(classes)
                            if t.isEmpty { emptyRow("No messages from teachers") }
                            ForEach(Array(t.prefix(15).enumerated()), id: \.element.id) { i, th in
                                threadRow(th, divider: i > 0)
                            }
                        }
                        section("New in your classes") {
                            let n = inbox.classNotices(classes)
                            if n.isEmpty { emptyRow("Nothing new") }
                            ForEach(Array(n.prefix(15).enumerated()), id: \.element.id) { i, no in
                                NoticeRow(notice: no, divider: i > 0) { sheet = $0 }
                            }
                        }
                        school(inbox, classes)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(Theme.background)
            .refreshable { await inbox.refresh(model) }
            .navigationDestination(for: MessageThread.self) { MessageView(thread: $0) }
            .navigationDestination(for: AssignmentLink.self) { AssignmentView(id: $0.id) }
            .toolbar(.hidden, for: .navigationBar)
            .sheet(item: $sheet) { SchoologySheet(target: $0) }
            .task {
                if inbox.asOf == nil || Date().timeIntervalSince(inbox.asOf!) > 120 { await inbox.refresh(model) }
            }
        }
    }

    @ViewBuilder
    private func section<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        Text(title).font(Theme.font(20, .bold)).padding(.top, 6)
        VStack(spacing: 0) { content() }.card()
    }

    private func emptyRow(_ s: String) -> some View {
        Text(s).font(Theme.font(14)).foregroundStyle(Theme.secondary)
            .frame(maxWidth: .infinity, alignment: .leading).padding(14)
    }

    private func threadRow(_ t: MessageThread, divider: Bool) -> some View {
        NavigationLink(value: t) {
            ThreadRow(thread: t, isNew: model.inbox.isNew(t))
                .overlay(alignment: .top) { if divider { Theme.divider.frame(height: 1) } }
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func school(_ inbox: Inbox, _ classes: [Course]) -> some View {
        let msgs = inbox.schoolThreads(classes)
        let other = inbox.otherNotices(classes)
        if !msgs.isEmpty || !other.isEmpty {
            Button { withAnimation { showSchool.toggle() } } label: {
                HStack {
                    Text("School").font(Theme.font(20, .bold))
                    Text("\(msgs.count + other.count)").font(Theme.mono(13)).foregroundStyle(Theme.tertiary)
                    Spacer()
                    Image(systemName: "chevron.down").rotationEffect(.degrees(showSchool ? 0 : -90)).foregroundStyle(Theme.tertiary)
                }
                .padding(.top, 6)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint(showSchool ? "Hides school messages" : "Shows school messages")
            if showSchool {
                VStack(spacing: 0) {
                    ForEach(Array(msgs.prefix(15).enumerated()), id: \.element.id) { i, th in threadRow(th, divider: i > 0) }
                    ForEach(Array(other.prefix(10).enumerated()), id: \.element.id) { i, no in
                        NoticeRow(notice: no, divider: i > 0 || !msgs.isEmpty) { sheet = $0 }
                    }
                }
                .card()
            }
        }
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
                    Text(thread.from ?? "Schoology").font(Theme.font(15, isNew ? .bold : .semibold)).lineLimit(1)
                    Spacer()
                    if let d = thread.when { Text(Fmt.ago(d)).font(Theme.font(12.5)).foregroundStyle(Theme.tertiary) }
                }
                Text(thread.subject).font(Theme.font(14, isNew ? .semibold : .regular)).lineLimit(1)
                if let g = thread.group { Text(Course.short(g)).font(Theme.font(12.5)).foregroundStyle(Theme.secondary).lineLimit(1) }
                if let p = thread.preview, !p.isEmpty {
                    Text(p).font(Theme.font(13)).foregroundStyle(Theme.secondary).lineLimit(2)
                }
            }
        }
        .padding(14)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(isNew ? "New. " : "")\(thread.from ?? ""), \(thread.subject)")
    }
}

/// "French 2 posted Interro des Pronoms …": each item opens (assignments natively, the rest in the sheet).
struct NoticeRow: View {
    let notice: Notice
    let divider: Bool
    let open: (SheetTarget) -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: notice.type == "grade" ? "checkmark.seal" : "doc.badge.plus")
                .font(Theme.font(15)).foregroundStyle(notice.type == "grade" ? Theme.good : Theme.accent)
                .frame(width: 22).padding(.top, 1)
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .firstTextBaseline) {
                    Text(notice.course.map(Course.short) ?? (notice.type == "grade" ? "New grade" : "Schoology"))
                        .font(Theme.font(14, .semibold)).lineLimit(1)
                    Spacer()
                    if let d = notice.when { Text(Fmt.ago(d)).font(Theme.font(12.5)).foregroundStyle(Theme.tertiary) }
                }
                ForEach(notice.items.prefix(4), id: \.url) { item in itemLink(item) }
                if notice.items.count > 4 {
                    Text("+\(notice.items.count - 4) more").font(Theme.font(12.5)).foregroundStyle(Theme.tertiary)
                }
                if notice.items.isEmpty { Text(notice.text).font(Theme.font(13)).foregroundStyle(Theme.secondary) }
            }
        }
        .padding(14)
        .overlay(alignment: .top) { if divider { Theme.divider.frame(height: 1) } }
    }

    @ViewBuilder
    private func itemLink(_ item: Notice.Item) -> some View {
        let label = Text(item.title).font(Theme.font(14)).foregroundStyle(Theme.accent).lineLimit(2)
            .multilineTextAlignment(.leading)
        if case .assignment(let id)? = WebConfig.nativeRoute(for: URL(string: item.url)) {
            NavigationLink(value: AssignmentLink(id: id)) { label }.buttonStyle(.plain)
        } else if let u = URL(string: item.url) {
            // Quizzes and everything else open in Schoology, by the student's tap.
            Button { open(SheetTarget(url: u)) } label: { label }.buttonStyle(.plain)
        }
    }
}
