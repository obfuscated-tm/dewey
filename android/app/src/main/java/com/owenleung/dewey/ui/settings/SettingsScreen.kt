package com.owenleung.dewey.ui.settings

import androidx.compose.foundation.layout.height
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Badge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import com.owenleung.dewey.store.CalendarSync
import com.owenleung.dewey.store.SchoolDay
import com.owenleung.dewey.store.StudentID
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.owenleung.dewey.BuildConfigInfo
import com.owenleung.dewey.R
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.EngineJson
import com.owenleung.dewey.neoplan.NeoPlanClient
import com.owenleung.dewey.store.CallNumber
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.GradesLook
import com.owenleung.dewey.store.ShelfLook
import com.owenleung.dewey.store.ToDoLayout
import com.owenleung.dewey.store.Inbox
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.store.Reminders
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.DetailScaffold
import com.owenleung.dewey.ui.Divider
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.SettingsPage
import com.owenleung.dewey.ui.classColor
import com.owenleung.dewey.ui.tour.coachTarget
import com.owenleung.dewey.ui.tour.Tour
import com.owenleung.dewey.ui.SheetTarget
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.WideButton
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.web.WebConfig
import com.owenleung.dewey.web.WebController
import com.owenleung.dewey.web.WebViewHost
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.Date

// ── Small form parts ─────────────────────────────────────────────────────

@Composable
internal fun Section(header: String, footer: String? = null, content: @Composable () -> Unit) {
    Column {
        Text(header.uppercase(), style = Theme.caption.copy(fontWeight = FontWeight.SemiBold), color = C.secondary, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        Card { content() }
        if (footer != null) Text(footer, style = Theme.footnote, color = C.secondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
    }
}

@Composable
internal fun RowLine(divider: Boolean, content: @Composable () -> Unit) {
    if (divider) Divider()
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) { content() }
}

@Composable
internal fun Toggle(label: String, key: String, def: Boolean, divider: Boolean = true, onChange: (Boolean) -> Unit = {}) {
    Prefs.revision.intValue
    val on = Prefs.bool(key, def)
    RowLine(divider) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
            Switch(on, { Prefs.set(key, it); onChange(it) }, colors = SwitchDefaults.colors(checkedTrackColor = C.text, checkedThumbColor = C.background))
        }
    }
}

@Composable
internal fun Value(label: String, value: String, divider: Boolean = true) = RowLine(divider) {
    Row { Text(label, style = Theme.body, color = C.text, modifier = Modifier.weight(1f)); Text(value, style = Theme.body, color = C.secondary) }
}

@Composable
internal fun Action(label: String, divider: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    if (divider) Divider()
    Text(label, style = Theme.body, color = if (destructive) C.late else C.text,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 13.dp))
}

@Composable
private fun HourPicker(label: String, key: String, def: Int, hours: IntRange, onChange: () -> Unit) {
    Prefs.revision.intValue
    val h = Prefs.int(key, def)
    var open by remember { mutableStateOf(false) }
    fun text(x: Int) = Fmt.time(Fmt.atHour(Date(), x))
    RowLine(true) {
        Box {
            Row(Modifier.fillMaxWidth().clickable { open = true }) {
                Text(label, style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
                Text(text(h), style = Theme.body, color = C.accent)
            }
            DropdownMenu(open, { open = false }) {
                for (x in hours) DropdownMenuItem(text = { Text(text(x)) }, onClick = { Prefs.set(key, x); open = false; onChange() })
            }
        }
    }
}

/** A due-reminder switch or time changed: ask for permission when turning one on, then reschedule. */
private fun dueChanged(model: com.owenleung.dewey.AppModel, on: Boolean) {
    model.scope.launch {
        if (on) Reminders.requestPermission()
        Reminders.schedule(model.todoItems(), model.courses)
    }
}

private fun askIfOn(model: com.owenleung.dewey.AppModel, on: Boolean) { if (on) model.scope.launch { Reminders.requestPermission() } }

/** A white symbol on a class-colour square (the spine colour, the same in dark mode, so the white stays readable). */
@Composable
private fun IconSquare(icon: ImageVector, color: Int) {
    Box(Modifier.size(29.dp).clip(RoundedCornerShape(Theme.Radius.tag)).background(Theme.spineColor(color)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
    }
}

/** An Android Settings-style row: the icon square, the title (and a subtitle), its value, and a chevron. */
@Composable
private fun PageRow(icon: ImageVector, color: Int, title: String, value: String? = null, subtitle: String? = null, divider: Boolean = true,
                    modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (divider) Divider()
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconSquare(icon, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Theme.body, color = C.text)
            if (subtitle != null) Text(subtitle, style = Theme.caption, color = C.secondary)
        }
        if (value != null) Text(value, style = Theme.body, color = C.secondary, maxLines = 1)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = C.tertiary)
    }
}

