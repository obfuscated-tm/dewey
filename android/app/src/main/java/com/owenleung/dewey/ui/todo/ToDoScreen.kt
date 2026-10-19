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
import androidx.compose.runtime.key
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import com.owenleung.dewey.store.CallNumber
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.store.ToDoLayout
import com.owenleung.dewey.ui.DropShape
import com.owenleung.dewey.ui.RippleProgress
import com.owenleung.dewey.ui.classColor
import com.owenleung.dewey.ui.doneRipple
import com.owenleung.dewey.ui.settings.CallNumberMenu
import com.owenleung.dewey.ui.settings.StatusTip
import com.owenleung.dewey.ui.settings.TipKind
import com.owenleung.dewey.ui.tour.coachTarget
import java.util.Date
import kotlinx.coroutines.launch

/** The status tip: shown once, under the first row the student ticks, until closed. */
private const val TIP_SHOWN_KEY = "statusTipShown"
private const val TIP_ROW_KEY = "statusTipRow"
private const val TIP_KIND_KEY = "statusTipKind"

/** The first time anything is ticked (Done, Studied or Turn in), the tip goes under that row. Never in the tour's pretend week. */
fun firstTick(m: AppModel, it: ItemState, p: AppModel.Progress) {
    if (Prefs.bool(TIP_SHOWN_KEY) || p == AppModel.Progress.TODO || m.practice) return
    Prefs.set(TIP_SHOWN_KEY, true)
    Prefs.set(TIP_ROW_KEY, it.schoologyId)
    Prefs.set(TIP_KIND_KEY, (if (p == AppModel.Progress.TURNED_IN) TipKind.TURNED_IN else if (it.isExam) TipKind.STUDIED else TipKind.DONE).raw)
}

/** Sets the student's mark from a To Do control, and shows the first-tick tip. */
fun setFromRow(m: AppModel, it: ItemState, p: AppModel.Progress) {
    m.setProgress(it, p)
    firstTick(m, it, p)
}

/**
 * 1a · To Do: everything due, grouped by class in Course Dashboard order as
 * library catalog cards, or (Urgent first) by how soon it's due.
 */
@Composable
fun ToDoScreen() {
    val model = model
    Prefs.revision.intValue
    val layout = ToDoLayout.current
    var filter by remember(layout) { mutableStateOf<DayFilter?>(null) }
    var showOlder by rememberSaveable { mutableStateOf(false) }
    val items = model.todoItems()
    val now = Date()
    val big = LocalDensity.current.fontScale >= 1.6f

    ScrollPage(refresh = { model.refreshTodo() }, spacing = 12.dp) {
        // Header: date, title, ripple progress, gear, week strip.
        val open = items.filter { !model.isFinished(it) }
        val today = items.filter { TodoLogic.bucket(it.due, now) == TodoLogic.Bucket.TODAY }
        val ripple: (@Composable () -> Unit)? = if (today.isEmpty()) null else { { RippleProgress(today.count { model.isFinished(it) }, today.size) } }
        ScreenHeader("To Do", kicker = Fmt.header(now)) {
            if (!big && ripple != null) Box(Modifier.padding(bottom = Theme.Space.xs, end = Theme.Space.xs)) { ripple() }
            ScreenOptions(SettingsPage.TODO)
        }
        if (big && ripple != null) Box(Modifier.padding(horizontal = Theme.Space.xs)) { ripple() }
        // Late, today and the next six days, a dot per item still to do; tap one to show only it.
        if (layout == ToDoLayout.WEEK) WeekStrip(open, now, filter) { filter = it }
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
        val groups = if (layout == ToDoLayout.URGENT) urgentGroups(model, current, now) else grouped(model, current)
        if (groups.isEmpty() && older.isEmpty()) {
            EmptyState(if (filter == null) "Nothing due" else "Nothing here",
                if (filter == null) "Schoology lists nothing for you right now." else "Tap the day again to see everything.",
                art = filter == null)
        }
        // Each group is a library catalog card: its heading (Dewey number, class) over
        // a red rule, then the work on pale blue ruled lines.
        for (g in groups) key(g.key) {
            Card {
                CardHeading(g, now, layout)
                RowList(g.items, now, layout)
            }
        }
        if (older.isNotEmpty()) {
            Column(Modifier.animateContentSize()) {
                Row(Modifier.fillMaxWidth().clickable { showOlder = !showOlder }.padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Older · ${older.size}", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                    Spacer(Modifier.width(6.dp))
                    Text("over ${TodoLogic.OLDER_DAYS} days late", style = Theme.footnote, color = C.tertiary, modifier = Modifier.weight(1f))
                    Icon(if (showOlder) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = C.tertiary)
                }
                if (showOlder) Card { RowList(sortItems(model, older), now, layout) }
            }
        }
    }
}

