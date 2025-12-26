package com.example.gymtime.util

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * Tests for WeekUtils - verifies week boundary calculations.
 */
class WeekUtilsTest {

    @Test
    fun `getCurrentWeekStartMs returns a Sunday at midnight`() {
        val weekStartMs = WeekUtils.getCurrentWeekStartMs()
        val cal = Calendar.getInstance()
        cal.timeInMillis = weekStartMs

        assertEquals(Calendar.SUNDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `getLastWeekStartMs is exactly 7 days before current week start`() {
        val currentWeekStart = WeekUtils.getCurrentWeekStartMs()
        val lastWeekStart = WeekUtils.getLastWeekStartMs()

        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        assertEquals(sevenDaysMs, currentWeekStart - lastWeekStart)
    }

    @Test
    fun `getLastWeekStartMs returns a Sunday at midnight`() {
        val lastWeekStartMs = WeekUtils.getLastWeekStartMs()
        val cal = Calendar.getInstance()
        cal.timeInMillis = lastWeekStartMs

        assertEquals(Calendar.SUNDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
    }

    @Test
    fun `getLastWeekEndMs is one millisecond before current week start`() {
        val currentWeekStart = WeekUtils.getCurrentWeekStartMs()
        val lastWeekEnd = WeekUtils.getLastWeekEndMs()

        assertEquals(currentWeekStart - 1, lastWeekEnd)
    }

    @Test
    fun `getLastWeekEndMs falls on Saturday`() {
        val lastWeekEndMs = WeekUtils.getLastWeekEndMs()
        val cal = Calendar.getInstance()
        cal.timeInMillis = lastWeekEndMs

        assertEquals(Calendar.SATURDAY, cal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `getCurrentTimeMs returns approximately current time`() {
        val before = System.currentTimeMillis()
        val result = WeekUtils.getCurrentTimeMs()
        val after = System.currentTimeMillis()

        assertTrue(result >= before)
        assertTrue(result <= after)
    }

    @Test
    fun `week boundaries are consistent`() {
        // Last week end should be immediately before current week start
        val lastWeekEnd = WeekUtils.getLastWeekEndMs()
        val currentWeekStart = WeekUtils.getCurrentWeekStartMs()
        val lastWeekStart = WeekUtils.getLastWeekStartMs()

        assertTrue(lastWeekStart < lastWeekEnd)
        assertTrue(lastWeekEnd < currentWeekStart)
        assertEquals(1, currentWeekStart - lastWeekEnd)
    }
}
