package com.owenleung.dewey.ui.todo

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.neoplan.NeoPlanClient
import com.owenleung.dewey.store.DayFilter
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.ItemState
import com.owenleung.dewey.store.ItemType
import com.owenleung.dewey.store.Reminders
import com.owenleung.dewey.store.TodoLogic
import com.owenleung.dewey.ui.AsOfBanner
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.Divider
import com.owenleung.dewey.ui.EmptyState
import com.owenleung.dewey.ui.ErrorState
import com.owenleung.dewey.ui.PillButton
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.ScreenHeader
import com.owenleung.dewey.ui.ScreenOptions
import com.owenleung.dewey.ui.SettingsPage
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.SheetTarget
import com.owenleung.dewey.ui.SkeletonRows
import com.owenleung.dewey.ui.TypeBar
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import java.util.Date
import kotlinx.coroutines.launch

/** 1a · To Do: everything due, grouped by class in Course Dashboard order. */
@Composable
fun ToDoScreen() {
    val model = model
    val nav = nav
    var filter by remember { mutableStateOf<DayFilter?>(null) }
    var showOlder by rememberSaveable { mutableStateOf(false) }
    val items = model.todoItems()
    val now = Date()

    ScrollPage(refresh = { model.refreshTodo() }, spacing = 12.dp) {
        // Header: date, title, gear, week strip.
        val open = items.filter { !model.isFinished(it) }
        ScreenHeader("To Do", kicker = Fmt.header(now)) {
            ScreenOptions(SettingsPage.TODO)
        }
        // Late, today and the next six days, a dot per item still to do; tap one to show only it.
        WeekStrip(open, now, filter) { filter = it }
        filter?.let { f ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(filterTitle(f, now), style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.secondary, modifier = Modifier.weight(1f))
                Text("Show all", style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.accent,
                    modifier = Modifier.clickable { filter = null }.padding(4.dp))
            }
        }
        // Offline note / refresh spinner: only takes room when there's something to show.
        if ((model.todoAsOf != null && model.todoError != null) || model.refreshingTodo) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsOfBanner(model.todoAsOf, model.todoError)
                if (model.refreshingTodo) { Spacer(Modifier.width(8.dp)); CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = C.secondary) }
            }
        }

        // The groups.
        if (model.todoAsOf == null && items.isEmpty()) {
            val e = model.todoError
            if (e != null) ErrorState(e, retry = { model.scope.launch { model.refreshTodo() } }) else SkeletonRows()
            return@ScrollPage
        }
        val filtered = items.filter { filter == null || (!model.isFinished(it) && filter!!.matches(it, now)) }
        val older = filtered.filter { TodoLogic.isOlder(it.due, now) && !model.isFinished(it) }
        val current = filtered.filter { it !in older }
        val groups = grouped(model, current)
        if (groups.isEmpty() && older.isEmpty()) {
            EmptyState(if (filter == null) "Nothing due" else "Nothing here",
                if (filter == null) "Schoology lists nothing for you right now." else "Tap the day again to see everything.",
                art = filter == null)
        }
        for (g in groups) {
            Column {
                GroupHeader(g.title, g.items, now)
                Card { g.items.forEachIndexed { i, it -> if (i > 0) Divider(); TodoRowItem(it, now) } }
            }
        }
        if (older.isNotEmpty()) {
            Column(Modifier.animateContentSize()) {
                Row(Modifier.fillMaxWidth().clickable { showOlder = !showOlder }.padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Older · ${older.size}", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                    Spacer(Modifier.width(6.dp))
                    Text("over ${TodoLogic.OLDER_DAYS} days late", style = Theme.footnote, color = C.tertiary, modifier = Modifier.weight(1f))
                    Icon(if (showOlder) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = C.secondary)
                }
                if (showOlder) Card { sortItems(model, older).forEachIndexed { i, it -> if (i > 0) Divider(); TodoRowItem(it, now) } }
            }
        }
    }
}

private fun filterTitle(f: DayFilter, now: Date) = when (f) {
    DayFilter.Late -> "Late"
    is DayFilter.Day -> when (f.k) { 0 -> "Today"; 1 -> "Tomorrow"; else -> Fmt.header(Fmt.addDays(Fmt.startOfDay(now), f.k)) }
}

private data class Group(val key: String, val title: String, val items: List<ItemState>)

