package com.owenleung.dewey.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.engine.MaterialRow
import com.owenleung.dewey.engine.MessageThread
import com.owenleung.dewey.web.WebConfig

/** A screen pushed on a tab (NavigationStack destinations on iOS). */
sealed class Route {
    open val key: String get() = toString()
    data class Assignment(val id: String) : Route()
    data class CourseGrades(val sectionId: String) : Route()
    data class GradeDetail(val sectionId: String, val itemId: String) : Route()
    data class Materials(val sectionId: String, val folderId: String?, val title: String, val trail: List<String>) : Route()
    data class MaterialPage(val row: MaterialRow) : Route()
    data class Message(val thread: MessageThread) : Route()
    data object Settings : Route()
    data object Developer : Route()
}

/** What the Schoology sheet (1c) opens. `assignmentId`: re-check its dropbox on close. `google`: offer the full-screen frame. */
data class SheetTarget(val url: String, val assignmentId: String? = null, val google: Boolean = false)

/** A file to show natively (3c). */
data class FileTarget(val url: String, val title: String)

/** Tabs, each with its own back stack, plus the sheets over everything. */
class Nav(val model: AppModel) {
    companion object {
        const val TODO = 0
        const val GRADES = 1
        const val INBOX = 2
        const val CLASSES = 3
        const val SCHOOLOGY = 4
    }

    var tab by mutableIntStateOf(TODO)
    val stacks = List(5) { mutableStateListOf<Route>() }

    var sheet by mutableStateOf<SheetTarget?>(null)
    var file by mutableStateOf<FileTarget?>(null)
    /** "What if I get __ / N?" for one item: (itemId, sectionId). */
    var whatIf by mutableStateOf<Pair<String, String?>?>(null)

    fun push(r: Route) { stacks[tab] += r }
    fun pop(): Boolean {
        val s = stacks[tab]
        if (s.isEmpty()) return false
        s.removeAt(s.lastIndex)
        return true
    }
    fun select(t: Int) {
        if (t == tab) stacks[t].clear() else tab = t
    }

    fun openSheet(t: SheetTarget) { sheet = t }

    /**
     * Where a link opens (3b's table): Docs/Drive → Google's app if installed,
     * else the sheet; everything else → the sheet.
     */
    fun openLink(ctx: Context, url: String, assignmentId: String? = null) {
        val abs = WebConfig.abs(url)
        if (LinkOpener.isGoogle(abs) && LinkOpener.openInApp(ctx, abs)) return
        openSheet(SheetTarget(abs, assignmentId))
    }
}

object LinkOpener {
    fun isGoogle(url: String): Boolean {
        val h = Uri.parse(url).host ?: return false
        return h == "docs.google.com" || h == "drive.google.com"
    }

    /** Opens the link in a non-browser app that claims it (the Docs app). False when none does. */
    fun openInApp(ctx: Context, url: String): Boolean = if (android.os.Build.VERSION.SDK_INT < 30) false else try {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        ctx.startActivity(i)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("no Nav") }
val LocalModel = staticCompositionLocalOf<AppModel> { error("no AppModel") }

val nav: Nav @Composable get() = LocalNav.current
val model: AppModel @Composable get() = LocalModel.current
