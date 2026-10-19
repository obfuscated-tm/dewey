package com.owenleung.dewey.ui.tour

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.owenleung.dewey.ui.model

/** The guided practice's spotlight targets: where each `.coachTarget` key is, in root pixels. */
object Coach {
    val frames = mutableStateMapOf<String, Rect>()
}

/** Where the tour's spotlight can point. Does nothing outside practice. */
fun Modifier.coachTarget(key: String): Modifier = composed {
    if (!model.practice) return@composed this
    androidx.compose.runtime.DisposableEffect(key) { onDispose { Coach.frames.remove(key) } }
    onGloballyPositioned { Coach.frames[key] = it.boundsInRoot() }
}
