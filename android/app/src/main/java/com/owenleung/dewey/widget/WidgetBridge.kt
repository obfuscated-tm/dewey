package com.owenleung.dewey.widget

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.glance.appwidget.updateAll
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.R
import com.owenleung.dewey.store.Cache
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.ItemState
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.store.Reminders
import com.owenleung.dewey.store.TodoLogic
import com.owenleung.dewey.ui.theme.Theme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.Date

/** What the app hands the widgets: the open To Do rows, soonest first (Shared/WidgetData.swift). */
@Serializable
data class WidgetItem(
    val id: String,
    val title: String,
    val course: String,
    val due: Long? = null,
    val isTest: Boolean = false,
    /** Done (Studied for a test), in the app or from the widget's circle. */
    val marked: Boolean = false,
)

/** A graded item for "Latest grades". `day` is when it moved the grade (YYYY-MM-DD). */
@Serializable
data class WidgetGrade(val id: String, val sectionId: String, val title: String, val course: String, val grade: String, val day: String? = null)

@Serializable
data class WidgetSnapshot(
    val items: List<WidgetItem> = emptyList(),
    val updatedAt: Long = 0,
    val signedIn: Boolean = false,
    val accentHex: String = "#1D5EA8",
    val grades: List<WidgetGrade> = emptyList(),
) {
    fun overdue(now: Long) = items.filter { (it.due ?: Long.MAX_VALUE) < now }
    fun upcoming(now: Long) = items.filter { (it.due ?: Long.MAX_VALUE) >= now }
    /** Tests from today on, soonest first. */
    fun tests(now: Date): List<WidgetItem> {
        val start = Fmt.startOfDay(now).time
        return items.filter { it.isTest && (it.due ?: Long.MAX_VALUE) >= start }
    }

    companion object {
        private const val KEY = "widgetSnapshot"
        fun load(): WidgetSnapshot = runCatching { Cache.json.decodeFromString(serializer(), Prefs.string(KEY) ?: "") }.getOrDefault(WidgetSnapshot())

        /**
         * The latest snapshot, for widgets to collect. Glance keeps a widget's session (and its provideGlance
         * locals) alive between updates, so a snapshot read once there would go stale.
         */
        val latest: kotlinx.coroutines.flow.MutableStateFlow<WidgetSnapshot?> = kotlinx.coroutines.flow.MutableStateFlow(null)

        fun save(s: WidgetSnapshot) {
            Prefs.set(KEY, Cache.json.encodeToString(serializer(), s))
            latest.value = s
        }
    }
}

/**
 * Hands the open To Do rows to the home-screen widgets and keeps due
 * countdowns (the Live Activity on iOS: an ongoing notification here) in step
 * with the student's marks and Schoology's status.
 */
object WidgetBridge {
    private var lastKey: String? = null
    private var pending: Job? = null

    /** Coalesces bursts of saves into one write and one widget update. */
    fun publish(m: AppModel) {
        // The tour's pretend week never reaches the widgets (or takes their marks).
        if (m.practice) return
        pending?.cancel()
        pending = m.scope.launch {
            delay(400)
            publishNow(m)
        }
    }

    suspend fun publishNow(m: AppModel) {
        if (m.practice) return
        pending?.cancel()
        write(m)
        syncCountdowns(m)
    }

    private suspend fun write(m: AppModel) {
        val now = Date()
        val open = m.todoItems()
            .filter { !m.isFinished(it) && !TodoLogic.isOlder(it.due, now) }
            .sortedBy { it.dueAt ?: Long.MAX_VALUE }
        // The list's first 20, plus the next 3 tests even if they're further out.
        val rows = open.take(20).toMutableList()
        val today = Fmt.startOfDay(now).time
        rows += open.drop(20).filter { it.isExam && (it.dueAt ?: Long.MAX_VALUE) >= today }.take(3)
        val snap = WidgetSnapshot(
            items = rows.map { WidgetItem(it.schoologyId, it.title, course(m, it), it.dueAt, it.isExam, if (it.isExam) it.studied else it.done) },
            updatedAt = (m.todoAsOf ?: now).time,
            signedIn = m.session == AppModel.Session.SIGNED_IN,
            accentHex = Theme.accentHex,
            grades = latestGrades(m, WidgetSnapshot.load().grades),
        )
        // Compare without the timestamp so a no-change refresh doesn't redraw.
        val key = Cache.json.encodeToString(WidgetSnapshot.serializer(), snap.copy(updatedAt = 0))
        if (key == lastKey) return
        lastKey = key
        WidgetSnapshot.save(snap)
        updateWidgets(m.context)
    }

