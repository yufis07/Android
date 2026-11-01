package com.gamedeals.watchlist

import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.data.WatchItem
import com.gamedeals.watchlist.work.PriceCheckWorker
import com.gamedeals.watchlist.work.RefreshTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BackgroundRefreshTest {
    private val zone = ZoneId.of("Asia/Kuala_Lumpur")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 1, h, m, 0, 0, zone).toInstant()
    private val hour = 60 * 60 * 1000L

    @Test
    fun dailyRefreshIsAlignedToTheChosenTime() {
        assertEquals(2 * hour, RefreshTime.delayUntilNext(8, at(6, 0), zone))       // later today
        assertEquals(24 * hour, RefreshTime.delayUntilNext(8, at(8, 0), zone))      // exactly now -> tomorrow
        assertEquals(22 * hour + 30 * 60_000, RefreshTime.delayUntilNext(8, at(9, 30), zone)) // tomorrow
        assertEquals("8 AM", RefreshTime.label(8))
        assertEquals("12 PM", RefreshTime.label(12))
        assertEquals("9 PM", RefreshTime.label(21))
    }

    @Test
    fun detectsMissedRefreshes() {
        val now = 1_000L * hour
        assertFalse(RefreshTime.isOverdue(now - 20 * hour, 24, now))
        assertTrue(RefreshTime.isOverdue(now - 30 * hour, 24, now))
        assertFalse(RefreshTime.isOverdue(now - 100 * hour, 0, now)) // refresh turned off
    }

    @Test
    fun summarisesWhatChanged() {
        fun item(id: Long, price: Double?, target: Double? = null) = WatchItem(
            id = id, url = "https://psdeals.net/us-store/game/$id", store = Store.PLAYSTATION,
            title = "Game $id", currentPrice = price, targetPrice = target,
        )
        val before = listOf(item(1, 60.0, target = 30.0), item(2, 20.0), item(3, 10.0)).associateBy { it.id }
        val after = listOf(item(1, 29.99, target = 30.0), item(2, 15.0), item(3, 12.0), item(4, 5.0))
        val s = PriceCheckWorker.Summary.of(before, after, added = 1)
        assertTrue(s.hasNews)
        assertEquals("1 at target · 2 price drops · 1 new", s.text)
        assertEquals("Daily refresh: 1 game at your target price", s.title)
        assertEquals(
            listOf("🎯 Game 1 hit your target", "📉 Game 2 dropped in price", "➕ 1 new game from your site lists"),
            s.lines,
        )

        val quiet = PriceCheckWorker.Summary.of(before, before.values.toList(), added = 0)
        assertFalse(quiet.hasNews)
        assertEquals("3 games checked, no changes", quiet.text)
    }
}
