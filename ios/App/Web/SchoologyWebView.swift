import SwiftUI
import WebKit

/// One visible web view (sign-in, the Schoology sheet, the Schoology tab), on
/// the shared cookie store. `window.open` / target=_blank load in the same view
/// (Google SSO uses it). On a Google page that refuses embedded browsers, it
/// switches to the Safari user agent, remembers that, and reloads.
@MainActor
@Observable
final class WebController: NSObject {
    let webView: WKWebView
    private(set) var url: URL?
    private(set) var title: String = ""
    private(set) var isLoading = false
    private(set) var canGoBack = false
    private(set) var canGoForward = false
    /// Called after every finished load with the page's URL.
    var onFinish: ((URL) -> Void)?
    /// Links that should leave the app's web view (e.g. a Docs link to the Docs app). Return true to cancel the load.
    var onLink: ((URL) -> Bool)?

    private var observations: [NSKeyValueObservation] = []

    init(url: URL? = nil) {
        let wv = WKWebView(frame: .zero, configuration: WebConfig.make())
        wv.customUserAgent = WebConfig.userAgent
        wv.allowsBackForwardNavigationGestures = true
        webView = wv
        super.init()
        wv.navigationDelegate = self
        wv.uiDelegate = self
        observations = [
            wv.observe(\.url, options: [.new]) { [weak self] wv, _ in Task { @MainActor in self?.url = wv.url } },
            wv.observe(\.title, options: [.new]) { [weak self] wv, _ in Task { @MainActor in self?.title = wv.title ?? "" } },
            wv.observe(\.isLoading, options: [.new]) { [weak self] wv, _ in Task { @MainActor in self?.isLoading = wv.isLoading } },
            wv.observe(\.canGoBack, options: [.new]) { [weak self] wv, _ in Task { @MainActor in self?.canGoBack = wv.canGoBack } },
            wv.observe(\.canGoForward, options: [.new]) { [weak self] wv, _ in Task { @MainActor in self?.canGoForward = wv.canGoForward } },
        ]
        if let url { load(url) }
    }

    func load(_ url: URL) {
        webView.load(URLRequest(url: url))
    }

    /// Loads the page's biggest frame (e.g. a Google Assignment) as the page itself,
    /// so its site is first-party and WebKit lets it use its own cookies.
    func openMainFrame() async -> Bool {
        let js = """
        (() => { const f = [...document.querySelectorAll('iframe')]
          .filter(f => /^https?:/.test(f.src) && f.offsetWidth * f.offsetHeight > 0)
          .sort((a, b) => b.offsetWidth * b.offsetHeight - a.offsetWidth * a.offsetHeight)[0];
          return f ? f.src : null })()
        """
        guard let src = try? await webView.evaluateJavaScript(js) as? String, let u = URL(string: src) else { return false }
        load(u)
        return true
    }

    func reload() {
        if webView.url == nil, let url { load(url) } else { webView.reload() }
    }

    func goBack() { webView.goBack() }
    func goForward() { webView.goForward() }

    private func checkGoogleRefusal(_ wv: WKWebView) {
        guard let host = wv.url?.host, host.hasSuffix("google.com"), !WebConfig.compatibilityMode else { return }
        Task { @MainActor in
            let text = (try? await wv.evaluateJavaScript("document.body ? document.body.innerText : ''")) as? String ?? ""
            if WebConfig.googleRefusals.contains(where: { text.localizedCaseInsensitiveContains($0) }) {
                WebConfig.compatibilityMode = true
                wv.customUserAgent = WebConfig.userAgent
                wv.load(URLRequest(url: Engine.origin))
            }
        }
    }
}

extension WebController: WKNavigationDelegate, WKUIDelegate {
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction) async -> WKNavigationActionPolicy {
        if let url = navigationAction.request.url, navigationAction.targetFrame?.isMainFrame != false,
           let onLink, onLink(url) {
            return .cancel
        }
        return .allow
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        checkGoogleRefusal(webView)
        if let url = webView.url { onFinish?(url) }
    }

    // window.open / target=_blank: same view.
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if navigationAction.targetFrame == nil { webView.load(navigationAction.request) }
        return nil
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        webView.reload()
    }

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo) async {
        _ = await Self.present(message: message, confirm: false)
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo) async -> Bool {
        await Self.present(message: message, confirm: true)
    }

    private static func present(message: String, confirm: Bool) async -> Bool {
        await withCheckedContinuation { c in
            let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in c.resume(returning: true) })
            if confirm {
                alert.addAction(UIAlertAction(title: "Cancel", style: .cancel) { _ in c.resume(returning: false) })
            }
            guard let top = UIApplication.shared.topViewController else { return c.resume(returning: false) }
            top.present(alert, animated: true)
        }
    }
}

extension WebController: Identifiable {}

struct SchoologyWebView: UIViewRepresentable {
    let controller: WebController

    func makeUIView(context: Context) -> WKWebView { controller.webView }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

extension UIApplication {
    var topViewController: UIViewController? {
        let root = connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
            .first { $0.isKeyWindow }?.rootViewController
        var top = root
        while let p = top?.presentedViewController { top = p }
        return top
    }
}
