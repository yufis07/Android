package com.gamedeals.watchlist.sync

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.gamedeals.watchlist.price.SiteHtml
import com.gamedeals.watchlist.web.AdBlocker
import com.gamedeals.watchlist.web.LiteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Fallback page loader using a hidden WebView, for when a site answers plain HTTP with a
 * bot check. It behaves like the app's normal browser (same cookies, JavaScript), waits until
 * the real page has loaded and returns its HTML. One instance is reused for a whole crawl.
 */
class WebViewHtmlLoader(private val context: Context) {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun view(): WebView = webView ?: WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.blockNetworkImage = true
        settings.userAgentString = settings.userAgentString.replace("; wv", "")
        webView = this
    }

    suspend fun load(url: String, timeoutMs: Long = 25_000): String? = withContext(Dispatchers.Main) {
        val wv = view()
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        AdBlocker.intercept(request, enabled = true) ?: LiteMode.intercept(request, enabled = true)

                    override fun onPageFinished(view: WebView, finishedUrl: String?) {
                        view.postDelayed({
                            if (!cont.isActive) return@postDelayed
                            view.evaluateJavascript("document.documentElement.outerHTML") { result ->
                                val html = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
                                // A bot-check page loads first, then redirects: wait for the real page.
                                if (html != null && !SiteHtml.looksLikeChallenge(html) && cont.isActive) cont.resume(html)
                            }
                        }, 700)
                    }
                }
                cont.invokeOnCancellation { wv.post { wv.stopLoading() } }
                wv.loadUrl(url)
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.Main) {
        webView?.let {
            it.stopLoading()
            it.destroy()
        }
        webView = null
    }
}
