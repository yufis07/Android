package com.gamedeals.watchlist.data

import org.json.JSONArray
import org.json.JSONObject

/** Watchlist backup as JSON, for moving to a new phone or reinstalling the app. */
object Backup {
    private const val VERSION = 1

    fun toJson(items: List<WatchItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("url", item.url)
                    .put("store", item.store.name)
                    .put("title", item.title)
                    .putOpt("imageUrl", item.imageUrl)
                    .putOpt("currentPrice", item.currentPrice)
                    .putOpt("priceText", item.priceText)
                    .putOpt("currency", item.currency)
                    .putOpt("targetPrice", item.targetPrice)
                    .putOpt("lowestPrice", item.lowestPrice)
                    .put("addedAt", item.addedAt)
                    .putOpt("lastChecked", item.lastChecked)
            )
        }
        return JSONObject()
            .put("app", "GameDealsWatch")
            .put("version", VERSION)
            .put("items", arr)
            .toString(2)
    }

    /** Parses a backup; unknown or broken entries are skipped. */
    fun fromJson(text: String): List<WatchItem> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optStringOrNull("url") ?: return@mapNotNull null
            val store = runCatching { Store.valueOf(o.optString("store")) }.getOrNull()
                ?: Store.fromUrl(url) ?: return@mapNotNull null
            WatchItem(
                url = url,
                store = store,
                title = o.optStringOrNull("title") ?: url,
                imageUrl = o.optStringOrNull("imageUrl"),
                currentPrice = o.optDoubleOrNull("currentPrice"),
                priceText = o.optStringOrNull("priceText"),
                currency = o.optStringOrNull("currency"),
                targetPrice = o.optDoubleOrNull("targetPrice"),
                lowestPrice = o.optDoubleOrNull("lowestPrice"),
                addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                lastChecked = if (o.has("lastChecked") && !o.isNull("lastChecked")) o.optLong("lastChecked") else null,
            )
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null
}
