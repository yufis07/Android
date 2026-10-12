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

/** A game card found on a list page (for importing). */
data class GameCard(
    val url: String,
    val title: String,
    val info: PriceInfo,
    /** Already on your watchlist or wishlist on the site itself. */
    val onSiteList: Boolean,
)

/** Reported when you add a game with the site's own Watchlist / Wishlist button. */
data class SiteListAdd(
    val gameId: String,
    val list: String,
    val url: String?,
    val pageUrl: String?,
    val info: PriceInfo,
)
