package com.gamedeals.watchlist.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamedeals.watchlist.MainActivity
import com.gamedeals.watchlist.data.AppDatabase
import com.gamedeals.watchlist.data.Prefs
import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.data.WatchRepository
import com.gamedeals.watchlist.work.PriceCheckScheduler
import kotlinx.coroutines.launch

private const val WATCHLIST_TAB = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(activity: MainActivity) {
    val dao = remember { AppDatabase.get(activity).watchDao() }
    val repo = remember { WatchRepository(dao) }
    val itemsFlow = remember { dao.observeAll() }
    val watchItems by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(WATCHLIST_TAB) }
    var lastBrowserTab by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(tab) {
        if (tab < WATCHLIST_TAB) lastBrowserTab = tab
        activity.showBrowser(if (tab < WATCHLIST_TAB) Store.entries[tab] else null)
    }
    var intervalHours by remember { mutableIntStateOf(Prefs.intervalHours(activity)) }
    var blockAds by remember { mutableStateOf(Prefs.blockAds(activity)) }
    var autoSync by remember { mutableStateOf(Prefs.autoSync(activity)) }
    var liteMode by remember { mutableStateOf(Prefs.liteMode(activity)) }

    // Notification taps and shared links ask us to open a specific page.
    val pending = activity.pendingOpenUrl
    LaunchedEffect(pending) {
        if (pending != null) {
            val store = Store.fromUrl(pending)
            if (store != null) {
                tab = store.ordinal
                activity.browser(store).webView.loadUrl(pending)
            }
            activity.pendingOpenUrl = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                Store.entries.forEach { store ->
                    NavigationBarItem(
                        selected = tab == store.ordinal,
                        onClick = { tab = store.ordinal },
                        icon = { StoreBadge(store, size = 26.dp) },
                        label = { Text(store.label) },
                    )
                }
                NavigationBarItem(
                    selected = tab == WATCHLIST_TAB,
                    onClick = { tab = WATCHLIST_TAB },
                    icon = {
                        BadgedBox(badge = {
                            if (watchItems.isNotEmpty()) Badge { Text("${watchItems.size}") }
                        }) {
                            Icon(Icons.Filled.Star, contentDescription = null)
                        }
                    },
                    label = { Text("Watchlist") },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // The browser stays composed (just hidden) while the Watchlist is shown, and the same
            // screen is reused for all three sites, so switching tabs never reloads a page.
            val browserTab = if (tab < WATCHLIST_TAB) tab else lastBrowserTab
            if (browserTab >= 0) {
                val store = Store.entries[browserTab]
                val browserVisible = tab < WATCHLIST_TAB
                ShowIf(browserVisible) {
                    BrowserScreen(
                        state = activity.browser(store),
                        container = activity.browserContainer,
                        visible = browserVisible,
                        watchItems = watchItems,
                        onSave = { draft, title, target ->
                            scope.launch {
                                repo.addOrUpdate(draft.url, draft.store, title, draft.info, target)
                                activity.toast("“$title” saved to your watchlist")
                            }
                        },
                        onScanPage = { activity.scanPage(activity.browser(store).webView) },
                        onImport = { cards ->
                            scope.launch {
                                val added = activity.importCards(store, cards)
                                activity.toast("Imported $added game${if (added == 1) "" else "s"} to your watchlist")
                            }
                        },
                        onMessage = activity::toast,
                    )
                }
            }
            if (tab == WATCHLIST_TAB) {
                WatchlistScreen(
                    watchItems = watchItems,
                    refreshProgress = activity.refreshProgress,
                    intervalHours = intervalHours,
                    blockAds = blockAds,
                    autoSync = autoSync,
                    onAutoSyncChange = { enabled ->
                        autoSync = enabled
                        Prefs.setAutoSync(activity, enabled)
                    },
                    liteMode = liteMode,
                    onLiteModeChange = { enabled ->
                        liteMode = enabled
                        Prefs.setLiteMode(activity, enabled)
                        activity.reloadBrowsers()
                    },
                    onExport = activity::exportWatchlist,
                    onImport = activity::importWatchlist,
                    onBlockAdsChange = { enabled ->
                        blockAds = enabled
                        Prefs.setBlockAds(activity, enabled)
                        activity.reloadBrowsers()
                    },
                    onOpen = { item ->
                        tab = item.store.ordinal
                        activity.browser(item.store).webView.loadUrl(item.url)
                    },
                    onRefreshAll = activity::refreshAllNow,
                    onUpdate = { scope.launch { repo.update(it) } },
                    onDelete = { scope.launch { repo.delete(it) } },
                    onRestore = { scope.launch { repo.restore(it) } },
                    onIntervalChange = { hours ->
                        intervalHours = hours
                        Prefs.setIntervalHours(activity, hours)
                        PriceCheckScheduler.schedule(activity, hours, replace = true)
                    },
                    onRunBackgroundCheck = { PriceCheckScheduler.runNow(activity) },
                    onSignOut = activity::signOutAll,
                )
            }
        }
    }
}

/** Keeps [content] composed (so its state and views survive) but only places/draws it when [visible]. */
@Composable
private fun ShowIf(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    if (visible) placeable.place(0, 0)
                }
            }
    ) { content() }
}
