package com.owenleung.dewey.ui.schoology

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.ui.Divider
import com.owenleung.dewey.ui.FileTarget
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.SheetTarget
import com.owenleung.dewey.ui.WideButton
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.web.WebConfig
import com.owenleung.dewey.web.WebController
import com.owenleung.dewey.web.WebViewHost
import kotlinx.coroutines.launch

/**
 * 1c · Schoology's real page over the app, on the same cookie store. Submit,
 * quizzes, discussions, Google Assignments and outside pages all happen here,
 * by the student. On close, the assignment's status is checked again.
 */
@Composable
fun SchoologySheet(target: SheetTarget, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val model = model
    val nav = nav
    val scope = rememberCoroutineScope()
    val web = remember(target) {
        WebController(ctx, target.url).apply {
            onDownload = { u, name -> nav.file = FileTarget(u, name) }
        }
    }
    var frameMissing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        onClose()
        target.assignmentId?.let(model::recheckAfterSheet)
    }
    DisposableEffect(web) { onDispose { web.destroy() } }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { if (web.canGoBack) web.goBack() else close() }
        Column(Modifier.fillMaxSize().background(C.card).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = close) { Text("Done", style = Theme.headline, color = C.accent) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val host = Uri.parse(web.url ?: target.url).host ?: ""
                    Text(if (host == Engine.HOST) "Schoology" else web.title.ifEmpty { "Page" },
                        style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if ((web.url ?: target.url).startsWith("https")) Icons.Outlined.Lock else Icons.Outlined.LockOpen, null,
                            tint = C.secondary, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(host, style = Theme.caption, color = C.secondary, maxLines = 1)
                    }
                }
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (web.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = C.accent)
                    else IconButton(onClick = web::reload) { Icon(Icons.Outlined.Refresh, "Reload", tint = C.accent) }
                }
            }
            Divider()
            Box(Modifier.weight(1f)) { WebViewHost(web) }
            if (target.google && Uri.parse(web.url ?: "").host == Engine.HOST) {
                WideButton(if (frameMissing) "No Google frame on this page yet" else "Open Google full screen", icon = Icons.Outlined.Fullscreen,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    scope.launch { frameMissing = !web.openMainFrame() }
                }
            }
        }
    }
}

/**
 * 3d · The full site, for everything without a native screen. Bottom bar:
 * back, forward, home, reload, share. On a page the app has a screen for, an
 * "Open in app" pill.
 */
@Composable
fun SchoologyTab(web: WebController) {
    val ctx = LocalContext.current
    val model = model
    val nav = nav
    web.onDownload = { u, name -> nav.file = FileTarget(u, name) }
    val route = WebConfig.nativeRoute(web.url)
    Column(Modifier.fillMaxSize().background(C.card)) {
        Box(Modifier.weight(1f)) {
            WebViewHost(web)
            if (route != null) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(C.accent)
                        .clickable {
                            when (route) {
                                is WebConfig.NativeRoute.Assignment -> nav.push(Route.Assignment(route.id))
                                is WebConfig.NativeRoute.Grades -> nav.push(Route.CourseGrades(route.sectionId))
                                is WebConfig.NativeRoute.Materials -> nav.push(
                                    Route.Materials(route.sectionId, null, model.course(route.sectionId)?.shortTitle ?: "Materials", emptyList()))
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.OpenInNew, null, tint = C.onAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open in app", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.onAccent)
                }
            }
        }
        Divider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = web::goBack, enabled = web.canGoBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            IconButton(onClick = web::goForward, enabled = web.canGoForward) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Forward") }
            IconButton(onClick = { web.load("${Engine.ORIGIN}/home") }) { Icon(Icons.Outlined.Home, "Schoology home") }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (web.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = C.accent)
                else IconButton(onClick = web::reload) { Icon(Icons.Outlined.Refresh, "Reload") }
            }
            IconButton(onClick = {
                val u = web.url ?: return@IconButton
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, u), null))
            }, enabled = web.url != null) { Icon(Icons.Outlined.Share, "Share") }
        }
    }
}
