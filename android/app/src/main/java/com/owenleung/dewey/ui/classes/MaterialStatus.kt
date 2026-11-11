package com.owenleung.dewey.ui.classes

import com.owenleung.dewey.store.Fmt
import java.util.Date

/**
 * The one short status line under an assignment or quiz row in Materials: the date,
 * then one fact. "Late · Oct 24"; else "Oct 24 · 18 / 20"; else "Oct 24 · Submitted" or
 * "Oct 24 · Turned in"; else "Due …" (just the date once it has passed). With no date,
 * the fact alone. The check mark beside it already says done-but-not-turned-in, so that
 * never appears here. (iOS MaterialStatus.swift)
 */
object MaterialStatus {
    data class Line(val text: String, val late: Boolean = false)

    fun line(score: String?, late: Boolean, due: Date?, submitted: Boolean, turnedIn: Boolean, now: Date = Date()): Line? {
        val day = due?.let { Fmt.short(it) }
        val hasScore = !score.isNullOrEmpty()
        if (!hasScore && late && day != null) return Line("Late · $day", late = true)
        val fact = if (hasScore) score else if (submitted) "Submitted" else if (turnedIn) "Turned in" else null
        if (fact != null) return Line(day?.let { "$it · $fact" } ?: fact)
        if (due == null || day == null) return null
        return Line(if (due.before(now)) day else "Due " + Fmt.due(due, now))
    }
}

/** What kind of file a document row is, in one word (iOS FileKinds.word). */
fun FileKinds.word(title: String, url: String): String =
    when (ext(url).ifEmpty { ext(title) }) {
        "pdf" -> "PDF"
        "doc", "docx", "pages", "txt", "rtf" -> "Doc"
        "ppt", "pptx", "key" -> "Slides"
        "xls", "xlsx", "numbers", "csv" -> "Sheet"
        "png", "jpg", "jpeg", "gif", "heic" -> "Image"
        else -> "File"
    }
