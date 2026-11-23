package com.example.gymtime.util

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    @Test
    fun historicalAsOfIgnoresLaterWorkouts() {
        val asOf = date(2026, Calendar.JUNE, 10)
        val result = StreakCalculator.calculateStreak(
            workoutDates = listOf(
                date(2026, Calendar.JUNE, 10),
                date(2026, Calendar.JUNE, 11)
            ),
            allowedSkipsPerWeek = 2,
            asOf = asOf
        )

        assertEquals(StreakCalculator.StreakState.ACTIVE, result.state)
        assertEquals(1, result.streakDays)
    }

    @Test
    fun historicalAsOfCalculatesNextSundayFromReferenceDate() {
        val asOf = date(2026, Calendar.JUNE, 10)
        val result = StreakCalculator.calculateStreak(
            workoutDates = listOf(asOf),
            asOf = asOf
        )
        val reset = Calendar.getInstance().apply { time = result.nextResetDate }

        assertEquals(Calendar.SUNDAY, reset.get(Calendar.DAY_OF_WEEK))
        assertTrue(result.nextResetDate.after(asOf))
    }

    @Test(timeout = 2_000L)
    fun mondayFirstUkLocaleDoesNotHangAndComputesMultiWeekStreak() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        try {
            // Wednesday 2026-06-10 with multi-week workout history (Sun-Sat weeks with <=2 misses/week)
            val asOf = date(2026, Calendar.JUNE, 10)
            val workouts = listOf(
                // Previous week (Sun May 31 - Sat Jun 6): 5 workouts (2 skips)
                date(2026, Calendar.MAY, 31),
                date(2026, Calendar.JUNE, 1),
                date(2026, Calendar.JUNE, 2),
                date(2026, Calendar.JUNE, 4),
                date(2026, Calendar.JUNE, 5),
                // Current week (Sun Jun 7 - Wed Jun 10): 3 workouts (1 skip)
                date(2026, Calendar.JUNE, 7),
                date(2026, Calendar.JUNE, 8),
                date(2026, Calendar.JUNE, 10)
            )
            val result = StreakCalculator.calculateStreak(
                workoutDates = workouts,
                allowedSkipsPerWeek = 2,
                asOf = asOf
            )

            assertEquals(StreakCalculator.StreakState.ACTIVE, result.state)
            assertEquals(8, result.streakDays)
            assertEquals(1, result.skipsRemaining)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun streakAcrossDstSpringForwardAndFallBackWeeksRemainsAccurate() {
        val ny = java.time.ZoneId.of("America/New_York")

        // Spring-forward week containing Sunday 2026-03-08 (23h day)
        val springAsOf = Date.from(java.time.LocalDate.of(2026, 3, 10).atTime(12, 0).atZone(ny).toInstant())
        val springWorkouts = listOf(
            // Prior week (Mar 1 - Mar 7): 5 workouts (2 skips)
            Date.from(java.time.LocalDate.of(2026, 3, 1).atTime(10, 0).atZone(ny).toInstant()),
            Date.from(java.time.LocalDate.of(2026, 3, 2).atTime(10, 0).atZone(ny).toInstant()),
            Date.from(java.time.LocalDate.of(2026, 3, 4).atTime(10, 0).atZone(ny).toInstant()),
            Date.from(java.time.LocalDate.of(2026, 3, 5).atTime(10, 0).atZone(ny).toInstant()),
            Date.from(java.time.LocalDate.of(2026, 3, 7).atTime(10, 0).atZone(ny).toInstant()),
            // Spring-forward week (Mar 8 - Mar 10): 3 workouts (0 skips)
            Date.from(java.time.LocalDate.of(2026, 3, 8).atTime(10, 0).atZone(ny).toInstant()),
            Date.from(java.time.LocalDate.of(2026, 3, 9).atTime(10, 0).atZone(ny).toInstant()),
            Date.from(java.time.LocalDate.of(2026, 3, 10).atTime(10, 0).atZone(ny).toInstant())
        )
        val springResult = StreakCalculator.calculateStreakForZone(springWorkouts, allowedSkipsPerWeek = 2, asOf = springAsOf, zoneId = ny)
        assertEquals(StreakCalculator.StreakState.ACTIVE, springResult.state)
        assertEquals(8, springResult.streakDays)
        assertEquals(2, springResult.skipsRemaining)
        assertEquals(
            java.time.LocalDate.of(2026, 3, 15),
            springResult.nextResetDate.toInstant().atZone(ny).toLocalDate()
        )

        // Fall-back week containing Sunday 2026-11-01 (25h day)
        val fallAsOf = Date.from(java.time.LocalDate.of(2026, 11, 1).atTime(12, 0).atZone(ny).toInstant())
        val fallWorkouts = listOf(
            Date.from(java.time.LocalDate.of(2026, 10, 31).atTime(10, 0).atZone(ny).toInstant()),
            Date.from(java.time.LocalDate.of(2026, 11, 1).atTime(10, 0).atZone(ny).toInstant())
        )
        val fallResult = StreakCalculator.calculateStreakForZone(fallWorkouts, allowedSkipsPerWeek = 2, asOf = fallAsOf, zoneId = ny)
        assertEquals(StreakCalculator.StreakState.ACTIVE, fallResult.state)
        assertEquals(2, fallResult.streakDays)
        assertEquals(
            java.time.LocalDate.of(2026, 11, 8),
            fallResult.nextResetDate.toInstant().atZone(ny).toLocalDate()
        )
    }

    private fun date(year: Int, month: Int, day: Int): Date = Calendar.getInstance().apply {
        set(year, month, day, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
}
