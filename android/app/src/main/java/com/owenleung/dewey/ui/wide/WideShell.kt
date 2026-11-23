package com.owenleung.dewey.ui.wide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.owenleung.dewey.R
import com.owenleung.dewey.ui.LocalNav
import com.owenleung.dewey.ui.Nav
import com.owenleung.dewey.ui.Overlays
import com.owenleung.dewey.ui.RouteScreen
import com.owenleung.dewey.ui.TabRoot
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.tabs
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.ui.tour.coachTarget
import com.owenleung.dewey.web.WebController

/**
 * A wide window (ios/App/Screens/Wide/WideShell.swift, docs/ios/design/DuoBoard.html): a rail
 * of the five tabs down the left and each tab the whole width. Nothing opens by itself: a tapped
 * row or card opens over the right half, from the middle of the window to its edge, and the list
 * or board stays live on the left. The X (or Back) closes it; each tab keeps its own.
 *
 * Chosen by the window's size, never by "is it a foldable": [Wide.isWide].
 */
object Wide {
    /**
     * Material's expanded width class (≥ 840 dp), Android's nearest match to iOS's regular width:
     * the iPhone Duo's open screen is 951 × 669 pt, a Pixel Fold's inner one about 841 × 701 dp.
     * The height floor keeps a phone held sideways (or a short split-screen window) on the phone layout.
     */
    val MIN_WIDTH = 840.dp
    val MIN_HEIGHT = 480.dp
    val RAIL = 72.dp

    fun isWide(width: Dp, height: Dp) = width >= MIN_WIDTH && height >= MIN_HEIGHT
}

/** True on the first page over the right half, whose top bar shows X (close) instead of Back. */
val LocalPaneRoot = compositionLocalOf { false }

/**
 * Given to a tab's own screen on a wide window (null on the phone and inside the right half).
 * `pane`: what each tab has open over its right half (a Nav whose stacks are those pages).
 */
class Split(val pane: Nav) {
    /** The width left of the middle, after the rail: what a tab keeps while an item is open. */
    var listWidth by mutableStateOf(400.dp)

    fun isOpen(tab: Int) = pane.stacks[tab].isNotEmpty()
    /** Side by side: a tab's second column shows (nothing open over it). */
    fun twoColumns(tab: Int) = !isOpen(tab)
    /** The item opened from the tab's own screen (the bottom of the right half's stack). */
    fun opened(tab: Int) = pane.stacks[tab].firstOrNull()
}

val LocalSplit = compositionLocalOf<Split?> { null }

