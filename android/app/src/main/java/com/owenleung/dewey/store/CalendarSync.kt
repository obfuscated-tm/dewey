package com.owenleung.dewey.store

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.ContextCompat
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.widget.WidgetBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

/**
 * Settings › To Do › "Show in the Calendar app": a "Dewey" calendar on the
 * phone (a local one, never synced to an account) holding open work, tests and
 * the student's own tasks. Dewey only ever touches that one calendar: events
 * are matched by their dewey://assignment/{id} link, added, moved or removed on
 * each refresh, and work marked done drops off. Nothing reaches Schoology
 * (ios/App/Store/CalendarSync.swift).
 */
object CalendarSync {
    const val ENABLED_KEY = "calendarSyncOn"
    private const val ACCOUNT = "Dewey"
    private var pending: Job? = null

    val enabled: Boolean get() = Prefs.bool(ENABLED_KEY)

    val permissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun hasAccess(ctx: Context) = permissions.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    /** Coalesces bursts of saves into one pass. */
    fun schedule(m: AppModel) {
        if (!enabled || m.practice || !hasAccess(m.context)) return
        pending?.cancel()
        pending = m.scope.launch {
            delay(1000)
            val wanted = wanted(m)
            withContext(Dispatchers.IO) { runCatching { write(m.context, wanted) } }
        }
    }

    // ── What goes in ─────────────────────────────────────────────────────

    data class Spec(val id: String, val title: String, val location: String, val start: Long, val end: Long, val allDay: Boolean)

    data class Input(val id: String, val title: String, val course: String, val due: Date?, val isTest: Boolean, val finished: Boolean)

    /** Two weeks back to three months ahead. */
    const val BACK = 14 * 86_400_000L
    const val AHEAD = 90 * 86_400_000L

    /**
     * The event for one item, or null when it stays out (no date, finished, out of range).
     * A test is all day on its day (Schoology's time is often when it closes); so is work
     * due at 11:59 pm. Other work is a 30-minute block ending when it's due.
     * An all-day event's start and end are UTC midnights, as the Calendar provider wants.
     */
    fun spec(i: Input, now: Date, zone: ZoneId = Fmt.zone): Spec? {
        val due = i.due ?: return null
        if (i.finished || due.time <= now.time - BACK || due.time >= now.time + AHEAD) return null
        val local = due.toInstant().atZone(zone)
        val allDay = i.isTest || (local.hour == 23 && local.minute == 59)
        val start: Long
        val end: Long
        if (allDay) {
            val day = local.toLocalDate()
            start = day.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            end = day.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        } else {
            start = due.time - 30 * 60_000
            end = due.time
        }
        return Spec(i.id, if (i.isTest) "Test: ${i.title}" else i.title, i.course, start, end, allDay)
    }

    private fun wanted(m: AppModel): List<Spec> {
        val now = Date()
        return m.todoItems().mapNotNull {
            spec(Input(it.schoologyId, it.title, WidgetBridge.course(m, it), it.due, it.isExam,
                // A test stays until its day, studied or not.
                if (it.isExam) it.submitted || it.turnedIn else m.isFinished(it)), now)
        }
    }

    // ── Writing ──────────────────────────────────────────────────────────

    private fun asSync(u: Uri): Uri = u.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun link(id: String) = "dewey://assignment/$id"

    private fun write(ctx: Context, wanted: List<Spec>) {
        if (!enabled) return
        val cal = calendarId(ctx, create = true) ?: return
        val cr = ctx.contentResolver
        val byId = wanted.associateBy { it.id }
        val seen = HashSet<String>()
        cr.query(Events.CONTENT_URI, arrayOf(Events._ID, Events.CUSTOM_APP_URI, Events.TITLE, Events.EVENT_LOCATION, Events.DTSTART, Events.DTEND, Events.ALL_DAY),
            "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0", arrayOf("$cal"), null)?.use { c ->
            while (c.moveToNext()) {
                val eid = c.getLong(0)
                val id = c.getString(1)?.removePrefix("dewey://assignment/")
                val s = id?.let { byId[it] }
                val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eid)
                if (s == null || id in seen) { cr.delete(asSync(uri), null, null); continue }
                seen += id
                if (c.getString(2) != s.title || (c.getString(3) ?: "") != s.location || c.getLong(4) != s.start || c.getLong(5) != s.end || (c.getInt(6) == 1) != s.allDay) {
                    cr.update(uri, values(s, cal), null, null)
                }
            }
        }
        for (s in wanted) if (s.id !in seen) cr.insert(Events.CONTENT_URI, values(s, cal))
    }

    private fun values(s: Spec, cal: Long) = ContentValues().apply {
        put(Events.CALENDAR_ID, cal)
        put(Events.TITLE, s.title)
        put(Events.EVENT_LOCATION, s.location.ifEmpty { null })
        put(Events.DTSTART, s.start)
        put(Events.DTEND, s.end)
        put(Events.ALL_DAY, if (s.allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, if (s.allDay) "UTC" else TimeZone.getDefault().id)
        put(Events.DESCRIPTION, "From Dewey. Open it in Dewey: ${link(s.id)}")
        put(Events.CUSTOM_APP_PACKAGE, "com.owenleung.dewey")
        put(Events.CUSTOM_APP_URI, link(s.id))
    }

    /** Turning it off takes the whole Dewey calendar away. */
    fun disable(ctx: Context) {
        pending?.cancel()
        Prefs.set(ENABLED_KEY, false)
        if (!hasAccess(ctx)) return
        runCatching { calendarId(ctx, create = false)?.let { ctx.contentResolver.delete(asSync(ContentUris.withAppendedId(Calendars.CONTENT_URI, it)), null, null) } }
    }

    private fun calendarId(ctx: Context, create: Boolean): Long? {
        val cr = ctx.contentResolver
        cr.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID), "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL), null)?.use { if (it.moveToFirst()) return it.getLong(0) }
        if (!create) return null
        val v = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, "Dewey")
            put(Calendars.CALENDAR_DISPLAY_NAME, "Dewey")
            put(Calendars.CALENDAR_COLOR, 0xFF0F6E56.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        return cr.insert(asSync(Calendars.CONTENT_URI), v)?.let { ContentUris.parseId(it) }
    }
}
