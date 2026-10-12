package com.gamedeals.watchlist.work

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.gamedeals.watchlist.data.WatchItem
import com.gamedeals.watchlist.web.AdBlocker
import com.gamedeals.watchlist.price.PriceInfo
import com.gamedeals.watchlist.price.PriceParser
import com.gamedeals.watchlist.price.PriceScripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Refreshes prices with an off-screen WebView. Slower than [HttpPriceFetcher] but behaves
 * exactly like a real browser (JavaScript, bot checks, login cookies), so it is used for
 * the manual "check now" button while the app is open.
 */
class WebViewPriceRefresher(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun refresh(
        items: List<WatchItem>,
        onProgress: (done: Int, total: Int) -> Unit,
        onResult: suspend (WatchItem, PriceInfo?) -> Unit,
    ) = withContext(Dispatchers.Main) {
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.blockNetworkImage = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
        }
        try {
            items.forEachIndexed { index, item ->
                onProgress(index, items.size)
                val info = withTimeoutOrNull(TIMEOUT_MS) { load(webView, item.url) }
                onResult(item, info)
            }
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private suspend fun load(webView: WebView, url: String): PriceInfo? =
        suspendCancellableCoroutine { cont ->
            webView.webViewClient = object : WebViewClient() {
                // Ads are never needed for reading a price; skipping them makes checks faster.
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    AdBlocker.intercept(request, enabled = true)

                override fun onPageFinished(view: WebView, finishedUrl: String?) {
                    // Give client-side rendering a moment, then read the price. If a bot check or
                    // redirect page loaded instead, wait for the next onPageFinished (or the timeout).
                    view.postDelayed({
                        if (!cont.isActive) return@postDelayed
                        view.evaluateJavascript(PriceScripts.EXTRACT) { result ->
                            val info = PriceParser.fromJsResult(result)
                            if (info?.price != null && cont.isActive) cont.resume(info)
                        }
                    }, 1_500)
                }
            }
            cont.invokeOnCancellation { webView.post { webView.stopLoading() } }
            webView.loadUrl(url)
        }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
