package com.gamedeals.watchlist.price

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Extracts the title and current price of a game from an xbdeals / psdeals / ntdeals page.
 *
 * Strategy (same as [PriceScripts.EXTRACT], which runs inside the WebView):
 *  1. schema.org JSON-LD `offers.price`
 *  2. microdata `itemprop="price"` / OpenGraph `product:price:amount`
 *  3. the first leaf element whose class contains "price" (skipping old/struck-through prices)
 */
object PriceParser {
    private val EXCLUDED_CLASS =
        Regex("old|original|strike|regular|base|full|history|crossed|lowest", RegexOption.IGNORE_CASE)
    private val STRIKE_TAGS = setOf("s", "del", "strike")

    /** Parses "$1,299.99", "1.299,99 €", "¥1,299", "Free" etc. into a number. */
    fun parseNumber(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
        if (text.none { it.isDigit() }) {
            return if (text.contains("free", ignoreCase = true)) 0.0 else null
        }
        val match = Regex("""\d[\d.,\s ']*""").find(text) ?: return null
        var s = match.value.replace(Regex("""[\s ']"""), "").trimEnd('.', ',')
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        s = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastDot > lastComma) s.replace(",", "")
                else s.replace(".", "").replace(',', '.')
            lastComma >= 0 -> {
                val decimals = s.length - lastComma - 1
                if (s.count { it == ',' } == 1 && decimals in 1..2) s.replace(',', '.') else s.replace(",", "")
            }
            lastDot >= 0 -> {
                val decimals = s.length - lastDot - 1
                if (s.count { it == '.' } > 1 || decimals == 3) s.replace(".", "") else s
            }
            else -> s
        }
        return s.toDoubleOrNull()
    }

    /** Strips site boilerplate such as " — price history, screenshots • USA". */
    fun cleanTitle(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var t = raw.trim().split(Regex("""\s+[—–|•]\s+""")).first()
        t = t.replace(Regex("""(?i)^\s*-?\d+%\s+(discount|off)\s+on\s+"""), "")
        t = t.replace(Regex("""(?i)\s+(price history|prices? (on|for|in)\b).*$"""), "")
        return t.trim().ifBlank { raw.trim() }
    }

    /** Parses the JSON string returned by `WebView.evaluateJavascript(PriceScripts.EXTRACT)`. */
    fun fromJsResult(result: String?): PriceInfo? {
        if (result.isNullOrBlank() || result == "null") return null
        return runCatching {
            val decoded = JSONTokener(result).nextValue()
            val o = when (decoded) {
                is String -> JSONObject(decoded)
                is JSONObject -> decoded
                else -> return null
            }
            val priceText = o.str("priceText")
            PriceInfo(
                title = cleanTitle(o.str("title")),
                price = parseNumber(o.str("price")),
                priceText = priceText,
                currency = o.str("currency") ?: PriceFormat.symbolFrom(priceText),
                imageUrl = o.str("image"),
            )
        }.getOrNull()
    }

    /** Parses the JSON array returned by [PriceScripts.SCAN_PAGE]. */
    fun cardsFromJsResult(result: String?): List<GameCard> {
        if (result.isNullOrBlank() || result == "null") return emptyList()
        return runCatching {
            val decoded = JSONTokener(result).nextValue()
            val arr = when (decoded) {
                is String -> JSONArray(decoded)
                is JSONArray -> decoded
                else -> return emptyList()
            }
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.str("url") ?: return@mapNotNull null
                val priceText = o.str("priceText")
                GameCard(
                    url = url,
                    title = o.str("title") ?: return@mapNotNull null,
                    info = PriceInfo(
                        title = o.str("title"),
                        price = parseNumber(priceText),
                        priceText = priceText,
                        currency = PriceFormat.symbolFrom(priceText),
                        imageUrl = o.str("image"),
                    ),
                    onSiteList = o.optBoolean("watchlisted") || o.optBoolean("wishlisted"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Parses the JSON sent by [PriceScripts.SITE_BRIDGE] when a site watchlist/wishlist button is used. */
    fun siteAddFromJson(json: String): SiteListAdd? = runCatching {
        val o = JSONObject(json)
        val priceText = o.str("priceText")
        SiteListAdd(
            gameId = o.str("gameId") ?: return null,
            list = o.str("list") ?: "watchlist",
            url = o.str("url"),
            pageUrl = o.str("pageUrl"),
            info = PriceInfo(
                title = cleanTitle(o.str("title")),
                price = parseNumber(priceText),
                priceText = priceText,
                currency = PriceFormat.symbolFrom(priceText),
                imageUrl = o.str("image"),
            ),
        )
    }.getOrNull()

    /** Parses raw page HTML (used by the background worker). */
    fun fromHtml(html: String, baseUrl: String): PriceInfo {
        val doc = Jsoup.parse(html, baseUrl)
        val acc = Acc()

        for (script in doc.select("script[type=\"application/ld+json\"]")) {
            val data = runCatching { JSONTokener(script.data()).nextValue() }.getOrNull() ?: continue
            walk(data, acc)
        }
        if (acc.price == null) {
            acc.price = attrOrText(doc.selectFirst("[itemprop=price]"))
                ?: attrOrText(doc.selectFirst("meta[property=\"product:price:amount\"]"))
        }
        if (acc.currency == null) {
            acc.currency = attrOrText(doc.selectFirst("[itemprop=priceCurrency]"))
                ?: attrOrText(doc.selectFirst("meta[property=\"product:price:currency\"]"))
        }
        if (acc.price == null) {
            for (el in doc.select("[class*=price]")) {
                if (EXCLUDED_CLASS.containsMatchIn(el.className())) continue
                if (el.select("[class*=price]").size > 1) continue // not a leaf
                if (el.parents().any { it.tagName().lowercase() in STRIKE_TAGS }) continue
                val t = el.text().trim()
                if (t.isNotEmpty() && t.length < 25 && (t.any { it.isDigit() } || t.contains("free", true))) {
                    acc.priceText = t
                    acc.price = t
                    break
                }
            }
        }
        val name = doc.selectFirst(".game-title-info-name, [itemprop=name][class*=title], h1[itemprop=name]")
            ?.text()?.trim()?.takeIf { it.isNotEmpty() }
        acc.title = name ?: acc.title
            ?: attrOrText(doc.selectFirst("meta[property=\"og:title\"]")) ?: doc.title()
        if (acc.image == null) {
            acc.image = attrOrText(doc.selectFirst("meta[property=\"og:image\"]"))
        }
        return PriceInfo(
            title = cleanTitle(acc.title),
            price = parseNumber(acc.price),
            priceText = acc.priceText,
            currency = acc.currency ?: PriceFormat.symbolFrom(acc.priceText),
            imageUrl = acc.image,
        )
    }

    private class Acc {
        var title: String? = null
        var price: String? = null
        var priceText: String? = null
        var currency: String? = null
        var image: String? = null
    }

    private fun walk(node: Any?, acc: Acc) {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), acc)
            is JSONObject -> {
                walk(node.opt("@graph"), acc)
                val offers = node.opt("offers")
                if (offers != null && offers != JSONObject.NULL) {
                    val list = if (offers is JSONArray) (0 until offers.length()).map { offers.opt(it) } else listOf(offers)
                    for (offer in list) {
                        if (offer !is JSONObject) continue
                        val p = offer.str("price") ?: offer.str("lowPrice")
                        if (p != null && acc.price == null) {
                            acc.price = p
                            acc.currency = offer.str("priceCurrency") ?: acc.currency
                        }
                    }
                    if (acc.title == null) acc.title = node.str("name")
                    if (acc.image == null) acc.image = imageOf(node.opt("image"))
                }
            }
        }
    }

    private fun imageOf(v: Any?): String? = when (v) {
        is String -> v.takeIf { it.isNotBlank() }
        is JSONArray -> imageOf(v.opt(0))
        is JSONObject -> v.str("url")
        else -> null
    }

    private fun attrOrText(el: Element?): String? {
        if (el == null) return null
        val v = el.attr("content").ifBlank { el.text() }.trim()
        return v.ifBlank { null }
    }

    private fun JSONObject.str(key: String): String? =
        if (!has(key) || isNull(key)) null else opt(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
