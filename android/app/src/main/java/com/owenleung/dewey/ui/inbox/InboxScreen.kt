package com.owenleung.dewey.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.EngineError
import com.owenleung.dewey.engine.MessageDetail
import com.owenleung.dewey.engine.MessageThread
import com.owenleung.dewey.engine.Notice
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.Inbox
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.ui.AsOfBanner
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.DetailScaffold
import com.owenleung.dewey.ui.Divider
import com.owenleung.dewey.ui.ErrorState
import com.owenleung.dewey.ui.MarkdownText
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.ScreenHeader
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.SheetTarget
import com.owenleung.dewey.ui.SkeletonRows
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.web.WebConfig
import kotlinx.coroutines.launch

/**
 * R2 · Inbox: new school messages first (until opened), messages from teachers
 * (dot for new), new materials and grades in the student's classes, then School
 * (hub messages and the rest, collapsed). Writing a message happens in the Schoology sheet.
 */
@Composable
fun InboxScreen() {
    val model = model
    val nav = nav
    val scope = rememberCoroutineScope()
    val inbox = model.inbox
    val classes = model.classes
    var showSchool by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val at = inbox.asOf
        if (at == null || System.currentTimeMillis() - at.time > 120_000) inbox.refresh(model)
    }
    ScrollPage(refresh = { inbox.refresh(model) }) {
        ScreenHeader("Inbox") {
            IconButton(onClick = { nav.openSheet(SheetTarget("${Engine.ORIGIN}/messages/new")) }) {
                Icon(Icons.Outlined.EditNote, "New message in Schoology", tint = C.secondary)
            }
        }
        AsOfBanner(inbox.asOf, inbox.error)
        if (inbox.asOf == null) {
            val e = inbox.error
            if (e != null) ErrorState(e, retry = { scope.launch { inbox.refresh(model) } }) else SkeletonRows(4)
            return@ScrollPage
        }
        // A new school message sits at the top until it's opened, then goes back under School.
        val newSchool = inbox.schoolThreads(classes).filter(inbox::isNew)
        if (newSchool.isNotEmpty()) Section("New from school") { newSchool.take(15).forEachIndexed { i, t -> if (i > 0) Divider(); ThreadRow(t, true) } }
        Section("From teachers") {
            val t = inbox.teacherThreads(classes)
            if (t.isEmpty()) EmptyRow("No messages from teachers")
            t.take(15).forEachIndexed { i, th -> if (i > 0) Divider(); ThreadRow(th, inbox.isNew(th)) }
        }
        Section("New in your classes") {
            val n = inbox.classNotices(classes)
            if (n.isEmpty()) EmptyRow("Nothing new")
            n.take(15).forEachIndexed { i, no -> if (i > 0) Divider(); NoticeRow(no) }
        }
        val msgs = inbox.schoolThreads(classes).filter { !inbox.isNew(it) }
        val other = inbox.otherNotices(classes)
        if (msgs.isNotEmpty() || other.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().clickable { showSchool = !showSchool }.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("School", style = Theme.title, color = C.text)
                Spacer(Modifier.width(6.dp))
                Text("${msgs.size + other.size}", style = Theme.footnote, color = C.tertiary, modifier = Modifier.weight(1f))
                Icon(if (showSchool) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (showSchool) "Hide school messages" else "Show school messages", tint = C.tertiary)
            }
            if (showSchool) Card {
                msgs.take(15).forEachIndexed { i, t -> if (i > 0) Divider(); ThreadRow(t, false) }
                other.take(10).forEachIndexed { i, no -> if (i > 0 || msgs.isNotEmpty()) Divider(); NoticeRow(no) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = Theme.title, color = C.text, modifier = Modifier.padding(top = 6.dp))
    Card { content() }
}

@Composable
private fun EmptyRow(s: String) = Text(s, style = Theme.subhead, color = C.secondary, modifier = Modifier.fillMaxWidth().padding(14.dp))

@Composable
private fun ThreadRow(t: MessageThread, isNew: Boolean) {
    val nav = nav
    Row(Modifier.fillMaxWidth().clickable { nav.push(Route.Message(t)) }.padding(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(if (isNew) C.accent else Color.Transparent))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.from ?: "Schoology", style = Theme.subhead.copy(fontWeight = if (isNew) FontWeight.Bold else FontWeight.SemiBold), color = C.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                t.time?.let { Text(Fmt.ago(it), style = Theme.footnote, color = C.tertiary) }
            }
            Text(t.subject, style = Theme.subhead.copy(fontWeight = if (isNew) FontWeight.SemiBold else FontWeight.Normal), color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            t.group?.let { Text(Course.short(it), style = Theme.footnote, color = C.secondary, maxLines = 1) }
            if (!t.preview.isNullOrEmpty()) Text(t.preview, style = Theme.footnote, color = C.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "French 2 posted Interro des Pronoms …": each item opens (assignments natively, the rest in the sheet). */
@Composable
private fun NoticeRow(n: Notice) {
    val nav = nav
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
        Icon(if (n.type == "grade") Icons.Outlined.Verified else Icons.Outlined.NoteAdd, null, tint = if (n.type == "grade") C.good else C.accent,
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.course?.let(Course::short) ?: if (n.type == "grade") "New grade" else "Schoology",
                    style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text, maxLines = 1, modifier = Modifier.weight(1f))
                n.time?.let { Text(Fmt.ago(it), style = Theme.footnote, color = C.tertiary) }
            }
            for (item in n.items.take(4)) {
                Text(item.title, style = Theme.subhead, color = C.accent, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        val route = WebConfig.nativeRoute(item.url)
                        // Quizzes and everything else open in Schoology, by the student's tap.
                        if (route is WebConfig.NativeRoute.Assignment) nav.push(Route.Assignment(route.id)) else nav.openSheet(SheetTarget(WebConfig.abs(item.url)))
                    })
            }
            if (n.items.size > 4) Text("+${n.items.size - 4} more", style = Theme.footnote, color = C.tertiary)
            if (n.items.isEmpty()) Text(n.text, style = Theme.footnote, color = C.secondary)
        }
    }
}

