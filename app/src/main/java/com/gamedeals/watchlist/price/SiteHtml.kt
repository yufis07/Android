package com.gamedeals.watchlist.price

import com.gamedeals.watchlist.data.Store
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Reads xbdeals / psdeals / ntdeals pages from raw HTML (no JavaScript needed — the sites
 * render game cards on the server). Used by the full site-list sync to read every page of
 * your Watchlist / Wishlist quickly over plain HTTP.
 */
object SiteHtml {

    data class Page(
        val url: String,
        val cards: List<GameCard>,
        /** Next page of a paginated list (`<link rel="next">` / pagination "»"), if any. */
        val nextUrl: String?,
        /** true/false when the page says whether you're signed in, null if unknown. */
        val signedIn: Boolean?,
        /** Links to your own Watchlist / Wishlist pages found in the site menu. */
        val listLinks: List<String>,
        /** A bot-check / interstitial page instead of real content. */
        val isChallenge: Boolean,
    )

    private val GAME_ID = Regex("""/game/(\d+)""")
    private val USER_ID = Regex("""['"]user_id['"]\s*:\s*(null|"[^"]*"|'[^']*'|\d+)""")
    private val LIST_PATH = Regex("""(?i)(watch|wish)[-_ ]?list""")
    private val LIST_TEXT = Regex("""(?i)^\s*(my\s+)?(watch|wish)\s*-?\s*list\s*(\(\s*\d+\s*\))?\s*$""")
    private val EXCLUDED_PRICE_CLASS =
        Regex("old|original|strike|regular|base|full|history|crossed|lowest", RegexOption.IGNORE_CASE)

    fun parse(html: String, url: String): Page {
        val doc = Jsoup.parse(html, url)
        val cards = cards(doc)
        return Page(
            url = url,
            cards = cards,
            nextUrl = nextPageUrl(doc),
            signedIn = signedIn(html),
            listLinks = listLinks(doc),
            isChallenge = cards.isEmpty() && looksLikeChallenge(doc),
        )
    }

    fun signedIn(html: String): Boolean? {
        val value = USER_ID.find(html)?.groupValues?.get(1) ?: return null
        return value != "null" && value.trim('"', '\'').isNotBlank()
    }

    fun looksLikeChallenge(html: String): Boolean = looksLikeChallenge(Jsoup.parse(html))

    private fun looksLikeChallenge(doc: Document): Boolean {
        val title = doc.title().lowercase()
        return "just a moment" in title || "attention required" in title || "verify you are human" in title ||
            doc.selectFirst("#challenge-form, #cf-challenge-running, .cf-browser-verification, #challenge-stage") != null
    }

    fun nextPageUrl(doc: Document): String? {
        doc.selectFirst("link[rel=next][href]")?.absUrl("href")?.takeIf { it.isNotBlank() }?.let { return it }
        val next = doc.selectFirst(".pagination li.next:not(.disabled) a[href], .pagination a[rel=next][href]")
        return next?.absUrl("href")?.takeIf { it.isNotBlank() && it.substringBefore('#') != doc.location().substringBefore('#') }
    }

    /** Finds the signed-in menu links to your own Watchlist / Wishlist on the same site. */
    fun listLinks(doc: Document): List<String> {
        val pageStore = Store.fromUrl(doc.location())
        val pageUrl = doc.location().substringBefore('#')
        return doc.select("a[href]").mapNotNull { a ->
            // Skip the cards' own "Add to Watchlist / Wishlist" action buttons (href="<page>#").
            if (a.hasClass("add-to") || a.hasAttr("data-product-id")) return@mapNotNull null
            val href = a.absUrl("href")
            if (href.isBlank() || href.substringBefore('#') == pageUrl) return@mapNotNull null
            if (pageStore != null && Store.fromUrl(href) != pageStore) return@mapNotNull null
            val path = href.substringAfter("://").substringAfter('/', "").substringBefore('#').substringBefore('?')
            if (path.isBlank() || "/user/add-to" in "/$path" || "/user/remove" in "/$path") return@mapNotNull null
            if ("purchase" in path.lowercase()) return@mapNotNull null
            val text = a.text()
            if (LIST_PATH.containsMatchIn(path) || LIST_TEXT.matches(text)) href.substringBefore('#') else null
        }.distinctBy { UrlUtils.normalize(it) }
    }

    /** Every game card on the page (search results, collections, your list pages, home rows). */
    fun cards(doc: Document): List<GameCard> {
        val roots: List<Element> = doc.select(".game-collection-item, .game-table-item").ifEmpty {
            doc.select("a[href*=/game/]").map { a -> a.closest("li") ?: a }
        }
        val pageGameId = GAME_ID.find(doc.location())?.groupValues?.get(1)
        val seen = HashSet<String>()
        val out = ArrayList<GameCard>()
        for (root in roots) {
            val link = if (root.`is`("a[href*=/game/]")) root else root.selectFirst("a[href*=/game/]") ?: continue
            val url = link.absUrl("href").substringBefore('#').substringBefore('?')
            if (url.isBlank()) continue
            val id = root.selectFirst("[itemprop=sku]")?.text()?.trim()?.ifBlank { null }
                ?: GAME_ID.find(url)?.groupValues?.get(1) ?: continue
            if (id == pageGameId || !seen.add(id)) continue

            val title = (
                root.selectFirst("[itemprop=name]")
                    ?: root.selectFirst("[class*=item-details-title], [class*=item-title], [class*=title]")
                )?.text()?.trim()?.ifBlank { null }
                ?: link.attr("title").ifBlank { null }
                ?: link.text().trim().ifBlank { null }
                ?: continue

            val priceEl = root.selectFirst("[itemprop=price]")
            val priceValue = priceEl?.attr("content")?.ifBlank { null } ?: priceEl?.text()?.trim()?.ifBlank { null }
            val currency = root.selectFirst("[itemprop=priceCurrency]")?.let { it.attr("content").ifBlank { it.text() } }
                ?.trim()?.ifBlank { null }
            val priceText = visiblePrice(root)
            val img = root.selectFirst("img[class*=item-image], img[itemprop=image], img:not([src*=flag]):not([class*=flag])")
            out += GameCard(
                url = url,
                title = title,
                info = PriceInfo(
                    title = title,
                    price = PriceParser.parseNumber(priceValue ?: priceText),
                    priceText = priceText,
                    currency = currency ?: PriceFormat.symbolFrom(priceText),
                    imageUrl = img?.let { it.absUrl("data-src").ifBlank { it.absUrl("src") } }?.ifBlank { null },
                ),
                onSiteList = root.selectFirst(".icon-watchlist.active, .icon-wishlist.active") != null,
            )
        }
        return out
    }

    private fun visiblePrice(root: Element): String? {
        root.selectFirst("[class*=price-discount], [class*=price-current], [class*=price-final]")
            ?.text()?.trim()?.ifBlank { null }?.let { return it }
        for (el in root.select("[class*=price]")) {
            if (el.hasAttr("itemprop")) continue // hidden structured-data copies
            if (EXCLUDED_PRICE_CLASS.containsMatchIn(el.className())) continue
            if (el.select("[class*=price]").size > 1) continue
            val t = el.text().trim()
            if (t.isNotEmpty() && t.length < 25 && (t.any { it.isDigit() } || t.contains("free", true))) return t
        }
        return null
    }
}
