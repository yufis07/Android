package com.gamedeals.watchlist.web

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Blocks ads in the in-app browser: Google (AdMob / AdSense / Ad Manager / DoubleClick)
 * plus the other major ad networks and ad trackers. A built-in list is always active and is
 * extended with a community ad-server list (pgl.yoyo.org, ~3,500 domains) that is
 * downloaded and refreshed weekly. Requests to ad hosts get an empty response, and leftover
 * ad slots are hidden with CSS ([HIDE_ADS_SCRIPT]).
 */
object AdBlocker {
    /** Domains (and all their subdomains) that serve ads or ad tracking. */
    private val BUILT_IN = setOf(
        // Google
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
        "googletagmanager.com", "googleads.com", "admob.com", "adsense.com", "app-measurement.com",
        "google-analytics.com", "imasdk.googleapis.com", "fundingchoicesmessages.google.com",
        "ads.google.com", "pagead2.googlesyndication.com", "adtrafficquality.google",
        // Amazon / Microsoft / Meta / Apple / TikTok / X / Snap / Pinterest / Reddit ads
        "amazon-adsystem.com", "aax.amazon-adsystem.com", "assoc-amazon.com",
        "bat.bing.com", "ads.microsoft.com", "adnxs.com", "adnxs-simple.com",
        "an.facebook.com", "ads-api.tiktok.com", "analytics.tiktok.com",
        "ads-twitter.com", "static.ads-twitter.com", "ads.linkedin.com", "px.ads.linkedin.com",
        "sc-static.net", "tr.snapchat.com", "ct.pinterest.com", "ads.pinterest.com",
        "alb.reddit.com", "events.redditmedia.com", "iadsdk.apple.com",
        // Programmatic exchanges, SSPs and header bidding
        "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com", "indexww.com",
        "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "outbrainimg.com",
        "smartadserver.com", "sharethrough.com", "triplelift.com", "3lift.com", "33across.com",
        "teads.tv", "yieldmo.com", "media.net", "adform.net", "adform.com", "sovrn.com",
        "lijit.com", "gumgum.com", "districtm.io", "contextweb.com", "spotxchange.com",
        "spotx.tv", "springserve.com", "smaato.net", "inmobi.com", "applovin.com",
        "applvn.com", "unityads.unity3d.com", "vungle.com", "chartboost.com", "adcolony.com",
        "ironsrc.mobi", "supersonicads.com", "mopub.com", "startapp.com", "mintegral.com",
        "liftoff.io", "yieldlove.com", "adsafeprotected.com", "moatads.com",
        "doubleverify.com", "serving-sys.com", "flashtalking.com", "adsrvr.org",
        "adroll.com", "quantserve.com", "quantcount.com", "scorecardresearch.com",
        "bidswitch.net", "mathtag.com", "demdex.net", "everesttech.net", "rlcdn.com",
        "agkn.com", "tapad.com", "bluekai.com", "krxd.net", "exelator.com", "eyeota.net",
        "crwdcntrl.net", "lotame.com", "id5-sync.com", "33across.net", "onetag-sys.com",
        "richaudience.com", "improvedigital.com", "360yield.com", "adtelligent.com",
        "adkernel.com", "admixer.net", "e-planning.net", "seedtag.com", "nativo.com",
        "zemanta.com", "revcontent.com", "mgid.com", "propellerads.com", "popads.net",
        "popcash.net", "adsterra.com", "exoclick.com", "juicyads.com", "trafficjunky.net",
        "hilltopads.net", "adcash.com", "clickadu.com", "admaven.com", "ad-maven.com",
        "infolinks.com", "vibrantmedia.com", "kargo.com", "undertone.com", "connatix.com",
        "primis.tech", "aniview.com", "vidazoo.com", "ex.co", "playwire.com",
        "adthrive.com", "mediavine.com", "ezoic.net", "ezodn.com", "freestar.io",
        "pubfuture.com", "venatus.com", "nitropay.com", "snigelweb.com", "setupad.net",
        "confiant-integrations.net", "prebid.org", "hb.adscale.de", "adscale.de",
        "an.yandex.ru", "mc.yandex.ru", "ads.yahoo.com", "advertising.com",
        "gemini.yahoo.com", "adtech.de", "zedo.com", "adblade.com", "buysellads.com",
        "carbonads.com", "carbonads.net",
        // Analytics / session-recording trackers (slow pages down, add nothing for you)
        "static.cloudflareinsights.com", "cloudflareinsights.com", "hotjar.com", "hotjar.io",
        "clarity.ms", "mixpanel.com", "amplitude.com", "segment.io", "segment.com", "cdn.segment.com",
        "nr-data.net", "js-agent.newrelic.com", "fullstory.com", "mouseflow.com", "crazyegg.com",
        "luckyorange.com", "heap.io", "heapanalytics.com", "chartbeat.com", "chartbeat.net",
        "parsely.com", "branch.io", "adjust.com", "appsflyer.com", "kochava.com",
        "onesignal.com", "pushwoosh.com",
    )

