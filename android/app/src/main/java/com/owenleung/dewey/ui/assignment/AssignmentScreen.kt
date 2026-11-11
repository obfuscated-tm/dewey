package com.owenleung.dewey.ui.assignment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.R
import com.owenleung.dewey.engine.Assignment
import com.owenleung.dewey.engine.CourseGrade
import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.EngineError
import com.owenleung.dewey.engine.QuizInfo
import com.owenleung.dewey.engine.SubmissionStatus
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.GradesLook
import com.owenleung.dewey.store.ItemState
import com.owenleung.dewey.store.ItemType
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.store.TodoLogic
import com.owenleung.dewey.store.TurnInSite
import com.owenleung.dewey.ui.DetailScaffold
import com.owenleung.dewey.ui.Divider
import com.owenleung.dewey.ui.ErrorState
import com.owenleung.dewey.ui.MarkdownText
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.SheetTarget
import com.owenleung.dewey.ui.SkeletonRows
import com.owenleung.dewey.ui.TypeTags
import com.owenleung.dewey.ui.WideButton
import com.owenleung.dewey.ui.card
import com.owenleung.dewey.ui.classColor
import com.owenleung.dewey.ui.classes.AttachmentCard
import com.owenleung.dewey.ui.classes.OpenTarget
import com.owenleung.dewey.ui.doneRipple
import com.owenleung.dewey.ui.impactColor
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.reduceMotion
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.ui.todo.CheckMark
import com.owenleung.dewey.ui.todo.MineTaskScreen
import com.owenleung.dewey.widget.WidgetBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/** Where the work goes: exactly one of five. */
private sealed class Kind {
    data class Dropbox(val done: Boolean) : Kind()
    data class Outside(val url: String?) : Kind()
    data object Quiz : Kind()
    data object Google : Kind()
    data object Unknown : Kind()
}

/**
 * 1b · One assignment: the checkout card (class band with the stamp on it, catalog line,
 * title, then a ruled slip read top to bottom: Status, Due, Turn in, Grade with What if,
 * Type), then Instructions, then the bottom bar: your circle + the main action. The
 * countdown is the ⏱ in the top bar; Open in Schoology is in ⋮. Marking it changes words
 * only: every row is always there, so nothing on the page moves.
 */
@Composable
fun AssignmentScreen(id: String, sectionId: String? = null) {
    // The student's own task (from a widget, a reminder or a class page): never Schoology's.
    if (ItemState.isMine(id)) MineTaskScreen(id) else AssignmentPage(id, sectionId)
}

/** Now, to the minute: the due line's countdown and the ⏱'s time left tick with it. */
@Composable
private fun minuteClock(): Date = produceState(Date()) {
    while (true) {
        delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
        value = Date()
    }
}.value

