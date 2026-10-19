package com.owenleung.dewey.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.engine.Practice
import com.owenleung.dewey.store.Reminders
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.LocalModel
import com.owenleung.dewey.ui.LocalNav
import com.owenleung.dewey.ui.MainScreen
import com.owenleung.dewey.ui.Nav
import com.owenleung.dewey.ui.SheetTarget
import com.owenleung.dewey.ui.WideButton
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.widget.WidgetBridge
import kotlinx.coroutines.delay

/**
 * The tour's pretend week (ios/App/Screens/Tour/PracticeView.swift): the real
 * tabs on a practice model (Practice), with Dewey's coach on top. Ends when the
 * steps are done or the student skips; then the real app republishes its
 * widgets and reminders.
 */
@Composable
fun PracticeScreen(real: AppModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var practice by remember { mutableStateOf<AppModel?>(null) }
    LaunchedEffect(Unit) {
        Practice.begin()
        val m = AppModel.practice(ctx)
        m.boot()
        m.refreshTodo()
        practice = m
        // Let the list draw so the first targets have frames.
        delay(400)
        Coach.start(TourScript.all) {
            m.engine.destroy()
            Practice.end()
            WidgetBridge.publish(real)
            Reminders.schedule(real.todoItems(), real.courses)
            onDone()
        }
    }
    Box(Modifier.fillMaxSize().background(C.background)) {
        val m = practice
        if (m == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = C.accent)
        else {
            val nav = remember(m) { Nav(m) }
            // "Take me there": the step's tab, opened fresh.
            LaunchedEffect(Coach.jump) {
                if (Coach.jump == 0) return@LaunchedEffect
                nav.sheet = null
                nav.stacks[Coach.jumpTab].clear()
                nav.tab = Coach.jumpTab
            }
            // Back at a tab's root never leaves the app mid-tour.
            BackHandler {}
            CompositionLocalProvider(LocalModel provides m, LocalNav provides nav) {
                MainScreen()
                CoachOverlay(m)
            }
        }
    }
}

/** What Dewey says, step by step. Ids are Practice's pretend week. */
object TourScript {
    private fun seen(key: String) = Coach.seen(key)

    private val todo = listOf(
        Coach.Step(text = "Hi, I'm Dewey! This is a pretend week, so nothing you tap here touches your real Schoology. Let's try what you'll do every day.",
            hint = Coach.Hint.NONE, button = "Let's go"),
        Coach.Step("todo.check.${Practice.LAB}", "You just finished your Bio lab. Tap its circle to mark it Done.",
            done = { it.item(Practice.LAB)?.done == true }, tab = 0),
        Coach.Step("todo.action.${Practice.LAB}", "Done is just for you: your teacher doesn't have it yet. This lab goes in a Schoology dropbox, so tap Submit.",
            done = { seen("dropbox.submit") }, tab = 0),
        Coach.Step("dropbox.submit", "This is a pretend Schoology page. On the real one you'd attach your file here. Tap Submit.",
            done = { Practice.LAB in Practice.submitted }, tab = 0, rewind = 1),
        Coach.Step("dropbox.done", "Sent! Tap Done. When you close Schoology, Dewey checks it again.",
            done = { it.item(Practice.LAB)?.submitted == true }, tab = 0, rewind = 2),
        Coach.Step("todo.row.${Practice.LAB}", "The green seal means Schoology has it. Dewey only says Submitted when Schoology does.",
            hint = Coach.Hint.NONE, button = "Next", wide = true, tab = 0),
        Coach.Step("todo.row.${Practice.WORKSHEET}", "This worksheet went in on paper, so Schoology never sees it. Tap Turn in.",
            done = { it.item(Practice.WORKSHEET)?.turnedIn == true }, wide = true, tab = 0),
        Coach.Step("todo.check.${Practice.QUIZ}", "Tests are never turned in. Tap the circle once you've studied, and the night-before reminder stops.",
            done = { it.item(Practice.QUIZ)?.studied == true }, tab = 0),
        Coach.Step(text = "That's To Do! Circle = done for you. Submit or Turn in = handed in. Press and hold any row to pick its status. Now, grades.",
            hint = Coach.Hint.NONE, button = "Next"),
    )

