package com.example.gymtime.util

import java.util.Calendar
import java.util.Date

/**
 * Calculates the user's "Sustainable Streak" - a consistency tracker.
 *
 * Rules:
 * 1. The week starts on Sunday and ends on Saturday.
 * 2. Every user gets a configurable number of "free skips" per calendar week.
 * 3. On Sunday morning, the skip count resets.
 * 4. A streak is maintained as long as the user doesn't exceed their weekly skips.
 * 5. If a user misses one more workout than allowed in a week, the streak ends.
 * 6. Skips don't increment the streak count, but they don't break it.
 * 7. For new users, the "clock" starts on the day of their first workout.
 */
object StreakCalculator {

    enum class StreakState {
        ACTIVE,   // Worked out today
        RESTING,  // Using a skip today, but streak is alive
        BROKEN    // Used too many skips this week, streak reset
    }

    data class StreakResult(
        val state: StreakState,
        val streakDays: Int,
        val skipsRemaining: Int,
        val allowedSkipsPerWeek: Int,
        val nextResetDate: Date,    // Next Sunday
        val brokeToday: Boolean = false  // True only on the day the streak breaks
    )

    fun calculateStreak(workoutDates: List<Date>, allowedSkipsPerWeek: Int = 2): StreakResult {
        val safeAllowedSkips = allowedSkipsPerWeek.coerceIn(0, 7)

        if (workoutDates.isEmpty()) {
            return StreakResult(
                state = StreakState.RESTING,
                streakDays = 0,
                skipsRemaining = safeAllowedSkips,
                allowedSkipsPerWeek = safeAllowedSkips,
                nextResetDate = getNextSunday()
            )
        }

        val today = normalizeToMidnight(Date())
        val workoutDaysNormalized = workoutDates.map { normalizeToMidnight(it) }.toSet()
        val earliestWorkout = workoutDaysNormalized.minOrNull() ?: today

        // 1. Calculate how many skips have been used THIS calendar week (Sun-Sat)
        val currentWeekStart = getStartOfCurrentWeek()
        val usedSkipsThisWeek = countUsedSkipsInWeek(currentWeekStart, today, workoutDaysNormalized)
        val skipsRemaining = (safeAllowedSkips - usedSkipsThisWeek).coerceAtLeast(0)

        // 2. Determine current state
        val workedOutToday = workoutDaysNormalized.contains(today)
        val state = when {
            usedSkipsThisWeek > safeAllowedSkips -> StreakState.BROKEN
            workedOutToday -> StreakState.ACTIVE
            else -> StreakState.RESTING
        }

        // 3. Calculate streak length
        // We walk back week by week. A week is "safe" if it has 2 or fewer misses.
        // We count total workout days in consecutive safe weeks.
        var totalStreakDays = 0
        if (state != StreakState.BROKEN) {
            totalStreakDays = calculateTotalStreakDays(
                today = today,
                workoutDays = workoutDaysNormalized,
                earliestWorkout = earliestWorkout,
                allowedSkipsPerWeek = safeAllowedSkips
            )
        }

        // 4. Determine if streak broke TODAY (exactly one miss over the weekly allowance)
        val brokeToday = state == StreakState.BROKEN &&
                         usedSkipsThisWeek == safeAllowedSkips + 1 &&
                         !workedOutToday

        return StreakResult(
            state = state,
            streakDays = totalStreakDays,
            skipsRemaining = skipsRemaining,
            allowedSkipsPerWeek = safeAllowedSkips,
            nextResetDate = getNextSunday(),
            brokeToday = brokeToday
        )
    }

    private fun countUsedSkipsInWeek(weekStart: Date, today: Date, workoutDays: Set<Date>): Int {
        var misses = 0
        val cal = Calendar.getInstance()
        cal.time = weekStart
        
        // Count days from week start to today (inclusive)
        while (!cal.time.after(today)) {
            if (!workoutDays.contains(cal.time)) {
                misses++
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return misses
    }

    private fun calculateTotalStreakDays(
        today: Date,
        workoutDays: Set<Date>,
        earliestWorkout: Date,
        allowedSkipsPerWeek: Int
    ): Int {
        var totalDays = 0
        var currentWeekEnd = today
        val cal = Calendar.getInstance()

        while (true) {
            val weekStart = getStartOfWeek(currentWeekEnd)
            
            // For the first week (the one containing 'earliestWorkout'), we only count misses 
            // from the day of the first workout onwards.
            val effectiveWeekStart = if (weekStart.before(earliestWorkout)) earliestWorkout else weekStart
            
            var missesInWeek = 0
            var workoutDaysInWeek = 0
            
            val walkCal = Calendar.getInstance()
            walkCal.time = effectiveWeekStart
            
            // For the current week, we only walk up to today. 
            // For past weeks, we walk the full Sun-Sat range (or earliestWorkout-Sat).
            val walkEnd = if (currentWeekEnd.after(today)) today else currentWeekEnd
            
            while (!walkCal.time.after(walkEnd)) {
                if (workoutDays.contains(walkCal.time)) {
                    workoutDaysInWeek++
                } else {
                    missesInWeek++
                }
                walkCal.add(Calendar.DAY_OF_YEAR, 1)
            }

            if (missesInWeek > allowedSkipsPerWeek) {
                // Streak broke in this week
                break
            }

            totalDays += workoutDaysInWeek

            // Move to previous week (the Saturday before this week's Sunday)
            cal.time = weekStart
            cal.add(Calendar.DAY_OF_YEAR, -1)
            currentWeekEnd = cal.time

            if (currentWeekEnd.before(earliestWorkout)) break
        }

        return totalDays
    }

    private fun getStartOfCurrentWeek(): Date {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        return normalizeToMidnight(cal.time)
    }

    private fun getStartOfWeek(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        return normalizeToMidnight(cal.time)
    }

    private fun getNextSunday(): Date {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1) // Start from tomorrow
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return normalizeToMidnight(cal.time)
    }

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
