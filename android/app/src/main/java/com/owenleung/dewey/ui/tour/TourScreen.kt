package com.owenleung.dewey.ui.tour

import com.owenleung.dewey.ui.settings.StudentIDSettings
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.LocalNav
import com.owenleung.dewey.store.StudentID
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Flag
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.neoplan.NeoPlanClient
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.store.Reminders
import com.owenleung.dewey.ui.DropRipples
import com.owenleung.dewey.ui.Nav
import com.owenleung.dewey.ui.SettingsPage
import com.owenleung.dewey.ui.settings.AppearancePicker
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import kotlinx.coroutines.launch

/**
 * The tour (ios/App/Screens/Tour/TourView.swift), shown once after sign-in and
 * again from Settings → Help: a welcome card, the guided practice
 * (PracticeScreen), then neo-plan. Help → Tour cards shows the reference cards
 * instead. Drawn over the whole app while `Tour.mode` is set.
 */
object Tour {
    const val SHOWN_KEY = "tourShown"
    enum class Mode { TOUR, CARDS }
    var mode by mutableStateOf<Mode?>(null)
}

private enum class TourCard { WELCOME, PLACES, MARKING, PLAN, TURNING_IN, GRADES, SETTINGS, STUDENT_ID, NEOPLAN }

@Composable
fun TourScreen(model: AppModel, nav: Nav, mode: Tour.Mode) {
    val cards = if (mode == Tour.Mode.TOUR) listOf(TourCard.WELCOME, TourCard.STUDENT_ID, TourCard.NEOPLAN)
        else listOf(TourCard.PLACES, TourCard.MARKING, TourCard.PLAN, TourCard.TURNING_IN, TourCard.GRADES, TourCard.SETTINGS)
    var practicing by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    val pager = rememberPagerState { cards.size }
    val scope = rememberCoroutineScope()
    val current = if (mode == Tour.Mode.TOUR) page else pager.currentPage
    val last = current == cards.size - 1
    val finish = {
        Prefs.set(Tour.SHOWN_KEY, true)
        Tour.mode = null
    }

    if (practicing) {
        PracticeScreen(model) { page = cards.indexOf(TourCard.STUDENT_ID).takeIf { it >= 0 } ?: (cards.size - 1); practicing = false }
        return
    }
    BackHandler(onBack = finish)
    Column(Modifier.fillMaxSize().background(C.background).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            Text(if (mode == Tour.Mode.TOUR) "Tour" else "${current + 1} of ${cards.size}", style = Theme.footnote, color = C.secondary,
                modifier = Modifier.align(Alignment.Center))
            if (!last) TextButton(onClick = finish, modifier = Modifier.align(Alignment.CenterStart)) { Text("Skip", style = Theme.body, color = C.accent) }
        }
        Box(Modifier.weight(1f)) {
            if (mode == Tour.Mode.TOUR) {
                // No swiping past the practice: the button starts it.
                AnimatedContent(cards[page], label = "tour") { c -> CardFor(c, model, nav, finish) }
            } else {
                HorizontalPager(pager) { i -> CardFor(cards[i], model, nav, finish) }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Theme.Space.xl).padding(top = Theme.Space.m, bottom = Theme.Space.s),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Theme.Space.l)) {
            if (mode == Tour.Mode.CARDS) Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                cards.indices.forEach { i ->
                    Box(Modifier.size(width = if (i == current) 18.dp else 7.dp, height = 7.dp).clip(CircleShape)
                        .background(if (i == current) C.accent else C.divider))
                }
            }
            Button(onClick = {
                when {
                    cards[current] == TourCard.WELCOME -> practicing = true
                    last -> finish()
                    else -> scope.launch { pager.animateScrollToPage(current + 1) }
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.onAccent)) {
                Text(when { cards[current] == TourCard.WELCOME -> "Start practice"; last -> "Done"; else -> "Next" }, style = Theme.headline)
            }
        }
    }
}

@Composable
private fun CardFor(c: TourCard, model: AppModel, nav: Nav, finish: () -> Unit) = when (c) {
    TourCard.WELCOME -> WelcomeCard()
    TourCard.PLACES -> PlacesCard()
    TourCard.MARKING -> MarkingCard()
    TourCard.PLAN -> PlanCard()
    TourCard.STUDENT_ID -> StudentIDTourCard(model)
    TourCard.TURNING_IN -> TurningInCard()
    TourCard.GRADES -> GradesCard()
    TourCard.SETTINGS -> SettingsCard(model)
    TourCard.NEOPLAN -> NeoPlanCard { finish(); nav.openSettings(SettingsPage.NEOPLAN) }
}

