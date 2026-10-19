package com.owenleung.dewey.ui.classes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.engine.CourseGrade
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.ItemState
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.store.ShelfLook
import com.owenleung.dewey.store.TodoLogic
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.classColor
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.spineColor
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.ui.tour.coachTarget
import java.util.Date

/** The tag on a book's top edge. */
enum class Due(val label: String, val spoken: String) {
    LATE("LATE", "work late"), TODAY("TODAY", "work due today"), TOMORROW("TMRW", "work due tomorrow")
}

/**
 * The top of Classes (ios/App/Screens/Classes/Shelf.swift): each class is a book
 * on a shelf, in its own colour, its name up the spine and a call-number label
 * with the period and the letter. A bookmark ribbon says when work is late, due
 * today or due tomorrow. Classes taken off the shelf (press and hold, or Settings →
 * Classes) lie flat under it; at the largest text sizes every class lies flat.
 */
@Composable
fun Shelf() {
    val model = model
    Prefs.revision.intValue
    val off = ShelfLook.off()
    val flatAll = LocalDensity.current.fontScale >= 1.6f
    val standing = if (flatAll) emptyList() else model.classes.filter { it.sectionId !in off }
    val lying = if (flatAll) model.classes else model.classes.filter { it.sectionId in off }
    val now = Date()
    val open = model.classes.flatMap { model.openWork(it.sectionId, now) }
    // The letters on the labels (cached gradebooks; Grades refreshes them).
    LaunchedEffect(model.classes) { model.twoAtATime(model.classes) { model.loadGrades(it.sectionId) } }
    Column(verticalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
        if (standing.isNotEmpty()) Column(Modifier.padding(top = Theme.Space.l)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Theme.Space.xs), horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom) {
                for (c in standing) key(c.sectionId) {
                    BookSpine(c, due(c, open, now), open.count { it.sectionId == c.sectionId }, Modifier.weight(1f))
                }
            }
            // The plank.
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(2.dp)).background(C.border))
        }
        for (c in lying) key(c.sectionId) {
            FlatBook(c, due(c, open, now), open.count { it.sectionId == c.sectionId }, canPutBack = !flatAll,
                Modifier.padding(top = if (standing.isEmpty()) 0.dp else Theme.Space.xs))
        }
    }
}

private fun due(c: Course, open: List<ItemState>, now: Date): Due? {
    val mine = open.filter { it.sectionId == c.sectionId }
    // openWork keeps past-due rows only when they're really late (AppModel.isOverdue).
    if (mine.any { it.due?.before(now) == true }) return Due.LATE
    if (mine.any { TodoLogic.bucket(it.due, now) == TodoLogic.Bucket.TODAY }) return Due.TODAY
    val tomorrow = Fmt.addDays(now, 1)
    if (mine.any { d -> d.due?.let { Fmt.sameDay(it, tomorrow) } == true }) return Due.TOMORROW
    return null
}

private fun classLabel(c: Course, g: CourseGrade?, due: Due?, open: Int) =
    listOfNotNull(c.shortTitle, c.periodLabel, g?.letter?.let { "grade $it" }, due?.spoken, if (open > 0) "$open open" else null).joinToString(", ")

/** Text turned to run up the spine: measured unbounded, then laid out with width and height swapped. */
private fun Modifier.upright(): Modifier = rotate(-90f).layout { m, c ->
    val p = m.measure(Constraints(maxWidth = if (c.hasBoundedHeight) c.maxHeight else Constraints.Infinity))
    layout(p.height, p.width) { p.place(-(p.width - p.height) / 2, (p.width - p.height) / 2) }
}

/** One book standing up. Heights differ a little per class (stable, meaningless), like a real shelf. */
@Composable
private fun BookSpine(course: Course, due: Due?, openCount: Int, modifier: Modifier) {
    val model = model
    val nav = nav
    var menu by remember { mutableStateOf(false) }
    val g = model.grades[course.sectionId]
    val height = height(course.sectionId)
    val shape = RoundedCornerShape(4.dp)
    Box(modifier) {
        Box(
            Modifier.fillMaxWidth().height(height.dp).coachTarget("shelf.book.${course.sectionId}")
                .clip(shape).background(spineColor(course.sectionId))
                .combinedClickable(onClick = { nav.push(Route.Materials(course.sectionId, null, course.shortTitle, emptyList())) },
                    onLongClick = { menu = true })
                .clearAndSetSemantics {
                    contentDescription = classLabel(course, g, due, openCount)
                    onClick("Opens this class's materials") { nav.push(Route.Materials(course.sectionId, null, course.shortTitle, emptyList())); true }
                    onLongClick("Take off the shelf") { menu = true; true }
                },
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(top = 14.dp).height((height - 58 - 28).dp), contentAlignment = Alignment.Center) {
                    Text(course.shortTitle, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.upright())
                }
                Spacer(Modifier.weight(1f, fill = true))
            }
            // The call-number label: white and ink in both modes, like a printed sticker.
            Column(Modifier.align(Alignment.BottomCenter).padding(start = 5.dp, end = 5.dp, bottom = 8.dp).fillMaxWidth()
                .heightIn(min = 44.dp).clip(RoundedCornerShape(3.dp)).background(Color.White),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(course.periodLabel.takeIf { it.length <= 3 } ?: "", style = Theme.caption.copy(fontWeight = FontWeight.Bold), color = LABEL_INK, maxLines = 1)
                Text(g?.letter ?: "–", style = Theme.displaySmall, color = LABEL_INK, maxLines = 1)
            }
            if (due != null) DueRibbon(due, Modifier.align(Alignment.TopEnd).padding(end = 3.dp))
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Take off the shelf") }, leadingIcon = { Icon(Icons.Outlined.Download, null) },
                onClick = { menu = false; ShelfLook.toggle(course.sectionId) })
        }
    }
}