/** A row that opens another page inside a Settings page. */
@Composable
internal fun LinkLine(label: String, value: String? = null, divider: Boolean = true, onClick: () -> Unit) {
    if (divider) Divider()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = Theme.body, color = C.secondary)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = C.tertiary)
    }
}

/** System / Light / Dark. */
@Composable
fun AppearancePicker(modifier: Modifier = Modifier) {
    Prefs.revision.intValue
    val opts = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        opts.forEachIndexed { i, (k, l) ->
            SegmentedButton(Theme.appearance == k, { Prefs.set(Theme.APPEARANCE_KEY, k) }, SegmentedButtonDefaults.itemShape(i, opts.size),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = C.accentSoft, activeContentColor = C.accent)) { Text(l) }
        }
    }
}

private val notificationKeys = listOf(Reminders.ENABLED_KEY to false, Reminders.SOON_KEY to true, Reminders.TEST_NIGHT_KEY to true,
    Reminders.MORNING_KEY to false, Reminders.GRADES_KEY to true, Reminders.SIGNED_OUT_KEY to true, Reminders.MESSAGES_KEY to true,
    Reminders.MATERIALS_KEY to true)

/**
 * 4b · A short list of pages: Schoology, Notifications, Appearance (a picker right
 * in the list), one page per tab (To Do, Grades, Classes, Inbox), Help and About.
 * Each row shows its current value.
 */
@Composable
fun SettingsScreen() {
    val model = model
    val nav = nav
    Prefs.revision.intValue
    val notificationsOn = notificationKeys.any { (k, d) -> Prefs.bool(k, d) }
    val onShelf = model.classes.count { it.sectionId !in ShelfLook.off() }
    DetailScaffold("Settings") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            // Where the tour's step for this page points.
            Spacer(Modifier.fillMaxWidth().height(1.dp).coachTarget("settings.sheet"))
            Card {
                PageRow(Icons.Outlined.School, 5, "Schoology", subtitle = Engine.HOST, divider = false) { nav.push(Route.SettingsPage(SettingsPage.SCHOOLOGY)) }
            }
            Card {
                PageRow(Icons.Outlined.Notifications, 3, "Notifications", if (notificationsOn) "On" else "Off", divider = false,
                    modifier = Modifier.coachTarget("settings.notifications")) { nav.push(Route.SettingsPage(SettingsPage.NOTIFICATIONS)) }
                Divider()
                Column(Modifier.fillMaxWidth().coachTarget("settings.appearance").padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconSquare(Icons.Outlined.Contrast, 2)
                        Spacer(Modifier.width(12.dp))
                        Text("Appearance", style = Theme.body, color = C.text)
                    }
                    AppearancePicker()
                }
            }
            Card {
                PageRow(Icons.Outlined.Badge, 2, "Student ID", if (StudentID.number == null) "Not added" else "Added", divider = false) {
                    nav.push(Route.SettingsPage(SettingsPage.STUDENT_ID))
                }
            }
            Card {
                PageRow(Icons.Outlined.Checklist, 1, "To Do", ToDoLayout.current.title, divider = false) { nav.push(Route.SettingsPage(SettingsPage.TODO)) }
                PageRow(Icons.Outlined.BarChart, 0, "Grades", if (GradesLook.reportCard) "Letters" else "List") { nav.push(Route.SettingsPage(SettingsPage.GRADES)) }
                PageRow(Icons.Outlined.LibraryBooks, 4, "Classes", "$onShelf on shelf") { nav.push(Route.SettingsPage(SettingsPage.CLASSES)) }
                PageRow(Icons.Outlined.Email, 5, "Inbox") { nav.push(Route.SettingsPage(SettingsPage.INBOX)) }
            }
            Card {
                PageRow(Icons.AutoMirrored.Outlined.HelpOutline, 7, "Help", if (NeoPlanClient.connected) "neo-plan on" else null, divider = false) {
                    nav.push(Route.SettingsPage(SettingsPage.HELP))
                }
                PageRow(Icons.Outlined.Info, 6, "About", BuildConfigInfo.versionName(LocalContext.current)) { nav.push(Route.SettingsPage(SettingsPage.ABOUT)) }
            }
        }
    }
}

