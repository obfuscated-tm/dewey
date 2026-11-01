package com.owenleung.dewey.ui.tour

import androidx.compose.foundation.border
import kotlinx.coroutines.launch
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.R
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.reduceMotion
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The guided practice (ios/App/Screens/Tour/Coach.swift): Dewey and a spotlight
 * over the real screens. A dark layer covers everything but a hole around the
 * one thing to do next; taps outside the hole are swallowed. Screens say where
 * things are with `Modifier.coachTarget` (practice only); a step ends when the
 * practice model shows it happened. If the target goes missing (another tab, a
 * page pushed over it, scrolled away), the coach is "lost": it stops blocking
 * and offers "Take me there", which opens the step's tab fresh and rewinds to
 * the step that leads back, so a wrong tap never strands anyone.
 */
object Coach {
    data class Step(
        /** The `coachTarget` key to spotlight; null = no hole, the bubble in the middle. */
        val target: String? = null,
        val text: String,
        val hint: Hint = Hint.TAP,
        /** A button in the bubble moves on (and the hole takes no taps). */
        val button: String? = null,
        /** What the button does before moving on (asking for a permission, saving a choice). */
        val action: (suspend () -> Unit)? = null,
        /** A second, plain button that moves on without `action` ("Not now"). */
        val other: String? = null,
        /** Moves on by itself once this is true. */
        val done: ((AppModel) -> Boolean)? = null,
        /** The hole spans the screen's width: a list row. */
        val wide: Boolean = false,
        /** No dimming and every tap goes through: for a press-and-hold menu or a back arrow, which draw outside any hole. */
        val free: Boolean = false,
        /** The tab the target lives on, for "Take me there". */
        val tab: Int? = null,
        /** How many steps back "Take me there" goes: to the step that opens this one's screen. */
        val rewind: Int = 0,
    )

    enum class Hint { TAP, NONE }

    /** Where each `coachTarget` is, in root pixels. */
    val frames = mutableStateMapOf<String, Rect>()
    internal val owners = HashMap<String, Any>()
    /** Things a screen says happened that the model doesn't hold (`note`), e.g. a what-if score typed. */
    val flags = mutableStateListOf<String>()

    var steps by mutableStateOf<List<Step>>(emptyList()); private set
    var index by mutableIntStateOf(0); private set
    var running by mutableStateOf(false); private set
    /** A step was just done: Dewey cheers before the next one. */
    var cheering by mutableStateOf(false)
    /** The step's target has been off screen for a moment: nothing is blocked, and the bubble offers a way back. */
    var lost by mutableStateOf(false)
    /** Bumped by "Take me there": the practice opens `jumpTab` fresh. */
    var jump by mutableIntStateOf(0); private set
    var jumpTab = 0; private set
    private var onFinish: (() -> Unit)? = null

    val step: Step? get() = if (running && index < steps.size) steps[index] else null

    fun seen(key: String): Boolean = frames[key]?.let { it.width > 0 && it.height > 0 } == true

    fun start(steps: List<Step>, from: Int = 0, onFinish: () -> Unit) {
        this.steps = steps
        this.onFinish = onFinish
        index = from.coerceIn(0, steps.size - 1)
        cheering = false
        lost = false
        flags.clear()
        running = true
    }

    /** A screen reports something the steps wait for. */
    fun note(flag: String) {
        if (running && flag !in flags) flags += flag
    }

    private val scope = kotlinx.coroutines.MainScope()

    /** The bubble's button: its action first (a permission prompt waits for the answer). */
    fun next() {
        val s = step ?: return
        if (s.button == null) return
        val action = s.action ?: return move()
        val at = index
        scope.launch {
            action()
            if (running && index == at) move()
        }
    }

    /** The bubble's second button: on without the action. */
    fun pass() { if (step?.other != null) move() }

    fun move() {
        index += 1
        lost = false
        if (index >= steps.size) finish()
    }

    /** "Take me there": back to the step that opens this screen, on its tab, started fresh. */
    fun recover() {
        val s = step ?: return
        val t = s.tab ?: return
        index = maxOf(0, index - s.rewind)
        flags.clear()
        lost = false
        jumpTab = t
        jump += 1
    }

