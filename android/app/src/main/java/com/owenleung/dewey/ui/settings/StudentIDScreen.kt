package com.owenleung.dewey.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.owenleung.dewey.engine.AccountInfo
import com.owenleung.dewey.store.Code39
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.store.StudentID
import com.owenleung.dewey.ui.Card
import com.owenleung.dewey.ui.DetailScaffold
import com.owenleung.dewey.ui.EmptyState
import com.owenleung.dewey.ui.ScrollPage
import com.owenleung.dewey.ui.WideButton
import com.owenleung.dewey.ui.model
import com.owenleung.dewey.ui.nav
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import kotlinx.coroutines.launch
import kotlin.math.floor

private tailrec fun Context.activity(): Activity? = this as? Activity ?: (this as? ContextWrapper)?.baseContext?.activity()

/**
 * The ID card, full screen: the Code 39 barcode at full brightness for the
 * library or attendance scanner, and the number under it
 * (ios/App/Screens/Settings/StudentIDView.swift StudentIDCard).
 */
@Composable
fun StudentIDCard(onClose: () -> Unit) {
    val nav = nav
    Prefs.revision.intValue
    val number = StudentID.number
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        // Full brightness and no screen timeout while it's open.
        DisposableEffect(Unit) {
            val w = ctx.activity()?.window
            val was = w?.attributes?.screenBrightness
            w?.attributes = w?.attributes?.also { it.screenBrightness = 1f }
            w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                w?.attributes = w?.attributes?.also { it.screenBrightness = was ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        Column(Modifier.fillMaxSize().background(C.background).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Student ID", style = Theme.headline, color = C.text, modifier = Modifier.weight(1f).padding(start = 12.dp))
                TextButton(onClose) { Text("Done", color = C.text, fontWeight = FontWeight.SemiBold) }
            }
            Column(Modifier.fillMaxWidth().padding(Theme.Space.l).padding(top = Theme.Space.xl), verticalArrangement = Arrangement.spacedBy(Theme.Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (number != null && Code39.canEncode(number)) {
                    // Always black on white, whatever the theme: that's what scanners read.
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.Radius.card)).background(Color.White)
                        .border(1.dp, C.border, RoundedCornerShape(Theme.Radius.card)).padding(vertical = Theme.Space.xl, horizontal = Theme.Space.m),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Theme.Space.m)) {
                        Barcode(number, Modifier.fillMaxWidth().height(130.dp).semantics { contentDescription = "Barcode of your student ID" })
                        Text(number, style = Theme.largeTitle.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp),
                            color = Color.Black, modifier = Modifier.semantics { contentDescription = "Student ID ${number.toList().joinToString(" ")}" })
                    }
                    Text("The screen is at full brightness while this is open. Hold the phone flat under the scanner.",
                        style = Theme.footnote, color = C.secondary, textAlign = TextAlign.Center)
                    TextButton({ onClose(); nav.push(com.owenleung.dewey.ui.Route.SettingsPage(com.owenleung.dewey.ui.SettingsPage.STUDENT_ID)) }) {
                        Text("Change number", style = Theme.subhead.copy(fontWeight = FontWeight.SemiBold), color = C.text)
                    }
                } else {
                    EmptyState("No student ID yet", "Add your number once and Dewey shows its barcode here.", icon = Icons.Outlined.Badge)
                    WideButton("Add my student ID", Icons.Outlined.Add) {
                        onClose(); nav.push(com.owenleung.dewey.ui.Route.SettingsPage(com.owenleung.dewey.ui.SettingsPage.STUDENT_ID))
                    }
                }
            }
        }
    }
}

/** Code 39 bars, each a whole number of pixels wide so the edges stay sharp. */
@Composable
fun Barcode(value: String, modifier: Modifier) {
    Canvas(modifier.background(Color.White)) {
        val m = Code39.modules(value) ?: return@Canvas
        val module = maxOf(1f, floor(size.width / m.size))
        val x0 = ((size.width - module * m.size) / 2).let(::floor)
        var i = 0
        while (i < m.size) {
            if (!m[i]) { i++; continue }
            var j = i
            while (j < m.size && m[j]) j++
            drawRect(Color.Black, Offset(x0 + i * module, 0f), Size((j - i) * module, size.height))
            i = j
        }
    }
}

