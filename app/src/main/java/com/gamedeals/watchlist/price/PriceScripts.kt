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
     * Shared in-page card reader (mirrors [SiteHtml.cards]): defines `gdwReadCards()` which
     * returns every game card on the page with its id, link, title, price and list markers.
     */
    private val CARD_READER = """
        function gdwReadCards() {
          var seen = {}, out = [];
          function txt(el) { return el ? el.textContent.replace(/\s+/g, ' ').trim() : null; }
          function priceOf(c) {
            var p = c.querySelector('[class*="price-discount"], [class*="price-current"], [class*="price-final"]');
            if (p) return txt(p);
            var els = c.querySelectorAll('[class*="price"]');
            for (var i = 0; i < els.length; i++) {
              if (els[i].hasAttribute('itemprop')) continue;
              var cls = els[i].getAttribute('class') || '';
              if (/old|original|strike|regular|base|full|history|crossed|lowest/i.test(cls)) continue;
              if (els[i].querySelector('[class*="price"]')) continue;
              var t = txt(els[i]);
              if (t && t.length < 25 && (/\d/.test(t) || /free/i.test(t))) return t;
            }
            return null;
          }
          var roots = document.querySelectorAll('.game-collection-item, .game-table-item');
          var list = roots.length ? Array.prototype.slice.call(roots) :
            Array.prototype.map.call(document.querySelectorAll('a[href*="/game/"]'), function (a) { return a.closest('li') || a; });
          list.forEach(function (c) {
            var a = (c.matches && c.matches('a[href*="/game/"]')) ? c : c.querySelector('a[href*="/game/"]');
            if (!a) return;
            var m = a.href.match(/\/game\/(\d+)/);
            var skuEl = c.querySelector('[itemprop="sku"]');
            var id = (skuEl && txt(skuEl)) || (m && m[1]);
            if (!id || seen[id]) return;
            if (location.pathname.indexOf('/game/' + id) >= 0) return; // the page's own game
            var title = txt(c.querySelector('[itemprop="name"]')) ||
                        txt(c.querySelector('[class*="item-details-title"], [class*="item-title"], [class*="title"]')) ||
                        a.getAttribute('title') || txt(a);
            if (!title) return;
            seen[id] = true;
            var priceEl = c.querySelector('[itemprop="price"]');
            var curEl = c.querySelector('[itemprop="priceCurrency"]');
            var img = c.querySelector('img[class*="item-image"], img[itemprop="image"], img:not([src*="flag"]):not([class*="flag"])');
            out.push({
              id: id,
              url: a.href.split('#')[0].split('?')[0],
              title: title,
              price: priceEl ? (priceEl.getAttribute('content') || txt(priceEl)) : null,
              currency: curEl ? (curEl.getAttribute('content') || txt(curEl)) : null,
              priceText: priceOf(c),
              image: img ? (img.getAttribute('data-src') || img.getAttribute('src')) : null,
              watchlisted: !!c.querySelector('.icon-watchlist.active'),
              wishlisted: !!c.querySelector('.icon-wishlist.active')
            });
          });
          return out;
        }
        function gdwNextPage() {
          var l = document.querySelector('link[rel="next"][href]') ||
                  document.querySelector('.pagination li.next:not(.disabled) a[href], .pagination a[rel="next"][href]');
          return l ? l.href : null;
        }
    """.trimIndent()

    /**
     * Lists every game card on the current page (search results, collections, or your own
     * watchlist / wishlist pages on the site) for "Import games from this page", plus the
     * link to the next page of the list.
     */
    val SCAN_PAGE = "(function () {\n" + CARD_READER + "\nreturn JSON.stringify({ cards: gdwReadCards(), next: gdwNextPage() });\n})();"

    /**
     * Installed on every deal-site page. When you use the site's own "Add to Watchlist" or
     * "Add to Wishlist" buttons, the site posts `/user/add-to-watch-list` (or wish-list) with a
     * `game_id`; we spot that request, look up the game's card and tell the app, so the game
     * is added to the app's watchlist too.
     */
    val SITE_BRIDGE get() = BRIDGE_HEAD + "\n" + CARD_READER + "\n" + BRIDGE_BODY

    private val BRIDGE_HEAD = """
        (function () {
          if (window.__gdwBridge || !window.GameDealsApp) return;
          window.__gdwBridge = true;
    """.trimIndent()

    private val BRIDGE_BODY = """
          function txt(el) { return el ? el.textContent.replace(/\s+/g, ' ').trim() : null; }
          function info(id, list) {
            var r = { gameId: id, list: list, url: null, title: null, priceText: null, price: null,
                      currency: null, image: null, pageUrl: location.href };
            var found = gdwReadCards().filter(function (x) { return x.id === id; })[0];
            if (found) {
              r.url = found.url; r.title = found.title; r.priceText = found.priceText;
              r.price = found.price; r.currency = found.currency; r.image = found.image;
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

          // Live sync: whenever game cards appear or change (lazy loading, "load more", list
          // markers filled in after the page loads), report games that are on your lists.
          var syncOn = false, listPage = false;
          try { syncOn = !!window.GameDealsApp.syncEnabled(); listPage = !!window.GameDealsApp.isListPage(location.href); } catch (e) {}
          if (!syncOn) return;
          var reported = {}, timer = null;
          function syncScan() {
            var cards = gdwReadCards();
            var marked = cards.filter(function (c) { return c.watchlisted || c.wishlisted; });
            var use = marked.length ? marked : (listPage ? cards : []);
            var fresh = use.filter(function (c) { return !reported[c.id]; });
            if (!fresh.length) return;
            fresh.forEach(function (c) { reported[c.id] = true; c.watchlisted = c.watchlisted || (listPage && !c.wishlisted); });
            try { window.GameDealsApp.onCardsFound(JSON.stringify(fresh)); } catch (e) {}
          }
          function schedule() { if (!timer) timer = setTimeout(function () { timer = null; syncScan(); }, 600); }
          schedule();
          new MutationObserver(schedule).observe(document.documentElement,
            { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
        })();
    """.trimIndent()
}
