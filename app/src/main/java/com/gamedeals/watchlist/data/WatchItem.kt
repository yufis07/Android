package com.gamedeals.watchlist.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "watch_items", indices = [Index(value = ["url"], unique = true)])
data class WatchItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val store: Store,
    val title: String,
    val imageUrl: String? = null,
    /** Last known price as a number (0.0 = free). */
    val currentPrice: Double? = null,
    /** Last known price exactly as displayed on the site, e.g. "$19.99". */
    val priceText: String? = null,
    /** ISO code (USD) or symbol ($) if known. */
    val currency: String? = null,
    val targetPrice: Double? = null,
    val lowestPrice: Double? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastChecked: Long? = null,
    val lastError: String? = null,
    /** Price at which the last "target reached" alert was sent, to avoid repeat alerts. */
    val notifiedPrice: Double? = null,
)

val WatchItem.isAtTarget: Boolean
    get() {
        val t = targetPrice
        val c = currentPrice
        return t != null && c != null && c <= t
    }
