package com.example.gymtime.util

import java.util.Calendar

/**
 * Utility object for week boundary calculations.
 * Week is defined as Sunday 12:00 AM to Saturday 11:59:59 PM (local time).
 */
object WeekUtils {

    /**
     * Get Sunday 12:00 AM of the current week in milliseconds.
     */
    fun getCurrentWeekStartMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Get Sunday 12:00 AM of last week in milliseconds.
     */
    fun getLastWeekStartMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.WEEK_OF_YEAR, -1)
        return cal.timeInMillis
    }

    /**
     * Get Saturday 11:59:59.999 PM of last week in milliseconds.
     * This is equivalent to getCurrentWeekStartMs() - 1ms.
     */
    fun getLastWeekEndMs(): Long {
        return getCurrentWeekStartMs() - 1
    }

    /**
     * Get the current timestamp in milliseconds.
     */
    fun getCurrentTimeMs(): Long {
        return System.currentTimeMillis()
    }
}
