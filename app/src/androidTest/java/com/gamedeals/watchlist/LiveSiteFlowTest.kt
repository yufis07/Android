package com.gamedeals.watchlist

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gamedeals.watchlist.data.Prefs
import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.ui.BrowserState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end checks against the live psdeals.net site on a real (emulated) device.
 * Results are also logged under the "SmokeTest" tag so CI can print them.
 */
@RunWith(AndroidJUnit4::class)
class LiveSiteFlowTest {

    companion object {
        private const val TAG = "SmokeTest"
        private const val GAME = "https://psdeals.net/us-store/game/2997658/sonic-racing-crossworlds-ps4-ps5"
        private const val LIST = "https://psdeals.net/us-store/collection/most_wanted"

        @BeforeClass
        @JvmStatic
        fun prepare() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            // Keep the test deterministic: no automatic full sync running in the background.
            Prefs.setAutoSync(ctx, false)
        }
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun ps(): BrowserState {
        lateinit var state: BrowserState
        rule.runOnUiThread { state = rule.activity.browser(Store.PLAYSTATION) }
        return state
    }

    private fun loadInPlayStationTab(url: String, titleContains: String) {
        rule.onNodeWithText("PlayStation").performClick()
        rule.runOnUiThread { ps().webView.loadUrl(url) }
        rule.waitUntil(90_000) {
            val s = ps()
            s.title.contains(titleContains, ignoreCase = true) && !s.loading
        }
        Thread.sleep(3_000) // let the page finish rendering prices
    }

    @Test
    fun watchAGameFromTheLiveSite() {
        rule.onNodeWithText("Price Watchlist").assertExists()
        Log.i(TAG, "PASS Watchlist screen shown")

        loadInPlayStationTab(GAME, "Sonic Racing")
        Log.i(TAG, "PASS Game page loaded: ${ps().title}")

        rule.onNodeWithText("Watch").performClick()
        rule.waitUntil(20_000) { rule.onAllNodes(hasText("Add to watchlist")).fetchSemanticsNodes().isNotEmpty() }
        val priceLine = rule.onNode(hasText("Current price:", substring = true)).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString { it.text }
        val titleField = rule.onAllNodes(hasText("Sonic Racing", substring = true)).fetchSemanticsNodes().size
        Log.i(TAG, "Watch dialog: '$priceLine', title field matches=$titleField")
        assertTrue("A real price should be detected, got '$priceLine'", Regex("""\$\d+\.\d\d""").containsMatchIn(priceLine))
        Log.i(TAG, "PASS Price detected from the live page ($priceLine)")

        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Watchlist").performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Sonic Racing", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val shownPrice = rule.onAllNodes(hasText("$", substring = true)).fetchSemanticsNodes()
            .flatMap { n -> n.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } }
            .firstOrNull { it.matches(Regex("""\$\d+\.\d\d""")) }
        assertTrue("Price should be shown on the watchlist card", shownPrice != null)
        Log.i(TAG, "PASS Game saved and shown on the Watchlist with price $shownPrice")
    }

    @Test
    fun importReadsEveryPageOfAList() {
        loadInPlayStationTab(LIST, "Most Wanted")
        val started = System.currentTimeMillis()
        val cards = runBlocking {
            withContext(Dispatchers.Main) { rule.activity.scanAllPages(ps().webView) { Log.i(TAG, it) } }
        }
        val seconds = (System.currentTimeMillis() - started) / 1000.0
        val priced = cards.count { it.info.price != null }
        Log.i(TAG, "Import scan: ${cards.size} games, $priced with prices, in ${"%.1f".format(seconds)} s")
        assertTrue("Should read more than the first page (36 games), got ${cards.size}", cards.size > 36)
        assertTrue("Almost every game should have a price ($priced/${cards.size})", priced >= cards.size * 9 / 10)
        Log.i(TAG, "PASS Import read ${cards.size} games across all pages ($priced priced) in ${"%.1f".format(seconds)} s")
    }
}
