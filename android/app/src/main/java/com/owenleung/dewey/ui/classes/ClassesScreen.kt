package com.owenleung.dewey.ui.classes

import com.owenleung.dewey.ui.tour.coachTarget
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import com.owenleung.dewey.store.ItemState
import com.owenleung.dewey.ui.NewTag
import com.owenleung.dewey.ui.TypeTag
import com.owenleung.dewey.ui.todo.CheckMark
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Badge
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.MaterialRow
import com.owenleung.dewey.store.Cache
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.Divider
import com.owenleung.dewey.ui.EmptyState
import com.owenleung.dewey.ui.Nav
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.ScreenHeader
import com.owenleung.dewey.ui.ScreenOptions
import com.owenleung.dewey.ui.SettingsPage
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.SheetTarget
import com.owenleung.dewey.ui.SkeletonRows
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.web.WebConfig
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.Date

/** 3a · A shelf of classes (grade + what's due, see Shelf), search across every class's materials, and recently opened materials. */
@Composable
fun ClassesScreen() {
    val model = model
    val nav = nav
    var query by rememberSaveable { mutableStateOf("") }
    ScrollPage(refresh = { runCatching { model.loadCourses() } }, spacing = 14.dp) {
        ScreenHeader("Classes") {
            IconButton(onClick = { nav.showID = true }, modifier = Modifier.size(44.dp).coachTarget("classes.id")) {
                Icon(Icons.Outlined.Badge, "Student ID", tint = C.secondary)
            }
            ScreenOptions(SettingsPage.CLASSES)
        }
        SearchField(query, "Search every class's materials") { q ->
            query = q
            if (q.isNotEmpty()) MaterialIndex.build(model)
        }
        if (query.isEmpty()) {
            if (model.classes.isEmpty()) SkeletonRows(4) else Shelf()
            val recent = RecentMaterials.list()
            if (recent.isNotEmpty()) {
                Text("Recently opened", style = Theme.title, color = C.text, modifier = Modifier.padding(top = 8.dp))
                Card { recent.take(6).forEachIndexed { i, e -> if (i > 0) Divider(); MaterialLink(e.sectionId, e.row, model.course(e.sectionId)?.shortTitle) } }
            }
        } else {
            val hits = MaterialIndex.search(query)
            if (MaterialIndex.building) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = C.secondary)
                Spacer(Modifier.width(8.dp))
                Text("Reading materials… ${MaterialIndex.foldersRead} folders", style = Theme.footnote, color = C.secondary)
            }
            if (hits.isEmpty() && !MaterialIndex.building) EmptyState("Nothing called that", "Searches material names in every class.", icon = Icons.Outlined.Search)
            if (hits.isNotEmpty()) Card {
                hits.take(60).forEachIndexed { i, h ->
                    if (i > 0) Divider()
                    val c = model.course(h.sectionId)
                    val f = c?.let { h.containingFolder(it.shortTitle) }
                    MaterialLink(h.sectionId, h.row, listOf(c?.shortTitle ?: "", h.path).filter { it.isNotEmpty() }.joinToString(" › "),
                        isNew = MaterialIndex.isNew(h.sectionId, h.row),
                        showIn = if (c != null && f != null) f.title to {
                            nav.push(Route.Materials(c.sectionId, null, c.shortTitle, emptyList())); nav.push(f)
                        } else null)
                }
            }
        }
    }
}

/** The search box under a page's title (Classes, and a class page). */
@Composable
internal fun SearchField(q: String, prompt: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.Radius.card)).background(C.tagSoft).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Search, null, tint = C.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (q.isEmpty()) Text(prompt, style = Theme.body, color = C.tertiary)
            BasicTextField(q, onChange, singleLine = true, textStyle = Theme.body.copy(color = C.text), cursorBrush = SolidColor(C.accent),
                modifier = Modifier.fillMaxWidth())
        }
        if (q.isNotEmpty()) Text("Cancel", style = Theme.subhead, color = C.accent, modifier = Modifier.clickable { onChange("") }.padding(start = 8.dp))
    }
}

/**
 * Pops the tab's stack back `n` screens, then pushes `push` if given: the tappable path
 * above a folder (`FolderPath.jump`), and a class page's search opening the folder a hit is
 * in. Every tab's stack is a plain list here, so Classes and the Schoology tab both get it.
 */
