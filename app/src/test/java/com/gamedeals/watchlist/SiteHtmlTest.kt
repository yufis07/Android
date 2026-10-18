package com.gamedeals.watchlist

import com.gamedeals.watchlist.price.SiteHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteHtmlTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    @Test
    fun readsRealCollectionPageWithPagination() {
        // Trimmed from https://psdeals.net/us-store/collection/most_wanted (36 cards per page)
        val page = SiteHtml.parse(fixture("ps_collection_page1.html"), "https://psdeals.net/us-store/collection/most_wanted")
        assertEquals(3, page.cards.size)
        val first = page.cards[0]
        assertEquals("Marvel’s Wolverine", first.title)
        assertEquals("https://psdeals.net/us-store/game/3232736/marvels-wolverine", first.url)
        assertEquals(69.99, first.info.price!!, 0.001)
        assertEquals("USD", first.info.currency)
        assertEquals("$69.99", first.info.priceText)
        assertTrue(first.info.imageUrl!!.startsWith("https://store.playstation.com/"))
        assertFalse(first.onSiteList)
        assertEquals("https://psdeals.net/us-store/collection/most_wanted/2", page.nextUrl)
        assertEquals(false, page.signedIn)
        assertTrue(page.listLinks.isEmpty())
        assertFalse(page.isChallenge)
        assertTrue(page.cards.all { it.info.price != null })
    }

    @Test
    fun readsHomePageCards() {
        val page = SiteHtml.parse(fixture("ps_home_cards.html"), "https://psdeals.net/us-store")
        assertEquals(2, page.cards.size)
        assertEquals("Sonic Racing: CrossWorlds PS4 & PS5", page.cards[0].title)
        assertEquals(39.99, page.cards[0].info.price!!, 0.001) // discounted price, not the struck-through one
        assertNull(page.nextUrl)
        assertNull(page.signedIn)
    }

    @Test
    fun findsYourListLinksAndActiveMarkersWhenSignedIn() {
        val html = """
            <html><head><script>dataLayer.push({'user_id': 48213, 'user_premium': false});</script></head><body>
            <ul class="dropdown-menu">
              <li><a href="https://psdeals.net/us-store/profile/watchlist">Watchlist (12)</a></li>
              <li><a href="/us-store/my-collection?list=wish">Wishlist</a></li>
              <li><a href="https://psdeals.net/us-store/profile/purchaseslist">Purchased</a></li>
              <li><a href="https://psdeals.net/us-store/collection/most_wanted">Most Wanted</a></li>
              <li><a href="https://psdeals.net/us-store#">Add to Watchlist</a></li>
              <li><a href="https://xbdeals.net/us-store/profile/watchlist">Xbox watchlist</a></li>
            </ul>
            <div class="game-collection-item"><a class="game-collection-item-link" href="/us-store/game/11/a">
              <div class="icon-watchlist active"></div><span class="game-collection-item-details-title">Game A</span>
              <span class="game-collection-item-price">$5.00</span></a></div>
            <div class="game-collection-item"><a class="game-collection-item-link" href="/us-store/game/12/b">
              <div class="icon-watchlist"></div><span class="game-collection-item-details-title">Game B</span>
              <span class="game-collection-item-price-discount">$7.50</span>
              <span class="game-collection-item-price strikethrough">$15.00</span></a></div>
            <ul class="pagination"><li class="next disabled"><span>»</span></li></ul>
            </body></html>
        """.trimIndent()
        val page = SiteHtml.parse(html, "https://psdeals.net/us-store")
        assertEquals(true, page.signedIn)
        assertEquals(
            listOf("https://psdeals.net/us-store/profile/watchlist", "https://psdeals.net/us-store/my-collection?list=wish"),
            page.listLinks,
        )
        assertEquals(listOf(true, false), page.cards.map { it.onSiteList })
        assertEquals(7.5, page.cards[1].info.price!!, 0.001)
        assertNull(page.nextUrl)
    }

    @Test
    fun detectsBotCheckPages() {
        val html = "<html><head><title>Just a moment...</title></head><body><div id=\"challenge-stage\"></div></body></html>"
        assertTrue(SiteHtml.parse(html, "https://psdeals.net/us-store").isChallenge)
    }
}
