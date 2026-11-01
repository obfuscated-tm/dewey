package com.owenleung.dewey.ui.assignment

import com.owenleung.dewey.ui.todo.MineTaskScreen
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
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.owenleung.dewey.R
import com.owenleung.dewey.store.GradesLook
import com.owenleung.dewey.store.ItemState
import com.owenleung.dewey.store.TodoLogic
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.classColor

/** The Submission card is exactly one of five. */
private sealed class Kind {
    data class Dropbox(val done: Boolean) : Kind()
    data class Outside(val url: String?) : Kind()
    data object Quiz : Kind()
    data object Google : Kind()
    data object Unknown : Kind()
}

/** An outside submit page linked from the instructions (AP Bio's lab site). */
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
fun AssignmentScreen(id: String, sectionId: String? = null) {
    // The student's own task (from a widget, a reminder or a class page): never Schoology's.
    if (ItemState.isMine(id)) MineTaskScreen(id) else AssignmentPage(id, sectionId)
}

@Composable
private fun AssignmentPage(id: String, sectionId: String?) {
    val model = model
    val nav = nav
    val ctx = LocalContext.current
    var a by remember { mutableStateOf<Assignment?>(null) }
    var status by remember { mutableStateOf<SubmissionStatus?>(null) }
    var checkedAt by remember { mutableStateOf<Date?>(null) }
    var error by remember { mutableStateOf<EngineError?>(null) }
    val it = model.item(id)
    val pageUrl = "${Engine.ORIGIN}/assignment/$id"
    // The class: the saved row's, else the link's, else whichever loaded gradebook has this item.
    val section = it?.sectionId ?: sectionId ?: model.grades.entries.firstOrNull { e -> e.value.items.any { g -> g.id == id } }?.key
    val gradeItem: CourseGrade.Item? = section?.let { s -> model.grades[s]?.items?.firstOrNull { g -> g.id == id } }

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
                got.quiz?.let { q ->
                    val before = item.statusState
                    model.noteQuiz(item, q)
                    changed = changed || item.statusState != before
                }
                if (s != null || got.quiz != null) item.statusCheckedAt = System.currentTimeMillis()
                if (changed) model.save()
            }
            status = got.submission
            checkedAt = Date()
        } catch (e: EngineError) {
            error = e
        } catch (e: Exception) {
            error = EngineError.Js(e.message ?: "$e")
        }
        (model.item(id)?.sectionId ?: sectionId)?.let { model.loadGrades(it) }
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
            if (main != null) WideButton(main.first, icon = Icons.Outlined.Language, external = true, modifier = Modifier.background(C.background).navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp), onClick = main.second)
        },
    ) { pad ->
        ScrollPage(pad, refresh = { load() }, spacing = 16.dp) {
            // Header.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // A library catalog line: class · period · term · category, in the class colour.
                model.course(section)?.let { c ->
                    val line = listOfNotNull(GradesLook.abbreviation(c.shortTitle), c.periodLabel.takeIf { p -> p.length in 1..3 },
                        model.grades[c.sectionId]?.period, gradeItem?.category).filter { s -> s.isNotEmpty() }.joinToString(" · ").uppercase()
                    Text(line, style = Theme.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp), color = classColor(c.sectionId), maxLines = 2)
                }
                Text(a?.title ?: it?.title ?: "Assignment", style = Theme.displaySmall, color = C.text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (it != null) TypeTag(it.type)
                    // Once the assignment loads, the due date moves onto the checkout card.
                    if (a == null) Text(it?.due?.let { d -> "Due " + Fmt.dayTime(d) } ?: "No due date", style = Theme.footnote, color = C.secondary)
                    gradeItem?.possible?.let { p -> Text("${Fmt.num(p)} points", style = Theme.footnote, color = C.secondary) }
                }
            }
            val asg = a
            if (asg != null && kind != null) {
                SubmissionCard(kind, asg, status, checkedAt, it, section)
                if (it != null) ProgressCard(it.schoologyId)
                gradeItem?.let { g -> GradeCard(g, section ?: "") }
                Instructions(asg)
                if (it != null) TypeSwitch(it.schoologyId)
                if (gradeItem != null && gradeItem.earned == null && gradeItem.possible != null) {
                    Text("What if I get __ / ${Fmt.num(gradeItem.possible)}?", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .border(1.2.dp, C.tertiary, RoundedCornerShape(Theme.Radius.card))
                            .clickable { nav.whatIf = id to section }.padding(14.dp))
                }
            } else if (error != null) {
                ErrorState(error!!, pageUrl, retry = null, open = { u -> nav.openSheet(SheetTarget(u, id)) })
                // 403: the teacher removed it or made it private. The saved row would stay forever otherwise.
                if (error == EngineError.Http(403) && it != null && !it.dismissed) {
                    WideButton("Remove from my list", icon = Icons.Outlined.Delete, filled = false, color = C.late) {
                        model.dismiss(it)
                        nav.pop()
                    }
                }
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

/** The rubber stamp on the checkout card: the one fact that matters (due, submitted, turned in, late). */
private data class Stamp(val top: String, val main: String, val color: Color, val spoken: String)

@Composable
private fun stamp(kind: Kind, a: Assignment, item: ItemState?, due: Date?): Stamp? {
    val model = model
    val submitted = (kind is Kind.Dropbox && kind.done) || (kind == Kind.Quiz && a.quiz?.state == "submitted")
    if (submitted) return Stamp("SCHOOLOGY", "SUBMITTED", C.good, "Submitted on Schoology")
    if (item?.turnedIn == true) return Stamp("MARKED", "TURNED IN", C.good, "Marked turned in")
    due ?: return null
    val finished = item?.let { !model.showsLate(it) } ?: false
    val n = TodoLogic.daysLate(due)
    if (!finished && n != null) {
        val main = if (n == 0) "LATE" else if (n == 1) "1 DAY LATE" else "$n DAYS LATE"
        return Stamp("DUE " + Fmt.short(due).uppercase(), main, C.late, "Due ${Fmt.header(due)}, ${main.lowercase()}")
    }
    return Stamp("DUE", Fmt.short(due).uppercase(), C.text, "Due ${Fmt.header(due)}")
}

@Composable
private fun DueStamp(s: Stamp, tilt: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Theme.Radius.tag)
    Column(modifier.rotate(if (tilt) -7f else 0f).clip(shape).background(C.card).background(s.color.copy(alpha = 0.08f))
        .border(2.5.dp, s.color, shape).padding(horizontal = 10.dp, vertical = 4.dp)
        .clearAndSetSemantics { contentDescription = s.spoken }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(s.top, style = Theme.caption.copy(fontWeight = FontWeight.Black, letterSpacing = 1.4.sp), color = s.color, maxLines = 1)
        Text(s.main, style = Theme.title.copy(fontWeight = FontWeight.Black, letterSpacing = 0.4.sp), color = s.color, maxLines = 1)
    }
}

