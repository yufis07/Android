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
        assertEquals(
            "https://psdeals.net/us-store/game/123/x",
            UrlUtils.normalize("https://PSDeals.net/us-store/game/123/x/?ref=1#top"),
        )
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
}
