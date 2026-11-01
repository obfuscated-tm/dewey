package com.owenleung.dewey.ui.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.ItemType
import com.owenleung.dewey.store.TaskParse
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.DetailScaffold
import com.owenleung.dewey.ui.EmptyState
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.TaskTarget
import com.owenleung.dewey.ui.TypeTag
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.settings.Action
import com.owenleung.dewey.ui.settings.RowLine
import com.owenleung.dewey.ui.settings.Section
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.ui.tour.Coach
import com.owenleung.dewey.ui.tour.coachTarget
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** The four kinds a task of the student's own can be, in menu order. */
val TASK_KINDS = listOf(ItemType.TASK, ItemType.ASSIGNMENT, ItemType.EXAM, ItemType.CLASSWORK)

fun kindName(t: ItemType) = when (t) { ItemType.TASK -> "Task"; ItemType.ASSIGNMENT -> "Homework"; ItemType.EXAM -> "Test"; ItemType.CLASSWORK -> "Classwork" }

/** The task sheets: quick add for a new one, the full editor for one of the student's own. */
@Composable
fun TaskSheets() {
    val nav = nav
    val t = nav.task ?: return
    if (t.id == null) QuickTask(t.sectionId) { nav.task = null } else TaskEditor(t) { nav.task = null }
}

/** A chip's choice: a value, or deliberately none. */
private sealed class Choice<out T> {
    data object None : Choice<Nothing>()
    data class Some<T>(val v: T) : Choice<T>()
    val value: T? get() = (this as? Some<T>)?.v
}

