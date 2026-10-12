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
          var nameEl = document.querySelector('.game-title-info-name, [itemprop="name"][class*="title"], h1[itemprop="name"]');
          var name = nameEl ? nameEl.textContent.replace(/\s+/g, ' ').trim() : null;
          r.title = name || r.title || meta('meta[property="og:title"]') || document.title;
          r.image = r.image || meta('meta[property="og:image"]');
          return JSON.stringify(r);
        })();
    """.trimIndent()

    /**
     * Lists every game card on the current page (search results, collections, or your own
     * watchlist / wishlist pages on the site) for "Import games from this page".
     */
    val SCAN_PAGE = """
        (function () {
          var seen = {}, out = [];
          function txt(el) { return el ? el.textContent.replace(/\s+/g, ' ').trim() : null; }
          function priceOf(c) {
            var p = c.querySelector('[class*="price-discount"], [class*="price-current"], [class*="price-final"]');
            if (p) return txt(p);
            var els = c.querySelectorAll('[class*="price"]');
            for (var i = 0; i < els.length; i++) {
              var cls = els[i].getAttribute('class') || '';
              if (/old|original|strike|regular|base|full|history|crossed|lowest/i.test(cls)) continue;
              if (els[i].querySelector('[class*="price"]')) continue;
              var t = txt(els[i]);
              if (t && t.length < 25 && (/\d/.test(t) || /free/i.test(t))) return t;
            }
            return null;
          }
          document.querySelectorAll('a[href*="/game/"]').forEach(function (a) {
            var m = a.href.match(/\/game\/(\d+)/);
            if (!m || seen[m[1]]) return;
            if (location.pathname.indexOf('/game/' + m[1]) >= 0) return; // the page's own game
            var c = a.closest('.game-collection-item') || a.closest('[class*="game-table-item"]') ||
                    a.closest('[class*="game-item"]') || a.closest('li') || a;
            var title = txt(c.querySelector('[class*="item-details-title"], [class*="item-title"], [class*="title"]')) ||
                        a.getAttribute('title') || txt(a);
            if (!title) return;
            seen[m[1]] = true;
            var img = c.querySelector('img[class*="item-image"], img[itemprop="image"], img:not([src*="flag"]):not([class*="flag"])');
            out.push({
              id: m[1],
              url: a.href.split('#')[0].split('?')[0],
              title: title,
              priceText: priceOf(c),
              image: img ? (img.getAttribute('data-src') || img.getAttribute('src')) : null,
              watchlisted: !!c.querySelector('.icon-watchlist.active'),
              wishlisted: !!c.querySelector('.icon-wishlist.active')
            });
          });
          return JSON.stringify(out);
        })();
    """.trimIndent()

    /**
     * Installed on every deal-site page. When you use the site's own "Add to Watchlist" or
     * "Add to Wishlist" buttons, the site posts `/user/add-to-watch-list` (or wish-list) with a
     * `game_id`; we spot that request, look up the game's card and tell the app, so the game
     * is added to the app's watchlist too.
     */
    val SITE_BRIDGE = """
        (function () {
          if (window.__gdwBridge || !window.GameDealsApp) return;
          window.__gdwBridge = true;
          function txt(el) { return el ? el.textContent.replace(/\s+/g, ' ').trim() : null; }
          function info(id, list) {
            var r = { gameId: id, list: list, url: null, title: null, priceText: null, image: null, pageUrl: location.href };
            function card(el) {
              return el && (el.closest('.game-collection-item') || el.closest('[class*="game-table-item"]') || el.closest('[class*="game-item"]'));
            }
            var c = card(document.querySelector('[data-product-id="' + id + '"]')) ||
                    card(document.querySelector('a[href*="/game/' + id + '/"], a[href$="/game/' + id + '"]'));
            if (c) {
              var a = c.querySelector('a[href*="/game/"]');
              r.url = a ? a.href.split('#')[0] : null;
              r.title = txt(c.querySelector('[class*="item-details-title"], [class*="item-title"], [class*="title"]'));
              var p = c.querySelector('[class*="price-discount"]') || c.querySelector('[class*="price"]:not([class*="strike"]):not([class*="old"])');
              r.priceText = txt(p);
              var img = c.querySelector('img[class*="item-image"], img[itemprop="image"], img:not([src*="flag"]):not([class*="flag"])');
              r.image = img ? (img.getAttribute('data-src') || img.getAttribute('src')) : null;
            }
            if (!r.url && location.pathname.indexOf('/game/' + id) >= 0) r.url = location.href.split('#')[0];
            return r;
          }
          function report(url, body) {
            if (!url) return;
            var m = String(url).match(/add-to-(watch|wish)-list/);
            if (!m) return;
            var b = body == null ? '' : (typeof body === 'string' ? body : (body.toString ? body.toString() : ''));
            var id = (b.match(/game_id=(\d+)/) || [])[1];
            if (!id) return;
            try { window.GameDealsApp.onSiteListAdd(JSON.stringify(info(id, m[1] + 'list'))); } catch (e) {}
          }
          var open = XMLHttpRequest.prototype.open, send = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function (method, url) { this.__gdwUrl = url; return open.apply(this, arguments); };
          XMLHttpRequest.prototype.send = function (body) { try { report(this.__gdwUrl, body); } catch (e) {} return send.apply(this, arguments); };
          if (window.fetch) {
            var f = window.fetch;
            window.fetch = function (input, init) {
              try { report(typeof input === 'string' ? input : input && input.url, init && init.body); } catch (e) {}
              return f.apply(this, arguments);
            };
          }
        })();
    """.trimIndent()
}
