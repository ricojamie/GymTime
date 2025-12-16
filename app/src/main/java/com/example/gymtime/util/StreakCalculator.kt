package com.example.gymtime.util

import java.util.Calendar
import java.util.Date

/**
 * Calculates the user's "Iron Streak" - a sustainable consistency tracker.
 *
 * Rule: A streak is maintained as long as the user doesn't miss more than 2 days
 * in any rolling 7-day window. This encourages sustainable habits without the
 * toxic pressure of daily streaks.
 */
object StreakCalculator {

    /**
     * The state of the user's streak
     */
    enum class StreakState {
        ACTIVE,   // User worked out today - streak is growing
        RESTING,  // User hasn't worked out today but is within safe limits
        BROKEN    // User has exceeded the miss limit - streak is reset
    }

    /**
     * Result of streak calculation
     */
    data class StreakResult(
        val state: StreakState,
        val streakDays: Int,
        val restDaysRemaining: Int  // How many more days they can miss this week
    )

    /**
     * Calculate the current streak state and count.
     *
     * @param workoutDates List of dates when workouts with at least 1 working set occurred.
     *                     Should be sorted descending (most recent first).
     * @return StreakResult containing state, streak count, and remaining rest days
     */
    fun calculateStreak(workoutDates: List<Date>): StreakResult {
        if (workoutDates.isEmpty()) {
            // First workout starts a streak with 2 banked rest days
            return StreakResult(
                state = StreakState.RESTING,
                streakDays = 0,
                restDaysRemaining = 2
            )
        }

        val today = normalizeToMidnight(Date())
        val workoutDaysNormalized = workoutDates.map { normalizeToMidnight(it) }.toSet()

        // Check if worked out today
        val workedOutToday = workoutDaysNormalized.contains(today)

        // Calculate missed days in the last 7 days (including today)
        val missedDaysInWindow = countMissedDaysInWindow(today, workoutDaysNormalized)

        // Determine state based on today's workout and missed days
        val state = when {
            missedDaysInWindow > 2 -> StreakState.BROKEN
            workedOutToday -> StreakState.ACTIVE
            else -> StreakState.RESTING
        }

        // Calculate streak length
        val streakDays = if (state == StreakState.BROKEN) {
            0
        } else {
            calculateStreakLength(today, workoutDaysNormalized)
        }

        // Rest days remaining (max 2 - missed in current window, but not below 0)
        val restDaysRemaining = if (state == StreakState.BROKEN) {
            0
        } else {
            (2 - missedDaysInWindow).coerceAtLeast(0)
        }

        return StreakResult(
            state = state,
            streakDays = streakDays,
            restDaysRemaining = restDaysRemaining
        )
    }

    /**
     * Count how many days in the last 7 days (including today) had no workout.
     */
    private fun countMissedDaysInWindow(today: Date, workoutDays: Set<Date>): Int {
        var missedCount = 0
        val calendar = Calendar.getInstance()

        for (i in 0..6) {
            calendar.time = today
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val day = normalizeToMidnight(calendar.time)

            if (!workoutDays.contains(day)) {
                missedCount++
            }
        }

        return missedCount
    }

    /**
     * Calculate streak length by walking backwards through time.
     * Streak continues as long as each 7-day window has no more than 2 misses.
     */
    private fun calculateStreakLength(today: Date, workoutDays: Set<Date>): Int {
        if (workoutDays.isEmpty()) return 0

        // Find the earliest workout date to avoid infinite loops
        val earliestWorkout = workoutDays.minOrNull() ?: return 0

        var streakDays = 0
        val calendar = Calendar.getInstance()
        var currentDay = today

        // Walk backwards day by day
        while (true) {
            // Check the 7-day window ending on currentDay
            val missedInWindow = countMissedDaysInWindowFrom(currentDay, workoutDays)

            if (missedInWindow > 2) {
                // This is where the streak broke
                break
            }

            // Count this day toward the streak if it's a workout day
            if (workoutDays.contains(currentDay)) {
                streakDays++
            }

            // Move to previous day
            calendar.time = currentDay
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            currentDay = normalizeToMidnight(calendar.time)

            // Stop if we've gone before all workouts (no point continuing)
            if (currentDay.before(earliestWorkout)) {
                // Check remaining days from earliest workout to current check point
                break
            }
        }

        return streakDays
    }

    /**
     * Count missed days in a 7-day window ending on the given date.
     */
    private fun countMissedDaysInWindowFrom(endDate: Date, workoutDays: Set<Date>): Int {
        var missedCount = 0
        val calendar = Calendar.getInstance()

        for (i in 0..6) {
            calendar.time = endDate
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val day = normalizeToMidnight(calendar.time)

            if (!workoutDays.contains(day)) {
                missedCount++
            }
        }

        return missedCount
    }

    /**
     * Normalize a date to midnight for consistent comparison.
     */
    private fun normalizeToMidnight(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }
}