fun Nav.popFolders(n: Int, push: Route? = null) {
    val s = stacks[tab]
    repeat(minOf(n, s.size)) { s.removeAt(s.lastIndex) }
    if (push != null) s += push
}

/**
 * A row that opens a material where it belongs (3b's table). Assignments and quizzes are
 * work: the To Do mark (show-only) and type tag lead the row, with one status line under
 * it (`MaterialStatus`). Everything else is quiet: a grey icon and a word for what it is.
 *
 * `item`: the student's own state for a work row, if Dewey has one. `graded`: it has a score
 * in the gradebook (finished even with no saved state). `trail`: the class name, then each
 * folder above this row; with `trailIds` / `onStack` for a folder row (Route.Materials).
 * `isNew`: added since the student last looked; `hasNewInside`: a folder with something new
 * somewhere inside (a dot). `showIn`: a search hit's folder, for the long-press "Show in …".
 */
@Composable
fun MaterialLink(
    sectionId: String, row: MaterialRow, subtitle: String? = null, trail: List<String> = emptyList(),
    subtitleLate: Boolean = false, graded: Boolean = false, item: ItemState? = null,
    trailIds: List<String?> = emptyList(), onStack: List<Boolean> = emptyList(),
    isNew: Boolean = false, hasNewInside: Boolean = false, showIn: Pair<String, () -> Unit>? = null,
) {
    val nav = nav
    val ctx = LocalContext.current
    val abs = row.url?.let(WebConfig::abs)
    fun opened() {
        RecentMaterials.add(sectionId, row)
        MaterialIndex.markSeen(sectionId, listOf(row.key))
    }
    fun web() { if (abs != null) { opened(); nav.openLink(ctx, abs) } }
    val external = when {
        row.kind == "discussion" -> "Discussion ↗"
        Uri.parse(abs ?: "").host == Engine.HOST -> "Schoology ↗"
        else -> "${Uri.parse(abs ?: "").host ?: ""} ↗"
    }
    @Composable
    fun Link(icon: ImageVector, trailing: String, word: String? = null, onClick: () -> Unit) =
        LinkRow(icon, row, subtitle, trailing, word, subtitleLate, graded, item, isNew, hasNewInside, showIn, onClick)
    when (row.kind) {
        "folder" -> Link(Icons.Outlined.Folder, ">") {
            MaterialIndex.markSeen(sectionId, listOf(row.key))
            nav.push(Route.Materials(sectionId, row.id, row.title, trail, trailIds, onStack))
        }
        "assignment" -> if (row.id != null) Link(Icons.Outlined.Description, ">") {
            opened(); nav.push(Route.Assignment(row.id, sectionId))
        } else Link(Icons.Outlined.Language, external) { web() }
        "page", "other" -> if (abs != null && isMaterialPage(abs)) Link(if (row.kind == "page") Icons.Outlined.Article else Icons.Outlined.Link, ">",
            if (row.kind == "page") "Page" else "Link") {
            opened(); nav.push(Route.MaterialPage(row))
        } else Link(Icons.Outlined.Language, external) { web() }
        "document" -> {
            val (icon, where) = row.url?.let { FileKinds.label(row.title, it) } ?: (Icons.Outlined.Description to "")
            Link(icon, if (where == "Opens here ›") FileKinds.word(row.title, row.url ?: "") else where) {
                opened()
                when (val t = row.url?.let { FileKinds.target(row.title, it) }) {
                    is OpenTarget.FileOpen -> nav.file = t.f
                    is OpenTarget.Web -> nav.openLink(ctx, t.url)
                    null -> {}
                }
            }
        }
        // Quizzes, discussions, anything else: the real page, only on tap.
        else -> Link(when (row.kind) { "quiz" -> Icons.Outlined.Checklist; "discussion" -> Icons.AutoMirrored.Outlined.Chat; else -> Icons.Outlined.Language },
            external) { web() }
    }
}

