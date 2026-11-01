package com.owenleung.dewey.ui.grades

import com.owenleung.dewey.store.Goal
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.engine.CourseGrade
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import java.util.Date
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The class grade over the period, one point per day something was graded.
 * Drag a finger across it to read any day: its %, the change, and what was
 * graded. In what-if mode a dashed line runs from the last real point to the
 * what-if grade.
 */
@Composable
fun GradeHistoryChart(history: List<CourseGrade.Point>, cutoff: Double, whatIfPct: Double?, cutoffLabel: String = Goal.letter(cutoff)) {
    data class Pt(val date: Date, val pct: Double, val items: List<String>, val undated: Boolean = false)
    val pts = remember(history) {
        val out = history.mapNotNull { p -> val d = Fmt.wallDate(p.day) ?: return@mapNotNull null; p.pct?.let { Pt(d, it, p.items) } }.toMutableList()
        // History ends with work that has no due date: it goes at today, so the line ends on the real grade.
        history.lastOrNull { it.day == null }?.let { u ->
            u.pct?.let { v ->
                val today = Fmt.startOfDay(Date())
                val at = maxOf(today.time, (out.lastOrNull()?.date ?: today).time + 86_400_000)
                out += Pt(Date(at), v, u.items, undated = true)
            }
        }
        out
    }
    if (pts.size < 2) return
    var selected by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    val accent = C.accent; val good = C.good; val divider = C.divider; val tertiary = C.tertiary; val card = C.card; val secondary = C.secondary
    val measurer: TextMeasurer = rememberTextMeasurer()

    val whatIfDate = whatIfPct?.let { Date(maxOf(Fmt.startOfDay(Date()).time, pts.last().date.time + 3 * 86_400_000L)) }
    val vals = pts.map { it.pct } + listOfNotNull(whatIfPct)
    val lo = maxOf(0.0, floor(minOf(vals.min(), cutoff) - 2))
    val hi = maxOf(100.0, ceil(vals.max()))
    val t0 = pts.first().date.time
    val t1 = maxOf(pts.last().date.time, whatIfDate?.time ?: 0)

    Card(padding = 16.dp) {
        // The line above the graph: the picked day, or the latest.
        val i = selected ?: pts.lastIndex
        val p = pts[i]
        val change = if (i > 0) p.pct - pts[i - 1].pct else null
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Text(if (selected == null) "Grade over time" else if (p.undated) "No due date" else Fmt.header(p.date),
                    style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.secondary, modifier = Modifier.weight(1f))
                if (selected == null && whatIfPct != null) Text("What-if ${Fmt.pct(whatIfPct, 2)}", style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.accent)
            }
            Row {
                Text(Fmt.pct(p.pct, 2), style = Theme.title, color = C.text)
                if (change != null && abs(change) >= 0.005) {
                    Spacer(Modifier.width(8.dp))
                    Text(Fmt.signed(change), style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (change < 0) C.late else C.good)
                }
            }
            Text(p.items.joinToString(", "), style = Theme.footnote, color = C.secondary, maxLines = 2, minLines = 2)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(170.dp)
                .semantics { contentDescription = "Grade over time: " + pts.joinToString(", ") { "${Fmt.short(it.date)} ${Fmt.pct(it.pct)}" } }
                .pointerInput(pts) {
                    fun pick(x: Float) {
                        val w = size.width - 40.dp.toPx()
                        val t = t0 + (x / w).coerceIn(0f, 1f) * (t1 - t0)
                        val n = pts.indices.minBy { abs(pts[it].date.time - t) }
                        if (n != selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selected = n
                    }
                    detectDragGestures(onDragStart = { pick(it.x) }, onDragEnd = { selected = null }, onDragCancel = { selected = null }) { c, _ -> pick(c.position.x) }
                }
                .pointerInput(pts) { detectTapGestures(onPress = { tryAwaitRelease(); selected = null }) },
        ) {
            val right = 40.dp.toPx()
            val w = size.width - right
            val h = size.height - 18.dp.toPx()
            fun x(d: Long) = if (t1 == t0) 0f else (w * (d - t0) / (t1 - t0).toFloat())
            fun y(v: Double) = (h * (1 - (v - lo) / (hi - lo))).toFloat()
            // Grid and labels: 4 lines on the right, dates along the bottom.
            for (k in 0..3) {
                val v = lo + (hi - lo) * k / 3
                drawLine(divider, Offset(0f, y(v)), Offset(w, y(v)), 1f)
                drawText(measurer, "${Fmt.num(Math.round(v).toDouble())}%", Offset(w + 4.dp.toPx(), y(v) - 8.dp.toPx()), Theme.caption.copy(color = tertiary))
            }
            for (k in 0..3) {
                val t = t0 + (t1 - t0) * k / 3
                val label = Fmt.short(Date(t))
                drawText(measurer, label, Offset((x(t) - if (k == 3) 36.dp.toPx() else 0f).coerceAtLeast(0f), h + 2.dp.toPx()), Theme.caption.copy(color = tertiary))
            }
            // The A line.
            drawLine(good.copy(alpha = 0.6f), Offset(0f, y(cutoff)), Offset(w, y(cutoff)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            drawText(measurer, "$cutoffLabel ${Fmt.num(cutoff)}%", Offset(2f, y(cutoff) - 16.dp.toPx()), Theme.caption.copy(color = good, fontWeight = FontWeight.SemiBold))
            // Area and line.
            val line = Path()
            pts.forEachIndexed { n, p -> if (n == 0) line.moveTo(x(p.date.time), y(p.pct)) else line.lineTo(x(p.date.time), y(p.pct)) }
            val area = Path().apply { addPath(line); lineTo(x(pts.last().date.time), h); lineTo(x(pts.first().date.time), h); close() }
            drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.02f)), 0f, h))
            drawPath(line, accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            // What-if: dashed from the last real point.
            if (whatIfPct != null && whatIfDate != null) {
                val a = Offset(x(pts.last().date.time), y(pts.last().pct))
                val b = Offset(x(whatIfDate.time), y(whatIfPct))
                drawLine(accent.copy(alpha = 0.7f), a, b, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                drawCircle(card, 5.dp.toPx(), b)
                drawCircle(accent, 3.5.dp.toPx(), b)
            }
            val sel = selected
            if (sel != null) {
                val sp = pts[sel]
                val sx = x(sp.date.time)
                drawLine(secondary.copy(alpha = 0.5f), Offset(sx, 0f), Offset(sx, h), 1.dp.toPx())
                drawCircle(card, 7.dp.toPx(), Offset(sx, y(sp.pct)))
                drawCircle(accent, 4.5.dp.toPx(), Offset(sx, y(sp.pct)))
            } else {
                drawCircle(accent, 4.dp.toPx(), Offset(x(pts.last().date.time), y(pts.last().pct)))
            }
        }
        Text(if (selected == null) "Drag across the graph to see any day." else " ", style = Theme.caption, color = C.tertiary)
    }
}
