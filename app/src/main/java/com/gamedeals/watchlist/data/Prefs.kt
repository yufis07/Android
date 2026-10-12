package com.gamedeals.watchlist.data

import android.content.Context

object Prefs {
    private const val FILE = "settings"
    private const val KEY_INTERVAL = "check_interval_hours"
    const val DEFAULT_INTERVAL_HOURS = 12
    val INTERVAL_OPTIONS = listOf(0, 6, 12, 24)

    fun intervalHours(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_INTERVAL, DEFAULT_INTERVAL_HOURS)

    fun setIntervalHours(context: Context, hours: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_INTERVAL, hours).apply()
    }
}