private fun grouped(m: AppModel, items: List<ItemState>): List<Group> {
    val bySection = items.groupBy { it.sectionId ?: "" }.toMutableMap()
    val out = mutableListOf<Group>()
    for (c in m.courses) {
        val list = bySection.remove(c.sectionId)
        if (!list.isNullOrEmpty()) out += Group(c.sectionId, c.shortTitle, sortItems(m, list))
    }
    val rest = bySection.values.flatten()
    if (rest.isNotEmpty()) out += Group("other", "Other", sortItems(m, rest))
    return out
}

/** Unfinished first, then by due date (overdue oldest first, upcoming soonest first). */
private fun sortItems(m: AppModel, items: List<ItemState>) =
    items.sortedWith(compareBy<ItemState> { m.isFinished(it) }.thenBy { it.dueAt ?: Long.MAX_VALUE })

@Composable
private fun GroupHeader(title: String, items: List<ItemState>, now: Date) {
    val model = model
    val open = items.filter { !model.isFinished(it) }
    val dueToday = open.any { TodoLogic.bucket(it.due, now) == TodoLogic.Bucket.TODAY }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Theme.headline, color = C.text)
        if (dueToday) {
            Spacer(Modifier.width(8.dp))
            Text("Due today", style = Theme.caption.copy(fontWeight = FontWeight.SemiBold), color = C.accent,
                modifier = Modifier.clip(CircleShape).background(C.accentSoft).padding(horizontal = 7.dp, vertical = 2.dp))
        }
        Spacer(Modifier.weight(1f))
        Text("${open.size}", style = Theme.footnote, color = C.secondary)
    }
}

// ── One row ──────────────────────────────────────────────────────────────

@Composable
fun TodoRowItem(item: ItemState, now: Date) {
    val model = model
    val nav = nav
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val progress = model.progress(item)
    val submitUrl = "${Engine.ORIGIN}/assignment/${item.schoologyId}"

    Box {
        // The text sets the row's height; the left column (checkbox, then the type bar filling the rest)
        // matches it. Not IntrinsicSize.Min: that asks the weighted title for its height first and gets a huge one.
        Box(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = { nav.push(Route.Assignment(item.schoologyId)) }, onLongClick = { menu = true })
                .padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column(Modifier.matchParentSize(), horizontalAlignment = Alignment.Start) {
              Column(Modifier.width(44.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Checkbox) {
                        // One tap: to do ↔ done (a turned-in row goes back to done), like the extension.
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        model.setProgress(item, if (progress == AppModel.Progress.TODO) AppModel.Progress.DONE else if (progress == AppModel.Progress.DONE) AppModel.Progress.TODO else AppModel.Progress.DONE)
                    }.semantics { contentDescription = "Status: ${if (item.submitted) "Submitted on Schoology" else label(item, progress)}" },
                    contentAlignment = Alignment.Center,
                ) { CheckMark(item) }
                TypeBar(item.type, Modifier.weight(1f, fill = true).padding(top = 2.dp))
              }
            }
            Column(Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(start = 50.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = Theme.body, color = C.text)
                // The one-tap action (Submit / Turn in / Undo) sits at the right end of the due line.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DueText(item, now, Modifier.weight(1f))
                    RowAction(item, submitUrl)
                }
                statusNote(item)?.let { (text, color, strong) ->
                    Text(text, style = Theme.footnote.copy(fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium), color = color)
                }
            }
        }
        // Long press: all three states, Schoology, and remove (the context menu on iOS).
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            for (p in AppModel.Progress.entries) {
                DropdownMenuItem(
                    text = { Text(if (item.isExam && p == AppModel.Progress.DONE) "Studied" else p.label) },
                    leadingIcon = { if (progress == p) Icon(Icons.Outlined.Check, null) },
                    onClick = { menu = false; model.setProgress(item, p) },
                )
            }
            HorizontalDivider()
            if (item.labUrl != null) DropdownMenuItem(text = { Text("Lab site ↗") }, onClick = { menu = false; nav.openSheet(SheetTarget(item.labUrl!!)) })
            DropdownMenuItem(text = { Text("Open in Schoology ↗") }, onClick = {
                menu = false
                nav.openSheet(SheetTarget(Engine.ORIGIN + (item.url ?: "/assignment/${item.schoologyId}"), item.schoologyId))
            })
            if (!item.inSchoology) DropdownMenuItem(text = { Text("Remove from my list", color = C.late) }, onClick = { menu = false; model.dismiss(item) })
        }
    }
}