/** Where it's turned in, in two words. */
private fun place(k: Kind) = when (k) {
    is Kind.Dropbox -> "Dropbox"
    is Kind.Outside -> k.url?.let { Uri.parse(it).host?.removePrefix("www.") } ?: "Outside Schoology"
    Kind.Quiz -> "On Schoology"
    Kind.Google -> "Google"
    Kind.Unknown -> "Checking…"
}

/**
 * The Submission card as a library date-due card: ruled columns for the due date
 * and where it's turned in, what Schoology says under them, and a stamp.
 */
@Composable
private fun SubmissionCard(kind: Kind, a: Assignment, status: SubmissionStatus?, checkedAt: Date?, item: ItemState?, section: String?) {
    // ItemState is mutable and the same instance: read the revision so a mark redraws this.
    model.revision.intValue
    val model = model
    val due = Fmt.date(a.due) ?: item?.due
    val s = stamp(kind, a, item, due)
    val big = LocalDensity.current.fontScale >= 1.6f
    val band = section?.let { classColor(it) }
    val text = C.text
    val divider = C.divider
    Box(Modifier.fillMaxWidth().padding(top = if (big) 0.dp else 36.dp)) {
      Card(padding = 16.dp) {
        if (big && s != null) { DueStamp(s, tilt = false); Spacer(Modifier.height(Theme.Space.m)) }
        Row(Modifier.fillMaxWidth().drawBehind { drawLine(text, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }.padding(bottom = 6.dp)) {
            val st = Theme.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
            Text("DATE DUE", style = st, color = C.text, modifier = Modifier.weight(1f))
            if (!big) Text("TURN IN", style = st, color = C.text, modifier = Modifier.width(120.dp))
        }
        Row(Modifier.fillMaxWidth().drawBehind { drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(vertical = 10.dp).semantics(mergeDescendants = true) {}) {
            Text(Fmt.due(due), style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text, modifier = Modifier.weight(1f))
            Text(place(kind), style = Theme.subhead, color = C.secondary, modifier = if (big) Modifier else Modifier.width(120.dp))
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        // Dewey, a little worried: only here, on the one overdue thing you opened.
        if (item != null && model.isOverdue(item)) Image(painterResource(R.drawable.dewey_uh_oh), null, Modifier.size(44.dp))
        }
      }
      // The class colour across the top edge, like the band on a library card.
      if (band != null) Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(topStart = Theme.Radius.card, topEnd = Theme.Radius.card)).background(band))
      if (!big && s != null) DueStamp(s, tilt = true, Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = (-36).dp))
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
            g.impact?.let { i -> Text(Fmt.signed(i), style = Theme.footnote, color = com.owenleung.dewey.ui.impactColor(i)) }
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
    // No box: the reading text gets the room.
    Column(Modifier.fillMaxWidth().padding(top = Theme.Space.s), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Instructions", style = Theme.headline, color = C.text, modifier = Modifier.semantics { heading() })
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
