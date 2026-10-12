# Game Deals Watch (Android)

An Android app for **xbdeals.net**, **psdeals.net** and **ntdeals.net** in one place. You can sign in to each site, browse it like you would in a browser, and keep one **price watchlist** that covers all three stores. The app notifies you when a game reaches your target price.

## Features

| | |
|---|---|
| **One tab per site** | Xbox (xbdeals), PlayStation (psdeals) and Nintendo (ntdeals) each run in their own built-in browser tab. Each tab keeps its own page and history when you switch away. |
| **Login** | Sign in on each site's own login page. Cookies are saved, so you stay signed in after you close the app. Your account features on each site (its own wishlist, region and so on) work the same as on the web. |
| **Watch button** | Open a game's page and tap **Watch**. The app reads the game's title and current price, and you can set a target price if you want one. |
| **Unified watchlist** | Every watched game from all three stores in one list. Each entry shows the current price, your target and the lowest price seen. You can filter by store and sort by recent, closest to target or price. Tap an entry to open it. You can edit or delete entries, and undo a delete. |
| **Price alerts** | Background checks run every 6, 12 or 24 hours (or never). You get a notification when a game reaches your target and when its price drops. Tapping the notification opens the game. |
| **Check now** | The ↻ button re-checks every game with an off-screen browser, which gets past bot checks the same way your normal browsing does. Visiting a watched game's page also updates its saved price automatically. |
| **Deep links** | Links to xbdeals, psdeals or ntdeals can be opened with the app. |
| **Sign out** | Settings → *Sign out of all sites* clears cookies and site data. Your watchlist is kept. |

## Install

### Option A: download the APK from GitHub Actions
1. Open the repo's **Actions** tab, then the latest **Build APK** run.
2. Download the **GameDealsWatch-debug-apk** artifact and unzip it.
3. Copy `app-debug.apk` to your phone and open it. You may need to allow *Install unknown apps*.

### Option B: build it yourself
Requires Android Studio (Ladybug or newer) or JDK 17 with the Android SDK.

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug    # with a device connected
```

## How it works

- **Kotlin + Jetpack Compose (Material 3)**, min Android 8.0 (API 26).
- `BrowserState`: one persistent `WebView` per site. Cookies are handled by `CookieManager`, and the `; wv` marker is removed from the user agent so social sign-in pages are less likely to refuse the embedded browser.
- `PriceParser` / `PriceScripts`: read the price from schema.org JSON-LD first, then from microdata or OpenGraph tags, then from the first non-struck-through element whose class contains "price". The same logic runs as JavaScript inside the WebView and as Jsoup in the background worker.
- `Room` stores the watchlist and `WorkManager` (`PriceCheckWorker`) runs the background checks. Background checks use OkHttp and send your WebView login cookies.

## Known limitations

- **Google / Apple sign-in**: Google sometimes blocks sign-in inside embedded browsers. If that happens, use the site's email and password login instead.
- **Bot protection**: if a site serves a bot check page, background (HTTP-only) checks show *HTTP 403*. Tap ↻ in the app, or open the game, to refresh that game through the real browser.
- The sites don't publish an official API, so price detection is heuristic. If the sites change their markup, edit the detected price, or update the selectors in `PriceParser.kt` and `PriceScripts.kt`.
- This app is not affiliated with xbdeals.net, psdeals.net, ntdeals.net, Microsoft, Sony or Nintendo.
