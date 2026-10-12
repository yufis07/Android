package com.gamedeals.watchlist

import com.gamedeals.watchlist.data.Store
import com.gamedeals.watchlist.price.PriceFormat
import com.gamedeals.watchlist.price.PriceParser
import com.gamedeals.watchlist.price.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceParserTest {

    @Test
    fun parsesCommonPriceFormats() {
        assertEquals(19.99, PriceParser.parseNumber("$19.99")!!, 0.001)
        assertEquals(1299.99, PriceParser.parseNumber("$1,299.99")!!, 0.001)
        assertEquals(1299.99, PriceParser.parseNumber("1.299,99 €")!!, 0.001)
        assertEquals(19.99, PriceParser.parseNumber("19,99 €")!!, 0.001)
        assertEquals(1299.0, PriceParser.parseNumber("¥1,299")!!, 0.001)
        assertEquals(1299.0, PriceParser.parseNumber("1 299 ₽")!!, 0.001)
        assertEquals(59.0, PriceParser.parseNumber("R$ 59")!!, 0.001)
        assertEquals(0.0, PriceParser.parseNumber("Free")!!, 0.001)
        assertNull(PriceParser.parseNumber(""))
        assertNull(PriceParser.parseNumber("N/A"))
    }

    @Test
    fun readsJsonLdOffers() {
        val html = """
            <html><head>
            <script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Product","name":"Elden Ring",
             "image":["https://img.example/er.jpg"],
             "offers":{"@type":"Offer","price":"29.99","priceCurrency":"USD"}}
            </script></head><body><span class="price">$99.99</span></body></html>
        """.trimIndent()
        val info = PriceParser.fromHtml(html, "https://psdeals.net/us-store/game/1/elden-ring")
        assertEquals("Elden Ring", info.title)
        assertEquals(29.99, info.price!!, 0.001)
        assertEquals("USD", info.currency)
        assertEquals("https://img.example/er.jpg", info.imageUrl)
    }

    @Test
    fun readsMicrodata() {
        val html = """
            <html><head><meta property="og:title" content="Halo Infinite — price history • USA"></head>
            <body><div itemscope><meta itemprop="price" content="14.99"><meta itemprop="priceCurrency" content="USD"></div></body></html>
        """.trimIndent()
        val info = PriceParser.fromHtml(html, "https://xbdeals.net/us-store/game/1/halo")
        assertEquals("Halo Infinite", info.title)
        assertEquals(14.99, info.price!!, 0.001)
        assertEquals("USD", info.currency)
    }

    @Test
    fun fallsBackToPriceClassSkippingOldPrice() {
        val html = """
            <html><head><title>Mario Kart 8 Deluxe | NTDeals</title></head><body>
              <div class="game-price">
                <span class="price-old">$59.99</span>
                <span class="price-discount">$39.99</span>
              </div>
            </body></html>
        """.trimIndent()
        val info = PriceParser.fromHtml(html, "https://ntdeals.net/us-store/game/1/mk8")
        assertEquals("Mario Kart 8 Deluxe", info.title)
        assertEquals(39.99, info.price!!, 0.001)
        assertEquals("$39.99", info.priceText)
        assertEquals("$", info.currency)
    }

    @Test
    fun parsesWebViewResult() {
        val js = "\"{\\\"title\\\":\\\"Starfield\\\",\\\"price\\\":\\\"€49,99\\\",\\\"priceText\\\":\\\"€49,99\\\",\\\"currency\\\":null,\\\"image\\\":null}\""
        val info = PriceParser.fromJsResult(js)!!
        assertEquals("Starfield", info.title)
        assertEquals(49.99, info.price!!, 0.001)
        assertEquals("€", info.currency)
        assertNull(info.imageUrl)
        assertNull(PriceParser.fromJsResult("null"))
    }

    @Test
    fun detectsStoreAndNormalizesUrls() {
        assertEquals(Store.PLAYSTATION, Store.fromUrl("https://psdeals.net/us-store/game/123/x"))
        assertEquals(Store.XBOX, Store.fromUrl("https://www.xbdeals.net/gb-store"))
        assertEquals(Store.NINTENDO, Store.fromUrl("https://ntdeals.net/"))
        assertNull(Store.fromUrl("https://example.com/"))
        // Game pages collapse to the site's canonical slug-less URL
        assertEquals(
            "https://psdeals.net/us-store/game/123",
            UrlUtils.normalize("https://PSDeals.net/us-store/game/123/x/?ref=1#top"),
        )
        assertEquals(
            "https://xbdeals.net/gb-store/game/55",
            UrlUtils.normalize("https://www.xbdeals.net/gb-store/game/55/halo-infinite"),
        )
        assertEquals("https://ntdeals.net/us-store/discounts", UrlUtils.normalize("https://ntdeals.net/us-store/discounts/?page=2"))
        assertEquals(
            "https://psdeals.net/us-store/game/999",
            UrlUtils.gameUrl("https://psdeals.net/us-store/collection/most_wanted", "999"),
        )
        assertNull(UrlUtils.gameUrl("https://psdeals.net/", "999"))
    }

    @Test
    fun formatsPrices() {
        assertEquals("Free", PriceFormat.format(0.0, "USD"))
        assertEquals("—", PriceFormat.format(null, "USD"))
        assertEquals("15", PriceFormat.plain(15.0))
        assertEquals("14.99", PriceFormat.plain(14.99))
        assertEquals("€", PriceFormat.symbolFrom("€49,99"))
        assertEquals("R$", PriceFormat.symbolFrom("R$ 59,90"))
    }

    @Test
    fun readsRealPsDealsGamePage() {
        // Trimmed from https://psdeals.net/us-store/game/2997658/sonic-racing-crossworlds-ps4-ps5
        val html = """
            <html><head>
            <title>43% discount on Sonic Racing: CrossWorlds PS4 &amp; PS5 PS5 / PS4 — buy online  — PS Deals USA</title>
            <meta property="og:title" content="43% discount on Sonic Racing: CrossWorlds PS4 &amp; PS5 PS5 / PS4 — buy online — PS Deals USA">
            <script type="application/ld+json">{"@context":"http://schema.org","@type":"WebSite","name":"PS Deals","url":"https://psdeals.net/"}</script>
            </head><body>
            <div class="game-title-info"><span itemprop="name" class="game-title-info-name">Sonic Racing: CrossWorlds PS4 &amp; PS5</span></div>
            <div itemprop="offers" itemscope itemtype="http://schema.org/Offer">
              <meta itemprop="price" content="39.99"><meta itemprop="priceCurrency" content="USD">
            </div>
            <div class="game-collection-item-prices">
              <span class="game-collection-item-price-discount">${'$'}19.99</span>
              <span class="game-collection-item-price strikethrough">${'$'}29.99</span>
            </div>
            </body></html>
        """.trimIndent()
        val info = PriceParser.fromHtml(html, "https://psdeals.net/us-store/game/2997658/sonic")
        assertEquals("Sonic Racing: CrossWorlds PS4 & PS5", info.title)
        assertEquals(39.99, info.price!!, 0.001)
        assertEquals("USD", info.currency)
    }

    @Test
    fun cleansSiteTitles() {
        assertEquals(
            "Sonic Racing: CrossWorlds PS4 & PS5 PS5 / PS4",
            PriceParser.cleanTitle("43% discount on Sonic Racing: CrossWorlds PS4 & PS5 PS5 / PS4 — buy online — PS Deals USA"),
        )
    }

    @Test
    fun parsesScannedCards() {
        val js = org.json.JSONObject.quote(
            """[{"id":"1","url":"https://psdeals.net/us-store/game/1/a","title":"Game A","priceText":"${'$'}9.99",""" +
                """"image":null,"watchlisted":true,"wishlisted":false},""" +
                """{"id":"2","url":"https://psdeals.net/us-store/game/2/b","title":"Game B","priceText":null,""" +
                """"image":null,"watchlisted":false,"wishlisted":false},{"title":"no url"}]"""
        )
        val cards = PriceParser.cardsFromJsResult(js)
        assertEquals(2, cards.size)
        assertEquals("Game A", cards[0].title)
        assertEquals(9.99, cards[0].info.price!!, 0.001)
        assertEquals(true, cards[0].onSiteList)
        assertNull(cards[1].info.price)
        assertEquals(false, cards[1].onSiteList)
    }

    @Test
    fun parsesSiteListAdd() {
        val add = PriceParser.siteAddFromJson(
            """{"gameId":"2997658","list":"watchlist","url":null,"title":"Sonic Racing","priceText":"${'$'}39.99",""" +
                """"image":null,"pageUrl":"https://psdeals.net/us-store/game/2997658/sonic"}"""
        )!!
        assertEquals("2997658", add.gameId)
        assertNull(add.url)
        assertEquals("Sonic Racing", add.info.title)
        assertEquals(39.99, add.info.price!!, 0.001)
        assertNull(PriceParser.siteAddFromJson("""{"list":"watchlist"}"""))
    }
}
