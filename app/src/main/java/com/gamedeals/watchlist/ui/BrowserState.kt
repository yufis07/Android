package com.gamedeals.watchlist.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gamedeals.watchlist.data.Prefs
import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.price.GameCard
import com.gamedeals.watchlist.price.PriceParser
import com.gamedeals.watchlist.price.PriceScripts
import com.gamedeals.watchlist.price.SiteListAdd
import com.gamedeals.watchlist.web.AdBlocker
import com.gamedeals.watchlist.web.LiteMode

/** Events from the site tabs that the activity reacts to. */
interface BrowserCallbacks {
    fun onPageLoaded(url: String, view: WebView)

    /** You used the site's own "Add to Watchlist / Wishlist" button. */
    fun onSiteListAdd(add: SiteListAdd, view: WebView)

    /** The live sync spotted games on your site lists on the current page. */
    fun onCardsFound(cards: List<GameCard>, view: WebView)
}

/** Only these sites may message the app (via `window.GameDealsApp.postMessage`). */
private val BRIDGE_ORIGINS = Store.entries.flatMap { listOf("https://${it.host}", "https://www.${it.host}") }.toSet()

/** Handles messages from [PriceScripts.siteBridge] running in a deal-site page. */
private fun handleBridgeMessage(json: String, view: WebView, callbacks: BrowserCallbacks) {
    val msg = runCatching { JSONObject(json) }.getOrNull() ?: return
    if (Store.fromUrl(view.url) == null) return
    when (msg.optString("type")) {
        "add" -> PriceParser.siteAddFromJson(msg.optJSONObject("data")?.toString() ?: return)
            ?.let { callbacks.onSiteListAdd(it, view) }
        "cards" -> PriceParser.cardsFromJsResult(msg.optJSONArray("data")?.toString() ?: return)
            .takeIf { it.isNotEmpty() }
            ?.let { callbacks.onCardsFound(it, view) }
    }
}

/**
 * One long-lived WebView per deal site, so each site keeps its own page, scroll
 * position and history while you switch tabs. Login cookies are shared and persisted
 * by Android's CookieManager, so you stay signed in between app launches.
 */
class BrowserState private constructor(val store: Store) {
    lateinit var webView: WebView
        private set

    var title by mutableStateOf(store.label)
    var url by mutableStateOf(store.homeUrl)
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    private fun syncNav(view: WebView) {
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
    }

    companion object {
        @SuppressLint("SetJavaScriptEnabled")
        fun create(
            activity: Activity,
            store: Store,
            callbacks: BrowserCallbacks,
        ): BrowserState {
            val state = BrowserState(store)
            val webView = WebView(activity)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false) // pop-ups (e.g. social login) open in the same view
            }
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    // Google doesn't allow its sign-in inside in-app browsers; explain instead of erroring.
                    if (request.url.host.equals("accounts.google.com", ignoreCase = true)) {
                        Toast.makeText(
                            activity,
                            "Google sign-in isn't available inside apps. Please sign in with your email and password on the site.",
                            Toast.LENGTH_LONG,
                        ).show()
                        return true
                    }
                    if (scheme == "http" || scheme == "https") return false
                    // mailto:, intent:, store links, etc. go to other apps.
                    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    return true
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    AdBlocker.intercept(request, Prefs.blockAds(activity))
                        ?: LiteMode.intercept(request, Prefs.liteMode(activity))

                override fun onPageCommitVisible(view: WebView, url: String?) {
                    view.evaluateJavascript(PriceScripts.siteBridge(Prefs.autoSync(activity), Prefs.allListUrls(activity)), null)
                    if (Prefs.blockAds(activity)) view.evaluateJavascript(AdBlocker.HIDE_ADS_SCRIPT, null)
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    state.loading = true
                    url?.let { state.url = it }
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    state.loading = false
                    state.syncNav(view)
                    CookieManager.getInstance().flush()
                    view.evaluateJavascript(PriceScripts.siteBridge(Prefs.autoSync(activity), Prefs.allListUrls(activity)), null)
                    if (Prefs.blockAds(activity)) view.evaluateJavascript(AdBlocker.HIDE_ADS_SCRIPT, null)
                    if (url != null) {
                        state.url = url
                        callbacks.onPageLoaded(url, view)
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    url?.let { state.url = it }
                    state.syncNav(view)
                }
            }
            webView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    state.progress = newProgress
                }

                override fun onReceivedTitle(view: WebView, title: String?) {
                    if (!title.isNullOrBlank()) state.title = title
                }
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(webView, "GameDealsApp", BRIDGE_ORIGINS) { view, message, _, isMainFrame, _ ->
                    val data = message.data
                    if (isMainFrame && data != null) handleBridgeMessage(data, view, callbacks)
                }
            }
            state.webView = webView
            webView.loadUrl(store.homeUrl)
            return state
        }
    }
}
