package com.owenleung.dewey.ui.todo

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.store.ItemState
import com.owenleung.dewey.store.PlanHidden
import com.owenleung.dewey.store.PlanLogic
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.EmptyState
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.ui.tour.coachTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date

/** To Do's two views: every item (List: by class or urgent first), or Plan, the student's own order. Remembered between launches. */
enum class ToDoMode(val raw: String) {
    LIST("list"), PLAN("plan");

    companion object {
        const val KEY = "todoMode"
        val current: ToDoMode get() = entries.firstOrNull { it.raw == Prefs.string(KEY) } ?: LIST
    }
}

const val PLAN_HINT_KEY = "planHintClosed"
/** Folded sections, e.g. "later,done". Done starts folded: it only grows through the day. */
const val PLAN_COLLAPSED_KEY = "planCollapsed"

/** A drag by the grip (or a held row), while the finger is down: the rows as they're being reordered. */
private data class GripDrag(val id: String, val start: List<PlanLogic.Row>, val rows: List<PlanLogic.Row>)

/**
 * To Do › Plan: all open work in three sections, To do, Later and Done
 * (PlanLogic), in the student's order; To do is numbered 1…N. Drag a row by
 * its grip (at once) or by holding it anywhere else, within a section or into
 * another one (into Done marks it done). Later and Done fold away with a tap
 * on their heading and still take drops. Until it's moved, work sits where its
 * due date puts it (ios/App/Screens/ToDo/PlanView.swift).
 */
@Composable
fun PlanContent(now: Date, scroll: ScrollState, onNewTask: () -> Unit) {
    val model = model
    Prefs.revision.intValue
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val hintClosed = Prefs.bool(PLAN_HINT_KEY)
    val collapsed = (Prefs.string(PLAN_COLLAPSED_KEY) ?: "done").split(",").mapNotNull { PlanLogic.Bucket.of(it) }.toSet()
    var grip by remember { mutableStateOf<GripDrag?>(null) }
    val frames = remember { mutableStateMapOf<PlanLogic.Row, Rect>() }
    var fingerY by remember { mutableStateOf(0f) }
    var edgeJob by remember { mutableStateOf<Job?>(null) }
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val edgeTop = with(LocalDensity.current) { 130.dp.toPx() }
    val edgeBottom = with(LocalDensity.current) { 160.dp.toPx() }

    fun toggle(b: PlanLogic.Bucket) {
        val c = if (b in collapsed) collapsed - b else collapsed + b
        Prefs.set(PLAN_COLLAPSED_KEY, PlanLogic.Bucket.entries.filter { it in c }.joinToString(",") { it.raw })
    }

    val lists = model.planLists(now)
    val all = mapOf(PlanLogic.Bucket.TODO to lists.todo, PlanLogic.Bucket.LATER to lists.later, PlanLogic.Bucket.DONE to lists.done)
    fun shown(b: PlanLogic.Bucket) = if (b in collapsed) emptyList() else (all[b] ?: emptyList()).map { it.schoologyId }
    val base = PlanLogic.rows(shown(PlanLogic.Bucket.TODO), shown(PlanLogic.Bucket.LATER), shown(PlanLogic.Bucket.DONE))
    val hidden: PlanHidden = collapsed.associateWith { b -> (all[b] ?: emptyList()).map { it.schoologyId } }
    val rows = grip?.rows ?: base
    val byId = (lists.todo + lists.later + lists.done).associateBy { it.schoologyId }
    val numbers = numbers(rows)

    fun apply(placed: Map<String, PlanLogic.Place>, id: String) {
        model.applyPlan(placed, id)
        Prefs.set(PLAN_HINT_KEY, true)
    }

    fun edge(y: Float) = if (y < edgeTop) -1 else if (y > screenH - edgeBottom) 1 else 0

    // The grip: the row follows the finger, swapping with each row once the finger is past
    // that row's middle. Saved when the finger lifts.
    fun dragTo(id: String, y: Float) {
        var g = grip ?: GripDrag(id, rows, rows)
        if (g.id != id) return
        while (true) {
            val i = g.rows.indexOf(PlanLogic.Row.Item(id))
            if (i < 0) break
            val next = g.rows.getOrNull(i + 1)?.let { frames[it] }
            val prev = if (i - 1 >= 1) g.rows.getOrNull(i - 1)?.let { frames[it] } else null
            g = when {
                next != null && y > next.center.y -> g.copy(rows = PlanLogic.nudging(g.rows, id, 1))
                prev != null && y < prev.center.y -> g.copy(rows = PlanLogic.nudging(g.rows, id, -1))
                else -> break
            }
        }
        if (grip != null && g.rows != grip!!.rows) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        grip = g
        fingerY = y
        // Held near the top or bottom of the screen: the row keeps moving and the page scrolls with it.
        if (edge(y) != 0 && edgeJob == null) {
            edgeJob = scope.launch {
                while (isActive) {
                    val dir = edge(fingerY)
                    val cur = grip
                    if (dir == 0 || cur == null || cur.id != id) break
                    val moved = PlanLogic.nudging(cur.rows, id, dir)
                    if (moved != cur.rows) {
                        grip = cur.copy(rows = moved)
                        val h = frames[PlanLogic.Row.Item(id)]?.height ?: 120f
                        scroll.animateScrollBy(dir * h)
                    }
                    delay(220)
                }
                edgeJob = null
            }
        }
    }

    fun dragEnd(id: String) {
        edgeJob?.cancel(); edgeJob = null
        val g = grip ?: return
        grip = null
        if (g.id != id || g.rows == g.start) return
        apply(PlanLogic.place(PlanLogic.expand(g.rows, hidden)), id)
    }

    if (lists.todo.isEmpty() && lists.later.isEmpty() && lists.done.isEmpty()) {
        EmptyState("Nothing to plan", "Nothing open on Schoology right now. Add a task of your own with +.", art = true)
    } else {
        if (!hintClosed) Hint { Prefs.set(PLAN_HINT_KEY, true) }
        // One list, headings included: dragging a row past a heading puts it in that section.
        Card(Modifier.animateContentSize()) {
            for ((i, row) in rows.withIndex()) key(row) {
                when (row) {
                    is PlanLogic.Row.Header -> Heading(row.bucket, all[row.bucket]?.size ?: 0, first = i == 0, folded = row.bucket in collapsed,
                        onToggle = { toggle(row.bucket) }, onFrame = { frames[row] = it })
                    is PlanLogic.Row.Item -> byId[row.id]?.let { it ->
                        val b = bucketOf(row.id, rows)
                        if (rows.getOrNull(i - 1) is PlanLogic.Row.Item) HorizontalDivider(color = C.catalogLine, thickness = 1.dp, modifier = Modifier.padding(start = 12.dp))
                        PlanRow(it, numbers[row.id], b, now, lifted = grip?.id == row.id,
                            onFrame = { frames[row] = it },
                            onDrag = { y -> dragTo(row.id, y) }, onDragEnd = { dragEnd(row.id) },
                            onMove = { to -> apply(PlanLogic.moved(rows, row.id, to, hidden), row.id) },
                            onNudge = { by -> apply(PlanLogic.nudged(rows, row.id, by, hidden), row.id) },
                            hold = { if (b != PlanLogic.Bucket.DONE && row.id !in model.planKeep) model.planKeep += row.id })
                    }
                }
            }
        }
    }
    Card {
        Row(Modifier.fillMaxWidth().clickable(onClick = onNewTask).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Add, null, tint = C.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("New task", style = Theme.body, color = C.text)
        }
    }
    Text("Until you move it, work goes where its due date puts it: late, due today or tomorrow, and tests in the next 3 days in To do; the rest in Later.",
        style = Theme.footnote, color = C.secondary, modifier = Modifier.padding(horizontal = 16.dp))
}

