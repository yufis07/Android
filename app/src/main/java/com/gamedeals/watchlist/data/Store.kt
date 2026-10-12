package com.gamedeals.watchlist.data

import java.net.URI

/** The three deal sites supported by the app. */
enum class Store(
    val label: String,
    val short: String,
    val host: String,
    val homeUrl: String,
    val color: Long,
) {
    XBOX("Xbox", "XB", "xbdeals.net", "https://xbdeals.net/", 0xFF107C10),
    PLAYSTATION("PlayStation", "PS", "psdeals.net", "https://psdeals.net/", 0xFF0070D1),
    NINTENDO("Nintendo", "NT", "ntdeals.net", "https://ntdeals.net/", 0xFFE60012);

    companion object {
        fun fromUrl(url: String?): Store? {
            if (url.isNullOrBlank()) return null
            val host = runCatching { URI(url.trim()).host }.getOrNull()
                ?: Regex("""^[a-zA-Z]+://([^/?#:]+)""").find(url.trim())?.groupValues?.get(1)
                ?: return null
            val h = host.lowercase()
            return entries.firstOrNull { h == it.host || h.endsWith("." + it.host) }
        }
    }
}
