package com.example.gymtime.util

import com.example.gymtime.data.db.entity.DistanceUnit
import java.util.Locale

/**
 * Utilities for time and distance formatting/conversion
 */
object TimeUtils {
    const val METERS_PER_MILE = 1609.344f
    private const val METERS_PER_KILOMETER = 1000f
    private const val METERS_PER_YARD = 0.9144f
    private const val METERS_PER_FOOT = 0.3048f

    /**
     * Converts seconds to HH:MM:SS format
     */
    fun formatSecondsToHMS(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val h = safeSeconds / 3600
        val m = (safeSeconds % 3600) / 60
        val s = safeSeconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    /**
     * Parses HH:MM:SS, MM:SS, or SS to seconds.
     * Returns null if parsing fails, segments are empty (e.g. "1::30"), or value overflows Int.
     */
    fun parseHMSToSeconds(hms: String): Int? {
        val trimmed = hms.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.any { !it.isDigit() && it != ':' }) return null

        val parts = trimmed.split(":")
        if (parts.any { it.isEmpty() }) return null

        val totalSeconds: Long = when (parts.size) {
            1 -> parts[0].toLongOrNull() ?: return null
            2 -> {
                val mins = parts[0].toLongOrNull() ?: return null
                val secs = parts[1].toLongOrNull() ?: return null
                if (mins > (Int.MAX_VALUE.toLong() - secs) / 60L) return null
                mins * 60L + secs
            }
            3 -> {
                val hrs = parts[0].toLongOrNull() ?: return null
                val mins = parts[1].toLongOrNull() ?: return null
                val secs = parts[2].toLongOrNull() ?: return null
                if (hrs > Int.MAX_VALUE.toLong() / 3600L) return null
                val hrsSeconds = hrs * 3600L
                if (mins > (Int.MAX_VALUE.toLong() - hrsSeconds) / 60L) return null
                val hmSeconds = hrsSeconds + mins * 60L
                if (secs > Int.MAX_VALUE.toLong() - hmSeconds) return null
                hmSeconds + secs
            }
            else -> return null
        }

        return if (totalSeconds in 0L..Int.MAX_VALUE.toLong()) totalSeconds.toInt() else null
    }

    /**
     * Converts meters to miles (1 mile = 1609.344 meters)
     */
    fun metersToMiles(meters: Float): Float {
        if (!meters.isFinite()) return 0f
        return meters / METERS_PER_MILE
    }

    /**
     * Converts miles to meters
     */
    fun milesToMeters(miles: Float): Float {
        if (!miles.isFinite()) return 0f
        return miles * METERS_PER_MILE
    }

    /**
     * Formats a distance (miles) to 2 decimal places
     */
    fun formatMiles(miles: Float): String {
        val safeMiles = if (miles.isFinite()) miles else 0f
        return String.format(Locale.US, "%.2f", safeMiles)
    }

    fun distanceToMeters(value: Float, unit: DistanceUnit): Float? {
        if (!value.isFinite()) return null
        return when (unit) {
            DistanceUnit.METERS -> value
            DistanceUnit.KILOMETERS -> value * METERS_PER_KILOMETER
            DistanceUnit.YARDS -> value * METERS_PER_YARD
            DistanceUnit.FEET -> value * METERS_PER_FOOT
            DistanceUnit.MILES -> value * METERS_PER_MILE
            DistanceUnit.STEPS, DistanceUnit.FLOORS -> null
        }
    }

    fun metersToDistance(meters: Float, unit: DistanceUnit): Float {
        if (!meters.isFinite()) return 0f
        return when (unit) {
            DistanceUnit.METERS -> meters
            DistanceUnit.KILOMETERS -> meters / METERS_PER_KILOMETER
            DistanceUnit.YARDS -> meters / METERS_PER_YARD
            DistanceUnit.FEET -> meters / METERS_PER_FOOT
            DistanceUnit.MILES -> meters / METERS_PER_MILE
            DistanceUnit.STEPS, DistanceUnit.FLOORS -> meters
        }
    }

    fun formatDistance(value: Float, unit: DistanceUnit): String {
        val safeValue = if (value.isFinite()) value else 0f
        val decimals = when (unit) {
            DistanceUnit.METERS,
            DistanceUnit.YARDS,
            DistanceUnit.FEET,
            DistanceUnit.STEPS,
            DistanceUnit.FLOORS -> 0
            DistanceUnit.KILOMETERS,
            DistanceUnit.MILES -> 2
        }
        return "%.${decimals}f".format(Locale.US, safeValue)
    }
}
