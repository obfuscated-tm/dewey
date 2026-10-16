package com.owenleung.dewey.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.owenleung.dewey.BuildConfigInfo
import com.owenleung.dewey.R
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.EngineJson
import com.owenleung.dewey.neoplan.NeoPlanClient
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.Inbox
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.store.Reminders
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.DetailScaffold
import com.owenleung.dewey.ui.Divider
import com.owenleung.dewey.ui.Route
import com.owenleung.dewey.ui.SheetTarget
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.WideButton
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.web.WebConfig
import com.owenleung.dewey.web.WebController
import com.owenleung.dewey.web.WebViewHost
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.Date

// ── Small form parts ─────────────────────────────────────────────────────

@Composable
private fun Section(header: String, footer: String? = null, content: @Composable () -> Unit) {
    Column {
        Text(header.uppercase(), style = Theme.caption.copy(fontWeight = FontWeight.SemiBold), color = C.secondary, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        Card { content() }
        if (footer != null) Text(footer, style = Theme.footnote, color = C.secondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
    }
}

@Composable
private fun RowLine(divider: Boolean, content: @Composable () -> Unit) {
    if (divider) Divider()
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) { content() }
}

@Composable
private fun Toggle(label: String, key: String, def: Boolean, divider: Boolean = true, onChange: (Boolean) -> Unit = {}) {
    Prefs.revision.intValue
    val on = Prefs.bool(key, def)
    RowLine(divider) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
            Switch(on, { Prefs.set(key, it); onChange(it) }, colors = SwitchDefaults.colors(checkedTrackColor = C.accent))
        }
    }
}

@Composable
private fun Value(label: String, value: String, divider: Boolean = true) = RowLine(divider) {
    Row { Text(label, style = Theme.body, color = C.text, modifier = Modifier.weight(1f)); Text(value, style = Theme.body, color = C.secondary) }
}

@Composable
private fun Action(label: String, divider: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    if (divider) Divider()
    Text(label, style = Theme.body, color = if (destructive) C.late else C.accent,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 13.dp))
}

