package com.gamedeals.watchlist.web

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Blocks Google's ad network (AdMob / AdSense / Ad Manager / DoubleClick) inside the
 * in-app browser. Requests to ad hosts get an empty response, and leftover ad slots
 * are hidden with CSS ([HIDE_ADS_SCRIPT]).
 */
object AdBlocker {
    /** Domains (and all their subdomains) that only serve ads or ad tracking. */
    private val BLOCKED_DOMAINS = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "googleads.com",
        "admob.com",
        "adsense.com",
        "app-measurement.com",
        "google-analytics.com",
        "imasdk.googleapis.com",
        "fundingchoicesmessages.google.com",
        "ads.google.com",
    )

    /** adservice.google.com, adservice.google.co.uk, … */
    private const val ADSERVICE_PREFIX = "adservice.google."

    private val blockedCount = AtomicInteger(0)

    /** Number of ad requests blocked since the app started. */
    val blocked: Int get() = blockedCount.get()

    fun isAdHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase().trimEnd('.')
        if (h.startsWith(ADSERVICE_PREFIX)) return true
        return BLOCKED_DOMAINS.any { h == it || h.endsWith(".$it") }
    }

    /** Call from `WebViewClient.shouldInterceptRequest`; returns an empty response for ad requests. */
    fun intercept(request: WebResourceRequest, enabled: Boolean): WebResourceResponse? {
        if (!enabled || !isAdHost(request.url.host)) return null
        blockedCount.incrementAndGet()
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    /** Hides ad slots that remain in the page layout after their content was blocked. */
    val HIDE_ADS_SCRIPT = """
        (function () {
          if (document.getElementById('gdw-adblock-style')) return;
          var s = document.createElement('style');
          s.id = 'gdw-adblock-style';
          s.textContent =
            'ins.adsbygoogle, .adsbygoogle, [id^="google_ads_iframe"], [id^="div-gpt-ad"],' +
            '[data-google-query-id], [data-ad-slot], [data-ad-client],' +
            'iframe[src*="doubleclick.net"], iframe[src*="googlesyndication.com"],' +
            'iframe[id^="google_ads"], amp-ad, amp-auto-ads' +
            '{ display: none !important; height: 0 !important; min-height: 0 !important; }';
          (document.head || document.documentElement).appendChild(s);
        })();
    """.trimIndent()
}
