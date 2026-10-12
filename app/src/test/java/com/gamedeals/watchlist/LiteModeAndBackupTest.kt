package com.gamedeals.watchlist

import com.gamedeals.watchlist.data.Backup
import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.data.WatchItem
import com.gamedeals.watchlist.web.AdBlocker
import com.gamedeals.watchlist.web.LiteMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteModeAndBackupTest {

    @Test
    fun liteModeSkipsFontsAndMediaButKeepsSiteContent() {
        assertTrue(LiteMode.shouldSkip("https://fonts.googleapis.com/css2?family=Roboto", "fonts.googleapis.com"))
        assertTrue(LiteMode.shouldSkip("https://cdn.example.com/f/inter.woff2", "cdn.example.com"))
        assertTrue(LiteMode.shouldSkip("https://video.example.com/trailer.mp4?x=1", "video.example.com"))
        assertTrue(LiteMode.shouldSkip("https://cdn.example.com/stream/master.m3u8", "cdn.example.com"))
        // The deal sites' own icon font (watchlist / wishlist buttons) must keep working
        assertFalse(LiteMode.shouldSkip("https://psdeals.net/assets/fonts/flaticon.woff2", "psdeals.net"))
        // Game cover art and pages are untouched
        assertFalse(LiteMode.shouldSkip("https://store.playstation.com/store/api/chihiro/image?w=100", "store.playstation.com"))
        assertFalse(LiteMode.shouldSkip("https://psdeals.net/us-store/game/1/x", "psdeals.net"))
        assertFalse(LiteMode.shouldSkip("https://psdeals.net/assets/js/common.min.js", "psdeals.net"))
    }

    @Test
    fun blocksAnalyticsTrackers() {
        assertTrue(AdBlocker.isAdHost("static.cloudflareinsights.com"))
        assertTrue(AdBlocker.isAdHost("script.hotjar.com"))
        assertTrue(AdBlocker.isAdHost("www.clarity.ms"))
        assertFalse(AdBlocker.isAdHost("challenges.cloudflare.com"))
    }

    @Test
    fun backupRoundTrips() {
        val items = listOf(
            WatchItem(
                id = 7, url = "https://psdeals.net/us-store/game/1", store = Store.PLAYSTATION, title = "Game A",
                currentPrice = 19.99, priceText = "$19.99", currency = "USD", targetPrice = 15.0, lowestPrice = 12.5,
                addedAt = 1000L, lastChecked = 2000L,
            ),
            WatchItem(url = "https://ntdeals.net/us-store/game/2", store = Store.NINTENDO, title = "Game B", addedAt = 3000L),
        )
        val restored = Backup.fromJson(Backup.toJson(items))
        assertEquals(2, restored.size)
        assertEquals(0L, restored[0].id) // ids are reassigned on import
        assertEquals("Game A", restored[0].title)
        assertEquals(Store.PLAYSTATION, restored[0].store)
        assertEquals(15.0, restored[0].targetPrice!!, 0.001)
        assertEquals("$19.99", restored[0].priceText)
        assertEquals(2000L, restored[0].lastChecked)
        assertNull(restored[1].targetPrice)
        assertNull(restored[1].lastChecked)
        assertEquals(Store.NINTENDO, restored[1].store)
    }

    @Test
    fun backupSkipsBrokenEntries() {
        val json = """{"items":[{"title":"no url"},{"url":"https://xbdeals.net/us-store/game/5","title":"X"}]}"""
        val restored = Backup.fromJson(json)
        assertEquals(1, restored.size)
        assertEquals(Store.XBOX, restored[0].store) // inferred from URL
    }
}
