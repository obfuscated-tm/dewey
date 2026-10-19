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
import com.owenleung.dewey.ui.ScreenOptions
import com.owenleung.dewey.ui.SettingsPage
import com.owenleung.dewey.ui.bleed
import com.owenleung.dewey.ui.classColor
import com.owenleung.dewey.ui.tour.coachTarget
import com.owenleung.dewey.store.GradesLook
import com.owenleung.dewey.store.Prefs
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
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
 * 2a · The report card (every class's letter in one row; Settings can hide it),
 * New grades (only when there are some), a compact row per class (trend,
 * distance to the A line, latest score and its effect), then recently graded
 * work across classes. Rows sit on the background with hairlines, not in cards.
 */
@Composable
fun GradesScreen() {
    val model = model
    Prefs.revision.intValue
    LaunchedEffect(Unit) { model.twoAtATime(model.classes) { model.loadGrades(it.sectionId) } }
    val visible = model.gradeClasses
    val reportCard = GradesLook.reportCard
    ScrollPage(refresh = { model.twoAtATime(model.classes) { model.loadGrades(it.sectionId, force = true) } }, spacing = 12.dp) {
        ScreenHeader("Grades", kicker = model.classes.firstNotNullOfOrNull { model.grades[it.sectionId]?.period }) { ScreenOptions(SettingsPage.GRADES) }
        if (model.classes.isEmpty()) SkeletonRows(4)
        else if (reportCard && visible.isNotEmpty()) ReportCardRow(visible, Modifier.padding(top = Theme.Space.xs).bleed(16.dp))
        val fresh = model.newGrades()
        if (fresh.isNotEmpty()) {
            Row(Modifier.padding(top = Theme.Space.s), verticalAlignment = Alignment.CenterVertically) {
                Text("New", style = Theme.headline, color = C.text)
                Spacer(Modifier.width(8.dp))
                CountBadge("${fresh.size}")
                Spacer(Modifier.weight(1f))
                Text("Mark all seen", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.accent,
                    modifier = Modifier.clickable { model.markAllGradesSeen() }.padding(4.dp))
            }
            GradeList(fresh, showNew = false)
        }
        if (model.classes.isNotEmpty()) {
            // A bigger gap under New grades, so the classes never read as more new grades.
            Text("Classes", style = Theme.headline, color = C.text,
                modifier = Modifier.padding(top = if (fresh.isEmpty()) Theme.Space.s else Theme.Space.l).semantics { heading() })
            Column {
                visible.forEachIndexed { i, c ->
                    key(c.sectionId) {
                        if (i > 0) Divider()
                        ClassGradeRow(c, showLetter = !reportCard)
                    }
                }
            }
            val hiddenCount = model.classes.size - visible.size
            if (hiddenCount > 0) Text("$hiddenCount hidden. Show ${if (hiddenCount == 1) "it" else "them"} again in Settings → Grades.",
                style = Theme.footnote, color = C.tertiary)
        }
        val rows = visible.flatMap { c -> (model.grades[c.sectionId]?.items ?: emptyList()).filter { it.earned != null }.map { c to it } }
            .sortedWith { a, b -> model.gradedOrder(a.second, b.second) }
        if (rows.isNotEmpty()) {
            Text("Recently graded", style = Theme.headline, color = C.text, modifier = Modifier.padding(top = Theme.Space.l).semantics { heading() })
            GradeList(rows.take(8), showNew = true)
        }
    }
}

/**
 * The hero of Grades: every class's letter in the serif, in its class colour, with a
 * short name under it. A dot marks a class with new grades. Tap one to open it.
 */
@Composable
private fun ReportCardRow(classes: List<Course>, modifier: Modifier = Modifier) {
    val model = model
    val nav = nav
    val big = LocalDensity.current.fontScale >= 1.6f
    val perRow = if (big) 3 else classes.size.coerceIn(1, 6)
    val border = C.border
    Column(modifier.fillMaxWidth().background(C.card).drawBehind {
        drawLine(border, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        drawLine(border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }.padding(horizontal = Theme.Space.s, vertical = Theme.Space.l), verticalArrangement = Arrangement.spacedBy(Theme.Space.m)) {
        classes.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                for (c in row) {
                    val g = model.grades[c.sectionId]
                    val new = model.newGradeCount(c.sectionId)
                    Column(Modifier.weight(1f).heightIn(min = 44.dp).clickable { nav.push(Route.CourseGrades(c.sectionId)) }
                        .clearAndSetSemantics {
                            contentDescription = listOfNotNull(c.shortTitle, g?.letter ?: "no grade",
                                if (new > 0) (if (new == 1) "1 new grade" else "$new new grades") else null).joinToString(", ")
                            role = Role.Button
                        },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Theme.Space.xs)) {
                        Box {
                            Text(g?.letter ?: "–", style = Theme.display, maxLines = 1,
                                color = if (g?.letter == null) C.tertiary else classColor(c.sectionId))
                            if (new > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = 4.dp).size(8.dp).clip(CircleShape).background(C.accent))
                        }
                        Text(GradesLook.abbreviation(c.shortTitle), style = Theme.caption.copy(fontWeight = FontWeight.SemiBold), color = C.secondary, maxLines = 1)
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GradeList(rows: List<Pair<Course, CourseGrade.Item>>, showNew: Boolean) {
    val model = model
    val nav = nav
    Column {
        rows.forEachIndexed { i, (c, it) ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().clickable { nav.push(Route.GradeDetail(c.sectionId, it.id)) }.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
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

/**
 * One class on Grades: colour tick, name and distance to the A line, the trend,
 * the %, and under it the latest score and what it did to the grade. Press and
 * hold to hide it from Grades.
 */
@Composable
private fun ClassGradeRow(course: Course, showLetter: Boolean) {
    val model = model
    val nav = nav
    var menu by remember { mutableStateOf(false) }
    val g = model.grades[course.sectionId]
    val cutoff = model.cutoff(course.sectionId)
    val newCount = model.newGradeCount(course.sectionId)
    val d = g?.pct?.let { it - cutoff }
    Box {
        Row(Modifier.fillMaxWidth().coachTarget("grades.class.${course.sectionId}")
            .combinedClickable(onClick = { nav.push(Route.CourseGrades(course.sectionId)) }, onLongClick = { menu = true },
                onLongClickLabel = "Hide from Grades")
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(course.shortTitle,
                    if (newCount > 0) (if (newCount == 1) "1 new grade" else "$newCount new grades") else null,
                    g?.pct?.let { "${g.letter ?: ""}, ${String.format(Locale.US, "%.1f", it)} percent" }).joinToString(", ")
            }
            .padding(vertical = Theme.Space.m), horizontalArrangement = Arrangement.spacedBy(Theme.Space.m)) {
            Box(Modifier.padding(top = 2.dp).size(4.dp, 32.dp).clip(RoundedCornerShape(2.dp)).background(classColor(course.sectionId)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Theme.Space.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.Space.m)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(course.shortTitle, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (newCount > 0) { Spacer(Modifier.width(6.dp)); CountBadge("$newCount new") }
                        }
                        val good = C.good; val late = C.late; val tertiary = C.tertiary
                        Text(buildAnnotatedString {
                            if (d != null) {
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = if (d >= 0) good else late)) {
                                    append(if (d >= 0) String.format(Locale.US, "+%.1f over A", d) else String.format(Locale.US, "%.1f under A", abs(d)))
                                }
                                withStyle(SpanStyle(color = tertiary)) { append(" · ${course.periodLabel}") }
                            } else withStyle(SpanStyle(color = tertiary)) { append(course.periodLabel) }
                        }, style = Theme.footnote, maxLines = 1)
                    }
                    when {
                        g?.pct != null -> {
                            val vals = g.history.mapNotNull { it.pct }
                            if (vals.size > 1) Sparkline(vals, cutoff)
                            // Same width on every row, so the trend lines line up down the screen.
                            Row(Modifier.widthIn(min = if (showLetter) 84.dp else 56.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                verticalAlignment = Alignment.Bottom) {
                                if (showLetter) Text(g.letter ?: "", style = Theme.displaySmall, color = C.text)
                                Text(Fmt.pct(g.pct), style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                            }
                        }
                        model.gradeErrors[course.sectionId] != null && g == null -> Text("Couldn't load", style = Theme.footnote, color = C.secondary)
                        g != null -> Text("No grades yet", style = Theme.footnote, color = C.secondary)
                        else -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = C.secondary)
                    }
                }
                // The latest score and what it did to the grade.
                val last = g?.takeIf { it.pct != null }?.items?.filter { it.earned != null }?.minWithOrNull { a, b -> model.gradedOrder(a, b) }
                if (last != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(last.title, style = Theme.footnote, color = C.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(scoreText(last), style = Theme.footnote, color = C.secondary)
                    last.impact?.let { i -> Text(Fmt.signed(i), style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = impactColor(i)) }
                }
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Hide from Grades") }, leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) },
                onClick = { menu = false; GradesLook.toggle(course.sectionId) })
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
