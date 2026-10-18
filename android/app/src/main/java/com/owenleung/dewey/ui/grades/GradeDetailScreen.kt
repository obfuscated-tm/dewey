package com.owenleung.dewey.ui.grades

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.owenleung.dewey.engine.Assignment
import com.owenleung.dewey.engine.CourseGrade
import com.owenleung.dewey.engine.DropboxInfo
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.EngineError
import com.owenleung.dewey.engine.PlanResult
import com.owenleung.dewey.engine.Rubric
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.DetailScaffold
import com.owenleung.dewey.ui.Divider
import com.owenleung.dewey.ui.LabeledCard
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.SectionLabel
import com.owenleung.dewey.ui.SheetTarget
import com.owenleung.dewey.ui.SkeletonRows
import com.owenleung.dewey.ui.WideButton
import com.owenleung.dewey.ui.classes.AttachmentRow
import com.owenleung.dewey.ui.classes.OpenTarget
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/** 2c · Graded work: score, the teacher's comment, the rubric, dropbox comments, and the effect on the course grade. */
@Composable
fun GradeDetailScreen(sectionId: String, itemId: String) {
    val model = model
    val nav = nav
    val ctx = LocalContext.current
    var rubric by remember { mutableStateOf<Rubric?>(null) }
    var rubricError by remember { mutableStateOf<EngineError?>(null) }
    var dropbox by remember { mutableStateOf<DropboxInfo?>(null) }
    var assignment by remember { mutableStateOf<Assignment?>(null) }
    val item = model.grades[sectionId]?.items?.firstOrNull { it.id == itemId }

    LaunchedEffect(itemId) {
        if (model.grades[sectionId]?.items?.none { it.id == itemId } != false) model.loadGrades(sectionId)
        model.markGradesSeen(listOf(itemId))
        val it = model.grades[sectionId]?.items?.firstOrNull { it.id == itemId } ?: return@LaunchedEffect
        val r = it.rubric
        if (r?.rubricId != null && r.enrollmentId != null) {
            try {
                rubric = model.engine.call("rubric", mapOf("rubricId" to r.rubricId, "enrollmentId" to r.enrollmentId, "itemId" to itemId))
            } catch (e: EngineError) { rubricError = e }
        }
        runCatching { model.engine.call<Assignment>("assignment", mapOf("id" to itemId)) }.getOrNull()?.let { a ->
            assignment = a
            if (a.dropboxUrl != null && a.submission?.state == "submitted") {
                dropbox = runCatching { model.engine.call<DropboxInfo>("dropbox", mapOf("url" to a.dropboxUrl)) }.getOrNull()
            }
        }
    }

    DetailScaffold(item?.title ?: "Graded work") { pad ->
        ScrollPage(pad) {
            if (item == null) { SkeletonRows(3); return@ScrollPage }
            ScoreCard(item)
            if (item.comment.isNotEmpty()) LabeledCard("TEACHER'S COMMENT") { SelectionContainer { Text(item.comment, style = Theme.subhead, color = C.text) } }
            if (item.rubric != null) RubricCard(rubric, rubricError)
            val comments = dropbox?.teacherComments ?: emptyList()
            if (comments.isNotEmpty()) LabeledCard("COMMENTS ON YOUR SUBMISSION") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in comments) {
                        if (!c.author.isNullOrEmpty()) Text(c.author, style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.secondary)
                        SelectionContainer { Text(c.text, style = Theme.subhead, color = C.text) }
                    }
                }
            }
            assignment?.attachments?.takeIf { it.isNotEmpty() }?.let { atts ->
                LabeledCard("ATTACHED") {
                    for (att in atts) AttachmentRow(att.title, att.url) { t ->
                        when (t) { is OpenTarget.FileOpen -> nav.file = t.f; is OpenTarget.Web -> nav.openLink(ctx, t.url) }
                    }
                }
            }
            if (item.possible != null) WideButton("Try a different score", icon = Icons.Outlined.Tune, filled = false) { nav.whatIf = itemId to sectionId }
            TextButton(onClick = { nav.openSheet(SheetTarget("${Engine.ORIGIN}/assignment/$itemId", itemId)) }) {
                Icon(Icons.Outlined.Language, null, tint = C.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open in Schoology", style = Theme.subhead.copy(fontWeight = FontWeight.Medium), color = C.accent)
            }
        }
    }
}

