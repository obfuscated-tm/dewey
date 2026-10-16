package com.owenleung.dewey

import com.owenleung.dewey.ui.theme.Palette
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

    /**
     * In every theme, every text colour is readable (≥ 4.5:1) on every surface, in light and dark,
     * and a theme's own accent on a card and under its button text (ios/Tests ThemeTests).
     */
    @Test fun contrast() {
        for (p in Palette.all) for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            for (fg in listOf("text", "secondary", "tertiary", "late", "good")) for (bg in listOf("card", "background", "tagSoft")) {
                val r = ratio(p.hex(fg, dark), p.hex(bg, dark))
                assertTrue("${p.id} $mode $fg on $bg: $r", r >= 4.5)
            }
            val a = p.accent ?: continue
            val accent = if (dark) a.second else a.first
            assertTrue("${p.id} $mode accent", ratio(accent, p.hex("card", dark)) >= 4.5)
            assertTrue("${p.id} $mode on-accent", ratio(accent, if (dark) "#101418" else "#FFFFFF") >= 4.5)
        }
    }
}