/** To do's numbers, 1… down to the Later heading. */
private fun numbers(rows: List<PlanLogic.Row>): Map<String, Int> {
    val out = LinkedHashMap<String, Int>()
    for (r in rows.drop(1)) {
        if (r !is PlanLogic.Row.Item) break
        out[r.id] = out.size + 1
    }
    return out
}

private fun bucketOf(id: String, rows: List<PlanLogic.Row>): PlanLogic.Bucket {
    var b = PlanLogic.Bucket.TODO
    for (r in rows) {
        if (r is PlanLogic.Row.Header) b = r.bucket
        if (r == PlanLogic.Row.Item(id)) return b
    }
    return b
}

@Composable
private fun Hint(onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.Radius.card)).background(C.accentSoft).padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Gesture, null, tint = C.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Put these in the order you'll do them: drag a row by its dots, even into Later or Done.", style = Theme.footnote, color = C.text, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close tip", tint = C.secondary, modifier = Modifier.size(18.dp)) }
    }
}

/**
 * A section's heading: a row like the others, so drags can cross it, but never dragged
 * itself. Later and Done fold with a tap; a folded one still takes drops.
 */
@Composable
private fun Heading(b: PlanLogic.Bucket, count: Int, first: Boolean, folded: Boolean, onToggle: () -> Unit, onFrame: (Rect) -> Unit) {
    val folds = b != PlanLogic.Bucket.TODO
    val line = if (b == PlanLogic.Bucket.TODO) C.catalogRule else C.catalogLine
    val turn by animateFloatAsState(if (folded) -90f else 0f, label = "fold")
    Column(
        Modifier.fillMaxWidth().coachTarget("plan.heading.${b.raw}").onGloballyPositioned { onFrame(it.boundsInRoot()) }
            .then(if (folds) Modifier.clickable(onClick = onToggle) else Modifier)
            .semantics(mergeDescendants = true) { heading(); if (folds) stateDescription = if (folded) "Folded" else "Shown" }
            .padding(start = Theme.Space.l, end = Theme.Space.l, top = if (first) Theme.Space.m else Theme.Space.xl)
            .drawBehind { drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }
            .padding(bottom = Theme.Space.s),
        verticalArrangement = Arrangement.spacedBy(Theme.Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.title, style = Theme.headline, color = if (b == PlanLogic.Bucket.DONE) C.secondary else C.text, modifier = Modifier.weight(1f))
            Text(if (b == PlanLogic.Bucket.DONE) "$count today" else "$count", style = Theme.footnote, color = C.secondary)
            if (folds) Icon(Icons.Outlined.KeyboardArrowDown, null, tint = C.secondary, modifier = Modifier.size(20.dp).rotate(turn))
        }
        if (count == 0) Text(if (b == PlanLogic.Bucket.DONE) "Drag work here when it's done." else "Drag work here.", style = Theme.footnote, color = C.tertiary)
        else if (folded) Text("Folded. Tap to show, or drag work here.", style = Theme.footnote, color = C.tertiary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanRow(
    it: ItemState, number: Int?, b: PlanLogic.Bucket, now: Date, lifted: Boolean,
    onFrame: (Rect) -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit,
    onMove: (PlanLogic.Bucket) -> Unit, onNudge: (Int) -> Unit, hold: () -> Unit,
) {
    val model = model
    model.revision.intValue
    val id = it.schoologyId
    // Swipe left = Later (in To do) or To do (in Later); swipe right = Done. The row springs back.
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.EndToStart -> when (b) {
                PlanLogic.Bucket.TODO -> onMove(PlanLogic.Bucket.LATER)
                PlanLogic.Bucket.LATER -> onMove(PlanLogic.Bucket.TODO)
                else -> {}
            }
            SwipeToDismissBoxValue.StartToEnd -> if (b != PlanLogic.Bucket.DONE) { hold(); setFromRow(model, it, AppModel.Progress.DONE) }
            else -> {}
        }
        false
    })
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var gripCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val toRootY = { y: Float -> coords?.let { c -> c.localToRoot(Offset(0f, y)).y } ?: y }
    val actions = buildList {
        add(CustomAccessibilityAction("Move up") { onNudge(-1); true })
        add(CustomAccessibilityAction("Move down") { onNudge(1); true })
        add(CustomAccessibilityAction(if (b == PlanLogic.Bucket.LATER) "Move to To do" else "Move to Later") {
            onMove(if (b == PlanLogic.Bucket.LATER) PlanLogic.Bucket.TODO else PlanLogic.Bucket.LATER); true
        })
    }
    SwipeToDismissBox(
        swipe,
        enableDismissFromStartToEnd = b != PlanLogic.Bucket.DONE,
        enableDismissFromEndToStart = b != PlanLogic.Bucket.DONE,
        modifier = Modifier.coachTarget("plan.row.$id").onGloballyPositioned { c -> coords = c; onFrame(c.boundsInRoot()) }
            .semantics { customActions = actions },
        backgroundContent = {
            val toLeft = swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val (label, color) = if (toLeft) (if (b == PlanLogic.Bucket.LATER) "To do" to C.text else "Later" to C.secondary)
                                 else (if (it.isExam) "Studied" else "Done") to C.accent
            Box(Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp), contentAlignment = if (toLeft) Alignment.CenterEnd else Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (!toLeft) Icons.Outlined.Check else if (b == PlanLogic.Bucket.LATER) Icons.Outlined.VerticalAlignTop else Icons.Outlined.VerticalAlignBottom,
                        null, tint = C.background, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.background)
                }
            }
        },
    ) {
        Row(
            Modifier.fillMaxWidth().background(if (lifted) C.accentSoft else C.card)
                // Hold anywhere else on the row, then drag: the same move, a slower start.
                .pointerInput(id) {
                    detectDragGesturesAfterLongPress(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, _ ->
                        change.consume(); onDrag(toRootY(change.position.y))
                    }
                },
            verticalAlignment = Alignment.Top,
        ) {
            // To do is numbered; every row has the grip under its number (or where it would be).
            Column(Modifier.widthIn(min = 34.dp).padding(start = 8.dp, top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
                Text(number?.toString() ?: " ", style = Theme.callNumber, color = if (number == 1) C.accent else C.secondary)
                Grip(lifted, Modifier.coachTarget("plan.grip.$id").onGloballyPositioned { gripCoords = it }.pointerInput(id) {
                    // From the first touch, no hold.
                    detectDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, _ ->
                        change.consume(); onDrag(gripCoords?.localToRoot(change.position)?.y ?: 0f)
                    }
                })
            }
            Box(Modifier.weight(1f).alpha(if (b == PlanLogic.Bucket.DONE || model.planFinished(it)) 0.6f else 1f)) {
                TodoRowItem(it, now, course = model.course(it.sectionId)?.shortTitle ?: if (it.mine) "My task" else it.courseName,
                    inPlan = true, beforeSet = hold)
            }
        }
    }
}

/** The drag grip under a plan row's number: two columns of three dots. */
@Composable
private fun Grip(active: Boolean, modifier: Modifier) {
    val c = if (active) C.accent else C.tertiary.copy(alpha = 0.7f)
    Box(modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(2) { Box(Modifier.size(4.dp).clip(CircleShape).background(c)) }
                }
            }
        }
    }
}
