package com.example.gymtime.util

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date

class StreakCalculatorTest {

    private fun normalizeDate(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun today(): Date = normalizeDate(Date())

    private fun daysAgo(n: Int): Date {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return normalizeDate(cal.time)
    }

    @Test
    fun emptyListReturnsRestingState() {
        val result = StreakCalculator.calculateStreak(emptyList())
        assertEquals(StreakCalculator.StreakState.RESTING, result.state)
        assertEquals(0, result.streakDays)
        assertEquals(2, result.skipsRemaining)
    }

    @Test
    fun skipsRemainingIsBetween0And2() {
        val workouts = listOf(today())
        val result = StreakCalculator.calculateStreak(workouts)
        assertTrue(result.skipsRemaining in 0..2)
    }

    @Test
    fun nextResetDateIsSunday() {
        val result = StreakCalculator.calculateStreak(listOf(today()))
        val cal = Calendar.getInstance()
        cal.time = result.nextResetDate
        assertEquals(Calendar.SUNDAY, cal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun oldWorkoutsDoNotCountForCurrentStreak() {
        val workouts = listOf(daysAgo(60))
        val result = StreakCalculator.calculateStreak(workouts)
        assertTrue(result.streakDays == 0 || result.state == StreakCalculator.StreakState.BROKEN)
    }

    @Test
    fun streakDaysIsNonNegative() {
        val result = StreakCalculator.calculateStreak(emptyList())
        assertTrue(result.streakDays >= 0)
    }

    @Test
    fun singleWorkoutReturnsNonNegativeStreak() {
        val workouts = listOf(today())
        val result = StreakCalculator.calculateStreak(workouts)
        assertTrue(result.streakDays >= 0)
    }

    // brokeToday tests

    @Test
    fun brokeTodayIsFalseForEmptyList() {
        val result = StreakCalculator.calculateStreak(emptyList())
        assertFalse(result.brokeToday)
    }

    @Test
    fun brokeTodayIsFalseWhenActiveStreak() {
        val workouts = listOf(today())
        val result = StreakCalculator.calculateStreak(workouts)
        assertFalse(result.brokeToday)
    }

    @Test
    fun brokeTodayIsFalseWhenRestingWithValidStreak() {
        // Workout yesterday, resting today - streak should still be valid
        val workouts = listOf(daysAgo(1))
        val result = StreakCalculator.calculateStreak(workouts)
        // brokeToday should be false since we're in RESTING state (not BROKEN)
        assertFalse(result.brokeToday)
    }

    @Test
    fun brokeTodayFieldExists() {
        // Ensure the field exists and has a boolean value
        val result = StreakCalculator.calculateStreak(emptyList())
        assertNotNull(result.brokeToday)
    }
}
