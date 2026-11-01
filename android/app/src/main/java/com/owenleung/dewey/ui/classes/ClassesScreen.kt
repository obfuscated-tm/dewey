package com.owenleung.dewey.ui.classes

import com.owenleung.dewey.ui.tour.coachTarget
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
        SearchField(query) { q ->
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
                    MaterialLink(h.sectionId, h.row, listOf(model.course(h.sectionId)?.shortTitle ?: "", h.path).filter { it.isNotEmpty() }.joinToString(" › "))
                }
            }
        }
    }
}

@Composable
private fun SearchField(q: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.Radius.card)).background(C.tagSoft).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Search, null, tint = C.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (q.isEmpty()) Text("Search every class's materials", style = Theme.body, color = C.tertiary)
            BasicTextField(q, onChange, singleLine = true, textStyle = Theme.body.copy(color = C.text), cursorBrush = SolidColor(C.accent),
                modifier = Modifier.fillMaxWidth())
        }
        if (q.isNotEmpty()) Text("Cancel", style = Theme.subhead, color = C.accent, modifier = Modifier.clickable { onChange("") }.padding(start = 8.dp))
    }
}

/** A row that opens a material where it belongs (3b's table). */
@Composable
fun MaterialLink(sectionId: String, row: MaterialRow, subtitle: String? = null, trail: List<String> = emptyList()) {
    val nav = nav
    val ctx = LocalContext.current
    val abs = row.url?.let(WebConfig::abs)
    fun web() { if (abs != null) { RecentMaterials.add(sectionId, row); nav.openLink(ctx, abs) } }
    val external = if (Uri.parse(abs ?: "").host == Engine.HOST) "Schoology ↗" else "${Uri.parse(abs ?: "").host ?: ""} ↗"
    when (row.kind) {
        "folder" -> LinkRow(Icons.Outlined.Folder, row, subtitle, ">", C.secondary) { nav.push(Route.Materials(sectionId, row.id, row.title, trail)) }
        "assignment" -> if (row.id != null) LinkRow(Icons.Outlined.Description, row, subtitle, ">") {
            RecentMaterials.add(sectionId, row); nav.push(Route.Assignment(row.id, sectionId))
        } else LinkRow(Icons.Outlined.Language, row, subtitle, external) { web() }
        "page", "other" -> if (abs != null && isMaterialPage(abs)) LinkRow(if (row.kind == "page") Icons.Outlined.Article else Icons.Outlined.Link, row, subtitle, ">") {
            RecentMaterials.add(sectionId, row); nav.push(Route.MaterialPage(row))
        } else LinkRow(Icons.Outlined.Language, row, subtitle, external) { web() }
        "document" -> {
            val (icon, where) = row.url?.let { FileKinds.label(row.title, it) } ?: (Icons.Outlined.Description to "")
            LinkRow(icon, row, subtitle, where) {
                RecentMaterials.add(sectionId, row)
                when (val t = row.url?.let { FileKinds.target(row.title, it) }) {
                    is OpenTarget.FileOpen -> nav.file = t.f
                    is OpenTarget.Web -> nav.openLink(ctx, t.url)
                    null -> {}
                }
            }
        }
        // Quizzes, discussions, anything else: the real page, only on tap.
        else -> LinkRow(when (row.kind) { "quiz" -> Icons.Outlined.Checklist; "discussion" -> Icons.AutoMirrored.Outlined.Chat; else -> Icons.Outlined.Language },
            row, subtitle, external) { web() }
    }
}

fun isMaterialPage(u: String): Boolean {
    val uri = Uri.parse(u)
    if (uri.host != Engine.HOST) return false
    val p = uri.path ?: return false
    return Regex("""^/page/\d+$""").matches(p) || Regex("""^/course/\d+/materials/(gp|link/view)/\d+$""").matches(p)
}

@Composable
private fun LinkRow(icon: ImageVector, row: MaterialRow, subtitle: String?, trailing: String, tint: Color = C.accent, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, if (row.kind == "folder") "Folder" else if (row.kind == "quiz") "Quiz" else null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.title, style = Theme.subhead.copy(fontWeight = FontWeight.Medium), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = Theme.footnote, color = C.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        if (trailing == ">") Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = C.tertiary)
        else Text(trailing, style = Theme.footnote, color = C.tertiary, maxLines = 1)
    }
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

/**
 * Every class's Materials tree by name, like the MCP's find_material: walked
 * on the first search (folders through the engine, which paces requests),
 * cached for a day.
 */
object MaterialIndex {
    @Serializable
    data class Hit(val sectionId: String, val path: String, val row: MaterialRow)

    private val s = ListSerializer(Hit.serializer())
    var hits by mutableStateOf<List<Hit>>(emptyList()); private set
    var building by mutableStateOf(false); private set
    var foldersRead by mutableIntStateOf(0); private set
    private var builtAt: Date? = null
    private var loaded = false

    private fun loadCached() {
        if (loaded) return
        loaded = true
        Cache.load("materialIndex", s)?.let { hits = it.first; builtAt = it.second }
    }

    fun search(q: String): List<Hit> {
        loadCached()
        val words = q.lowercase().split(" ").filter { it.isNotEmpty() }
        return hits.filter { h -> h.row.kind != "folder" && words.all { h.row.title.lowercase().contains(it) } }
    }

    fun build(model: AppModel, force: Boolean = false) {
        loadCached()
        if (building) return
        val at = builtAt
        if (!force && at != null && Date().time - at.time < 24 * 3600_000 && hits.isNotEmpty()) return
        building = true
        foldersRead = 0
        model.scope.launch {
            val out = mutableListOf<Hit>()
            for (c in model.classes) {
                val queue = ArrayDeque(listOf(Triple<String?, String, Int>(null, "", 0)))
                while (queue.isNotEmpty()) {
                    val (fid, path, depth) = queue.removeFirst()
                    val args = mutableMapOf<String, Any?>("sectionId" to c.sectionId)
                    if (fid != null) args["folderId"] = fid
                    val rows = runCatching { model.engine.call<List<MaterialRow>>("materials", args) }.getOrNull() ?: continue
                    foldersRead++
                    for (r in rows) {
                        out += Hit(c.sectionId, path, r)
                        if (r.kind == "folder" && r.id != null && depth < 4) queue += Triple(r.id, if (path.isEmpty()) r.title else "$path › ${r.title}", depth + 1)
                    }
                    hits = out.toList()
                }
            }
            hits = out
            builtAt = Date()
            Cache.save("materialIndex", s, out)
            building = false
        }
    }
}
