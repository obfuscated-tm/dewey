package com.owenleung.dewey.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.ui.DetailScaffold
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme

/**
 * How statuses work: To do → Done → Turned in, what "on Schoology" means, and
 * Studied for tests (ios/App/Screens/Settings/StatusGuideView.swift). Opened from
 * the first-tick tip on To Do and from Settings → Help.
 */
@Composable
fun StatusGuideScreen() {
    DetailScaffold("How statuses work") { pad ->
        ScrollPage(pad, spacing = Theme.Space.l) {
            GuideStep(GuideKind.TODO, "To do", "Not finished yet.")
            GuideStep(GuideKind.DONE, "Done", "You finished it. Nothing is handed in yet. “Done · not submitted yet” means it still needs Submit on Schoology.")
            Column(verticalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
                GuideStep(GuideKind.TURNED_IN, "Turned in", "Your teacher has it. Two kinds:")
                Branch("Submitted on Schoology", "the dropbox has your file. Dewey says this only when Schoology does.")
                Branch("Turned in · not on Schoology", "you tapped Turn in because it went in on paper, on a lab site or in class.")
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.Radius.card)).background(C.card).padding(Theme.Space.m),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Why paper work is never “missing”", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                Text("Schoology only knows about its own dropbox. Paper, lab sites and Google Assignments look “not submitted” there even after you hand them in, and some teachers grade paper even when there's a dropbox. Dewey shows them late until you tap Turn in, never missing.",
                    style = Theme.footnote, color = C.secondary)
            }
            GuideStep(GuideKind.STUDIED, "Tests: Studied", "Tests aren't turned in. Tick Studied when you're ready and the night-before reminder stops.")
            GuideStep(GuideKind.UNDO, "Changed your mind?", "Tap the circle again, or Undo on a turned-in row. Press and hold a row to pick any status.")
        }
    }
}

@Composable
private fun GuideStep(kind: GuideKind, title: String, detail: String) {
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Theme.Space.m)) {
        GuideMark(kind)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Theme.body.copy(fontWeight = FontWeight.SemiBold), color = C.text)
            Text(detail, style = Theme.footnote, color = C.secondary)
        }
    }
}

@Composable
private fun Branch(title: String, detail: String) {
    val good = C.good
    val text = C.text
    Text(buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = good)) { append(title) }
        withStyle(SpanStyle(color = text)) { append(": $detail") }
    }, style = Theme.footnote, modifier = Modifier.padding(start = 24.dp + Theme.Space.m).fillMaxWidth()
        .clip(RoundedCornerShape(Theme.Radius.card)).background(C.card).padding(horizontal = 10.dp, vertical = 8.dp))
}

enum class GuideKind { TODO, DONE, TURNED_IN, STUDIED, UNDO }

/** The To Do row's marks (CheckMark), drawn without an item. */
@Composable
fun GuideMark(kind: GuideKind) {
    val secondary = C.secondary
    val accent = C.accent
    val accentSoft = C.accentSoft
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        when (kind) {
            GuideKind.TODO -> Canvas(Modifier.size(24.dp)) { drawCircle(secondary, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.6.dp.toPx())) }
            GuideKind.DONE, GuideKind.STUDIED -> {
                Canvas(Modifier.size(24.dp)) {
                    drawCircle(accentSoft)
                    drawCircle(accent, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
                Icon(Icons.Outlined.Check, null, tint = accent, modifier = Modifier.size(15.dp))
            }
            GuideKind.TURNED_IN -> Icon(Icons.Filled.Verified, null, tint = C.good, modifier = Modifier.size(27.dp))
            GuideKind.UNDO -> Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = secondary, modifier = Modifier.size(20.dp))
        }
    }
}

/** The one-time tip under the first row a student ticks on To Do: the one thing that matters next, and a link to the full guide. */
enum class TipKind(val raw: String) {
    DONE("done"), STUDIED("studied"), TURNED_IN("turnedIn");
    companion object { fun of(raw: String?) = entries.firstOrNull { it.raw == raw } ?: DONE }
}

@Composable
fun StatusTip(kind: TipKind, onGuide: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val text = C.text
    val message = buildAnnotatedString {
        fun b(s: String) = withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s) }
        when (kind) {
            TipKind.DONE -> { b("Done is just for you. "); append("Hand it in next: "); b("Submit"); append(" for a Schoology dropbox, "); b("Turn in"); append(" for paper or a lab site.") }
            TipKind.STUDIED -> { b("Studied stops the night-before reminder. "); append("Tests are never turned in.") }
            TipKind.TURNED_IN -> { b("Turned in · not on Schoology "); append("means you handed it in on paper, on a lab site or in class. Dewey says Submitted on Schoology only when Schoology does.") }
        }
    }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.Radius.card)).background(C.accentSoft).padding(Theme.Space.m), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(message, style = Theme.footnote, color = text)
            Text("How statuses work", style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.accent,
                modifier = Modifier.clickable(role = Role.Button, onClick = onGuide).padding(vertical = 4.dp))
        }
        Box(Modifier.size(32.dp).clickable(role = Role.Button, onClickLabel = "Close tip", onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, "Close tip", tint = C.tertiary, modifier = Modifier.size(16.dp))
        }
    }
}

