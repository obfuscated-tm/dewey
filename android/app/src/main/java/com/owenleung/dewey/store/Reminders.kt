package com.owenleung.dewey.store

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.MainActivity
import com.owenleung.dewey.R
import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.engine.CourseGrade
import com.owenleung.dewey.engine.MessageThread
import com.owenleung.dewey.engine.Notice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.Date
import kotlin.math.abs

/**
 * Local notifications only (no push server). Due reminders (2 h before an
 * unsubmitted dropbox, the night before a test, mornings, evenings) go to
 * AlarmManager; grade, message, material and signed-out alerts post right away
 * when a refresh finds one. Same rules as ios/App/Store/Reminders.swift.
 */
object Reminders {
    const val ENABLED_KEY = "remindersOn"
    const val HOUR_KEY = "reminderHour"
    const val GRADES_KEY = "gradeAlertsOn"
    const val SIGNED_OUT_KEY = "signedOutAlertOn"
    const val MESSAGES_KEY = "messageAlertsOn"
    const val MATERIALS_KEY = "materialAlertsOn"
    /** Set when Schoology ends the session; background refreshes stop until sign-in. */
    const val SESSION_ENDED_KEY = "sessionEnded"
    const val SOON_KEY = "dueSoonOn"
    const val TEST_NIGHT_KEY = "testNightOn"
    const val MORNING_KEY = "morningSummaryOn"
    const val MORNING_HOUR_KEY = "morningHour"
    private const val PLANNED_KEY = "plannedReminders"

    const val CH_DUE = "due"
    const val CH_GRADES = "grades"
    const val CH_INBOX = "inbox"
    const val CH_ACCOUNT = "account"
    const val CH_COUNTDOWN = "countdown"

    lateinit var app: Context
        private set

