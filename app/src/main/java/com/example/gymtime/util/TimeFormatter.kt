package com.example.gymtime.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object TimeFormatter {
    private val shortDateFormatters = ConcurrentHashMap<Locale, DateTimeFormatter>()

    fun formatShortDate(date: Date, locale: Locale = Locale.getDefault()): String {
        val formatter = shortDateFormatters.computeIfAbsent(locale) { loc ->
            DateTimeFormatter.ofPattern("MMM d", loc)
        }
        return Instant.ofEpochMilli(date.time)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(formatter)
    }

    fun formatSecondsToMMSS(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val mins = safeSeconds / 60
        val secs = safeSeconds % 60
        return if (mins > 0) {
            String.format(Locale.US, "%d:%02d", mins, secs)
        } else {
            String.format(Locale.US, "0:%02d", secs)
        }
    }
}
