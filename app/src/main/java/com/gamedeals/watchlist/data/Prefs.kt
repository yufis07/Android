package com.gamedeals.watchlist.data

import android.content.Context

object Prefs {
    private const val FILE = "settings"
    private const val KEY_INTERVAL = "check_interval_hours"
    private const val KEY_BLOCK_ADS = "block_ads"
    private const val KEY_AUTO_SYNC = "auto_sync_site_lists"
    private const val KEY_LITE_MODE = "lite_mode"
    private const val KEY_URLS_MIGRATED = "urls_migrated_v2"
    private const val KEY_LAST_LIST_SYNC = "last_list_sync"
    private const val KEY_LIST_SYNC_SUMMARY = "list_sync_summary"
    const val DEFAULT_INTERVAL_HOURS = 12
    val INTERVAL_OPTIONS = listOf(0, 6, 12, 24)

    fun intervalHours(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_INTERVAL, DEFAULT_INTERVAL_HOURS)

    fun setIntervalHours(context: Context, hours: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_INTERVAL, hours).apply()
    }

    fun blockAds(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_BLOCK_ADS, true)

    fun setBlockAds(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BLOCK_ADS, enabled).apply()
    }

    fun autoSync(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_SYNC, true)

    fun setAutoSync(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }

    /** Lite mode: skip web fonts, videos and audio in the site tabs. */
    fun liteMode(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_LITE_MODE, true)

    fun setLiteMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LITE_MODE, enabled).apply()
    }

    fun urlsMigrated(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_URLS_MIGRATED, false)

    fun setUrlsMigrated(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_URLS_MIGRATED, true).apply()
    }

    /** Your Watchlist / Wishlist page URLs on [store], found by the first full sync. */
    fun listUrls(context: Context, store: Store): List<String> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getStringSet("list_urls_${store.name}", emptySet()).orEmpty().sorted()

    fun setListUrls(context: Context, store: Store, urls: List<String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putStringSet("list_urls_${store.name}", urls.toSet()).apply()
    }

    fun allListUrls(context: Context): List<String> = Store.entries.flatMap { listUrls(context, it) }

    fun lastListSync(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_LAST_LIST_SYNC, 0L)

    fun listSyncSummary(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LIST_SYNC_SUMMARY, null)

    fun setListSyncResult(context: Context, summary: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_LIST_SYNC, System.currentTimeMillis())
            .putString(KEY_LIST_SYNC_SUMMARY, summary)
            .apply()
    }
}
