import Foundation
import WebKit

/// Shared web view settings: one cookie store (the Schoology session) for the
/// engine, sign-in, the Schoology sheet and tab; the login user agent.
enum WebConfig {
    static let compatKey = "loginCompatibilityMode"

    /// Login compatibility mode: a Safari user agent for Google sign-in
    /// (docs/ios/FINDINGS.md, "Login"). Off by default; switched on
    /// automatically if Google refuses the embedded view.
    static var compatibilityMode: Bool {
        get { UserDefaults.standard.bool(forKey: compatKey) }
        set { UserDefaults.standard.set(newValue, forKey: compatKey) }
    }

    /// nil = WebKit's default. The default UA lacks the Version/… Safari/… tokens Google keys on.
    static var userAgent: String? {
        guard compatibilityMode else { return nil }
        return "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1"
    }

    static func make() -> WKWebViewConfiguration {
        let c = WKWebViewConfiguration()
        c.websiteDataStore = .default()
        c.allowsInlineMediaPlayback = true
        c.defaultWebpagePreferences.allowsContentJavaScript = true
        return c
    }

    /// On Schoology and not on its sign-in page.
    static func isSignedInURL(_ url: URL) -> Bool {
        guard url.host == Engine.host else { return false }
        return !url.path.hasPrefix("/login") && !url.path.hasPrefix("/saml")
    }

    /// Text Google shows when it refuses an embedded browser.
    static let googleRefusals = ["disallowed_useragent", "doesn't comply with Google's policies",
                                 "This browser or app may not be secure"]

    /// Schoology's login cookie has no expiry, so iOS drops it when the app
    /// quits and every cold launch would ask to sign in again. Give the
    /// Schoology session cookies a 30-day expiry so WebKit writes them to the
    /// app's own cookie file. Schoology still decides when the session ends.
    @MainActor
    static func keepSession() async {
        let store = WKWebsiteDataStore.default().httpCookieStore
        for c in await store.allCookies() where c.isSessionOnly && c.domain.hasSuffix("schoology.com") {
            var p = c.properties ?? [:]
            p[.discard] = nil
            p[.expires] = Date().addingTimeInterval(30 * 86400)
            if let kept = HTTPCookie(properties: p) { await store.setCookie(kept) }
        }
    }

    /// Forget the Schoology (and Google) session: every cookie and cache.
    @MainActor
    static func signOut() async {
        let store = WKWebsiteDataStore.default()
        await store.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince: .distantPast)
    }

    /// Schoology paths the app has a native screen for.
    enum NativeRoute: Equatable {
        case assignment(String)
        case grades(String)
        case materials(String)
    }

    static func nativeRoute(for url: URL?) -> NativeRoute? {
        guard let url, url.host == Engine.host else { return nil }
        let p = url.path
        if let m = p.firstMatch(of: /^\/assignment\/(\d+)/) { return .assignment(String(m.1)) }
        if let m = p.firstMatch(of: /^\/course\/(\d+)\/student_grades/) { return .grades(String(m.1)) }
        if let m = p.firstMatch(of: /^\/course\/(\d+)\/materials$/) { return .materials(String(m.1)) }
        return nil
    }
}