private fun label(item: ItemState, p: AppModel.Progress) =
    if (item.isExam && p != AppModel.Progress.TURNED_IN) (if (p == AppModel.Progress.DONE) "Studied" else "Not studied") else p.label

@Composable
private fun RowAction(item: ItemState, submitUrl: String) {
    val model = model
    val nav = nav
    when {
        item.submitted -> {}
        item.turnedIn -> PillButton("Undo", color = C.secondary) { model.setProgress(item, AppModel.Progress.DONE) }
        item.hasDropbox -> PillButton("Submit", external = true) { nav.openSheet(SheetTarget(submitUrl, item.schoologyId)) }
        !item.isExam -> PillButton("Turn in", color = C.good) { model.setProgress(item, AppModel.Progress.TURNED_IN) }
    }
}

/** "Mon Oct 5, 11:59 pm · 7 days late"; a test adds its countdown and studied mark. */
@Composable
private fun DueText(item: ItemState, now: Date, modifier: Modifier) {
    val late = if (item.submitted || item.turnedIn) null else TodoLogic.daysLate(item.due, now)
    val dueColor = if (late != null) C.late else C.secondary
    val accent = C.accent
    val secondary = C.secondary
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = dueColor)) {
            append(Fmt.due(item.due, now))
            if (late != null && late > 0) append(" · $late day${if (late == 1) "" else "s"} late") else if (late == 0) append(" · late")
        }
        if (item.isExam && !item.submitted && !item.turnedIn) {
            // "Tomorrow, 11:59 pm" already says it: no "· tomorrow" after it.
            val dueWords = Fmt.due(item.due, now).lowercase()
            val countdown = Reminders.testCountdown(item.due, now)?.removePrefix("Test ")?.let { if (dueWords.startsWith(it.lowercase())) null else " · $it" } ?: ""
            withStyle(SpanStyle(color = if (item.studied) accent else secondary)) { append("$countdown · ${if (item.studied) "studied" else "not studied"}") }
        }
    }
    Text(text, style = Theme.footnote.copy(fontWeight = if (late != null) FontWeight.SemiBold else FontWeight.Normal), modifier = modifier)
}

/** The status, when it says more than "to do": its own line, never cut short. */
@Composable
private fun statusNote(item: ItemState): Triple<String, Color, Boolean>? {
    if (item.submitted) return Triple("Submitted on Schoology${if (item.statusLate == true) " · late" else ""}", C.good, true)
    if (item.turnedIn) return Triple("Turned in · not on Schoology", C.good, true)
    if (item.isExam) return null
    if (item.external) return Triple(if (item.done) "Done · check it in Schoology" else "Google Assignment · turn in on Schoology",
        if (item.done) C.accent else C.secondary, false)
    if (item.done) return Triple(if (item.hasDropbox) "Done · not submitted yet" else "Done", C.accent, false)
    if (!item.inSchoology) return Triple("Not on Schoology anymore · tick when done", C.secondary, false)
    return null
}

/**
 * Accent ring with an accent check = done or studied (student) · solid green
 * seal = turned in (submitted on Schoology, or marked). Dashed = not in neo-plan yet.
 */
@Composable
fun CheckMark(item: ItemState, size: androidx.compose.ui.unit.Dp = 24.dp) {
    val good = C.good
    val accent = C.accent
    val accentSoft = C.accentSoft
    val dashed = item.neoPlanId == null && NeoPlanClient.connected
    val ring = if (dashed) C.tertiary else C.secondary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            // Solid green seal with a check = turned in (submitted on Schoology, or marked): stamped, received.
            // The seal's outline keeps it apart from done in every theme, even where the accent and green are close.
            item.submitted || item.turnedIn ->
                Icon(Icons.Filled.Verified, null, tint = good, modifier = Modifier.size(size * 1.12f))
            // Accent ring with an accent check = done or studied (student).
            if (item.isExam) item.studied else item.done -> {
                Canvas(Modifier.size(size)) {
                    drawCircle(accentSoft)
                    drawCircle(accent, style = Stroke(width = 2.dp.toPx()), radius = this.size.minDimension / 2 - 1.dp.toPx())
                }
                Icon(Icons.Outlined.Check, null, tint = accent, modifier = Modifier.size(size * 0.62f))
            }
            else -> Canvas(Modifier.size(size)) {
                drawCircle(ring, style = Stroke(width = 1.6.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 5f)) else null),
                    radius = this.size.minDimension / 2 - 1.dp.toPx())
            }
        }
    }
}

