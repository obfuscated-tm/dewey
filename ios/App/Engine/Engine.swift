import UIKit
import WebKit

/// What an engine op can fail with (docs/ios/ENGINE.md, "Engine API").
enum EngineError: Error, Equatable {
    case login
    case quiz
    case rateLimit
    case parse(String?)
    case http(Int?)
    case network(String?)
    case badArgs
    case notReady
    case js(String)

    var message: String {
        switch self {
        case .login: return "Signed out of Schoology."
        case .quiz: return "This is a quiz page. It only opens when you tap it."
        case .rateLimit: return "Schoology asked us to slow down. Try again in a minute."
        case .parse: return "Couldn't read this page."
        case .http(403): return "This was removed or made private on Schoology."
        case .http(let s): return "Schoology answered with an error\(s.map { " (\($0))" } ?? "")."
        case .network: return "You're offline, or Schoology can't be reached."
        case .badArgs: return "That link isn't one the app can open."
        case .notReady: return "Still connecting to Schoology."
        case .js(let m): return "Something went wrong: \(m)"
        }
    }

    static func from(code: String, status: Int?, message: String?) -> EngineError {
        switch code {
        case "login": return .login
        case "quiz": return .quiz
        case "ratelimit": return .rateLimit
        case "http": return .http(status)
        case "network": return .network(message)
        case "bad_args": return .badArgs
        case "parse": return .parse(message)
        default: return .js(message ?? code)
        }
    }
}

/// The extension's parsers and grade math, running in a hidden WKWebView on the
/// Schoology origin (docs/ios/ENGINE.md). One for the whole app.
///
/// The page is a small JSON endpoint; the bundled `engine.js` is injected as a
/// user script and, if WebKit didn't run it there, evaluated directly. Calls go
/// through `callAsyncJavaScript` and come back as JSON strings.
@MainActor
final class Engine: NSObject {
    nonisolated static let host = "fuhsd.schoology.com"
    nonisolated static let origin = URL(string: "https://\(host)")!
    nonisolated static let homePage = URL(string: "https://\(host)/iapi/course/active")!

    private(set) var webView: WKWebView?
    private var ready = false
    private var loading = false
    private var waiters: [CheckedContinuation<Void, Error>] = []
    private let script: String

    /// Called when Schoology's session is gone (a load ended on sign-in, or an op said `login`).
    var onSignedOut: (() -> Void)?

    override init() {
        if let url = Bundle.main.url(forResource: "engine", withExtension: "js"),
           let s = try? String(contentsOf: url, encoding: .utf8) {
            script = s
        } else {
            script = ""
        }
        super.init()
    }

    var hasScript: Bool { !script.isEmpty }

    /// Builds the hidden web view (once) and loads the engine page.
    func start() {
        if webView == nil {
            let config = WebConfig.make()
            config.userContentController.addUserScript(
                WKUserScript(source: script, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
            let wv = WKWebView(frame: CGRect(x: 0, y: 0, width: 1, height: 1), configuration: config)
            wv.customUserAgent = WebConfig.userAgent
            wv.navigationDelegate = self
            wv.isUserInteractionEnabled = false
            wv.alpha = 0.01
            webView = wv
            attach(wv)
        }
        reload()
    }

    /// Reloads the engine page (after sign-in, a crash, or a sign-out).
    func reload() {
        guard let webView else { return start() }
        ready = false
        loading = true
        webView.customUserAgent = WebConfig.userAgent
        webView.load(URLRequest(url: Self.homePage))
    }

    // WebKit can stop running JS in a view that's in no window: keep it in the
    // key window, 1×1 and transparent.
    private func attach(_ wv: WKWebView) {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow } ?? UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }.first?.windows.first
        if let window, wv.superview == nil {
            window.insertSubview(wv, at: 0)
        }
    }

    private func finishWaiters(_ error: Error?) {
        let w = waiters
        waiters = []
        for c in w {
            if let error { c.resume(throwing: error) } else { c.resume() }
        }
    }

    private func waitReady() async throws {
        if ready { return }
        if webView == nil || !loading { start() }
        try await withThrowingTaskGroup(of: Void.self) { group in
            group.addTask { @MainActor in
                try await withCheckedThrowingContinuation { c in
                    if self.ready { c.resume() } else { self.waiters.append(c) }
                }
            }
            group.addTask {
                try await Task.sleep(nanoseconds: 30_000_000_000)
                throw EngineError.notReady
            }
            defer { group.cancelAll() }
            try await group.next()
        }
    }