/** One Settings page; the gear on To Do, Grades, Inbox and Classes lands on its own. */
@Composable
fun SettingsPageScreen(page: SettingsPage) {
    when (page) {
        SettingsPage.SCHOOLOGY -> SchoologySettings()
        SettingsPage.NOTIFICATIONS -> NotificationSettings()
        SettingsPage.STUDENT_ID -> StudentIDSettings()
        SettingsPage.TODO -> ToDoSettings()
        SettingsPage.GRADES -> GradesSettings()
        SettingsPage.CLASSES -> ShelfSettings()
        SettingsPage.INBOX -> InboxSettings()
        SettingsPage.HELP -> HelpSettings()
        SettingsPage.ABOUT -> AboutSettings()
        SettingsPage.CALL_NUMBERS -> CallNumberSettings()
        SettingsPage.STATUS_GUIDE -> StatusGuideScreen()
        SettingsPage.NEOPLAN -> NeoPlanScreen()
    }
}

@Composable
private fun SchoologySettings() {
    val model = model
    val nav = nav
    var confirmSignOut by remember { mutableStateOf(false) }
    DetailScaffold("Schoology") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            Section("Account") {
                Value("Signed in to", Engine.HOST, divider = false)
                Action("Sign out", destructive = true) { confirmSignOut = true }
            }
            Section("Sign-in", "Signs in with Chrome's user agent instead of the app's web view's. Google refuses sign-in in a web view that says it is one, so this is on by default; it turns on by itself if Google refuses.") {
                Toggle("Login compatibility mode", WebConfig.COMPAT_KEY, true, divider = false) { model.engine.reload() }
            }
        }
    }
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out of Schoology?") },
        text = { Text("Clears the Schoology session and saved pages. Your Done marks stay.") },
        confirmButton = {
            TextButton({ confirmSignOut = false; nav.stacks[nav.tab].clear(); model.scope.launch { model.signOut() } }) { Text("Sign out", color = C.late) }
        },
        dismissButton = { TextButton({ confirmSignOut = false }) { Text("Cancel") } },
    )
}

