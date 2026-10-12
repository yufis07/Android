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
import com.gamedeals.watchlist.data.Prefs
import com.gamedeals.watchlist.data.WatchRepository
import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.price.GameCard
import com.gamedeals.watchlist.price.PriceParser
import com.gamedeals.watchlist.price.PriceScripts
import com.gamedeals.watchlist.price.PriceUpdater
import com.gamedeals.watchlist.price.SiteListAdd
import com.gamedeals.watchlist.price.UrlUtils
import com.gamedeals.watchlist.ui.AppRoot
import com.gamedeals.watchlist.ui.BrowserCallbacks
import com.gamedeals.watchlist.ui.BrowserState
import com.gamedeals.watchlist.ui.DealsTheme
import com.gamedeals.watchlist.web.evaluate
import com.gamedeals.watchlist.work.WebViewPriceRefresher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), BrowserCallbacks {

    private val browsers = mutableMapOf<Store, BrowserState>()
    private val repo by lazy { WatchRepository(AppDatabase.get(this).watchDao()) }

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
        browsers.getOrPut(store) { BrowserState.create(this, store, this) }

    /**
     * After each page load:
     *  - if the page is a watched game, quietly refresh its stored price;
     *  - if auto-sync is on, add any games marked as on your site watchlist / wishlist.
     */
    override fun onPageLoaded(url: String, view: WebView) {
        lifecycleScope.launch {
            delay(1_200) // let the page finish rendering prices and list icons
            val item = AppDatabase.get(this@MainActivity).watchDao().findByUrl(UrlUtils.normalize(url))
            if (item != null) {
                val info = PriceParser.fromJsResult(view.evaluate(PriceScripts.EXTRACT))
                if (info?.price != null) PriceUpdater.apply(this@MainActivity, item.id, info, notify = false)
            }
            if (Prefs.autoSync(this@MainActivity)) {
                val store = Store.fromUrl(url) ?: return@launch
                val onSiteLists = scanPage(view).filter { it.onSiteList }
                if (onSiteLists.isEmpty()) return@launch
                val added = repo.importCards(store, onSiteLists)
                if (added > 0) {
                    toast("Synced $added game${if (added == 1) "" else "s"} from your ${store.host} lists")
                }
            }
        }
    }

    /** You tapped the site's own "Add to Watchlist / Wishlist" — mirror it in the app. */
    override fun onSiteListAdd(add: SiteListAdd, view: WebView) {
        if (!Prefs.autoSync(this)) return
        lifecycleScope.launch {
            val url = add.url?.takeIf { Store.fromUrl(it) != null }
                ?: UrlUtils.gameUrl(add.pageUrl ?: view.url, add.gameId)
                ?: return@launch
            val store = Store.fromUrl(url) ?: return@launch
            var info = add.info
            // Added from the game's own page: read its full details from the page.
            if (UrlUtils.normalize(url) == UrlUtils.normalize(view.url ?: "")) {
                PriceParser.fromJsResult(view.evaluate(PriceScripts.EXTRACT))?.let { info = it }
            }
            val title = info.title ?: "Game #${add.gameId}"
            val listName = if (add.list.startsWith("wish")) "wishlist" else "watchlist"
            if (repo.addIfMissing(url, store, title, info)) {
                toast("“$title” added to your $listName and the app watchlist")
            }
        }
    }

    /** Game cards on the page shown in [view] (used by import and auto-sync). */
    suspend fun scanPage(view: WebView): List<GameCard> =
        PriceParser.cardsFromJsResult(view.evaluate(PriceScripts.SCAN_PAGE))

    suspend fun importCards(store: Store, cards: List<GameCard>): Int = repo.importCards(store, cards)

    fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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

    /** Reloads any open site tabs, e.g. after toggling the ad blocker. */
    fun reloadBrowsers() {
        browsers.values.forEach { it.webView.reload() }
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