    /// Runs one op; returns the JSON of its `data`. Throws `EngineError`.
    func callRaw(_ op: String, _ args: [String: Any] = [:]) async throws -> Data {
        try await waitReady()
        guard let webView else { throw EngineError.notReady }
        let value: Any?
        do {
            value = try await webView.callAsyncJavaScript(
                "return await SchoologyEngine.callJSON(op, args)",
                arguments: ["op": op, "args": args], in: nil, contentWorld: .page)
        } catch {
            // The page navigated away or the engine isn't there: reload once.
            reload()
            throw EngineError.js(error.localizedDescription)
        }
        guard let text = value as? String, let raw = text.data(using: .utf8),
              let obj = try JSONSerialization.jsonObject(with: raw) as? [String: Any] else {
            throw EngineError.js("no result")
        }
        if obj["ok"] as? Bool == true {
            let data = obj["data"] ?? NSNull()
            return try JSONSerialization.data(withJSONObject: data, options: [.fragmentsAllowed])
        }
        let err = EngineError.from(code: obj["error"] as? String ?? "", status: obj["status"] as? Int,
                                   message: obj["message"] as? String)
        if err == .login {
            // One op's `login` isn't proof: a 403 page, a redirect off Schoology or
            // a page with a login box in it all say `login` while the session is
            // fine. Only sign out when Schoology's own course list says so.
            switch await probeSession(webView) {
            case .gone:
                ready = false
                onSignedOut?()
            case .alive:
                throw EngineError.http(403)
            case .unknown:
                throw EngineError.network(nil)
            }
        }
        throw err
    }

    private enum SessionProbe { case alive, gone, unknown }

    /// Asks `/iapi/course/active` (no redirects followed) whether the session is still there.
    private func probeSession(_ webView: WKWebView) async -> SessionProbe {
        let js = """
        try {
          const r = await fetch('/iapi/course/active', { credentials: 'include', redirect: 'manual' });
          if (r.type === 'opaqueredirect' || (r.status >= 300 && r.status < 400) || r.status === 401) return 'gone';
          const t = await r.text();
          if (/s-user-login-form|login-container/.test(t)) return 'gone';
          return r.ok ? 'alive' : 'unknown';
        } catch (e) { return 'unknown'; }
        """
        let v = try? await webView.callAsyncJavaScript(js, arguments: [:], in: nil, contentWorld: .page)
        switch v as? String {
        case "gone": return .gone
        case "alive": return .alive
        default: return .unknown
        }
    }

    /// Runs one op and decodes its `data`.
    func call<T: Decodable>(_ op: String, _ args: [String: Any] = [:], as type: T.Type = T.self) async throws -> T {
        let data = try await callRaw(op, args)
        do {
            return try JSONDecoder.engine.decode(T.self, from: data)
        } catch {
            throw EngineError.parse("decode \(op): \(error)")
        }
    }

    /// The raw result of an op as a pretty JSON string (Settings → Developer).
    func debugCall(_ op: String, _ args: [String: Any] = [:]) async -> String {
        do {
            let data = try await callRaw(op, args)
            let obj = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
            let pretty = try JSONSerialization.data(withJSONObject: obj, options: [.prettyPrinted, .fragmentsAllowed])
            return String(decoding: pretty, as: UTF8.self)
        } catch let e as EngineError {
            return "error: \(e)"
        } catch {
            return "error: \(error)"
        }
    }
}

extension Engine: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        guard let url = webView.url else { return }
        if !WebConfig.isSignedInURL(url) {
            loading = false
            ready = false
            finishWaiters(EngineError.login)
            onSignedOut?()
            return
        }
        Task { @MainActor in
            let has = (try? await webView.evaluateJavaScript("typeof SchoologyEngine === 'object'")) as? Bool ?? false
            if !has, !script.isEmpty {
                _ = try? await webView.evaluateJavaScript(script + "\n;void 0")
            }
            let ok = (try? await webView.evaluateJavaScript("typeof SchoologyEngine === 'object'")) as? Bool ?? false
            loading = false
            ready = ok
            finishWaiters(ok ? nil : EngineError.js("engine.js did not load"))
        }
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        loading = false
        finishWaiters(EngineError.network(error.localizedDescription))
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        loading = false
        finishWaiters(EngineError.network(error.localizedDescription))
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        ready = false
        reload()
    }
}

extension JSONDecoder {
    /// Engine JSON: snake_case keys become camelCase.
    static let engine: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()
}
