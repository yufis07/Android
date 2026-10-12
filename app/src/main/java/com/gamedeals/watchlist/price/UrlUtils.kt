package com.gamedeals.watchlist.price

import java.net.URI

object UrlUtils {
    /**
     * Canonical form used as the watchlist key: scheme + host + path, without
     * query string, fragment or trailing slash.
     */
    fun normalize(url: String): String {
        val trimmed = url.trim()
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

    fun isGamePage(url: String?): Boolean = url != null && url.contains("/game/")

    /** Short "host/path" string for display in the toolbar. */
    fun pretty(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
    }
}
