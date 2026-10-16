package com.owenleung.dewey.web

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.owenleung.dewey.MainActivity
import com.owenleung.dewey.engine.Engine
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONTokener

/**
 * One visible web view (sign-in, the Schoology sheet, the Schoology tab), on
 * the shared cookie store. window.open / target=_blank load in the same view
 * (Google SSO uses it). On a Google page that refuses embedded browsers, it
 * switches to the Chrome user agent, remembers that, and reloads.
 */
@SuppressLint("SetJavaScriptEnabled")
class WebController(context: Context, startUrl: String? = null) {
    val webView: WebView = WebView(context)
    var url by mutableStateOf<String?>(null); private set
    var title by mutableStateOf(""); private set
    var isLoading by mutableStateOf(false); private set
    var canGoBack by mutableStateOf(false); private set
    var canGoForward by mutableStateOf(false); private set

    /** Called after every finished load with the page's URL. */
    var onFinish: ((String) -> Unit)? = null
    /** Links that should leave the app's web view. Return true to cancel the load. */
    var onLink: ((String) -> Boolean)? = null
    /** A file download (an attachment link): the app shows it natively. */
    var onDownload: ((url: String, name: String) -> Unit)? = null

    init {
        WebConfig.apply(webView)
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
        webView.setDownloadListener { u, _, disposition, mime, _ ->
            onDownload?.invoke(u, URLUtil.guessFileName(u, disposition, mime))
        }
        if (startUrl != null) load(startUrl)
    }

    fun load(u: String) = webView.loadUrl(u)
    fun reload() { if (webView.url == null) url?.let(::load) else webView.reload() }
    fun goBack() = webView.goBack()
    fun goForward() = webView.goForward()
    fun destroy() { (webView.parent as? ViewGroup)?.removeView(webView); webView.destroy() }

    private fun sync(v: WebView) {
        url = v.url
        title = v.title ?: ""
        canGoBack = v.canGoBack()
        canGoForward = v.canGoForward()
    }

    /**
     * Loads the page's biggest frame (e.g. a Google Assignment) as the page itself,
     * so its site is first-party and uses its own cookies.
     */
    suspend fun openMainFrame(): Boolean {
        val js = """
            (() => { const f = [...document.querySelectorAll('iframe')]
              .filter(f => /^https?:/.test(f.src) && f.offsetWidth * f.offsetHeight > 0)
              .sort((a, b) => b.offsetWidth * b.offsetHeight - a.offsetWidth * a.offsetHeight)[0];
              return f ? f.src : null })()
        """.trimIndent()
        val d = CompletableDeferred<String?>()
        webView.evaluateJavascript(js) { d.complete(it) }
        val src = d.await()?.let { runCatching { JSONTokener(it).nextValue() as? String }.getOrNull() } ?: return false
        load(src)
        return true
    }

    private fun checkGoogleRefusal(v: WebView) {
        val host = Uri.parse(v.url ?: return).host ?: return
        if (!host.endsWith("google.com") || WebConfig.compatibilityMode) return
        v.evaluateJavascript("document.body ? document.body.innerText : ''") { raw ->
            val text = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: ""
            if (WebConfig.googleRefusals.any { text.contains(it, ignoreCase = true) }) {
                WebConfig.compatibilityMode = true
                v.settings.userAgentString = WebConfig.userAgent(v)
                v.loadUrl(Engine.ORIGIN)
            }
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val u = request.url.toString()
            val scheme = request.url.scheme ?: ""
            if (scheme != "http" && scheme != "https") {
                // mailto:, intent:, tel: … go to the system.
                return try {
                    val i = if (scheme == "intent") Intent.parseUri(u, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, request.url)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    view.context.startActivity(i)
                    true
                } catch (e: ActivityNotFoundException) { true } catch (e: Exception) { true }
            }
            return onLink?.invoke(u) ?: false
        }

        override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
            isLoading = true
            sync(view)
        }

        override fun onPageFinished(view: WebView, u: String) {
            isLoading = false
            sync(view)
            checkGoogleRefusal(view)
            onFinish?.invoke(u)
        }

        override fun doUpdateVisitedHistory(view: WebView, u: String?, isReload: Boolean) = sync(view)

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            (view.parent as? ViewGroup)?.removeView(view)
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onReceivedTitle(view: WebView, t: String?) { title = t ?: "" }

        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean =
            MainActivity.chooseFiles(callback, params)

        // window.open with multiple windows off still asks here on some pages: load it in this view.
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            val href = view.hitTestResult.extra
            if (href != null && (href.startsWith("http"))) view.loadUrl(href)
            return false
        }
    }
}

/** Shows a WebController's view. The view outlives this composable (tabs keep their page). */
@Composable
fun WebViewHost(controller: WebController, modifier: Modifier = Modifier.fillMaxSize()) {
    AndroidView(
        factory = {
            (controller.webView.parent as? ViewGroup)?.removeView(controller.webView)
            controller.webView
        },
        modifier = modifier,
        onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
    )
}
