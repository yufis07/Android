package com.gamedeals.watchlist.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gamedeals.watchlist.data.AppDatabase
import com.gamedeals.watchlist.price.PriceUpdater
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/** Periodically re-checks every watched game's price in the background. */
class PriceCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val items = AppDatabase.get(applicationContext).watchDao().getAll()
        if (items.isEmpty()) return Result.success()
        val fetcher = HttpPriceFetcher(applicationContext)
        items.forEachIndexed { index, item ->
            val (info, error) = fetcher.fetch(item.url)
            PriceUpdater.apply(applicationContext, item.id, info, error, notify = true)
            if (index < items.lastIndex) delay(2_000) // be polite to the sites
        }
        return Result.success()
    }
}

object PriceCheckScheduler {
    private const val PERIODIC = "price-check-periodic"
    private const val ONE_TIME = "price-check-now"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** [hours] <= 0 disables background checks. */
    fun schedule(context: Context, hours: Int, replace: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (hours <= 0) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<PriceCheckWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PriceCheckWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, request)
    }
}
