package com.owenleung.dewey.ui.classes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.owenleung.dewey.store.ItemState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OpenInBrowser
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
import com.owenleung.dewey.store.TodoLogic
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
 * 3b · One Materials folder. Every row says where it opens. The class's root
 * starts with its open work (what the book's LATE / TODAY / TMRW ribbon is
 * about), then the class switch (Materials / Grades / Updates), then a search of
 * the class's whole tree above its list. Rows the teacher added since the student
 * last looked wear NEW; a folder with something new inside has a dot (MaterialIndex).
 * `trailIds` / `onStack`: each trail level's folder id and whether it is on the stack
 * (Route.Materials); empty: all on it.
 */
@Composable
fun MaterialsScreen(sectionId: String, folderId: String?, title: String, trail: List<String>,
                    trailIds: List<String?> = emptyList(), onStack: List<Boolean> = emptyList()) {
    val model = model
    val nav = nav
    val scope = rememberCoroutineScope()
    val cacheKey = "materials-$sectionId-${folderId ?: "root"}"
    val s = ListSerializer(MaterialRow.serializer())
    var rows by remember { mutableStateOf<List<MaterialRow>?>(Cache.load(cacheKey, s)?.first) }
    var asOf by remember { mutableStateOf<Date?>(Cache.load(cacheKey, s)?.second) }
    var error by remember { mutableStateOf<EngineError?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
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
    // The class's tree is walked again once its copy is a day old: search and the NEW marks.
    LaunchedEffect(sectionId) { if (folderId == null) MaterialIndex.refresh(sectionId, model) }
    LaunchedEffect(query) { if (query.isNotEmpty()) MaterialIndex.refresh(sectionId, model) }
    // Left this folder: what was new in it has been seen (it stays marked while you're here).
    val shownRows by rememberUpdatedState(rows)
    DisposableEffect(sectionId, folderId) { onDispose { shownRows?.let { r -> MaterialIndex.markSeen(sectionId, r.map { it.key }) } } }

    /**
     * The To Do row for an assignment or quiz, if there is one. An assignment's row id is
     * its Schoology id; a quiz's is the page's own row key, so a quiz is looked up by the id
     * at the end of its /assessments/ link (the id To Do saves it under, per sync.js; not
     * yet checked against a live quiz).
     */
    fun todo(r: MaterialRow): ItemState? = when (r.kind) {
        "assignment" -> r.id?.let { model.item(it) }
        "quiz" -> r.url?.let { Regex("""/assessments/(\d+)""").find(it) }?.let { model.item(it.groupValues[1]) }
        else -> null
    }
    /** The gradebook score text for an assignment, if graded. */
    fun score(r: MaterialRow): String? {
        if (r.kind != "assignment" || r.id == null) return null
        return model.grades[sectionId]?.items?.firstOrNull { it.id == r.id && it.earned != null }?.let { scoreText(it) }
    }
    fun status(r: MaterialRow): MaterialStatus.Line? {
        if (r.kind != "assignment" && r.kind != "quiz") return null
        val it = todo(r)
        val now = Date()
        // The saved To Do item's date, else the one Schoology prints on the folder row.
        return MaterialStatus.line(score(r), it?.let { model.isOverdue(it, now) } ?: false, it?.due ?: Fmt.date(r.due),
            it?.submitted ?: false, it?.turnedIn ?: false, now)
    }
    val levelIds = if (trailIds.size == trail.size) trailIds else List(trail.size) { null }
    val levelsOnStack = if (onStack.size == trail.size) onStack else List(trail.size) { true }
    /** A path name: back to that level, or (opened from search, the level never pushed) to it. */
    fun goTo(level: Int) {
        val j = FolderPath.jump(level, levelsOnStack)
        val route = if (j.push) Route.Materials(sectionId, levelIds[level], trail[level], trail.take(level), levelIds.take(level), levelsOnStack.take(level)) else null
        nav.popFolders(j.pop, route)
    }
    val dot = com.owenleung.dewey.ui.classColor(sectionId)

    DetailScaffold("", actions = { IconButton(onClick = { nav.openSheet(SheetTarget(pageUrl)) }) { Icon(Icons.Outlined.Language, "Open in Schoology") } }) { pad ->
        ScrollPage(pad, refresh = { load() }, spacing = 12.dp) {
            // Where you are: the class dot and the path above this folder, then the folder's name in full.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (trail.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(6.dp))
                    // Each name goes back to that level. A path too long to fit scrolls, showing its end.
                    val scroll = rememberScrollState()
                    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
                    Row(Modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
                        trail.forEachIndexed { i, name ->
                            if (i > 0) Text(" › ", style = Theme.footnote, color = C.secondary, modifier = Modifier.clearAndSetSemantics {})
                            // Looks like the plain text it replaced; Compose stretches a small target's
                            // touch area to the 48 dp minimum, so no padding changes the look.
                            Text(name, style = Theme.footnote, color = C.secondary, maxLines = 1,
                                modifier = Modifier.clickable(onClickLabel = "Back to $name", role = Role.Button) { goTo(i) }
                                    .semantics { contentDescription = "Back to $name" })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trail.isEmpty()) { Box(Modifier.size(10.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(8.dp)) }
                    Text(title, style = Theme.title, color = C.text)
                }
            }
            if (folderId == null) ClassOpenWork(sectionId)
            if (folderId == null) model.course(sectionId)?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Materials", filled = true) {}
                    PillButton("Grades") { nav.push(Route.CourseGrades(sectionId)) }
                    PillButton("Updates", external = true) { nav.openSheet(SheetTarget("${Engine.ORIGIN}/course/$sectionId/updates")) }
                }
            }
            if (folderId == null) SearchField(query, "Search this class's materials") { query = it }
            if (query.isNotEmpty()) {
                // The class's whole tree by name (MaterialIndex), as the same rows with the folder
                // path under each. A folder opens on top of this page; its path still goes to every level.
                val hits = MaterialIndex.search(query, sectionId)
                val walking = MaterialIndex.isWalking(sectionId)
                if (walking) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = C.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (MaterialIndex.walking == sectionId && MaterialIndex.foldersRead > 0)
                        "Still reading this class's folders… ${MaterialIndex.foldersRead} read" else "Still reading this class's folders…",
                        style = Theme.footnote, color = C.secondary)
                }
                if (hits.isEmpty() && !walking) EmptyState("Nothing called that", "Searches the names of everything in this class's folders.", icon = Icons.Outlined.Search)
                if (hits.isNotEmpty()) Card {
                    hits.take(60).forEachIndexed { i, h ->
                        if (i > 0) Divider()
                        val t = h.trail(title)
                        val r = h.row
                        val st = if (h.path.isEmpty()) status(r) else null
                        val f = h.containingFolder(title)
                        MaterialLink(sectionId, r, if (h.path.isEmpty()) st?.text else h.path, t.names, subtitleLate = st?.late ?: false,
                            graded = score(r) != null, item = todo(r), trailIds = t.ids, onStack = t.onStack,
                            isNew = MaterialIndex.isNew(sectionId, r), hasNewInside = r.kind == "folder" && MaterialIndex.folderHasNew(sectionId, r.id),
                            showIn = f?.let { it.title to { nav.popFolders(0, it) } })
                    }
                }
            } else {
                AsOfBanner(asOf, error)
                val r = rows
                when {
                    r != null -> {
                        if (r.isEmpty()) EmptyState("Empty folder", icon = Icons.Outlined.Folder)
                        else Card {
                            r.forEachIndexed { i, row ->
                                if (i > 0) Divider()
                                val st = status(row)
                                MaterialLink(sectionId, row, st?.text, trail + title, subtitleLate = st?.late ?: false, graded = score(row) != null,
                                    item = todo(row), trailIds = levelIds + folderId, onStack = levelsOnStack + true,
                                    isNew = MaterialIndex.isNew(sectionId, row),
                                    hasNewInside = row.kind == "folder" && MaterialIndex.folderHasNew(sectionId, row.id))
                            }
                        }
                    }
                    error != null -> ErrorState(error!!, pageUrl, retry = { scope.launch { load() } }, open = { nav.openSheet(SheetTarget(it)) })
                    else -> SkeletonRows()
                }
            }
        }
    }
}

