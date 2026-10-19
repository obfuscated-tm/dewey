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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owenleung.dewey.store.Prefs

/**
 * Dewey's one palette, Dew (the brand teal), with a light and a dark value for
 * every colour (WCAG AA: text ≥ 4.5:1 on every surface; ThemeTest checks).
 * Settings → Appearance picks light, dark or the system's. The same values as
 * ios/App/Screens/Shared/Theme.swift's Palette.dew.
 */
class Palette(light: String, dark: String) {
    private val light = light.split(" ")
    private val dark = dark.split(" ")

    init { require(this.light.size == NAMES.size && this.dark.size == NAMES.size) }

    fun hex(name: String, dark: Boolean): String = (if (dark) this.dark else light)[NAMES.indexOf(name)]

    companion object {
        val NAMES = listOf("background", "card", "border", "divider", "text", "secondary", "tertiary",
            "late", "lateSoft", "good", "goodSoft", "tagSoft", "accent")

        val dew = Palette(
            "#EEF6F2 #FFFFFF #CFE3DA #E0EDE7 #0E2A24 #3F5A53 #52685F #A63A0B #FBEDE5 #426E10 #E9F2DC #E1F0E9 #0F6E56",
            "#0F1A18 #172623 #27403A #1F332E #E1F5EE #A9C7BD #8FB0A5 #FF9466 #3A2219 #B5DB6E #2B3A17 #21352F #5DCAA5",
        )
    }
}

/**
 * The look in docs/ios/SCREENS.md ("Look"), the same tokens as
 * ios/App/Screens/Shared/Theme.swift. Text follows the system font scale (sp).
 */
object Theme {
    const val APPEARANCE_KEY = "appearance"

    /** The accent for the widgets (they lighten it in dark mode themselves). */
    val accentHex: String get() = Palette.dew.hex("accent", false)
    /** Settings → Appearance: "system" | "light" | "dark". */
    val appearance: String get() = Prefs.string(APPEARANCE_KEY) ?: "system"

    fun hex(s: String): Color = Color(android.graphics.Color.parseColor(s))

    /** A colour in dark mode: less saturated, full brightness, so it stays readable (Accent.dark on iOS). */
    fun darkAccent(c: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hsv[1] *= 0.6f
        hsv[2] = 1f
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /**
     * Each class's own colour, in Course Dashboard order: the Classes shelf, the
     * Grades letters, the catalog line. White text reads on every one (ThemeTest).
     */
    val classColors = listOf("#1D5EA8", "#23794A", "#6A4BA8", "#A8324A", "#9A5B0C", "#16707A", "#5E6670", "#8A4B1D")
    /** For text and marks: lightened in dark mode, like the accent. */
    fun classColor(i: Int, dark: Boolean): Color = hex(classColors[Math.floorMod(i, classColors.size)]).let { if (dark) darkAccent(it) else it }
    /** For a book spine or an icon square under white: the same in light and dark. */
    fun spineColor(i: Int): Color = hex(classColors[Math.floorMod(i, classColors.size)])

    // Seven text styles (SCREENS.md "Look"); numbers use tabular figures where it matters.
    val largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 41.sp)
    val title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val subhead = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    // Plus two display styles in the system serif, the one place the type has
    // character: big screen titles and the big grade letter, nothing else.
    /** 34 bold serif · screen titles (ScreenHeader), the big grade letter. */
    val display = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 41.sp, fontFamily = FontFamily.Serif)
    /** 22 bold serif · the grade letter on a Grades row or a shelf label, an assignment's title. */
    val displaySmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp, fontFamily = FontFamily.Serif)
    /** 17 bold serif · the Dewey number on a To Do catalog card. */
    val callNumber = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, fontFamily = FontFamily.Serif)
    /** 10 heavy · the word on a shelf book's bookmark ribbon only (a fixed size, so the ribbon stays thin). */
    val ribbon = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp)
    /** Settings → Developer only: raw engine JSON. Never in the student-facing UI. */
    val code = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)

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
    /** A To Do catalog card's red rule under its heading, and its pale blue ruled lines. */
    val catalogRule: Color,
    val catalogLine: Color,
)

fun colors(dark: Boolean, palette: Palette = Palette.dew): DeweyColors {
    fun p(name: String) = Theme.hex(palette.hex(name, dark))
    val accent = p("accent")
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
        catalogRule = Theme.hex(if (dark) "#D9706B" else "#C94B4B"),
        catalogLine = Theme.hex(if (dark) "#2E4258" else "#C9DDF0"),
    )
}

val LocalColors = staticCompositionLocalOf { colors(false) }

/** Shorthand inside composables: `C.accent`. */
val C: DeweyColors @Composable get() = LocalColors.current

@Composable
fun DeweyTheme(content: @Composable () -> Unit) {
    Prefs.revision.intValue // re-read the appearance when Settings change it
    val dark = when (Theme.appearance) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val c = colors(dark)
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
