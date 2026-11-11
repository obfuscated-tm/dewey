package com.owenleung.dewey.ui.todo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberUpdatedState
import com.owenleung.dewey.ui.reduceMotion
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.store.DayFilter
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.ItemState
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The week strip, opened up to a month (To Do › List): a dot per open item on
 * each day (black = test). Tap a day to list it and the day after it
 * (DayFilter.Span); tap it again to show everything. Sideways on it (CalendarPull)
 * steps a month too (ios/App/Screens/ToDo/MonthGrid.swift).
 */
@Composable
fun MonthGrid(items: List<ItemState>, now: Date, selected: DayFilter?, onSelect: (DayFilter?) -> Unit,
              month: YearMonth, forward: Boolean, onStep: (Int) -> Unit) {
    val today = Fmt.localDate(now)
    val week = WeekFields.of(Locale.getDefault())
    Column(Modifier.padding(horizontal = Theme.Space.xs), verticalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)), style = Theme.headline, color = C.text, modifier = Modifier.weight(1f))
            IconButton(onClick = { onStep(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month", tint = C.text) }
            IconButton(onClick = { onStep(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month", tint = C.text) }
        }
        Row(Modifier.fillMaxWidth()) {
            for (k in 0 until 7) {
                val d = week.firstDayOfWeek.plus(k.toLong())
                Text(d.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.US), style = Theme.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = C.tertiary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        AnimatedContent(month, transitionSpec = {
            val dir = if (forward) 1 else -1
            (slideInHorizontally { it * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir } + fadeOut())
        }, label = "month") { m ->
            val lead = Math.floorMod(m.atDay(1).dayOfWeek.value - week.firstDayOfWeek.value, 7)
            val cells: List<LocalDate?> = List(lead) { null } + (1..m.lengthOfMonth()).map { m.atDay(it) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in cells.chunked(7)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (k in 0 until 7) {
                            val day = row.getOrNull(k)
                            Box(Modifier.weight(1f)) { if (day != null) DayCell(day, today, items, selected, onSelect) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, today: LocalDate, items: List<ItemState>, selected: DayFilter?, onSelect: (DayFilter?) -> Unit) {
    val its = items.filter { it.due?.let(Fmt::localDate) == day }.sortedBy { if (it.isExam) 0 else 1 }
    val isToday = day == today
    val past = day.isBefore(today)
    val start = (selected as? DayFilter.Span)?.let { Fmt.localDate(Date(it.start)) }
    val on = start != null && (day == start || day == start.plusDays(1))
    val dayStart = Date.from(day.atStartOfDay(Fmt.zone).toInstant())
    val pick = { onSelect(if (start == day) null else DayFilter.Span(dayStart.time)) }
    val shape = RoundedCornerShape(Theme.Radius.tag)
    val tests = its.count { it.isExam }
    val a11y = listOfNotNull(day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)), if (its.isEmpty()) "nothing due" else "${its.size} due",
        if (tests > 0) (if (tests == 1) "1 test" else "$tests tests") else null).joinToString(", ")
    // A tap, not a button, so a pull or a sideways swipe can start on a day (CalendarPull).
    Column(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(shape)
            .then(if (on) Modifier.background(C.accentSoft) else Modifier)
            .then(if (isToday) Modifier.border(1.5.dp, C.accent, shape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = pick)
            .semantics(mergeDescendants = true) { contentDescription = a11y; this.selected = on; onClick("Shows this day and the next") { pick(); true } },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("${day.dayOfMonth}", style = Theme.subhead.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal),
            color = if (isToday) C.accent else if (past) C.tertiary else C.text)
        Row(Modifier.height(5.dp).padding(top = 0.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val text = C.text; val tertiary = C.tertiary
            for (it in its.take(3)) Box(Modifier.size(5.dp).clip(CircleShape).background(if (it.isExam) text else tertiary.copy(alpha = 0.5f)))
        }
        Spacer(Modifier.height(2.dp))
    }
}

/**
 * The week strip that grows into the month under your finger, like Calendar: its height
 * follows the drag (the week fades out first, then the month in, never both), stops where
 * you stop, and on letting go springs open or shut by where a flick would carry it. Past
 * either end it keeps following, more and more slowly. The chevron bends with it (the V
 * flattens to a line and comes out pointing the other way). Sideways on the month steps a
 * month. `opening` runs as it starts to open (the month it grows into is this one).
 */
@Composable
fun CalendarPull(open: Boolean, opening: () -> Unit, set: (Boolean) -> Unit, swipe: (Int) -> Unit, modifier: Modifier = Modifier,
                 week: @Composable () -> Unit, month: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduceMotion = reduceMotion()
    // 0 = week, 1 = month. `drag` while a finger holds it; `anim` while it settles; else `open`.
    val anim = remember { Animatable(0f) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var moving by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var weekH by remember { mutableIntStateOf(0) }
    var monthH by remember { mutableIntStateOf(0) }
    val active = drag != null || moving
    val progress = drag ?: if (moving) anim.value else if (open) 1f else 0f
    // Until the month is measured, a guess (about five weeks).
    val range = (if (monthH > 0) monthH - weekH else weekH * 4).coerceAtLeast(1).toFloat()
    val t = progress.coerceIn(0f, 1f)
    val accent = C.accent
    val tertiary = C.tertiary
    val swipePx = with(density) { 40.dp.toPx() }

    // The drag outlives recompositions (the month gets measured mid-drag), and so does anything
    // it calls: they read these, never a captured `open` or `set` that may be from before it opened.
    val openNow by rememberUpdatedState(open)
    val rangeNow by rememberUpdatedState(range)
    val setNow by rememberUpdatedState(set)
    val openingNow by rememberUpdatedState(opening)
    val swipeNow by rememberUpdatedState(swipe)
    val reduceMotionNow by rememberUpdatedState(reduceMotion)

    fun rubber(raw: Float): Float {
        val r = rangeNow
        fun damp(over: Float): Float { val pts = over * r; return pts / (1 + pts / with(density) { 50.dp.toPx() }) / r }
        return when { raw > 1f -> 1f + damp(raw - 1f); raw < 0f -> -damp(-raw); else -> raw }
    }
    // Animates from wherever it is now. The drag value stays shown until the animation holds it.
    fun settle(target: Boolean, velocity: Float) {
        val from = drag ?: if (moving) anim.value else if (openNow) 1f else 0f
        moving = true
        job?.cancel()
        job = scope.launch {
            anim.snapTo(from)
            drag = null
            if (!reduceMotionNow) anim.animateTo(if (target) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 260f), initialVelocity = velocity)
            setNow(target)
            moving = false
        }
    }
    fun toggle() { if (!openNow) openingNow(); settle(!openNow, 0f) }

    Column(modifier.fillMaxWidth().pointerInput(Unit) {
        var dx = 0f; var dy = 0f
        var base = 0f
        var vertical: Boolean? = null
        val tracker = VelocityTracker()
        detectDragGestures(
            onDragStart = { dx = 0f; dy = 0f; vertical = null; tracker.resetTracking() },
            onDragEnd = {
                val v = vertical
                vertical = null
                if (v == false) { if (openNow && abs(dx) > swipePx) swipeNow(if (dx < 0) 1 else -1); return@detectDragGestures }
                if (v == null) return@detectDragGestures
                val vy = tracker.calculateVelocity().y
                // Where a flick would carry it, so a quick short flick still opens it.
                val land = base + (dy + vy * 0.2f) / rangeNow
                settle(land > 0.5f, vy / rangeNow)
            },
            onDragCancel = { if (vertical == true) settle(openNow, 0f); vertical = null },
        ) { change, amount ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            dx += amount.x; dy += amount.y
            if (vertical == null) {
                vertical = abs(dy) > abs(dx)
                if (vertical == true) {
                    // Catch it mid-settle where it is.
                    job?.cancel()
                    base = drag ?: if (moving) anim.value else if (openNow) 1f else 0f
                    if (!openNow && !moving) openingNow()
                }
            }
            if (vertical == true) drag = rubber(base + dy / rangeNow)
        }
    }) {
        // Before the first measure, the natural size; after, the height the drag says.
        val h = if (weekH == 0) null else maxOf(weekH * 0.85f, weekH + range * progress)
        Box(Modifier.fillMaxWidth().then(if (h != null) Modifier.height(with(density) { h.toDp() }) else Modifier).clipToBounds()) {
            // At rest only the one shown is drawn, so a tap never lands on the hidden one;
            // while moving both are, the one more than half there on top.
            @Composable fun weekLayer() = Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
                .onSizeChanged { weekH = it.height }.graphicsLayer { alpha = 1f - minOf(1f, t / 0.12f) }) { week() }
            @Composable fun monthLayer() = Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
                .onSizeChanged { monthH = it.height }.graphicsLayer { alpha = ((t - 0.14f) / 0.36f).coerceIn(0f, 1f) }) { month() }
            when {
                !active -> if (open) monthLayer() else weekLayer()
                t >= 0.5f -> { weekLayer(); monthLayer() }
                else -> { monthLayer(); weekLayer() }
            }
        }
        // A wide, shallow chevron: a bigger tap target than it looks. bend 1 points down, -1 up, 0 is flat.
        val bend = 1f - 2f * t
        val lit = active && abs(t - (if (open) 1f else 0f)) > 0.5f
        Box(Modifier.fillMaxWidth().height(28.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { toggle() }
            .semantics { contentDescription = if (open) "Show week" else "Show month" },
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.width(40.dp).height(6.dp)) {
                val hh = size.height / 2 * bend
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height / 2 - hh); lineTo(size.width / 2, size.height / 2 + hh); lineTo(size.width, size.height / 2 - hh)
                }
                drawPath(path, if (lit) accent else tertiary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
