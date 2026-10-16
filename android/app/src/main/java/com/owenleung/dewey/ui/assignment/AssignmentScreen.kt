package com.owenleung.dewey.ui.assignment

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.engine.Assignment
import com.owenleung.dewey.engine.CourseGrade
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.EngineError
import com.owenleung.dewey.engine.QuizInfo
import com.owenleung.dewey.engine.SubmissionStatus
import com.owenleung.dewey.neoplan.NeoPlanClient
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.ItemType
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.DetailScaffold
import com.owenleung.dewey.ui.Divider
import com.owenleung.dewey.ui.ErrorState
import com.owenleung.dewey.ui.LabeledCard
import com.owenleung.dewey.ui.MarkdownText
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.SectionLabel
import com.owenleung.dewey.ui.SheetTarget
import com.owenleung.dewey.ui.SkeletonRows
import com.owenleung.dewey.ui.TypeTag
import com.owenleung.dewey.ui.WideButton
import com.owenleung.dewey.ui.classes.AttachmentRow
import com.owenleung.dewey.ui.classes.OpenTarget
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.ui.todo.ProgressChips
import com.owenleung.dewey.widget.WidgetBridge
import java.util.Date
import kotlin.math.abs

/** The Submission card is exactly one of five. */
private sealed class Kind {
    data class Dropbox(val done: Boolean) : Kind()
    data class Outside(val url: String?) : Kind()
    data object Quiz : Kind()
    data object Google : Kind()
    data object Unknown : Kind()
}

/** An outside submit page linked from the instructions (AP CS's lab site). */
fun labLink(a: Assignment): String? {
    val md = a.instructionsMd ?: return null
    for (m in Regex("""\]\((https?://[^)\s]+)\)""").findAll(md)) {
        val u = m.groupValues[1]
        val h = Uri.parse(u).host ?: continue
        if (h.endsWith("schoology.com") || h.endsWith("google.com") || h.endsWith("youtube.com")) continue
        return u
    }
    return null
}

