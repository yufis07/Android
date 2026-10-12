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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gamedeals.watchlist.data.Prefs
import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.web.AdBlocker

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
            onPageLoaded: (url: String, view: WebView) -> Unit,
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
                // Some sign-in providers refuse embedded browsers; drop the WebView marker.
                userAgentString = userAgentString.replace("; wv", "")
            }
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") return false
                    // mailto:, intent:, store links, etc. go to other apps.
                    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    return true
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    AdBlocker.intercept(request, Prefs.blockAds(activity))

                override fun onPageCommitVisible(view: WebView, url: String?) {
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
                    if (Prefs.blockAds(activity)) view.evaluateJavascript(AdBlocker.HIDE_ADS_SCRIPT, null)
                    if (url != null) {
                        state.url = url
                        onPageLoaded(url, view)
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
            state.webView = webView
            webView.loadUrl(store.homeUrl)
            return state
        }
    }
}