@Composable
private fun NotificationSettings() {
    val model = model
    val ctx = LocalContext.current
    Prefs.revision.intValue
    // Re-read when coming back from Android's "Alarms & reminders" page, and reschedule with exact times.
    var exact by remember { mutableStateOf(Reminders.exactAllowed) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && Reminders.exactAllowed != exact) {
                exact = Reminders.exactAllowed
                Reminders.schedule(model.todoItems(), model.courses)
            }
        }
        lifecycle.lifecycle.addObserver(o)
        onDispose { lifecycle.lifecycle.removeObserver(o) }
    }
    DetailScaffold("Notifications") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            // Where the tour's step for this page points.
            Spacer(Modifier.fillMaxWidth().height(1.dp).coachTarget("settings.page.notifications"))
            Section("Due work", "Each stops once the work is done, turned in or (for a test) studied. The 2-hour one is for Schoology dropboxes only." +
                if (exact) "" else " Without exact times, Android may send each one up to 10 minutes late.") {
                Toggle("2 hours before, if not submitted", Reminders.SOON_KEY, true, divider = false) { dueChanged(model, it) }
                Toggle("Night before a test", Reminders.TEST_NIGHT_KEY, true) { dueChanged(model, it) }
                Toggle("Homework the evening before", Reminders.ENABLED_KEY, false) { dueChanged(model, it) }
                if (Reminders.enabled || Prefs.bool(Reminders.TEST_NIGHT_KEY, true)) HourPicker("Evening time", Reminders.HOUR_KEY, 19, 15..22) { dueChanged(model, false) }
                Toggle("Morning summary", Reminders.MORNING_KEY, false) { dueChanged(model, it) }
                if (Prefs.bool(Reminders.MORNING_KEY)) HourPicker("Morning time", Reminders.MORNING_HOUR_KEY, 7, 5..10) { dueChanged(model, false) }
                if (!exact) Action("Allow exact reminder times") { Reminders.openExactAlarmSettings(ctx) }
            }
            Section("Alerts", "These come when the app refreshes in the background, so they can be late. Schoology signs you out about once a day.") {
                Toggle("New grades", Reminders.GRADES_KEY, true, divider = false) { askIfOn(model, it) }
                Toggle("Messages from teachers", Reminders.MESSAGES_KEY, true) { askIfOn(model, it) }
                Toggle("New materials in your classes", Reminders.MATERIALS_KEY, true) { askIfOn(model, it) }
                Toggle("Signed out of Schoology", Reminders.SIGNED_OUT_KEY, true) { askIfOn(model, it) }
            }
        }
    }
}

/** The To Do gear lands here: view, call numbers, and the two reminders about due work. */
@Composable
private fun ToDoSettings() {
    val model = model
    val nav = nav
    Prefs.revision.intValue
    val layout = ToDoLayout.current
    DetailScaffold("To Do") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            // Where the tour's step for this page points.
            Spacer(Modifier.fillMaxWidth().height(1.dp).coachTarget("settings.page.todo"))
            Section("View", if (layout == ToDoLayout.URGENT) "List shows one list, late and due today at the top. Plan, beside it, is your own order."
                else "List groups work by class, under a strip of days (pull it down for the month). Plan, beside it, is your own order.") {
                RowLine(false) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ToDoLayout.entries.forEachIndexed { i, l ->
                            SegmentedButton(layout == l, { Prefs.set(ToDoLayout.KEY, l.raw) }, SegmentedButtonDefaults.itemShape(i, ToDoLayout.entries.size),
                                colors = SegmentedButtonDefaults.colors(activeContainerColor = C.divider, activeContentColor = C.text)) { Text(l.title) }
                        }
                    }
                }
                LinkLine("Call numbers") { nav.push(Route.SettingsPage(SettingsPage.CALL_NUMBERS)) }
            }
            Section("School day", "A new task's \u201CBefore school\u201D and \u201CAfter school\u201D use these.") {
                MinutePicker("School starts", SchoolDay.START_KEY, SchoolDay.DEFAULT_START, divider = false, modifier = Modifier.coachTarget("settings.schoolDay"))
                MinutePicker("School ends", SchoolDay.END_KEY, SchoolDay.DEFAULT_END)
            }
            Section("Reminders", "More in Settings › Notifications.") {
                Toggle("2 hours before, if not submitted", Reminders.SOON_KEY, true, divider = false) { dueChanged(model, it) }
                Toggle("Night before a test", Reminders.TEST_NIGHT_KEY, true) { dueChanged(model, it) }
            }
            CalendarSection()
        }
    }
}

