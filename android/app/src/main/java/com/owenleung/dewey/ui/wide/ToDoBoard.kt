package com.owenleung.dewey.ui.wide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.store.DayFilter
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.ItemState
import com.owenleung.dewey.store.PlanLogic
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.store.ToDoLayout
import com.owenleung.dewey.store.TodoLogic
import com.owenleung.dewey.ui.AsOfBanner
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.EmptyState
import com.owenleung.dewey.ui.ErrorState
import com.owenleung.dewey.ui.Nav
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.ScreenOptions
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.SettingsPage
import com.owenleung.dewey.ui.SkeletonRows
import com.owenleung.dewey.ui.TaskTarget
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.ui.todo.CardHeading
import com.owenleung.dewey.ui.todo.Group
import com.owenleung.dewey.ui.todo.MonthGrid
import com.owenleung.dewey.ui.todo.PLAN_HINT_KEY
import com.owenleung.dewey.ui.todo.TodoRowItem
import com.owenleung.dewey.ui.todo.WeekStrip
import com.owenleung.dewey.ui.todo.grouped
import com.owenleung.dewey.ui.todo.sortItems
import com.owenleung.dewey.ui.tour.coachTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.Date
import kotlin.math.roundToInt

/** To Do's three views on a wide window. Remembered between launches. */
enum class BoardView(val raw: String, val title: String) {
    CLASSES("classes", "By class"), CALENDAR("calendar", "Calendar"), PLAN("plan", "Plan");

    companion object {
        const val KEY = "duoTodoView"
        val current: BoardView get() = entries.firstOrNull { it.raw == Prefs.string(KEY) } ?: CLASSES
    }
}

/**
 * To Do on a wide window (ios/App/Screens/Wide/ToDoBoard.swift): a board, one column per class,
 * each a catalog card (call number, class, at most one stamp, its work on ruled lines) only as tall
 * as its work. Calendar puts the month left of the middle and the picked day's work right of it;
 * Plan is a second board, To do / Later / Done. A card opens its assignment over the right half and
 * its column slides to the front; nothing opens by itself.
 */
@Composable
fun ToDoBoard(split: Split) {
    val model = model
    Prefs.revision.intValue
    model.revision.intValue
    val view = BoardView.current
    var filter by remember(view) { mutableStateOf<DayFilter?>(null) }
    val now = Date()
    val items = model.todoItems()
    val open = items.filter { model.isPending(it, now) }
    Column(Modifier.fillMaxSize()) {
        Header(view, open, now, filter) { filter = it }
        if (model.todoAsOf == null && items.isEmpty()) {
            Column(Modifier.padding(Theme.Space.l)) {
                val e = model.todoError
                if (e != null) ErrorState(e, retry = { model.scope.launch { model.refreshTodo() } }) else SkeletonRows()
            }
        } else when (view) {
            BoardView.CLASSES -> ClassBoard(items, now, filter, split)
            BoardView.CALENDAR -> CalendarView(open, now, split)
            BoardView.PLAN -> PlanBoard(now, split)
        }
    }
}

// ── Header: title, the three views, +, gear, the week ───────────────────

@Composable
private fun Header(view: BoardView, open: List<ItemState>, now: Date, filter: DayFilter?, onFilter: (DayFilter?) -> Unit) {
    val model = model
    val nav = nav
    Column(Modifier.fillMaxWidth().padding(horizontal = Theme.Space.l).padding(top = Theme.Space.s, bottom = Theme.Space.xs),
        verticalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.Space.l)) {
            Text("To Do", style = Theme.display, color = C.text, maxLines = 1, modifier = Modifier.semantics { heading() })
            Segments(view)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.task = TaskTarget() }, modifier = Modifier.coachTarget("todo.add")) {
                    Icon(Icons.Outlined.Add, "New task", tint = C.accent, modifier = Modifier.size(28.dp))
                }
                ScreenOptions(SettingsPage.TODO)
            }
        }
        // Late, today and the next six days, a dot per item still to do; tap one to show only it.
        if (view == BoardView.CLASSES) Box(Modifier.widthIn(max = 520.dp)) { WeekStrip(open, now, filter, onFilter) }
        // Offline note / refresh spinner: only takes room when there's something to show.
        if ((model.todoAsOf != null && model.todoError != null) || model.refreshingTodo) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsOfBanner(model.todoAsOf, model.todoError)
                if (model.refreshingTodo) { Spacer(Modifier.width(8.dp)); CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = C.secondary) }
            }
        }
    }
}

