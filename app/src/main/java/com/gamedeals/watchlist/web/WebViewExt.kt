package com.gamedeals.watchlist.web

import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Suspending wrapper around [WebView.evaluateJavascript]. Must be called on the main thread. */
suspend fun WebView.evaluate(script: String): String? = suspendCancellableCoroutine { cont ->
    evaluateJavascript(script) { result -> if (cont.isActive) cont.resume(result) }
}
