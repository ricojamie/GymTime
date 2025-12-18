package com.example.gymtime.util

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Utilities for time and distance formatting/conversion
 */
object TimeUtils {

    /**
     * Converts seconds to HH:MM:SS format
     */
    fun formatSecondsToHMS(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    /**
     * Parses HH:MM:SS or MM:SS to seconds
     * Returns null if parsing fails
     */
    fun parseHMSToSeconds(hms: String): Int? {
        if (hms.isBlank()) return null
        
        // Clean input: remove non-numeric except colon
        val clean = hms.filter { it.isDigit() || it == ':' }
        val parts = clean.split(":").filter { it.isNotBlank() }
        
        return try {
            when (parts.size) {
                1 -> parts[0].toInt() // Just seconds
                2 -> (parts[0].toInt() * 60) + parts[1].toInt() // MM:SS
                3 -> (parts[0].toInt() * 3600) + (parts[1].toInt() * 60) + parts[2].toInt() // HH:MM:SS
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts meters to miles (1 mile = 1609.34 meters)
     */
    fun metersToMiles(meters: Float): Float {
        return meters / 1609.344f
    }

    /**
     * Converts miles to meters
     */
    fun milesToMeters(miles: Float): Float {
        return miles * 1609.344f
    }

    /**
     * Formats a distance (miles) to 2 decimal places
     */
    fun formatMiles(miles: Float): String {
        return String.format(Locale.US, "%.2f", miles)
    }
}
