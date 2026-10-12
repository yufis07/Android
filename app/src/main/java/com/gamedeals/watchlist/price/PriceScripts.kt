package com.gamedeals.watchlist.price

/** JavaScript injected into the WebView to read the current game's price. Mirrors [PriceParser.fromHtml]. */
object PriceScripts {
    val EXTRACT = """
        (function () {
          var r = { title: null, price: null, priceText: null, currency: null, image: null };
          function meta(sel) {
            var e = document.querySelector(sel);
            if (!e) return null;
            var v = (e.getAttribute('content') || e.textContent || '').trim();
            return v.length ? v : null;
          }
          function walk(o) {
            if (!o || typeof o !== 'object') return;
            if (Array.isArray(o)) { o.forEach(walk); return; }
            if (o['@graph']) walk(o['@graph']);
            if (o.offers) {
              var offers = [].concat(o.offers);
              for (var i = 0; i < offers.length; i++) {
                var of = offers[i];
                if (!of) continue;
                var p = of.price != null ? of.price : of.lowPrice;
                if (p != null && r.price == null) { r.price = String(p); r.currency = of.priceCurrency || r.currency; }
              }
              if (o.name && !r.title) r.title = o.name;
              if (o.image && !r.image) {
                var im = [].concat(o.image)[0];
                r.image = typeof im === 'string' ? im : (im && im.url) || null;
              }
            }
          }
          try {
            document.querySelectorAll('script[type="application/ld+json"]').forEach(function (s) {
              try { walk(JSON.parse(s.textContent)); } catch (e) {}
            });
          } catch (e) {}
          if (r.price == null) r.price = meta('[itemprop="price"]') || meta('meta[property="product:price:amount"]');
          if (!r.currency) r.currency = meta('[itemprop="priceCurrency"]') || meta('meta[property="product:price:currency"]');
          if (r.price == null) {
            var els = document.querySelectorAll('[class*="price"]');
            for (var j = 0; j < els.length; j++) {
              var el = els[j];
              var cls = typeof el.className === 'string' ? el.className : (el.getAttribute('class') || '');
              if (/old|original|strike|regular|base|full|history|crossed|lowest/i.test(cls)) continue;
              if (el.querySelector('[class*="price"]')) continue;
              if (el.closest('s, del, strike')) continue;
              var t = (el.textContent || '').replace(/\s+/g, ' ').trim();
              if (t.length > 0 && t.length < 25 && (/\d/.test(t) || /free/i.test(t))) { r.priceText = t; r.price = t; break; }
            }
          }
          r.title = r.title || meta('meta[property="og:title"]') || document.title;
          r.image = r.image || meta('meta[property="og:image"]');
          return JSON.stringify(r);
        })();
    """.trimIndent()
}
