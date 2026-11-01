package com.owenleung.dewey.store

/**
 * The student's ID number, for the barcode (StudentIDCard). Kept in the
 * Android Keystore on this phone only: never sent anywhere, never in the widgets
 * (ios/App/Screens/Settings/StudentIDView.swift StudentID).
 */
object StudentID {
    private const val KEY = "studentId"
    /** FUHSD's student numbers are 7 digits (the card, and the Schoology username, 2026-10-20). */
    const val USUAL_LENGTH = 7

    var number: String?
        get() = Secrets.get(KEY)
        set(v) = Secrets.set(KEY, v?.takeIf { it.isNotEmpty() })

    /** Digits only: anything else typed or pasted is dropped. */
    fun clean(s: String): String = s.filter { it in '0'..'9' }.take(12)

    /** A note under the number when it doesn't look like an FUHSD ID (never a block: older IDs may differ). */
    fun warning(s: String): String? {
        if (s.isEmpty() || s.length == USUAL_LENGTH) return null
        return "FUHSD student IDs are $USUAL_LENGTH digits. This has ${s.length}: check it against your card."
    }
}