/** A time of day stored as minutes after midnight, picked on a clock. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MinutePicker(label: String, key: String, def: Int, divider: Boolean = true, modifier: Modifier = Modifier) {
    Prefs.revision.intValue
    val m = if (Prefs.has(key)) Prefs.int(key) else def
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        RowLine(divider) {
            Row(Modifier.fillMaxWidth().clickable { open = true }) {
                Text(label, style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
                Text(Fmt.clock(m / 60, m % 60), style = Theme.body, color = C.secondary)
            }
        }
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = m / 60, initialMinute = m % 60, is24Hour = false)
        AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = { TimePicker(state) },
            confirmButton = { TextButton({ Prefs.set(key, state.hour * 60 + state.minute); open = false }) { Text("Done") } },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } })
    }
}

/** The phone's Calendar app: a "Dewey" calendar with open work, tests and own tasks. */
@Composable
private fun CalendarSection() {
    val model = model
    val ctx = LocalContext.current
    Prefs.revision.intValue
    val on = CalendarSync.enabled
    var note by remember { mutableStateOf<String?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { got ->
        if (got.values.all { it } && CalendarSync.hasAccess(ctx)) {
            Prefs.set(CalendarSync.ENABLED_KEY, true); note = null; CalendarSync.schedule(model)
        } else {
            Prefs.set(CalendarSync.ENABLED_KEY, false)
            note = "Dewey isn't allowed to use Calendar. Turn it on in the phone's Settings › Apps › Dewey › Permissions."
        }
    }
    Section("Phone calendar", note ?: if (on) "Your open work, tests and own tasks are in a calendar called Dewey. Done work drops off. Turning this off removes that calendar."
        else "Puts your open work, tests and own tasks in a \u201CDewey\u201D calendar. Dewey never touches your other calendars.") {
        RowLine(false) {
            Row(Modifier.coachTarget("settings.calendar"), verticalAlignment = Alignment.CenterVertically) {
                Text("Show in the Calendar app", style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
                Switch(on, { want ->
                    if (want) {
                        if (CalendarSync.hasAccess(ctx)) { Prefs.set(CalendarSync.ENABLED_KEY, true); note = null; CalendarSync.schedule(model) }
                        else ask.launch(CalendarSync.permissions)
                    } else { CalendarSync.disable(ctx); note = null }
                }, colors = SwitchDefaults.colors(checkedTrackColor = C.text, checkedThumbColor = C.background))
            }
        }
    }
}

/**
 * Every Schoology course, as a class or a group. Dewey guesses from the name
 * (a class ends in "- 12345"); a school that names courses differently can fix it here.
 */
@Composable
private fun CourseKindSection() {
    val model = model
    Prefs.revision.intValue
    Section("Classes and groups", "Dewey guesses from the name. Classes get a book on the shelf and a place in Grades, and their messages count as from teachers. " +
        "Groups, like a class hub or advisory, show their posts in Inbox under School updates.") {
        model.courses.forEachIndexed { i, c ->
            var open by remember { mutableStateOf(false) }
            RowLine(i > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.shortTitle, style = Theme.body, color = C.text)
                        if (com.owenleung.dewey.store.CourseKinds.choice(c.sectionId) != null)
                            Text("Changed from ${if (c.looksLikeClass) "Class" else "Group"}", style = Theme.caption, color = C.secondary)
                    }
                    Box {
                        TextButton({ open = true }) { Text(if (c.isClass) "Class ▾" else "Group ▾", color = C.accent) }
                        DropdownMenu(open, { open = false }) {
                            for ((label, v) in listOf("Class" to true, "Group" to false)) DropdownMenuItem(
                                text = { Text(label) }, leadingIcon = { if (c.isClass == v) Icon(Icons.Outlined.Check, null) },
                                onClick = { model.setKind(c, v); open = false })
                        }
                    }
                }
            }
        }
    }
}

/** A switch per class, with its colour dot. */
@Composable
private fun ClassToggle(c: com.owenleung.dewey.engine.Course, on: Boolean, divider: Boolean, onToggle: () -> Unit) {
    RowLine(divider) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(classColor(c.sectionId)))
            Spacer(Modifier.width(8.dp))
            Text(c.shortTitle, style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
            Switch(on, { onToggle() }, colors = SwitchDefaults.colors(checkedTrackColor = C.accent))
        }
    }
}

