package com.owenleung.dewey.ui.grades

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import com.owenleung.dewey.store.Goal
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.engine.CourseGrade
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.PlanResult
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.CountBadge
import com.owenleung.dewey.ui.DetailScaffold
import com.owenleung.dewey.ui.Divider
import com.owenleung.dewey.ui.EmptyState
import com.owenleung.dewey.ui.ErrorState
import com.owenleung.dewey.ui.NewTag
import com.owenleung.dewey.ui.PillButton
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.SheetTarget
import com.owenleung.dewey.ui.SkeletonRows
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import kotlinx.coroutines.launch
import java.util.UUID
import com.owenleung.dewey.ui.tour.Coach
import com.owenleung.dewey.ui.tour.coachTarget
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/** A made-up assignment, only in what-if. */
private data class Extra(val id: String = UUID.randomUUID().toString(), val category: String, val title: String = "",
                         val earned: String = "", val possible: String = "100")

/**
 * 2b · Every item of the current period by category, facts first, with the
 * grade-over-time graph. What-if mode (off by default, cleared on leaving)
 * puts a score box on every row, lets the student drop a row or add a made-up
 * one per category, keeps real → what-if pinned at the top, and holds "Plan an
 * upcoming test".
 */
@Composable
fun CourseGradesScreen(sectionId: String) {
    val model = model
    val nav = nav
    val scope = rememberCoroutineScope()
    val course = model.course(sectionId)
    var whatIfOn by remember { mutableStateOf(false) }
    val whatIf = remember { mutableStateMapOf<String, String>() }
    /** "Out of" typed for ungraded rows whose points Schoology doesn't show. */
    val outOf = remember { mutableStateMapOf<String, String>() }
    val dropped = remember { mutableStateListOf<String>() }
    val extras = remember { mutableStateListOf<Extra>() }
    var preview by remember { mutableStateOf<CourseGrade?>(null) }
    var gen by remember { mutableIntStateOf(0) }
    /** Scores that were new when the screen opened; they keep their tag until it closes. */
    val newIds = remember { mutableStateListOf<String>() }
    // Categories opened (by title). Everything starts collapsed, like the extension; remembered per class.
    val expandedKey = "expandedCategories-$sectionId"
    Prefs.revision.intValue
    val expanded = (Prefs.string(expandedKey) ?: "").split("\n").filter { it.isNotEmpty() }.toSet()
    fun setExpanded(s: Set<String>) = Prefs.set(expandedKey, s.sorted().joinToString("\n"))

    val real = model.grades[sectionId]
    val shown = if (whatIfOn) (preview ?: real) else real
    val cutoff = model.cutoff(sectionId)
    val goal = model.goal(sectionId)
    fun possibleOf(it: CourseGrade.Item) = it.possible ?: outOf[it.id]?.toDoubleOrNull()?.takeIf { p -> p > 0 }
    val changeCount = (real?.items ?: emptyList()).count { whatIf[it.id]?.toDoubleOrNull() != null && possibleOf(it) != null } +
        dropped.size + extras.count { it.earned.toDoubleOrNull() != null }

    /** The what-if scores as grademath opts: typed scores, made-up items, drops. */
    fun scenario(): Scenario {
        val w = HashMap<String, Map<String, Double>>()
        for ((id, s) in whatIf) {
            val v = s.toDoubleOrNull() ?: continue
            val it = real?.items?.firstOrNull { it.id == id } ?: continue
            val p = possibleOf(it) ?: continue
            w[id] = mapOf("earned" to v, "possible" to p)
        }
        val extra = extras.mapNotNull { x ->
            val e = x.earned.toDoubleOrNull(); val p = x.possible.toDoubleOrNull()
            if (e == null || p == null || p <= 0) null else mapOf<String, Any>("category" to x.category, "earned" to e, "possible" to p)
        }
        return Scenario(w, extra, dropped.toList())
    }
    fun recompute() {
        val rows = model.gradeRows(sectionId) ?: return
        if (real == null) return
        val sc = scenario()
        val mine = ++gen
        scope.launch {
            val r = runCatching { model.summarize(rows, sc.whatIf, sc.extra, sc.drop) }.getOrNull()
            if (mine == gen) preview = r
        }
    }
    /** Reset clears the what-if scores; "Show real" only hides them. */
    fun clearWhatIf() { whatIf.clear(); outOf.clear(); dropped.clear(); extras.clear(); preview = null; gen++ }

    LaunchedEffect(sectionId) {
        model.loadGrades(sectionId)
        // Opening the class counts as seeing its new scores.
        val fresh = (model.grades[sectionId]?.items ?: emptyList()).map { it.id }.filter(model::isNewGrade)
        newIds += fresh
        model.markGradesSeen(fresh)
    }
    // The tour waits for a typed score.
    LaunchedEffect(changeCount) { if (model.practice && changeCount > 0) Coach.note("whatif") }

    val gradesUrl = "${Engine.ORIGIN}/course/$sectionId/student_grades"
    DetailScaffold(
        title = course?.shortTitle ?: "Grades",
        actions = {
            TextButton(onClick = { whatIfOn = !whatIfOn }) {
                Text(if (whatIfOn) "Show real" else "What-if", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
            }
        },
        topExtra = { if (whatIfOn) Box(Modifier.coachTarget("grades.whatifbar")) { WhatIfBar(real, if (changeCount > 0) preview else null, changeCount) { clearWhatIf() } } },
    ) { pad ->
        ScrollPage(pad, refresh = { model.loadGrades(sectionId, force = true) }) {
            if (!whatIfOn) Summary(sectionId, real, changeCount) { whatIfOn = true }
            if (real != null) GradeHistoryChart(real.history, goal, if (whatIfOn && changeCount > 0) preview?.pct else null)
            if (shown != null) {
                if (shown.categories.size > 1 && !whatIfOn) {
                    val allShut = shown.categories.none { it.title in expanded }
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Categories", style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.secondary, modifier = Modifier.weight(1f))
                        Text(if (allShut) "Expand all" else "Collapse all", style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.secondary,
                            modifier = Modifier.clickable { setExpanded(if (allShut) shown.categories.map { it.title }.toSet() else emptySet()) }.padding(4.dp))
                    }
                }
                for (cat in shown.categories) {
                    val items = (real?.items ?: emptyList()).filter { it.category == cat.title }
                    val open = cat.title in expanded || whatIfOn
                    val realCat = real?.categories?.firstOrNull { it.title == cat.title }
                    val moved = whatIfOn && changeCount > 0 && abs((cat.pct ?: -1.0) - (realCat?.pct ?: -1.0)) >= 0.005
                    Card(Modifier.animateContentSize()) {
                        CategoryHeader(cat, items.size, items.count { it.id in newIds }, open, whatIfOn, moved, realCat?.pct) {
                            setExpanded(if (cat.title in expanded) expanded - cat.title else expanded + cat.title)
                        }
                        if (open) {
                            for (it in items) {
                                Divider()
                                if (whatIfOn) WhatIfRow(it, possibleOf(it), cutoff, whatIf[it.id] ?: "", outOf[it.id] ?: "", it.id in dropped,
                                    onScore = { s -> whatIf[it.id] = s; recompute() },
                                    onOutOf = { s -> outOf[it.id] = s; recompute() },
                                    onDrop = { d -> if (d) { dropped += it.id; whatIf.remove(it.id) } else dropped.remove(it.id); recompute() },
                                    onReset = { dropped.remove(it.id); whatIf.remove(it.id); recompute() })
                                else ItemRow(it, it.id in newIds) { nav.push(Route.GradeDetail(sectionId, it.id)) }
                            }
                            if (whatIfOn) {
                                for (x in extras.filter { it.category == cat.title }) {
                                    Divider()
                                    ExtraRow(x, onChange = { n -> extras[extras.indexOfFirst { it.id == x.id }] = n; recompute() },
                                        onDelete = { extras.removeAll { it.id == x.id }; recompute() })
                                }
                                Divider()
                                Row(Modifier.fillMaxWidth().clickable { extras += Extra(category = cat.title) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.AddCircle, null, tint = C.accent, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add an assignment", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.accent)
                                }
                            }
                        }
                    }
                }
                if (whatIfOn && shown.categories.isNotEmpty()) Planner(sectionId, real, preview, goal, gen, changeCount, ::scenario)
                if (shown.categories.isEmpty()) EmptyState("No grades yet", icon = Icons.Outlined.GridOn)
            } else {
                val e = model.gradeErrors[sectionId]
                if (e != null) ErrorState(e, gradesUrl, retry = { scope.launch { model.loadGrades(sectionId, force = true) } },
                    open = { nav.openSheet(SheetTarget(it)) })
                else SkeletonRows()
            }
        }
    }
}

@Composable
private fun Summary(sectionId: String, real: CourseGrade?, changeCount: Int, startWhatIf: () -> Unit) {
    val model = model
    val course = model.course(sectionId)
    Card(padding = 16.dp) {
        Row {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course?.shortTitle ?: "", style = Theme.title, color = C.text)
                Text(listOfNotNull(course?.periodLabel, real?.period ?: "Current period").filter { it.isNotEmpty() }.joinToString(" · "),
                    style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.secondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(real?.letter ?: "—", style = Theme.display, color = C.text)
                    Spacer(Modifier.width(8.dp))
                    Text(Fmt.pct(real?.pct, 2), style = Theme.title.copy(fontWeight = FontWeight.Medium), color = C.text, modifier = Modifier.padding(bottom = 4.dp))
                }
                if (real != null) Text(if (real.mode == "weighted") "Weighted by category" else "Total points", style = Theme.footnote, color = C.tertiary)
                if (real?.pct != null) {
                    val goal = model.goal(sectionId)
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GoalChip(sectionId, Modifier.coachTarget("grades.goal"))
                        Text(if (real.pct >= goal - 0.005) "At your goal" else String.format(java.util.Locale.US, "%.2f to go", goal - real.pct),
                            style = Theme.caption, color = C.secondary)
                    }
                }
                if (real?.pct != null) PillButton(if (changeCount > 0) "Back to what-if · $changeCount change${if (changeCount == 1) "" else "s"}" else "Try what-if scores", modifier = Modifier.padding(top = 4.dp).coachTarget("grades.whatif"), onClick = startWhatIf)
            }
            model.gradesAsOf[sectionId]?.let { Text(Fmt.ago(it), style = Theme.caption, color = C.tertiary) }
        }
    }
}

