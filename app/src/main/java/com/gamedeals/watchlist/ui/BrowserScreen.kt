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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.data.WatchItem
import com.gamedeals.watchlist.price.PriceInfo
import com.gamedeals.watchlist.price.PriceParser
import com.gamedeals.watchlist.price.PriceScripts
import com.gamedeals.watchlist.price.UrlUtils

data class AddDraft(val url: String, val store: Store, val title: String, val info: PriceInfo?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    state: BrowserState,
    watchItems: List<WatchItem>,
    onSave: (draft: AddDraft, title: String, target: Double?) -> Unit,
) {
    var draft by remember { mutableStateOf<AddDraft?>(null) }
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
                    IconButton(onClick = { state.webView.loadUrl(state.store.homeUrl) }) {
                        Icon(Icons.Filled.Home, contentDescription = "Home")
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
