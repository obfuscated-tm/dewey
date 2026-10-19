package com.owenleung.dewey.ui.classes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.EngineError
import com.owenleung.dewey.engine.Material
import com.owenleung.dewey.engine.MaterialRow
import com.owenleung.dewey.store.Cache
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.ui.AsOfBanner
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.DetailScaffold
import com.owenleung.dewey.ui.Divider
import com.owenleung.dewey.ui.EmptyState
import com.owenleung.dewey.ui.ErrorState
import com.owenleung.dewey.ui.MarkdownText
import com.owenleung.dewey.ui.PillButton
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.SheetTarget
import com.owenleung.dewey.ui.SkeletonRows
import com.owenleung.dewey.ui.WideButton
import com.owenleung.dewey.ui.grades.scoreText
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.web.WebConfig
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.util.Date

/**
 * 3b · One Materials folder. Every row says where it opens. "Only open work"
 * keeps folders and assignments still on the To Do list. The class switch
 * (Materials / Grades / Updates) sits at the top of the class's root.
 */
@Composable
fun MaterialsScreen(sectionId: String, folderId: String?, title: String, trail: List<String>) {
    val model = model
    val nav = nav
    val scope = rememberCoroutineScope()
    val cacheKey = "materials-$sectionId-${folderId ?: "root"}"
    val s = ListSerializer(MaterialRow.serializer())
    var rows by remember { mutableStateOf<List<MaterialRow>?>(Cache.load(cacheKey, s)?.first) }
    var asOf by remember { mutableStateOf<Date?>(Cache.load(cacheKey, s)?.second) }
    var error by remember { mutableStateOf<EngineError?>(null) }
    var onlyOpen by remember { mutableStateOf(false) }
    val pageUrl = "${Engine.ORIGIN}/course/$sectionId/materials${folderId?.let { "?f=$it" } ?: ""}"

    suspend fun load() {
        val args = mutableMapOf<String, Any?>("sectionId" to sectionId)
        if (folderId != null) args["folderId"] = folderId
        try {
            val r: List<MaterialRow> = model.engine.call("materials", args)
            rows = r; asOf = Date(); error = null
            Cache.save(cacheKey, s, r)
        } catch (e: EngineError) { error = e }
    }
    LaunchedEffect(cacheKey) { load() }

    /** The To Do row for an assignment, if there is one. */
    fun todo(r: MaterialRow) = if (r.kind == "assignment" && r.id != null) model.item(r.id) else null
    fun isOpen(r: MaterialRow): Boolean {
        if (r.kind == "folder") return true
        val it = todo(r) ?: return false
        return !model.isFinished(it) && !it.graded
    }
    fun subtitle(r: MaterialRow): String? {
        if (r.id != null && r.kind == "assignment") {
            model.grades[sectionId]?.items?.firstOrNull { it.id == r.id && it.earned != null }?.let { return scoreText(it) }
        }
        val it = todo(r) ?: return null
        if (it.submitted) return "Submitted"
        if (it.turnedIn) return "Turned in"
        return it.due?.let { "Due ${Fmt.due(it)}" }
    }
    val dot = com.owenleung.dewey.ui.classColor(sectionId)

    DetailScaffold("", actions = { IconButton(onClick = { nav.openSheet(SheetTarget(pageUrl)) }) { Icon(Icons.Outlined.Language, "Open in Schoology") } }) { pad ->
        ScrollPage(pad, refresh = { load() }, spacing = 12.dp) {
            // Where you are: the class dot and the path above this folder, then the folder's name in full.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (trail.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(6.dp))
                    Text(trail.joinToString(" › "), style = Theme.footnote, color = C.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trail.isEmpty()) { Box(Modifier.size(10.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(8.dp)) }
                    Text(title, style = Theme.title, color = C.text)
                }
            }
            if (folderId == null) model.course(sectionId)?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Materials", filled = true) {}
                    PillButton("Grades") { nav.push(Route.CourseGrades(sectionId)) }
                    PillButton("Updates", external = true) { nav.openSheet(SheetTarget("${Engine.ORIGIN}/course/$sectionId/updates")) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { AsOfBanner(asOf, error) }
                FilterChip(onlyOpen, { onlyOpen = !onlyOpen }, label = { Text("Only open work", style = Theme.footnote.copy(fontWeight = FontWeight.Medium)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.accentSoft, selectedLabelColor = C.accent))
            }
            val r = rows
            when {
                r != null -> {
                    val shown = if (onlyOpen) r.filter(::isOpen) else r
                    if (shown.isEmpty()) EmptyState(if (onlyOpen) "No open work here" else "Empty folder", icon = Icons.Outlined.Folder)
                    else Card { shown.forEachIndexed { i, row -> if (i > 0) Divider(); MaterialLink(sectionId, row, subtitle(row), trail + title) } }
                }
                error != null -> ErrorState(error!!, pageUrl, retry = { scope.launch { load() } }, open = { nav.openSheet(SheetTarget(it)) })
                else -> SkeletonRows()
            }
        }
    }
}

/** A Schoology page / link material, read through the engine: its text, files and links. */
@Composable
fun MaterialPageScreen(row: MaterialRow) {
    val model = model
    val nav = nav
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val url = row.url?.let(WebConfig::abs)
    var m by remember { mutableStateOf<Material?>(null) }
    var error by remember { mutableStateOf<EngineError?>(null) }
    suspend fun load() {
        if (url == null) { error = EngineError.BadArgs; return }
        try {
            m = model.engine.call("material", mapOf("path" to android.net.Uri.parse(url).path))
            error = null
        } catch (e: EngineError) { error = e }
    }
    LaunchedEffect(url) { load() }
    DetailScaffold(row.title, actions = {
        if (url != null) IconButton(onClick = { nav.openSheet(SheetTarget(url)) }) { Icon(Icons.Outlined.Language, "Open in Schoology") }
    }) { pad ->
        ScrollPage(pad) {
            val mat = m
            when {
                mat != null -> {
                    Text(mat.title ?: row.title, style = Theme.title, color = C.text)
                    mat.target?.let { t ->
                        WideButton("Open ${android.net.Uri.parse(t).host ?: "link"}", icon = Icons.Outlined.OpenInBrowser, external = true) { nav.openLink(ctx, t) }
                    }
                    if (!mat.bodyMd.isNullOrEmpty()) Card(padding = 16.dp) { MarkdownText(mat.bodyMd) { u -> nav.openLink(ctx, u) } }
                    val all = mat.files + mat.links
                    if (all.isNotEmpty()) Card {
                        all.forEachIndexed { i, f ->
                            if (i > 0) Divider()
                            AttachmentRow(f.title, f.url, Modifier.padding(horizontal = 14.dp)) { t ->
                                when (t) { is OpenTarget.FileOpen -> nav.file = t.f; is OpenTarget.Web -> nav.openLink(ctx, t.url) }
                            }
                        }
                    }
                }
                error != null -> ErrorState(error!!, url, retry = { scope.launch { load() } }, open = { nav.openSheet(SheetTarget(it)) })
                else -> SkeletonRows(3)
            }
        }
    }
}
