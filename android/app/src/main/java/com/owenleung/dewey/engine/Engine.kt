package com.owenleung.dewey.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.owenleung.dewey.web.WebConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What an engine op can fail with (docs/ios/ENGINE.md, "Engine API"). */
sealed class EngineError(msg: String) : Exception(msg) {
    data object Login : EngineError("login")
    data object Quiz : EngineError("quiz")
    data object RateLimit : EngineError("ratelimit")
    data class Parse(val detail: String?) : EngineError("parse: $detail")
    data class Http(val status: Int?) : EngineError("http $status")
    data class Network(val detail: String?) : EngineError("network: $detail")
    data object BadArgs : EngineError("bad_args")
    data object NotReady : EngineError("not ready")
    data class Js(val detail: String) : EngineError("js: $detail")

    /** In the student's words. */
    val text: String
        get() = when (this) {
            Login -> "Signed out of Schoology."
            Quiz -> "This is a quiz page. It only opens when you tap it."
            RateLimit -> "Schoology asked us to slow down. Try again in a minute."
            is Parse -> "Couldn't read this page."
            is Http -> if (status == 403) "This was removed or made private on Schoology."
                else "Schoology answered with an error${status?.let { " ($it)" } ?: ""}."
            is Network -> "You're offline, or Schoology can't be reached."
            BadArgs -> "That link isn't one the app can open."
            NotReady -> "Still connecting to Schoology."
            is Js -> "Something went wrong: $detail"
        }

    companion object {
        fun from(code: String, status: Int?, message: String?): EngineError = when (code) {
            "login" -> Login
            "quiz" -> Quiz
            "ratelimit" -> RateLimit
            "http" -> Http(status)
            "network" -> Network(message)
            "bad_args" -> BadArgs
            "parse" -> Parse(message)
            else -> Js(message ?: code)
        }
    }
}

/** Engine JSON: keys go snake_case → camelCase first, like Swift's convertFromSnakeCase. */
object EngineJson {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; isLenient = true }

    fun camel(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.entries.associate { (k, v) -> camelKey(k) to camel(v) })
        is JsonArray -> JsonArray(e.map(::camel))
        else -> e
    }

    /** "section_id" → "sectionId"; leading/trailing underscores kept, like Foundation. */
    fun camelKey(k: String): String {
        if (!k.contains('_')) return k
        val core = k.trim('_')
        if (core.isEmpty()) return k
        val lead = k.takeWhile { it == '_' }
        val trail = k.takeLastWhile { it == '_' }
        val parts = core.split('_').filter { it.isNotEmpty() }
        val joined = parts.first().lowercase() + parts.drop(1).joinToString("") { it.lowercase().replaceFirstChar(Char::uppercase) }
        return lead + joined + trail
    }

    inline fun <reified T> decode(e: JsonElement): T = json.decodeFromJsonElement(serializer<T>(), camel(e))
    fun <T> decode(s: KSerializer<T>, e: JsonElement): T = json.decodeFromJsonElement(s, camel(e))
}

/**
 * The extension's parsers and grade math, running in a hidden WebView on the
 * Schoology origin (docs/ios/ENGINE.md; the Android side is docs/android/ANDROID-APP.md).
 * One per process, made and called on the main thread.
 *
 * The page is a small JSON endpoint; after it loads, the bundled `engine.js`
 * is evaluated into it. A call evaluates `SchoologyEngine.callJSON(op, args)`
 * and the promise hands its JSON string back through the `DeweyBridge`
 * JavaScript interface (evaluateJavascript can't wait for a promise).
 */
@SuppressLint("SetJavaScriptEnabled")
class Engine(private val context: Context) {
    companion object {
        const val HOST = "fuhsd.schoology.com"
        const val ORIGIN = "https://$HOST"
        private const val TAG = "DeweyEngine"
        const val HOME_PAGE = "$ORIGIN/iapi/course/active"
        private const val FALLBACK_PAGE = "$ORIGIN/home"
    }

    private val main = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var ready = false
    private var loading = false
    private var triedFallback = false
    private var waiters = mutableListOf<CompletableDeferred<Unit>>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()