fun isMaterialPage(u: String): Boolean {
    val uri = Uri.parse(u)
    if (uri.host != Engine.HOST) return false
    val p = uri.path ?: return false
    return Regex("""^/page/\d+$""").matches(p) || Regex("""^/course/\d+/materials/(gp|link/view)/\d+$""").matches(p)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkRow(
    icon: ImageVector, row: MaterialRow, subtitle: String?, trailing: String, word: String?, subtitleLate: Boolean, graded: Boolean,
    item: ItemState?, isNew: Boolean, hasNewInside: Boolean, showIn: Pair<String, () -> Unit>?, onClick: () -> Unit,
) {
    val isWork = row.kind == "assignment" || row.kind == "quiz"
    var menu by remember { mutableStateOf(false) }
    val spoken = spoken(row, item, subtitle, word, trailing, isNew, hasNewInside)
    Box {
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp)
            .then(if (showIn != null) Modifier.combinedClickable(onClick = onClick, onLongClick = { menu = true }, onLongClickLabel = "Show in ${showIn.first}")
                  else Modifier.clickable(onClick = onClick))
            .clearAndSetSemantics { contentDescription = spoken; role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                when {
                    !isWork -> Icon(icon, null, tint = C.secondary, modifier = Modifier.size(22.dp))
                    item != null -> CheckMark(item)
                    // Graded but nothing saved: finished, the turned-in seal.
                    graded -> Icon(Icons.Filled.Verified, null, tint = C.good, modifier = Modifier.size(24.dp * 1.12f))
                    // Nothing known about it yet: the same empty ring To Do shows for work not started.
                    else -> { val ring = C.secondary; Canvas(Modifier.size(24.dp)) {
                        drawCircle(ring, style = Stroke(width = 1.6.dp.toPx()), radius = size.minDimension / 2 - 1.dp.toPx())
                    } }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.title, style = Theme.subhead.copy(fontWeight = FontWeight.Medium), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (isWork && item != null) TypeTag(item.type)
                    if (isNew) NewTag()
                    if (hasNewInside) Box(Modifier.size(8.dp).clip(CircleShape).background(C.accent))
                }
                if (!subtitle.isNullOrEmpty()) Text(subtitle, style = Theme.footnote.copy(fontWeight = if (subtitleLate) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (subtitleLate) C.late else C.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            if (word != null) { Text(word, style = Theme.footnote, color = C.tertiary, maxLines = 1); Spacer(Modifier.width(4.dp)) }
            if (trailing == ">") Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = C.tertiary)
            else Text(trailing, style = Theme.footnote, color = C.tertiary, maxLines = 1)
        }
        if (showIn != null) DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Show in ${showIn.first}") }, leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                onClick = { menu = false; showIn.second() })
        }
    }
}

/** "Assignment, Lab 2, submitted, 18 out of 20": the kind, the title, then the state. */
private fun spoken(row: MaterialRow, item: ItemState?, subtitle: String?, word: String?, trailing: String, isNew: Boolean, hasNewInside: Boolean): String {
    val parts = mutableListOf<String>()
    when (row.kind) {
        "folder" -> parts += listOf("Folder", row.title)
        "assignment", "quiz" -> {
            parts += listOf(if (row.kind == "quiz") "Quiz" else "Assignment", row.title)
            if (item != null) parts += when {
                item.submitted -> "submitted"
                item.turnedIn -> "turned in"
                item.isExam && item.studied -> "studied"
                !item.isExam && item.done -> "done, not turned in"
                else -> if (item.isExam) "not studied" else "not done"
            }
            if (!subtitle.isNullOrEmpty() && subtitle != "Submitted" && subtitle != "Turned in") parts += subtitle.replace(" / ", " out of ")
        }
        "discussion" -> parts += listOf("Discussion", row.title)
        else -> {
            parts += row.title
            val kind = word ?: if (trailing == ">") "" else trailing.replace(" ↗", "")
            if (kind.isNotEmpty()) parts.add(0, kind)
        }
    }
    if (isNew) parts.add(0, "New")
    if (hasNewInside) parts += "has new items"
    return parts.joinToString(", ")
}

/** Recently opened materials (local only). */
object RecentMaterials {
    @Serializable
    data class Entry(val sectionId: String, val row: MaterialRow)

    private val s = ListSerializer(Entry.serializer())
    var version by mutableIntStateOf(0)

    fun list(): List<Entry> { version; return Cache.load("recentMaterials", s)?.first ?: emptyList() }

    fun add(sectionId: String, row: MaterialRow) {
        val l = list().filter { it.row.key != row.key }.toMutableList()
        l.add(0, Entry(sectionId, row))
        Cache.save("recentMaterials", s, l.take(20))
        version++
    }
}
