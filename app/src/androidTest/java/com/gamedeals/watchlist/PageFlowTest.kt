package com.gamedeals.watchlist

import android.Manifest
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.gamedeals.watchlist.data.Prefs
import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.price.PriceParser
import com.gamedeals.watchlist.price.PriceScripts
import com.gamedeals.watchlist.ui.BrowserState
import com.gamedeals.watchlist.web.evaluate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device UI tests of the Watch flow and list scanning, using real psdeals.net pages saved in
 * androidTest/assets and loaded under their real address (no network needed).
 * Results are also logged under the "SmokeTest" tag.
 */
@RunWith(AndroidJUnit4::class)
class PageFlowTest {

    companion object {
        private const val TAG = "SmokeTest"
        private const val GAME = "https://psdeals.net/us-store/game/2997658/sonic-racing-crossworlds-ps4-ps5"
        private const val LIST = "https://psdeals.net/us-store/collection/most_wanted"

        @BeforeClass
        @JvmStatic
        fun prepare() {
            // No automatic full sync while testing.
            Prefs.setAutoSync(InstrumentationRegistry.getInstrumentation().targetContext, false)
        }
    }

    // Grant the notification permission up front: otherwise Android's permission dialog covers
    // (and pauses) the app on first launch and the UI can't be driven.
    @get:Rule(order = 0)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    /** Clicks via the node's accessibility click action (more reliable than injected touches on CI emulators). */
    private fun click(text: String) {
        rule.onAllNodes(hasClickAction() and (hasText(text) or hasAnyDescendant(hasText(text))), useUnmergedTree = true)
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    private fun ps(): BrowserState {
        lateinit var state: BrowserState
        rule.runOnUiThread { state = rule.activity.browser(Store.PLAYSTATION) }
        return state
    }

    private fun openSavedPage(asset: String, url: String, titleContains: String) {
        val html = InstrumentationRegistry.getInstrumentation().context.assets.open(asset)
            .bufferedReader().use { it.readText() }
        click("PlayStation")
        rule.runOnUiThread { ps().webView.loadDataWithBaseURL(url, html, "text/html", "utf-8", url) }
        rule.waitUntil(30_000) { ps().title.contains(titleContains, ignoreCase = true) && !ps().loading }
        Thread.sleep(1_000)
    }

    @Test
    fun watchAGameAndSeeItOnTheWatchlist() {
        rule.onNodeWithText("Price Watchlist").assertExists()
        Log.i(TAG, "PASS Watchlist screen shown")

        openSavedPage("psdeals_game.html", GAME, "Sonic Racing")
        Log.i(TAG, "PASS Game page shown in the PlayStation tab: ${ps().title}")

        click("Watch")
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Add to watchlist")).fetchSemanticsNodes().isNotEmpty() }
        val priceLine = rule.onNode(hasText("Current price:", substring = true)).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString { it.text }
        assertTrue("Price should be detected, got '$priceLine'", priceLine.contains("39.99"))
        Log.i(TAG, "PASS Watch dialog detected the price ($priceLine)")

        click("Save")
        click("Watchlist")
        rule.waitUntil(15_000) {
            rule.onAllNodes(hasText("Sonic Racing: CrossWorlds", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val prices = rule.onAllNodes(hasText("$", substring = true)).fetchSemanticsNodes()
            .flatMap { n -> n.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } }
        assertTrue("Watchlist card should show \$39.99, saw $prices", prices.any { it.contains("39.99") })
        Log.i(TAG, "PASS Game saved and shown on the Watchlist with its price (\$39.99)")
    }

    @Test
    fun scanAListPageForImport() {
        openSavedPage("psdeals_most_wanted.html", LIST, "Most Wanted")
        val scan = runBlocking {
            withContext(Dispatchers.Main) { PriceParser.scanFromJsResult(ps().webView.evaluate(PriceScripts.SCAN_PAGE)) }
        }
        val priced = scan.cards.count { it.info.price != null }
        Log.i(TAG, "List scan: ${scan.cards.size} games, $priced priced, next page ${scan.nextUrl}")
        assertEquals(36, scan.cards.size)
        assertEquals(36, priced)
        assertEquals("$LIST/2", scan.nextUrl)
        Log.i(TAG, "PASS List page scanned: 36 games with prices, next page found")
    }
}