/**
 * To Do's +: a short sheet over the keyboard. Type the task, with its day, time
 * and class in plain words if you like ("chem lab fri after school", TaskParse);
 * or tap a chip. A chip wins over what was typed. Drawn in the window (not a
 * dialog), so the tour's coach can point into it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTask(sectionId: String?, onClose: () -> Unit) {
    val model = model
    val now = remember { Date() }
    val today = Fmt.localDate(now)
    var text by remember { mutableStateOf("") }
    var day by remember { mutableStateOf<Choice<LocalDate>?>(null) }
    var time by remember { mutableStateOf<Choice<TaskParse.Clock>?>(null) }
    var section by remember { mutableStateOf<Choice<String>?>(sectionId?.let { Choice.Some(it) }) }
    var type by remember { mutableStateOf<ItemType?>(null) }
    var pickDay by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val parsed = TaskParse.parse(text, now, model.classes.map { TaskParse.ClassName(it.sectionId, it.title) })
    val d = if (day != null) day!!.value else parsed.day
    val t = if (time != null) time!!.value else parsed.time
    val s = if (section != null) section!!.value else parsed.sectionId
    val k = type ?: parsed.type ?: ItemType.TASK
    val clean = text.trim()
    fun add() {
        if (clean.isEmpty()) return
        model.addTask(parsed.title, k, s, TaskParse.due(d, t, now))
        onClose()
    }
    // The tour waits for a task typed with its day, time and class.
    val all = parsed.day != null && parsed.time != null && parsed.sectionId != null
    LaunchedEffect(all) { if (all && model.practice) Coach.note("quickParsed") }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(C.background)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .coachTarget("quick.sheet")
                .padding(Theme.Space.l),
            verticalArrangement = Arrangement.spacedBy(Theme.Space.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
                OutlinedTextField(text, { text = it }, Modifier.weight(1f).focusRequester(focus).coachTarget("quick.field")
                    .semantics { contentDescription = "Task" },
                    placeholder = { Text("Chem lab fri after school", color = C.tertiary) }, singleLine = true, textStyle = Theme.body.copy(color = C.text),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }), shape = RoundedCornerShape(Theme.Radius.card),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.text, unfocusedBorderColor = C.border, cursorColor = C.text))
                IconButton(onClick = { add() }, enabled = clean.isNotEmpty(), modifier = Modifier.coachTarget("quick.add")) {
                    Icon(Icons.Filled.ArrowCircleUp, "Add task", tint = if (clean.isEmpty()) C.tertiary else C.accent, modifier = Modifier.size(34.dp))
                }
            }
            // Day, time, class, kind: four small menus, all on screen. Each shows what's chosen.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    ChipLabel(d?.let { dayLabel(it, today) } ?: "Day", Icons.Outlined.CalendarMonth, d != null,
                        d?.let { "Due ${it.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US))}" } ?: "Due day") { pickDay = true }
                }
                Box(Modifier.weight(1f)) {
                    var open by remember { mutableStateOf(false) }
                    ChipLabel(t?.let { clockText(it, short = true) } ?: "Time", Icons.Outlined.Schedule, t != null, "Due time") { open = true }
                    DropdownMenu(open, { open = false }) {
                        val options = listOf("Before school · ${clockText(TaskParse.beforeSchool)}" to TaskParse.beforeSchool,
                            "After school · ${clockText(TaskParse.afterSchool)}" to TaskParse.afterSchool, "Tonight · 11:59 pm" to TaskParse.TONIGHT, "No time" to null)
                        for ((label, c) in options) DropdownMenuItem(text = { Text(label) }, leadingIcon = { if (t == c) Icon(Icons.Outlined.Check, null) },
                            onClick = { open = false; time = c?.let { Choice.Some(it) } ?: Choice.None })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Pick a time…") }, leadingIcon = { Icon(Icons.Outlined.Schedule, null) }, onClick = { open = false; pickTime = true })
                    }
                }
                Box(Modifier.weight(1f)) {
                    var open by remember { mutableStateOf(false) }
                    ChipLabel(s?.let { model.course(it)?.shortTitle } ?: "Class", Icons.Outlined.School, s != null, "Class") { open = true }
                    DropdownMenu(open, { open = false }) {
                        for (c in model.classes) DropdownMenuItem(text = { Text(c.shortTitle) }, leadingIcon = { if (s == c.sectionId) Icon(Icons.Outlined.Check, null) },
                            onClick = { open = false; section = Choice.Some(c.sectionId) })
                        DropdownMenuItem(text = { Text("No class") }, leadingIcon = { if (s == null) Icon(Icons.Outlined.Check, null) }, onClick = { open = false; section = Choice.None })
                    }
                }
                Box(Modifier.weight(1f)) {
                    var open by remember { mutableStateOf(false) }
                    ChipLabel(kindName(k), Icons.Outlined.Sell, k != ItemType.TASK, "Kind: ${kindName(k)}") { open = true }
                    DropdownMenu(open, { open = false }) {
                        for (x in TASK_KINDS) DropdownMenuItem(text = { Text(kindName(x)) }, leadingIcon = { if (k == x) Icon(Icons.Outlined.Check, null) },
                            onClick = { open = false; type = x })
                    }
                }
            }
            // One tap for the usual picks; tap one that's on to clear it.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val tomorrow = today.plusDays(1)
                Quick("Today", d == today) { day = if (d == today) Choice.None else Choice.Some(today) }
                Quick("Tomorrow", d == tomorrow) { day = if (d == tomorrow) Choice.None else Choice.Some(tomorrow) }
                Quick("After school", t == TaskParse.afterSchool) { time = if (t == TaskParse.afterSchool) Choice.None else Choice.Some(TaskParse.afterSchool) }
                Quick("Tonight", t == TaskParse.TONIGHT) { time = if (t == TaskParse.TONIGHT) Choice.None else Choice.Some(TaskParse.TONIGHT) }
            }
        }
    }

    if (pickDay) {
        // The picker works in UTC midnights.
        val state = rememberDatePickerState(initialSelectedDateMillis = (d ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
            })
        // Picking a day closes the calendar.
        val first = remember { state.selectedDateMillis }
        LaunchedEffect(state.selectedDateMillis) {
            val ms = state.selectedDateMillis ?: return@LaunchedEffect
            if (ms == first) return@LaunchedEffect
            day = Choice.Some(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
            pickDay = false
        }
        DatePickerDialog(onDismissRequest = { pickDay = false },
            confirmButton = { TextButton({ state.selectedDateMillis?.let { day = Choice.Some(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; pickDay = false }) { Text("Done") } },
            dismissButton = { TextButton({ day = Choice.None; pickDay = false }) { Text("No date") } }) {
            DatePicker(state, title = null, headline = null, showModeToggle = false)
        }
    }
    if (pickTime) {
        val state = rememberTimePickerState(initialHour = t?.hour ?: 16, initialMinute = t?.minute ?: 0, is24Hour = false)
        AlertDialog(onDismissRequest = { pickTime = false }, title = { Text("Due time") }, text = { TimePicker(state) },
            confirmButton = { TextButton({ time = Choice.Some(TaskParse.Clock(state.hour, state.minute)); pickTime = false }) { Text("Done") } },
            dismissButton = { TextButton({ pickTime = false }) { Text("Cancel") } })
    }
}

/** A menu button: the four share the row's width equally. */
@Composable
private fun ChipLabel(title: String, icon: ImageVector, on: Boolean, a11y: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Theme.Radius.tag)
    val color = if (on) C.accent else C.secondary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 32.dp).clip(shape).background(if (on) C.accentSoft else C.card)
            .border(1.dp, if (on) C.accent.copy(alpha = 0.5f) else C.border, shape).clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = a11y }.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(title, style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A shortcut on the second row: plain text, tinted when it's what's set. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Quick(title: String, on: Boolean, onClick: () -> Unit) {
    Text(title, style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = if (on) C.accent else C.text, maxLines = 1,
        overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(Theme.Radius.tag)).background(if (on) C.accentSoft else C.divider.copy(alpha = 0.5f))
            .clickable(onClick = onClick).semantics { selected = on }.padding(vertical = 7.dp))
}

