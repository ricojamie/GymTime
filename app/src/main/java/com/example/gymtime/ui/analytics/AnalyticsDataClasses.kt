package com.example.gymtime.ui.analytics

/**
 * Data classes for Analytics screen
 * Created for Analytics v1.0 MVP
 */

// Hero card data classes
data class FrequencyData(
    val sessionsPerWeek: Float,
    val delta: Float,
    val sparklineData: List<Float>
)

data class VolumeData(
    val totalVolume: Float,
    val percentChange: Float,
    val sparklineData: List<Float>
)

data class BestE1RMData(
    val exerciseName: String,
    val estimatedMax: Float,
    val delta: Float,
    val sparklineData: List<Float>
)

// Volume chart data
data class VolumePoint(
    val week: String,
    val volume: Float
)

// Time range enum for selector
enum class TimeRange(val weeks: Int) {
    FOUR_WEEKS(4),
    TWELVE_WEEKS(12),
    SIX_MONTHS(26),
    ONE_YEAR(52),
    ALL_TIME(-1); // Special case: no limit

    fun getDateRange(): Pair<Long, Long> {
        val endDate = System.currentTimeMillis()
        val startDate = if (this == ALL_TIME) {
            0L
        } else {
            endDate - (weeks * 7 * 24 * 60 * 60 * 1000L)
        }
        return startDate to endDate
    }

    fun getPreviousPeriod(): Pair<Long, Long> {
        if (this == ALL_TIME) return 0L to 0L

        val currentEnd = System.currentTimeMillis()
        val currentStart = currentEnd - (weeks * 7 * 24 * 60 * 60 * 1000L)

        val previousEnd = currentStart
        val previousStart = previousEnd - (weeks * 7 * 24 * 60 * 60 * 1000L)

        return previousStart to previousEnd
    }

    fun displayName(): String = when (this) {
        FOUR_WEEKS -> "4W"
        TWELVE_WEEKS -> "12W"
        SIX_MONTHS -> "6M"
        ONE_YEAR -> "1Y"
        ALL_TIME -> "ALL"
    }
}
