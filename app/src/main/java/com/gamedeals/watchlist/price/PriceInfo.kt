package com.gamedeals.watchlist.price

/** Price data extracted from a game page. */
data class PriceInfo(
    val title: String?,
    val price: Double?,
    val priceText: String?,
    val currency: String?,
    val imageUrl: String?,
) {
    fun display(): String =
        if (price == null) "not detected" else priceText ?: PriceFormat.format(price, currency)
}