@Composable
private fun GradesSettings() {
    val model = model
    Prefs.revision.intValue
    val hidden = GradesLook.hidden()
    DetailScaffold("Grades") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            Section("Look", if (GradesLook.reportCard) "Grades starts with every class's letter in one row, like a report card."
                else "Grades starts straight with the list; each class shows its letter.") {
                Toggle("Letters across the top", GradesLook.REPORT_CARD_KEY, false, divider = false)
            }
            Section("Classes in Grades", "A hidden class leaves the list, the letters, New and Recently graded, and sends no grade alerts. You can also press and hold a class in Grades.") {
                model.classes.forEachIndexed { i, c -> ClassToggle(c, c.sectionId !in hidden, i > 0) { GradesLook.toggle(c.sectionId) } }
            }
        }
    }
}

/** Settings → Classes: which classes stand on the Classes shelf. */
@Composable
private fun ShelfSettings() {
    val model = model
    Prefs.revision.intValue
    val off = ShelfLook.off()
    DetailScaffold("Classes") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            Box(Modifier.coachTarget("settings.page.classes")) {
                Section("On the shelf", "Classes off the shelf lie flat under it. You can also press and hold a class on the shelf.") {
                    model.classes.forEachIndexed { i, c -> ClassToggle(c, c.sectionId !in off, i > 0) { ShelfLook.toggle(c.sectionId) } }
                }
            }
            CourseKindSection()
        }
    }
}

@Composable
private fun InboxSettings() {
    Prefs.revision.intValue
    DetailScaffold("Inbox") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            Section("Messages", if (Inbox.markRead) "Opening a message here marks it read on Schoology."
                else "Messages stay unread on Schoology until you open them there.") {
                Toggle("Mark messages read when opened", Inbox.MARK_READ_KEY, true, divider = false)
            }
            CourseKindSection()
        }
    }
}

@Composable
private fun HelpSettings() {
    val nav = nav
    val connected = NeoPlanClient.connected
    DetailScaffold("Help") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            Section("Help", if (connected) NeoPlanStatus.text() else "neo-plan is an optional planner board. Most people don't need it.") {
                HelpTourRows()
                LinkLine("How statuses work", divider = !model.practice) { nav.push(Route.SettingsPage(SettingsPage.STATUS_GUIDE)) }
                LinkLine("neo-plan", if (connected) "Connected" else "Not connected") { nav.push(Route.SettingsPage(SettingsPage.NEOPLAN)) }
            }
        }
    }
}

/** Take the tour / Tour cards. Not inside the tour's own pretend week. */
@Composable
private fun HelpTourRows() {
    if (model.practice) return
    LinkLine("Take the tour", divider = false) { Tour.mode = Tour.Mode.TOUR }
    LinkLine("Tour cards") { Tour.mode = Tour.Mode.CARDS }
}

@Composable
private fun AboutSettings() {
    val nav = nav
    DetailScaffold("About") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            Section("This build", "A sideloaded beta: install a newer APK over it to update.") {
                Value("Version", BuildConfigInfo.versionName(LocalContext.current), divider = false)
            }
            Card { LinkLine("Developer", divider = false) { nav.push(Route.Developer) } }
        }
    }
}

/** Settings → To Do → Call numbers: each class's Dewey number on its catalog card. Automatic shows the guess (or that there isn't one). */
@Composable
private fun CallNumberSettings() {
    val model = model
    Prefs.revision.intValue
    val raw = Prefs.string(CallNumber.KEY)
    DetailScaffold("Call numbers") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            Section("Classes", "Each class's card on To Do carries its library call number. Dewey guesses from the class name and leaves it blank when it can't tell; pick one here if it's wrong or missing.") {
                model.classes.forEachIndexed { i, c ->
                    val o = CallNumber.overrides(raw)[c.sectionId]
                    RowLine(i > 0) {
                        Box {
                            var open by remember { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth().clickable { open = true }, verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(classColor(c.sectionId)))
                                Spacer(Modifier.width(8.dp))
                                Text(c.shortTitle, style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
                                Text(when (o) { null -> autoLabel(c); "" -> "None"; else -> o }, style = Theme.body, color = C.accent, maxLines = 1)
                            }
                            CallNumberMenu(c, open) { open = false }
                        }
                    }
                }
            }
        }
    }
}

