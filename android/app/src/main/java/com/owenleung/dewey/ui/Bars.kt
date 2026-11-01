package com.owenleung.dewey.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import kotlinx.coroutines.launch

/** A pushed screen: back arrow, a title, actions on the right, an optional bottom bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    topExtra: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val nav = nav
    Scaffold(
        containerColor = C.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, style = Theme.headline, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = C.text) }
                    },
                    actions = actions,
                    windowInsets = TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = C.background, titleContentColor = C.text, actionIconContentColor = C.text),
                )
                topExtra()
            }
        },
        bottomBar = bottomBar,
        content = content,
    )
}

/** A scrolling column with the screens' standard padding, and pull to refresh when given. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollPage(
    pad: PaddingValues = PaddingValues(0.dp),
    refresh: (suspend () -> Unit)? = null,
    horizontal: Dp = 16.dp,
    spacing: Dp = 14.dp,
    /** The page's scroller, for a screen that scrolls it itself (Plan's grip at an edge). */
    scroll: androidx.compose.foundation.ScrollState? = null,
    /** Room under the last row (To Do's + button). */
    bottom: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val state = scroll ?: rememberScrollState()
    val body: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxSize().verticalScroll(state).padding(horizontal = horizontal).padding(top = 8.dp, bottom = bottom),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing),
            content = content,
        )
    }
    Box(Modifier.padding(pad).fillMaxSize()) {
        if (refresh == null) body()
        else PullToRefreshBox(isRefreshing = refreshing, onRefresh = {
            scope.launch { refreshing = true; try { refresh() } finally { refreshing = false } }
        }, modifier = Modifier.fillMaxWidth()) { body() }
    }
}