/** 1b · One assignment: the Submission card, your status, grade, instructions, type, what-if, and the main action. */
@Composable
fun AssignmentScreen(id: String) {
    val model = model
    val nav = nav
    val ctx = LocalContext.current
    var a by remember { mutableStateOf<Assignment?>(null) }
    var status by remember { mutableStateOf<SubmissionStatus?>(null) }
    var checkedAt by remember { mutableStateOf<Date?>(null) }
    var error by remember { mutableStateOf<EngineError?>(null) }
    val it = model.item(id)
    val pageUrl = "${Engine.ORIGIN}/assignment/$id"
    val gradeItem: CourseGrade.Item? = it?.sectionId?.let { s -> model.grades[s]?.items?.firstOrNull { g -> g.id == id } }

    suspend fun load() {
        try {
            val got: Assignment = model.engine.call("assignment", mapOf("id" to id))
            a = got
            error = null
            model.item(id)?.let { item ->
                // Save only real changes: a save redraws the To Do list under this screen.
                val ext = got.type == "external_tool"
                val lab = labLink(got)
                var changed = item.external != ext || item.labUrl != lab
                item.external = ext
                item.labUrl = lab
                val s = got.submission
                if (s != null && (s.state != item.statusState || s.late != item.statusLate)) {
                    item.statusState = s.state
                    item.statusLate = s.late
                    changed = true
                }
                if (s != null) item.statusCheckedAt = System.currentTimeMillis()
                if (changed) model.save()
            }
            status = got.submission
            checkedAt = Date()
        } catch (e: EngineError) {
            error = e
        } catch (e: Exception) {
            error = EngineError.Js(e.message ?: "$e")
        }
        model.item(id)?.sectionId?.let { model.loadGrades(it) }
    }

    LaunchedEffect(id) { load() }
    // After the sheet re-checks the dropbox, show what the row now says.
    LaunchedEffect(model.revision.intValue) {
        it?.statusState?.let { s -> status = SubmissionStatus(s, it.statusLate); checkedAt = it.statusCheckedAt?.let(::Date) ?: checkedAt }
    }

    val kind = a?.let { asg ->
        when {
            asg.type == "quiz" || it?.isQuiz == true -> Kind.Quiz
            asg.type == "external_tool" -> Kind.Google
            else -> when (status?.state ?: asg.submission?.state) {
                "submitted" -> Kind.Dropbox(true)
                "not_submitted" -> Kind.Dropbox(false)
                "no_dropbox" -> Kind.Outside(labLink(asg))
                else -> Kind.Unknown
            }
        }
    }
    // The main action for each kind.
    val main: Pair<String, () -> Unit>? = when (kind) {
        is Kind.Dropbox -> "Submit on Schoology" to { nav.openSheet(SheetTarget(pageUrl, id)) }
        is Kind.Outside -> kind.url?.let { u -> "Turn in on ${Uri.parse(u).host ?: "lab site"}" to { nav.openSheet(SheetTarget(u)) } }
        Kind.Quiz -> quizAction(a?.quiz) to { nav.openSheet(SheetTarget(Engine.ORIGIN + (it?.url ?: "/assignment/$id"), id)) }
        Kind.Google -> "Open in Schoology" to { nav.openSheet(SheetTarget(pageUrl, id, google = true)) }
        else -> null
    }

    DetailScaffold(
        title = "",
        actions = { IconButton(onClick = { nav.openSheet(SheetTarget(pageUrl, id)) }) { Icon(Icons.Outlined.Language, "Open in Schoology") } },
        bottomBar = {
            if (main != null) WideButton(main.first, external = true, modifier = Modifier.background(C.background).navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp), onClick = main.second)
        },
    ) { pad ->
        ScrollPage(pad, refresh = { load() }, spacing = 16.dp) {
            // Header.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                model.course(it?.sectionId)?.let { c -> Text(c.shortTitle, style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.accent) }
                Text(a?.title ?: it?.title ?: "Assignment", style = Theme.title, color = C.text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (it != null) TypeTag(it.type)
                    val due = Fmt.date(a?.due) ?: it?.due
                    Text(due?.let { "Due " + Fmt.dayTime(it) } ?: "No due date", style = Theme.footnote, color = C.secondary)
                    gradeItem?.possible?.let { p -> Text("· ${Fmt.num(p)} pts", style = Theme.footnote, color = C.secondary) }
                }
            }
            val asg = a
            if (asg != null && kind != null) {
                SubmissionCard(kind, asg, status, checkedAt)
                if (it != null) ProgressCard(it.schoologyId)
                gradeItem?.let { g -> GradeCard(g, it?.sectionId ?: "") }
                Instructions(asg)
                if (it != null) TypeSwitch(it.schoologyId)
                if (gradeItem != null && gradeItem.earned == null && gradeItem.possible != null) {
                    Text("What if I get __ / ${Fmt.num(gradeItem.possible)}?", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.accent,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .border(1.2.dp, C.accent, RoundedCornerShape(Theme.Radius.card))
                            .clickable { nav.whatIf = id to it?.sectionId }.padding(14.dp))
                }
            } else if (error != null) {
                ErrorState(error!!, pageUrl, retry = null, open = { u -> nav.openSheet(SheetTarget(u, id)) })
            } else {
                SkeletonRows(3)
            }
            Spacer(Modifier.size(60.dp))
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, color: Color = C.text) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Theme.body.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