    private val grades = listOf(
        Coach.Step("tab.1", "Tap Grades to see every class's letter.", done = { seen("grades.class.${Practice.chem}") }, tab = 1),
        Coach.Step("grades.class.${Practice.chem}", "Every class, and how far it is from an A. Tap Chemistry to see every grade in it.",
            done = { seen("grades.whatif") }, tab = 1),
        Coach.Step("grades.whatif", "What-if lets you try scores before they happen. Tap Try what-if scores.",
            done = { seen("grades.whatifbar") }, tab = 1, rewind = 1),
        Coach.Step("grades.score.${Practice.FLAME_TEST}", "Your Flame Test lab got 18 / 20. Tap the box and type 20 to see what full marks would do.",
            done = { "whatif" in Coach.flags }, tab = 1, rewind = 2),
        Coach.Step("grades.whatifbar", "Real → what-if, right here. It's only a preview: nothing is saved, and leaving the class clears it.",
            hint = Coach.Hint.NONE, button = "Next", tab = 1, rewind = 3),
    )

    private val classes = listOf(
        Coach.Step("tab.3", "Tap Classes. Each class is a book on your shelf.",
            done = { seen("shelf.book.${Practice.english}") || seen("shelf.flat.any") }, tab = 3),
        Coach.Step("shelf.book.${Practice.english}", "Not a real class, like study hall? Press and hold English 10, then tap Take off the shelf. (A tap opens its materials.)",
            done = { seen("shelf.flat.any") }, free = true, tab = 3),
        Coach.Step("shelf.flat.any", "It lies flat under the shelf now. Press and hold it again to put it back.",
            hint = Coach.Hint.NONE, button = "Next", tab = 3),
    )

    private val settings = listOf(
        Coach.Step("gear.classes", "Last stop: the gear. It opens Settings for the tab you're on. Tap it.",
            done = { seen("settings.page.classes") }, tab = 3),
        Coach.Step(text = "These are the Classes settings. Tap the back arrow at the top left for the rest of Settings.",
            hint = Coach.Hint.NONE, done = { !seen("settings.page.classes") && seen("settings.appearance") }, free = true),
        Coach.Step("settings.appearance", "Appearance: System, Light or Dark. Change it any time.",
            hint = Coach.Hint.NONE, button = "Next", wide = true, tab = 3, rewind = 2),
        Coach.Step("settings.notifications", "Notifications: reminders before work is due, the night before a test, and new grades. Pick which ones here.",
            hint = Coach.Hint.NONE, button = "Next", wide = true, tab = 3, rewind = 3),
        Coach.Step(text = "That's the tour! Your real week is waiting. You can take this again from Settings → Help.",
            hint = Coach.Hint.NONE, button = "Finish"),
    )

    val all: List<Coach.Step> get() = todo + grades + classes + settings
}

/** The Schoology sheet during practice: a pretend dropbox, so the real page (and the student's real session) is never loaded. */
@Composable
fun PracticeDropbox(target: SheetTarget, model: AppModel, onClose: () -> Unit) {
    var sent by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        onClose()
        target.assignmentId?.let(model::recheckAfterSheet)
    }
    BackHandler(onBack = close)
    val title = target.assignmentId?.let { model.item(it)?.title } ?: "Assignment"
    Column(Modifier.fillMaxSize().background(C.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(C.card).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("Done", style = Theme.headline, color = C.accent) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Schoology", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                Text("pretend page", style = Theme.caption, color = C.secondary)
            }
            Spacer(Modifier.width(72.dp))
        }
        Column(Modifier.padding(Theme.Space.l), verticalArrangement = Arrangement.spacedBy(Theme.Space.l)) {
            Row(Modifier.clip(CircleShape).background(C.accentSoft).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.TheaterComedy, null, tint = C.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pretend page · nothing is sent", style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.accent)
            }
            Text(title, style = Theme.title, color = C.text)
            Card(padding = Theme.Space.l) {
                Column(verticalArrangement = Arrangement.spacedBy(Theme.Space.m)) {
                    Text("Submit Assignment", style = Theme.headline, color = C.text)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.Radius.tag)).background(C.background).padding(Theme.Space.m),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, null, tint = C.secondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Theme.Space.s))
                        Text("my-lab-report.pdf", style = Theme.subhead, color = C.text, modifier = Modifier.weight(1f))
                        Text("Attached", style = Theme.footnote, color = C.secondary)
                    }
                    if (sent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Verified, null, tint = C.good, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Submitted", style = Theme.headline, color = C.good)
                        }
                        WideButton("Done", filled = false, modifier = Modifier.coachTarget("dropbox.done"), onClick = close)
                    } else {
                        WideButton("Submit", modifier = Modifier.coachTarget("dropbox.submit")) {
                            target.assignmentId?.let { if (it !in Practice.submitted) Practice.submitted.add(it) }
                            sent = true
                        }
                    }
                }
            }
        }
    }
}

/** The Schoology tab in the pretend week: the real website stays out of it. */
@Composable
fun PracticeSchoologyTab() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text("The full Schoology website isn't part of the pretend week.", style = Theme.subhead, color = C.secondary, textAlign = TextAlign.Center)
    }
}
