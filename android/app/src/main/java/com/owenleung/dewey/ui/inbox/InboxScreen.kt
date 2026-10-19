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
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.EngineError
import com.owenleung.dewey.engine.GroupPost
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
import com.owenleung.dewey.ui.markdown
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.ScreenHeader
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.ScreenOptions
import com.owenleung.dewey.ui.SettingsPage
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
 * (hub messages and the rest). Each is a box that folds away (School starts
 * folded) and has its own "Show older" at the bottom. Writing a message happens
 * in the Schoology sheet.
 */
@Composable
fun InboxScreen() {
    val model = model
    val nav = nav
    val scope = rememberCoroutineScope()
    val inbox = model.inbox
    val classes = model.classes
    /** Rows shown per box; "Show older" adds a step. */
    val shown = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(Unit) {
        val at = inbox.asOf
        if (at == null || System.currentTimeMillis() - at.time > 120_000) inbox.refresh(model)
    }
    ScrollPage(refresh = { inbox.refresh(model); shown.clear() }) {
        ScreenHeader("Inbox") {
            IconButton(onClick = { nav.openSheet(SheetTarget("${Engine.ORIGIN}/messages/new")) }) {
                Icon(Icons.Outlined.EditNote, "New message in Schoology", tint = C.secondary)
            }
            ScreenOptions(SettingsPage.INBOX)
        }
        AsOfBanner(inbox.asOf, inbox.error)
        if (inbox.asOf == null) {
            val e = inbox.error
            if (e != null) ErrorState(e, retry = { scope.launch { inbox.refresh(model) } }) else SkeletonRows(4)
            return@ScrollPage
        }
        // A new school message sits at the top until it's opened, then goes back under School.
        val newSchool = inbox.schoolThreads(classes).filter(inbox::isNew)
        if (newSchool.isNotEmpty()) InboxBox("newSchool", "New from school", shown, sources = emptySet()) { newSchool.map(InboxRow::Thread) }
        InboxBox("teachers", "From teachers", shown, empty = "No messages from teachers", sources = setOf(Source.MESSAGES)) {
            it.teacherThreads(classes).map(InboxRow::Thread)
        }
        InboxBox("classes", "New in your classes", shown, empty = "Nothing new", sources = setOf(Source.NOTICES)) {
            it.classNotices(classes).map(InboxRow::Notice)
        }
        // Only groups: a course switched to a class in Settings drops out right away.
        val posts = inbox.posts.filter { p -> classes.none { it.sectionId == p.sectionId } }
        if (posts.isNotEmpty()) InboxBox("updates", "School updates", shown, sources = emptySet()) { posts.map(InboxRow::Post) }
        InboxBox("school", "School", shown, empty = "Nothing from school", sources = setOf(Source.MESSAGES, Source.NOTICES)) { i ->
            i.schoolThreads(classes).filter { !i.isNew(it) }.map(InboxRow::Thread) + i.otherNotices(classes).map(InboxRow::Notice)
        }
    }
}

// ── Boxes ────────────────────────────────────────────────────────────

private sealed class InboxRow {
    data class Thread(val t: MessageThread) : InboxRow()
    data class Notice(val n: com.owenleung.dewey.engine.Notice) : InboxRow()
    data class Post(val p: GroupPost) : InboxRow()
}

/** The Schoology lists a box's rows come from; "Show older" loads their next page. */
private enum class Source { MESSAGES, NOTICES }

private const val STEP = 10
/** Folded boxes, by id, kept across launches. */
private const val FOLDED_KEY = "inboxFolded"

private fun folded(): List<String> = Prefs.string(FOLDED_KEY)?.split(",")?.filter { it.isNotEmpty() } ?: listOf("school")

private fun hasOlder(inbox: Inbox, sources: Set<Source>) =
    (Source.MESSAGES in sources && inbox.hasOlderMessages) || (Source.NOTICES in sources && inbox.hasOlderNotices)

@Composable
private fun InboxBox(id: String, title: String, shown: MutableMap<String, Int>, empty: String? = null, sources: Set<Source>,
                rows: (Inbox) -> List<InboxRow>) {
    val model = model
    val inbox = model.inbox
    val scope = rememberCoroutineScope()
    Prefs.revision.intValue
    val all = rows(inbox)
    val isFolded = id in folded()
    val limit = shown[id] ?: STEP
    var busy by remember { mutableStateOf(false) }

    fun showOlder() {
        if (all.size > limit) { shown[id] = limit + STEP; return }
        busy = true
        scope.launch {
            // Up to 3 pages, in case a page holds nothing for this box.
            for (n in 0 until 3) {
                if (rows(inbox).size > limit || !hasOlder(inbox, sources)) break
                if (Source.MESSAGES in sources && inbox.hasOlderMessages) inbox.loadOlderMessages(model)
                if (Source.NOTICES in sources && inbox.hasOlderNotices) inbox.loadOlderNotices(model)
                if (inbox.error != null) break
            }
            busy = false
            shown[id] = limit + STEP
        }
    }

    Card {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(onClickLabel = if (isFolded) "Show $title" else "Hide $title") {
                    val set = folded().toMutableList()
                    if (!set.remove(id)) set += id
                    Prefs.set(FOLDED_KEY, set.joinToString(","))
                }
                .semantics { heading(); stateDescription = if (isFolded) "Folded" else "Open" }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = Theme.headline, color = C.text)
            Spacer(Modifier.width(8.dp))
            Text("${all.size}", style = Theme.footnote, color = C.tertiary, modifier = Modifier.weight(1f))
            Icon(if (isFolded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null, tint = C.tertiary)
        }
        if (!isFolded) {
            Divider()
            if (all.isEmpty() && empty != null) EmptyRow(empty)
            all.take(limit).forEachIndexed { i, row ->
                if (i > 0) Divider()
                when (row) {
                    is InboxRow.Thread -> ThreadRow(row.t, inbox.isNew(row.t))
                    is InboxRow.Notice -> NoticeRow(row.n)
                    is InboxRow.Post -> PostRow(row.p)
                }
            }
            if (all.size > limit || hasOlder(inbox, sources)) {
                Divider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled = !busy) { showOlder() },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    if (busy) { CircularProgressIndicator(Modifier.size(16.dp), color = C.accent, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(if (busy) "Loading…" else "Show older", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.accent)
                }
            }
        }
    }
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

/** "Spanish 2 posted Prueba de pronombres …": each item opens (assignments natively, the rest in the sheet). */
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

/** A hub or group update: who posted where and when, the text folded to five lines (tap for all of it). */
@Composable
private fun PostRow(p: GroupPost) {
    val nav = nav
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Show less" else "Show the whole update") { open = !open }.padding(14.dp),
        verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Campaign, null, tint = C.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.group, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                p.`when`?.let { Text(Fmt.ago(it), style = Theme.footnote, color = C.tertiary) }
            }
            p.post.author?.let { Text(it, style = Theme.footnote, color = C.secondary) }
            Text(markdown(p.post.bodyMd ?: "", C.accent) { u -> nav.openLink(ctx, u) }, style = Theme.subhead, color = C.text,
                maxLines = if (open) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
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
