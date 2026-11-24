package com.owenleung.dewey.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.store.BellSchedule
import com.owenleung.dewey.store.BellStore
import com.owenleung.dewey.store.BellTemplate
import com.owenleung.dewey.ui.DetailScaffold
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.widget.PeriodWidget
import java.util.Date

/**
 * Settings → Schedule (ios/App/Screens/Settings/ScheduleSettings.swift): the bell schedule behind the
 * "Now in class" widget (BellSchedule). Day types the student makes and names, which type each weekday
 * uses, and what each period is (class, room, teacher). Nothing is pre-made; everything is looked up by
 * id, so a day type deleted while it's open just isn't there any more. Its own pages (a day type, a
 * period, the school list) are a small stack inside this one Settings page.
 */
private sealed interface SchedulePage {
    data class Day(val id: String) : SchedulePage
    data class Period(val name: String) : SchedulePage
    data object Schools : SchedulePage
}

/** Monday to Friday, as calendar weekdays (1 = Sunday). */
private val weekdays = listOf(2 to "Monday", 3 to "Tuesday", 4 to "Wednesday", 5 to "Thursday", 6 to "Friday")

private fun title(t: BellSchedule.DayType) = t.name.ifEmpty { "Day type" }

@Composable
fun ScheduleSettings() {
    val model = model
    val ctx = LocalContext.current
    var schedule by remember { mutableStateOf(BellStore.load()) }
    var page by remember { mutableStateOf<SchedulePage?>(null) }
    fun edit(new: BellSchedule) {
        if (new == schedule) return
        schedule = new
        BellStore.save(new)
        PeriodWidget.refresh(ctx)
    }
    // A period nobody has described yet takes its class from Schoology.
    LaunchedEffect(model.classes) { edit(schedule.filled(model.classes)) }
    BackHandler(page != null) { page = null }
    val back = { page = null }
    when (val p = page) {
        is SchedulePage.Day -> DayTypeEditor(schedule, p.id, model.classes, ::edit, back)
        is SchedulePage.Period -> PeriodEditor(p.name, schedule, model.classes, ::edit, back)
        SchedulePage.Schools -> SchoolPicker(replacing = schedule.dayTypes.isNotEmpty(), onBack = back) { t ->
            // The school's day types and week; the student's classes, rooms and teachers stay.
            edit(t.schedule.copy(periods = schedule.periods).filled(model.classes))
            page = null
        }
        null -> ScheduleMain(schedule, ::edit) { page = it }
    }
}

@Composable
private fun ScheduleMain(schedule: BellSchedule, edit: (BellSchedule) -> Unit, open: (SchedulePage) -> Unit) {
    DetailScaffold("Schedule") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            if (!schedule.isEmpty) Section(null) { TodayRow(schedule, edit) }
            Section("Day types") {
                schedule.dayTypes.forEachIndexed { i, t ->
                    LinkLine(title(t), BellSchedule.summary(schedule.slots(t.id)), divider = i > 0) { open(SchedulePage.Day(t.id)) }
                }
                Action("Add a day type", divider = schedule.dayTypes.isNotEmpty()) {
                    val id = BellSchedule.newId()
                    edit(schedule.addingDayType(weekdays.map { it.first }, id))
                    open(SchedulePage.Day(id))
                }
                LinkLine("Use my school's schedule") { open(SchedulePage.Schools) }
            }
            if (schedule.dayTypes.isNotEmpty()) Section("Week") {
                weekdays.forEachIndexed { i, (day, name) ->
                    Choice(name, schedule.dayTypes.firstOrNull { it.id == schedule.week[day] }?.let(::title) ?: "No school", divider = i > 0,
                        options = listOf<Pair<String, String?>>("No school" to null) + schedule.dayTypes.map { title(it) to it.id },
                        selected = schedule.week[day]) { id ->
                        edit(schedule.copy(week = if (id == null) schedule.week - day else schedule.week + (day to id)))
                    }
                }
            }
            if (schedule.periodNames.isNotEmpty()) Section("Periods") {
                schedule.periodNames.forEachIndexed { i, name ->
                    val p = schedule.periods[name] ?: BellSchedule.Period()
                    LinkLine(name, listOf(p.title, p.room).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }, divider = i > 0) {
                        open(SchedulePage.Period(name))
                    }
                }
            }
        }
    }
}

