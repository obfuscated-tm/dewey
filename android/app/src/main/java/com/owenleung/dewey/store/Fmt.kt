package com.owenleung.dewey.store

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Dates and numbers the way the iOS app shows them (ios/App/Store/Cache.swift Fmt). */
object Fmt {
    var zone: ZoneId = ZoneId.systemDefault()

    fun date(s: String?): Date? {
        if (s.isNullOrEmpty()) return null
        runCatching { return Date.from(OffsetDateTime.parse(s).toInstant()) }
        runCatching { return Date.from(Instant.parse(s)) }
        return null
    }

    fun iso(d: Date): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(d.toInstant().atZone(zone).toOffsetDateTime())

    /** grademath's 'YYYY-MM-DDTHH:MM' wall-clock date. */
    fun wallDate(s: String?): Date? {
        if (s == null) return null
        return runCatching {
            if (s.length > 10) Date.from(LocalDateTime.parse(s.take(16)).atZone(zone).toInstant())
            else Date.from(LocalDate.parse(s).atStartOfDay(zone).toInstant())
        }.getOrNull()
    }

    // Built once per pattern: these run per row.
    private val formatters = java.util.concurrent.ConcurrentHashMap<String, DateTimeFormatter>()
    private fun f(p: String) = formatters.getOrPut(p) { DateTimeFormatter.ofPattern(p, Locale.US) }
    private fun fmt(p: String, d: Date): String =
        f(p).format(d.toInstant().atZone(zone)).replace("AM", "am").replace("PM", "pm")

    fun time(d: Date) = fmt("h:mm a", d)
    fun weekdayTime(d: Date) = fmt("EEE h:mm a", d)
    fun dayTime(d: Date) = fmt("EEE MMM d, h:mm a", d)
    fun header(d: Date) = fmt("EEEE, MMM d", d)
    fun short(d: Date) = fmt("MMM d", d)
    fun weekday(d: Date) = fmt("EEE", d)
    fun dayNumber(d: Date) = fmt("d", d)

    fun localDate(d: Date): LocalDate = d.toInstant().atZone(zone).toLocalDate()
    fun startOfDay(d: Date): Date = Date.from(localDate(d).atStartOfDay(zone).toInstant())
    fun addDays(d: Date, n: Int): Date = Date.from(d.toInstant().atZone(zone).plusDays(n.toLong()).toInstant())
    fun sameDay(a: Date, b: Date) = localDate(a) == localDate(b)
    /** Calendar days from `from`'s day to `to`'s day. */
    fun daysBetween(from: Date, to: Date): Int = ChronoUnit.DAYS.between(localDate(from), localDate(to)).toInt()
    fun atHour(day: Date, hour: Int, minute: Int = 0): Date =
        Date.from(localDate(day).atTime(hour, minute).atZone(zone).toInstant())

    /** "Today, 11:59 pm", "Fri, 8:30 am", "Tue Sep 29, 8:30 am". */
    fun due(d: Date?, now: Date = Date()): String {
        if (d == null) return "No due date"
        if (sameDay(d, now)) return "Today, ${time(d)}"
        if (sameDay(d, addDays(now, 1))) return "Tomorrow, ${time(d)}"
        val days = daysBetween(now, d)
        if (days in 1..6) return fmt("EEE, h:mm a", d)
        return dayTime(d)
    }

    /** "just now", "5 min ago", "2 h ago", "Oct 3". */
    fun ago(d: Date, now: Date = Date()): String {
        val s = (now.time - d.time) / 1000.0
        if (s < 60) return "just now"
        if (s < 3600) return "${(s / 60).toInt()} min ago"
        if (s < 86400) return "${(s / 3600).toInt()} h ago"
        return short(d)
    }

    fun pct(p: Double?, digits: Int = 1): String = if (p == null) "—" else String.format(Locale.US, "%.${digits}f%%", p)

    fun num(n: Double?): String {
        if (n == null) return "—"
        if (n == Math.rint(n) && abs(n) < 1e15) return n.roundToLong().toString()
        return String.format(Locale.US, "%.2f", n).trimEnd('0').trimEnd('.')
    }

    /** A grade change: "+0.24%", "−1.10%". Under half a hundredth rounds to nothing: "+0.00%", never "−0.00%". */
    fun signed(d: Double?): String {
        if (d == null) return ""
        return (if (d <= -0.005) "−" else "+") + String.format(Locale.US, "%.2f%%", abs(d))
    }

    /** A score's effect on the grade didn't move it (shown grey, not red or green). */
    fun unmoved(d: Double) = abs(d) < 0.005
}
