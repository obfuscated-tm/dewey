package com.owenleung.dewey.ui.classes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.ui.Divider
import com.owenleung.dewey.ui.FileTarget
import com.owenleung.dewey.ui.LinkOpener
import com.owenleung.dewey.ui.WideButton
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.web.WebConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class OpenTarget {
    data class FileOpen(val f: FileTarget) : OpenTarget()
    data class Web(val url: String) : OpenTarget()
}

/** "Where it opens" for one attachment / file link (3b's table). */
object FileKinds {
    val native = setOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "png", "jpg", "jpeg", "gif", "heic", "webp", "txt", "rtf", "csv")

    fun ext(s: String): String {
        val path = runCatching { Uri.parse(s).path }.getOrNull() ?: s
        return path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
    }

    /** Schoology attachments (/attachment/…/source/…) and plain file URLs open in the viewer. */
    fun target(title: String, url: String): OpenTarget {
        val u = WebConfig.abs(url)
        if (LinkOpener.isGoogle(u)) return OpenTarget.Web(u)
        val e = ext(url).ifEmpty { ext(title) }
        if ((Uri.parse(u).path ?: "").contains("/attachment/") || e in native) return OpenTarget.FileOpen(FileTarget(u, title))
        return OpenTarget.Web(u)
    }

    /** The row's icon and where it says it opens. */
    fun label(title: String, url: String) = when (val t = target(title, url)) {
        is OpenTarget.FileOpen -> (if (ext(url) == "pdf" || ext(title) == "pdf") Icons.Outlined.PictureAsPdf else Icons.Outlined.Description) to "Opens here ›"
        is OpenTarget.Web -> if (LinkOpener.isGoogle(t.url)) Icons.Outlined.Description to "Google ↗" else Icons.Outlined.Link to "${Uri.parse(t.url).host ?: ""} ↗"
    }
}

@Composable
fun AttachmentRow(title: String, url: String, modifier: Modifier = Modifier, open: (OpenTarget) -> Unit) {
    val (icon, where) = FileKinds.label(title, url)
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { open(FileKinds.target(title, url)) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = Theme.subhead.copy(fontWeight = FontWeight.Medium), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(where, style = Theme.footnote, color = C.secondary, maxLines = 1)
    }
}

/**
 * 3c · Downloads with the Schoology session (the shared WebView cookies), then
 * shows PDFs and images here; other files open in another app. Cached for
 * offline; Share / Open with.
 */
@Composable
fun FileViewer(target: FileTarget, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val state by produceState<Result<File>?>(null, target) { value = runCatching { FileStore.fetch(ctx, target) } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        Column(Modifier.fillMaxSize().background(C.background).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().background(C.card).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Done", style = Theme.headline, color = C.accent) }
                Text(target.title, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                val f = state?.getOrNull()
                IconButton(onClick = { f?.let { FileStore.share(ctx, it) } }, enabled = f != null) { Icon(Icons.Outlined.Share, "Share", tint = C.accent) }
            }
            Divider()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val r = state
                when {
                    r == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = C.accent)
                        Spacer(Modifier.size(8.dp))
                        Text("Downloading…", style = Theme.subhead, color = C.secondary)
                    }
                    r.isFailure -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Couldn't open this file", style = Theme.headline, color = C.text)
                        Text(r.exceptionOrNull()?.message ?: "", style = Theme.subhead, color = C.secondary, textAlign = TextAlign.Center)
                    }
                    else -> FileBody(r.getOrThrow())
                }
            }
        }
    }
}

@Composable
private fun FileBody(f: File) {
    val ctx = LocalContext.current
    when (f.extension.lowercase()) {
        "pdf" -> PdfView(f)
        "png", "jpg", "jpeg", "gif", "webp", "heic" -> {
            val bmp = remember(f) { BitmapFactory.decodeFile(f.path) }
            if (bmp != null) Zoomable { Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) }
        }
        "txt", "csv" -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(remember(f) { f.readText().take(200_000) }, style = Theme.subhead, color = C.text)
        }
        else -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Description, null, tint = C.tertiary, modifier = Modifier.size(48.dp))
            Text(f.name.substringAfter('-'), style = Theme.headline, color = C.text, textAlign = TextAlign.Center)
            Text("Saved on this phone. Open it in an app that reads ${f.extension.uppercase()} files.", style = Theme.subhead, color = C.secondary, textAlign = TextAlign.Center)
            WideButton("Open with…", icon = Icons.Outlined.OpenInNew) { FileStore.openWith(ctx, f) }
        }
    }
}

