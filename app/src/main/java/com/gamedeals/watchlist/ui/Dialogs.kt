package com.gamedeals.watchlist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gamedeals.watchlist.data.Prefs
import com.gamedeals.watchlist.data.WatchItem
import com.gamedeals.watchlist.price.GameCard
import com.gamedeals.watchlist.price.PriceFormat
import com.gamedeals.watchlist.price.PriceParser
import com.gamedeals.watchlist.price.UrlUtils
import com.gamedeals.watchlist.web.AdBlocker
import com.gamedeals.watchlist.web.LiteMode

private fun priceFilter(input: String) = input.filter { it.isDigit() || it == '.' || it == ',' }

@Composable
fun AddWatchDialog(
    draft: AddDraft,
    existing: WatchItem?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, target: Double?) -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title ?: draft.title) }
    var target by remember { mutableStateOf(existing?.targetPrice?.let(PriceFormat::plain) ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { StoreBadge(draft.store, size = 36.dp) },
        title = { Text(if (existing != null) "Update watch" else "Add to watchlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Game") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Current price: ${draft.info?.display() ?: "not detected"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = priceFilter(it) },
                    label = { Text("Alert me at or below (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!UrlUtils.isGamePage(draft.url)) {
                    Text(
                        "Tip: open a game's own page before tapping Watch so its price can be tracked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title.trim(), PriceParser.parseNumber(target)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EditWatchDialog(
    item: WatchItem,
    onDismiss: () -> Unit,
    onSave: (WatchItem) -> Unit,
) {
    var title by remember { mutableStateOf(item.title) }
    var target by remember { mutableStateOf(item.targetPrice?.let(PriceFormat::plain) ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { StoreBadge(item.store, size = 36.dp) },
        title = { Text("Edit watch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Game") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = priceFilter(it) },
                    label = { Text("Target price (empty = none)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        item.copy(
                            title = title.trim(),
                            targetPrice = PriceParser.parseNumber(target),
                            notifiedPrice = null,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SettingsDialog(
    currentInterval: Int,
    blockAds: Boolean,
    onBlockAdsChange: (Boolean) -> Unit,
    autoSync: Boolean,
    onAutoSyncChange: (Boolean) -> Unit,
    liteMode: Boolean,
    onLiteModeChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
    onIntervalChange: (Int) -> Unit,
    onRunBackgroundCheck: () -> Unit,
    onSignOut: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(currentInterval) }
    var adsBlocked by remember { mutableStateOf(blockAds) }
    var syncing by remember { mutableStateOf(autoSync) }
    var lite by remember { mutableStateOf(liteMode) }
    var confirmSignOut by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Background price checks", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Prefs.INTERVAL_OPTIONS.forEach { hours ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == hours, onClick = { selected = hours }),
                    ) {
                        RadioButton(selected = selected == hours, onClick = { selected = hours })
                        Spacer(Modifier.width(4.dp))
                        Text(if (hours == 0) "Off" else "Every $hours hours")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRunBackgroundCheck, modifier = Modifier.fillMaxWidth()) {
                    Text("Run background check now")
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Block ads & trackers", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Google (AdMob, AdSense, DoubleClick) and other ad networks · " +
                                "${AdBlocker.listSize} ad domains · ${AdBlocker.blocked} blocked this session",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = adsBlocked,
                        onCheckedChange = {
                            adsBlocked = it
                            onBlockAdsChange(it)
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sync site watchlists", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Games you add with a site's own Watchlist / Wishlist button, or that show as " +
                                "on your lists while you browse, are added here automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = syncing,
                        onCheckedChange = {
                            syncing = it
                            onAutoSyncChange(it)
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Lite mode", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Faster, lighter pages: skips web fonts, videos and audio in the site tabs · " +
                                "${LiteMode.skipped} skipped this session",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = lite,
                        onCheckedChange = {
                            lite = it
                            onLiteModeChange(it)
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Backup", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Save your watchlist to a file, or restore it after reinstalling or on a new phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Export") }
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Accounts", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Sign in on each site's own tab (Xbox / PlayStation / Nintendo). Your login is remembered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out of all sites")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selected != currentInterval) onIntervalChange(selected)
                onDismiss()
            }) { Text("Done") }
        },
    )

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("This clears cookies and site data for xbdeals.net, psdeals.net and ntdeals.net. Your watchlist is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    onSignOut()
                    confirmSignOut = false
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ImportGamesDialog(
    cards: List<GameCard>,
    alreadyWatched: Set<String>,
    onDismiss: () -> Unit,
    onImport: (List<GameCard>) -> Unit,
) {
    val newCards = remember(cards) { cards.filter { UrlUtils.normalize(it.url) !in alreadyWatched } }
    // Pre-select games that are on your site watchlist/wishlist; if none are marked, select all.
    val selected = remember(newCards) {
        val marked = newCards.filter { it.onSiteList }
        mutableStateListOf<GameCard>().apply { addAll(marked.ifEmpty { newCards }) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import games") },
        text = {
            Column {
                Text(
                    if (newCards.isEmpty()) "All ${cards.size} games on this page are already in your watchlist."
                    else "${newCards.size} new game${if (newCards.size == 1) "" else "s"} found on this page" +
                        if (cards.size > newCards.size) " (${cards.size - newCards.size} already watched)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (newCards.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selected.clear(); selected.addAll(newCards) }) { Text("Select all") }
                        TextButton(onClick = { selected.clear() }) { Text("Select none") }
                    }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(newCards, key = { it.url }) { card ->
                            val checked = card in selected
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = checked, onClick = {
                                        if (checked) selected.remove(card) else selected.add(card)
                                    }),
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(card.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                    val price = card.info.priceText ?: "price unknown"
                                    Text(
                                        if (card.onSiteList) "$price · on your site list" else price,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = { onImport(selected.toList()) },
            ) { Text("Import ${selected.size}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