@Composable
private fun SubmissionCard(kind: Kind, a: Assignment, status: SubmissionStatus?, checkedAt: Date?) {
    LabeledCard("SUBMISSION") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (kind) {
                is Kind.Dropbox -> {
                    StatusLine(if (kind.done) Icons.Outlined.CheckCircle else Icons.Outlined.HighlightOff,
                        if (kind.done) "Submitted on Schoology" else "Not submitted on Schoology", if (kind.done) C.good else C.late)
                    if (kind.done && status?.late == true) Text("Late", style = Theme.footnote, color = C.late)
                    checkedAt?.let { Text("Checked ${Fmt.ago(it)}", style = Theme.footnote, color = C.secondary) }
                }
                is Kind.Outside -> {
                    StatusLine(Icons.Outlined.OpenInNew, "Handed in outside Schoology")
                    Text(kind.url?.let { "This class takes it on its own site (${Uri.parse(it).host}). Schoology can't see it." }
                        ?: "No Schoology dropbox. Hand it in the way your teacher asked, then mark it turned in.", style = Theme.footnote, color = C.secondary)
                }
                Kind.Quiz -> a.quiz?.let { QuizFacts(it) } ?: run {
                    StatusLine(Icons.Outlined.Assignment, "Test / quiz on Schoology")
                    Text("It only opens when you tap Take on Schoology.", style = Theme.footnote, color = C.secondary)
                }
                Kind.Google -> {
                    StatusLine(Icons.Outlined.Description, "Google Assignment")
                    Text("Turned in inside Schoology's Google frame. Schoology never tells the app whether you did, so check it there.",
                        style = Theme.footnote, color = C.secondary)
                }
                Kind.Unknown -> StatusLine(Icons.Outlined.HourglassEmpty, "Checking Schoology…", C.secondary)
            }
        }
    }
}

// ── A newer quiz: read from its landing page, never from the quiz itself ──

@Composable
private fun QuizFacts(q: QuizInfo) {
    when (q.state) {
        "in_progress" -> {
            StatusLine(Icons.Outlined.Schedule, "Attempt in progress", C.late)
            Text("Started on Schoology but not turned in. Resume it there.", style = Theme.footnote, color = C.secondary)
        }
        "submitted" -> StatusLine(Icons.Outlined.CheckCircle, "Submitted on Schoology", C.good)
        else -> {
            StatusLine(Icons.Outlined.Assignment, "Not started")
            Text("Test / quiz on Schoology. It only opens when you tap Take on Schoology.", style = Theme.footnote, color = C.secondary)
        }
    }
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        q.score?.let { s -> QuizRow("Score", Fmt.num(s) + (q.maxPoints?.let { " / ${Fmt.num(it)}" } ?: "") + (q.letter?.let { " · $it" } ?: "")) }
        QuizRow("Attempts", q.attemptsAllowed?.let { "${q.attemptsTaken} of $it" } ?: "${q.attemptsTaken}, no limit")
        q.timeLimitMin?.let { QuizRow("Time limit", "${Fmt.num(it)} min") }
        Fmt.date(q.opensAt)?.let { QuizRow("Opens", Fmt.dayTime(it)) }
        Fmt.date(q.closesAt)?.let { QuizRow("Closes", Fmt.dayTime(it)) }
        if (q.overdue == true) Text("Overdue", style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.late)
    }
    if (q.attempts.isNotEmpty()) {
        Divider()
        for (t in q.attempts) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (t.finished) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule, null, tint = if (t.finished) C.good else C.late, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Attempt ${t.n}", style = Theme.subhead.copy(fontWeight = FontWeight.Medium), color = C.text, modifier = Modifier.weight(1f))
                val parts = listOfNotNull(Fmt.date(t.at)?.let(Fmt::dayTime), t.minutes?.let { "${Fmt.num(it)} min" }, if (!t.finished) "in progress" else null)
                Text(parts.joinToString(" · "), style = Theme.footnote, color = C.secondary)
            }
        }
        if (q.attempts.any { it.canView }) Text("Your answers are on Schoology: tap Review on Schoology, then View on the attempt.",
            style = Theme.footnote, color = C.secondary)
    }
}

@Composable
private fun QuizRow(label: String, value: String) {
    Row {
        Text(label, style = Theme.subhead, color = C.secondary, modifier = Modifier.weight(1f))
        Text(value, style = Theme.subhead, color = C.text)
    }
}

