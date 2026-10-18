package com.gamedeals.watchlist.sync

import android.content.Context
import com.gamedeals.watchlist.data.AppDatabase
import com.gamedeals.watchlist.data.Prefs
import com.gamedeals.watchlist.data.WatchRepository
import com.gamedeals.watchlist.price.GameCard
import com.gamedeals.watchlist.price.PriceUpdater
import com.gamedeals.watchlist.price.UrlUtils

/** Runs a full site-list sync and applies it to the watchlist (used by the app and the background worker). */
object ListSyncRunner {

    data class Outcome(
        val added: Int,
        val pricesUpdated: Int,
        val summary: String,
        val results: List<SiteListSync.StoreResult>,
    ) {
        val anySignedIn: Boolean get() = results.any { it.error == null }
    }

    suspend fun run(context: Context, allowWebView: Boolean, onProgress: (String) -> Unit = {}): Outcome {
        val results = SiteListSync(context).syncAll(allowWebView, onProgress)
        val dao = AppDatabase.get(context).watchDao()
        val repo = WatchRepository(dao)
        var updated = 0
        var added = 0
        for (r in results) {
            updated += refreshPrices(context, r.cards) // before import, so new games aren't counted
            added += repo.importCards(r.store, r.cards)
        }
        val summary = results.joinToString(" · ") { r ->
            r.store.short + " " + when {
                r.error == "Not signed in" -> "not signed in"
                r.error != null -> "failed"
                else -> "${r.cards.size} game${if (r.cards.size == 1) "" else "s"}"
            }
        }
        Prefs.setListSyncResult(context, summary)
        return Outcome(added, updated, summary, results)
    }

    /** List pages show current prices, so watched games get a free price update (with alerts). */
    private suspend fun refreshPrices(context: Context, cards: List<GameCard>): Int {
        if (cards.isEmpty()) return 0
        val byUrl = AppDatabase.get(context).watchDao().getAll().associateBy { it.url }
        var changed = 0
        for (card in cards) {
            val price = card.info.price ?: continue
            val item = byUrl[UrlUtils.normalize(card.url)] ?: continue
            if (item.currentPrice != price) changed++
            PriceUpdater.apply(context, item.id, card.info, notify = true)
        }
        return changed
    }
}