/**
 * Settings › Student ID: the number from Schoology (its username is the ID at FUHSD),
 * by scanning the card, or typed. Digits only; a length other than 7 gets a note.
 */
@Composable
fun StudentIDSettings() {
    val model = model
    val nav = nav
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Prefs.revision.intValue
    val saved = StudentID.number
    var text by remember { mutableStateOf(saved ?: "") }
    var typing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    fun save() { StudentID.number = text; Prefs.set("studentIdSaved", System.currentTimeMillis()) }

    DetailScaffold("Student ID", actions = {
        TextButton({ save(); nav.pop() }, enabled = text.isNotEmpty()) { Text("Save", fontWeight = FontWeight.SemiBold, color = if (text.isEmpty()) C.tertiary else C.text) }
    }) { pad ->
        ScrollPage(pad, spacing = 22.dp) {
            if (saved != null) Section("Your card", "Faster: add Dewey's Student ID shortcut to your home screen (press and hold the app icon).") {
                Action("Show my barcode", divider = false) { nav.showID = true }
            }
            Section("Your student number", "The number printed under the barcode on your ID card. Only on this phone: Dewey never sends it anywhere.") {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(text, { text = StudentID.clean(it) }, Modifier.fillMaxWidth().onFocusChanged { typing = it.isFocused },
                        placeholder = { Text("Student ID", color = C.tertiary) }, singleLine = true,
                        textStyle = Theme.title.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = C.text),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.text, unfocusedBorderColor = C.border, cursorColor = C.text))
                    // Too short only once typing stops; too long at once.
                    StudentID.warning(text)?.takeIf { !typing || text.length > StudentID.USUAL_LENGTH }?.let { w ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.WarningAmber, null, tint = C.late, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                            Text(" $w", style = Theme.footnote, color = C.late)
                        }
                    }
                    note?.let { Text(it, style = Theme.footnote, color = C.secondary) }
                }
            }
            Section("Fill it in", "Schoology: your username, which at FUHSD is your student ID. Scan: point the camera at the barcode on your card.") {
                RowLine(false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fill in from Schoology", style = Theme.body, color = if (loading) C.tertiary else C.text,
                            modifier = Modifier.weight(1f).then(if (loading) Modifier else Modifier.clickable {
                                loading = true
                                scope.launch {
                                    try {
                                        val a: AccountInfo = model.engine.call("account")
                                        if (a.studentId != null) { text = a.studentId; note = "From your Schoology username." }
                                        else note = "Your Schoology username isn't a number, so it can't be your ID. Scan your card or type the number."
                                    } catch (e: Exception) {
                                        note = "Couldn't read Schoology just now. Scan your card or type the number."
                                    } finally { loading = false }
                                }
                            }))
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = C.secondary)
                    }
                }
                Action("Scan my ID card") {
                    val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
                    GmsBarcodeScanning.getClient(ctx, opts).startScan()
                        .addOnSuccessListener { b ->
                            val raw = b.rawValue ?: ""
                            val digits = StudentID.clean(raw)
                            note = when {
                                b.format != Barcode.FORMAT_CODE_39 -> "That barcode isn't the kind on an FUHSD ID card (Code 39). Try the one above your name."
                                digits.isEmpty() || digits.length != raw.length -> "That barcode has letters in it, so it isn't a student number."
                                else -> { text = digits; "Read from your card." }
                            }
                        }
                        .addOnFailureListener { note = "The camera scanner isn't available on this phone. Type the number instead." }
                }
            }
            if (saved != null) Card {
                Action("Remove student ID", divider = false, destructive = true) {
                    StudentID.number = null
                    Prefs.set("studentIdSaved", null)
                    text = ""
                    nav.pop()
                }
            }
        }
    }
}
