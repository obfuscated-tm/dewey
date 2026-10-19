package com.owenleung.dewey.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.MainActivity
import com.owenleung.dewey.R
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.ItemStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

// Home-screen widgets (Widgets/DeweyWidgets.swift). They can't reach
// Schoology: the app updates them after each refresh or mark.

private object W {
    val bg = ColorProvider(Color(0xFFFFFFFF), Color(0xFF1C1C1E))
    val text = ColorProvider(Color(0xFF15191D), Color(0xFFF2F2F7))
    val secondary = ColorProvider(Color(0xFF4F5761), Color(0xFFC1C5CC))
    val late = ColorProvider(Color(0xFFA63A0B), Color(0xFFFF8A5C))
    val tagBg = ColorProvider(Color(0xFF15191D), Color(0xFFF2F2F7))
    val tagText = ColorProvider(Color(0xFFFFFFFF), Color(0xFF1C1C1E))

    fun accent(hex: String): UnitColorProvider {
        val c = Color(android.graphics.Color.parseColor(hex))
        return ColorProvider(c, Color(android.graphics.Color.parseColor("#9DC0EA")).takeIf { hex == "#1D5EA8" } ?: lighten(c))
    }

    private fun lighten(c: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()), hsv)
        hsv[1] *= 0.6f; hsv[2] = 1f
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    enum class Bucket(val label: String) { LATE("LATE"), TODAY("TODAY"), TOMORROW("TOMORROW"), WEEK("THIS WEEK"), LATER("LATER") }

    fun days(d: Long?, now: Date): Int = d?.let { Fmt.daysBetween(now, Date(it)) } ?: 99

    fun bucket(d: Long?, now: Date): Bucket {
        if (d == null) return Bucket.LATER
        if (d < now.time) return Bucket.LATE
        return when (days(d, now)) {
            0 -> Bucket.TODAY
            1 -> Bucket.TOMORROW
            in 2..6 -> Bucket.WEEK
            else -> Bucket.LATER
        }
    }

    /** Short due text for a row: a time today/tomorrow, else a weekday or date; "3 d late" when overdue. */
    fun short(d: Long?, now: Date): String {
        if (d == null) return ""
        val date = Date(d)
        if (d < now.time) {
            val n = Fmt.daysBetween(date, now)
            return if (n == 0) "Was due ${Fmt.time(date)}" else "$n d late"
        }
        val n = days(d, now)
        if (n == 0 || n == 1) return Fmt.time(date)
        if (n < 7) return Fmt.weekday(date)
        return Fmt.short(date)
    }

    fun countdown(d: Long?, now: Date) = when (val n = days(d, now)) { 0 -> "today"; 1 -> "tomorrow"; else -> "in $n days" }

    fun open(ctx: Context, link: String) =
        actionStartActivity(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(link)))
}

private val idKey = ActionParameters.Key<String>("id")

/** The widget's circle: Done (Studied for a test) in Dewey only, never on Schoology. */
class ToggleDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[idKey] ?: return
        withContext(Dispatchers.Main) {
            val m = AppModel.get(context)
            val it = ItemStore.real.item(id) ?: return@withContext
            if (it.turnedIn) return@withContext
            m.setProgress(it, if (m.progress(it) == AppModel.Progress.TODO) AppModel.Progress.DONE else AppModel.Progress.TODO)
            WidgetBridge.publishNow(m)
        }
    }
}

class TodoWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = WidgetSnapshot.load()
        provideContent {
            val s by WidgetSnapshot.latest.collectAsState()
            TodoContent(context, s ?: first)
        }
    }

    @Composable
    private fun TodoContent(ctx: Context, s: WidgetSnapshot) {
        val now = Date()
        val size = LocalSize.current
        val accent = W.accent(s.accentHex)
        val open = s.items
        Column(
            GlanceModifier.fillMaxSize().background(W.bg).cornerRadius(20.dp).padding(12.dp).clickable(W.open(ctx, "dewey://todo")),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("To Do", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = W.text))
                Spacer(GlanceModifier.defaultWeight())
                val late = s.overdue(now.time).count { !it.marked }
                if (late > 0) Text("$late late", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = W.late))
                else Text("${open.count { !it.marked }}", style = TextStyle(fontSize = 12.sp, color = W.secondary))
            }
            Spacer(GlanceModifier.height(6.dp))
            if (!s.signedIn || open.isEmpty()) {
                Text(if (s.signedIn) "Nothing due. Nice." else "Open Dewey to sign in.",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = W.secondary))
                return@Column
            }
            val max = ((size.height.value - 48) / 34).toInt().coerceIn(1, 12)
            var last: W.Bucket? = null
            var shown = 0
            for (it in open) {
                if (shown >= max) break
                val b = W.bucket(it.due, now)
                if (b != last && size.height > 180.dp) {
                    Text(b.label, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (b == W.Bucket.LATE) W.late else W.secondary), modifier = GlanceModifier.padding(top = 4.dp))
                    last = b
                }
                Row(GlanceModifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        ImageProvider(if (it.isTest) (if (it.marked) R.drawable.w_square_done else R.drawable.w_square)
                                      else (if (it.marked) R.drawable.w_circle_done else R.drawable.w_circle)),
                        contentDescription = if (it.marked) "Done" else "Mark done",
                        modifier = GlanceModifier.size(22.dp).clickable(actionRunCallback<ToggleDoneAction>(actionParametersOf(idKey to it.id))),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Column(GlanceModifier.defaultWeight().clickable(W.open(ctx, "dewey://assignment/${it.id}"))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (it.isTest) {
                                Box(GlanceModifier.background(W.tagBg).cornerRadius(3.dp).padding(horizontal = 3.dp)) {
                                    Text("TEST", style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = W.tagText))
                                }
                                Spacer(GlanceModifier.width(4.dp))
                            }
                            Text(it.title, maxLines = 1, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = if (it.marked) W.secondary else W.text))
                        }
                        if (size.width > 200.dp && it.course.isNotEmpty()) {
                            Text(it.course, maxLines = 1, style = TextStyle(fontSize = 11.sp, color = W.secondary))
                        }
                    }
                    Text(W.short(it.due, now), style = TextStyle(fontSize = 11.sp,
                        color = if ((it.due ?: Long.MAX_VALUE) < now.time) W.late else accent))
                }
                shown++
            }
        }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()
}

/** Next tests with their countdown and Studied mark; on a bigger widget, the latest grades too. */
class TestsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 250.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = WidgetSnapshot.load()
        provideContent {
            val s by WidgetSnapshot.latest.collectAsState()
            Content(context, s ?: first)
        }
    }

    @Composable
    private fun Content(ctx: Context, s: WidgetSnapshot) {
        val now = Date()
        val size = LocalSize.current
        val accent = W.accent(s.accentHex)
        val tests = s.tests(now)
        Column(GlanceModifier.fillMaxSize().background(W.bg).cornerRadius(20.dp).padding(12.dp).clickable(W.open(ctx, "dewey://todo"))) {
            if (!s.signedIn) {
                Text("Open Dewey to sign in.", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = W.secondary))
                return@Column
            }
            if (size.width < 200.dp) {
                // Small: the next test, big.
                val t = tests.firstOrNull()
                Text("NEXT TEST", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = W.secondary))
                if (t == null) {
                    Text("None coming up", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = W.text))
                } else {
                    Text(W.countdown(t.due, now), style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent))
                    Text(t.title, maxLines = 2, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = W.text))
                    Text(t.course, maxLines = 1, style = TextStyle(fontSize = 11.sp, color = W.secondary))
                    Text(if (t.marked) "Studied" else "Not studied", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        color = if (t.marked) accent else W.secondary))
                }
                return@Column
            }
            Text("Tests", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = W.text))
            if (tests.isEmpty()) Text("None coming up", style = TextStyle(fontSize = 12.sp, color = W.secondary))
            for (t in tests.take(3)) {
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(W.open(ctx, "dewey://assignment/${t.id}")),
                    verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(if (t.marked) R.drawable.w_square_done else R.drawable.w_square),
                        contentDescription = if (t.marked) "Studied" else "Mark studied",
                        modifier = GlanceModifier.size(20.dp).clickable(actionRunCallback<ToggleDoneAction>(actionParametersOf(idKey to t.id))))
                    Spacer(GlanceModifier.width(8.dp))
                    Text(t.title, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = W.text))
                    Text(W.countdown(t.due, now), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = accent))
                }
            }
            if (size.height > 200.dp && s.grades.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                Text("Latest grades", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = W.text))
                for (g in s.grades) {
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(W.open(ctx, "dewey://grades")),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(GlanceModifier.defaultWeight()) {
                            Text(g.title, maxLines = 1, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = W.text))
                            Text(g.course, maxLines = 1, style = TextStyle(fontSize = 11.sp, color = W.secondary))
                        }
                        Text(g.grade, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = W.text))
                    }
                }
            }
        }
    }
}

class TestsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TestsWidget()
}
