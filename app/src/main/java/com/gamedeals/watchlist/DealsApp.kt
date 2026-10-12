package com.gamedeals.watchlist

import android.app.Application
import com.gamedeals.watchlist.data.Prefs
import com.gamedeals.watchlist.work.PriceCheckScheduler

class DealsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        PriceCheckScheduler.schedule(this, Prefs.intervalHours(this), replace = false)
    }
}
