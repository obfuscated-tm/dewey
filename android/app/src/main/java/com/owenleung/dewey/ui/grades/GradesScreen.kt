package com.owenleung.dewey.ui.grades

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.engine.CourseGrade
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.CountBadge
import com.owenleung.dewey.ui.Divider
import com.owenleung.dewey.ui.NewTag
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.ScreenHeader
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.SkeletonRows
import com.owenleung.dewey.ui.impactColor
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import java.util.Locale
import kotlin.math.abs

fun scoreText(i: CourseGrade.Item): String {
    if (i.earned != null && i.possible != null) return "${Fmt.num(i.earned)} / ${Fmt.num(i.possible)}"
    // "—Collected", "Missing": show the word.
    return i.grade.trim().ifEmpty { "—" }
}

/**
 * 2a · New grades (only when there are some), a card per class (letter + %,
 * history, distance to the A line, latest score and its effect), then
 * recently graded work across classes.
 */
@Composable
fun GradesScreen() {
    val model = model
    LaunchedEffect(Unit) { model.twoAtATime(model.classes) { model.loadGrades(it.sectionId) } }
    ScrollPage(refresh = { model.twoAtATime(model.classes) { model.loadGrades(it.sectionId, force = true) } }, spacing = 12.dp) {
        ScreenHeader("Grades")
        val fresh = model.newGrades()
        if (fresh.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("New grades", style = Theme.title, color = C.text)
                Spacer(Modifier.width(8.dp))
                CountBadge("${fresh.size}")
                Spacer(Modifier.weight(1f))
                Text("Mark all seen", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.accent,
                    modifier = Modifier.clickable { model.markAllGradesSeen() }.padding(4.dp))
            }
            GradeList(fresh, showNew = false)
        }
        if (model.classes.isEmpty()) SkeletonRows(4)
        for (c in model.classes) GradeCard(c)
        val rows = model.classes.flatMap { c -> (model.grades[c.sectionId]?.items ?: emptyList()).filter { it.earned != null }.map { c to it } }
            .sortedWith { a, b -> model.gradedOrder(a.second, b.second) }
        if (rows.isNotEmpty()) {
            Text("Recently graded", style = Theme.title, color = C.text, modifier = Modifier.padding(top = 10.dp))
            GradeList(rows.take(8), showNew = true)
        }
    }
}

@Composable
private fun GradeList(rows: List<Pair<Course, CourseGrade.Item>>, showNew: Boolean) {
    val model = model
    val nav = nav
    Card {
        rows.forEachIndexed { i, (c, it) ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().clickable { nav.push(Route.GradeDetail(c.sectionId, it.id)) }.padding(14.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(it.title, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (showNew && model.isNewGrade(it.id)) { Spacer(Modifier.width(6.dp)); NewTag() }
                    }
                    Text(c.shortTitle, style = Theme.footnote, color = C.secondary)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(scoreText(it), style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                    it.impact?.let { d -> Text(Fmt.signed(d), style = Theme.caption, color = impactColor(d)) }
                }
            }
        }
    }
}

@Composable
private fun GradeCard(course: Course) {
    val model = model
    val nav = nav
    val g = model.grades[course.sectionId]
    val cutoff = model.cutoff(course.sectionId)
    val newCount = model.newGradeCount(course.sectionId)
    val d = g?.pct?.let { it - cutoff }
    Card(Modifier.clickable { nav.push(Route.CourseGrades(course.sectionId)) }
        .semantics { contentDescription = listOfNotNull(course.shortTitle, g?.pct?.let { "${g.letter ?: ""}, ${String.format(Locale.US, "%.1f", it)} percent" }).joinToString(", ") }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(course.shortTitle, style = Theme.headline, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        if (newCount > 0) { Spacer(Modifier.width(6.dp)); CountBadge("$newCount new") }
                    }
                    val good = C.good; val late = C.late; val tertiary = C.tertiary
                    Text(buildAnnotatedString {
                        if (d != null) {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = if (d >= 0) good else late)) {
                                append(if (d >= 0) String.format(Locale.US, "+%.1f over A", d) else String.format(Locale.US, "%.1f under A", d))
                            }
                            withStyle(SpanStyle(color = tertiary)) { append(" · ${course.periodLabel}") }
                        } else withStyle(SpanStyle(color = tertiary)) { append(course.periodLabel) }
                    }, style = Theme.footnote, maxLines = 1)
                }
                when {
                    g?.pct != null -> {
                        val vals = g.history.mapNotNull { it.pct }
                        if (vals.size > 1) Sparkline(vals, cutoff)
                        Column(Modifier.widthIn(min = 56.dp), horizontalAlignment = Alignment.End) {
                            Text(g.letter ?: "", style = Theme.title, color = C.text)
                            Text(Fmt.pct(g.pct), style = Theme.footnote, color = C.secondary)
                        }
                    }
                    model.gradeErrors[course.sectionId] != null && g == null -> Text("Couldn't load", style = Theme.footnote, color = C.secondary)
                    g != null -> Text("No grades yet", style = Theme.footnote, color = C.secondary)
                    else -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = C.secondary)
                }
            }
            // The latest score and what it did to the grade.
            val last = g?.takeIf { it.pct != null }?.items?.filter { it.earned != null }?.minWithOrNull { a, b -> model.gradedOrder(a, b) }
            if (last != null) {
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(last.title, style = Theme.footnote, color = C.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(scoreText(last), style = Theme.footnote, color = C.secondary)
                    last.impact?.let { i ->
                        Text(Fmt.signed(i), style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = impactColor(i))
                    }
                }
            }
        }
    }
}

/** The small trend line on a class card, with the A line dashed. */
@Composable
private fun Sparkline(vals: List<Double>, cutoff: Double) {
    val accent = C.accent
    val good = C.good
    val lo = maxOf(0.0, minOf(vals.min(), cutoff) - 3)
    val hi = maxOf(100.0, vals.max())
    Canvas(Modifier.size(96.dp, 28.dp)) {
        fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo))).toFloat()
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        drawLine(good.copy(alpha = 0.5f), Offset(0f, y(cutoff)), Offset(size.width, y(cutoff)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        val p = Path()
        vals.forEachIndexed { i, v ->
            val x = size.width * i / (vals.size - 1)
            if (i == 0) p.moveTo(x, y(v)) else p.lineTo(x, y(v))
        }
        drawPath(p, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