/** By class · Calendar · Plan, as one segmented control. */
@Composable
private fun Segments(view: BoardView) {
    Row(Modifier.clip(CircleShape).background(C.tagSoft).padding(2.dp)) {
        for (v in BoardView.entries) {
            val on = v == view
            Box(Modifier.heightIn(min = 36.dp).clip(CircleShape).background(if (on) C.card else Color.Transparent)
                .clickable { Prefs.set(BoardView.KEY, v.raw) }
                .semantics { role = Role.Tab; selected = on }
                .padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(v.title, style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = if (on) C.text else C.secondary, maxLines = 1)
            }
        }
    }
}

// ── By class ────────────────────────────────────────────────────────────

@Composable
private fun ClassBoard(items: List<ItemState>, now: Date, filter: DayFilter?, split: Split) {
    val model = model
    val density = LocalDensity.current
    val columnWidth = 272.dp * density.fontScale.coerceIn(1f, 1.6f)
    val filtered = items.filter { filter == null || (model.isPending(it, now) && filter.matches(it, now)) }
    val older = filtered.filter { TodoLogic.isOlder(it.due, now) && !model.isFinished(it) }
    val current = filtered.filter { it !in older }
    val groups = grouped(model, current) + (if (older.isNotEmpty()) listOf(Group("older", "Older", sortItems(model, older))) else emptyList())
    val quiet = if (filter == null) model.classes.filter { c -> groups.none { it.key == c.sectionId } } else emptyList()
    val hScroll = rememberScrollState()
    val xs = remember { mutableStateMapOf<String, Int>() }
    // The opened item's column slides to the left edge, clear of the item, once the item has slid in.
    val opened = (split.opened(Nav.TODO) as? Route.Assignment)?.id
    val groupsNow by rememberUpdatedState(groups)
    LaunchedEffect(opened) {
        val id = opened ?: return@LaunchedEffect
        val key = groupsNow.firstOrNull { g -> g.items.any { it.schoologyId == id } }?.key ?: return@LaunchedEffect
        delay(320)
        val margin = with(density) { Theme.Space.l.roundToPx() }
        xs[key]?.let { hScroll.animateScrollTo((it - margin).coerceAtLeast(0)) }
    }
    ScrollPage(refresh = { model.refreshTodo(quick = true) }, horizontal = 0.dp, spacing = 0.dp) {
        if (groups.isEmpty()) Box(Modifier.padding(Theme.Space.l)) {
            EmptyState(if (filter == null) "Nothing due" else "Nothing here",
                if (filter == null) "Schoology lists nothing for you right now." else "Tap the day again to see everything.", art = filter == null)
        } else Row(Modifier.horizontalScroll(hScroll).padding(horizontal = Theme.Space.l, vertical = Theme.Space.m),
            horizontalArrangement = Arrangement.spacedBy(Theme.Space.m), verticalAlignment = Alignment.Top) {
            for (g in groups) key(g.key) {
                Card(Modifier.width(columnWidth).onGloballyPositioned { xs[g.key] = it.positionInParent().x.roundToInt() }) {
                    CardHeading(g, now, ToDoLayout.WEEK)
                    g.items.forEachIndexed { i, it ->
                        key(it.schoologyId) {
                            if (i > 0) HorizontalDivider(color = C.catalogLine, thickness = 1.dp, modifier = Modifier.padding(start = 12.dp))
                            BoardCard(it, now, showClass = g.key == "older" || g.key == "other", split = split)
                        }
                    }
                }
            }
            if (quiet.isNotEmpty()) Text("Nothing open: " + quiet.joinToString(", ") { it.shortTitle }, style = Theme.footnote, color = C.tertiary,
                modifier = Modifier.width(150.dp).padding(top = Theme.Space.m))
        }
    }
}