/** Pinned under the top bar in what-if mode: real → what-if, always in view while typing. */
@Composable
private fun WhatIfBar(real: CourseGrade?, preview: CourseGrade?, changes: Int, reset: () -> Unit) {
    val r = real?.pct
    val p = preview?.pct
    Column {
        Row(Modifier.fillMaxWidth().background(C.card).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("WHAT-IF", style = Theme.caption.copy(fontWeight = FontWeight.Bold), color = C.accent)
                Text(if (changes == 0) "Type a score on any row" else "$changes change${if (changes == 1) "" else "s"} · preview only",
                    style = Theme.caption, color = C.secondary, maxLines = 1)
            }
            if (p != null) {
                Text("${real?.letter ?: ""} ${Fmt.pct(r)}", style = Theme.subhead, color = C.secondary)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = C.tertiary, modifier = Modifier.size(14.dp).padding(horizontal = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 6.dp)) {
                Text("${if (p == null) real?.letter ?: "" else preview.letter ?: ""} ${Fmt.pct(p ?: r)}", style = Theme.headline, color = C.text)
                if (p != null && r != null) Text(Fmt.signed(p - r), style = Theme.caption.copy(fontWeight = FontWeight.SemiBold), color = if (p < r - 0.005) C.late else C.good)
            }
            if (changes > 0) IconButton(onClick = reset) { Icon(Icons.Outlined.Undo, "Reset all what-if scores", tint = C.accent) }
        }
        Divider()
    }
}

