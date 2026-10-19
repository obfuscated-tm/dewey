package com.owenleung.dewey.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owenleung.dewey.neoplan.NeoPlanClient
import com.owenleung.dewey.store.Cache
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.ui.DetailScaffold
import com.owenleung.dewey.ui.PillButton
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.WideButton
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import kotlinx.coroutines.launch

object NeoPlanStatus {
    /** "Connected · synced 2 min ago", or just "Connected" before the first sync. */
    fun text(): String {
        val at = Cache.loadDate("neoplanSyncedAt") ?: return "Connected"
        return "Connected · synced ${DateUtils.getRelativeTimeSpanString(at.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}"
    }
}

/**
 * Settings → Help → neo-plan: what it is, who wants it, what Dewey syncs, and how
 * to connect (ios/App/Screens/Settings/NeoPlanView.swift). The only place the app
 * mentions neo-plan until it's connected.
 */
@Composable
fun NeoPlanScreen() {
    val model = model
    Prefs.revision.intValue
    var connected by remember { mutableStateOf(NeoPlanClient.connected) }
    var token by remember { mutableStateOf("") }
    var tokenError by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    val box = Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.Radius.card)).background(C.card)
    DetailScaffold("neo-plan") { pad ->
        ScrollPage(pad, spacing = Theme.Space.l) {
            if (connected) Column(box.padding(Theme.Space.m), verticalArrangement = Arrangement.spacedBy(Theme.Space.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = C.good, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(NeoPlanStatus.text(), style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.good)
                }
                Row {
                    Text("Token", style = Theme.subhead, color = C.text, modifier = Modifier.weight(1f))
                    Text(NeoPlanClient.mask(NeoPlanClient.token), style = Theme.subhead, color = C.secondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillButton(if (syncing) "Syncing…" else "Sync now") {
                        if (syncing) return@PillButton
                        syncing = true
                        model.scope.launch { model.syncNeoPlan(); syncing = false }
                    }
                    Spacer(Modifier.weight(1f))
                    PillButton("Disconnect", color = C.late) { NeoPlanClient.setToken(null); connected = false; Prefs.revision.intValue++ }
                }
            }
            Text("Optional. Dewey works fully without it.", style = Theme.subhead, color = C.secondary)
            Part("What it is", "A planner website (neo-plan.vercel.app) that lays your week out as a board, one column per class. Dewey can fill it from Schoology for you.")
            Part("Who it's for", "People who already plan in neo-plan, or want one board shared between this phone and a computer with the Dewey extension. If you don't have an account, skip this.")
            Column(verticalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
                Heading("What Dewey syncs")
                Column(box.padding(horizontal = Theme.Space.m)) {
                    SyncLine(Icons.Outlined.Add, "New work from your To Do goes onto the board.")
                    SyncLine(Icons.Outlined.Check, "Done, Studied and Turned in, both ways. The newer change wins.")
                    SyncLine(Icons.Outlined.Check, "Submitted on Schoology clears it from the board.")
                    SyncLine(Icons.Outlined.Check, "Type: HW, Test, Task or Classwork.")
                    SyncLine(Icons.Outlined.Remove, "Never your Schoology password, grades or messages.", dim = true)
                }
            }
            if (!connected) Column(verticalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
                Heading("Connect")
                Column(box.padding(Theme.Space.m), verticalArrangement = Arrangement.spacedBy(Theme.Space.m)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Numbered(1, "Sign in at neo-plan.vercel.app.")
                        Numbered(2, "Open Settings and make an extension token.")
                        Numbered(3, "Copy it (it starts with np_) and paste it here.")
                    }
                    Box(Modifier.fillMaxWidth().border(1.dp, C.border, RoundedCornerShape(Theme.Radius.card)).padding(10.dp)) {
                        if (token.isEmpty()) Text("np_…", style = Theme.subhead, color = C.tertiary)
                        BasicTextField(token, { token = it }, singleLine = true, textStyle = Theme.subhead.copy(color = C.text),
                            visualTransformation = PasswordVisualTransformation(), cursorBrush = SolidColor(C.accent), modifier = Modifier.fillMaxWidth())
                    }
                    tokenError?.let { Text(it, style = Theme.footnote, color = C.late) }
                    WideButton("Connect", filled = token.isNotEmpty()) {
                        val t = token.trim()
                        if (t.isEmpty()) return@WideButton
                        if (!NeoPlanClient.validToken(t)) { tokenError = "That doesn't look like a neo-plan token (np_…)."; return@WideButton }
                        NeoPlanClient.setToken(t)
                        token = ""; tokenError = null; connected = true
                        Prefs.revision.intValue++
                        model.scope.launch { model.syncNeoPlan() }
                    }
                    Text("The token is kept encrypted on this phone.", style = Theme.caption, color = C.tertiary)
                }
            }
        }
    }
}

@Composable
private fun Heading(s: String) = Text(s.uppercase(), style = Theme.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    color = C.secondary, modifier = Modifier.semantics { heading() })

@Composable
private fun Part(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.Space.s)) {
        Heading(title)
        Text(body, style = Theme.body, color = C.text)
    }
}

@Composable
private fun SyncLine(icon: ImageVector, text: String, dim: Boolean = false) {
    Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = if (dim) C.tertiary else C.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Theme.Space.s))
        Text(text, style = Theme.subhead, color = if (dim) C.secondary else C.text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Numbered(n: Int, text: String) {
    Row {
        Text("$n.", style = Theme.subhead, color = C.secondary)
        Spacer(Modifier.width(Theme.Space.s))
        Text(text, style = Theme.subhead, color = C.text)
    }
}
