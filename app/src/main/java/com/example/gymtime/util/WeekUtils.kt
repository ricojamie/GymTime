package com.example.gymtime.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Utility object for week boundary calculations.
 * Week is defined as Sunday 12:00 AM to Saturday 11:59:59 PM (local time).
 */
object WeekUtils {

    /**
     * Get Sunday 12:00 AM of the current week in milliseconds.
     */
    fun getCurrentWeekStartMs(): Long = getCurrentWeekStartMsAt(System.currentTimeMillis())

    internal fun getCurrentWeekStartMsAt(
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val sunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return sunday.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Get Sunday 12:00 AM of last week in milliseconds.
     */
    fun getLastWeekStartMs(): Long = getLastWeekStartMsAt(System.currentTimeMillis())

    internal fun getLastWeekStartMsAt(
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return currentWeekStart.minusWeeks(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Get Saturday 11:59:59.999 PM of last week in milliseconds.
     * This is equivalent to getCurrentWeekStartMs() - 1ms.
     */
    fun getLastWeekEndMs(): Long = getCurrentWeekStartMs() - 1

    internal fun getLastWeekEndMsAt(
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long = getCurrentWeekStartMsAt(nowMs, zoneId) - 1

    /**
     * Get the current timestamp in milliseconds.
     */
    fun getCurrentTimeMs(): Long {
        return System.currentTimeMillis()
    }
}
