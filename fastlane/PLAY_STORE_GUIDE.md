# Publishing Game Deals Watch on Google Play

Everything you need for the Play Console, in order. Store texts and graphics are in
`fastlane/metadata/android/en-US/`; the privacy policy is `docs/privacy-policy.html`.

---

## 0. Read first: the one thing that can get the app rejected

Google Play's **Spam policy → "Webviews and Affiliate Spam"** says:

> We don't allow apps whose primary purpose is to … provide a webview of a website **without
> permission from the website owner or administrator**.

The app's site tabs show xbdeals.net, psdeals.net and ntdeals.net, which belong to someone else.
The watchlist, alerts, sync and background refresh are real native features, which helps. But
the content still comes from those sites, and their owner (IRONPAW, `support@psdeals.net`)
can report the app at any time.

**Strongly recommended: get written permission before you submit.** Keep the reply. If Google
asks, you can attach it in the appeal / "App access" notes. Template:

> Subject: Permission request – independent Android app for xbdeals / psdeals / ntdeals users
>
> Hello,
>
> I've built a free Android app, "Game Deals Watch", for people who use your sites. Users sign
> in to their own xbdeals / psdeals / ntdeals accounts inside the app. The app then shows their
> watchlists and wishlists from all three sites in one list and sends price-drop notifications.
> It links back to your pages for every game and clearly says it is not affiliated with you.
>
> I'd like to publish it on Google Play and would like your permission to display your sites in
> the app. I'm happy to adjust anything you'd like: branding, how often it checks prices, keeping
> your ads, linking to your official apps, and so on.
>
> Thank you,
> <your name> – <your email>

The Play build already **keeps the sites' ads on by default**. Users can still turn ad blocking
on in Settings. If the owner asks, ad blocking can be removed from the Play build entirely.

---

## 1. One-time setup (before the first upload)

1. **Signing (upload key).** Play only accepts bundles signed with a release key. Add the two
   GitHub secrets you already have (`SIGNING_KEYSTORE_BASE64`, `SIGNING_PASSWORD`) at
   *GitHub → Settings → Secrets and variables → Actions*. Without them, CI signs with a
   throw-away debug key and **Play will reject the .aab**.
   That keystore becomes your **upload key**. Google re-signs the app with its own key
   (Play App Signing, on by default). Keep a backup of the keystore and password.
2. **Host the privacy policy.** In GitHub, go to *Settings → Pages → Build and deployment →
   Deploy from a branch*, choose your branch and the `/docs` folder, then save. After a minute
   the policy is live at **https://yufis07.github.io/Android/privacy-policy.html**. This URL
   is already linked inside the app (Settings → About).
3. **Support email.** Replace `YOUR_SUPPORT_EMAIL` in `docs/privacy-policy.html` and
   `docs/index.html` with the address you want to show publicly. Play also shows it on the
   listing.
4. **Package name** is `com.gamedeals.watchlist`. It can never change after the first upload.
   If you'd prefer your own (for example `com.yourname.gamedeals`), tell me before uploading.
5. **Developer account:** USD 25 one-time fee plus identity verification at
   https://play.google.com/console/signup.

---

## 2. Build what you upload

Every push builds automatically (GitHub → Actions → **Build APK**):

| Artifact | What it is | Use |
|---|---|---|
| `GameDealsWatch-play-bundle` → `app-play-release.aab` | Google Play build | **Upload to Play Console** |
| `GameDealsWatch-play-bundle` → `mapping.txt` | R8 deobfuscation map | Upload in *App bundle explorer → Downloads* (readable crash reports) |
| `GameDealsWatch-apk` → `app-sideload-release.apk` | Sideload build (ad blocking on) | Personal install only |

The version code increases with every CI run, so each new upload is accepted.

---

## 3. Create the app and fill in the store listing

*Play Console → Create app*: name **Game Deals Watch**, App, Free, then accept the declarations.

**Main store listing** (*Grow users → Store presence → Main store listing*):

| Field | Value / file |
|---|---|
| App name (30) | `title.txt` → **Game Deals Watch: Price Alerts** |
| Short description (80) | `short_description.txt` |
| Full description (4000) | `full_description.txt` (the `<b>` tags show as bold on Play) |
| App icon 512×512 | `images/icon.png` |
| Feature graphic 1024×500 | `images/featureGraphic.png` |
| Phone screenshots | `images/phoneScreenshots/1…6_en-US.png` (1080×1920) |
| Category | **Shopping** (alternative: Tools) |
| Tags | Shopping, Price comparison, Deals |
| Email | your support email |
| Website | https://yufis07.github.io/Android/ |
| Privacy policy | https://yufis07.github.io/Android/privacy-policy.html |

