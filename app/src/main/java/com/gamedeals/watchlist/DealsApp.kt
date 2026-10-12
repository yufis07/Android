package com.gamedeals.watchlist

import android.app.Application
import com.gamedeals.watchlist.data.AppDatabase
import com.gamedeals.watchlist.data.Prefs
import com.gamedeals.watchlist.data.WatchRepository
import com.gamedeals.watchlist.web.AdBlocker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.gamedeals.watchlist.work.PriceCheckScheduler

class DealsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        PriceCheckScheduler.schedule(this, Prefs.intervalHours(this), replace = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { AdBlocker.init(this@DealsApp) }
        if (!Prefs.urlsMigrated(this)) {
            scope.launch {
                WatchRepository(AppDatabase.get(this@DealsApp).watchDao()).migrateUrls()
                Prefs.setUrlsMigrated(this@DealsApp)
            }
        }
    }
}