    val enabled get() = Prefs.bool(ENABLED_KEY)
    val hour get() = Prefs.int(HOUR_KEY, 19)
    val gradeAlerts get() = Prefs.bool(GRADES_KEY, true)
    val signedOutAlert get() = Prefs.bool(SIGNED_OUT_KEY, true)
    val sessionEnded get() = Prefs.bool(SESSION_ENDED_KEY)
    val messageAlerts get() = Prefs.bool(MESSAGES_KEY, true)
    val materialAlerts get() = Prefs.bool(MATERIALS_KEY, true)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CH_DUE, "Due reminders", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_GRADES, "New grades", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_INBOX, "Messages and materials", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_ACCOUNT, "Signed out of Schoology", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_COUNTDOWN, "Due countdowns", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The time left on one assignment, started from its screen."
            },
        ).forEach(nm::createNotificationChannel)
    }

    fun permitted(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Android 12+ needs "Alarms & reminders" allowed for on-the-minute reminders; new installs start without it. */
    val exactAllowed: Boolean
        get() = Build.VERSION.SDK_INT < 31 || app.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Opens the system page that allows exact alarms for Dewey. */
    fun openExactAlarmSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        runCatching {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Asks for the notification permission (Android 13+) through the open activity. */
    suspend fun requestPermission(): Boolean = if (permitted()) true else MainActivity.requestNotifications()

    // ── Due reminders ────────────────────────────────────────────────────

    /** What the due reminders need from an item (a plain copy, so the plan is testable). */
    data class DueInput(
        val id: String,
        val title: String,
        val course: String,
        val due: Date?,
        val isTest: Boolean,
        val hasDropbox: Boolean,
        val done: Boolean = false,
        val studied: Boolean = false,
        val turnedIn: Boolean = false,
        val submitted: Boolean = false,
    ) {
        /** Nothing left to remind about: handed in, or done / studied. */
        val finished get() = turnedIn || submitted || (if (isTest) studied else done)
    }

    data class Settings(
        val evening: Boolean = false,
        val hour: Int = 19,
        val soon: Boolean = true,
        val testNight: Boolean = true,
        val morning: Boolean = false,
        val morningHour: Int = 7,
    )

    @Serializable
    data class Planned(val id: String, val fire: Long, val title: String, val body: String, val schoologyId: String? = null)

    val settings: Settings
        get() = Settings(enabled, hour, Prefs.bool(SOON_KEY, true), Prefs.bool(TEST_NIGHT_KEY, true),
            Prefs.bool(MORNING_KEY), Prefs.int(MORNING_HOUR_KEY, 7))

    /**
     * The reminders to have pending at `now`, soonest first (at most 60):
     * - evening before, at `hour`: homework not done (`evening`, off by default);
     * - night before a test, at `hour`: not marked Studied (`testNight`);
     * - 2 h before: dropbox work not submitted on Schoology, even if marked done (`soon`);
     * - mornings, next 7 days at `morningHour`: what's due that day, skipped when nothing is (`morning`).
     */
    fun plan(items: List<DueInput>, s: Settings, now: Date): List<Planned> {
        val out = mutableListOf<Planned>()
        fun nightBefore(due: Date) = Fmt.atHour(Fmt.addDays(Fmt.startOfDay(due), -1), s.hour)
        fun with(course: String, rest: String) = if (course.isEmpty()) rest else "$course · $rest"
        for (it in items) {
            val due = it.due ?: continue
            if (!due.after(now)) continue
            if (!it.isTest && s.evening && !it.finished) {
                val fire = nightBefore(due)
                if (fire.after(now)) out += Planned("due-${it.id}", fire.time, it.title, with(it.course, "Due ${Fmt.due(due, fire)}"), it.id)
            }
            if (it.isTest && s.testNight && !it.finished) {
                val fire = nightBefore(due)
                if (fire.after(now)) out += Planned("test-${it.id}", fire.time, "Test tomorrow: ${it.title}", with(it.course, "Not marked Studied yet"), it.id)
            }
            val soon = Date(due.time - 2 * 3600_000L)
            if (s.soon && it.hasDropbox && !it.submitted && !it.turnedIn && soon.after(now)) {
                out += Planned("soon-${it.id}", soon.time, "Due in 2 hours: ${it.title}", with(it.course, "Not submitted on Schoology yet"), it.id)
            }
        }
        if (s.morning) {
            val today = Fmt.startOfDay(now)
            for (d in 0 until 7) {
                val day = Fmt.addDays(today, d)
                val fire = Fmt.atHour(day, s.morningHour)
                if (!fire.after(now)) continue
                val open = items.filter { i -> !i.finished && i.due?.let { Fmt.sameDay(it, day) && it.after(fire) } == true }
                    .sortedBy { it.due }
                if (open.isEmpty()) continue
                val tests = open.filter { it.isTest }
                val hw = open.filter { !it.isTest }
                var title = if (hw.isEmpty()) "" else "${hw.size} due today"
                if (tests.isNotEmpty()) title += (if (title.isEmpty()) "" else " · ") + (if (tests.size == 1) "test today" else "${tests.size} tests today")
                val lines = open.take(4).map { (if (it.isTest) "Test: " else "") + it.title }.toMutableList()
                if (open.size > 4) lines += "+${open.size - 4} more"
                val key = Fmt.localDate(day)
                out += Planned("morning-${key.year}-${key.monthValue}-${key.dayOfMonth}", fire.time,
                    title.replaceFirstChar { it.uppercase() }, lines.joinToString("\n"))
            }
        }
        return out.sortedBy { it.fire }.take(60)
    }

    /** "Test today", "Test tomorrow", "Test in 3 days"; null when it's past. */
    fun testCountdown(due: Date?, now: Date): String? {
        if (due == null || !due.after(now)) return null
        return when (val days = Fmt.daysBetween(now, due)) {
            0 -> "Test today"
            1 -> "Test tomorrow"
            else -> "Test in $days days"
        }
    }

    private var pending: Job? = null

    /** Every due reminder, worked out from the To Do list and replaced as a set. Debounced: saves call it often. */
    fun schedule(items: List<ItemState>, courses: List<Course>) {
        val inputs = items.map { it.toDueInput(courses) }
        val plan = plan(inputs, settings, Date())
        pending?.cancel()
        pending = CoroutineScope(Dispatchers.Default).launch {
            delay(300)
            apply(plan)
        }
    }

    fun ItemState.toDueInput(courses: List<Course>) = DueInput(
        schoologyId, title, courses.firstOrNull { it.sectionId == sectionId }?.shortTitle ?: courseName ?: "",
        due, isExam, hasDropbox, done, studied, turnedIn, submitted,
    )

    /** What's set with AlarmManager now (Settings → Developer). */
    fun planned(): List<Planned> = runCatching {
        Cache.json.decodeFromString(ListSerializer(Planned.serializer()), Prefs.string(PLANNED_KEY) ?: "[]")
    }.getOrDefault(emptyList()).filter { it.fire > System.currentTimeMillis() }

    private fun apply(plan: List<Planned>) {
        val am = app.getSystemService(AlarmManager::class.java)
        val keep = plan.map { it.id }.toSet()
        for (old in planned()) if (old.id !in keep) am.cancel(alarmIntent(old))
        val exact = exactAllowed
        for (p in plan) {
            val pi = alarmIntent(p)
            // Without exact alarms, setAndAllowWhileIdle can come up to an hour late; a 10-minute window
            // is the tightest Android allows (it can still wait for Doze to end).
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.fire, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, p.fire, 10 * 60_000L, pi)
        }
        Prefs.set(PLANNED_KEY, Cache.json.encodeToString(ListSerializer(Planned.serializer()), plan))
    }

    /** Settings → Developer: one reminder a minute from now, through the same alarm and receiver. Not saved in the plan. */
    fun testInOneMinute() {
        val am = app.getSystemService(AlarmManager::class.java)
        val p = Planned("dev-test", System.currentTimeMillis() + 60_000, "Test reminder", "From Settings → Developer")
        if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.fire, alarmIntent(p))
        else am.setWindow(AlarmManager.RTC_WAKEUP, p.fire, 10 * 60_000L, alarmIntent(p))
    }

    private fun alarmIntent(p: Planned): PendingIntent {
        val i = Intent(app, ReminderReceiver::class.java)
            .setData(Uri.parse("dewey-reminder://${p.id}"))
            .putExtra("id", p.id).putExtra("title", p.title).putExtra("body", p.body).putExtra("schoologyId", p.schoologyId)
        return PendingIntent.getBroadcast(app, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // ── Posting ──────────────────────────────────────────────────────────

    fun openIntent(link: String?): PendingIntent {
        val i = Intent(app, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(link ?: "dewey://todo")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(app, (link ?: "").hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun post(tag: String, channel: String, title: String, body: String, link: String? = null) {
        if (!permitted()) return
        val n = NotificationCompat.Builder(app, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull() ?: body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent(link))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(tag, 0, n) }
    }

    fun newGrades(course: String, items: List<CourseGrade.Item>, from: Double?, to: Double?) {
        if (!gradeAlerts) return
        post("grade-${System.nanoTime()}", CH_GRADES, "New grade in $course", gradeAlertBody(items, from, to), "dewey://grades")
    }

    /** "Lab 4: 18 / 20 · 92.1% → 92.6%"; with more items, the class change goes on its own line. */
    fun gradeAlertBody(items: List<CourseGrade.Item>, from: Double?, to: Double?): String {
        var change = ""
        if (to != null) {
            change = from?.let { if (abs(it - to) >= 0.05) "${Fmt.pct(it)} → ${Fmt.pct(to)}" else "still ${Fmt.pct(to)}" } ?: Fmt.pct(to)
        }
        val lines = items.take(3).map { "${it.title}: ${it.grade}" }.toMutableList()
        if (items.size > 3) lines += "+${items.size - 3} more"
        if (change.isEmpty()) return lines.joinToString("\n")
        if (lines.size == 1) return "${lines[0]} · $change"
        return (lines + "Class: $change").joinToString("\n")
    }

    /** One alert per new message from a teacher: sender, subject, preview. */
    fun newMessages(threads: List<MessageThread>) {
        if (!messageAlerts) return
        for (t in threads.take(3)) {
            post("message-${t.id}", CH_INBOX, t.from?.let { "Message from $it" } ?: "New message",
                listOfNotNull(t.subject, t.preview).filter { it.isNotEmpty() }.joinToString("\n"), "dewey://message/${t.id}")
        }
    }

    /** New materials posted in a class: one alert per Schoology notification. */
    fun newMaterials(notices: List<Notice>) {
        if (!materialAlerts) return
        for (n in notices.take(3)) {
            var body = n.items.take(3).joinToString("\n") { it.title } + if (n.items.size > 3) "\n+${n.items.size - 3} more" else ""
            if (body.isEmpty()) body = n.text
            post("notice-${n.key}", CH_INBOX, n.course?.let { "New in ${Course.short(it)}" } ?: "New in your classes", body, "dewey://inbox")
        }
    }

    /** Schoology ended the session. One alert per sign-out; `notify = false` when the student signed out on purpose. */
    fun sessionDidEnd(notify: Boolean) {
        if (sessionEnded) return
        Prefs.set(SESSION_ENDED_KEY, true)
        if (!notify || !signedOutAlert) return
        post("signed-out", CH_ACCOUNT, "Signed out of Schoology", "Open Dewey to sign in. Until then your list and widgets won't update.")
    }

    fun sessionDidStart() {
        Prefs.set(SESSION_ENDED_KEY, false)
        NotificationManagerCompat.from(app).cancel("signed-out", 0)
    }
}

/** A due reminder's alarm went off: post it. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.init(context)
        val id = intent.getStringExtra("id") ?: return
        val sid = intent.getStringExtra("schoologyId")
        Reminders.post(id, Reminders.CH_DUE, intent.getStringExtra("title") ?: "", intent.getStringExtra("body") ?: "",
            sid?.let { "dewey://assignment/$it" })
    }
}

/** Alarms don't survive a reboot or an app update: plan them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val m = AppModel.get(context)
        // schedule() waits 300 ms (debounce) on another thread: keep the process alive until it has run.
        val pending = goAsync()
        Reminders.schedule(m.todoItems(), m.courses)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ pending.finish() }, 2_000)
    }
}