/** One card: the art in a box at the top, a serif title, a lede, then the details. */
@Composable
private fun CardLayout(title: String, lede: String, art: @Composable ColumnScope.() -> Unit, details: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Theme.Space.xl).padding(top = Theme.Space.s, bottom = Theme.Space.l),
        verticalArrangement = Arrangement.spacedBy(Theme.Space.l)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.Radius.card)).background(C.card).padding(Theme.Space.l),
            horizontalAlignment = Alignment.CenterHorizontally, content = art)
        Column(verticalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
            Text(title, style = Theme.display, color = C.text)
            Text(lede, style = Theme.body, color = C.secondary)
        }
        details()
    }
}

/** An icon and a line of text, for the details under a card. */
@Composable
private fun Point(icon: ImageVector, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Theme.Space.m)) {
        Icon(icon, null, tint = C.accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
            Text(detail, style = Theme.footnote, color = C.secondary)
        }
    }
}

/** A pill like the To Do row's Submit ↗ / Turn in. */
@Composable
private fun Pill(text: String, green: Boolean = false, onClick: (() -> Unit)? = null) {
    val m = Modifier.clip(CircleShape).background(if (green) C.goodSoft else C.accentSoft)
    Text(text, style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = if (green) C.good else C.accent,
        modifier = (if (onClick != null) m.clickable(onClick = onClick) else m).padding(horizontal = 11.dp, vertical = 5.dp))
}

@Composable
private fun WelcomeCard() = CardLayout("Welcome to Dewey",
    "Your Schoology, easier to read: what's due, your grades and your classes in one place.",
    art = { DropRipples() }) {
    Point(Icons.Outlined.Visibility, "Dewey only reads Schoology",
        "Submitting, quizzes and posts happen on Schoology's own page inside the app, so nothing changes there unless you do it.")
    Point(Icons.Outlined.TheaterComedy, "Practice with a pretend week",
        "Dewey walks you through marking work and handing it in, in the real screens. Nothing you tap reaches Schoology.")
    Point(Icons.AutoMirrored.Outlined.Undo, "Skip any time", "The tour and the reference cards are in Settings → Help.")
}

@Composable
private fun PlacesCard() {
    val tabs = listOf(
        Triple(Icons.AutoMirrored.Outlined.FormatListBulleted, "To Do", "Everything due, by class, or Plan: your own order. Start here."),
        Triple(Icons.Outlined.BarChart, "Grades", "Every class's letter, new scores, what-if."),
        Triple(Icons.Outlined.Inbox, "Inbox", "Messages from teachers, school updates, new materials and grades."),
        Triple(Icons.Outlined.GridView, "Classes", "Your classes on a shelf: materials, files, search."),
        Triple(Icons.Outlined.Language, "Schoology", "The full website, for anything else."),
    )
    CardLayout("Where everything is", "Five tabs along the bottom.", art = {
        tabs.forEachIndexed { i, (icon, title, detail) ->
            if (i > 0) HorizontalDivider(color = C.divider)
            Row(Modifier.fillMaxWidth().padding(vertical = Theme.Space.s), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(Theme.Radius.tag)).background(Theme.spineColor(i)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(Theme.Space.m))
                Column(Modifier.weight(1f)) {
                    Text(title, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                    Text(detail, style = Theme.footnote, color = C.secondary)
                }
            }
        }
    }) {
        Point(Icons.Outlined.Settings, "The gear by each title", "Opens Settings on that tab's own page, with everything else one tap back.")
        Point(Icons.Outlined.Refresh, "Pull down to refresh", "Offline, you see the last saved copy and how old it is.")
    }
}

@Composable
private fun MarkingCard() {
    // 0 = to do, 1 = done, 2 = turned in.
    var hw by remember { mutableIntStateOf(0) }
    var studied by remember { mutableStateOf(false) }
    CardLayout("Marking work", "Try it: tap a circle, then Turn in.", art = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(when (hw) { 0 -> Icons.Outlined.Circle; 1 -> Icons.Filled.CheckCircle; else -> Icons.Filled.Verified }, if (hw == 0) "Mark done" else "Mark not done",
                tint = when (hw) { 0 -> C.tertiary; 1 -> C.accent; else -> C.good },
                modifier = Modifier.size(28.dp).clip(CircleShape).clickable { hw = if (hw == 0) 1 else 0 })
            Spacer(Modifier.width(Theme.Space.m))
            Column(Modifier.weight(1f)) {
                Text("Enzyme lab report", style = Theme.body, color = C.text)
                Text(when (hw) { 0 -> "HW · due tomorrow"; 1 -> "Done · not handed in yet"; else -> "Turned in · not on Schoology" },
                    style = Theme.footnote, color = if (hw == 2) C.good else C.secondary)
            }
            if (hw < 2) Pill("Turn in", green = true) { hw = 2 } else Pill("Undo") { hw = 0 }
        }
        HorizontalDivider(Modifier.padding(vertical = Theme.Space.m), color = C.divider)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (studied) Icons.Filled.CheckCircle else Icons.Outlined.Circle, if (studied) "Mark not studied" else "Mark studied",
                tint = if (studied) C.accent else C.tertiary, modifier = Modifier.size(28.dp).clip(CircleShape).clickable { studied = !studied })
            Spacer(Modifier.width(Theme.Space.m))
            Column(Modifier.weight(1f)) {
                Text("Unit 3 test", style = Theme.body, color = C.text)
                Text(if (studied) "Studied · no night-before reminder" else "TEST · Friday · not studied", style = Theme.footnote, color = C.secondary)
            }
        }
    }) {
        Point(Icons.Outlined.Circle, "Circle = Done, just for you", "Done doesn't hand anything in. Submit or Turn in does.")
        Point(Icons.Filled.Verified, "Green seal = turned in", "Schoology has it, or you marked paper work turned in.")
        Point(Icons.Outlined.TouchApp, "Press and hold", "Press and hold a row for every status: Done, Turned in, Studied, the type, and Hide.")
    }
}

