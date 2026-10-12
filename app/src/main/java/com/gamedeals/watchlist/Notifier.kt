package com.gamedeals.watchlist

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gamedeals.watchlist.data.WatchItem
import com.gamedeals.watchlist.price.PriceFormat

object Notifier {
    const val CHANNEL_ID = "price_alerts"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_price_alerts),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Alerts when a watched game hits your target price or drops in price" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Decides whether [new] warrants an alert compared with [old]; returns [new] with updated alert bookkeeping. */
    fun process(context: Context, old: WatchItem, new: WatchItem): WatchItem {
        val price = new.currentPrice ?: return new
        val now = new.priceText ?: PriceFormat.format(price, new.currency)
        val target = new.targetPrice
        if (target != null && price <= target) {
            val lastAlert = old.notifiedPrice
            if (lastAlert == null || price < lastAlert) {
                show(context, new, "🎯 Target price reached", "${new.title} is now $now (target ${PriceFormat.format(target, new.currency)})")
                return new.copy(notifiedPrice = price)
            }
            return new
        }
        val prev = old.currentPrice
        if (prev != null && price < prev - 0.001) {
            show(context, new, "📉 Price drop", "${new.title}: ${PriceFormat.format(prev, new.currency)} → $now")
        }
        return new.copy(notifiedPrice = null)
    }

    private fun show(context: Context, item: WatchItem, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_URL, item.url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            item.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(item.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}
