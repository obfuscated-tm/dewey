import SwiftUI
import WebKit

/// What the Schoology sheet (1c) opens. `assignmentId`: re-check its dropbox on close.
struct SheetTarget: Identifiable, Equatable {
    let url: URL
    var assignmentId: String?
    /// A Google Assignment: offer to open its frame full screen.
    var google = false
    var id: String { url.absoluteString }
}

/// 1c · Schoology's real page in a sheet, on the same cookie store. Submit,
/// quizzes, discussions, Google Assignments and outside pages all happen here,
/// by the student. On close, the assignment's status is checked again.
/// In the tour's pretend week it's a pretend dropbox instead: the real page
/// (and the student's real session) is never loaded there.
struct SchoologySheet: View {
    @Environment(AppModel.self) private var model
    let target: SheetTarget

    var body: some View {
        if model.practice { PracticeDropbox(target: target) } else { SchoologyWebSheet(target: target) }
    }
}

private struct SchoologyWebSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let target: SheetTarget
    @State private var web: WebController
    @State private var frameMissing = false

    init(target: SheetTarget) {
        self.target = target
        _web = State(initialValue: WebController(url: target.url))
    }

    var body: some View {
        NavigationStack {
            SchoologyWebView(controller: web)
                .ignoresSafeArea(edges: .bottom)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Done") { dismiss() }.fontWeight(.semibold)
                    }
                    ToolbarItem(placement: .principal) {
                        VStack(spacing: 0) {
                            Text(web.url?.host == Engine.host ? "Schoology" : (web.title.isEmpty ? "Page" : web.title))
                                .font(Theme.subhead.weight(.semibold)).oneLine()
                            HStack(spacing: 3) {
                                Image(systemName: web.url?.scheme == "https" ? "lock.fill" : "lock.open")
                                Text(web.url?.host ?? target.url.host ?? "")
                            }
                            .font(Theme.caption).foregroundStyle(Theme.secondary)
                        }
                    }
                    ToolbarItem(placement: .primaryAction) {
                        if web.isLoading { ProgressView() } else {
                            Button { web.reload() } label: { Label("Reload", systemImage: "arrow.clockwise").labelStyle(.iconOnly) }
                        }
                    }
                }
                .navigationBarTitleDisplayMode(.inline)
                .safeAreaInset(edge: .bottom) {
                    if target.google, web.url?.host == Engine.host {
                        Button {
                            Task { frameMissing = !(await web.openMainFrame()) }
                        } label: {
                            Label(frameMissing ? "No Google frame on this page yet" : "Open Google full screen", systemImage: "arrow.up.left.and.arrow.down.right")
                                .font(Theme.subhead.weight(.semibold)).frame(maxWidth: .infinity).padding(.vertical, 12)
                        }
                        .buttonStyle(.borderedProminent).padding(.horizontal, 16).padding(.bottom, 8)
                    }
                }
        }
        .onDisappear {
            guard let id = target.assignmentId else { return }
            Task { await model.recheckAfterSheet(id) }
        }
    }
}

extension AppModel {
    /// After the sheet closes: if Schoology now says submitted, say so and clear the row.
    func recheckAfterSheet(_ id: String) async {
        guard let it = item(id) else {
            // Not on the list: still worth knowing for an open Assignment screen.
            revision += 1
            return
        }
        let was = it.statusState
        it.statusCheckedAt = nil
        if let s = await checkStatus(it), s.state == "submitted", was != "submitted" {
            showToast(neoPlan.connected ? "Submitted on Schoology · Cleared from To Do and neo-plan" : "Submitted on Schoology · Cleared from To Do")
        }
    }
}

/// Where a link opens (3b's table): Docs/Drive → Google's app if installed,
/// else the sheet; everything else → the sheet.
enum LinkOpener {
    static func isGoogle(_ url: URL) -> Bool {
        guard let h = url.host else { return false }
        return h == "docs.google.com" || h == "drive.google.com"
    }

    @MainActor
    static func open(_ url: URL, assignmentId: String? = nil, sheet: @escaping (SheetTarget) -> Void) {
        if isGoogle(url) {
            UIApplication.shared.open(url, options: [.universalLinksOnly: true]) { ok in
                if !ok { sheet(SheetTarget(url: url)) }
            }
            return
        }
        sheet(SheetTarget(url: url, assignmentId: assignmentId))
    }
}
