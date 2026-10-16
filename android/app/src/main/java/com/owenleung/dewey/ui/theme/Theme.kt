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
 * One colour theme from Settings → Look, with a light and a dark value for every
 * colour (WCAG AA: text ≥ 4.5:1 on every surface). The same values as
 * ios/App/Screens/Shared/Theme.swift's Palette.
 */
class Palette(val id: String, val name: String, light: String, dark: String,
              /** Themes other than Standard and Midnight bring their own accent, so the accent dots hide. */
              val accent: Pair<String, String>? = null) {
    private val light = light.split(" ")
    private val dark = dark.split(" ")

    init { require(this.light.size == NAMES.size && this.dark.size == NAMES.size) { id } }

    fun hex(name: String, dark: Boolean): String = (if (dark) this.dark else light)[NAMES.indexOf(name)]

    companion object {
        val NAMES = listOf("background", "card", "border", "divider", "text", "secondary", "tertiary",
            "late", "lateSoft", "good", "goodSoft", "tagSoft")

        private const val STANDARD_LIGHT = "#F2F3F5 #FFFFFF #D9DDE2 #E4E7EB #15191D #4F5761 #5F6771 #A63A0B #FBEDE5 #1C6B3F #E5F2EA #E9ECF0"

        val all = listOf(
            // Light as before; dark is soft graphite instead of pure black.
            Palette("standard", "Standard", STANDARD_LIGHT,
                "#16171A #212327 #34373D #2C2E33 #ECEDEF #B9BDC4 #9DA2AA #FF9466 #3A2219 #6BD497 #1A3325 #2D3035"),
            // Standard with a true-black dark mode (saves battery on OLED screens).
            Palette("midnight", "Midnight", STANDARD_LIGHT,
                "#000000 #1C1C1E #38383A #2C2C2E #F2F2F7 #C1C5CC #A3A8B0 #FF8A5C #3A1E12 #5FD08D #14301F #2C2C2E"),
            // Dewey's brand teal.
            Palette("dew", "Dew",
                "#EEF6F2 #FFFFFF #CFE3DA #E0EDE7 #0E2A24 #3F5A53 #52685F #A63A0B #FBEDE5 #426E10 #E9F2DC #E1F0E9",
                "#0F1A18 #172623 #27403A #1F332E #E1F5EE #A9C7BD #8FB0A5 #FF9466 #3A2219 #B5DB6E #2B3A17 #21352F",
                "#0F6E56" to "#5DCAA5"),
            // Cool blue-grey, after Nord.
            Palette("frost", "Frost",
                "#ECEFF4 #FFFFFF #D3D9E3 #E2E6ED #2E3440 #4C566A #5A6478 #A5402D #F8E9E5 #3B6E3B #E6F0E2 #E5E9F0",
                "#242933 #2E3440 #434C5E #3B4252 #ECEFF4 #C8CFDB #AEB6C4 #EFA08A #4A3434 #B4D98F #354236 #3B4252",
                "#3F6A9A" to "#88C0D0"),
            // Soft lavender, after Catppuccin.
            Palette("pastel", "Pastel",
                "#EFF1F5 #FFFFFF #DCE0E8 #E6E9EF #4C4F69 #5C5F77 #626579 #B8203F #FBE6EA #2A6E1B #E5F3E0 #E6E9EF",
                "#1E1E2E #28283B #3E3E55 #34344A #CDD6F4 #BAC2DE #A6ADC8 #F38BA8 #45283A #A6E3A1 #26402F #34344A",
                "#7032D6" to "#CBA6F7"),
            // Warm sepia for reading.
            Palette("paper", "Paper",
                "#F4EEE3 #FBF7F0 #E2D8C6 #EAE2D4 #2B2620 #5A5145 #6B6152 #A3380E #F7E4D8 #3E6B1F #E6EDD8 #ECE3D3",
                "#1D1B18 #282521 #3D3832 #34302A #EBE3D5 #C7BCAA #ACA290 #F0956A #3D261B #9FD27F #263821 #35312B",
                "#9A5B13" to "#E0A458"),
        )

        fun named(id: String?): Palette = all.firstOrNull { it.id == id } ?: all[0]
    }
}

/**
 * The look in docs/ios/SCREENS.md ("Look"), the same tokens as
 * ios/App/Screens/Shared/Theme.swift. Text follows the system font scale (sp).
 */
object Theme {
    val accentOptions = listOf("#1D5EA8", "#23794A", "#6A4BA8", "#A8324A")
    val accentNames = listOf("Blue", "Green", "Purple", "Red")
    const val ACCENT_KEY = "accentHex"
    const val PALETTE_KEY = "palette"
    const val APPEARANCE_KEY = "appearance"

    val palette: Palette get() = Palette.named(Prefs.string(PALETTE_KEY))
    /** The accent dot picked in Settings (used by Standard and Midnight). */
    val chosenAccentHex: String get() = Prefs.string(ACCENT_KEY) ?: accentOptions[0]
    /** The accent for the widgets: the theme's own, or the chosen one. */
    val accentHex: String get() = palette.accent?.first ?: chosenAccentHex
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

fun colors(dark: Boolean, palette: Palette, accentHex: String): DeweyColors {
    fun p(name: String) = Theme.hex(palette.hex(name, dark))
    val accent = palette.accent?.let { Theme.hex(if (dark) it.second else it.first) }
        ?: Theme.hex(accentHex).let { if (dark) Theme.darkAccent(it) else it }
    return DeweyColors(
        dark = dark,
        accent = accent,
        accentSoft = accent.copy(alpha = 0.11f),
        background = p("background"),
        card = p("card"),
        border = p("border"),
        divider = p("divider"),
        text = p("text"),
        secondary = p("secondary"),
        tertiary = p("tertiary"),
        late = p("late"),
        lateSoft = p("lateSoft"),
        good = p("good"),
        goodSoft = p("goodSoft"),
        tagSoft = p("tagSoft"),
        onAccent = Theme.hex(if (dark) "#101418" else "#FFFFFF"),
    )
}

val LocalColors = staticCompositionLocalOf { colors(false, Palette.all[0], Theme.accentOptions[0]) }

/** Shorthand inside composables: `C.accent`. */
val C: DeweyColors @Composable get() = LocalColors.current

@Composable
fun DeweyTheme(content: @Composable () -> Unit) {
    Prefs.revision.intValue // re-read appearance and accent when Settings change them
    val dark = when (Theme.appearance) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val c = colors(dark, Theme.palette, Theme.chosenAccentHex)
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
