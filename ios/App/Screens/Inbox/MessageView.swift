import SwiftUI

/// One message thread. With "Mark messages read when opened" on, the app opens
/// the thread (which marks it read on Schoology) and shows every message; with
/// it off, only the list's preview shows and nothing is opened until the
/// student taps "Open in Schoology". Replies happen in the Schoology sheet.
struct MessageView: View {
    @Environment(AppModel.self) private var model
    let thread: MessageThread
    @State private var detail: MessageDetail?
    @State private var error: EngineError?
    @State private var sheet: SheetTarget?
    @AppStorage(Inbox.markReadKey) private var markRead = true

    private var schoologyURL: URL { Engine.origin.appendingPathComponent("messages/view/\(thread.id)") }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(detail?.subject ?? thread.subject).font(Theme.title)
                    if let g = detail?.group ?? thread.group {
                        Text(Course.short(g)).font(Theme.subhead).foregroundStyle(Theme.secondary)
                    }
                    if let p = detail?.participants, !p.isEmpty {
                        Text("With \(p.joined(separator: ", "))").font(Theme.footnote).foregroundStyle(Theme.tertiary)
                    }
                }
                if !markRead {
                    previewOnly
                } else if let detail {
                    ForEach(Array(detail.messages.enumerated()), id: \.offset) { _, msg in bubble(msg) }
                } else if let error {
                    ErrorState(error: error, schoologyURL: schoologyURL, retry: { Task { await load() } }, open: { sheet = SheetTarget(url: $0) })
                } else {
                    SkeletonRows(count: 2)
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button(markRead ? "Reply" : "Open in Schoology") { sheet = SheetTarget(url: schoologyURL) }
            }
        }
        .sheet(item: $sheet) { SchoologySheet(target: $0) }
        .task { if markRead { await load() } }
    }

    private func load() async {
        do {
            detail = try await model.engine.call("message", ["id": thread.id])
            error = nil
            model.inbox.markSeen(thread.id)
        } catch let e as EngineError {
            error = e
        } catch {
            self.error = .js(error.localizedDescription)
        }
    }

    private var previewOnly: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(thread.from ?? "").font(Theme.subhead.weight(.semibold))
                Spacer()
                if let d = thread.when { Text(Fmt.dayTime.string(from: d)).font(Theme.footnote).foregroundStyle(Theme.tertiary) }
            }
            Text(thread.preview ?? "").font(Theme.subhead)
            Text("Only the preview shows, so the message stays unread on Schoology. Turn on \"Mark messages read when opened\" in Settings to read it here.")
                .font(Theme.footnote).foregroundStyle(Theme.tertiary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    private func bubble(_ m: MessageDetail.Message) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(m.author ?? "").font(Theme.subhead.weight(.semibold))
                Spacer()
                if let d = Fmt.date(m.date) { Text(Fmt.dayTime.string(from: d)).font(Theme.footnote).foregroundStyle(Theme.tertiary) }
            }
            if let md = m.bodyMd, !md.isEmpty {
                MarkdownText(md: md) { u in LinkOpener.open(u) { sheet = $0 } }
            }
            ForEach(m.attachments, id: \.url) { a in
                Button {
                    if let u = URL(string: a.url) { LinkOpener.open(u) { sheet = $0 } }
                } label: {
                    Label(a.title, systemImage: "paperclip").font(Theme.subhead).foregroundStyle(Theme.accent)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }
}