@Composable
private fun HourPicker(label: String, key: String, def: Int, hours: IntRange, onChange: () -> Unit) {
    Prefs.revision.intValue
    val h = Prefs.int(key, def)
    var open by remember { mutableStateOf(false) }
    fun text(x: Int) = Fmt.time(Fmt.atHour(Date(), x))
    RowLine(true) {
        Box {
            Row(Modifier.fillMaxWidth().clickable { open = true }) {
                Text(label, style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
                Text(text(h), style = Theme.body, color = C.accent)
            }
            DropdownMenu(open, { open = false }) {
                for (x in hours) DropdownMenuItem(text = { Text(text(x)) }, onClick = { Prefs.set(key, x); open = false; onChange() })
            }
        }
    }
}

/** 4b · Account, neo-plan, reminders, notifications, inbox, look, login compatibility, this build, Developer. */
@Composable
fun SettingsScreen() {
    val model = model
    val nav = nav
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var tokenError by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(NeoPlanClient.connected) }
    var confirmSignOut by remember { mutableStateOf(false) }
    Prefs.revision.intValue

    /** A due-reminder switch or time changed: ask for permission when turning one on, then reschedule. */
    fun dueChanged(on: Boolean) {
        scope.launch {
            if (on) Reminders.requestPermission()
            Reminders.schedule(model.todoItems(), model.courses)
        }
    }
    fun askIfOn(on: Boolean) { if (on) scope.launch { Reminders.requestPermission() } }

    val ctx = LocalContext.current
    // Re-read when coming back from Android's "Alarms & reminders" page, and reschedule with exact times.
    var exact by remember { mutableStateOf(Reminders.exactAllowed) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && Reminders.exactAllowed != exact) {
                exact = Reminders.exactAllowed
                Reminders.schedule(model.todoItems(), model.courses)
            }
        }
        lifecycle.lifecycle.addObserver(o)
        onDispose { lifecycle.lifecycle.removeObserver(o) }
    }

    DetailScaffold("Settings") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            Section("Schoology") {
                Value("Signed in to", Engine.HOST, divider = false)
                Action("Sign out", destructive = true) { confirmSignOut = true }
            }

            Section("neo-plan", if (connected) "Done, turned in and type changes are shared with neo-plan; the newer edit wins."
                else "Optional. Copy the token from neo-plan's extension settings. Kept encrypted on this phone.") {
                if (connected) {
                    Value("Token", NeoPlanClient.mask(NeoPlanClient.token), divider = false)
                    Action("Sync now") { scope.launch { model.syncNeoPlan() } }
                    Action("Disconnect", destructive = true) { NeoPlanClient.setToken(null); connected = false }
                } else {
                    RowLine(false) {
                        Box(Modifier.fillMaxWidth()) {
                            if (token.isEmpty()) Text("np_…", style = Theme.body, color = C.tertiary)
                            BasicTextField(token, { token = it }, singleLine = true, textStyle = Theme.body.copy(color = C.text),
                                visualTransformation = PasswordVisualTransformation(), cursorBrush = SolidColor(C.accent), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    tokenError?.let { Text(it, style = Theme.footnote, color = C.late, modifier = Modifier.padding(horizontal = 16.dp)) }
                    Action("Connect") {
                        val t = token.trim()
                        if (!NeoPlanClient.validToken(t)) { tokenError = "That doesn't look like a neo-plan token (np_…)."; return@Action }
                        NeoPlanClient.setToken(t)
                        token = ""; tokenError = null; connected = true
                        scope.launch { model.syncNeoPlan() }
                    }
                }
            }

            Section("Due reminders", "A test reminder stops once you mark it Studied; the others stop once it's submitted, turned in or done. " +
                "The 2-hour one is for Schoology dropboxes only and still comes if you marked it done but haven't submitted." +
                if (exact) "" else " Without exact times, Android may send each one up to 10 minutes late.") {
                Toggle("2 hours before, if not submitted", Reminders.SOON_KEY, true, divider = false, onChange = ::dueChanged)
                Toggle("Night before a test", Reminders.TEST_NIGHT_KEY, true, onChange = ::dueChanged)
                Toggle("Homework the evening before", Reminders.ENABLED_KEY, false, onChange = ::dueChanged)
                if (Reminders.enabled || Prefs.bool(Reminders.TEST_NIGHT_KEY, true)) HourPicker("Evening time", Reminders.HOUR_KEY, 19, 15..22) { dueChanged(false) }
                Toggle("Morning summary", Reminders.MORNING_KEY, false, onChange = ::dueChanged)
                if (Prefs.bool(Reminders.MORNING_KEY)) HourPicker("Morning time", Reminders.MORNING_HOUR_KEY, 7, 5..10) { dueChanged(false) }
                if (!exact) Action("Allow exact reminder times") { Reminders.openExactAlarmSettings(ctx) }
            }

            Section("Notifications", "Grade, message, material and signed-out alerts come when the app refreshes in the background, so they can be late. Schoology signs you out about once a day.") {
                Toggle("New grades", Reminders.GRADES_KEY, true, divider = false, onChange = ::askIfOn)
                Toggle("Messages from teachers", Reminders.MESSAGES_KEY, true, onChange = ::askIfOn)
                Toggle("New materials in your classes", Reminders.MATERIALS_KEY, true, onChange = ::askIfOn)
                Toggle("Signed out of Schoology", Reminders.SIGNED_OUT_KEY, true, onChange = ::askIfOn)
            }

            Section("Inbox", if (Inbox.markRead) "Opening a message in the app marks it read on Schoology, like opening it there."
                else "Messages show their preview only, so they stay unread on Schoology until you open them there.") {
                Toggle("Mark messages read when opened", Inbox.MARK_READ_KEY, true, divider = false)
            }

            Section("Look") {
                RowLine(false) {
                    val opts = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        opts.forEachIndexed { i, (k, l) ->
                            SegmentedButton(Theme.appearance == k, { Prefs.set(Theme.APPEARANCE_KEY, k) }, SegmentedButtonDefaults.itemShape(i, opts.size),
                                colors = SegmentedButtonDefaults.colors(activeContainerColor = C.accentSoft, activeContentColor = C.accent)) { Text(l) }
                        }
                    }
                }
                RowLine(true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Theme.accentOptions.forEachIndexed { i, hex ->
                            val on = Theme.accentHex == hex
                            Box(Modifier.size(36.dp).border(if (on) 2.dp else 0.dp, if (on) C.text else C.card, CircleShape).padding(3.dp)
                                .clip(CircleShape).background(Theme.hex(hex)).clickable { Prefs.set(Theme.ACCENT_KEY, hex); com.owenleung.dewey.widget.WidgetBridge.publish(model) })
                        }
                    }
                }
            }

            Section("Sign-in", "Signs in with Chrome's user agent instead of the app's web view's. Google refuses sign-in in a web view that says it is one, so this is on by default; it turns on by itself if Google refuses.") {
                Toggle("Login compatibility mode", WebConfig.COMPAT_KEY, true, divider = false) { model.engine.reload() }
            }

            Section("This build") {
                Value("Version", BuildConfigInfo.versionName(LocalContext.current), divider = false)
                Action("Developer") { nav.push(Route.Developer) }
            }
        }
    }

    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out of Schoology?") },
        text = { Text("Clears the Schoology session and saved pages. Your Done marks stay.") },
        confirmButton = { TextButton({ confirmSignOut = false; nav.pop(); scope.launch { model.signOut() } }) { Text("Sign out", color = C.late) } },
        dismissButton = { TextButton({ confirmSignOut = false }) { Text("Cancel") } },
    )
}