/** A class's open work at the top of its page: late first, then by due date. The same rules as To Do and the shelf's ribbon, so the ribbon is explained here. */
@Composable
private fun ClassOpenWork(sectionId: String) {
    val model = model
    val nav = nav
    model.revision.intValue
    val now = Date()
    val items = model.openWork(sectionId, now)
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Open work", style = Theme.headline, color = C.text)
        Card {
            items.forEachIndexed { i, it ->
                if (i > 0) Divider()
                val late = model.isOverdue(it, now)
                Row(Modifier.fillMaxWidth().clickable { nav.push(Route.Assignment(it.schoologyId, sectionId)) }.heightIn(min = 44.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(it.title, style = Theme.body, color = C.text)
                        Text(openWorkWhen(it.due, late, now), style = Theme.footnote.copy(fontWeight = if (late) FontWeight.SemiBold else FontWeight.Normal),
                            color = if (late) C.late else C.secondary)
                    }
                    if (if (it.isExam) it.studied else it.done) {
                        Text(if (it.isExam) "Studied" else "Done", style = Theme.footnote, color = C.accent)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("›", color = C.tertiary)
                }
            }
        }
    }
}

/** "Late · Fri Oct 16, 6:59 pm · 2 days", or the due words ("Today, 11:59 pm"). */
internal fun openWorkWhen(due: Date?, late: Boolean, now: Date): String {
    val words = Fmt.due(due, now)
    val n = if (late) TodoLogic.daysLate(due, now) else null
    n ?: return words
    return "Late · $words" + if (n > 0) " · $n day${if (n == 1) "" else "s"}" else ""
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