@Composable
private fun ScoreCard(it: CourseGrade.Item) {
    Card(padding = 16.dp) {
        SectionLabel(it.category.uppercase())
        Row(verticalAlignment = Alignment.Bottom) {
            Text(scoreText(it), style = Theme.largeTitle, color = C.text)
            it.pct?.let { p -> Spacer(Modifier.width(10.dp)); Text(Fmt.pct(p), style = Theme.body, color = C.secondary, modifier = Modifier.padding(bottom = 6.dp)) }
        }
        it.impact?.let { i ->
            Text(if (abs(i) < 0.005) "Didn't move your course grade" else "Moved your course grade ${Fmt.signed(i)}",
                style = Theme.subhead.copy(fontWeight = FontWeight.Medium), color = if (abs(i) < 0.005) C.secondary else if (i < 0) C.late else C.good)
        }
        Fmt.wallDate(it.due)?.let { d -> Text("Due ${Fmt.dayTime(d)}", style = Theme.footnote, color = C.secondary) }
    }
}

@Composable
private fun RubricCard(r: Rubric?, error: EngineError?) {
    when {
        r != null -> Card {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.title.ifEmpty { "Rubric" }, style = Theme.headline, color = C.text, modifier = Modifier.weight(1f))
                Text("${Fmt.num(r.earned)} / ${Fmt.num(r.total)}", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
            }
            for (c in r.criteria) {
                Divider()
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(c.title, style = Theme.subhead.copy(fontWeight = FontWeight.Medium), color = C.text, modifier = Modifier.weight(1f))
                        Text("${Fmt.num(c.earned)} / ${Fmt.num(c.max)}", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold),
                            color = if (c.earned != null && c.earned == c.max) C.good else C.text)
                    }
                    if (!c.level.isNullOrEmpty()) Text(c.level, style = Theme.footnote, color = C.secondary)
                    if (c.comment.isNotEmpty()) Text(c.comment, style = Theme.footnote, color = C.accent)
                }
            }
        }
        error != null -> LabeledCard("RUBRIC") { Text(error.text, style = Theme.subhead, color = C.secondary) }
        else -> LabeledCard("RUBRIC") { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = C.accent) }
    }
}

/**
 * "What if I get __ / N?" for one item: type a score, tap a quick one, or
 * slide; the course grade shows now → with this score, and an ungraded item
 * also shows the score that keeps the A.
 */
