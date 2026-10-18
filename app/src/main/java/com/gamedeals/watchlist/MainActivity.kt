package com.gamedeals.watchlist

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.FrameLayout
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
import com.gamedeals.watchlist.data.Backup
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
import com.gamedeals.watchlist.sync.ListSyncRunner
import com.gamedeals.watchlist.sync.SiteListSync
import com.gamedeals.watchlist.sync.WebViewHtmlLoader
import com.gamedeals.watchlist.web.evaluate
import com.gamedeals.watchlist.work.WebViewPriceRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), BrowserCallbacks {

    private val browsers = mutableMapOf<Store, BrowserState>()

    /**
     * Holds every site tab's WebView. It stays attached while you switch tabs; switching only
     * changes which child is visible, so pages never reload or redraw from scratch.
     */
    val browserContainer: FrameLayout by lazy { FrameLayout(this) }

    /** The site tab currently (or most recently) shown. */
    var activeStore: Store? = null
        private set
    private var browsersVisible = false
    private val repo by lazy { WatchRepository(AppDatabase.get(this).watchDao()) }

    /** A URL (from a notification or shared link) the UI should open. */
    var pendingOpenUrl by mutableStateOf<String?>(null)

    /** (index, total) while "check all prices" is running, null otherwise. */
    var refreshProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) lifecycleScope.launch { exportTo(uri) }
        }
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) lifecycleScope.launch { importFrom(uri) }
        }

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
        autoSyncSiteListsIfDue()
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
        browsers.getOrPut(store) {
            BrowserState.create(this, store, this).also { state ->
                state.webView.visibility = View.INVISIBLE
                browserContainer.addView(
                    state.webView,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        }

    /**
     * Shows [store]'s tab (or hides all site tabs when null, e.g. on the Watchlist screen).
     * Hidden tabs are paused so they don't use CPU or battery in the background.
     */
    fun showBrowser(store: Store?) {
        if (store != null) {
            browser(store)
            activeStore = store
        }
        browsersVisible = store != null
        browsers.forEach { (s, state) ->
            val visible = s == store
            state.webView.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            state.webView.settings.offscreenPreRaster = visible // smoother scrolling for the visible tab
            if (visible) state.webView.onResume() else state.webView.onPause()
        }
    }

    /** When you open a watched game's page, quietly refresh its stored price. */
    override fun onPageLoaded(url: String, view: WebView) {
        if (!UrlUtils.isGamePage(url)) return
        lifecycleScope.launch {
            val item = AppDatabase.get(this@MainActivity).watchDao().findByUrl(UrlUtils.normalize(url)) ?: return@launch
            delay(800) // let the page finish rendering its price
            val info = PriceParser.fromJsResult(view.evaluate(PriceScripts.EXTRACT))
            if (info?.price != null) PriceUpdater.apply(this@MainActivity, item.id, info, notify = false)
        }
    }

    /** Live sync: games on your site lists showed up on the page you're browsing. */
    override fun onCardsFound(cards: List<GameCard>, view: WebView) {
        if (!Prefs.autoSync(this)) return
        val store = Store.fromUrl(view.url) ?: return
        lifecycleScope.launch {
            val added = repo.importCards(store, cards)
            if (added > 0) toast("Synced $added game${if (added == 1) "" else "s"} from your ${store.host} lists")
        }
    }

    /** Progress text while a full site-list sync runs, null otherwise. */
    var listSyncStatus by mutableStateOf<String?>(null)
        private set

    /** Bumped after each full sync so the UI re-reads the saved summary. */
    var listSyncVersion by mutableStateOf(0)
        private set

    /**
     * Full sync: reads every page of your Watchlist / Wishlist on all three sites.
     * [manual] shows the result even when nothing changed.
     */
    fun syncSiteLists(manual: Boolean) {
        if (listSyncStatus != null) return
        listSyncStatus = "Starting…"
        lifecycleScope.launch {
            try {
                val outcome = ListSyncRunner.run(this@MainActivity, allowWebView = true) { progress ->
                    runOnUiThread { listSyncStatus = progress }
                }
                val parts = buildList {
                    if (outcome.added > 0) add("${outcome.added} new game${if (outcome.added == 1) "" else "s"} added")
                    if (outcome.pricesUpdated > 0) add("${outcome.pricesUpdated} price${if (outcome.pricesUpdated == 1) "" else "s"} updated")
                }
                when {
                    parts.isNotEmpty() -> toast("Site lists synced: " + parts.joinToString(", "))
                    manual && outcome.anySignedIn -> toast("Site lists synced – already up to date")
                    manual -> toast("Sign in on the Xbox, PlayStation or Nintendo tab first")
                }
            } finally {
                listSyncStatus = null
                listSyncVersion++
            }
        }
    }

    /** Runs a full sync on app start when it's on and hasn't run for a while. */
    private fun autoSyncSiteListsIfDue() {
        if (!Prefs.autoSync(this)) return
        if (System.currentTimeMillis() - Prefs.lastListSync(this) < 6 * 60 * 60 * 1000L) return
        lifecycleScope.launch {
            delay(3_000) // don't compete with app startup
            syncSiteLists(manual = false)
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

    /**
     * Game cards on the page shown in [view] **and all following pages** of the same list
     * (the sites show 36 games per page), for "Import games from this page".
     */
    suspend fun scanAllPages(view: WebView, onProgress: (String) -> Unit): List<GameCard> {
        val first = PriceParser.scanFromJsResult(view.evaluate(PriceScripts.SCAN_PAGE))
        val next = first.nextUrl ?: return first.cards
        val loader = WebViewHtmlLoader(this)
        return try {
            val rest = SiteListSync(this).crawl(next, loader, yourList = false) { page ->
                onProgress("Reading page ${page + 1}…")
            }
            (first.cards + rest.cards).distinctBy { UrlUtils.normalize(it.url) }
        } finally {
            loader.close()
        }
    }

    suspend fun importCards(store: Store, cards: List<GameCard>): Int = repo.importCards(store, cards)

    fun exportWatchlist() = exportLauncher.launch("game-deals-watchlist.json")

    fun importWatchlist() = importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))

    private suspend fun exportTo(uri: Uri) {
        val result = runCatching {
            val json = Backup.toJson(repo.allItems())
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("Can't write file")
            }
        }
        toast(result.fold({ "Watchlist exported" }, { "Export failed: ${it.message}" }))
    }

    private suspend fun importFrom(uri: Uri) {
        val result = runCatching {
            val text = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Can't read file")
            }
            repo.importBackup(Backup.fromJson(text))
        }
        toast(result.fold({ "Imported $it game${if (it == 1) "" else "s"}" }, { "Import failed: not a watchlist backup" }))
    }

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
        browsers.values.forEach { it.webView.onPause() }
    }

    override fun onResume() {
        super.onResume()
        if (browsersVisible) activeStore?.let { browsers[it]?.webView?.onResume() }
    }

    /** Frees memory used by site tabs you aren't looking at when Android asks for it. */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            val unused = browsers.keys.filter { it != activeStore }
            unused.forEach { store ->
                browsers.remove(store)?.webView?.let { webView ->
                    browserContainer.removeView(webView)
                    webView.destroy()
                }
            }
        }
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
