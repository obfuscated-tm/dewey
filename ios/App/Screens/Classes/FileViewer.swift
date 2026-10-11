import CryptoKit
import PDFKit
import QuickLook
import SwiftUI
import WebKit

/// A file to show natively (3c).
struct FileTarget: Identifiable, Equatable {
    let url: URL
    let title: String
    var id: String { url.absoluteString }
}

enum OpenTarget { case file(FileTarget), web(URL) }

/// "Where it opens" for one attachment / file link (3b's table).
enum FileKinds {
    static let native: Set<String> = ["pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "png", "jpg", "jpeg", "gif", "heic", "txt", "rtf", "key", "pages", "numbers", "csv"]

    static func ext(_ s: String) -> String {
        let path = URL(string: s)?.path ?? s
        return (path as NSString).pathExtension.lowercased()
    }

    /// Schoology attachments (/attachment/…/source/…) and plain file URLs open in the viewer.
    static func target(title: String, url: String) -> OpenTarget? {
        guard let u = URL(string: url, relativeTo: Engine.origin)?.absoluteURL else { return nil }
        if LinkOpener.isGoogle(u) { return .web(u) }
        let e = ext(url).isEmpty ? ext(title) : ext(url)
        if u.path.contains("/attachment/") || native.contains(e) { return .file(FileTarget(url: u, title: title)) }
        return .web(u)
    }

    static func label(title: String, url: String) -> (icon: String, where: String) {
        guard let t = target(title: title, url: url) else { return ("link", "") }
        switch t {
        case .file: return (ext(url) == "pdf" || ext(title) == "pdf" ? "doc.richtext" : "doc", "Opens here ›")
        case .web(let u): return (LinkOpener.isGoogle(u) ? "doc.text" : "safari", LinkOpener.isGoogle(u) ? "Google ↗" : "\(u.host ?? "") ↗")
        }
    }
}

struct AttachmentRow: View {
    let title: String
    let url: String
    let open: (OpenTarget) -> Void
    var body: some View {
        let l = FileKinds.label(title: title, url: url)
        Button {
            if let t = FileKinds.target(title: title, url: url) { open(t) }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: l.icon).frame(width: 24).foregroundStyle(Theme.accent)
                Text(title).font(Theme.font(15, .medium)).foregroundStyle(Theme.text).lineLimit(2).multilineTextAlignment(.leading)
                Spacer()
                Text(l.where).font(Theme.font(12.5)).foregroundStyle(Theme.secondary)
            }
            .frame(minHeight: 44)
        }
        .buttonStyle(.plain)
    }
}

/// 3c · Downloads with the Schoology session (the shared WebKit cookies), then
/// PDFKit for PDFs, QuickLook for the rest. Cached for offline; Share / Save to Files.
struct FileViewer: View {
    let target: FileTarget
    @Environment(\.dismiss) private var dismiss
    @State private var local: URL?
    @State private var error: String?
    @State private var search = ""
    @State private var pdf: PDFDocument?

    var body: some View {
        NavigationStack {
            Group {
                if let local {
                    if local.pathExtension.lowercased() == "pdf", let pdf {
                        PDFKitView(document: pdf, search: search)
                            .searchable(text: $search, prompt: "Find in PDF")
                    } else {
                        QuickLookView(url: local)
                    }
                } else if let error {
                    VStack(spacing: 12) {
                        Text("Couldn't open this file").font(.headline)
                        Text(error).font(.subheadline).foregroundStyle(Theme.secondary).multilineTextAlignment(.center)
                    }.padding()
                } else {
                    ProgressView("Downloading…")
                }
            }
            .navigationTitle(target.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } }
                if let local {
                    ToolbarItem(placement: .primaryAction) { ShareLink(item: local) }
                }
            }
        }
        .task { await download() }
    }

    private func download() async {
        do {
            let f = try await FileStore.fetch(target)
            if f.pathExtension.lowercased() == "pdf" { pdf = PDFDocument(url: f) }
            local = f
        } catch {
            self.error = error.localizedDescription
        }
    }
}

enum FileStore {
    struct NotAFile: LocalizedError {
        var errorDescription: String? { "Schoology sent a web page instead of the file. You may need to sign in again." }
    }

    static let dir: URL = {
        let d = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("files", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }()

    /// GET with the WebKit cookies (the same session the engine uses). Cached by URL.
    @MainActor
    static func fetch(_ t: FileTarget) async throws -> URL {
        let key = SHA256.hash(data: Data(t.url.absoluteString.utf8)).prefix(8).map { String(format: "%02x", $0) }.joined()
        if let hit = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)
            .first(where: { $0.lastPathComponent.hasPrefix(key + "-") }) {
            return hit
        }
        let cookies = await WKWebsiteDataStore.default().httpCookieStore.allCookies()
            .filter { t.url.host?.hasSuffix($0.domain.trimmingCharacters(in: CharacterSet(charactersIn: "."))) ?? false }
        var req = URLRequest(url: t.url)
        HTTPCookie.requestHeaderFields(with: cookies).forEach { req.setValue($1, forHTTPHeaderField: $0) }
        let config = URLSessionConfiguration.ephemeral
        config.httpShouldSetCookies = false
        let (tmp, resp) = try await URLSession(configuration: config).download(for: req)
        let mime = (resp as? HTTPURLResponse)?.mimeType ?? ""
        if mime.contains("text/html") { throw NotAFile() }
        var name = resp.suggestedFilename ?? t.title
        if (name as NSString).pathExtension.isEmpty, let e = ext(forMime: mime) { name += "." + e }
        let safe = name.replacingOccurrences(of: "/", with: "-")
        let dest = dir.appendingPathComponent("\(key)-\(safe)")
        try? FileManager.default.removeItem(at: dest)
        try FileManager.default.moveItem(at: tmp, to: dest)
        return dest
    }

    static func ext(forMime m: String) -> String? {
        switch m {
        case "application/pdf": return "pdf"
        case "image/png": return "png"
        case "image/jpeg": return "jpg"
        case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return "docx"
        case "application/vnd.openxmlformats-officedocument.presentationml.presentation": return "pptx"
        case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": return "xlsx"
        case "application/msword": return "doc"
        default: return nil
        }
    }
}

struct PDFKitView: UIViewRepresentable {
    let document: PDFDocument
    var search: String
    func makeUIView(context: Context) -> PDFView {
        let v = PDFView()
        v.autoScales = true
        v.document = document
        return v
    }
    func updateUIView(_ v: PDFView, context: Context) {
        guard !search.isEmpty else { v.highlightedSelections = nil; return }
        let found = document.findString(search, withOptions: .caseInsensitive)
        v.highlightedSelections = found
        if let first = found.first { v.go(to: first) }
    }
}

struct QuickLookView: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> QLPreviewController {
        let c = QLPreviewController()
        c.dataSource = context.coordinator
        return c
    }
    func updateUIViewController(_ c: QLPreviewController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(url: url) }
    final class Coordinator: NSObject, QLPreviewControllerDataSource {
        let url: URL
        init(url: URL) { self.url = url }
        func numberOfPreviewItems(in controller: QLPreviewController) -> Int { 1 }
        func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem { url as NSURL }
    }
}