/**
 * One message thread. With "Mark messages read when opened" on, the app opens
 * the thread (which marks it read on Schoology) and shows every message; with
 * it off, only the list's preview shows. Replies happen in the Schoology sheet.
 */
@Composable
fun MessageScreen(thread: MessageThread) {
    val model = model
    val nav = nav
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Prefs.revision.intValue
    val markRead = Inbox.markRead
    var detail by remember { mutableStateOf<MessageDetail?>(null) }
    var error by remember { mutableStateOf<EngineError?>(null) }
    val url = "${Engine.ORIGIN}/messages/view/${thread.id}"
    suspend fun load() {
        try {
            detail = model.engine.call("message", mapOf("id" to thread.id))
            error = null
            model.inbox.markSeen(thread.id)
        } catch (e: EngineError) { error = e }
    }
    LaunchedEffect(thread.id) { if (markRead) load() }
    DetailScaffold("", actions = {
        TextButton(onClick = { nav.openSheet(SheetTarget(url)) }) {
            Text(if (markRead) "Reply" else "Open in Schoology", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.accent)
        }
    }) { pad ->
        ScrollPage(pad) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(detail?.subject ?: thread.subject, style = Theme.title, color = C.text)
                (detail?.group ?: thread.group)?.let { Text(Course.short(it), style = Theme.subhead, color = C.secondary) }
                detail?.participants?.takeIf { it.isNotEmpty() }?.let { Text("With ${it.joinToString(", ")}", style = Theme.footnote, color = C.tertiary) }
            }
            val d = detail
            when {
                !markRead -> Card(padding = 16.dp) {
                    Row {
                        Text(thread.from ?: "", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text, modifier = Modifier.weight(1f))
                        thread.time?.let { Text(Fmt.dayTime(it), style = Theme.footnote, color = C.tertiary) }
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(thread.preview ?: "", style = Theme.subhead, color = C.text)
                    Spacer(Modifier.size(10.dp))
                    Text("Only the preview shows, so the message stays unread on Schoology. Turn on \"Mark messages read when opened\" in Settings to read it here.",
                        style = Theme.footnote, color = C.tertiary)
                }
                d != null -> for (m in d.messages) {
                    Card(padding = 16.dp) {
                        Row {
                            Text(m.author ?: "", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text, modifier = Modifier.weight(1f))
                            Fmt.date(m.date)?.let { Text(Fmt.dayTime(it), style = Theme.footnote, color = C.tertiary) }
                        }
                        if (!m.bodyMd.isNullOrEmpty()) { Spacer(Modifier.size(10.dp)); MarkdownText(m.bodyMd) { u -> nav.openLink(ctx, u) } }
                        for (a in m.attachments) {
                            Row(Modifier.padding(top = 8.dp).clickable { nav.openLink(ctx, a.url) }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AttachFile, null, tint = C.accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(a.title, style = Theme.subhead, color = C.accent)
                            }
                        }
                    }
                }
                error != null -> ErrorState(error!!, url, retry = { scope.launch { load() } }, open = { nav.openSheet(SheetTarget(it)) })
                else -> SkeletonRows(2)
            }
        }
    }
}
