package com.example.gymtime.util

import com.example.gymtime.data.db.entity.DistanceUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Utilities for time and distance formatting/conversion
 */
object TimeUtils {
    private const val METERS_PER_MILE = 1609.344f
    private const val METERS_PER_KILOMETER = 1000f
    private const val METERS_PER_YARD = 0.9144f
    private const val METERS_PER_FOOT = 0.3048f

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
        return meters / METERS_PER_MILE
    }

    /**
     * Converts miles to meters
     */
    fun milesToMeters(miles: Float): Float {
        return miles * METERS_PER_MILE
    }

    /**
     * Formats a distance (miles) to 2 decimal places
     */
    fun formatMiles(miles: Float): String {
        return String.format(Locale.US, "%.2f", miles)
    }

    fun distanceToMeters(value: Float, unit: DistanceUnit): Float? {
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
        val decimals = when (unit) {
            DistanceUnit.METERS,
            DistanceUnit.YARDS,
            DistanceUnit.FEET,
            DistanceUnit.STEPS,
            DistanceUnit.FLOORS -> 0
            DistanceUnit.KILOMETERS,
            DistanceUnit.MILES -> 2
        }
        return "%.${decimals}f".format(Locale.US, value)
    }
}
