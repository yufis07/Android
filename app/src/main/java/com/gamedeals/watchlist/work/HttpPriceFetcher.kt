package com.gamedeals.watchlist.work

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.gamedeals.watchlist.price.PriceInfo
import com.gamedeals.watchlist.price.PriceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Downloads a game page with plain HTTP (no WebView), reusing the WebView's login
 * cookies so regional / account-specific prices match what you see in the app.
 */
class HttpPriceFetcher(context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val userAgent: String = runCatching {
        WebSettings.getDefaultUserAgent(context).replace("; wv", "")
    }.getOrDefault(
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
    )

    /** Returns the parsed price, or null plus a human-readable error. */
    suspend fun fetch(url: String): Pair<PriceInfo?, String?> = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", Locale.getDefault().toLanguageTag())
            runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { builder.header("Cookie", it) }

            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null to "HTTP ${response.code} – tap ↻ in the app to refresh"
                }
                val html = response.body?.string().orEmpty()
                val info = PriceParser.fromHtml(html, url)
                if (info.price == null) info to "Price not found on page" else info to null
            }
        } catch (e: Exception) {
            null to (e.message ?: e.javaClass.simpleName)
        }
    }
}