/** One piece of work: the phone's row, with its action on its own line, tinted while it's open on the right. */
@Composable
private fun BoardCard(it: ItemState, now: Date, showClass: Boolean, split: Split, inPlan: Boolean = false) {
    val model = model
    val r = split.opened(Nav.TODO)
    val isOpen = r is Route.Assignment && r.id == it.schoologyId
    Box(Modifier.background(if (isOpen) C.accentSoft else Color.Transparent).semantics { selected = isOpen }) {
        TodoRowItem(it, now, inPlan = inPlan, board = true,
            course = if (showClass) model.course(it.sectionId)?.shortTitle ?: (if (it.mine) "My task" else it.courseName) else null)
    }
}

// ── Calendar ────────────────────────────────────────────────────────────

/** The month left of the middle, the picked day and the day after right of it (stacked beside an opened item). */
@Composable
private fun CalendarView(open: List<ItemState>, now: Date, split: Split) {
    val model = model
    var calDay by remember { mutableStateOf(Fmt.startOfDay(Date()).time) }
    var month by remember { mutableStateOf(YearMonth.now(Fmt.zone)) }
    var forward by remember { mutableStateOf(true) }
    val monthCard: @Composable () -> Unit = {
        Card(padding = Theme.Space.m) {
            MonthGrid(open, now, DayFilter.Span(calDay), { f -> if (f is DayFilter.Span) calDay = f.start }, month, forward) { n ->
                forward = n > 0; month = month.plusMonths(n.toLong())
            }
        }
    }
    val refresh: suspend () -> Unit = { model.refreshTodo(quick = true) }
    if (split.twoColumns(Nav.TODO)) Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(split.listWidth)) { ScrollPage(refresh = refresh) { monthCard() } }
        Box(Modifier.weight(1f)) { ScrollPage(refresh = refresh) { Days(open, now, Date(calDay), split) } }
    } else ScrollPage(refresh = refresh) {
        monthCard()
        Days(open, now, Date(calDay), split)
    }
}

@Composable
private fun Days(open: List<ItemState>, now: Date, day: Date, split: Split) {
    val model = model
    val late = if (Fmt.sameDay(day, now)) open.filter { DayFilter.Late.matches(it, now) } else emptyList()
    val next = Fmt.addDays(day, 1)
    fun due(d: Date) = sortItems(model, open.filter { it.due?.let { x -> Fmt.sameDay(x, d) } == true && it !in late })
    if (late.isNotEmpty()) DayCard("Late", sortItems(model, late), now, split, late = true)
    DayCard(dayTitle(day, now), due(day), now, split)
    DayCard(dayTitle(next, now), due(next), now, split)
}

private fun dayTitle(day: Date, now: Date) = when {
    Fmt.sameDay(day, now) -> "Today"
    Fmt.sameDay(day, Fmt.addDays(now, 1)) -> "Tomorrow"
    else -> Fmt.header(day)
}

@Composable
private fun DayCard(title: String, items: List<ItemState>, now: Date, split: Split, late: Boolean = false) {
    val rule = if (late) C.late else C.catalogRule
    Card {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }
            .padding(start = Theme.Space.m, end = Theme.Space.m, top = Theme.Space.m)
            .drawBehind { drawLine(rule, Offset(0f, size.height), Offset(size.width, size.height), if (late) 2.dp.toPx() else 1.5.dp.toPx()) }
            .padding(bottom = Theme.Space.s), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Theme.callNumber, color = if (late) C.late else C.text, modifier = Modifier.weight(1f))
            Text("${items.size}", style = Theme.footnote, color = C.secondary)
        }
        if (items.isEmpty()) Text("Nothing due", style = Theme.subhead, color = C.secondary,
            modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = Theme.Space.m, vertical = Theme.Space.m))
        items.forEachIndexed { i, it ->
            key(it.schoologyId) {
                if (i > 0) HorizontalDivider(color = C.catalogLine, thickness = 1.dp, modifier = Modifier.padding(start = 12.dp))
                BoardCard(it, now, showClass = true, split = split)
            }
        }
    }
}

