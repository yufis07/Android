package com.gamedeals.watchlist.web

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.gamedeals.watchlist.data.Store
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lite mode makes the site tabs lighter: it skips third-party web fonts (pages fall back to
 * the phone's own fonts) and video / audio files. Game cover art and the sites' own icon
 * fonts are kept, so pages still look and work the same.
 */
object LiteMode {
    private val FONT_FILE = Regex("""\.(woff2?|ttf|otf|eot)($|[?#])""", RegexOption.IGNORE_CASE)
    private val MEDIA_FILE = Regex("""\.(mp4|m4v|webm|mov|m3u8|mpd|ts|mp3|m4a|aac|ogg|oga|wav|flac)($|[?#])""", RegexOption.IGNORE_CASE)
    private val FONT_SERVICES = setOf(
        "fonts.googleapis.com", "fonts.gstatic.com", "use.typekit.net", "p.typekit.net",
        "fonts.bunny.net", "use.fontawesome.com", "kit.fontawesome.com", "ka-f.fontawesome.com",
    )

    private val savedCount = AtomicInteger(0)

    /** Requests skipped by Lite mode since the app started. */
    val skipped: Int get() = savedCount.get()

    fun shouldSkip(url: String, host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        if (h in FONT_SERVICES) return true
        val path = url.substringBefore('?').substringBefore('#')
        if (MEDIA_FILE.containsMatchIn(path)) return true
        // Fonts hosted by the deal sites themselves include their icon font, so keep those.
        return FONT_FILE.containsMatchIn(path) && Store.fromUrl(url) == null
    }

    fun intercept(request: WebResourceRequest, enabled: Boolean): WebResourceResponse? {
        if (!enabled || request.isForMainFrame) return null
        val url = request.url.toString()
        if (!shouldSkip(url, request.url.host)) return null
        savedCount.incrementAndGet()
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}