/** Opening the quiz is always the student's tap, in the Schoology sheet. */
private fun quizAction(q: QuizInfo?) = when (q?.state) {
    "in_progress" -> "Resume on Schoology"
    "submitted" -> if (q.attempts.any { it.canView }) "Review on Schoology" else "Open on Schoology"
    else -> "Take on Schoology"
}

// ── Your status: To do / Done / Turned in, and the countdown ─────────────

@Composable
private fun ProgressCard(id: String) {
    val model = model
    val it = model.item(id) ?: return
    var counting by remember { mutableStateOf(WidgetBridge.isCounting(id)) }
    var countdownError by remember { mutableStateOf<String?>(null) }
    LabeledCard("YOUR STATUS") {
        ProgressChips(model.progress(it), it.isExam) { p -> model.setProgress(it, p) }
        Spacer(Modifier.size(8.dp))
        Text(if (it.submitted) "Schoology also shows it submitted." else "Only you see this. Turned in means you handed it in, on paper or another site.",
            style = Theme.footnote, color = C.secondary)
        val window = WidgetBridge.countdownWindow(it.due)
        if (counting || (window != WidgetBridge.Window.None && !model.isFinished(it))) {
            Spacer(Modifier.size(8.dp))
            Divider()
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Countdown notification", style = Theme.subhead.copy(fontWeight = FontWeight.Medium), color = C.text)
                    Text(if (window is WidgetBridge.Window.Opens && !counting) "Available from ${Fmt.dayTime(window.at)}, 12 hours before it's due."
                        else "Shows the time left in your notifications. Ends when you mark it done.", style = Theme.footnote, color = C.secondary)
                }
                Switch(
                    checked = counting,
                    enabled = counting || window == WidgetBridge.Window.Open,
                    onCheckedChange = { on ->
                        countdownError = null
                        if (!on) { counting = false; WidgetBridge.stopCountdown(model.context, id) }
                        else {
                            val err = WidgetBridge.startCountdown(model, it)
                            counting = err == null
                            countdownError = err
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.accent),
                )
            }
            countdownError?.let { Text(it, style = Theme.footnote, color = C.late) }
        }
    }
}

// ── Grade, instructions, type ────────────────────────────────────────────

@Composable
private fun GradeCard(g: CourseGrade.Item, sectionId: String) {
    if (g.earned == null && g.comment.isEmpty()) return
    val nav = nav
    Card(Modifier.clickable { nav.push(Route.GradeDetail(sectionId, g.id)) }, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SectionLabel("GRADED")
                Text(g.grade, style = Theme.body.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                if (g.comment.isNotEmpty()) Text(g.comment, style = Theme.footnote, color = C.secondary, maxLines = 2)
            }
            g.impact?.takeIf { abs(it) >= 0.005 }?.let { i -> Text(Fmt.signed(i), style = Theme.footnote, color = if (i < 0) C.late else C.good) }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = C.tertiary)
        }
    }
}

@Composable
private fun Instructions(a: Assignment) {
    val nav = nav
    val ctx = LocalContext.current
    val md = (a.instructionsMd ?: "").trim()
    if (md.isEmpty() && a.attachments.isEmpty()) return
    LabeledCard("INSTRUCTIONS") {
        if (md.isNotEmpty()) MarkdownText(md) { u -> nav.openLink(ctx, u) }
        for (att in a.attachments) {
            AttachmentRow(att.title, att.url) { t ->
                when (t) {
                    is OpenTarget.FileOpen -> nav.file = t.f
                    is OpenTarget.Web -> nav.openLink(ctx, t.url)
                }
            }
        }
    }
}

@Composable
private fun TypeSwitch(id: String) {
    val model = model
    val it = model.item(id) ?: return
    LabeledCard(if (NeoPlanClient.connected) "IN NEO-PLAN AS" else "TYPE") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ItemType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = it.type == t, onClick = { model.setType(it, t) },
                    shape = SegmentedButtonDefaults.itemShape(i, ItemType.entries.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = C.accentSoft, activeContentColor = C.accent),
                ) { Text(t.display) }
            }
        }
    }
}
