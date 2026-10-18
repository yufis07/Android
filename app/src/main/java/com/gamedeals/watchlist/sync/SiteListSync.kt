package com.gamedeals.watchlist.sync

import android.content.Context
import com.gamedeals.watchlist.data.Prefs
import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.price.GameCard
import com.gamedeals.watchlist.price.SiteHtml
import com.gamedeals.watchlist.price.UrlUtils
import com.gamedeals.watchlist.work.HttpPriceFetcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Full sync of your Watchlist / Wishlist on xbdeals, psdeals and ntdeals.
 *
 * For each site it finds your list pages (from the signed-in site menu, remembered after the
 * first time) and reads **every page** of each list (the sites show 36 games per page). Pages
 * are fetched over plain HTTP with your login cookies, which is fast; if a site answers with a
 * bot check, it falls back to a hidden browser (only when [allowWebView] is set, i.e. in the app).
 * The three sites are synced in parallel.
 */
class SiteListSync(private val context: Context) {

    data class StoreResult(
        val store: Store,
        val signedIn: Boolean?,
        val listsFound: Int,
        val pagesRead: Int,
        val cards: List<GameCard>,
        val error: String?,
    )

    data class CrawlResult(val cards: List<GameCard>, val pages: Int, val signedOut: Boolean)

    private val http = HttpPriceFetcher(context)

    suspend fun syncAll(allowWebView: Boolean, onProgress: (String) -> Unit = {}): List<StoreResult> =
        coroutineScope {
            Store.entries.map { store -> async { syncStore(store, allowWebView, onProgress) } }.awaitAll()
        }

    suspend fun syncStore(store: Store, allowWebView: Boolean, onProgress: (String) -> Unit): StoreResult {
        val loader = if (allowWebView) WebViewHtmlLoader(context) else null
        try {
            var signedIn: Boolean? = null
            var lists = Prefs.listUrls(context, store)
            if (lists.isEmpty()) {
                onProgress("${store.label}: finding your lists…")
                val home = load(store.homeUrl, loader)
                    ?: return StoreResult(store, null, 0, 0, emptyList(), "Couldn't reach ${store.host}")
                signedIn = home.signedIn
                if (home.signedIn == false) {
                    return StoreResult(store, false, 0, 0, emptyList(), "Not signed in")
                }
                lists = home.listLinks
                if (lists.isEmpty()) {
                    return StoreResult(
                        store, signedIn, 0, 0, emptyList(),
                        "Couldn't find your list pages – open your Watchlist on ${store.host} and use ⋮ → Import",
                    )
                }
                Prefs.setListUrls(context, store, lists)
            }

            val all = LinkedHashMap<String, GameCard>()
            var pages = 0
            for (listUrl in lists) {
                val result = crawl(listUrl, loader, yourList = true) { page ->
                    onProgress("${store.label}: reading page ${pages + page}…")
                }
                if (result.signedOut) {
                    Prefs.setListUrls(context, store, emptyList()) // rediscover next time
                    return StoreResult(store, false, lists.size, pages, all.values.toList(), "Not signed in")
                }
                pages += result.pages
                result.cards.forEach { all.putIfAbsent(UrlUtils.normalize(it.url), it) }
            }
            return StoreResult(store, signedIn ?: true, lists.size, pages, all.values.toList(), null)
        } finally {
            loader?.close()
        }
    }

    /**
     * Reads [startUrl] and every following page. On your own list pages ([yourList]) every game
     * counts as listed, unless the page marks list membership, in which case only marked ones do.
     */
    suspend fun crawl(
        startUrl: String,
        loader: WebViewHtmlLoader?,
        yourList: Boolean,
        maxPages: Int = 60,
        onPage: (Int) -> Unit = {},
    ): CrawlResult {
        val cards = LinkedHashMap<String, GameCard>()
        val visited = HashSet<String>()
        var url: String? = startUrl
        var pages = 0
        while (url != null && pages < maxPages && visited.add(UrlUtils.normalize(url))) {
            val page = load(url, loader) ?: break
            if (yourList && page.signedIn == false) return CrawlResult(cards.values.toList(), pages, signedOut = true)
            pages++
            onPage(pages)
            // On your list pages, trust the page's "on your list" markers when it shows them.
            val take = if (yourList) page.cards.filter { it.onSiteList }.ifEmpty { page.cards } else page.cards
            val before = cards.size
            take.forEach { card -> cards.putIfAbsent(UrlUtils.normalize(card.url), card.copy(onSiteList = yourList || card.onSiteList)) }
            if (cards.size == before && page.cards.isNotEmpty()) break // nothing new: end of list / loop
            url = page.nextUrl
        }
        return CrawlResult(cards.values.toList(), pages, signedOut = false)
    }

    /** Fetches and parses one page: HTTP first, hidden browser if the site shows a bot check. */
    suspend fun load(url: String, loader: WebViewHtmlLoader?): SiteHtml.Page? {
        http.fetchHtml(url)?.let { res ->
            if (res.code in 200..299) {
                val page = SiteHtml.parse(res.html, res.finalUrl)
                if (!page.isChallenge) return page
            }
        }
        val html = loader?.load(url) ?: return null
        return SiteHtml.parse(html, url)
    }
}
