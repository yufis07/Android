package com.gamedeals.watchlist.price

import java.net.URI

object UrlUtils {
    /** https://psdeals.net/us-store/game/2997658/sonic-racing… -> host, region, game id */
    private val GAME_URL = Regex("""^https?://(?:www\.)?([^/?#]+)/([a-z]{2,3}-store)/game/(\d+)""", RegexOption.IGNORE_CASE)
    private val REGION = Regex("""^https?://(?:www\.)?([^/?#]+)/([a-z]{2,3}-store)\b""", RegexOption.IGNORE_CASE)

    /**
     * Canonical form used as the watchlist key. Game pages become
     * `https://<site>/<region>-store/game/<id>` (the sites' own canonical URL, which loads fine
     * without the name slug), so the same game can't be added twice. Other pages keep
     * scheme + host + path, without query string, fragment or trailing slash.
     */
    fun normalize(url: String): String {
        val trimmed = url.trim()
        GAME_URL.find(trimmed)?.let { m ->
            val (host, region, id) = m.destructured
            return "https://${host.lowercase()}/${region.lowercase()}/game/$id"
        }
        return runCatching {
            val u = URI(trimmed)
            val host = u.host ?: error("no host")
            val scheme = (u.scheme ?: "https").lowercase()
            val path = (u.rawPath ?: "").trimEnd('/')
            "$scheme://${host.lowercase()}$path"
        }.getOrElse {
            trimmed.substringBefore('#').substringBefore('?').trimEnd('/')
        }
    }

    /** Builds a game URL from any page on the same site/region plus a game id. */
    fun gameUrl(anyPageUrl: String?, gameId: String): String? {
        val m = REGION.find(anyPageUrl ?: return null) ?: return null
        val (host, region) = m.destructured
        return "https://${host.lowercase()}/${region.lowercase()}/game/$gameId"
    }

    fun isGamePage(url: String?): Boolean = url != null && GAME_URL.containsMatchIn(url)

    /** Short "host/path" string for display in the toolbar. */
    fun pretty(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
    }
}
