package com.gamedeals.watchlist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gamedeals.watchlist.data.AppDatabase
import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.price.PriceParser
import com.gamedeals.watchlist.price.PriceScripts
import com.gamedeals.watchlist.price.PriceUpdater
import com.gamedeals.watchlist.price.UrlUtils
import com.gamedeals.watchlist.ui.AppRoot
import com.gamedeals.watchlist.ui.BrowserState
import com.gamedeals.watchlist.ui.DealsTheme
import com.gamedeals.watchlist.work.WebViewPriceRefresher
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val browsers = mutableMapOf<Store, BrowserState>()

    /** A URL (from a notification or shared link) the UI should open. */
    var pendingOpenUrl by mutableStateOf<String?>(null)

    /** (index, total) while "check all prices" is running, null otherwise. */
    var refreshProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenUrl = urlFrom(intent)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            DealsTheme { AppRoot(this) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        urlFrom(intent)?.let { pendingOpenUrl = it }
    }

    private fun urlFrom(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_OPEN_URL) ?: intent?.data?.toString()?.takeIf { Store.fromUrl(it) != null }

    /** Returns (creating on first use) the WebView-backed browser for [store]. */
    fun browser(store: Store): BrowserState =
        browsers.getOrPut(store) { BrowserState.create(this, store, ::onPageLoaded) }

    /** When you visit a page that's on your watchlist, quietly refresh its stored price. */
    private fun onPageLoaded(url: String, view: WebView) {
        lifecycleScope.launch {
            val item = AppDatabase.get(this@MainActivity).watchDao().findByUrl(UrlUtils.normalize(url)) ?: return@launch
            view.postDelayed({
                view.evaluateJavascript(PriceScripts.EXTRACT) { result ->
                    val info = PriceParser.fromJsResult(result) ?: return@evaluateJavascript
                    if (info.price == null) return@evaluateJavascript
                    lifecycleScope.launch { PriceUpdater.apply(this@MainActivity, item.id, info, notify = false) }
                }
            }, 1_200)
        }
    }

    /** Re-checks every watched game using an off-screen browser. */
    fun refreshAllNow() {
        if (refreshProgress != null) return
        lifecycleScope.launch {
            val items = AppDatabase.get(this@MainActivity).watchDao().getAll()
            if (items.isEmpty()) return@launch
            refreshProgress = 0 to items.size
            try {
                WebViewPriceRefresher(this@MainActivity).refresh(
                    items = items,
                    onProgress = { done, total -> refreshProgress = done to total },
                    onResult = { item, info ->
                        PriceUpdater.apply(
                            this@MainActivity, item.id, info,
                            error = "Couldn't read the price – open the game to check",
                            notify = true,
                        )
                    },
                )
            } finally {
                refreshProgress = null
            }
        }
    }

    fun signOutAll() {
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        WebStorage.getInstance().deleteAllData()
        browsers.values.forEach {
            it.webView.clearCache(true)
            it.webView.loadUrl(it.store.homeUrl)
        }
        Toast.makeText(this, "Signed out of all sites", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        browsers.values.forEach { it.webView.destroy() }
        browsers.clear()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_URL = "open_url"
    }
}