> The screenshots are faithful renders of the app's real screens using **fictional game titles**,
> so no game publisher's artwork is shown. If you'd rather use real captures from your phone,
> avoid showing real game cover art and console logos.

---

## 4. App content forms (*Policy → App content*)

**Privacy policy:** the URL above.

**Ads:** *No, my app does not contain ads.* The app itself shows no ads. Ads on the websites
belong to those websites.

**App access:** choose *All or some functionality is restricted* and add these instructions:
> No account is needed to try the app. Open the PlayStation tab, open any game, tap "Watch" and
> set a target price. The game then appears on the Watchlist tab. "Sync now" imports the user's
> own lists and needs a free account on psdeals.net / xbdeals.net / ntdeals.net (email sign-up on
> those sites).

Optionally create a free test account on psdeals.net and give the reviewer its email/password.

**Content rating** (IARC questionnaire). Pick category *"All other app types"*, then answer:
- Violence, fear, sexuality, language, controlled substances, gambling: **No**
- Users can interact or exchange content with each other: **No**
- Shares the user's location with others: **No**
- Allows purchases of digital goods: **No**
- Lets users browse the web / access unrestricted internet content: **No**. Only the three deal
  sites are shown in tabs. Links to other sites open in the same tab, so if you'd rather be
  cautious, answer **Yes**; that usually gives a Teen/12+ rating.
- Expected result: Everyone / PEGI 3 (or Teen/PEGI 12 if you answered Yes to web access).

**Target audience:** **18 and over** (or 13+). Do not include under-13 age groups; that triggers
the Families policy. "Could the app unintentionally appeal to children?" → **No**.

**Data safety** (what the code actually does):
- Does your app collect or share any of the required user data types? → **No.**
  - The watchlist, settings and site cookies are stored **only on the device**. Nothing is sent to
    you, and there's no analytics, crash reporting or ad SDK.
  - Sign-in details are typed directly into the deal websites. They're the user's own browsing of
    those sites, not data the app collects.
- If the form still asks: data is encrypted in transit (**Yes**, HTTPS only). Users can delete
  data by uninstalling, or with Settings → *Sign out of all sites*.

**Advertising ID:** *No*. The app doesn't use it and doesn't declare the `AD_ID` permission.

**Government app / Financial features / Health / News app:** *No / None / No / No.*

---

## 5. Testing, then release

New personal developer accounts (created after 13 Nov 2023) **must run a closed test with at least
12 testers, opted in continuously for 14 days**, before you can apply for production.

1. *Test and release → Testing → Closed testing → Create track*. Upload the `.aab`, add release
   notes from `changelogs/default.txt`, and add a tester list (Google Groups or emails) of
   **12+ people**.
2. Send testers the opt-in link. They must stay opted in for **14 days in a row**. Ask them to
   actually use the app and send feedback, because Google asks about this.
3. Check the **Pre-launch report** (automatic device tests) and fix any crashes it finds.
4. After 14 days: *Dashboard → Apply for production* and answer the questions about your test.
5. Promote the release to **Production** (a staged rollout of 20% → 100% is a good idea).

Review usually takes from a few days to a week for a new app.

---

## 6. What was changed in the app for Play compliance

- **Targets Android 16 (API 36)**, which Play requires for all new apps and updates.
- **Edge-to-edge layout** (mandatory on Android 15+), with screens padded for system bars and
  the keyboard.
- **App Bundle (.aab)** build for Play, with R8 shrinking and the mapping file uploaded.
- **Version code** increases with every build.
- **Secure JavaScript bridge**: only xbdeals/psdeals/ntdeals pages can talk to the app
  (`addWebMessageListener` with an origin allow-list). The bridge is not exposed to other pages.
- **No user-agent spoofing**: it was removed because it goes against Google's sign-in policy.
  Google sign-in inside the tabs now shows a clear message asking users to use email login.
- **Privacy policy linked inside the app** (Settings → About), with a not-affiliated disclaimer.
- **Ad blocking off by default** in the Play build.
- No sensitive permissions: only Internet, network state and notifications.
  No `QUERY_ALL_PACKAGES`, no foreground service, no location or files access.

## 7. Remaining risks (not fixable in code)

| Risk | What to do |
|---|---|
| Webview policy, site owner not consenting | Get written permission (section 0) before submitting |
| Trademarks (Xbox, PlayStation, Nintendo, site names) | Used only to describe compatibility, with a disclaimer. Not in the icon or title. Don't add console logos to the graphics |
| Site owner complaint about automated syncing | Background checks are once a day by default with polite delays. Mention this in your permission email |
| Sites change their layout | Price/list parsing could break. The Pre-launch report won't catch it, so keep an eye on reviews |