@Composable
fun WideShell(schoologyWeb: WebController?) {
    val model = model
    val nav = nav
    val pane = remember(nav) { Nav(model) }
    val split = remember(pane) { Split(pane) }
    val holder = rememberSaveableStateHolder()

    // Turning wide: pages pushed on a tab (an item opened on the phone layout) move over the right
    // half. Turning narrow again: they go back on the tab's stack, so the student keeps their place.
    DisposableEffect(nav, pane) {
        for (t in 0 until 5) {
            val s = nav.stacks[t]
            val n = s.indexOfFirst { !it.opensInPane }.let { if (it < 0) s.size else it }
            if (n > 0) {
                pane.stacks[t].addAll(s.take(n))
                repeat(n) { s.removeAt(0) }
            }
        }
        nav.pane = pane
        onDispose {
            nav.pane = null
            for (t in 0 until 5) {
                val p = pane.stacks[t].toList()
                pane.stacks[t].clear()
                nav.stacks[t].addAll(0, p)
            }
            pane.sheet?.let { nav.sheet = it }
            pane.file?.let { nav.file = it }
            pane.whatIf?.let { nav.whatIf = it }
            pane.task?.let { nav.task = it }
        }
    }
    SideEffect { pane.tab = nav.tab }

    Box(Modifier.fillMaxSize().background(C.background)
        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
        Row(Modifier.fillMaxSize()) {
            Rail(nav)
            Box(Modifier.width(1.dp).fillMaxHeight().background(C.border))
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                // The middle of the window is the "hinge": the right half opens from there.
                val total = maxWidth + Wide.RAIL + 1.dp
                val paneWidth = total / 2
                SideEffect { split.listWidth = max(280.dp, total / 2 - Wide.RAIL - 1.dp) }
                val t = nav.tab
                val full = nav.stacks[t].lastOrNull()
                val open = pane.stacks[t].isNotEmpty() && full == null
                holder.SaveableStateProvider("wide:$t:${nav.stacks[t].size}:${full?.key ?: "root"}") {
                    if (full != null) RouteScreen(full)
                    else Box(Modifier.fillMaxSize().statusBarsPadding().padding(end = if (open) paneWidth else 0.dp)) {
                        CompositionLocalProvider(LocalSplit provides split) {
                            if (t == Nav.TODO) ToDoBoard(split) else TabRoot(t, schoologyWeb)
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(open, Modifier.align(Alignment.CenterEnd),
                    enter = slideInHorizontally(tween(280)) { it }, exit = slideOutHorizontally(tween(280)) { it }) {
                    Row(Modifier.width(paneWidth).fillMaxHeight().background(C.background)) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(C.border))
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            CompositionLocalProvider(LocalNav provides pane) {
                                val stack = pane.stacks[t]
                                stack.lastOrNull()?.let { r ->
                                    // A new item starts fresh; pages pushed inside it get the usual Back.
                                    key(t, stack.size, r.key) {
                                        CompositionLocalProvider(LocalPaneRoot provides (stack.size == 1)) { RouteScreen(r) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // Sheets opened from inside the right half.
    CompositionLocalProvider(LocalNav provides pane) { Overlays() }
}

/**
 * The five tabs as icons over their names, with the tab bar's badges, under the Dewey mark.
 * Nothing else: each screen has its own gear. Red means late: only To Do's count is red.
 */
@Composable
private fun Rail(nav: Nav) {
    val model = model
    Column(Modifier.width(Wide.RAIL).fillMaxHeight().background(C.tagSoft).statusBarsPadding().padding(vertical = Theme.Space.s),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Theme.Space.xs)) {
        Box(Modifier.size(52.dp, 48.dp).padding(bottom = Theme.Space.xs).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.dewey_mark), null, Modifier.size(30.dp))
        }
        tabs.forEachIndexed { i, t ->
            val on = nav.tab == i
            val badge = when (i) {
                Nav.TODO -> model.overdueCount()
                Nav.GRADES -> model.newGradeCount()
                Nav.INBOX -> model.inbox.newCount()
                else -> 0
            }
            // Like the tab bar: the rail keeps its size at large text.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density, density.fontScale.coerceAtMost(1.3f))) {
                Column(Modifier.widthIn(min = 68.dp).height(56.dp).coachTarget("tab.$i")
                    .clickable { if (on) nav.stacks[i].clear() else nav.tab = i }
                    .clearAndSetSemantics {
                        contentDescription = listOfNotNull(t.title, if (badge > 0) (if (i == Nav.TODO) "$badge late" else "$badge new") else null).joinToString(", ")
                        role = Role.Tab
                        selected = on
                    },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box {
                        Box(Modifier.size(48.dp, 30.dp).clip(CircleShape).background(if (on) C.accent else Color.Transparent),
                            contentAlignment = Alignment.Center) {
                            Icon(t.icon, null, tint = if (on) C.onAccent else C.accent, modifier = Modifier.size(22.dp))
                        }
                        if (badge > 0) Text("$badge", style = Theme.caption.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-5).dp)
                                .clip(CircleShape).background(if (i == Nav.TODO) C.late else C.accent)
                                .widthIn(min = 18.dp).padding(horizontal = 5.dp))
                    }
                    Text(t.title, style = Theme.caption.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (on) C.text else C.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
