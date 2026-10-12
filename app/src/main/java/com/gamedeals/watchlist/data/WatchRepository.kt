package com.gamedeals.watchlist.data

import com.gamedeals.watchlist.price.GameCard
import com.gamedeals.watchlist.price.PriceFormat
import com.gamedeals.watchlist.price.PriceInfo
import com.gamedeals.watchlist.price.UrlUtils

class WatchRepository(private val dao: WatchDao) {

    /** Adds a game to the watchlist, or updates it if the same page is already watched. */
    suspend fun addOrUpdate(url: String, store: Store, title: String, info: PriceInfo?, target: Double?) {
        val normalized = UrlUtils.normalize(url)
        val now = System.currentTimeMillis()
        val price = info?.price
        val currency = info?.currency ?: PriceFormat.symbolFrom(info?.priceText)
        val existing = dao.findByUrl(normalized)
        if (existing != null) {
            dao.update(
                existing.copy(
                    title = title,
                    targetPrice = target,
                    notifiedPrice = null,
                    currentPrice = price ?: existing.currentPrice,
                    priceText = if (price != null) info?.priceText else existing.priceText,
                    currency = currency ?: existing.currency,
                    imageUrl = existing.imageUrl ?: info?.imageUrl,
                    lowestPrice = minOfNullable(existing.lowestPrice, price),
                    lastChecked = if (price != null) now else existing.lastChecked,
                    lastError = if (price != null) null else existing.lastError,
                )
            )
        } else {
            dao.insert(
                WatchItem(
                    url = normalized,
                    store = store,
                    title = title,
                    imageUrl = info?.imageUrl,
                    currentPrice = price,
                    priceText = info?.priceText,
                    currency = currency,
                    targetPrice = target,
                    lowestPrice = price,
                    lastChecked = if (price != null) now else null,
                )
            )
        }
    }

    /**
     * Adds a game only if it isn't watched yet (used for syncing from the sites, so your own
     * target price is never overwritten). Returns true if it was added.
     */
    suspend fun addIfMissing(url: String, store: Store, title: String, info: PriceInfo?): Boolean {
        val normalized = UrlUtils.normalize(url)
        if (dao.findByUrl(normalized) != null) return false
        val price = info?.price
        val id = dao.insert(
            WatchItem(
                url = normalized,
                store = store,
                title = title,
                imageUrl = info?.imageUrl,
                currentPrice = price,
                priceText = if (price != null) info?.priceText else null,
                currency = info?.currency ?: PriceFormat.symbolFrom(info?.priceText),
                lowestPrice = price,
                lastChecked = if (price != null) System.currentTimeMillis() else null,
            )
        )
        return id > 0
    }

    /** Adds every card that isn't watched yet; returns how many were added. */
    suspend fun importCards(defaultStore: Store, cards: List<GameCard>): Int =
        cards.count { card ->
            addIfMissing(card.url, Store.fromUrl(card.url) ?: defaultStore, card.title, card.info)
        }

    /** Re-keys items saved by older versions (slug URLs) to the canonical game URL. */
    suspend fun migrateUrls() {
        val seen = mutableSetOf<String>()
        for (item in dao.getAll().sortedBy { it.addedAt }) {
            val canonical = UrlUtils.normalize(item.url)
            when {
                canonical in seen -> dao.delete(item) // duplicate of an earlier entry
                canonical != item.url -> {
                    seen += canonical
                    runCatching { dao.update(item.copy(url = canonical)) }.onFailure { dao.delete(item) }
                }
                else -> seen += canonical
            }
        }
    }

    suspend fun update(item: WatchItem) = dao.update(item)

    suspend fun delete(item: WatchItem) = dao.delete(item)

    suspend fun restore(item: WatchItem) {
        dao.insert(item)
    }

    companion object {
        fun minOfNullable(a: Double?, b: Double?): Double? = when {
            a == null -> b
            b == null -> a
            else -> minOf(a, b)
        }
    }
}
