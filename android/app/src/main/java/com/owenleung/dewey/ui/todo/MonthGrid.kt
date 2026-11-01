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
 * The week strip (or month) and the chevron under it. Pull down anywhere on it for the
 * month, push up for the week; it follows the finger a little and the chevron turns, then
 * springs. Sideways on the month steps a month.
 */
@Composable
fun CalendarPull(open: Boolean, set: (Boolean) -> Unit, swipe: (Int) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val pull = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val px40 = with(density) { 40.dp.toPx() }
    val px60 = with(density) { 60.dp.toPx() }
    val progress = (abs(pull.value) / px40).coerceAtMost(1f)
    val toward = if (open) pull.value < 0 else pull.value > 0
    val accent = C.accent
    val tertiary = C.tertiary
    Column(modifier.fillMaxWidth().pointerInput(open) {
        var dx = 0f; var dy = 0f
        detectDragGestures(
            onDragStart = { dx = 0f; dy = 0f },
            onDragEnd = {
                scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) }
                if (abs(dx) > abs(dy)) { if (open && abs(dx) > px40) swipe(if (dx < 0) 1 else -1) }
                else if (dy > px40 * 0.75f) set(true) else if (dy < -px40 * 0.75f) set(false)
            },
            onDragCancel = { scope.launch { pull.animateTo(0f) } },
        ) { change, amount ->
            change.consume()
            dx += amount.x; dy += amount.y
            if (abs(dy) > abs(dx)) scope.launch { pull.snapTo(dy.coerceIn(-px60, px60)) }
        }
    }) {
        Box(Modifier.graphicsLayer { translationY = pull.value * 0.15f }) { content() }
        // A wide, shallow chevron: a bigger tap target than it looks.
        Box(Modifier.fillMaxWidth().height(28.dp).graphicsLayer { translationY = pull.value * 0.35f }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { set(!open) }
            .semantics { contentDescription = if (open) "Show week" else "Show month" },
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.width(40.dp).height(6.dp).rotate((if (open) 180f else 0f) + if (toward) (if (open) -180f else 180f) * progress else 0f)) {
                val c = if (toward && progress > 0.5f) accent else tertiary
                val w = 3.dp.toPx()
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f); lineTo(size.width / 2, size.height); lineTo(size.width, 0f)
                }
                drawPath(path, c, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
