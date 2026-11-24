package com.owenleung.dewey.store

import java.util.Date

/**
 * The last 60 things the app did with its screens (launch, leaving and coming back, the Schoology
 * sheet opening and closing), shown on Settings → About → Developer, so a screen that vanished on
 * the phone can be traced afterwards. Kept across launches (ios/App/Store/AppLog.swift).
 */
object AppLog {
    private const val KEY = "appLog"

    fun note(s: String) {
        if (!Prefs.ready) return
        Prefs.set(KEY, (lines + "${Fmt.logStamp(Date())}  $s").takeLast(60))
    }

    val lines: List<String> get() = if (Prefs.ready) runCatching { Prefs.strings(KEY) }.getOrDefault(emptyList()) else emptyList()

    fun clear() = Prefs.set(KEY, null)
}