    val script: String by lazy {
        runCatching { context.assets.open("engine.js").bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    val hasScript: Boolean get() = script.isNotEmpty()

    /** Called when Schoology's session is gone (a load ended on sign-in, or an op said `login`). */
    var onSignedOut: (() -> Unit)? = null

    private inner class Bridge {
        @JavascriptInterface
        fun resolve(id: String, json: String) {
            pending.remove(id)?.complete(json)
        }

        @JavascriptInterface
        fun reject(id: String, message: String) {
            pending.remove(id)?.completeExceptionally(EngineError.Js(message))
        }
    }

    /** Builds the hidden web view (once) and loads the engine page. Main thread. */
    fun start() {
        if (webView == null) {
            val wv = WebView(context.applicationContext)
            WebConfig.apply(wv)
            wv.addJavascriptInterface(Bridge(), "DeweyBridge")
            wv.webViewClient = Client()
            wv.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) Log.w(TAG, "js: ${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                    return true
                }
            }
            webView = wv
            place(wv)
        }
        reload()
    }

    private var host: ViewGroup? = null

    /**
     * Puts the hidden view in the activity's window (1×1, invisible). A WebView in
     * no window counts as a background tab: Chrome runs its timers at most once a
     * second, then once a minute after 5 minutes, and engine.js waits on a timer
     * before every request (ANDROID-APP.md).
     */
    fun attach(root: ViewGroup) {
        host = root
        webView?.let(::place)
    }

    fun detach(root: ViewGroup) {
        if (host !== root) return
        host = null
        webView?.let { (it.parent as? ViewGroup)?.removeView(it) }
    }

    private fun place(wv: WebView) {
        val root = host ?: return
        if (wv.parent === root) return
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.alpha = 0f
        root.addView(wv, 0, FrameLayout.LayoutParams(1, 1))
    }

    /** Reloads the engine page (after sign-in, a crash, or a sign-out). */
    fun reload() {
        val wv = webView ?: return start()
        ready = false
        loading = true
        triedFallback = false
        wv.settings.userAgentString = WebConfig.userAgent(wv)
        wv.loadUrl(HOME_PAGE)
    }

    private fun finishWaiters(error: Throwable?) {
        val w = waiters
        waiters = mutableListOf()
        for (d in w) if (error != null) d.completeExceptionally(error) else d.complete(Unit)
    }