@Composable
private fun CategoryHeader(cat: CourseGrade.Category, count: Int, newCount: Int, open: Boolean, whatIfOn: Boolean, moved: Boolean, realPct: Double?, toggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = !whatIfOn, onClick = toggle).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!whatIfOn) {
            Icon(Icons.Outlined.KeyboardArrowRight, if (open) "Expanded" else "Collapsed", tint = C.secondary,
                modifier = Modifier.size(20.dp).then(if (open) Modifier.graphicsRotate(90f) else Modifier))
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cat.title, style = Theme.headline, color = C.text)
                if (newCount > 0 && !open) { Spacer(Modifier.width(6.dp)); CountBadge("$newCount new") }
            }
            val floor = floorText(cat)
            Text(if (open) floor else "$count item${if (count == 1) "" else "s"}${if (floor.isEmpty()) "" else " · $floor"}",
                style = Theme.footnote, color = if (cat.floorStatus == "out") C.late else C.secondary)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (moved) { Text(Fmt.pct(realPct), style = Theme.caption.copy(textDecoration = TextDecoration.LineThrough), color = C.tertiary); Spacer(Modifier.width(4.dp)) }
                Text(Fmt.pct(cat.pct), style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (moved) C.accent else C.text)
            }
            cat.weight?.let { Text("${Fmt.num(it)}% of grade", style = Theme.caption, color = C.tertiary) }
        }
    }
}

