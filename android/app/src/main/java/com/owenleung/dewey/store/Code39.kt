package com.owenleung.dewey.store

/**
 * Code 39, the barcode on an FUHSD student ID card (docs/ios/FINDINGS.md):
 * each character is 5 bars and 4 spaces, 3 of the 9 wide, framed by `*`.
 * Digits only here, since a student number is digits. Pure, so tests can
 * decode what it draws (ios/App/Store/Code39.swift).
 */
object Code39 {
    /** Wide (1) or narrow (0) for bar, space, bar, … of each character. */
    private val table = mapOf(
        '0' to "000110100", '1' to "100100001", '2' to "001100001", '3' to "101100000", '4' to "000110001",
        '5' to "100110000", '6' to "001110000", '7' to "000100101", '8' to "100100100", '9' to "001100100",
        '*' to "010010100",
    )
    /** Wide to narrow (Code 39 allows 2…3; 3 reads best off a phone screen). */
    const val RATIO = 3
    /** Blank narrow modules on each side, so a scanner finds the edges. */
    const val QUIET = 10

    fun canEncode(s: String) = s.isNotEmpty() && s.all { it in '0'..'9' }

    /** The barcode as modules, true = bar, left to right, quiet zones included; null if `s` isn't all digits. */
    fun modules(s: String): List<Boolean>? {
        if (!canEncode(s)) return null
        val out = MutableList(QUIET) { false }
        for ((i, ch) in "*$s*".withIndex()) {
            if (i > 0) out += false  // the narrow gap between characters
            for ((j, w) in table.getValue(ch).withIndex()) repeat(if (w == '1') RATIO else 1) { out += j % 2 == 0 }
        }
        return out + List(QUIET) { false }
    }
}