    private suspend fun waitReady() {
        if (ready) return
        if (webView == null || !loading) start()
        val d = CompletableDeferred<Unit>()
        if (ready) return
        waiters.add(d)
        try {
            withTimeout(30_000) { d.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw EngineError.NotReady
        }
    }

    /** Runs one op; returns its `data` (camelCased later, on decode). Throws EngineError. */
    suspend fun callRaw(op: String, args: JsonObject = JsonObject(emptyMap())): JsonElement = withContext(Dispatchers.Main) {
        waitReady()
        val wv = webView ?: throw EngineError.NotReady
        val id = UUID.randomUUID().toString()
        val d = CompletableDeferred<String>()
        pending[id] = d
        val js = """
            (function(){ try {
              SchoologyEngine.callJSON(${JsonPrimitive(op)}, JSON.parse(${JsonPrimitive(args.toString())}))
                .then(function(r){ DeweyBridge.resolve(${JsonPrimitive(id)}, r) },
                      function(e){ DeweyBridge.reject(${JsonPrimitive(id)}, String(e)) });
            } catch (e) { DeweyBridge.reject(${JsonPrimitive(id)}, String(e)) } })();
        """.trimIndent()
        val t0 = System.currentTimeMillis()
        wv.evaluateJavascript(js, null)
        val text = try {
            withTimeout(120_000) { d.await() }.also { Log.d(TAG, "$op ${System.currentTimeMillis() - t0} ms") }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            pending.remove(id)
            Log.w(TAG, "$op: no answer after 120 s")
            reload()
            throw EngineError.Js("no answer from the engine")
        } catch (e: EngineError) {
            // The page navigated away or the engine isn't there: reload once.
            reload()
            throw e
        }
        val obj = runCatching { EngineJson.json.parseToJsonElement(text).jsonObject }.getOrElse { throw EngineError.Js("no result") }
        if ((obj["ok"] as? JsonPrimitive)?.booleanOrNull == true) return@withContext obj["data"] ?: JsonNull
        val err = EngineError.from(
            (obj["error"] as? JsonPrimitive)?.content ?: "",
            (obj["status"] as? JsonPrimitive)?.intOrNull,
            (obj["message"] as? JsonPrimitive)?.content,
        )
        if (err == EngineError.Login) {
            // One op's `login` isn't proof: a 403 page, a redirect off Schoology or
            // a page with a login box in it all say `login` while the session is
            // fine. Only sign out when Schoology's own course list says so.
            when (probeSession(wv)) {
                SessionProbe.Gone -> {
                    ready = false
                    onSignedOut?.invoke()
                }
                SessionProbe.Alive -> throw EngineError.Http(403)
                SessionProbe.Unknown -> throw EngineError.Network(null)
            }
        }
        throw err
    }

    private enum class SessionProbe { Alive, Gone, Unknown }

    /** Asks `/iapi/course/active` (no redirects followed) whether the session is still there. */
    private suspend fun probeSession(wv: WebView): SessionProbe {
        val id = UUID.randomUUID().toString()
        val d = CompletableDeferred<String>()
        pending[id] = d
        val js = """
            (async function(){
              let v = 'unknown';
              try {
                const r = await fetch('/iapi/course/active', { credentials: 'include', redirect: 'manual' });
                if (r.type === 'opaqueredirect' || (r.status >= 300 && r.status < 400) || r.status === 401) v = 'gone';
                else {
                  const t = await r.text();
                  if (/s-user-login-form|login-container/.test(t)) v = 'gone';
                  else if (r.ok) v = 'alive';
                }
              } catch (e) {}
              DeweyBridge.resolve(${JsonPrimitive(id)}, v);
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
        val v = try {
            withTimeout(20_000) { d.await() }
        } catch (e: Exception) {
            pending.remove(id)
            "unknown"
        }
        return when (v) {
            "gone" -> SessionProbe.Gone
            "alive" -> SessionProbe.Alive
            else -> SessionProbe.Unknown
        }
    }

    /** Runs one op and decodes its `data`. */
    suspend inline fun <reified T> call(op: String, args: Map<String, Any?> = emptyMap()): T {
        val data = callRaw(op, jsonArgs(args))
        return try {
            EngineJson.decode<T>(data)
        } catch (e: Exception) {
            throw EngineError.Parse("decode $op: ${e.message}")
        }
    }

    /** The raw result of an op as pretty JSON (Settings → Developer). */
    suspend fun debugCall(op: String, args: JsonObject): String = try {
        val data = callRaw(op, args)
        Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), data)
    } catch (e: EngineError) {
        "error: $e"
    } catch (e: Exception) {
        "error: $e"
    }

    private inner class Client : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            Log.d(TAG, "page finished: ${android.net.Uri.parse(url).host}${android.net.Uri.parse(url).path}")
            if (!WebConfig.isSignedInUrl(url)) {
                loading = false
                ready = false
                finishWaiters(EngineError.Login)
                onSignedOut?.invoke()
                return
            }
            CookieManager.getInstance().flush()
            view.evaluateJavascript("typeof SchoologyEngine === 'object'") { has ->
                if (has == "true") return@evaluateJavascript done(view, true)
                if (script.isEmpty()) return@evaluateJavascript done(view, false)
                view.evaluateJavascript(script + "\n;void 0") {
                    view.evaluateJavascript("typeof SchoologyEngine === 'object'") { ok -> done(view, ok == "true") }
                }
            }
        }

        private fun done(view: WebView, ok: Boolean) {
            if (!ok && !triedFallback) {
                // WebView didn't run scripts on the JSON page: use /home (ENGINE.md).
                triedFallback = true
                view.loadUrl(FALLBACK_PAGE)
                return
            }
            loading = false
            ready = ok
            Log.d(TAG, if (ok) "ready" else "engine.js did not load")
            finishWaiters(if (ok) null else EngineError.Js("engine.js did not load"))
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            loading = false
            finishWaiters(EngineError.Network(error.description?.toString()))
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // The renderer died: drop this view and build a new one.
            ready = false
            webView = null
            view.destroy()
            main.post { start() }
            return true
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
    }
}

/** Plain Kotlin values → a JSON object for the engine. */
fun jsonArgs(args: Map<String, Any?>): JsonObject = JsonObject(args.mapValues { (_, v) -> toJson(v) })

fun toJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to toJson(x) })
    is Iterable<*> -> JsonArray(v.map(::toJson))
    is Array<*> -> JsonArray(v.map(::toJson))
    else -> JsonPrimitive(v.toString())
}

val JsonElement.stringOrNull: String? get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