/** Run any engine op and see its JSON (M1/M2 checks), and what reminders are set. */
@Composable
fun DeveloperScreen() {
    val model = model
    val nav = nav
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val examples = linkedMapOf(
        "courses" to "{}", "todo" to "{}", "status" to """{"id":""}""", "assignment" to """{"id":""}""",
        "gradebook" to """{"sectionId":""}""", "dropbox" to """{"url":"/assignment/ID/dropbox/view/N"}""",
        "rubric" to """{"rubricId":"","enrollmentId":"","itemId":""}""", "materials" to """{"sectionId":""}""",
        "material" to """{"path":"/page/ID"}""", "feed" to """{"sectionId":"","page":0}""",
        "notifications" to """{"page":0}""", "messages" to """{"page":0}""",
    )
    fun fill(s: String) = model.classes.firstOrNull()?.sectionId?.let { s.replace("\"sectionId\":\"\"", "\"sectionId\":\"$it\"") } ?: s
    var op by remember { mutableStateOf("courses") }
    var args by remember { mutableStateOf("{}") }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    DetailScaffold("Developer") { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            val planned = Reminders.planned()
            Section("Scheduled reminders (${planned.size})", if (Reminders.exactAllowed) "Exact times allowed." else "Exact times not allowed: each can come up to 10 minutes late.") {
                Action("Test reminder in 1 minute", divider = false) { Reminders.testInOneMinute() }
                Action("Background refresh in 30 seconds") { com.owenleung.dewey.RefreshWorker.runOnce(ctx, 30) }
                if (planned.isNotEmpty()) Divider()
                if (planned.isEmpty()) RowLine(true) { Text("None", style = Theme.subhead, color = C.secondary) }
                planned.forEachIndexed { i, p ->
                    RowLine(i > 0) {
                        Column {
                            Text(p.title, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                            Text(Fmt.dayTime(Date(p.fire)), style = Theme.caption, color = C.secondary)
                        }
                    }
                }
            }
            Section("Engine", "engine.js ${if (model.engine.hasScript) "bundled" else "MISSING"}. GET only; quiz pages are refused.") {
                RowLine(false) {
                    Box {
                        Row(Modifier.fillMaxWidth().clickable { menu = true }) {
                            Text("Op", style = Theme.body, color = C.text, modifier = Modifier.weight(1f))
                            Text(op, style = Theme.body, color = C.accent)
                        }
                        DropdownMenu(menu, { menu = false }) {
                            for (k in examples.keys) DropdownMenuItem(text = { Text(k) }, onClick = { op = k; args = fill(examples[k]!!); menu = false })
                        }
                    }
                }
                RowLine(true) {
                    BasicTextField(args, { args = it }, textStyle = Theme.code.copy(color = C.text), cursorBrush = SolidColor(C.accent), modifier = Modifier.fillMaxWidth())
                }
                Action(if (running) "Running…" else "Run") {
                    if (running) return@Action
                    val a = runCatching { EngineJson.json.parseToJsonElement(args).jsonObject }.getOrNull()
                    if (a == null) { output = "args isn't a JSON object"; return@Action }
                    running = true
                    scope.launch {
                        val t0 = System.currentTimeMillis()
                        val out = model.engine.debugCall(op, a)
                        output = String.format("%.2fs\n", (System.currentTimeMillis() - t0) / 1000.0) + out
                        running = false
                    }
                }
                Action("Reload engine") { model.engine.reload() }
                // The Schoology sheet's file chooser (Files, Photos, camera) on a local page: nothing is sent anywhere.
                Action("Test file picker") {
                    nav.openSheet(SheetTarget("data:text/html,<meta name=viewport content='width=device-width'>" +
                        "<p style='font:18px sans-serif'>File picker test. Nothing is uploaded.</p><input type=file id=f multiple>" +
                        "<p id=o style='font:16px sans-serif'></p><script>f.onchange=()=>o.textContent=[...f.files].map(x=>x.name+' ('+x.size+' bytes)').join(', ')</script>"))
                }
            }
            if (output.isNotEmpty()) Section("Result") {
                RowLine(false) { SelectionContainer { Text(output.take(60_000), style = Theme.code, color = C.text) } }
                Action("Copy") {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("engine", output))
                }
            }
        }
    }
}

