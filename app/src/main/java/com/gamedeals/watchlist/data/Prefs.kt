package com.gamedeals.watchlist.data

import android.content.Context

object Prefs {
    private const val FILE = "settings"
    private const val KEY_INTERVAL = "check_interval_hours"
    private const val KEY_BLOCK_ADS = "block_ads"
    private const val KEY_AUTO_SYNC = "auto_sync_site_lists"
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
}