@Composable
private fun AssignmentPage(id: String, sectionId: String?) {
    val model = model
    val nav = nav
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var a by remember { mutableStateOf<Assignment?>(null) }
    var status by remember { mutableStateOf<SubmissionStatus?>(null) }
    var error by remember { mutableStateOf<EngineError?>(null) }
    // ItemState is mutable and the same instance: read the revision so a mark redraws this.
    model.revision.intValue
    val it = model.item(id)
    val pageUrl = "${Engine.ORIGIN}/assignment/$id"
    // The class: the saved row's, else the link's, else whichever loaded gradebook has this item.
    val section = it?.sectionId ?: sectionId ?: model.grades.entries.firstOrNull { e -> e.value.items.any { g -> g.id == id } }?.key
    val gradeItem: CourseGrade.Item? = section?.let { s -> model.grades[s]?.items?.firstOrNull { g -> g.id == id } }

    /** Where an off-Schoology assignment is turned in: this class's saved site, else a guess from the links. */
    fun turnInLink(asg: Assignment) = TurnInSite.pick(asg.instructionsMd, model.turnInSite(section))

    suspend fun load() {
        try {
            val got: Assignment = model.engine.call("assignment", mapOf("id" to id))
            a = got
            error = null
            model.item(id)?.let { item ->
                // Save only real changes: a save redraws the To Do list under this screen.
                val ext = got.type == "external_tool"
                val lab = turnInLink(got)
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
        } catch (e: EngineError) {
            error = e
        } catch (e: Exception) {
            error = EngineError.Js(e.message ?: "$e")
        }
        (model.item(id)?.sectionId ?: section)?.let { model.loadGrades(it) }
    }

    LaunchedEffect(id) { load() }
    // After the sheet re-checks the dropbox, show what the row now says.
    LaunchedEffect(model.revision.intValue) {
        it?.statusState?.let { s -> status = SubmissionStatus(s, it.statusLate) }
    }

    val asg = a
    val kind = asg?.let { x -> kindOf(x, it, status, turnInLink(x)) }
    val main = if (asg != null && kind != null) mainAction(kind, asg, it, section, pageUrl, id) else null

    DetailScaffold(
        title = "",
        actions = {
            if (asg != null && it != null) CountdownButton(it)
            MoreMenu { nav.openSheet(SheetTarget(pageUrl, id)) }
        },
        bottomBar = {
            if (asg != null && (it != null || main != null)) {
                Column(Modifier.background(C.background).navigationBarsPadding()) {
                    Divider()
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (it != null) StatusCircle(it)
                        if (main != null) WideButton(main.title, external = main.external, modifier = Modifier.weight(1f), onClick = main.run)
                        else Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
    ) { pad ->
        ScrollPage(pad, refresh = { load() }, spacing = 20.dp, bottom = 40.dp) {
            if (asg != null && kind != null) {
                Header(kind, asg, it, section, gradeItem, status, id)
                Instructions(asg)
                if (kind == Kind.Quiz) asg.quiz?.let { QuizDetails(it) }
            } else if (error != null) {
                TitleBlock(null, it, section, gradeItem, stampWidth = null)
                ErrorState(error!!, pageUrl, retry = { scope.launch { load() } }, open = { u -> nav.openSheet(SheetTarget(u, id)) })
                // 403: the teacher removed it or made it private. The saved row would stay forever otherwise.
                if (error == EngineError.Http(403) && it != null && !it.dismissed) {
                    WideButton("Remove from my list", icon = Icons.Outlined.Delete, filled = false, color = C.late) {
                        model.dismiss(it)
                        nav.pop()
                    }
                }
            } else {
                TitleBlock(null, it, section, gradeItem, stampWidth = null)
                SkeletonRows(3)
            }
        }
    }
}

private fun kindOf(a: Assignment, item: ItemState?, status: SubmissionStatus?, turnInLink: String?): Kind {
    if (a.type == "quiz" || item?.isQuiz == true) return Kind.Quiz
    if (a.type == "external_tool") return Kind.Google
    // The saved row's last check, until this page's own check answers.
    return when (status?.state ?: a.submission?.state ?: item?.statusState) {
        "submitted" -> Kind.Dropbox(true)
        "not_submitted" -> Kind.Dropbox(false)
        "no_dropbox" -> Kind.Outside(turnInLink)
        else -> Kind.Unknown
    }
}

private class MainAction(val title: String, val external: Boolean, val run: () -> Unit)

/** The main action for each kind. Paper work's is Mark turned in. */
@Composable
private fun mainAction(k: Kind, a: Assignment, it: ItemState?, section: String?, pageUrl: String, id: String): MainAction? {
    val model = model
    val nav = nav
    return when (k) {
        is Kind.Dropbox -> MainAction("Submit on Schoology", true) { nav.openSheet(SheetTarget(pageUrl, id)) }
        is Kind.Outside -> if (k.url != null) {
            val h = TurnInSite.host(k.url)
            // Opening it is how Dewey learns where this class turns in (see TurnInSite).
            MainAction("Turn in on ${h ?: "lab site"}", true) {
                if (section != null && model.turnInSite(section) == null && h != null) model.setTurnInSite(section, "https://$h")
                nav.openSheet(SheetTarget(k.url))
            }
        } else if (it != null && !it.turnedIn) {
            MainAction("Mark turned in", false) { model.setProgress(it, AppModel.Progress.TURNED_IN) }
        } else null
        Kind.Quiz -> MainAction(quizAction(a.quiz), true) { nav.openSheet(SheetTarget(Engine.ORIGIN + (it?.url ?: "/assignment/$id"), id)) }
        Kind.Google -> MainAction("Open in Schoology", true) { nav.openSheet(SheetTarget(pageUrl, id, google = true)) }
        Kind.Unknown -> null
    }
}

// ── Header ───────────────────────────────────────────────────────────────

/** "AP BIOL · P2 · 26-27 T1 · LABS & HOMEWORK": a library catalog line. */
@Composable
private fun catalogLine(c: Course, gradeItem: CourseGrade.Item?): String =
    listOfNotNull(GradesLook.abbreviation(c.shortTitle), c.periodLabel.takeIf { p -> p.length in 1..3 },
        model.grades[c.sectionId]?.period, gradeItem?.category).filter { s -> s.isNotEmpty() }.joinToString(" · ").uppercase()

/**
 * Catalog line and title. `stampWidth`: the stamp hangs over the card's top right, so
 * the class line stops short of it. Until the assignment loads (or if it can't), the
 * type, due and points sit here; after, they're rows on the slip.
 */
@Composable
private fun TitleBlock(a: Assignment?, it: ItemState?, section: String?, gradeItem: CourseGrade.Item?, stampWidth: Dp?) {
    val model = model
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        model.course(section)?.let { c ->
            Text(catalogLine(c, gradeItem), style = Theme.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                color = classColor(c.sectionId), maxLines = 2,
                modifier = Modifier.padding(end = stampWidth?.let { w -> (w - 2.dp).coerceAtLeast(0.dp) } ?: 0.dp))
        }
        Text(a?.title ?: it?.title ?: "Assignment", style = Theme.displaySmall, color = C.text)
        if (a == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (it != null) TypeTags(it.type) { t -> model.setType(it, t) }
                Text(it?.due?.let { d -> "Due " + Fmt.dayTime(d) } ?: "No due date", style = Theme.footnote, color = C.secondary)
                gradeItem?.possible?.let { p -> Text("${Fmt.num(p)} points", style = Theme.footnote, color = C.secondary) }
            }
        }
    }
}

/**
 * The header: the stamp on the class band, the catalog line and title, then the slip.
 * Its rows go in the order a student asks: Is it done? When? Where does it go? What did
 * I get? What kind is it?
 */
@Composable
private fun Header(k: Kind, a: Assignment, it: ItemState?, section: String?, gradeItem: CourseGrade.Item?, status: SubmissionStatus?, id: String) {
    val model = model
    val due = Fmt.date(a.due) ?: it?.due
    val s = stamp(k, a, it, due)
    val big = LocalDensity.current.fontScale >= 1.6f
    val density = LocalDensity.current
    var stampWidth by remember { mutableStateOf(120.dp) }
    val band = section?.let { classColor(it) }
    val ink = C.text
    Box(Modifier.fillMaxWidth().padding(top = if (big) 0.dp else 22.dp)) {
        Column(Modifier.fillMaxWidth().card(C).padding(16.dp).padding(top = 4.dp)) {
            if (big && s != null) { SlammingStamp(s, tilt = false); Spacer(Modifier.height(Theme.Space.m)) }
            TitleBlock(a, it, section, gradeItem, stampWidth = if (s != null && !big) stampWidth else null)
            Column(Modifier.padding(top = 12.dp).drawBehind { drawLine(ink, Offset(0f, 0f), Offset(size.width, 0f), 1.5.dp.toPx()) }) {
                SlipRow("Status", big) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusLine(k, a, it, status, Modifier.weight(1f))
                        // Dewey, a little worried: only here, on the one overdue thing you opened.
                        if (it != null && model.isOverdue(it)) Image(painterResource(R.drawable.dewey_uh_oh), null, Modifier.size(36.dp))
                    }
                }
                SlipRow("Due", big) { DueLine(due, it) }
                SlipRow("Turn in", big) { TurnInValue(k, a, section) }
                SlipRow("Grade", big, last = it == null) { GradeValue(gradeItem, section, id) }
                // The type is a guess from Schoology's words, so all four show and one tap fixes it.
                if (it != null) SlipRow("Type", big, last = true) { TypeTags(it.type) { t -> model.setType(it, t) } }
            }
        }
        // The class colour across the top edge, like the band on a library card; the stamp sits on it.
        if (band != null) Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(topStart = Theme.Radius.card, topEnd = Theme.Radius.card)).background(band))
        if (!big && s != null) SlammingStamp(s, tilt = true, Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = (-30).dp)
            .onSizeChanged { sz -> stampWidth = with(density) { sz.width.toDp() } })
    }
}