/** A card's rows on pale blue ruled lines, with the status tip under the row it belongs to. */
@Composable
private fun RowList(items: List<ItemState>, now: Date, layout: ToDoLayout) {
    val model = model
    val nav = nav
    val tipRow = Prefs.string(TIP_ROW_KEY)
    items.forEachIndexed { i, it ->
        key(it.schoologyId) {
            if (i > 0) HorizontalDivider(color = C.catalogLine, thickness = 1.dp, modifier = Modifier.padding(start = 12.dp))
            TodoRowItem(it, now, course = if (layout == ToDoLayout.URGENT) model.course(it.sectionId)?.shortTitle ?: it.courseName else null)
            if (tipRow == it.schoologyId) StatusTip(TipKind.of(Prefs.string(TIP_KIND_KEY)),
                onGuide = { nav.push(Route.SettingsPage(SettingsPage.STATUS_GUIDE)) },
                onClose = { Prefs.set(TIP_ROW_KEY, "") },
                modifier = Modifier.padding(start = Theme.Space.m, end = Theme.Space.m, bottom = Theme.Space.m))
        }
    }
}

private fun filterTitle(f: DayFilter, now: Date) = when (f) {
    DayFilter.Late -> "Late"
    is DayFilter.Day -> when (f.k) { 0 -> "Today"; 1 -> "Tomorrow"; else -> Fmt.header(Fmt.addDays(Fmt.startOfDay(now), f.k)) }
}

/** `sectionId` is set for a class's card (Week); Urgent first's sections have none. */
private data class Group(val key: String, val title: String, val items: List<ItemState>, val sectionId: String? = null)

private fun grouped(m: AppModel, items: List<ItemState>): List<Group> {
    val bySection = items.groupBy { it.sectionId ?: "" }.toMutableMap()
    val out = mutableListOf<Group>()
    for (c in m.courses) {
        val list = bySection.remove(c.sectionId)
        if (!list.isNullOrEmpty()) out += Group(c.sectionId, c.shortTitle, sortItems(m, list), c.sectionId)
    }
    val rest = bySection.values.flatten()
    if (rest.isNotEmpty()) out += Group("other", "Other", sortItems(m, rest))
    return out
}

/** Urgent first: Late, Today, Next 7 days, Later, then everything done (sorted last, as in Week). */
private fun urgentGroups(m: AppModel, items: List<ItemState>, now: Date): List<Group> {
    val open = items.filter { !m.isFinished(it) }.groupBy { TodoLogic.bucket(it.due, now) }
    val out = mutableListOf<Group>()
    for ((b, title) in listOf(TodoLogic.Bucket.OVERDUE to "Late", TodoLogic.Bucket.TODAY to "Today", TodoLogic.Bucket.WEEK to "Next 7 days", TodoLogic.Bucket.LATER to "Later")) {
        val list = open[b]
        if (!list.isNullOrEmpty()) out += Group(b.name, title, sortItems(m, list))
    }
    val finished = items.filter { m.isFinished(it) }
    if (finished.isNotEmpty()) out += Group("finished", "Done", sortItems(m, finished))
    return out
}

/** Unfinished first, then by due date (overdue oldest first, upcoming soonest first). */
private fun sortItems(m: AppModel, items: List<ItemState>) =
    items.sortedWith(compareBy<ItemState> { m.isFinished(it) }.thenBy { it.dueAt ?: Long.MAX_VALUE })

/**
 * The top of a catalog card: the class's call number in its colour (none when Dewey
 * can't tell, see CallNumber), the class, its period, a red rule under it. Press and
 * hold to set the number.
 */
@Composable
private fun CardHeading(g: Group, now: Date, layout: ToDoLayout) {
    val model = model
    val open = g.items.filter { !model.isFinished(it) }
    val course = model.course(g.sectionId)
    val number = course?.let { CallNumber.number(it, Prefs.string(CallNumber.KEY)) }
    // Urgent first already has a Today section.
    val dueToday = layout == ToDoLayout.WEEK && open.any { TodoLogic.bucket(it.due, now) == TodoLogic.Bucket.TODAY }
    var menu by remember { mutableStateOf(false) }
    val rule = C.catalogRule
    val color = if (course != null) classColor(course.sectionId) else C.accent
    Box {
        Row(
            Modifier.fillMaxWidth()
                .then(if (course != null) Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true }, onLongClickLabel = "Change call number") else Modifier)
                .semantics(mergeDescendants = true) { heading() }
                .padding(start = Theme.Space.l, end = Theme.Space.l, top = Theme.Space.m)
                .drawBehind { drawLine(rule, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }
                .padding(bottom = Theme.Space.s),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.Space.s),
        ) {
            if (number != null) Text(number, style = Theme.callNumber, color = color,
                modifier = Modifier.semantics { contentDescription = "Call number $number" })
            Text(g.title, style = Theme.headline, color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (dueToday) Text("Due today", style = Theme.caption.copy(fontWeight = FontWeight.SemiBold), color = C.accent,
                modifier = Modifier.clip(CircleShape).background(C.accentSoft).padding(horizontal = 7.dp, vertical = 2.dp))
            Spacer(Modifier.weight(1f))
            val period = course?.periodLabel?.takeIf { it.length <= 3 }
            Text(listOfNotNull(period, "${open.size} open").joinToString(" · "), style = Theme.footnote, color = C.secondary, maxLines = 1)
        }
        if (course != null) CallNumberMenu(course, menu) { menu = false }
    }
}