private fun autoLabel(c: com.owenleung.dewey.engine.Course) = CallNumber.guess(c.shortTitle)?.let { "Automatic · $it" } ?: "Automatic · none"

/** Automatic, None, or one of Dewey's ten main classes (also from a long press on a To Do card's heading). */
@Composable
fun CallNumberMenu(c: com.owenleung.dewey.engine.Course, open: Boolean, onDismiss: () -> Unit) {
    val o = CallNumber.overrides(Prefs.string(CallNumber.KEY))[c.sectionId]
    DropdownMenu(open, onDismiss) {
        @Composable fun item(label: String, value: String?, on: Boolean) = DropdownMenuItem(
            text = { Text(label) }, leadingIcon = { if (on) Icon(Icons.Outlined.Check, null) },
            onClick = { CallNumber.set(c.sectionId, value); onDismiss() })
        item(autoLabel(c), null, o == null)
        item("None", "", o == "")
        for ((n, name) in CallNumber.mains) item("$n · $name", n, o == n)
        // A precise number set once stays listed.
        if (!o.isNullOrEmpty() && CallNumber.mains.none { it.first == o }) item(o, o, true)
    }
}

/** Run any engine op and see its JSON (M1/M2 checks), and what reminders are set. */
@Composable
fun DeveloperScreen() {
    val model = model
    val nav = nav
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val examples = linkedMapOf(
        "courses" to "{}", "todo" to "{}", "status" to """{"id":""}""", "assignment" to """{"id":""}""",
        "gradebook" to """{"sectionId":""}""", "dropbox" to """{"url":"/assignment/ID/dropbox/view/N"}""",
        "rubric" to """{"rubricId":"","enrollmentId":"","itemId":""}""", "materials" to """{"sectionId":""}""",
        "material" to """{"path":"/page/ID"}""", "feed" to """{"sectionId":"","page":0}""",
        "notifications" to """{"page":0}""", "messages" to """{"page":0}""",
    )
    fun fill(s: String) = model.classes.firstOrNull()?.sectionId?.let { s.replace("\"sectionId\":\"\"", "\"sectionId\":\"$it\"") } ?: s
    var op by remember { mutableStateOf("courses") }
    var args by remember { mutableStateOf("{}") }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    DetailScaffold("Developer") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            val planned = Reminders.planned()
            Section("Scheduled reminders (${planned.size})", if (Reminders.exactAllowed) "Exact times allowed." else "Exact times not allowed: each can come up to 10 minutes late.") {
                Action("Test reminder in 1 minute", divider = false) { Reminders.testInOneMinute() }
                Action("Background refresh in 30 seconds") { com.owenleung.dewey.RefreshWorker.runOnce(ctx, 30) }
                if (planned.isNotEmpty()) Divider()
                if (planned.isEmpty()) RowLine(true) { Text("None", style = Theme.subhead, color = C.secondary) }
                planned.forEachIndexed { i, p ->
                    RowLine(i > 0) {
                        Column {
                            Text(p.title, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                            Text(Fmt.dayTime(Date(p.fire)), style = Theme.caption, color = C.secondary)
                        }
                    }
                }
            }
            Section("Engine", "engine.js ${if (model.engine.hasScript) "bundled" else "MISSING"}. GET only; quiz pages are refused.") {
                RowLine(false) {
                    Box {
                        Row(Modifier.fillMaxWidth().clickable { menu = true }) {
                            Text("Op", style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
                            Text(op, style = Theme.body, color = C.accent)
                        }
                        DropdownMenu(menu, { menu = false }) {
                            for (k in examples.keys) DropdownMenuItem(text = { Text(k) }, onClick = { op = k; args = fill(examples[k]!!); menu = false })
                        }
                    }
                }
                RowLine(true) {
                    BasicTextField(args, { args = it }, textStyle = Theme.code.copy(color = C.text), cursorBrush = SolidColor(C.accent), modifier = Modifier.fillMaxWidth())
                }
                Action(if (running) "Running…" else "Run") {
                    if (running) return@Action
                    val a = runCatching { EngineJson.json.parseToJsonElement(args).jsonObject }.getOrNull()
                    if (a == null) { output = "args isn't a JSON object"; return@Action }
                    running = true
                    scope.launch {
                        val t0 = System.currentTimeMillis()
                        val out = model.engine.debugCall(op, a)
                        output = String.format("%.2fs\n", (System.currentTimeMillis() - t0) / 1000.0) + out
                        running = false
                    }
                }
                Action("Reload engine") { model.engine.reload() }
                // The Schoology sheet's file chooser (Files, Photos, camera) on a local page: nothing is sent anywhere.
                Action("Test file picker") {
                    nav.openSheet(SheetTarget("data:text/html,<meta name=viewport content='width=device-width'>" +
                        "<p style='font:18px sans-serif'>File picker test. Nothing is uploaded.</p><input type=file id=f multiple>" +
                        "<p id=o style='font:16px sans-serif'></p><script>f.onchange=()=>o.textContent=[...f.files].map(x=>x.name+' ('+x.size+' bytes)').join(', ')</script>"))
                }
            }
            if (output.isNotEmpty()) Section("Result") {
                RowLine(false) { SelectionContainer { Text(output.take(60_000), style = Theme.code, color = C.text) } }
                Action("Copy") {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("engine", output))
                }
            }
        }
    }
}