/** Today: the weekday's usual day type, another one, or No school, for today only. */
@Composable
private fun TodayRow(schedule: BellSchedule, edit: (BellSchedule) -> Unit) {
    val now = Date()
    val key = BellSchedule.dayKey(now)
    val usualId = schedule.week[BellSchedule.weekday(java.time.LocalDate.now())]
    val usualName = schedule.dayTypes.firstOrNull { it.id == usualId }?.let(::title) ?: "No school"
    val o = schedule.today?.takeIf { it.day == key }
    // "usual" stands for no switch at all.
    val current = when { o == null -> "usual"; o.dayType == null -> "none"; else -> o.dayType }
    val options = buildList {
        add(usualName to "usual")
        schedule.dayTypes.filter { it.id != usualId }.forEach { add(title(it) to it.id) }
        if (usualId != null) add("No school" to "none")
    }
    Choice("Today", options.firstOrNull { it.second == current }?.first ?: usualName, divider = false, options = options, selected = current) { v ->
        edit(schedule.copy(today = when (v) {
            "usual" -> null
            "none" -> BellSchedule.TodayOverride(key, null)
            else -> BellSchedule.TodayOverride(key, v)
        }))
    }
}

/** A label and its value; tapping it opens a menu of the choices. */
@Composable
private fun <T> Choice(label: String, value: String, divider: Boolean, options: List<Pair<String, T>>, selected: T, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    RowLine(divider) {
        Box {
            Row(Modifier.fillMaxWidth().clickable { open = true }) {
                Text(label, style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
                Text(value, style = Theme.body, color = C.accent, maxLines = 1)
            }
            DropdownMenu(open, { open = false }) {
                for ((text, v) in options) DropdownMenuItem(text = { Text(text) }, leadingIcon = { if (v == selected) Icon(Icons.Outlined.Check, null) },
                    onClick = { open = false; onPick(v) })
            }
        }
    }
}

/** A typed line with grey placeholder text, like a form row on iOS. */
@Composable
private fun Field(placeholder: String, value: String, divider: Boolean = true, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    RowLine(divider) { InlineField(placeholder, value, modifier.fillMaxWidth(), onChange) }
}

@Composable
private fun InlineField(placeholder: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    BasicTextField(value, onChange, singleLine = true, textStyle = Theme.body.copy(color = C.text), cursorBrush = SolidColor(C.text),
        modifier = modifier.semantics { contentDescription = placeholder },
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, style = Theme.body, color = C.tertiary); inner() } })
}

/** A clock time, picked on a dial. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(label: String, minutes: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(BellSchedule.clock(minutes), style = Theme.body, color = C.accent,
        modifier = Modifier.clickable { open = true }.padding(horizontal = 6.dp, vertical = 8.dp).semantics { contentDescription = "$label ${BellSchedule.clock(minutes)}" })
    if (open) {
        val state = rememberTimePickerState(initialHour = (minutes / 60).coerceIn(0, 23), initialMinute = minutes % 60, is24Hour = false)
        AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = { TimePicker(state) },
            confirmButton = { TextButton({ onPick(state.hour * 60 + state.minute); open = false }) { Text("Done") } },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } })
    }
}

/** One day type: its name and its periods in time order, each a name and two times. */
@Composable
private fun DayTypeEditor(schedule: BellSchedule, id: String, classes: List<Course>, edit: (BellSchedule) -> Unit, onBack: () -> Unit) {
    val type = schedule.dayTypes.firstOrNull { it.id == id }
    val slots = schedule.slots(id)
    val classPeriods = BellSchedule.classPeriods(classes)
    DetailScaffold(type?.let(::title) ?: "", onBack = onBack) { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            if (type == null) return@ScrollPage
            Section(null) { Field("Name", type.name, divider = false) { edit(schedule.namingDayType(id, it)) } }
            val copyable = schedule.dayTypes.filter { it.id != id && it.slots.isNotEmpty() }
            if (slots.isEmpty() && (classPeriods.isNotEmpty() || copyable.isNotEmpty())) Section(null) {
                if (classPeriods.isNotEmpty()) Action("Add my classes", divider = false) {
                    // A period for every class Schoology names one for, back to back from 8:00, to adjust.
                    val new = mutableListOf<BellSchedule.Slot>()
                    for (name in classPeriods) new += BellSchedule.nextSlot(new, name)
                    edit(schedule.settingSlots(new, id).filled(classes))
                }
                copyable.forEachIndexed { i, other ->
                    Action("Same as ${title(other)}", divider = i > 0 || classPeriods.isNotEmpty()) {
                        edit(schedule.settingSlots(schedule.slots(other.id).map { BellSchedule.Slot(name = it.name, start = it.start, end = it.end) }, id))
                    }
                }
            }
            Section(null) {
                slots.forEachIndexed { i, slot ->
                    RowLine(i > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InlineField("Period name", slot.name, Modifier.widthIn(min = 56.dp).weight(1f)) { edit(schedule.renamingSlot(slot.id, it)) }
                            TimeButton("Starts", slot.start) { m -> edit(schedule.updatingSlot(slot.id) { it.copy(start = m) }) }
                            TimeButton("Ends", slot.end) { m -> edit(schedule.updatingSlot(slot.id) { it.copy(end = m) }) }
                            IconButton({ edit(schedule.removingSlot(slot.id)) }) {
                                Icon(Icons.Outlined.RemoveCircleOutline, "Delete ${slot.name}", tint = C.secondary)
                            }
                        }
                    }
                }
                Action("Add a period", divider = slots.isNotEmpty()) {
                    edit(schedule.settingSlots(slots + BellSchedule.nextSlot(slots, BellSchedule.nextName(slots)), id).filled(classes))
                }
                Action("Add lunch or a break") { edit(schedule.settingSlots(slots + BellSchedule.nextSlot(slots, "Lunch"), id).filled(classes)) }
            }
            Section(null) {
                Action("Delete this day type", divider = false, destructive = true) {
                    onBack()
                    edit(schedule.removingDayType(id))
                }
            }
        }
    }
}

