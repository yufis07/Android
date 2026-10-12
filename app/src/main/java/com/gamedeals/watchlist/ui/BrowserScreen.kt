package com.gamedeals.watchlist.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.data.WatchItem
import com.gamedeals.watchlist.price.GameCard
import com.gamedeals.watchlist.price.PriceInfo
import com.gamedeals.watchlist.price.PriceParser
import com.gamedeals.watchlist.price.PriceScripts
import com.gamedeals.watchlist.price.UrlUtils
import kotlinx.coroutines.launch

data class AddDraft(val url: String, val store: Store, val title: String, val info: PriceInfo?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    state: BrowserState,
    watchItems: List<WatchItem>,
    onSave: (draft: AddDraft, title: String, target: Double?) -> Unit,
    onScanPage: suspend () -> List<GameCard>,
    onImport: (List<GameCard>) -> Unit,
    onMessage: (String) -> Unit,
) {
    var draft by remember { mutableStateOf<AddDraft?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var importCards by remember { mutableStateOf<List<GameCard>?>(null) }
    val scope = rememberCoroutineScope()
    val watchedUrls = watchItems.map { it.url }.toSet()
    val currentKey = UrlUtils.normalize(state.url)
    val existing = watchItems.firstOrNull { it.url == currentKey }

    BackHandler(enabled = state.canGoBack) { state.webView.goBack() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                navigationIcon = { StoreBadge(state.store, Modifier.padding(start = 12.dp, end = 4.dp)) },
                title = {
                    Column {
                        Text(
                            state.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            UrlUtils.pretty(state.url),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { state.webView.goBack() }, enabled = state.canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(onClick = { state.webView.goForward() }, enabled = state.canGoForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(onClick = { state.webView.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Import games from this page") },
                            onClick = {
                                menuOpen = false
                                scope.launch {
                                    val cards = onScanPage()
                                    if (cards.isEmpty()) {
                                        onMessage("No games found on this page")
                                    } else {
                                        importCards = cards
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Home") },
                            leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                state.webView.loadUrl(state.store.homeUrl)
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val url = state.url
                    state.webView.evaluateJavascript(PriceScripts.EXTRACT) { result ->
                        val info = PriceParser.fromJsResult(result)
                        draft = AddDraft(
                            url = url,
                            store = Store.fromUrl(url) ?: state.store,
                            title = info?.title ?: PriceParser.cleanTitle(state.title) ?: state.title,
                            info = info,
                        )
                    }
                },
                icon = { Icon(if (existing != null) Icons.Filled.Check else Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (existing != null) "Watching" else "Watch") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (state.loading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AndroidView(
                factory = {
                    state.webView.also { (it.parent as? ViewGroup)?.removeView(it) }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    importCards?.let { cards ->
        ImportGamesDialog(
            cards = cards,
            alreadyWatched = watchedUrls,
            onDismiss = { importCards = null },
            onImport = { selected ->
                onImport(selected)
                importCards = null
            },
        )
    }

    draft?.let { d ->
        AddWatchDialog(
            draft = d,
            existing = existing,
            onDismiss = { draft = null },
            onConfirm = { title, target ->
                onSave(d, title, target)
                draft = null
            },
        )
    }
}