    suspend fun updateWidgets(ctx: Context) {
        runCatching { TodoWidget().updateAll(ctx) }
        runCatching { TestsWidget().updateAll(ctx) }
    }

    /** The 3 most recently graded items across classes; classes not loaded this run keep the last snapshot's. */
    fun latestGrades(m: AppModel, old: List<WidgetGrade>): List<WidgetGrade> {
        val all = old.filter { m.grades[it.sectionId] == null }.toMutableList()
        for ((sid, g) in m.grades) {
            val day = HashMap<String, String>()
            for (p in g.history) p.day?.let { d -> p.items.forEach { day[it] = d } }
            val name = m.course(sid)?.shortTitle ?: ""
            all += g.items.filter { it.earned != null }.map {
                WidgetGrade(it.id, sid, it.title, name, it.grade, day[it.id] ?: it.due?.take(10))
            }
        }
        return all.sortedByDescending { it.day ?: "" }.take(3)
    }

    fun course(m: AppModel, it: ItemState) = it.sectionId?.let { s -> m.course(s)?.shortTitle } ?: it.courseName ?: ""

    // ── Countdowns ───────────────────────────────────────────────────────

    /** A countdown can start 12 h before the due time (like the iOS Live Activity). */
    const val COUNTDOWN_LEAD = 12 * 3600_000L
    private const val ACTIVE_KEY = "countdowns"

    sealed class Window {
        data object None : Window()                 // no due time, or already past it
        data class Opens(val at: Date) : Window()   // due later; the countdown can start from this time
        data object Open : Window()
    }

    fun countdownWindow(due: Date?, now: Date = Date()): Window {
        if (due == null || !due.after(now)) return Window.None
        return if (due.time - now.time < COUNTDOWN_LEAD) Window.Open else Window.Opens(Date(due.time - COUNTDOWN_LEAD))
    }

    fun active(): Set<String> = Prefs.strings(ACTIVE_KEY).toSet()
    fun isCounting(id: String) = id in active()

    /** Returns an error in the student's words, or null when it started. */
    fun startCountdown(m: AppModel, it: ItemState): String? {
        val due = it.due ?: return "This has no due time."
        if (!Reminders.permitted()) return "Notifications are off for Dewey. Turn them on in Android Settings › Apps › Dewey."
        post(m.context, it.schoologyId, it.title, course(m, it), it.isExam, due)
        Prefs.set(ACTIVE_KEY, (active() + it.schoologyId).toList())
        return null
    }

    fun stopCountdown(ctx: Context, id: String) {
        NotificationManagerCompat.from(ctx).cancel("countdown-$id", 0)
        Prefs.set(ACTIVE_KEY, (active() - id).toList())
    }

    private fun post(ctx: Context, id: String, title: String, course: String, isTest: Boolean, due: Date) {
        val n = NotificationCompat.Builder(ctx, Reminders.CH_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(listOf(course, (if (isTest) "Test · " else "Due ") + Fmt.due(due)).filter { it.isNotEmpty() }.joinToString(" · "))
            .setWhen(due.time)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setTimeoutAfter(due.time - System.currentTimeMillis() + 3600_000)
            .setContentIntent(Reminders.openIntent("dewey://assignment/$id"))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify("countdown-$id", 0, n) }
    }

    /** Finished or gone from the list → "Done" for 15 minutes, then gone. Due date moved → update. */
    private fun syncCountdowns(m: AppModel) {
        val ctx = m.context
        for (id in active()) {
            val it = m.item(id)
            if (it == null || m.isFinished(it) || it.dismissed || (it.due?.before(Date()) ?: true)) {
                stopCountdown(ctx, id)
                if (it != null && m.isFinished(it)) {
                    val n = NotificationCompat.Builder(ctx, Reminders.CH_COUNTDOWN)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(it.title)
                        .setContentText(if (it.submitted) "Submitted on Schoology" else "Done")
                        .setTimeoutAfter(15 * 60_000)
                        .setAutoCancel(true)
                        .build()
                    runCatching { NotificationManagerCompat.from(ctx).notify("countdown-$id", 0, n) }
                }
            } else {
                post(ctx, id, it.title, course(m, it), it.isExam, it.due!!)
            }
        }
    }
}
