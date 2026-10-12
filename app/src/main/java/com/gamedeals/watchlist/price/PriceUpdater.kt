package com.gamedeals.watchlist.price

import android.content.Context
import com.gamedeals.watchlist.Notifier
import com.gamedeals.watchlist.data.AppDatabase
import com.gamedeals.watchlist.data.WatchItem
import com.gamedeals.watchlist.data.WatchRepository

object PriceUpdater {
    /**
     * Stores a fresh price check result for [itemId] and, if [notify] is set,
     * posts "target reached" / "price drop" notifications.
     */
    suspend fun apply(
        context: Context,
        itemId: Long,
        info: PriceInfo?,
        error: String? = null,
        notify: Boolean,
    ): WatchItem? {
        val dao = AppDatabase.get(context).watchDao()
        val old = dao.findById(itemId) ?: return null
        val now = System.currentTimeMillis()
        val price = info?.price
        var updated = if (price == null) {
            old.copy(lastChecked = now, lastError = error ?: "Price not found on page")
        } else {
            old.copy(
                currentPrice = price,
                priceText = info?.priceText,
                currency = info?.currency ?: old.currency,
                imageUrl = old.imageUrl ?: info?.imageUrl,
                lowestPrice = WatchRepository.minOfNullable(old.lowestPrice, price),
                lastChecked = now,
                lastError = null,
            )
        }
        if (price != null && notify) {
            updated = Notifier.process(context, old, updated)
        }
        dao.update(updated)
        return updated
    }
}
