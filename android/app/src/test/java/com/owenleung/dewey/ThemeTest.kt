package com.owenleung.dewey

import com.owenleung.dewey.ui.theme.Palette
import com.owenleung.dewey.ui.theme.Theme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ThemeTest {
    /** WCAG relative luminance of "#RRGGBB". */
    private fun lum(hex: String): Double {
        val v = hex.removePrefix("#").toLong(16)
        fun f(c: Long): Double { val x = c / 255.0; return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * f((v shr 16) and 0xFF) + 0.7152 * f((v shr 8) and 0xFF) + 0.0722 * f(v and 0xFF)
    }

    private fun ratio(a: String, b: String): Double {
        val (x, y) = lum(a) to lum(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /** Theme.darkAccent, on hex: less saturated, full brightness (java.awt-free HSV). */
    private fun darkHex(hex: String): String {
        val v = hex.removePrefix("#").toLong(16)
        val r = ((v shr 16) and 0xFF) / 255.0; val g = ((v shr 8) and 0xFF) / 255.0; val b = (v and 0xFF) / 255.0
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        val h = when { d == 0.0 -> 0.0; mx == r -> 60 * (((g - b) / d) % 6); mx == g -> 60 * ((b - r) / d + 2); else -> 60 * ((r - g) / d + 4) }.let { if (it < 0) it + 360 else it }
        val sat = if (mx == 0.0) 0.0 else d / mx * 0.6
        val c = sat; val x = c * (1 - kotlin.math.abs((h / 60) % 2 - 1)); val m = 1 - c
        val (rr, gg, bb) = when ((h / 60).toInt()) { 0 -> Triple(c, x, 0.0); 1 -> Triple(x, c, 0.0); 2 -> Triple(0.0, c, x); 3 -> Triple(0.0, x, c); 4 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x) }
        fun hx(f: Double) = "%02X".format(((f + m) * 255).toInt().coerceIn(0, 255))
        return "#" + hx(rr) + hx(gg) + hx(bb)
    }

    /**
     * Every text colour is readable (≥ 4.5:1) on every surface, in light and dark, and the
     * accent on a card and under its button text (ios/Tests ThemeTests).
     */
    @Test fun contrast() {
        val p = Palette.dew
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            for (fg in listOf("text", "secondary", "tertiary", "late", "good", "accent")) for (bg in listOf("card", "background", "tagSoft")) {
                if (fg == "accent" && bg == "tagSoft") continue
                val r = ratio(p.hex(fg, dark), p.hex(bg, dark))
                assertTrue("$mode $fg on $bg: $r", r >= 4.5)
            }
            assertTrue("$mode on-accent", ratio(p.hex("accent", dark), if (dark) "#101418" else "#FFFFFF") >= 4.5)
        }
    }

    /** White class names read on every class colour (the shelf), and the colour reads as text on the cards. */
    @Test fun classColors() {
        val p = Palette.dew
        for (hex in Theme.classColors) {
            assertTrue("$hex white", ratio(hex, "#FFFFFF") >= 4.5)
            for (bg in listOf("card", "background")) {
                assertTrue("$hex on $bg", ratio(hex, p.hex(bg, false)) >= 4.5)
                assertTrue("$hex dark on $bg", ratio(darkHex(hex), p.hex(bg, true)) >= 4.5)
            }
        }
    }
}
