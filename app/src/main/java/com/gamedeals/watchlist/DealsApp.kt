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
import com.gamedeals.watchlist.work.RefreshTime

class DealsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        // Version 2 = daily refresh at a set time. Replace the old 12-hourly schedule once.
        val upgrade = Prefs.scheduleVersion(this) < SCHEDULE_VERSION
        PriceCheckScheduler.schedule(this, replace = upgrade)
        if (upgrade) Prefs.setScheduleVersion(this, SCHEDULE_VERSION)
        // Catch up if the phone missed a refresh (switched off, battery saver, …).
        if (RefreshTime.isOverdue(Prefs.lastBackgroundRefresh(this), Prefs.intervalHours(this)) &&
            Prefs.lastBackgroundRefresh(this) > 0L
        ) {
            PriceCheckScheduler.runNow(this)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { AdBlocker.init(this@DealsApp) }
        if (!Prefs.urlsMigrated(this)) {
            scope.launch {
                WatchRepository(AppDatabase.get(this@DealsApp).watchDao()).migrateUrls()
                Prefs.setUrlsMigrated(this@DealsApp)
            }
        }
    }

    private companion object {
        const val SCHEDULE_VERSION = 2
    }
}