/**
 * 4a · First launch: Schoology in a web view → Google SSO. The app never sees
 * the password; it only watches for a signed-in Schoology page.
 */
@Composable
fun SignInScreen() {
    val model = model
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var web by remember { mutableStateOf<WebController?>(null) }
    Column(Modifier.fillMaxSize().background(C.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.dewey_mark), null, Modifier.size(60.dp))
        Text("Dewey", style = Theme.largeTitle, color = C.text)
        Text("Your Schoology To Do, grades and materials, made for your phone. Sign in with your school Google account, the same way you do on Schoology.",
            style = Theme.body, color = C.secondary)
        Card(padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Step("1", "Sign in to fuhsd.schoology.com", "Required")
                Step("2", "Connect neo-plan", "Optional · in Settings")
                Step("3", "Turn on reminders", "Optional · in Settings")
            }
        }
        Spacer(Modifier.weight(1f))
        WideButton("Sign in to Schoology", icon = Icons.Outlined.AccountCircle) {
            web = WebController(ctx, Engine.ORIGIN).apply {
                onFinish = { url ->
                    val p = android.net.Uri.parse(url).path ?: ""
                    if (WebConfig.isSignedInUrl(url) && (p.startsWith("/home") || p == "/" || p.isEmpty() || p.startsWith("/course"))) {
                        web = null
                        scope.launch { model.signedIn() }
                    }
                }
            }
        }
        Text("Nothing is sent anywhere but Schoology. The app only reads; submitting happens on Schoology's own page.", style = Theme.caption, color = C.tertiary)
    }
    web?.let { w ->
        DisposableEffect(w) { onDispose { w.destroy() } }
        Dialog({ web = null }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            BackHandler { if (w.canGoBack) w.goBack() else web = null }
            Column(Modifier.fillMaxSize().background(C.card).statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ web = null }) { Text("Cancel", style = Theme.headline, color = C.accent) }
                    Text("Sign in", style = Theme.headline, color = C.text, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (w.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = C.accent)
                        else IconButton(w::reload) { Icon(Icons.Outlined.Refresh, "Reload", tint = C.accent) }
                    }
                }
                Divider()
                Box(Modifier.weight(1f)) { WebViewHost(w) }
            }
        }
    }
}

@Composable
private fun Step(n: String, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(C.accentSoft), contentAlignment = Alignment.Center) {
            Text(n, style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = C.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
            Text(sub, style = Theme.footnote, color = C.secondary)
        }
    }
}