private fun Modifier.graphicsRotate(deg: Float) = this.rotate(deg)

private fun floorText(c: CourseGrade.Category) = when (c.floorStatus) {
    "any" -> "Any score here keeps your A"
    "out" -> "This category alone can't reach the A line"
    "need" -> "A holds down to ${Fmt.pct(c.floor)} here"
    else -> if (c.possible > 0) "" else "No grades yet"
}

@Composable
private fun ItemRow(it: CourseGrade.Item, isNew: Boolean, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(14.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it.title, style = Theme.subhead.copy(fontWeight = FontWeight.Medium), color = C.text, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                if (isNew) { Spacer(Modifier.width(6.dp)); NewTag() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Fmt.wallDate(it.due)?.let { d -> Text(Fmt.short(d), style = Theme.caption, color = C.tertiary) }
                if (it.comment.isNotEmpty()) Marker(Icons.Outlined.ChatBubbleOutline, "Comment")
                if (it.rubric != null) Marker(Icons.Outlined.GridOn, "Rubric")
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(scoreText(it), style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (it.earned == null) C.tertiary else C.text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                it.pct?.let { p -> Text(Fmt.pct(p, 0), style = Theme.caption, color = C.secondary) }
                it.impact?.let { i -> Text(Fmt.signed(i), style = Theme.caption, color = com.owenleung.dewey.ui.impactColor(i)) }
            }
        }
    }
}

@Composable
private fun Marker(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.accent, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, style = Theme.caption, color = C.accent)
    }
}

/** A score box: a small field with the accent outline when changed or focused. */
@Composable
fun ScoreBox(value: String, placeholder: String, changed: Boolean, width: Int = 58, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(Theme.Radius.tag)
    Box(modifier.width(width.dp).background(C.card, shape).border(if (changed) 1.5.dp else 1.dp, if (changed) C.accent else C.border, shape)
        .padding(vertical = 8.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
        if (value.isEmpty()) Text(placeholder, style = Theme.body.copy(textAlign = TextAlign.Center), color = C.tertiary)
        BasicTextField(value, { onChange(it.filter { c -> c.isDigit() || c == '.' }) }, singleLine = true,
            textStyle = Theme.body.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = C.text),
            cursorBrush = SolidColor(C.accent), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth())
    }
}

/**
 * A real item in what-if: a score box (blank = keep the real score), a menu of
 * quick scores, and Leave out. A changed row gets an accent edge and shows real → new.
 */
