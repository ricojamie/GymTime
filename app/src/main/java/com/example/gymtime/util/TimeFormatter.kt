package com.example.gymtime.util

import java.util.Locale

object TimeFormatter {
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