    fun finish() {
        if (!running) return
        running = false
        frames.clear()
        owners.clear()
        flags.clear()
        lost = false
        cheering = false
        val f = onFinish
        onFinish = null
        f?.invoke()
    }
}

/** Where the tour's spotlight can point. Does nothing outside practice. */
fun Modifier.coachTarget(key: String): Modifier = composed {
    if (!model.practice) return@composed this
    // Which composable set the frame: a list that rebuilds its rows (Plan, after a drop) shows the
    // new row before the old one goes, and the old one's leaving mustn't clear the new one's frame.
    val id = remember { Any() }
    DisposableEffect(key) { onDispose { if (Coach.owners[key] === id) { Coach.frames.remove(key); Coach.owners.remove(key) } } }
    onGloballyPositioned { Coach.frames[key] = it.boundsInRoot(); Coach.owners[key] = id }
}

/** Swallows every touch on it. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
}

/** The spotlight, the hint ring, the blockers and the bubble, over the practice's screens. */
@Composable
fun CoachOverlay(model: AppModel) {
    val step = Coach.step ?: return
    val d = LocalDensity.current
    val still = reduceMotion()
    val ctx = LocalContext.current
    // With TalkBack on, nothing is blocked (the bubble still says what to do).
    val talkBack = remember { (ctx.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val target = step.target
        val f = target?.let { Coach.frames[it] }?.takeIf { it.width > 0 }
        val hole: Rect? = f?.let {
            with(d) {
                if (step.wide) Rect(8.dp.toPx(), it.top - 6.dp.toPx(), w - 8.dp.toPx(), it.bottom + 6.dp.toPx())
                else Rect(it.left - 8.dp.toPx(), it.top - 6.dp.toPx(), it.right + 8.dp.toPx(), it.bottom + 6.dp.toPx())
            }
        }
        val onScreen = hole != null && hole.center.x in 0f..w && hole.center.y in 0f..h

        // A missing target (after 1.5 s, so screens can slide in first) makes the coach lost.
        LaunchedEffect(Coach.index, onScreen, target) {
            if (target != null && step.tab != null && !onScreen) { delay(1500); Coach.lost = true } else Coach.lost = false
        }
        // Done: Dewey cheers, then the next step.
        // Polled: some of what the steps wait for (the item store, Practice) isn't snapshot state.
        LaunchedEffect(Coach.index, Coach.jump) {
            val done = step.done ?: return@LaunchedEffect
            while (!done(model)) delay(150)
            Coach.cheering = true; delay(900); Coach.cheering = false; Coach.move()
        }

        val lost = Coach.lost
        val open = step.free || lost || talkBack
        val spring = spring<Float>(stiffness = 300f)
        val l by animateFloatAsState(hole?.left ?: w / 2, if (still) tween(0) else spring, label = "l")
        val t by animateFloatAsState(hole?.top ?: h / 2, if (still) tween(0) else spring, label = "t")
        val r by animateFloatAsState(hole?.right ?: w / 2, if (still) tween(0) else spring, label = "r")
        val b by animateFloatAsState(hole?.bottom ?: h / 2, if (still) tween(0) else spring, label = "b")
        val corner = with(d) { Theme.Radius.card.toPx() }
        val accent = C.accent

        if (!step.free && !lost) Canvas(Modifier.fillMaxSize()) {
            val p = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                if (hole != null) addRoundRect(RoundRect(Rect(l, t, r, b), CornerRadius(corner)))
            }
            drawPath(p, Color.Black.copy(alpha = 0.55f))
        }
        // The pulsing ring for a tap.
        if (hole != null && step.button == null && step.hint != Coach.Hint.NONE && !lost) {
            val pulse = if (still) 0f else rememberInfiniteTransition(label = "pulse")
                .animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p").value
            Canvas(Modifier.fillMaxSize()) {
                val grow = 1 + 0.04f * pulse
                val cx = (l + r) / 2; val cy = (t + b) / 2
                val hw = (r - l) / 2 * grow; val hh = (b - t) / 2 * grow
                drawRoundRect(accent.copy(alpha = 1 - 0.6f * pulse), Offset(cx - hw, cy - hh), Size(hw * 2, hh * 2), CornerRadius(corner), style = Stroke(2.5.dp.toPx()))
            }
        }
        // Taps: inside the hole go through; everything else is swallowed (none on a free or lost step).
        if (!open) {
            if (hole == null || step.button != null) Box(Modifier.fillMaxSize().blockTouches())
            else with(d) {
                Box(Modifier.offset { IntOffset(0, 0) }.size(w.toDp(), hole.top.coerceAtLeast(0f).toDp()).blockTouches())
                Box(Modifier.offset { IntOffset(0, hole.bottom.roundToInt()) }.size(w.toDp(), (h - hole.bottom).coerceAtLeast(0f).toDp()).blockTouches())
                Box(Modifier.offset { IntOffset(0, hole.top.roundToInt()) }.size(hole.left.coerceAtLeast(0f).toDp(), hole.height.toDp()).blockTouches())
                Box(Modifier.offset { IntOffset(hole.right.roundToInt(), hole.top.roundToInt()) }.size((w - hole.right).coerceAtLeast(0f).toDp(), hole.height.toDp()).blockTouches())
            }
        }

        // The bubble: out of the way above the tab bar on a free or lost step, else above or below the hole.
        val bubble: @Composable () -> Unit = { Bubble(step, Coach.cheering, lost, still) }
        with(d) {
            when {
                open || hole == null && step.free -> Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 110.dp), contentAlignment = Alignment.BottomCenter) { bubble() }
                hole == null -> Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { bubble() }
                hole.center.y > h * 0.5f -> Box(Modifier.fillMaxWidth().height((hole.top - 10.dp.toPx()).coerceAtLeast(0f).toDp()).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.BottomCenter) { bubble() }
                else -> Box(Modifier.offset { IntOffset(0, (hole.bottom + 10.dp.toPx()).roundToInt()) }.fillMaxWidth().padding(horizontal = 16.dp),
                    contentAlignment = Alignment.TopCenter) { bubble() }
            }
        }
    }
}

