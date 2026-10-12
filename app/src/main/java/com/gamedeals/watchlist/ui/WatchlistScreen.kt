package com.gamedeals.watchlist.ui

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.data.WatchItem
import com.gamedeals.watchlist.data.isAtTarget
import com.gamedeals.watchlist.price.PriceFormat
import kotlinx.coroutines.launch

private enum class SortMode(val label: String) { RECENT("Recent"), TARGET("Closest to target"), PRICE("Price") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    watchItems: List<WatchItem>,
    refreshProgress: Pair<Int, Int>?,
    intervalHours: Int,
    onOpen: (WatchItem) -> Unit,
    onRefreshAll: () -> Unit,
    onUpdate: (WatchItem) -> Unit,
    onDelete: (WatchItem) -> Unit,
    onRestore: (WatchItem) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onRunBackgroundCheck: () -> Unit,
    onSignOut: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortMode.RECENT.name) }
    var editing by remember { mutableStateOf<WatchItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val shown = watchItems
        .filter { filter == null || it.store.name == filter }
        .let { list ->
            when (SortMode.valueOf(sort)) {
                SortMode.RECENT -> list
                SortMode.PRICE -> list.sortedBy { it.currentPrice ?: Double.MAX_VALUE }
                SortMode.TARGET -> list.sortedBy { item ->
                    val t = item.targetPrice
                    val c = item.currentPrice
                    if (t == null || c == null || t <= 0.0) Double.MAX_VALUE else c / t
                }
            }
        }
    val reached = watchItems.count { it.isAtTarget }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Price Watchlist")
                        val subtitle = when {
                            refreshProgress != null ->
                                "Checking ${refreshProgress.first + 1} of ${refreshProgress.second}…"
                            reached > 0 -> "$reached game${if (reached == 1) "" else "s"} at target price"
                            else -> "${watchItems.size} game${if (watchItems.size == 1) "" else "s"} watched"
                        }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (reached > 0 && refreshProgress == null) DealGreen
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (refreshProgress != null) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = onRefreshAll, enabled = watchItems.isNotEmpty()) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Check all prices now")
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text("All (${watchItems.size})") },
                )
                Store.entries.forEach { store ->
                    val count = watchItems.count { it.store == store }
                    FilterChip(
                        selected = filter == store.name,
                        onClick = { filter = if (filter == store.name) null else store.name },
                        label = { Text("${store.label} ($count)") },
                        leadingIcon = { StoreBadge(store, size = 18.dp) },
                    )
                }
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sort:", style = MaterialTheme.typography.labelMedium)
                SortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = sort == mode.name,
                        onClick = { sort = mode.name },
                        label = { Text(mode.label) },
                    )
                }
            }

            if (shown.isEmpty()) {
                EmptyState(hasAny = watchItems.isNotEmpty())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.id }) { item ->
                        WatchCard(
                            item = item,
                            onOpen = { onOpen(item) },
                            onEdit = { editing = item },
                            onDelete = {
                                onDelete(item)
                                scope.launch {
                                    val result = snackbar.showSnackbar(
                                        message = "Removed ${item.title}",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) onRestore(item)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    editing?.let { item ->
        EditWatchDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = {
                onUpdate(it)
                editing = null
            },
        )
    }
    if (showSettings) {
        SettingsDialog(
            currentInterval = intervalHours,
            onDismiss = { showSettings = false },
            onIntervalChange = onIntervalChange,
            onRunBackgroundCheck = {
                onRunBackgroundCheck()
                scope.launch { snackbar.showSnackbar("Background check queued") }
            },
            onSignOut = onSignOut,
        )
    }
}

@Composable
private fun EmptyState(hasAny: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (hasAny) "Nothing here for this filter" else "Your watchlist is empty",
            style = MaterialTheme.typography.titleMedium,
        )
        if (!hasAny) {
            Spacer(Modifier.height(8.dp))
            Text(
                "1. Open the Xbox, PlayStation or Nintendo tab and sign in.\n" +
                    "2. Browse to a game's page.\n" +
                    "3. Tap “Watch” and (optionally) set a target price.\n\n" +
                    "You'll get a notification when it drops.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchCard(
    item: WatchItem,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreBadge(item.store)
                Spacer(Modifier.width(10.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.padding(end = 10.dp)) {
                PriceCell(
                    label = "Now",
                    value = if (item.currentPrice == null) "—"
                    else item.priceText ?: PriceFormat.format(item.currentPrice, item.currency),
                    highlight = item.isAtTarget,
                    modifier = Modifier.weight(1f),
                )
                PriceCell("Target", PriceFormat.format(item.targetPrice, item.currency), modifier = Modifier.weight(1f))
                PriceCell("Lowest seen", PriceFormat.format(item.lowestPrice, item.currency), modifier = Modifier.weight(1f))
            }
            if (item.isAtTarget) {
                Spacer(Modifier.height(8.dp))
                Surface(color = DealGreen, shape = MaterialTheme.shapes.small) {
                    Text(
                        "🎯 Target price reached",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val status = item.lastError?.let { "⚠ $it" }
                ?: item.lastChecked?.let {
                    "Checked " + DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                }
                ?: "Not checked yet"
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.lastError != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PriceCell(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) DealGreen else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