/**
 * 4a · First launch: Schoology in a web view → Google SSO. The app never sees
 * the password; it only watches for a signed-in Schoology page.
 */
@Composable
fun SignInScreen() {
    val model = model
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var web by remember { mutableStateOf<WebController?>(null) }
    Column(Modifier.fillMaxSize().background(C.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.dewey_hello), null, Modifier.size(84.dp))
        Text("Dewey", style = Theme.display, color = C.text)
        Text("Your Schoology To Do, grades and materials, made for your phone. Sign in with your school Google account, the same way you do on Schoology.",
            style = Theme.body, color = C.secondary)
        Card(padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Step("1", "Sign in to fuhsd.schoology.com", "Required")
                Step("2", "Turn on reminders", "Optional · in Settings")
            }
        }
        Spacer(Modifier.weight(1f))
        WideButton("Sign in to Schoology", icon = Icons.Outlined.AccountCircle) {
            web = WebController(ctx, Engine.ORIGIN).apply {
                onFinish = { url ->
                    val p = android.net.Uri.parse(url).path ?: ""
                    if (WebConfig.isSignedInUrl(url) && (p.startsWith("/home") || p == "/" || p.isEmpty() || p.startsWith("/course"))) {
                        web = null
                        scope.launch { model.signedIn() }
                    }
                }
            }
        }
        Text("Nothing is sent anywhere but Schoology. The app only reads; submitting happens on Schoology's own page.", style = Theme.caption, color = C.tertiary)
    }
    web?.let { w ->
        DisposableEffect(w) { onDispose { w.destroy() } }
        Dialog({ web = null }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            BackHandler { if (w.canGoBack) w.goBack() else web = null }
            Column(Modifier.fillMaxSize().background(C.card).statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ web = null }) { Text("Cancel", style = Theme.headline, color = C.accent) }
                    Text("Sign in", style = Theme.headline, color = C.text, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (w.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = C.accent)
                        else IconButton(w::reload) { Icon(Icons.Outlined.Refresh, "Reload", tint = C.accent) }
                    }
                }
                Divider()
                Box(Modifier.weight(1f)) { WebViewHost(w) }
            }
        }
    }
}

@Composable
private fun Step(n: String, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(C.accentSoft), contentAlignment = Alignment.Center) {
            Text(n, style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
            Text(sub, style = Theme.footnote, color = C.secondary)
        }
    }
}
