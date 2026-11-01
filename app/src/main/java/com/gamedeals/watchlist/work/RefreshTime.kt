package com.gamedeals.watchlist.work

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Time-of-day maths for the daily background refresh. */
object RefreshTime {
    /** Times offered for the daily refresh (hour of day, local time). */
    val HOUR_OPTIONS = listOf(6, 8, 12, 18, 21)

    /** Milliseconds from [now] until the next [hour]:00 local time (always > 0, at most 24 h). */
    fun delayUntilNext(hour: Int, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val current = ZonedDateTime.ofInstant(now, zone)
        var next = current.with(LocalTime.of(hour.coerceIn(0, 23), 0))
        if (!next.isAfter(current)) next = next.plusDays(1)
        return Duration.between(current, next).toMillis()
    }

    /** "8 AM", "12 PM", "9 PM" */
    fun label(hour: Int): String = when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }

    /** True if the last refresh is overdue (missed by more than a few hours, e.g. phone was off). */
    fun isOverdue(lastRefresh: Long, intervalHours: Int, now: Long = System.currentTimeMillis()): Boolean =
        intervalHours > 0 && now - lastRefresh > (intervalHours + 3) * 60 * 60 * 1000L
}