@Composable
private fun WhatIfRow(it: CourseGrade.Item, possible: Double?, cutoff: Double, score: String, outOf: String, isDropped: Boolean,
                      onScore: (String) -> Unit, onOutOf: (String) -> Unit, onDrop: (Boolean) -> Unit, onReset: () -> Unit) {
    val typed = score.toDoubleOrNull()
    val changed = typed != null || isDropped
    val p = possible ?: 0.0
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().background(if (changed) C.accentSoft else C.card).padding(start = if (changed) 0.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (changed) Box(Modifier.width(3.dp).height(64.dp).background(C.accent))
        Row(Modifier.weight(1f).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(it.title, style = Theme.subhead.copy(fontWeight = FontWeight.Medium, textDecoration = if (isDropped) TextDecoration.LineThrough else null),
                    color = if (isDropped) C.tertiary else C.text, maxLines = 2)
                val sub = when {
                    isDropped -> "Left out of the grade"
                    typed != null -> (if (it.earned != null) scoreText(it) else "Not graded") + " → " + (if (p > 0) Fmt.pct(typed / p * 100, 0) else "—")
                    it.earned != null -> "Real ${scoreText(it)}" + (it.pct?.let { " · ${Fmt.pct(it, 0)}" } ?: "")
                    else -> "Not graded yet" + (Fmt.wallDate(it.due)?.let { " · due ${Fmt.short(it)}" } ?: "")
                }
                Text(sub, style = Theme.footnote, color = if (typed != null && p > 0 && typed / p * 100 >= cutoff) C.good else C.secondary)
            }
            Spacer(Modifier.width(4.dp))
            if (isDropped) PillButton("Count it") { onDrop(false) }
            else {
                ScoreBox(score, it.earned?.let(Fmt::num) ?: "—", typed != null, modifier = Modifier.coachTarget("grades.score.${it.id}"), onChange = onScore)
                Spacer(Modifier.width(4.dp))
                if (it.possible != null) Text("/ ${Fmt.num(p)}", style = Theme.subhead, color = C.secondary)
                else {
                    // Schoology doesn't show the points of ungraded work: the student types them.
                    Text("/", style = Theme.subhead, color = C.secondary)
                    Spacer(Modifier.width(4.dp))
                    ScoreBox(outOf, "pts", false, 50, onChange = onOutOf)
                }
            }
            Box {
                IconButton(onClick = { menu = true }, enabled = p > 0 || isDropped) { Icon(Icons.Outlined.MoreVert, "More what-if options", tint = C.accent) }
                DropdownMenu(menu, { menu = false }) {
                    for (pc in listOf(100.0, 90.0, 80.0, 70.0)) {
                        val v = (p * pc / 100 * 100).roundToLong() / 100.0
                        DropdownMenuItem(text = { Text("${pc.toInt()}%  ·  ${Fmt.num(v)} / ${Fmt.num(p)}") }, onClick = {
                            menu = false; if (isDropped) onDrop(false); onScore(Fmt.num(v))
                        })
                    }
                    HorizontalDivider()
                    if (!isDropped) DropdownMenuItem(text = { Text("Leave out of grade") }, onClick = { menu = false; onDrop(true) })
                    if (changed) DropdownMenuItem(text = { Text("Back to real score") }, onClick = { menu = false; onReset() })
                }
            }
        }
    }
}

/** A made-up assignment: name, score / out of, delete. */
@Composable
private fun ExtraRow(x: Extra, onChange: (Extra) -> Unit, onDelete: () -> Unit) {
    val e = x.earned.toDoubleOrNull(); val p = x.possible.toDoubleOrNull()
    Row(Modifier.fillMaxWidth().background(C.accentSoft), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(64.dp).background(C.accent))
        Row(Modifier.weight(1f).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box {
                    if (x.title.isEmpty()) Text("New assignment", style = Theme.subhead.copy(fontWeight = FontWeight.Medium), color = C.tertiary)
                    BasicTextField(x.title, { onChange(x.copy(title = it)) }, singleLine = true,
                        textStyle = Theme.subhead.copy(fontWeight = FontWeight.Medium, color = C.text), cursorBrush = SolidColor(C.accent))
                }
                Text(if (e != null && (p ?: 0.0) > 0) "Made up · ${Fmt.pct(e / p!! * 100, 0)}" else "Made up · type a score", style = Theme.footnote, color = C.secondary)
            }
            ScoreBox(x.earned, "—", e != null) { onChange(x.copy(earned = it)) }
            Text(" / ", style = Theme.subhead, color = C.secondary)
            ScoreBox(x.possible, "100", false, 50) { onChange(x.copy(possible = it)) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Remove the made-up assignment", tint = C.late) }
        }
    }
}

/** The what-if scores as grademath opts. */
private class Scenario(val whatIf: Map<String, Map<String, Double>>, val extra: List<Map<String, Any>>, val drop: List<String>)