// ── Plan ────────────────────────────────────────────────────────────────

/** Where a dragged Plan card can land. */
private sealed class Drop {
    data class OnCard(val id: String) : Drop()
    data class Heading(val b: PlanLogic.Bucket) : Drop()
    data class Column(val b: PlanLogic.Bucket) : Drop()
}

/**
 * The student's own order as three columns (To do numbered, Later, Done dimmed). Hold a card, then
 * drag: onto a card takes that card's place, onto a heading goes to the top of that section, under
 * a column's last card goes to its end; into Done marks it done. The same PlanLogic as the phone.
 */
@Composable
private fun PlanBoard(now: Date, split: Split) {
    val model = model
    val haptic = LocalHapticFeedback.current
    val lists = model.planLists(now)
    val rows = PlanLogic.rows(lists.todo.map { it.schoologyId }, lists.later.map { it.schoologyId }, lists.done.map { it.schoologyId })
    val rowsNow by rememberUpdatedState(rows)
    val columns = listOf(PlanLogic.Bucket.TODO to lists.todo, PlanLogic.Bucket.LATER to lists.later, PlanLogic.Bucket.DONE to lists.done)
    val byId = (lists.todo + lists.later + lists.done).associateBy { it.schoologyId }
    val frames = remember { mutableStateMapOf<Drop, Rect>() }
    val coords = remember { mutableStateMapOf<String, LayoutCoordinates>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    fun apply(placed: Map<String, PlanLogic.Place>, id: String) {
        model.applyPlan(placed, id)
        Prefs.set(PLAN_HINT_KEY, true)
    }

    fun drop(id: String) {
        val rows = rowsNow
        val from = rows.indexOf(PlanLogic.Row.Item(id))
        if (from < 0) return
        val p = finger
        val target = frames.entries.firstOrNull { (k, r) -> k is Drop.OnCard && k.id != id && r.contains(p) }?.key
            ?: frames.entries.firstOrNull { (k, r) -> k is Drop.Heading && r.contains(p) }?.key
            ?: frames.entries.firstOrNull { (k, r) -> k is Drop.Column && p.x >= r.left && p.x <= r.right && p.y > r.top }?.key
            ?: return
        when (target) {
            is Drop.OnCard -> {
                val to = rows.indexOf(PlanLogic.Row.Item(target.id))
                if (to < 0) return
                // Down its own section, the card moves up to make room; from above or another section, down.
                val down = from < to && rows.subList(from, to + 1).none { it is PlanLogic.Row.Header }
                apply(PlanLogic.moved(rows, from, if (down) to + 1 else to), id)
            }
            is Drop.Heading -> apply(PlanLogic.moved(rows, id, target.b), id)
            is Drop.Column -> {
                val h = rows.indexOf(PlanLogic.Row.Header(target.b))
                val end = (h + 1 until rows.size).firstOrNull { rows[it] is PlanLogic.Row.Header } ?: rows.size
                apply(PlanLogic.moved(rows, from, end), id)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
        // All three across when there's room; they scroll sideways when narrower.
        val width = maxOf(232.dp, (maxWidth - Theme.Space.l * 2 - Theme.Space.m * 2) / 3)
        ScrollPage(refresh = { model.refreshTodo(quick = true) }, horizontal = 0.dp, spacing = 0.dp) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Theme.Space.l, vertical = Theme.Space.m),
                horizontalArrangement = Arrangement.spacedBy(Theme.Space.m), verticalAlignment = Alignment.Top) {
                for ((b, items) in columns) {
                    Card(Modifier.width(width).onGloballyPositioned { frames[Drop.Column(b)] = it.boundsInRoot() }) {
                        val rule = if (b == PlanLogic.Bucket.TODO) C.catalogRule else C.catalogLine
                        Row(Modifier.fillMaxWidth().onGloballyPositioned { frames[Drop.Heading(b)] = it.boundsInRoot() }
                            .semantics(mergeDescendants = true) { heading() }
                            .padding(start = Theme.Space.m, end = Theme.Space.m, top = Theme.Space.m)
                            .drawBehind { drawLine(rule, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }
                            .padding(bottom = Theme.Space.s), verticalAlignment = Alignment.CenterVertically) {
                            Text(b.title, style = Theme.callNumber, color = if (b == PlanLogic.Bucket.DONE) C.secondary else C.text, modifier = Modifier.weight(1f))
                            Text(if (b == PlanLogic.Bucket.DONE) "${items.size} today" else "${items.size}", style = Theme.footnote, color = C.secondary)
                        }
                        if (items.isEmpty()) Text(if (b == PlanLogic.Bucket.DONE) "Drag work here when it's done." else "Drag work here.",
                            style = Theme.footnote, color = C.tertiary, modifier = Modifier.heightIn(min = 44.dp).padding(Theme.Space.m))
                        items.forEachIndexed { i, it ->
                            val id = it.schoologyId
                            key(id) {
                                if (i > 0) HorizontalDivider(color = C.catalogLine, thickness = 1.dp, modifier = Modifier.padding(start = 12.dp))
                                val actions = listOf(
                                    CustomAccessibilityAction("Move up") { apply(PlanLogic.nudged(rowsNow, id, -1), id); true },
                                    CustomAccessibilityAction("Move down") { apply(PlanLogic.nudged(rowsNow, id, 1), id); true },
                                    CustomAccessibilityAction(if (b == PlanLogic.Bucket.LATER) "Move to To do" else "Move to Later") {
                                        apply(PlanLogic.moved(rowsNow, id, if (b == PlanLogic.Bucket.LATER) PlanLogic.Bucket.TODO else PlanLogic.Bucket.LATER), id); true
                                    },
                                )
                                Box(Modifier.alpha(if (dragging == id) 0.35f else if (b == PlanLogic.Bucket.DONE) 0.6f else 1f)
                                    .onGloballyPositioned { c -> coords[id] = c; frames[Drop.OnCard(id)] = c.boundsInRoot() }
                                    .semantics { customActions = actions }
                                    .pointerInputDrag(id, onStart = { o ->
                                        dragging = id
                                        finger = coords[id]?.localToRoot(o) ?: o
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }, onMove = { d -> finger += d }, onEnd = { done ->
                                        if (done) drop(id)
                                        dragging = null
                                    })) {
                                    BoardCard(it, now, showClass = true, split = split, inPlan = true)
                                    // To do is numbered: the order to do it in.
                                    if (b == PlanLogic.Bucket.TODO) Text("${i + 1}", style = Theme.callNumber, color = if (i == 0) C.accent else C.tertiary,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(top = Theme.Space.s, end = Theme.Space.m))
                                }
                            }
                        }
                    }
                }
            }
        }
        // The card under the finger while it's dragged.
        dragging?.let { byId[it] }?.let { item ->
            Text(item.title, style = Theme.body, color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset { IntOffset((finger.x - origin.x).roundToInt() - 40, (finger.y - origin.y).roundToInt() - 30) }
                    .width(width).shadow(8.dp, RoundedCornerShape(Theme.Radius.card))
                    .background(C.card, RoundedCornerShape(Theme.Radius.card))
                    .border(1.5.dp, C.accent, RoundedCornerShape(Theme.Radius.card)).padding(Theme.Space.m))
        }
    }
}

/** Hold, then drag: `onStart` with the touch in the card's own coordinates, `onEnd(true)` on a drop. */
private fun Modifier.pointerInputDrag(key: String, onStart: (Offset) -> Unit, onMove: (Offset) -> Unit, onEnd: (Boolean) -> Unit): Modifier =
    this.then(Modifier.pointerInput(key) {
        detectDragGesturesAfterLongPress(onDragStart = onStart, onDragEnd = { onEnd(true) }, onDragCancel = { onEnd(false) }) { change, amount ->
            change.consume(); onMove(amount)
        }
    })