// ── One row ──────────────────────────────────────────────────────────────

@Composable
fun TodoRowItem(item: ItemState, now: Date, course: String? = null) {
    // ItemState is mutable and the same instance: read the revision so a mark redraws this.
    model.revision.intValue
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
            Modifier.fillMaxWidth().coachTarget("todo.row.${item.schoologyId}")
                .combinedClickable(onClick = { nav.push(Route.Assignment(item.schoologyId)) }, onLongClick = { menu = true })
                .padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column(Modifier.matchParentSize(), horizontalAlignment = Alignment.Start) {
              Column(Modifier.width(44.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(44.dp).coachTarget("todo.check.${item.schoologyId}").doneRipple(progress != AppModel.Progress.TODO, C.accent)
                        .clip(CircleShape).clickable(role = Role.Checkbox) {
                        // One tap: to do ↔ done (a turned-in row goes back to done), like the extension.
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        setFromRow(model, item, if (progress == AppModel.Progress.TODO) AppModel.Progress.DONE else if (progress == AppModel.Progress.DONE) AppModel.Progress.TODO else AppModel.Progress.DONE)
                    }.semantics { contentDescription = "Status: ${if (item.submitted) "Submitted on Schoology" else label(item, progress)}" },
                    contentAlignment = Alignment.Center,
                ) { CheckMark(item) }
                TypeBar(item.type, Modifier.weight(1f, fill = true).padding(top = 2.dp))
              }
            }
            Column(Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(start = 50.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Urgent first mixes classes, so each row says its own.
                if (course != null) Text(course, style = Theme.caption.copy(fontWeight = FontWeight.SemiBold), color = C.secondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
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
                    onClick = { menu = false; setFromRow(model, item, p) },
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
    // ItemState is mutable and the same instance: read the revision so a mark redraws this.
    model.revision.intValue
    val model = model
    val nav = nav
    val m = Modifier.coachTarget("todo.action.${item.schoologyId}")
    when {
        item.submitted -> {}
        item.turnedIn -> PillButton("Undo", color = C.secondary, modifier = m) { model.setProgress(item, AppModel.Progress.DONE) }
        item.hasDropbox -> PillButton("Submit", external = true, modifier = m) { nav.openSheet(SheetTarget(submitUrl, item.schoologyId)) }
        !item.isExam -> PillButton("Turn in", color = C.good, modifier = m) { setFromRow(model, item, AppModel.Progress.TURNED_IN) }
    }
}

/** "Mon Oct 5, 11:59 pm · 7 days late"; a test adds its countdown and studied mark. */
@Composable
private fun DueText(item: ItemState, now: Date, modifier: Modifier) {
    // ItemState is mutable and the same instance: read the revision so a mark redraws this.
    model.revision.intValue
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
    // ItemState is mutable and the same instance: read the revision so a mark redraws this.
    model.revision.intValue
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

/**
 * The To Do header: Late, then today and the next six days, as bare type (no
 * boxes). Each item still to do is a dot in its type's colour (black = test,
 * grey = the rest, like the type bar on each row); tap a day to show only it,
 * and it sits in a drop.
 */
@Composable
fun WeekStrip(items: List<ItemState>, now: Date, selected: DayFilter?, onSelect: (DayFilter?) -> Unit) {
    val tiles = listOf<DayFilter>(DayFilter.Late) + (0 until 7).map { DayFilter.Day(it) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (f in tiles) {
            val its = items.filter { f.matches(it, now) }.sortedBy { if (it.isExam) 0 else 1 }
            val on = selected == f
            val isLate = f == DayFilter.Late
            val isToday = f == DayFilter.Day(0)
            val date = (f as? DayFilter.Day)?.let { Fmt.addDays(Fmt.startOfDay(now), it.k) }
            val label = when { isLate -> "Late"; isToday -> "Today"; else -> Fmt.weekday(date!!) }
            val sub = date?.let(Fmt::dayNumber) ?: "${its.size}"
            Column(
                Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(Theme.Radius.card))
                    .clickable { onSelect(if (on) null else f) }
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = "$label, ${if (its.isEmpty()) "nothing due" else "${its.size} ${if (isLate) "overdue" else "due"}"}"; this.selected = on },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(label, style = Theme.caption.copy(fontWeight = FontWeight.SemiBold), maxLines = 1,
                    color = if (isLate) C.late else if (isToday) C.accent else C.secondary)
                // Bare type; the chosen day sits in a drop, the logo's shape.
                Box(Modifier.size(width = 34.dp, height = 36.dp)
                    .then(if (on) Modifier.background(if (isLate) C.late else C.accent, DropShape) else Modifier),
                    contentAlignment = Alignment.BottomCenter) {
                    Text(sub, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (on) C.onAccent else if (isLate) C.late else C.text,
                        modifier = Modifier.padding(bottom = 5.dp))
                }
                Dots(its, false)
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