private val LABEL_INK = Color(0xFF15191D)

/** 200–236 dp, from the section id. */
private fun height(id: String) = 200 + (id.sumOf { it.code } % 5) * 9

/** A ribbon's outline: square top, a V notch cut into the bottom end. */
private class RibbonShape(val notch: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val n = notch * density.density
        return Outline.Generic(Path().apply {
            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height)
            lineTo(size.width / 2, size.height - n); lineTo(0f, size.height); close()
        })
    }
}

/** A bookmark ribbon hanging from the top of a standing book, words running up it: filled for late and today, outlined for tomorrow. */
@Composable
private fun DueRibbon(due: Due, modifier: Modifier = Modifier) {
    val color = if (due == Due.LATE) C.late else C.accent
    val shape = RibbonShape(6f)
    val outlined = due == Due.TOMORROW
    Box(modifier.width(13.dp).height(55.dp).clip(shape).background(if (outlined) C.card else color)
        // A white edge, so the ribbon reads on any spine colour; tomorrow's is outlined in its own colour.
        .border(1.5.dp, if (outlined) color else Color.White, shape).padding(top = 3.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center) {
        Text(due.label, style = Theme.ribbon, maxLines = 1, softWrap = false,
            color = if (outlined) color else if (due == Due.LATE) C.card else C.onAccent, modifier = Modifier.upright())
    }
}

/** The LATE / TODAY / TMRW tag on a book lying flat. */
@Composable
private fun DueTag(due: Due) {
    val color = if (due == Due.LATE) C.late else C.accent
    val outlined = due == Due.TOMORROW
    val shape = RoundedCornerShape(3.dp)
    Text(due.label, style = Theme.caption.copy(fontWeight = FontWeight.Black, letterSpacing = 0.4.sp), maxLines = 1,
        color = if (outlined) color else if (due == Due.LATE) C.card else C.onAccent,
        modifier = Modifier.clip(shape).background(if (outlined) C.card else color).border(1.5.dp, color, shape).padding(horizontal = 5.dp, vertical = 2.dp))
}

/** A book lying flat: a class taken off the shelf, or every class at the largest text sizes. */
@Composable
private fun FlatBook(course: Course, due: Due?, openCount: Int, canPutBack: Boolean, modifier: Modifier) {
    val model = model
    val nav = nav
    var menu by remember { mutableStateOf(false) }
    val g = model.grades[course.sectionId]
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).coachTarget("shelf.flat.${course.sectionId}").coachTarget("shelf.flat.any")
                .clip(RoundedCornerShape(Theme.Radius.tag)).background(C.card)
                .combinedClickable(onClick = { nav.push(Route.Materials(course.sectionId, null, course.shortTitle, emptyList())) },
                    onLongClick = { if (canPutBack) menu = true })
                .clearAndSetSemantics {
                    contentDescription = classLabel(course, g, due, openCount)
                    onClick("Opens this class's materials") { nav.push(Route.Materials(course.sectionId, null, course.shortTitle, emptyList())); true }
                    if (canPutBack) onLongClick("Put on the shelf") { menu = true; true }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(6.dp).height(52.dp).background(classColor(course.sectionId)))
            Spacer(Modifier.width(Theme.Space.m))
            Row(Modifier.weight(1f).padding(vertical = Theme.Space.s).padding(end = Theme.Space.m), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
                Text(course.shortTitle, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(course.periodLabel, style = Theme.footnote, color = C.tertiary, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (due != null) DueTag(due)
                if (openCount > 0) Text("$openCount open", style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.accent, maxLines = 1)
                Text(g?.letter ?: "–", style = Theme.displaySmall, color = C.text, maxLines = 1)
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Put on the shelf") }, leadingIcon = { Icon(Icons.Outlined.Upload, null) },
                onClick = { menu = false; ShelfLook.toggle(course.sectionId) })
        }
    }
}
