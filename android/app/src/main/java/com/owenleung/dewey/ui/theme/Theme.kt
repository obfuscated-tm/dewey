package com.owenleung.dewey.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owenleung.dewey.store.Prefs

/**
 * The look in docs/ios/SCREENS.md ("Look"), the same tokens as
 * ios/App/Screens/Shared/Theme.swift. Text follows the system font scale (sp).
 */
object Theme {
    val accentOptions = listOf("#1D5EA8", "#23794A", "#6A4BA8", "#A8324A")
    val accentNames = listOf("Blue", "Green", "Purple", "Red")
    const val ACCENT_KEY = "accentHex"
    const val APPEARANCE_KEY = "appearance"

    val accentHex: String get() = Prefs.string(ACCENT_KEY) ?: accentOptions[0]
    val appearance: String get() = Prefs.string(APPEARANCE_KEY) ?: "system"

    fun hex(s: String): Color = Color(android.graphics.Color.parseColor(s))

    /** The accent in dark mode: less saturated, full brightness, so it stays readable. */
    fun darkAccent(c: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hsv[1] *= 0.6f
        hsv[2] = 1f
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    // Seven text styles (SCREENS.md "Look"); numbers use tabular figures where it matters.
    val largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 41.sp)
    val title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val subhead = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val code = TextStyle(fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)

    object Radius { val tag = 6.dp; val card = 14.dp }
    object Space { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp }
}

/** Every colour has a light and a dark value (WCAG AA: text ≥ 4.5:1 on its card). */
@Immutable
data class DeweyColors(
    val dark: Boolean,
    val accent: Color,
    val accentSoft: Color,
    val background: Color,
    val card: Color,
    val border: Color,
    val divider: Color,
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val late: Color,
    val lateSoft: Color,
    val good: Color,
    val goodSoft: Color,
    val tagSoft: Color,
    /** Text on a filled accent button: white in light mode, near-black in dark. */
    val onAccent: Color,
)

fun colors(dark: Boolean, accentHex: String): DeweyColors {
    fun p(l: String, d: String) = Theme.hex(if (dark) d else l)
    val base = Theme.hex(accentHex)
    val accent = if (dark) Theme.darkAccent(base) else base
    return DeweyColors(
        dark = dark,
        accent = accent,
        accentSoft = accent.copy(alpha = 0.11f),
        background = p("#F2F3F5", "#000000"),
        card = p("#FFFFFF", "#1C1C1E"),
        border = p("#D9DDE2", "#38383A"),
        divider = p("#E4E7EB", "#2C2C2E"),
        text = p("#15191D", "#F2F2F7"),
        secondary = p("#4F5761", "#C1C5CC"),
        tertiary = p("#5F6771", "#A3A8B0"),
        late = p("#A63A0B", "#FF8A5C"),
        lateSoft = p("#FBEDE5", "#3A1E12"),
        good = p("#1C6B3F", "#5FD08D"),
        goodSoft = p("#E5F2EA", "#14301F"),
        tagSoft = p("#E9ECF0", "#2C2C2E"),
        onAccent = p("#FFFFFF", "#101418"),
    )
}

val LocalColors = staticCompositionLocalOf { colors(false, Theme.accentOptions[0]) }

/** Shorthand inside composables: `C.accent`. */
val C: DeweyColors @Composable get() = LocalColors.current

@Composable
fun DeweyTheme(content: @Composable () -> Unit) {
    Prefs.revision.intValue // re-read appearance and accent when Settings change them
    val dark = when (Theme.appearance) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val c = colors(dark, Theme.accentHex)
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.accent, onPrimary = c.onAccent, background = c.background, surface = c.card,
        onSurface = c.text, onBackground = c.text, surfaceVariant = c.tagSoft, onSurfaceVariant = c.secondary,
        outline = c.border, outlineVariant = c.divider, error = c.late, surfaceContainer = c.card,
        surfaceContainerHigh = c.card, surfaceContainerLow = c.background, secondaryContainer = c.accentSoft,
        onSecondaryContainer = c.accent,
    )
    CompositionLocalProvider(LocalColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
