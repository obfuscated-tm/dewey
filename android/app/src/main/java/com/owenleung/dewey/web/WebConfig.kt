package com.owenleung.dewey.web

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.store.Prefs
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Shared web view settings: one cookie store (the Schoology session) for the
 * engine, sign-in, the Schoology sheet and tab; the login user agent.
 * Android's CookieManager is one store for every WebView in the app.
 */
object WebConfig {
    const val COMPAT_KEY = "loginCompatibilityMode"

    /**
     * Login compatibility mode: a Chrome user agent without WebView's "; wv"
     * token. Google refuses sign-in in a WebView whose user agent says it is
     * one (disallowed_useragent), so on Android this is ON by default; the
     * Settings switch can turn it off. Switched back on automatically if
     * Google refuses the embedded view.
     */
    var compatibilityMode: Boolean
        get() = Prefs.bool(COMPAT_KEY, true)
        set(v) = Prefs.set(COMPAT_KEY, v)

    /** WebView's own UA, or the same without "; wv" and "Version/4.0" (looks like Chrome). */
    fun userAgent(wv: WebView): String {
        val base = WebSettings.getDefaultUserAgent(wv.context)
        if (!compatibilityMode) return base
        return chromeUserAgent(base)
    }

    fun chromeUserAgent(base: String): String =
        base.replace("; wv)", ")").replace(Regex("""Version/\d+(\.\d+)* """), "")

    @SuppressLint("SetJavaScriptEnabled")
    fun apply(wv: WebView) {
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true          // Schoology's desktop layout, pinch to zoom (FINDINGS.md)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.setSupportMultipleWindows(false) // window.open / target=_blank load in the same view (Google SSO)
        s.javaScriptCanOpenWindowsAutomatically = true
        s.mediaPlaybackRequiresUserGesture = false
        s.userAgentString = userAgent(wv)
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        // Google Assignments run in a google.com frame inside Schoology: let it use its own cookies.
        cm.setAcceptThirdPartyCookies(wv, true)
        // Don't tell Google which app this is (X-Requested-With), where WebView supports it.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(s, emptySet())
        }
    }

    /** On Schoology and not on its sign-in page. */
    fun isSignedInUrl(url: String?): Boolean {
        val u = url?.let(Uri::parse) ?: return false
        if (u.host != Engine.HOST) return false
        val p = u.path ?: ""
        return !p.startsWith("/login") && !p.startsWith("/saml")
    }

    /** Text Google shows when it refuses an embedded browser. */
    val googleRefusals = listOf(
        "disallowed_useragent", "doesn't comply with Google's policies",
        "This browser or app may not be secure", "Use a secure browser",
    )

    /** Writes cookies to disk now (WebView otherwise does it on its own schedule). */
    fun keepSession() {
        CookieManager.getInstance().flush()
    }

    /** Forget the Schoology (and Google) session: every cookie, web storage and cache. */
    suspend fun signOut() {
        suspendCancellableCoroutine { c -> CookieManager.getInstance().removeAllCookies { c.resume(Unit) } }
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    /** Schoology paths the app has a native screen for. */
    sealed class NativeRoute {
        data class Assignment(val id: String) : NativeRoute()
        data class Grades(val sectionId: String) : NativeRoute()
        data class Materials(val sectionId: String) : NativeRoute()
    }

    fun nativeRoute(url: String?): NativeRoute? {
        val u = url?.let(Uri::parse) ?: return null
        if (u.host != Engine.HOST) return null
        val p = u.path ?: return null
        Regex("""^/assignment/(\d+)""").find(p)?.let { return NativeRoute.Assignment(it.groupValues[1]) }
        Regex("""^/course/(\d+)/student_grades""").find(p)?.let { return NativeRoute.Grades(it.groupValues[1]) }
        Regex("""^/course/(\d+)/materials$""").find(p)?.let { return NativeRoute.Materials(it.groupValues[1]) }
        return null
    }

    fun abs(path: String): String = if (path.startsWith("http")) path else Engine.ORIGIN + (if (path.startsWith("/")) path else "/$path")
}
