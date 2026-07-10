package com.example.gymtime.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormatter {
    fun formatShortDate(date: Date, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("MMM d", locale).format(date)

    fun formatSecondsToMMSS(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) {
            String.format(Locale.US, "%d:%02d", mins, secs)
        } else {
            String.format(Locale.US, "0:%02d", secs)
        }
    }
}
