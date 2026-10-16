package com.owenleung.dewey.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.MessageThread
import com.owenleung.dewey.ui.assignment.AssignmentScreen
import com.owenleung.dewey.ui.classes.ClassesScreen
import com.owenleung.dewey.ui.classes.FileViewer
import com.owenleung.dewey.ui.classes.MaterialPageScreen
import com.owenleung.dewey.ui.classes.MaterialsScreen
import com.owenleung.dewey.ui.grades.CourseGradesScreen
import com.owenleung.dewey.ui.grades.GradeDetailScreen
import com.owenleung.dewey.ui.grades.GradesScreen
import com.owenleung.dewey.ui.grades.WhatIfSheet
import com.owenleung.dewey.ui.inbox.InboxScreen
import com.owenleung.dewey.ui.inbox.MessageScreen
import com.owenleung.dewey.ui.schoology.SchoologySheet
import com.owenleung.dewey.ui.schoology.SchoologyTab
import com.owenleung.dewey.ui.settings.DeveloperScreen
import com.owenleung.dewey.ui.settings.SettingsScreen
import com.owenleung.dewey.ui.settings.SignInScreen
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.DeweyTheme
import com.owenleung.dewey.ui.theme.Theme
import com.owenleung.dewey.ui.todo.ToDoScreen
import com.owenleung.dewey.web.WebController

@Composable
fun RootView(model: AppModel) {
    DeweyTheme {
        val nav = remember { Nav(model) }
        CompositionLocalProvider(LocalModel provides model, LocalNav provides nav) {
            Box(Modifier.fillMaxSize().background(C.background)) {
                when (model.session) {
                    AppModel.Session.CHECKING -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = C.accent)
                    AppModel.Session.SIGNED_OUT -> SignInScreen()
                    AppModel.Session.SIGNED_IN -> MainScreen()
                }
                Toast(model.toast, Modifier.align(Alignment.BottomCenter))
            }
        }
        LaunchedEffect(Unit) { model.boot() }
    }
}

@Composable
private fun Toast(text: String?, modifier: Modifier) {
    AnimatedVisibility(text != null, modifier.navigationBarsPadding().padding(bottom = 96.dp, start = 20.dp, end = 20.dp),
        enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        Text(text ?: "", style = Theme.subhead.copy(fontWeight = FontWeight.Medium), color = C.card,
            modifier = Modifier.clip(RoundedCornerShape(Theme.Radius.card)).background(C.text).padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

private data class TabItem(val title: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("To Do", Icons.AutoMirrored.Outlined.FormatListBulleted),
    TabItem("Grades", Icons.Outlined.BarChart),
    TabItem("Inbox", Icons.Outlined.Inbox),
    TabItem("Classes", Icons.Outlined.GridView),
    TabItem("Schoology", Icons.Outlined.Language),
)

@Composable
fun MainScreen() {
    val model = model
    val nav = nav
    val ctx = LocalContext.current
    val holder = rememberSaveableStateHolder()
    // The Schoology tab keeps its page while the student uses other tabs.
    val schoologyWeb = remember { WebController(ctx, "${Engine.ORIGIN}/home") }

    BackHandler(enabled = nav.stacks[nav.tab].isNotEmpty() || (nav.tab == Nav.SCHOOLOGY && schoologyWeb.canGoBack)) {
        if (!nav.pop() && nav.tab == Nav.SCHOOLOGY) schoologyWeb.goBack()
    }

    LaunchedEffect(model.deepLink) {
        val link = model.deepLink ?: return@LaunchedEffect
        model.deepLink = null
        val parts = link.removePrefix("dewey://").split("/")
        when (parts.firstOrNull()) {
            "assignment" -> parts.getOrNull(1)?.let { nav.tab = Nav.TODO; nav.stacks[Nav.TODO].clear(); nav.push(Route.Assignment(it)) }
            "todo" -> nav.tab = Nav.TODO
            "grades" -> nav.tab = Nav.GRADES
            "inbox" -> nav.tab = Nav.INBOX
            "message" -> parts.getOrNull(1)?.let { id ->
                nav.tab = Nav.INBOX
                nav.push(Route.Message(model.inbox.threads.firstOrNull { it.id == id } ?: MessageThread(id = id)))
            }
        }
    }

    LaunchedEffect(Unit) {
        val at = model.todoAsOf
        if (at == null || System.currentTimeMillis() - at.time > 120_000) model.refreshTodo()
    }

    Scaffold(
        containerColor = C.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = C.card, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, t ->
                    val badge = when (i) {
                        Nav.TODO -> model.overdueCount()
                        Nav.GRADES -> model.newGradeCount()
                        Nav.INBOX -> model.inbox.newCount()
                        else -> 0
                    }
                    NavigationBarItem(
                        selected = nav.tab == i,
                        onClick = { nav.select(i) },
                        icon = {
                            BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) { Icon(t.icon, contentDescription = null) }
                        },
                        label = { Text(t.title, style = Theme.caption) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = C.accent, selectedTextColor = C.accent, indicatorColor = C.accentSoft,
                            unselectedIconColor = C.secondary, unselectedTextColor = C.secondary,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val route = nav.stacks[nav.tab].lastOrNull()
            val key = "${nav.tab}:${nav.stacks[nav.tab].size}:${route?.key ?: "root"}"
            holder.SaveableStateProvider(key) {
                if (route != null) RouteScreen(route)
                else Box(Modifier.statusBarsPadding()) {
                    when (nav.tab) {
                        Nav.TODO -> ToDoScreen()
                        Nav.GRADES -> GradesScreen()
                        Nav.INBOX -> InboxScreen()
                        Nav.CLASSES -> ClassesScreen()
                        else -> SchoologyTab(schoologyWeb)
                    }
                }
            }
        }
    }

    nav.sheet?.let { SchoologySheet(it) { nav.sheet = null } }
    nav.file?.let { FileViewer(it) { nav.file = null } }
    nav.whatIf?.let { (item, section) -> WhatIfSheet(item, section) { nav.whatIf = null } }
}

@Composable
fun RouteScreen(r: Route) {
    when (r) {
        is Route.Assignment -> AssignmentScreen(r.id)
        is Route.CourseGrades -> CourseGradesScreen(r.sectionId)
        is Route.GradeDetail -> GradeDetailScreen(r.sectionId, r.itemId)
        is Route.Materials -> MaterialsScreen(r.sectionId, r.folderId, r.title, r.trail)
        is Route.MaterialPage -> MaterialPageScreen(r.row)
        is Route.Message -> MessageScreen(r.thread)
        Route.Settings -> SettingsScreen()
        Route.Developer -> DeveloperScreen()
    }
}
