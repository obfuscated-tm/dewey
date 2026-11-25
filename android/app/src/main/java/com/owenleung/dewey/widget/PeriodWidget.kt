package com.owenleung.dewey.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as UnitColorProvider
import com.owenleung.dewey.DeweyApp
import com.owenleung.dewey.R
import com.owenleung.dewey.store.BellSchedule
import com.owenleung.dewey.store.BellStore
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.ui.theme.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.math.ceil

// "Now in class" (ios/Widgets/PeriodWidget.swift): the period in session with the time left, and
// what's next. Everything comes from the bell schedule typed in Settings → Schedule (BellSchedule);
// nothing here reaches Schoology. The countdown is a Chronometer that ticks by itself; a non-waking
// alarm a minute (from an hour before the first bell) redraws the ring and bar, then one at midnight.
// Android has no Lock Screen widgets on phones, so only the 2×2 and 4×2 Home Screen sizes are ported.

class PeriodWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        scheduleNext(context)
        provideContent {
            val t by tick.collectAsState()
            Content(Date(maxOf(t, System.currentTimeMillis())))
        }
    }

    @Composable
    private fun Content(now: Date) {
        val ctx = LocalContext.current
        val s = BellStore.widgetSchedule(now)
        val v = View(s, now, W.accent(WidgetSnapshot.load().accentHex))
        Column(GlanceModifier.fillMaxSize().background(W.bg).cornerRadius(20.dp).padding(12.dp).clickable(W.open(ctx, "dewey://todo"))) {
            if (LocalSize.current.width >= 250.dp) v.Medium(ctx) else v.Small(ctx)
        }
    }

    /** One drawing of the widget at `now`. */
    private class View(val schedule: BellSchedule, val now: Date, val accent: UnitColorProvider) {
        val m = schedule.moment(now)

        /** Whole minutes from now to `d`, rounded up: 9:40:20 to 9:50 is 10. */
        fun minutes(to: Date) = maxOf(0, ceil((to.time - now.time) / 60_000.0).toInt())

        /** The next class, when it's close enough to count down to (within the hour). */
        val soon: BellSchedule.Block? = m.next?.takeIf { m.now == null && minutes(it.start) <= 60 }

        val idle: String = when {
            schedule.isEmpty -> "Set up in Settings"
            m.dayEnd == null -> "No school today"
            m.next != null -> "${m.next.title} at ${Fmt.time(m.next.start)}"
            else -> "Done for today"
        }

        fun color(b: BellSchedule.Block): UnitColorProvider =
            b.period.color?.let { ColorProvider(Theme.classColor(it, false), Theme.classColor(it, true)) } ?: accent

        fun progress(b: BellSchedule.Block) = if (m.now != null) b.progress(now) else 0.0

        /** "12:03 left" in class, "in 4:10" before one, worded the same in both sizes. */
        @Composable
        fun Timer(ctx: Context, b: BellSchedule.Block, size: Float) {
            val inClass = m.now != null
            val small = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = W.secondary)
            Row(verticalAlignment = Alignment.Bottom) {
                if (!inClass) Text("in ", style = small)
                // Glance sizes a RemoteViews box as if its text were default size, so the big
                // countdown got its lower half cut off by the line below. Give it its own height.
                AndroidRemoteViews(timerViews(ctx, if (inClass) b.end else b.start, size),
                    modifier = GlanceModifier.height((size * 1.45f).dp))
                if (inClass) Text(" left", style = small)
            }
        }

        @Composable
        fun Idle() = Text(idle, maxLines = 3, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = W.text))

        /** 2×2, laid out like the To Do widget: a ring and the room on top, then the timer, the class and what comes after it. */
        @Composable
        fun Small(ctx: Context) {
            val b = m.now ?: soon ?: return Idle()
            Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Image(ImageProvider(ring(ctx, progress(b), b)), contentDescription = null, modifier = GlanceModifier.size(46.dp))
                Spacer(GlanceModifier.defaultWeight())
                if (b.period.room.isNotEmpty()) Text(b.period.room, maxLines = 1,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = W.secondary))
            }
            Spacer(GlanceModifier.defaultWeight())
            Timer(ctx, b, 24f)
            Text(b.title, maxLines = 2, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color(b)))
            val n = m.next
            if (m.now != null && n != null) Text("then ${n.title}", maxLines = 1, style = TextStyle(fontSize = 12.sp, color = W.secondary))
            }
        }

        /** 4×2: now on the left (timer, bar, the bell's clock time); on the right the next class, then the rest of today. */
        @Composable
        fun Medium(ctx: Context) {
            val b = m.now ?: soon ?: return Idle()
            val inClass = m.now != null
            val upcoming = schedule.blocks(now).filter { it.start.after(now) && it.start != soon?.start }
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(b.title, maxLines = 2, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color(b)))
                    Text(listOf(b.period.room, if (inClass) "ends ${Fmt.time(b.end)}" else "starts ${Fmt.time(b.start)}").filter { it.isNotEmpty() }.joinToString(" · "),
                        maxLines = 1, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = W.secondary))
                    Spacer(GlanceModifier.defaultWeight())
                    Timer(ctx, b, 28f)
                    Spacer(GlanceModifier.height(4.dp))
                    LinearProgressIndicator(progress(b).toFloat(), GlanceModifier.fillMaxWidth(), color = color(b),
                        backgroundColor = ColorProvider(android.graphics.Color.argb(48, 128, 128, 128).toComposeColor(), android.graphics.Color.argb(64, 200, 200, 200).toComposeColor()))
                }
                val n = upcoming.firstOrNull() ?: return@Row
                Spacer(GlanceModifier.width(16.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(if (inClass) "Next" else "Then", style = TextStyle(fontSize = 12.sp, color = W.secondary))
                    Text(n.title, maxLines = 1, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color(n)))
                    Text(listOf(Fmt.time(n.start), n.period.room).filter { it.isNotEmpty() }.joinToString(" · "), maxLines = 1,
                        style = TextStyle(fontSize = 12.sp, color = W.secondary))
                    val later = upcoming.drop(1)
                    if (later.isNotEmpty()) {
                        Spacer(GlanceModifier.height(6.dp))
                        for (l in later.take(2)) Row {
                            Text(Fmt.time(l.start), style = TextStyle(fontSize = 12.sp, color = W.secondary))
                            Spacer(GlanceModifier.width(6.dp))
                            Text(l.title, maxLines = 1, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color(l)))
                        }
                        if (later.size > 2) Text("${later.size - 2} more", style = TextStyle(fontSize = 12.sp, color = W.secondary))
                    }
                }
            }
        }

        /** The ring fills as the class goes (empty while waiting for one), drawn as a bitmap: Glance has no arcs. */
        fun ring(ctx: Context, value: Double, b: BellSchedule.Block): Bitmap {
            val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val tint = b.period.color?.let { Theme.classColor(it, dark).toArgb() }
                ?: android.graphics.Color.parseColor(WidgetSnapshot.load().accentHex)
            val px = (46 * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            val stroke = 7 * ctx.resources.displayMetrics.density
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = RectF(stroke / 2, stroke / 2, px - stroke / 2, px - stroke / 2)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
            c.drawArc(r, 0f, 360f, false, p.apply { color = android.graphics.Color.argb(64, 128, 128, 128) })
            if (value > 0) c.drawArc(r, -90f, (360 * value).toFloat(), false, p.apply { color = tint })
            return bmp
        }

        private fun Int.toComposeColor() = androidx.compose.ui.graphics.Color(this)
        private fun androidx.compose.ui.graphics.Color.toArgb(): Int =
            android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
    }

    companion object {
        /** Bumped on each redraw so a running Glance session recomposes. */
        val tick = MutableStateFlow(0L)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** The countdown to `target`, ticking on its own (a Chronometer counting down). */
        fun timerViews(ctx: Context, target: Date, sizeSp: Float): RemoteViews = RemoteViews(ctx.packageName, R.layout.period_timer).apply {
            setChronometer(R.id.period_timer, SystemClock.elapsedRealtime() + (target.time - System.currentTimeMillis()), null, true)
            setChronometerCountDown(R.id.period_timer, true)
            setTextViewTextSize(R.id.period_timer, TypedValue.COMPLEX_UNIT_SP, sizeSp)
        }

        suspend fun update(ctx: Context) {
            tick.value = System.currentTimeMillis()
            runCatching { PeriodWidget().updateAll(ctx) }
        }

        /** The schedule (or a widget test) changed: redraw now. */
        fun refresh(ctx: Context) {
            val app = ctx.applicationContext
            scope.launch { update(app) }
        }

        private fun pending(ctx: Context) = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, PeriodTickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        /**
         * The next redraw: each minute from an hour before the first bell to the last, else midnight (or when a widget
         * test ends). Not a waking alarm: with the screen off it waits, and the Chronometer is right when it comes on.
         */
        fun scheduleNext(ctx: Context) {
            val now = Date()
            val schedule = BellStore.widgetSchedule(now)
            val m = schedule.moment(now)
            var at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val start = m.dayStart
            val end = m.dayEnd
            if (start != null && end != null && now.before(end)) at = maxOf((now.time / 60_000 + 1) * 60_000, start.time - 3_600_000)
            // Redraw on the bell itself too: past its target the Chronometer counts on below zero ("-00:04").
            schedule.blocks(now).flatMap { listOf(it.start.time, it.end.time) }.filter { it > now.time }.minOrNull()
                ?.let { at = minOf(at, it) }
            BellStore.testUntil?.let { at = minOf(at, it.time) }
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            // Exact when "Alarms & reminders" is allowed (Settings asks for it for reminders); otherwise Android
            // may stretch a short window, and the countdown can sit below zero until the redraw comes.
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, at, pending(ctx))
            else am.setWindow(AlarmManager.RTC, at, 10_000, pending(ctx))
        }

        fun cancel(ctx: Context) {
            ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx))
        }
    }
}

class PeriodWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PeriodWidget()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        PeriodWidget.cancel(context)
    }
}

/** The minute redraw; redrawing schedules the next one. */
class PeriodTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val done = goAsync()
        DeweyApp.setUp(context.applicationContext)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                PeriodWidget.update(context.applicationContext)
                PeriodWidget.scheduleNext(context.applicationContext)
            } finally { done.finish() }
        }
    }
}