@Composable
fun WhatIfSheet(itemId: String, sectionId: String?, onClose: () -> Unit) {
    val model = model
    val scope = rememberCoroutineScope()
    val item = sectionId?.let { s -> model.grades[s]?.items?.firstOrNull { it.id == itemId } }
    val cutoff = sectionId?.let(model::cutoff) ?: 93.0
    var score by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CourseGrade?>(null) }
    var needForA by remember { mutableStateOf<PlanResult?>(null) }
    var gen by remember { mutableIntStateOf(0) }

    LaunchedEffect(itemId) {
        // An ungraded item counts like a new one in its category, so planTest gives the score that keeps the A.
        if (item != null && item.earned == null && item.possible != null && sectionId != null) needForA = model.plan(sectionId, item.category, item.possible, cutoff)
    }
    fun recompute(s: String) {
        score = s
        val e = s.toDoubleOrNull()
        val rows = sectionId?.let(model::gradeRows)
        if (e == null || rows == null || item?.possible == null) { result = null; return }
        val mine = ++gen
        scope.launch {
            val r = runCatching { model.summarize(rows, mapOf(itemId to mapOf("earned" to e, "possible" to item.possible))) }.getOrNull()
            if (mine == gen) result = r
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        Column(Modifier.fillMaxSize().background(C.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("What if", style = Theme.headline, color = C.text, modifier = Modifier.weight(1f).padding(start = 8.dp))
                TextButton(onClick = onClose) { Text("Done", style = Theme.headline, color = C.accent) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                val possible = item?.possible
                if (item == null || sectionId == null || possible == null || possible <= 0) {
                    Text("Load this class's grades first.", style = Theme.subhead, color = C.secondary)
                    return@Column
                }
                val real = model.grades[sectionId]
                val typed = score.toDoubleOrNull()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, style = Theme.headline, color = C.text)
                    Text(if (item.earned != null) "Real score ${scoreText(item)}${item.pct?.let { " · " + Fmt.pct(it, 0) } ?: ""}" else "Not graded yet",
                        style = Theme.footnote, color = C.secondary)
                }
                Card(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreBox(score, item.earned?.let(Fmt::num) ?: "score", typed != null, 110) { recompute(it) }
                        Text("  / ${Fmt.num(possible)}", style = Theme.title.copy(fontWeight = FontWeight.Normal), color = C.secondary, modifier = Modifier.weight(1f))
                        if (typed != null) Text(Fmt.pct(typed / possible * 100, 0), style = Theme.title, color = C.accent)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (pc in listOf(100.0, 90.0, 80.0, 70.0, 60.0)) {
                            val v = (possible * pc / 100 * 100).roundToLong() / 100.0
                            val on = typed == v
                            Box(Modifier.weight(1f).heightIn(min = 32.dp).clip(CircleShape).background(if (on) C.accent else C.accentSoft)
                                .clickable { recompute(Fmt.num(v)) }, contentAlignment = Alignment.Center) {
                                Text("${pc.toInt()}%", style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = if (on) C.onAccent else C.accent)
                            }
                        }
                    }
                    Slider(
                        value = minOf(typed ?: item.earned ?: 0.0, possible).toFloat(),
                        onValueChange = { recompute(Fmt.num(Math.round(it * 2) / 2.0)) },
                        valueRange = 0f..possible.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent),
                    )
                }
                Card(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradeColumn("NOW", real?.letter, real?.pct, C.secondary, null, Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = C.tertiary, modifier = Modifier.padding(horizontal = 8.dp))
                        val r = result
                        if (r != null && typed != null) GradeColumn("WITH THIS SCORE", r.letter, r.pct, C.accent, r.pct?.let { p -> real?.pct?.let { p - it } }, Modifier.weight(1f))
                        else GradeColumn("WITH THIS SCORE", null, null, C.tertiary, null, Modifier.weight(1f))
                    }
                }
                if (item.earned == null) needForA?.let { n ->
                    val text = when (n.status) {
                        "any" -> "Any score here keeps you at ${Fmt.num(cutoff)}% or above."
                        "out" -> "Even ${Fmt.num(possible)} / ${Fmt.num(possible)} leaves you under ${Fmt.num(cutoff)}%."
                        "need" -> { val need = minOf(ceil(n.need ?: 0.0), possible); "You need ${Fmt.num(need)} / ${Fmt.num(possible)} (${Fmt.pct(need / possible * 100, 0)}) to stay at ${Fmt.num(cutoff)}%." }
                        else -> ""
                    }
                    if (text.isNotEmpty()) Card(padding = 16.dp) { Text(text, style = Theme.subhead.copy(fontWeight = FontWeight.Medium), color = if (n.status == "out") C.late else C.good) }
                }
                Text("Only a preview. Nothing changes on Schoology.", style = Theme.footnote, color = C.tertiary)
            }
        }
    }
}

@Composable
private fun GradeColumn(label: String, letter: String?, pct: Double?, color: Color, delta: Double?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = Theme.caption.copy(fontWeight = FontWeight.Bold), color = color)
        Text(letter ?: "—", style = Theme.largeTitle, color = if (pct == null) C.tertiary else C.text)
        Row {
            Text(Fmt.pct(pct, 2), style = Theme.subhead, color = C.secondary)
            if (delta != null && abs(delta) >= 0.005) {
                Spacer(Modifier.width(6.dp))
                Text(Fmt.signed(delta), style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = if (delta < 0) C.late else C.good)
            }
        }
    }
}
