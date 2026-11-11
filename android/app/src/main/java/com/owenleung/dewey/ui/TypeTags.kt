package com.owenleung.dewey.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owenleung.dewey.store.ItemType
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import kotlinx.coroutines.launch

/** The type's name, read aloud. */
val ItemType.spoken: String
    get() = when (this) {
        ItemType.ASSIGNMENT -> "Homework"
        ItemType.EXAM -> "Test"
        ItemType.CLASSWORK -> "Classwork"
        ItemType.TASK -> "Task"
    }

/**
 * The four types as one outlined track (HW · TEST · CW · TASK, caps like the tags).
 * The picked one is filled exactly like its tag elsewhere (HW/CW/TASK soft grey, TEST
 * solid ink) and slides over when you pick another; a selection tick, no slide with
 * animations off. Dewey guesses the type and can be wrong, so all four show and one tap fixes it.
 */
@Composable
fun TypeTags(current: ItemType, modifier: Modifier = Modifier, pick: (ItemType) -> Unit) {
    val still = reduceMotion()
    val haptic = LocalHapticFeedback.current
    // Each tag's left edge and width in the track, once laid out.
    val bounds = remember { mutableStateMapOf<ItemType, Pair<Float, Float>>() }
    val left = remember { Animatable(-1f) }
    val width = remember { Animatable(0f) }
    val target = bounds[current]
    LaunchedEffect(target) {
        val (x, w) = target ?: return@LaunchedEffect
        // The first time (and with animations off) it's just there; after, it slides.
        if (left.value < 0f || still) { left.snapTo(x); width.snapTo(w); return@LaunchedEffect }
        launch { left.animateTo(x, tween(220)) }
        width.animateTo(w, tween(220))
    }
    val fill by animateColorAsState(if (current == ItemType.EXAM) C.text else C.tagSoft, if (still) snap() else tween(220), label = "typeFill")
    val radius = Theme.Radius.tag
    Row(
        modifier.border(1.dp, C.divider, RoundedCornerShape(radius + 2.dp)).padding(2.dp)
            .drawBehind {
                if (left.value >= 0f) drawRoundRect(fill, topLeft = Offset(left.value, 0f), size = Size(width.value, size.height),
                    cornerRadius = CornerRadius(radius.toPx()))
            }
            .semantics { contentDescription = "Type" },
    ) {
        for (t in ItemType.entries) {
            val on = t == current
            Text(
                t.label,
                style = Theme.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
                color = if (on) (if (t == ItemType.EXAM) C.card else C.text) else C.secondary,
                maxLines = 1,
                modifier = Modifier
                    .onPlaced { c -> bounds[t] = c.positionInParent().x to c.size.width.toFloat() }
                    .clip(RoundedCornerShape(radius))
                    .clickable(role = Role.RadioButton) {
                        if (!on) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        pick(t)
                    }
                    .semantics { contentDescription = t.spoken; selected = on }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}