@Composable
private fun TurningInCard() = CardLayout("Turning in", "Every class turns in its own way. Dewey shows the right button.", art = {
    listOf("Schoology dropbox" to ("Submit ↗" to false), "Lab site" to ("Lab site ↗" to false),
        "Paper or in class" to ("Mark turned in" to true), "Quiz" to ("Take on Schoology ↗" to false)).forEachIndexed { i, (way, pill) ->
        if (i > 0) HorizontalDivider(color = C.divider)
        Row(Modifier.fillMaxWidth().padding(vertical = Theme.Space.s), verticalAlignment = Alignment.CenterVertically) {
            Text(way, style = Theme.subhead, color = C.text, modifier = Modifier.weight(1f))
            Pill(pill.first, green = pill.second)
        }
    }
}) {
    Point(Icons.AutoMirrored.Outlined.OpenInNew, "↗ opens Schoology's real page",
        "Upload there and tap Done. Dewey checks again, and when Schoology has it the row turns green.")
    Point(Icons.Outlined.Description, "Paper work is never “missing”",
        "Schoology only knows its own dropbox. Paper work shows late until you mark it turned in.")
    Point(Icons.Outlined.ChevronRight, "› stays in the app, ↗ goes to Schoology or a website",
        "Tap a row for the assignment: instructions, files and how it's turned in.")
}

@Composable
private fun GradesCard() = CardLayout("Grades and what-if", "Your real grade first. Then try out scores before they happen.", art = {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("B+", style = Theme.display, color = Theme.classColor(2, C.dark))
        Spacer(Modifier.width(Theme.Space.m))
        Column {
            Text("Chemistry", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
            Text("88.4% · Goal A− · 1.6 to go", style = Theme.footnote, color = C.secondary)
        }
    }
    Spacer(Modifier.height(Theme.Space.m))
    Text("What if I get __ / 20?", style = Theme.subhead, color = C.accent,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.Radius.tag)).background(C.accentSoft).padding(Theme.Space.m))
}) {
    Point(Icons.Outlined.AutoAwesome, "New grades first",
        "New scores sit at the top until you've seen them. Tap a class for every item, its effect on your grade, comments and rubrics.")
    Point(Icons.Outlined.Tune, "What-if", "In a class, tap Try what-if scores and type a score for anything not graded yet to see your new grade.")
    Point(Icons.Outlined.Flag, "Your goal",
        "The Goal chip under a class's grade: the letter you're aiming for and how far you are from it. The graph's line and the test planner follow it.")
    Point(Icons.Outlined.TrackChanges, "Plan an upcoming test", "Also in what-if: the score you need on a test to reach your goal, counting any what-if scores you typed.")
    Point(Icons.Outlined.Lock, "Just a preview", "Nothing is saved or sent anywhere. Show real hides your what-if scores and keeps them; ↺ or leaving the class clears them.")
}

@Composable
private fun SettingsCard(model: AppModel) {
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(Reminders.permitted()) }
    CardLayout("Settings", "Set these two now, or later from the gear on To Do, Grades, Inbox or Classes.", art = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.Space.l)) {
            Column(verticalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
                Text("Appearance", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                AppearancePicker()
            }
            Column(verticalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
                Text("Reminders", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                if (allowed) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = C.good, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Notifications are on", style = Theme.subhead, color = C.good)
                } else OutlinedButton(onClick = {
                    scope.launch {
                        allowed = Reminders.requestPermission()
                        Reminders.schedule(model.todoItems(), model.courses)
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Notifications, null, tint = C.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Allow notifications", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.accent)
                }
            }
        }
    }) {
        Point(Icons.Outlined.NotificationsActive, "Notifications",
            "2 hours before work that isn't submitted, the night before a test, new grades and messages. Pick which in Settings → Notifications.")
        Point(Icons.AutoMirrored.Outlined.FormatListBulleted, "To Do",
            "View: Week (by class, under the week strip) or Urgent first. School day: the times behind Before and After school. Show in the Calendar app: your work in a Dewey calendar.")
        Point(Icons.Outlined.Badge, "Student ID",
            "Your number for the barcode on Classes. Also a launcher shortcut: press and hold Dewey's icon.")
        Point(Icons.Outlined.MenuBook, "Your classes",
            "Press and hold a book on the Classes shelf to take it off (study hall, advisory). Press and hold a class heading on To Do to change its call number.")
    }
}