/** Pinch to zoom (two fingers; one finger still scrolls what's inside). */
@Composable
private fun Zoomable(content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.size >= 2) {
                        scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                        val pan = event.calculatePan()
                        if (scale == 1f) { dx = 0f; dy = 0f } else { dx += pan.x; dy += pan.y }
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = dx, translationY = dy),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Every page of a PDF, rendered with PdfRenderer as you scroll. */
@Composable
private fun PdfView(f: File) {
    val holder = remember(f) {
        runCatching {
            val fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            fd to PdfRenderer(fd)
        }.getOrNull()
    }
    DisposableEffect(holder) { onDispose { holder?.second?.close(); holder?.first?.close() } }
    val renderer = holder?.second ?: return Text("Couldn't read this PDF.", style = Theme.subhead, color = C.secondary)
    val count = renderer.pageCount
    Zoomable {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items((0 until count).toList()) { i ->
                val bmp by produceState<Bitmap?>(null, i) {
                    value = withContext(Dispatchers.IO) {
                        synchronized(renderer) {
                            renderer.openPage(i).use { p ->
                                val w = 1400
                                val b = Bitmap.createBitmap(w, (w.toFloat() * p.height / p.width).toInt(), Bitmap.Config.ARGB_8888)
                                b.eraseColor(android.graphics.Color.WHITE)
                                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                b
                            }
                        }
                    }
                }
                val b = bmp
                if (b != null) Image(b.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height))
                else Box(Modifier.fillMaxWidth().aspectRatio(0.77f).background(Color.White))
            }
        }
    }
}

object FileStore {
    class NotAFile : Exception("Schoology sent a web page instead of the file. You may need to sign in again.")

    fun dir(ctx: Context) = File(ctx.cacheDir, "files").apply { mkdirs() }

    /** GET with the WebView cookies (the same session the engine uses). Cached by URL. */
    suspend fun fetch(ctx: Context, t: FileTarget): File = withContext(Dispatchers.IO) {
        val key = MessageDigest.getInstance("SHA-256").digest(t.url.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        dir(ctx).listFiles()?.firstOrNull { it.name.startsWith("$key-") }?.let { return@withContext it }
        var url = URL(t.url)
        var c: HttpURLConnection
        var hops = 0
        while (true) {
            c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            CookieManager.getInstance().getCookie(url.toString())?.let { c.setRequestProperty("Cookie", it) }
            c.setRequestProperty("User-Agent", WebConfig.chromeUserAgent(System.getProperty("http.agent") ?: "Mozilla/5.0"))
            val code = c.responseCode
            if (code in 300..399 && hops < 8) {
                val loc = c.getHeaderField("Location") ?: break
                url = URL(url, loc)
                c.disconnect()
                hops++
                continue
            }
            break
        }
        if (c.responseCode !in 200..299) throw Exception("Schoology answered with an error (${c.responseCode}).")
        val mime = c.contentType?.substringBefore(';')?.trim() ?: ""
        if (mime.contains("text/html")) throw NotAFile()
        var name = c.getHeaderField("Content-Disposition")?.let { Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""").find(it)?.groupValues?.get(1) }
            ?.let { Uri.decode(it) } ?: t.title
        if (name.substringAfterLast('.', "").isEmpty()) MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { name += ".$it" }
        val dest = File(dir(ctx), "$key-${name.replace("/", "-")}")
        c.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
        c.disconnect()
        dest
    }

    private fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

    private fun mime(f: File) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream"

    fun share(ctx: Context, f: File) {
        val i = Intent(Intent.ACTION_SEND).setType(mime(f)).putExtra(Intent.EXTRA_STREAM, uri(ctx, f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, null))
    }

    fun openWith(ctx: Context, f: File) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri(ctx, f), mime(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { ctx.startActivity(Intent.createChooser(i, null)) }
    }
}

/** The origin for relative material links. */
val ORIGIN = Engine.ORIGIN
