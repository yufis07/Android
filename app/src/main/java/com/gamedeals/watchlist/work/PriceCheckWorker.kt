package com.gamedeals.watchlist.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gamedeals.watchlist.Notifier
import com.gamedeals.watchlist.data.AppDatabase
import com.gamedeals.watchlist.data.Prefs
import com.gamedeals.watchlist.data.WatchItem
import com.gamedeals.watchlist.data.isAtTarget
import com.gamedeals.watchlist.price.PriceUpdater
import com.gamedeals.watchlist.sync.ListSyncRunner
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * The background refresh (daily by default):
 *  1. pulls your Watchlist / Wishlist from all three sites (every page) and adds new games;
 *     list pages also update the price of every watched game on them;
 *  2. checks the price of every remaining watched game;
 *  3. sends price alerts as it goes, and one summary notification at the end.
 * Plain HTTP is used first; if a site answers with a bot check, a hidden browser is used.
 */
class PriceCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val dao = AppDatabase.get(ctx).watchDao()
        val before = dao.getAll().associateBy { it.id }

        // 1. Pull your site lists.
        var added = 0
        var listsOk = !Prefs.autoSync(ctx)
        if (Prefs.autoSync(ctx)) {
            runCatching { ListSyncRunner.run(ctx, allowWebView = true) }.onSuccess { outcome ->
                added = outcome.added
                listsOk = outcome.anySignedIn || outcome.results.all { it.error == "Not signed in" }
            }
        }

        // 2. Check the games the list pull didn't just refresh.
        val freshCutoff = System.currentTimeMillis() - 30 * 60 * 1000L
        val toCheck = dao.getAll().filter { (it.lastChecked ?: 0L) < freshCutoff || it.lastError != null }
        val fetcher = HttpPriceFetcher(ctx)
        val blocked = mutableListOf<WatchItem>()
        toCheck.forEachIndexed { index, item ->
            val (info, error) = fetcher.fetch(item.url)
            if (info?.price == null && error?.startsWith("HTTP 403") == true) {
                blocked += item // bot check: retry these in a real browser below
            } else {
                PriceUpdater.apply(ctx, item.id, info, error, notify = true)
            }
            if (index < toCheck.lastIndex) delay(1_500) // be polite to the sites
        }
        if (blocked.isNotEmpty()) {
            runCatching {
                WebViewPriceRefresher(ctx).refresh(blocked, onProgress = { _, _ -> }) { item, info ->
                    PriceUpdater.apply(ctx, item.id, info, "Couldn't read the price – open the game to check", notify = true)
                }
            }
        }

        // 3. Summary.
        val after = dao.getAll()
        val summary = Summary.of(before, after, added)
        Prefs.setLastBackgroundRefresh(ctx, summary.text)
        if (summary.hasNews && Prefs.dailySummary(ctx)) Notifier.showRefreshSummary(ctx, summary.title, summary.lines)

        // Nothing at all could be read (e.g. network dropped mid-run): try again later.
        val anythingWorked = listsOk || after.any { (it.lastChecked ?: 0L) >= freshCutoff && it.lastError == null }
        return if (!anythingWorked && after.isNotEmpty() && runAttemptCount < 3) Result.retry() else Result.success()
    }

    /** What changed during one refresh. */
    data class Summary(val title: String, val lines: List<String>, val text: String, val hasNews: Boolean) {
        companion object {
            fun of(before: Map<Long, WatchItem>, after: List<WatchItem>, added: Int): Summary {
                val drops = after.filter { item ->
                    val old = before[item.id]?.currentPrice
                    val new = item.currentPrice
                    old != null && new != null && new < old - 0.001
                }
                val reached = after.filter { it.isAtTarget && before[it.id]?.isAtTarget != true }
                val failed = after.count { it.lastError != null }
                val lines = buildList {
                    reached.take(5).forEach { add("🎯 ${it.title} hit your target") }
                    drops.filter { it !in reached }.take(5).forEach { add("📉 ${it.title} dropped in price") }
                    if (added > 0) add("➕ $added new game${if (added == 1) "" else "s"} from your site lists")
                }
                val parts = buildList {
                    if (reached.isNotEmpty()) add("${reached.size} at target")
                    if (drops.isNotEmpty()) add("${drops.size} price drop${if (drops.size == 1) "" else "s"}")
                    if (added > 0) add("$added new")
                    if (failed > 0) add("$failed couldn't be checked")
                }
                val text = if (parts.isEmpty()) "${after.size} games checked, no changes" else parts.joinToString(" · ")
                val title = when {
                    reached.isNotEmpty() -> "Daily refresh: ${reached.size} game${if (reached.size == 1) "" else "s"} at your target price"
                    drops.isNotEmpty() -> "Daily refresh: ${drops.size} price drop${if (drops.size == 1) "" else "s"}"
                    else -> "Daily refresh: $added new game${if (added == 1) "" else "s"} synced"
                }
                return Summary(title, lines, text, hasNews = reached.isNotEmpty() || drops.isNotEmpty() || added > 0)
            }
        }
    }
}

object PriceCheckScheduler {
    private const val PERIODIC = "price-check-periodic"
    private const val ONE_TIME = "price-check-now"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * Schedules the background refresh every [hours] (0 = off). A daily refresh (24 h) is
     * aligned to [dailyHour]:00 local time.
     */
    fun schedule(context: Context, hours: Int, dailyHour: Int, replace: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (hours <= 0) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val initialDelayMs = if (hours >= 24) RefreshTime.delayUntilNext(dailyHour) else TimeUnit.HOURS.toMillis(hours.toLong())
        val request = PeriodicWorkRequestBuilder<PriceCheckWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun schedule(context: Context, replace: Boolean) =
        schedule(context, Prefs.intervalHours(context), Prefs.dailyRefreshHour(context), replace)

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PriceCheckWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.KEEP, request)
    }
}