/** One ruled row of the slip: a quiet label column, the value beside it (stacked at the biggest text sizes). */
@Composable
private fun SlipRow(label: String, big: Boolean, last: Boolean = false, value: @Composable () -> Unit) {
    val divider = C.divider
    val m = Modifier.fillMaxWidth()
        .drawBehind { if (!last) drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
        .padding(top = 10.dp, bottom = if (last) 4.dp else 10.dp)
        .semantics(mergeDescendants = true) {}
    val labelText: @Composable () -> Unit = {
        Text(label, style = Theme.caption.copy(fontWeight = FontWeight.Medium), color = C.secondary,
            modifier = if (big) Modifier else Modifier.width(64.dp))
    }
    if (big) Column(m, verticalArrangement = Arrangement.spacedBy(4.dp)) { labelText(); value() }
    else Row(m, horizontalArrangement = Arrangement.spacedBy(10.dp)) { labelText(); Box(Modifier.weight(1f)) { value() } }
}

/** Where it's turned in, in two words. */
private fun place(k: Kind) = when (k) {
    is Kind.Dropbox -> "Dropbox"
    is Kind.Outside -> k.url?.let { TurnInSite.host(it) ?: "Lab site" } ?: "Off Schoology"
    Kind.Quiz -> "On Schoology"
    Kind.Google -> "Google"
    Kind.Unknown -> "Checking…"
}

/**
 * The Turn in row. Off Schoology, an ⓘ says why Dewey can't see it (collapsed, so it
 * doesn't nag); holding a lab site lets you pick this class's site from the links.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TurnInValue(k: Kind, a: Assignment, section: String?) {
    val model = model
    var hint by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val off = when (k) {
        is Kind.Outside -> if (k.url != null) "Dewey can't check this site. Tap your circle once it's in." else "No Schoology dropbox. Tap your circle once it's in."
        Kind.Google -> "Schoology never tells Dewey whether you turned it in, so check it there, then tap your circle."
        else -> null
    }
    val canPick = k is Kind.Outside && k.url != null && section != null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            Row(
                if (canPick) Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true }, onLongClickLabel = "Pick this class's turn-in site") else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(place(k), style = Theme.subhead, color = C.text)
                if (off != null) {
                    Box(
                        Modifier.padding(start = 2.dp).size(40.dp, 30.dp).clip(RoundedCornerShape(Theme.Radius.tag))
                            .clickable(role = Role.Button, onClickLabel = if (hint) "Hide" else "Show") { hint = !hint }
                            .semantics { contentDescription = "How Dewey knows it's turned in"; stateDescription = if (hint) "Shown" else "Hidden" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (hint) Icons.Filled.Info else Icons.Outlined.Info, null, tint = C.secondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (canPick) {
                // Hold the Turn in row: the links in these instructions, to say which one is this class's turn-in site, and Forget.
                val current = (k as Kind.Outside).url?.let(TurnInSite::host)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    for (h in TurnInSite.hosts(a.instructionsMd)) {
                        DropdownMenuItem(text = { Text("Turn in on $h") }, onClick = { menu = false; model.setTurnInSite(section!!, "https://$h") },
                            leadingIcon = { if (h == current) Icon(Icons.Outlined.Check, null) else Spacer(Modifier.size(24.dp)) })
                    }
                    if (model.turnInSite(section) != null) {
                        DropdownMenuItem(text = { Text("Forget this class's site", color = C.late) }, onClick = { menu = false; model.setTurnInSite(section!!, null) },
                            leadingIcon = { Icon(Icons.Outlined.Close, null, tint = C.late) })
                    }
                }
            }
        }
        AnimatedVisibility(off != null && hint, enter = fadeIn(), exit = fadeOut()) {
            Text(off ?: "", style = Theme.footnote, color = C.secondary)
        }
    }
}

/**
 * "Sun Nov 1, 11:59 pm", then "5 h 12 min left" under it in the last 12 hours of work
 * still owed, ticking once a minute (no seconds: it's seen every time the page opens).
 */
@Composable
private fun DueLine(due: Date?, it: ItemState?) {
    val model = model
    val now = minuteClock()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(Fmt.due(due, now), style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
        countdown(due, now, it != null && model.isOwed(it))?.let { Text(it, style = Theme.footnote, color = C.secondary) }
    }
}

/** "5 h 12 min left" in the last 12 hours before it's due, while it's still owed. */
internal fun countdown(due: Date?, now: Date, owed: Boolean): String? {
    if (due == null || !owed || !due.after(now) || due.time - now.time > 12 * 3600_000L) return null
    val m = ((due.time - now.time + 59_999) / 60_000).toInt()
    return if (m >= 60) "${m / 60} h ${m % 60} min left" else "$m min left"
}

/** One line for Schoology's fact and your own mark together. Red only while it's late. No notes under it. */
@Composable
private fun StatusLine(k: Kind, a: Assignment, it: ItemState?, status: SubmissionStatus?, modifier: Modifier = Modifier) {
    val (text, color) = statusWords(k, a, it, status)
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(statusIcon(k, a, it), null, tint = color, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

private fun statusIcon(k: Kind, a: Assignment, it: ItemState?): ImageVector = when (k) {
    is Kind.Dropbox -> if (k.done) Icons.Filled.CheckCircle else Icons.Outlined.HighlightOff
    is Kind.Outside -> if (it?.turnedIn == true) Icons.Filled.CheckCircle else Icons.AutoMirrored.Outlined.OpenInNew
    Kind.Quiz -> when (a.quiz?.state) { "submitted" -> Icons.Filled.CheckCircle; "in_progress" -> Icons.Outlined.Schedule; else -> Icons.Outlined.Assignment }
    Kind.Google -> Icons.Outlined.Description
    Kind.Unknown -> Icons.Outlined.HourglassEmpty
}

@Composable
private fun statusWords(k: Kind, a: Assignment, it: ItemState?, status: SubmissionStatus?): Pair<String, Color> {
    val model = model
    val mark = it?.let { model.progress(it) } ?: AppModel.Progress.TODO
    val isExam = it?.type == ItemType.EXAM
    val late = it?.let { model.isOverdue(it) } ?: false
    val owedColor = if (late) C.late else C.text
    val yours = when (mark) {
        AppModel.Progress.TURNED_IN -> "you marked it turned in"
        AppModel.Progress.DONE -> if (isExam) "studied" else "you marked it done"
        else -> null
    }
    return when (k) {
        is Kind.Dropbox -> if (k.done) (if (status?.late == true) "Submitted on Schoology, late" else "Submitted on Schoology") to C.good
            else listOfNotNull("Not submitted on Schoology", yours).joinToString(" · ") to owedColor
        is Kind.Outside -> if (mark == AppModel.Progress.TURNED_IN) "Turned in · marked by you" to C.good
            else (if (mark == AppModel.Progress.DONE) "Done · not turned in yet" else "Not turned in yet") to owedColor
        Kind.Quiz -> when (a.quiz?.state) {
            "submitted" -> "Submitted on Schoology" to C.good
            "in_progress" -> "Attempt in progress · resume it on Schoology" to C.late
            else -> (if (mark == AppModel.Progress.DONE) "Not started · studied" else "Not started · opens only when you tap Take") to (if (late) C.late else C.secondary)
        }
        Kind.Google -> if (mark == AppModel.Progress.TURNED_IN) "Google Assignment · you marked it turned in" to C.good else "Google Assignment" to C.text
        Kind.Unknown -> "Checking Schoology…" to C.secondary
    }
}

/**
 * The Grade row. Graded: the grade, its effect on the class, › to the grade page.
 * Not yet: "Not graded · 20 pts" and a small What if? that opens one line in place.
 */
@Composable
private fun GradeValue(g: CourseGrade.Item?, section: String?, id: String) {
    val nav = nav
    if (g != null && (g.earned != null || g.comment.isNotEmpty())) {
        Row(Modifier.fillMaxWidth().clickable { nav.push(Route.GradeDetail(section ?: "", g.id)) }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(g.grade, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                if (g.comment.isNotEmpty()) Text(g.comment, style = Theme.footnote, color = C.secondary, maxLines = 2)
            }
            g.impact?.let { i -> Text(Fmt.signed(i), style = Theme.footnote, color = impactColor(i)) }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = C.tertiary, modifier = Modifier.size(18.dp))
        }
        return
    }
    var whatIf by remember { mutableStateOf(false) }
    val possible = g?.possible
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(possible?.let { "Not graded · ${Fmt.num(it)} pts" } ?: "Not graded", style = Theme.subhead, color = C.secondary, modifier = Modifier.weight(1f))
            // Schoology shows the points only once it's graded, so usually you type them.
            if (g != null) {
                val line = C.tertiary
                val r = Theme.Radius.tag
                Text(
                    if (whatIf) "Close" else "What if?",
                    style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.text,
                    modifier = Modifier.heightIn(min = 30.dp).clip(RoundedCornerShape(r))
                        .drawBehind {
                            drawRoundRect(line, cornerRadius = CornerRadius(r.toPx()),
                                style = Stroke(1.2.dp.toPx(), pathEffect = if (whatIf) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                        }
                        .clickable(role = Role.Button) { whatIf = !whatIf }
                        .semantics { contentDescription = if (whatIf) "Close what if" else "What if: see your class grade with a score" }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        AnimatedVisibility(whatIf && section != null, enter = fadeIn(), exit = fadeOut()) {
            WhatIfLine(id, section ?: "", possible?.takeIf { it > 0 })
        }
    }
}

/** The rubber stamp on the checkout card: the one fact that matters (due, submitted, turned in, late). */
internal data class Stamp(val top: String, val main: String, val color: Color, val spoken: String)

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

/** Clean bold type in a rounded frame, tilted a little; a fixed minimum width so DUE NOV 1 and TURNED IN take the same room. */
@Composable
private fun DueStamp(s: Stamp, tilt: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Theme.Radius.tag)
    Column(modifier.rotate(if (tilt) -7f else 0f).then(if (tilt) Modifier.widthIn(min = 112.dp) else Modifier)
        .clip(shape).background(C.card).background(s.color.copy(alpha = 0.08f))
        .border(2.5.dp, s.color, shape).padding(horizontal = 10.dp, vertical = 4.dp)
        .clearAndSetSemantics { contentDescription = s.spoken }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(s.top, style = Theme.caption.copy(fontWeight = FontWeight.Black, letterSpacing = 1.4.sp), color = s.color, maxLines = 1)
        Text(s.main, style = Theme.title.copy(fontWeight = FontWeight.Black, letterSpacing = 0.4.sp), color = s.color, maxLines = 1)
    }
}

/**
 * The stamp, slammed down again when what it says changes while you're looking (back from
 * the Schoology sheet and it's SUBMITTED, or you mark it turned in): in big, a heavy thud,
 * settled in about 300 ms. Never on opening the page; just the thud with animations off.
 */
@Composable
private fun SlammingStamp(s: Stamp, tilt: Boolean, modifier: Modifier = Modifier) {
    val still = reduceMotion()
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    val key = s.top + s.main
    val seen = remember { arrayOf(key) }
    LaunchedEffect(key) {
        if (seen[0] == key) return@LaunchedEffect
        seen[0] = key
        if (!still) {
            scale.snapTo(1.8f); alpha.snapTo(0f)
            launch { alpha.animateTo(1f, tween(70)) }
            launch { delay(130); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            scale.animateTo(0.94f, tween(140))
            scale.animateTo(1f, tween(140))
        } else haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    DueStamp(s, tilt, modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value })
}

// ── What if, in one line inside the Grade row ────────────────────────────

/**
 * "If I get [18] / 20 → 91.3% +0.3": the class grade worked out as you type, with the
 * same math as the what-if sheet. Schoology shows an item's points only once it's graded,
 * so without them both boxes are yours: "[18] / [20]".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhatIfLine(itemId: String, sectionId: String, known: Double?) {
    val model = model
    val scope = rememberCoroutineScope()
    var score by remember { mutableStateOf("") }
    var outOf by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CourseGrade?>(null) }
    var gen by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val possible = known ?: outOf.toDoubleOrNull()?.takeIf { it > 0 }
    val now = model.grades[sectionId]?.pct

    fun recompute() {
        val e = score.toDoubleOrNull()
        val p = known ?: outOf.toDoubleOrNull()?.takeIf { it > 0 }
        val rows = model.gradeRows(sectionId)
        if (e == null || p == null || rows == null) { result = null; return }
        val mine = ++gen
        scope.launch {
            val r = runCatching { model.summarize(rows, mapOf(itemId to mapOf("earned" to e, "possible" to p))) }.getOrNull()
            if (mine == gen) result = r
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val style = Theme.subhead
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("If I get", style = style, color = C.secondary)
            ScoreBox(score, known?.let { "What-if score, out of ${Fmt.num(it)}" } ?: "What-if score", Modifier.focusRequester(focus)) { score = it; recompute() }
            if (known != null) Text("/ ${Fmt.num(known)}", style = style, color = C.secondary)
            else {
                Text("/", style = style, color = C.secondary)
                ScoreBox(outOf, "Out of how many points") { outOf = it; recompute() }
            }
        }
        val r = result?.pct
        if (r != null && score.toDoubleOrNull() != null && possible != null) {
            Row(Modifier.clearAndSetSemantics { contentDescription = "Class grade would be ${Fmt.pct(r, 1)}" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("→", style = style, color = C.secondary)
                Text(Fmt.pct(r, 1), style = style.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                if (now != null && kotlin.math.abs(r - now) >= 0.05) {
                    Text(Fmt.signed(r - now), style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = if (r < now) C.late else C.good)
                }
            }
        } else if (now != null) {
            Text("Now " + Fmt.pct(now, 1), style = style, color = C.secondary)
        }
    }
}

@Composable
private fun ScoreBox(value: String, label: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(Theme.Radius.tag)
    BasicTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { c -> c.isDigit() || c == '.' }.take(6)) },
        singleLine = true,
        textStyle = Theme.subhead.copy(fontWeight = FontWeight.SemiBold, color = C.text, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(C.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.width(48.dp).clip(shape).background(C.background).border(1.2.dp, C.tertiary, shape)
            .padding(vertical = 4.dp).semantics { contentDescription = label },
    )
}

// ── A newer quiz: read from its landing page, never from the quiz itself ──

/** Score, attempts and times, under the instructions (the header says the state). */
@Composable
private fun QuizDetails(q: QuizInfo) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quiz", style = Theme.headline, color = C.text, modifier = Modifier.semantics { heading() })
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
                Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
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
}

@Composable
private fun QuizRow(label: String, value: String) {
    Row(Modifier.semantics(mergeDescendants = true) {}) {
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

// ── Instructions: the page ───────────────────────────────────────────────

@Composable
private fun Instructions(a: Assignment) {
    val nav = nav
    val ctx = LocalContext.current
    val md = (a.instructionsMd ?: "").trim()
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (md.isNotEmpty() || a.attachments.isNotEmpty()) {
            Text("Instructions", style = Theme.headline, color = C.text, modifier = Modifier.semantics { heading() })
        }
        // Body size, unboxed: the reading is the page. Links in the accent, underlined.
        if (md.isNotEmpty()) MarkdownText(md, style = Theme.body.copy(lineHeight = Theme.body.lineHeight * 1.12f)) { u -> nav.openLink(ctx, u) }
        else if (a.attachments.isEmpty()) Text("No instructions on Schoology.", style = Theme.subhead, color = C.secondary)
        a.attachments.forEachIndexed { n, att ->
            AttachmentCard(att.title, att.url, thumbnail = n < 4) { t ->
                when (t) {
                    is OpenTarget.FileOpen -> nav.file = t.f
                    is OpenTarget.Web -> nav.openLink(ctx, t.url)
                }
            }
        }
    }
}

// ── ⏱ countdown and ⋮, in the top bar ───────────────────────────────────

/**
 * Always there so nothing shifts; usable in the last 12 hours of work still owed. While it
 * runs it shows the time left in teal. A one-time tip names it the first time it's usable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownButton(it: ItemState) {
    val model = model
    var counting by remember { mutableStateOf(WidgetBridge.isCounting(it.schoologyId)) }
    val now = minuteClock()
    val open = WidgetBridge.countdownWindow(it.due, now) == WidgetBridge.Window.Open
    val can = counting || (open && !model.isFinished(it))
    val tip = rememberTooltipState(isPersistent = true)
    LaunchedEffect(can, counting) {
        if (can && !counting && !Prefs.bool(TIP_KEY)) {
            Prefs.set(TIP_KEY, true)
            tip.show()
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("Countdown: shows the time left in your notifications.") } },
        state = tip,
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(Theme.Radius.tag))
                .clickable(enabled = can, role = Role.Button) {
                    tip.dismiss()
                    if (counting) {
                        counting = false
                        WidgetBridge.stopCountdown(model.context, it.schoologyId)
                    } else {
                        val err = WidgetBridge.startCountdown(model, it)
                        if (err == null) counting = true else model.toast = err
                    }
                }
                .semantics { contentDescription = if (counting) "Stop countdown notification" else if (can) "Countdown notification" else "Countdown notification, available in the last 12 hours before it's due" }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (counting) C.accent else if (can) C.text else C.tertiary
            Icon(Icons.Outlined.Timer, null, tint = tint)
            val due = it.due
            if (counting && due != null && due.after(now)) {
                val m = ((due.time - now.time + 59_999) / 60_000).toInt()
                Spacer(Modifier.width(4.dp))
                Text(String.format(java.util.Locale.US, "%d:%02d", m / 60, m % 60), style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = tint)
            }
        }
    }
}

private const val TIP_KEY = "tip.assignmentCountdown"

@Composable
private fun MoreMenu(openInSchoology: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "More", tint = C.text) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Open in Schoology") }, onClick = { open = false; openInSchoology() },
                leadingIcon = { Icon(Icons.Outlined.Language, null) })
        }
    }
}

// ── Bottom bar: your circle ──────────────────────────────────────────────

/**
 * Your circle, the same one as on To Do (tap: to do ↔ done; hold for every status).
 * Outlined so it reads as a button; a fixed width so the main action never resizes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusCircle(it: ItemState) {
    val model = model
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val p = model.progress(it)
    fun word(q: AppModel.Progress) = if (it.isExam && q == AppModel.Progress.DONE) "Studied" else q.label
    val shape = RoundedCornerShape(Theme.Radius.tag)
    Box {
        Row(
            Modifier.widthIn(min = 132.dp).heightIn(min = 48.dp).clip(shape).border(1.2.dp, C.text, shape)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = if (p == AppModel.Progress.TODO) (if (it.isExam) "Mark it studied" else "Mark it done") else "Mark it to do",
                    onLongClickLabel = "Every status",
                    onLongClick = { menu = true },
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    model.setProgress(it, if (p == AppModel.Progress.TODO) AppModel.Progress.DONE else if (p == AppModel.Progress.DONE) AppModel.Progress.TODO else AppModel.Progress.DONE)
                }
                .semantics { contentDescription = "Your status: ${word(p)}" }
                .padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(28.dp).doneRipple(p != AppModel.Progress.TODO, C.accent)) { CheckMark(it, 28.dp) }
            Spacer(Modifier.width(8.dp))
            Text(word(p), style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text, maxLines = 1)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            for (q in AppModel.Progress.entries) {
                DropdownMenuItem(text = { Text(word(q)) }, onClick = { menu = false; model.setProgress(it, q) },
                    leadingIcon = { if (q == p) Icon(Icons.Outlined.Check, null) else Spacer(Modifier.size(24.dp)) })
            }
        }
    }
}