/** The day button: "Today", "Tmrw", "Fri", or "Oct 30" further out. */
private fun dayLabel(d: LocalDate, today: LocalDate): String {
    val n = java.time.temporal.ChronoUnit.DAYS.between(today, d)
    return when {
        n == 0L -> "Today"
        n == 1L -> "Tmrw"
        n in 2..6 -> d.format(DateTimeFormatter.ofPattern("EEE", Locale.US))
        else -> d.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
    }
}

/** "3:30 pm", or "3:30" for a chip that already says before or after school. */
private fun clockText(c: TaskParse.Clock, short: Boolean = false): String {
    val h = if (c.hour % 12 == 0) 12 else c.hour % 12
    val base = "$h:${"%02d".format(c.minute)}"
    return if (short) base else "$base ${if (c.hour < 12) "am" else "pm"}"
}

/** No time: 11:59 pm, like Schoology's own "due that day". */
fun isEndOfDay(d: Date): Boolean = d.toInstant().atZone(Fmt.zone).let { it.hour == 23 && it.minute == 59 }

/**
 * A task of the student's own: change or delete one (tap its row). Lives on
 * the phone only: never on Schoology or neo-plan (ios/App/Screens/ToDo/TaskEditor.swift).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditor(target: TaskTarget, onClose: () -> Unit) {
    val model = model
    val task = target.id?.let { model.item(it) }
    var title by remember { mutableStateOf(task?.title ?: "") }
    var type by remember { mutableStateOf(task?.type ?: ItemType.TASK) }
    var section by remember { mutableStateOf(task?.sectionId ?: target.sectionId) }
    val start = task?.due ?: Fmt.atHour(Fmt.addDays(Date(), 1), 16)
    var hasDue by remember { mutableStateOf(task?.due != null) }
    var dueDay by remember { mutableStateOf(Fmt.localDate(start)) }
    var hasTime by remember { mutableStateOf(task?.due?.let { !isEndOfDay(it) } ?: false) }
    var clock by remember { mutableStateOf(start.toInstant().atZone(Fmt.zone).let { TaskParse.Clock(it.hour, it.minute) }) }
    var notes by remember { mutableStateOf(task?.notes ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDay by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val clean = title.trim()

    fun save() {
        if (clean.isEmpty()) return
        val c = if (hasTime) clock else TaskParse.TONIGHT
        val whenDue = if (hasDue) TaskParse.due(dueDay, c, Date()) else null
        val n = notes.trim().ifEmpty { null }
        if (task != null) model.updateTask(task, clean, type, section, whenDue, n) else model.addTask(clean, type, section, whenDue, n)
        onClose()
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        Column(Modifier.fillMaxSize().background(C.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClose) { Text("Cancel", color = C.text) }
                Text(if (task == null) "New task" else "Task", style = Theme.headline, color = C.text, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(::save, enabled = clean.isNotEmpty()) { Text(if (task == null) "Add" else "Save", fontWeight = FontWeight.SemiBold, color = if (clean.isEmpty()) C.tertiary else C.text) }
            }
            ScrollPage(spacing = 22.dp) {
                Card {
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(8.dp), placeholder = { Text("What do you need to do?", color = C.tertiary) },
                        textStyle = Theme.body.copy(color = C.text), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = C.text))
                    MenuRow("Kind", kindName(type), TASK_KINDS.map { kindName(it) to { type = it } })
                    MenuRow("Class", section?.let { model.course(it)?.shortTitle } ?: "None",
                        listOf("None" to { section = null }) + model.classes.map { c -> c.shortTitle to { section = c.sectionId } })
                }
                Section("Due", if (hasDue) (if (hasTime) "Late once that time passes." else "Due by the end of the day.")
                    else "No date: it stays at the end of your Plan until you move it.") {
                    SwitchRow("Due date", hasDue, false) { hasDue = it }
                    if (hasDue) {
                        PickRow("Day", dueDay.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US))) { pickDay = true }
                        SwitchRow("At a time", hasTime) { hasTime = it }
                        if (hasTime) PickRow("Time", clockText(clock)) { pickTime = true }
                    }
                }
                Card {
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().padding(8.dp), placeholder = { Text("Notes", color = C.tertiary) },
                        minLines = 2, maxLines = 6, textStyle = Theme.body.copy(color = C.text),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = C.text))
                }
                if (task != null) Card { Action("Delete task", divider = false, destructive = true) { confirmDelete = true } }
                else Text("Only on this phone. Nothing is added to Schoology.", style = Theme.footnote, color = C.secondary, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this task?") },
            confirmButton = { TextButton({ confirmDelete = false; task?.let(model::deleteTask); onClose() }) { Text("Delete", color = C.late) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
        if (pickDay) {
            val state = rememberDatePickerState(initialSelectedDateMillis = dueDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(onDismissRequest = { pickDay = false },
                confirmButton = { TextButton({ state.selectedDateMillis?.let { dueDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; pickDay = false }) { Text("Done") } },
                dismissButton = { TextButton({ pickDay = false }) { Text("Cancel") } }) { DatePicker(state, showModeToggle = false) }
        }
        if (pickTime) {
            val state = rememberTimePickerState(initialHour = clock.hour, initialMinute = clock.minute, is24Hour = false)
            AlertDialog(onDismissRequest = { pickTime = false }, title = { Text("Due time") }, text = { TimePicker(state) },
                confirmButton = { TextButton({ clock = TaskParse.Clock(state.hour, state.minute); pickTime = false }) { Text("Done") } },
                dismissButton = { TextButton({ pickTime = false }) { Text("Cancel") } })
        }
    }
}

@Composable
private fun MenuRow(label: String, value: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    RowLine(true) {
        Box {
            Row(Modifier.fillMaxWidth().clickable { open = true }, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
                Text(value, style = Theme.body, color = C.secondary)
            }
            DropdownMenu(open, { open = false }) {
                for ((t, f) in options) DropdownMenuItem(text = { Text(t) }, leadingIcon = { if (t == value) Icon(Icons.Outlined.Check, null) }, onClick = { open = false; f() })
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, divider: Boolean = true, onChange: (Boolean) -> Unit) = RowLine(divider) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.text, checkedThumbColor = C.background))
    }
}

@Composable
private fun PickRow(label: String, value: String, onClick: () -> Unit) = RowLine(true) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
        Text(value, style = Theme.body, color = C.secondary)
    }
}

/** The student's own task, opened somewhere other than To Do (a widget, a class page): what it is, its status, and Edit. */
@Composable
fun MineTaskScreen(id: String) {
    val model = model
    val nav = nav
    model.revision.intValue
    val it = model.item(id)
    DetailScaffold("", actions = { if (it != null) TextButton({ nav.task = TaskTarget(id) }) { Text("Edit", color = C.text) } }) { pad ->
        ScrollPage(pad, spacing = Theme.Space.l) {
            if (it == null) { EmptyState("Task deleted"); return@ScrollPage }
            Column(verticalArrangement = Arrangement.spacedBy(Theme.Space.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Theme.Space.s), verticalAlignment = Alignment.CenterVertically) {
                    TypeTag(it.type)
                    Text(model.course(it.sectionId)?.shortTitle ?: "My task", style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.secondary)
                }
                Text(it.title, style = Theme.title, color = C.text)
                Text(Fmt.due(it.due, Date()), style = Theme.subhead, color = C.secondary)
            }
            ProgressChips(model.progress(it), it.isExam) { p -> model.setProgress(it, p) }
            it.notes?.let { n -> Card(padding = Theme.Space.l) { Text(n, style = Theme.body, color = C.text) } }
            Text("Your own task: only on this phone, never on Schoology.", style = Theme.footnote, color = C.tertiary)
        }
    }
}