/** To do / Done (Studied) / Turned in as three big buttons (the Assignment screen). */
@Composable
fun ProgressChips(current: AppModel.Progress, isExam: Boolean, onPick: (AppModel.Progress) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in AppModel.Progress.entries) {
            val on = p == current
            val color = when (p) { AppModel.Progress.TURNED_IN -> C.good; AppModel.Progress.DONE -> C.accent; else -> C.secondary }
            val title = when (p) {
                AppModel.Progress.TODO -> if (isExam) "Not studied" else "To do"
                AppModel.Progress.DONE -> if (isExam) "Studied" else "Done"
                AppModel.Progress.TURNED_IN -> "Turned in"
            }
            Row(
                Modifier.weight(1f).height(40.dp).clip(CircleShape).background(if (on) color else color.copy(alpha = 0.1f))
                    .border(1.dp, color.copy(alpha = if (on) 0f else 0.35f), CircleShape)
                    .clickable(role = Role.RadioButton) { onPick(p) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) { Icon(if (p == AppModel.Progress.TURNED_IN) Icons.Filled.Verified else Icons.Outlined.Check, null, tint = C.onAccent, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)) }
                Text(title, style = Theme.footnote.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold), color = if (on) C.onAccent else color, maxLines = 1)
            }
        }
    }
}

// ── The week strip ───────────────────────────────────────────────────────

/** A Late tile, then today and the next six days, a dot per open item (black = test); tap a tile to show only it. */
@Composable
fun WeekStrip(items: List<ItemState>, now: Date, selected: DayFilter?, onSelect: (DayFilter?) -> Unit) {
    val tiles = listOf<DayFilter>(DayFilter.Late) + (0 until 7).map { DayFilter.Day(it) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (f in tiles) {
            val its = items.filter { f.matches(it, now) }.sortedBy { if (it.isExam) 0 else 1 }
            val on = selected == f
            val isLate = f == DayFilter.Late
            val isToday = f == DayFilter.Day(0)
            val date = (f as? DayFilter.Day)?.let { Fmt.addDays(Fmt.startOfDay(now), it.k) }
            val label = when { isLate -> "Late"; isToday -> "Today"; else -> Fmt.weekday(date!!) }
            val sub = date?.let(Fmt::dayNumber) ?: "${its.size}"
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(Theme.Radius.card)
            Column(
                Modifier.weight(1f).clip(shape)
                    .background(if (on) C.accent else if (isLate && its.isNotEmpty()) C.lateSoft else C.card)
                    .border(if (isToday) 1.5.dp else 1.dp, if (on) Color.Transparent else if (isToday) C.accent else C.border, shape)
                    .clickable { onSelect(if (on) null else f) }
                    .padding(vertical = 7.dp)
                    .semantics { contentDescription = "$label, ${if (its.isEmpty()) "nothing due" else "${its.size} ${if (isLate) "overdue" else "due"}"}" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(label, style = Theme.caption.copy(fontWeight = FontWeight.SemiBold), maxLines = 1,
                    color = if (on) C.onAccent else if (isLate) C.late else if (isToday) C.accent else C.secondary)
                Text(sub, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (on) C.onAccent else if (isLate) C.late else C.text)
                Dots(its, on)
            }
        }
    }
}

/** Up to six dots in two rows of three; more than six → five dots and "+N". */
@Composable
private fun Dots(its: List<ItemState>, on: Boolean) {
    val shown = if (its.size > 6) its.take(5) else its
    val extra = its.size - shown.size
    val onAccent = C.onAccent
    val text = C.text
    val tertiary = C.tertiary
    Column(Modifier.height(17.dp), verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        shown.chunked(3).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                for (it in row) {
                    val c = if (on) (if (it.isExam) onAccent else onAccent.copy(alpha = 0.55f)) else if (it.isExam) text else tertiary.copy(alpha = 0.45f)
                    Box(Modifier.size(6.dp).clip(CircleShape).background(c))
                }
                if (r == (shown.size - 1) / 3 && extra > 0) Text("+$extra", style = Theme.caption.copy(fontWeight = FontWeight.Bold, fontSize = Theme.caption.fontSize * 0.8f),
                    color = if (on) onAccent else C.secondary)
            }
        }
    }
}
