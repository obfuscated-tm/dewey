package com.owenleung.dewey.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import kotlin.math.min

/**
 * The drop from Dewey's logo, point up (ios/App/Screens/Shared/Drop.swift): the
 * selected day in the week strip and the centre of the ripple. Fills its box;
 * the round part sits at the bottom.
 */
object DropShape : Shape {
    fun path(size: Size): Path {
        val r = min(size.width / 2, size.height * 0.38f)
        val cx = size.width / 2
        val cy = size.height - r
        return Path().apply {
            moveTo(cx, 0f)
            cubicTo(cx + r * 0.35f, cy * 0.35f, cx + r, cy - r * 0.75f, cx + r, cy)
            arcTo(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r), 0f, 180f, false)
            cubicTo(cx - r, cy - r * 0.75f, cx - r * 0.35f, cy * 0.35f, cx, 0f)
            close()
        }
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) = Outline.Generic(path(size))
}

/** Android's Reduce Motion: "Remove animations" sets the animator scale to 0. */
@Composable
fun reduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * Dewey's one signature motion: when a checkbox turns on (Done, or Studied for a
 * test), two thin accent rings spread out from it, like the ripples around the
 * logo's drop. About 420 ms, only on marking done (never on undo or when a list
 * first draws), and not at all with animations off. Drawn behind, not clipped.
 */
fun Modifier.doneRipple(isOn: Boolean, color: Color): Modifier = composed {
    val p = remember { Animatable(1f) }
    val was = remember { booleanArrayOf(isOn) }
    val still = reduceMotion()
    LaunchedEffect(isOn) {
        val before = was[0]
        was[0] = isOn
        if (isOn && !before && !still) {
            p.snapTo(0f)
            p.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }
    drawBehind {
        val t = p.value
        if (t >= 1f) return@drawBehind
        val base = size.minDimension / 2
        val inner = maxOf(0f, (t - 0.22f) / 0.78f)
        fun ring(scale: Float, alpha: Float, width: Float) {
            if (alpha <= 0f) return
            drawCircle(color.copy(alpha = alpha), radius = base * scale, style = Stroke(width.dp.toPx()))
        }
        ring(1 + 1.15f * t, 0.55f * (1 - t), 2 - t)
        ring(1 + 0.7f * inner, 0.4f * (1 - inner), 1.6f - inner * 0.8f)
    }
}

/** "1 of 3 done today": a drop with a ripple ring per thing due today (at most four), filling from the centre out. */
@Composable
fun RippleProgress(done: Int, total: Int, size: Dp = 40.dp) {
    val rings = min(total, 4)
    val filled = if (done >= total) rings else (done.toDouble() / maxOf(total, 1) * rings).toInt()
    val accent = C.accent
    val divider = C.divider
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.Space.s),
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$done of $total done today" }) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val w = 2.5.dp.toPx()
                for (i in 0 until rings) {
                    val d = this.size.width * (0.42f + 0.58f * (i + 1) / maxOf(rings, 1))
                    drawCircle(if (i < filled) accent else divider, radius = (d - w) / 2, style = Stroke(w))
                }
                val dw = this.size.width * 0.2f
                val dh = this.size.width * 0.26f
                translate(this.size.width / 2 - dw / 2, this.size.height / 2 - dh / 2) {
                    drawPath(DropShape.path(Size(dw, dh)), if (done >= total) accent else accent.copy(alpha = 0.75f))
                }
            }
        }
        Column {
            Text("$done of $total", style = Theme.footnote.copy(fontWeight = FontWeight.Bold), color = C.text, maxLines = 1)
            Text("done today", style = Theme.footnote, color = C.secondary, maxLines = 1)
        }
    }
}

/** The tour's welcome art: ripples round the drop. */
@Composable
fun DropRipples(modifier: Modifier = Modifier) {
    val accent = C.accent
    Canvas(modifier.size(170.dp)) {
        for (i in 0 until 4) {
            val d = (60 + i * 34).dp.toPx()
            drawCircle(accent.copy(alpha = 0.25f + 0.18f * (3 - i)), radius = d / 2, style = Stroke(2.5.dp.toPx()))
        }
        val dw = 30.dp.toPx(); val dh = 40.dp.toPx()
        translate(center.x - dw / 2, center.y - dh / 2) { drawPath(DropShape.path(Size(dw, dh)), accent) }
    }
}