    /** adservice.google.com, adservice.google.co.uk, … */
    private const val ADSERVICE_PREFIX = "adservice.google."

    private const val LIST_URL =
        "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml&showintro=0&mimetype=plaintext"
    private const val CACHE_FILE = "ad_hosts.txt"
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /** Never block these, even if a downloaded list contains them (sites and logins must work). */
    private val ALLOW = setOf(
        "psdeals.net", "xbdeals.net", "ntdeals.net",
        "accounts.google.com", "google.com", "www.google.com", "gstatic.com", "googleapis.com",
        "facebook.com", "www.facebook.com", "apple.com", "appleid.apple.com",
        "playstation.com", "store.playstation.com", "xbox.com", "microsoft.com", "nintendo.com",
        "challenges.cloudflare.com",
    )

    @Volatile
    private var downloaded: Set<String> = emptySet()
    private val blockedCount = AtomicInteger(0)

    /** Number of ad requests blocked since the app started. */
    val blocked: Int get() = blockedCount.get()

    /** Size of the active block list. */
    val listSize: Int get() = BUILT_IN.size + downloaded.size

    fun isAdHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase().trimEnd('.')
        if (h in ALLOW) return false
        if (h.startsWith(ADSERVICE_PREFIX)) return true
        // Check the host and each parent domain: a.b.ads.com -> b.ads.com -> ads.com
        var candidate = h
        while (true) {
            if (candidate in BUILT_IN || candidate in downloaded) return true
            val dot = candidate.indexOf('.')
            if (dot < 0 || candidate.indexOf('.', dot + 1) < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    /** Call from `WebViewClient.shouldInterceptRequest`; returns an empty response for ad requests. */
    fun intercept(request: WebResourceRequest, enabled: Boolean): WebResourceResponse? {
        if (!enabled || !isAdHost(request.url.host)) return null
        blockedCount.incrementAndGet()
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    /** Parses a plain domain-per-line or hosts-file formatted list. */
    fun parseList(text: String): Set<String> =
        text.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map { line -> line.split(Regex("\\s+")).last().lowercase().trimEnd('.') }
            .filter { it.contains('.') && it !in ALLOW && it != "localhost" && !it.startsWith("0.0.0.0") }
            .toSet()

    /** Loads the cached list, then refreshes it from the network if it's older than a week. */
    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, CACHE_FILE)
        if (file.exists()) {
            runCatching { downloaded = parseList(file.readText()) }
        }
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) return@withContext
        runCatching {
            val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(LIST_URL).build()).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string().orEmpty()
                val parsed = parseList(body)
                if (parsed.size > 100) { // sanity check before replacing the cache
                    file.writeText(body)
                    downloaded = parsed
                }
            }
        }
    }

    /** Hides ad slots and common ad containers left in the page layout. */
    val HIDE_ADS_SCRIPT = """
        (function () {
          if (document.getElementById('gdw-adblock-style')) return;
          var s = document.createElement('style');
          s.id = 'gdw-adblock-style';
          s.textContent =
            'ins.adsbygoogle, .adsbygoogle, [id^="google_ads_iframe"], [id^="div-gpt-ad"],' +
            '[data-google-query-id], [data-ad-slot], [data-ad-client], [data-ad-unit], [data-adunit],' +
            'iframe[src*="doubleclick.net"], iframe[src*="googlesyndication.com"], iframe[id^="google_ads"],' +
            'iframe[src*="amazon-adsystem.com"], iframe[src*="taboola.com"], iframe[src*="outbrain.com"],' +
            'amp-ad, amp-auto-ads, amp-embed, .ad-container, .ad-wrapper, .ad-slot, .ad-banner, .ad-unit,' +
            '.adsbox, .adbox, .advert, .advertisement, .sponsored-ad, .banner-ad, .top-ad, .bottom-ad,' +
            '[id^="ad-slot"], [id^="ad_slot"], [id^="adslot"], [class^="ad-"][class*="-banner"],' +
            '.trc_rbox_container, .OUTBRAIN, [id^="taboola-"], [id^="rc_widget"], .mgbox' +
            '{ display: none !important; height: 0 !important; min-height: 0 !important; }';
          (document.head || document.documentElement).appendChild(s);
        })();
    """.trimIndent()
}