/** What a period is: the class (one of the student's, or typed), the room, the teacher. */
@Composable
private fun PeriodEditor(name: String, schedule: BellSchedule, classes: List<Course>, edit: (BellSchedule) -> Unit, onBack: () -> Unit) {
    val period = schedule.periods[name] ?: BellSchedule.Period()
    fun set(p: BellSchedule.Period) = edit(schedule.settingPeriod(name, p))
    // "Something else" picked: the class is typed.
    var typing by remember { mutableStateOf(false) }
    val courseIndex = classes.indexOfFirst { it.shortTitle == period.title }
    val choice: Int? = when { typing -> -1; courseIndex >= 0 -> courseIndex; period.title.isEmpty() -> null; else -> -1 }
    DetailScaffold(name, onBack = onBack) { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            Section(null) {
                val options = listOf<Pair<String, Int?>>("None" to null) + classes.mapIndexed { i, c -> c.shortTitle to i } + ("Something else" to -1)
                Choice("Class", options.firstOrNull { it.second == choice }?.first ?: "None", divider = false, options = options, selected = choice) { v ->
                    when (v) {
                        null -> { typing = false; set(BellSchedule.Period(room = period.room)) }
                        -1 -> { typing = true; set(period.copy(color = null)) }
                        else -> { typing = false; set(BellSchedule.Period(classes[v].shortTitle, period.room, BellSchedule.teacher(classes[v].sectionTitle), v)) }
                    }
                }
                if (choice == -1) Field("Class name", period.title) { set(period.copy(title = it)) }
                Field("Room", period.room) { set(period.copy(room = it)) }
                Field("Teacher", period.teacher) { set(period.copy(teacher = it)) }
            }
            Section(null) {
                Action("Delete $name", divider = false, destructive = true) {
                    onBack()
                    edit(schedule.removingPeriod(name))
                }
            }
        }
    }
}

/**
 * Schools whose bell schedule Dewey knows, by district. Picking one replaces the day types and the week,
 * after asking when there are day types already.
 */
@Composable
private fun SchoolPicker(replacing: Boolean, onBack: () -> Unit, onPick: (BellTemplate) -> Unit) {
    var pending by remember { mutableStateOf<BellTemplate?>(null) }
    DetailScaffold("Schools", onBack = onBack) { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            for (district in BellTemplate.all.map { it.district }.distinct().sorted()) Section(district) {
                BellTemplate.all.filter { it.district == district }.forEachIndexed { i, t ->
                    RowLine(i > 0) {
                        Column(Modifier.fillMaxWidth().clickable { if (replacing) pending = t else onPick(t) }) {
                            Text(t.school, style = Theme.body, color = C.text)
                            Text("${t.year} · ${t.days.size} day types", style = Theme.subhead, color = C.secondary)
                        }
                    }
                }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
    pending?.let { t ->
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text("Replace your day types with ${t.school}'s?") },
            text = { Text("Your classes, rooms and teachers stay.") },
            confirmButton = { TextButton({ pending = null; onPick(t) }) { Text("Replace", color = C.late) } },
            dismissButton = { TextButton({ pending = null }) { Text("Cancel") } })
    }
}