/** The grade to aim for: a small chip, saved per class. */
@Composable
private fun GoalChip(sectionId: String, modifier: Modifier = Modifier) {
    val model = model
    val goal = model.goal(sectionId)
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(C.accentSoft).clickable { open = true }
            .semantics(mergeDescendants = true) { contentDescription = "Goal ${Goal.letter(goal)}, ${Fmt.num(goal)} percent. Changes the grade you're aiming for in this class" }
            .padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Goal ${Goal.letter(goal)}", style = Theme.caption.copy(fontWeight = FontWeight.SemiBold), color = C.accent)
            Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(14.dp), tint = C.accent)
        }
        DropdownMenu(open, { open = false }) {
            for ((l, pct) in Goal.scale) DropdownMenuItem(text = { Text("$l  ·  ${Fmt.num(pct)}%") },
                trailingIcon = { if (abs(pct - goal) < 1e-9) Icon(Icons.Outlined.Check, null) },
                onClick = { open = false; model.setGoal(sectionId, pct) })
        }
    }
}

/** Plan an upcoming test (needFor), with the what-if scores on screen and the goal. */
@Composable
private fun Planner(sectionId: String, real: CourseGrade?, preview: CourseGrade?, goal: Double, gen: Int, changeCount: Int, scenario: () -> Scenario) {
    val model = model
    val scope = rememberCoroutineScope()
    val cats = real?.categories?.map { it.title } ?: emptyList()
    // The student's pick, else the first category about tests, else the first: never "" once the categories load.
    var picked by remember { mutableStateOf("") }
    val category = picked.takeIf { it in cats } ?: cats.firstOrNull { it.contains("test", true) } ?: cats.firstOrNull() ?: ""
    var points by remember { mutableStateOf("100") }
    var plan by remember { mutableStateOf<PlanResult?>(null) }
    var planGen by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    fun run() {
        val sc = scenario(); val pts = points.toDoubleOrNull() ?: 0.0; val mine = ++planGen
        scope.launch {
            val r = model.plan(sectionId, category, pts, goal, sc.whatIf, sc.extra, sc.drop)
            if (mine == planGen) plan = r
        }
    }
    // An answer on screen follows the scores and the goal; none appears until "Work it out".
    LaunchedEffect(gen, goal) { if (plan != null) run() }
    Card(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Plan an upcoming test", style = Theme.headline, color = C.text, modifier = Modifier.weight(1f))
            GoalChip(sectionId)
        }
        Spacer(Modifier.height(10.dp))
        Box {
            Row(Modifier.clickable { menu = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Category: ", style = Theme.subhead, color = C.secondary)
                Text(category, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
            }
            DropdownMenu(menu, { menu = false }) { for (c in cats) DropdownMenuItem(text = { Text(c) }, onClick = { picked = c; menu = false }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Worth ", style = Theme.subhead, color = C.text)
            ScoreBox(points, "100", false, 70) { points = it }
            Text(" points", style = Theme.subhead, color = C.text, modifier = Modifier.weight(1f))
            PillButton("Work it out", filled = true) { run() }
        }
        plan?.let { p ->
            val pts = points.toDoubleOrNull() ?: 0.0
            val name = "${Goal.letter(goal)} (${Fmt.num(goal)}%)"
            val reach = (preview?.pct ?: real?.pct ?: 0.0) < goal
            val text = when (p.status) {
                "any" -> if (reach) "Even a zero gets you to $name." else "Even a zero keeps your $name."
                "out" -> "A full score still leaves you under $name."
                "need" -> { val need = ceil(p.need ?: 0.0); "You need ${Fmt.num(need)} / ${Fmt.num(pts)} (${Fmt.pct(if (pts > 0) need / pts * 100 else null, 0)}) to ${if (reach) "reach" else "keep"} $name." }
                else -> "Pick a category with grades."
            }
            Spacer(Modifier.height(8.dp))
            Text(text, style = Theme.subhead.copy(fontWeight = FontWeight.Medium), color = if (p.status == "out") C.late else C.good)
            if (changeCount > 0) Text("Counts your what-if scores above.", style = Theme.caption, color = C.secondary)
        }
    }
}
