package com.example.gymtime.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
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

    fun calculateStreak(
        workoutDates: List<Date>,
        allowedSkipsPerWeek: Int = 2,
        asOf: Date = Date()
    ): StreakResult = calculateStreakForZone(
        workoutDates = workoutDates,
        allowedSkipsPerWeek = allowedSkipsPerWeek,
        asOf = asOf,
        zoneId = ZoneId.systemDefault()
    )

    internal fun calculateStreakForZone(
        workoutDates: List<Date>,
        allowedSkipsPerWeek: Int = 2,
        asOf: Date = Date(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): StreakResult {
        val safeAllowedSkips = allowedSkipsPerWeek.coerceIn(0, 7)
        val today = toLocalDate(asOf, zoneId)
        val nextResetDate = toMidnightDate(getNextSunday(today), zoneId)

        if (workoutDates.isEmpty()) {
            return StreakResult(
                state = StreakState.RESTING,
                streakDays = 0,
                skipsRemaining = safeAllowedSkips,
                allowedSkipsPerWeek = safeAllowedSkips,
                nextResetDate = nextResetDate
            )
        }

        val workoutDaysNormalized = workoutDates
            .asSequence()
            .map { toLocalDate(it, zoneId) }
            .filterNot { it.isAfter(today) }
            .toSet()
        val earliestWorkout = workoutDaysNormalized.minOrNull() ?: today

        // 1. Calculate how many skips have been used THIS calendar week (Sun-Sat)
        val currentWeekStart = getStartOfWeek(today)
        val effectiveCurrentWeekStart = if (currentWeekStart.isBefore(earliestWorkout)) {
            earliestWorkout
        } else {
            currentWeekStart
        }
        val usedSkipsThisWeek = countUsedSkipsInWeek(effectiveCurrentWeekStart, today, workoutDaysNormalized)
        val skipsRemaining = (safeAllowedSkips - usedSkipsThisWeek).coerceAtLeast(0)

        // 2. Determine current state
        val workedOutToday = today in workoutDaysNormalized
        val state = when {
            usedSkipsThisWeek > safeAllowedSkips -> StreakState.BROKEN
            workedOutToday -> StreakState.ACTIVE
            else -> StreakState.RESTING
        }

        // 3. Calculate streak length
        // We walk back week by week. A week is "safe" if it has allowedSkipsPerWeek or fewer misses.
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
            nextResetDate = nextResetDate,
            brokeToday = brokeToday
        )
    }

    fun calculateBestStreak(
        workoutDates: List<Date>,
        allowedSkipsPerWeek: Int = 2,
        asOf: Date = Date()
    ): Int = calculateBestStreakForZone(
        workoutDates = workoutDates,
        allowedSkipsPerWeek = allowedSkipsPerWeek,
        asOf = asOf,
        zoneId = ZoneId.systemDefault()
    )

    internal fun calculateBestStreakForZone(
        workoutDates: List<Date>,
        allowedSkipsPerWeek: Int = 2,
        asOf: Date = Date(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int {
        if (workoutDates.isEmpty()) return 0
        val safeAllowedSkips = allowedSkipsPerWeek.coerceIn(0, 7)
        val today = toLocalDate(asOf, zoneId)
        val workoutDays = workoutDates
            .asSequence()
            .map { toLocalDate(it, zoneId) }
            .filterNot { it.isAfter(today) }
            .toSet()
        val earliestWorkout = workoutDays.minOrNull() ?: return 0

        var bestStreak = 0
        var runningPriorWeeksStreak = 0
        var weekStart = getStartOfWeek(earliestWorkout)

        while (!weekStart.isAfter(today)) {
            val weekEnd = weekStart.plusDays(6)
            val effectiveWeekStart = if (weekStart.isBefore(earliestWorkout)) earliestWorkout else weekStart
            val walkEnd = if (weekEnd.isAfter(today)) today else weekEnd

            var missesInWeek = 0
            var workoutsInWeek = 0
            var day = effectiveWeekStart
            while (!day.isAfter(walkEnd)) {
                if (day in workoutDays) {
                    workoutsInWeek++
                } else {
                    missesInWeek++
                }
                if (missesInWeek <= safeAllowedSkips) {
                    val streakOnDay = runningPriorWeeksStreak + workoutsInWeek
                    if (streakOnDay > bestStreak) {
                        bestStreak = streakOnDay
                    }
                }
                day = day.plusDays(1)
            }

            runningPriorWeeksStreak = if (missesInWeek <= safeAllowedSkips) {
                runningPriorWeeksStreak + workoutsInWeek
            } else {
                0
            }
            weekStart = weekStart.plusWeeks(1)
        }

        return bestStreak
    }

    private fun countUsedSkipsInWeek(
        weekStart: LocalDate,
        today: LocalDate,
        workoutDays: Set<LocalDate>
    ): Int {
        var misses = 0
        var day = weekStart
        // Count days from week start to today (inclusive)
        while (!day.isAfter(today)) {
            if (day !in workoutDays) {
                misses++
            }
            day = day.plusDays(1)
        }
        return misses
    }

    private fun calculateTotalStreakDays(
        today: LocalDate,
        workoutDays: Set<LocalDate>,
        earliestWorkout: LocalDate,
        allowedSkipsPerWeek: Int
    ): Int {
        var totalDays = 0
        var currentWeekEnd = today

        while (true) {
            val weekStart = getStartOfWeek(currentWeekEnd)

            // For the first week (the one containing 'earliestWorkout'), we only count misses
            // from the day of the first workout onwards.
            val effectiveWeekStart = if (weekStart.isBefore(earliestWorkout)) earliestWorkout else weekStart

            var missesInWeek = 0
            var workoutDaysInWeek = 0

            // For the current week, we only walk up to today.
            // For past weeks, we walk the full Sun-Sat range (or earliestWorkout-Sat).
            val walkEnd = if (currentWeekEnd.isAfter(today)) today else currentWeekEnd
            var day = effectiveWeekStart

            while (!day.isAfter(walkEnd)) {
                if (day in workoutDays) {
                    workoutDaysInWeek++
                } else {
                    missesInWeek++
                }
                day = day.plusDays(1)
            }

            if (missesInWeek > allowedSkipsPerWeek) {
                // Streak broke in this week
                break
            }

            totalDays += workoutDaysInWeek

            // Move to previous week (the Saturday before this week's Sunday)
            currentWeekEnd = weekStart.minusDays(1)

            if (currentWeekEnd.isBefore(earliestWorkout)) break
        }

        return totalDays
    }

    private fun getStartOfWeek(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    private fun getNextSunday(referenceDate: LocalDate): LocalDate =
        referenceDate.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))

    private fun toLocalDate(date: Date, zoneId: ZoneId): LocalDate =
        date.toInstant().atZone(zoneId).toLocalDate()

    private fun toMidnightDate(localDate: LocalDate, zoneId: ZoneId): Date =
        Date.from(localDate.atStartOfDay(zoneId).toInstant())
}