@Composable
private fun NeoPlanCard(onConnect: () -> Unit) {
    val connected = NeoPlanClient.connected
    CardLayout("Using neo-plan?", "If you also use the Dewey extension on a computer with neo-plan, connect it here and both stay in step.", art = {
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
            for (col in 0 until 3) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Theme.spineColor(col)))
                repeat(3 - col) { Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(Theme.Radius.tag)).background(C.background)) }
            }
        }
    }) {
        Point(Icons.Outlined.Sync, "What syncs", "New work, Done, Studied, Turned in and the type (HW, Test, Task, CW). Never your password, grades or messages.")
        Point(Icons.Outlined.PersonOutline, "Optional", "Dewey works fully without it. No neo-plan account? Skip this.")
        if (connected) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = C.good, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("neo-plan is connected", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.good)
        } else {
            OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Text("Connect neo-plan", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.accent)
            }
            Text("Not using it? Tap Done. It's always in Settings → Help → neo-plan.", style = Theme.footnote, color = C.secondary)
        }
    }
}

// ── Plan and your own tasks ─────────────────────────────────────────────

@Composable
private fun PlanCard() = CardLayout("Plan and your own tasks", "Tap Plan beside To Do's title to put today in your own order.", art = {
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        listOf("Ch. 4 problems", "Reading notes", "Lab write-up").forEachIndexed { i, t ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.divider))
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.Space.m)) {
                Text("${i + 1}", style = Theme.callNumber, color = if (i == 0) C.accent else C.secondary, modifier = Modifier.widthIn(min = 18.dp))
                Text(t, style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.DragHandle, null, tint = C.tertiary)
            }
        }
    }
}) {
    Point(Icons.Outlined.FormatListNumbered, "To do, Later, Done",
        "To do is today, numbered. Later is another day. Done fills up as you finish. New work goes where its due date puts it.")
    Point(Icons.Outlined.PanTool, "Drag by the dots",
        "At once, no holding, even into Later or Done. Hold near the top or bottom of the screen to keep going. Swipe left for Later, right for Done.")
    Point(Icons.Outlined.ExpandMore, "Fold Later and Done",
        "Tap the heading. Work you finish in Plan stays in its row until you leave Plan.")
    Point(Icons.Outlined.Add, "Tasks of your own",
        "Tap + and type it in words: \u201Cchem lab fri after school\u201D fills in the day, time and class. Tap your task later to change it.")
    Point(Icons.Outlined.CalendarMonth, "The month",
        "On To Do, pull the week strip down. Swipe or tap the arrows for other months; tap a day to see it and the next.")
}

// ── Student ID (after the practice: the real account) ───────────────────

@Composable
private fun StudentIDTourCard(model: AppModel) {
    Prefs.revision.intValue
    val number = StudentID.number
    var adding by remember { mutableStateOf(false) }
    CardLayout("Your student ID", "Keep your ID's barcode on your phone, for the library and attendance.", art = {
        Icon(Icons.Outlined.QrCode2, null, tint = C.text, modifier = Modifier.size(96.dp).padding(vertical = 12.dp))
    }) {
        if (number != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = C.good, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Added: $number", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.good)
        } else OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, null, tint = C.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add my student ID", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
        }
        Point(Icons.Outlined.School, "Fill in from Schoology, or scan your card", "It stays on this phone. Dewey never sends it anywhere.")
        Point(Icons.Outlined.GridView, "Where it shows", "The card button on Classes, and a launcher shortcut: press and hold Dewey's icon.")
        if (number == null) Text("Not now? Tap Next. It's in Settings → Student ID.", style = Theme.footnote, color = C.secondary)
    }
    if (adding) {
        // Settings › Student ID on a nav of its own: its Back and Save close it.
        val local = remember { Nav(model).also { it.push(Route.SettingsPage(SettingsPage.STUDENT_ID)) } }
        val open = local.stacks[local.tab].isNotEmpty()
        LaunchedEffect(open) { if (!open) adding = false }
        androidx.compose.ui.window.Dialog({ adding = false }, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            CompositionLocalProvider(LocalNav provides local) {
                Box(Modifier.fillMaxSize().background(C.background)) { if (open) StudentIDSettings() }
            }
        }
    }
}