/** Dewey and what he says. He stays still; the text changes when a step is done. */
@Composable
private fun Bubble(step: Coach.Step, cheering: Boolean, lost: Boolean, still: Boolean) {
    Row(
        Modifier.widthIn(max = 520.dp).fillMaxWidth().shadow(12.dp, RoundedCornerShape(Theme.Radius.card)).clip(RoundedCornerShape(Theme.Radius.card))
            .background(C.card).blockTouches().padding(Theme.Space.m),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Theme.Space.m),
    ) {
        Image(painterResource(R.drawable.dewey_hello), null, Modifier.size(52.dp))
        Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
            if (cheering) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, null, tint = C.good, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Done", style = Theme.headline, color = C.good)
            } else Text(step.text, style = Theme.subhead, color = C.text)
            if (lost) {
                Text("Lost your place? I'll take you back.", style = Theme.footnote, color = C.secondary)
                CoachButton("Take me there") { Coach.recover() }
            } else if (step.button != null && !cheering) Row(horizontalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
                CoachButton(step.button) { Coach.next() }
                step.other?.let { o ->
                    Text(o, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text,
                        modifier = Modifier.clip(CircleShape).border(1.dp, C.border, CircleShape).clickable { Coach.pass() }.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
        // Skip lives in the bubble, so it never covers a target.
        Box(Modifier.heightIn(min = 32.dp).widthIn(min = 44.dp).clip(RoundedCornerShape(Theme.Radius.tag)).clickable(onClickLabel = "Skip tour") { Coach.finish() },
            contentAlignment = Alignment.Center) {
            Text("Skip", style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.secondary)
        }
    }
}

@Composable
private fun CoachButton(title: String, onClick: () -> Unit) {
    Text(title, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.onAccent,
        modifier = Modifier.clip(CircleShape).background(C.accent).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp))
}

