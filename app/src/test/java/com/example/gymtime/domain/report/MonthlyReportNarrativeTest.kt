package com.example.gymtime.domain.report

import java.util.Date
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyReportNarrativeTest {
    @Test
    fun templateUsesOnlyComputedMonthlyFacts() {
        val report = report(
            workoutCount = 8,
            volume = 12_000f,
            previousVolume = 10_000f,
            change = 20f,
            prs = listOf(MonthlyPR("Deadlift", 405f, 3))
        )

        val narrative = MonthlyReportNarrative.template(report)

        assertTrue(narrative.contains("8 workouts"))
        assertTrue(narrative.contains("up 20%"))
        assertTrue(narrative.contains("Deadlift"))
    }

    @Test
    fun emptyMonthDoesNotInventATrend() {
        val narrative = MonthlyReportNarrative.template(
            report(workoutCount = 0, volume = 0f, previousVolume = 1_000f, change = -100f)
        )

        assertTrue(narrative.contains("No completed workouts"))
        assertFalse(narrative.contains("PR"))
    }

    private fun report(
        workoutCount: Int,
        volume: Float,
        previousVolume: Float,
        change: Float?,
        prs: List<MonthlyPR> = emptyList()
    ) = MonthlyReport(
        periodStart = Date(0),
        periodEnd = Date(1),
        monthLabel = "June 2026",
        workoutCount = workoutCount,
        totalVolume = volume,
        totalWorkingSets = 24,
        exerciseCount = 6,
        previousWorkoutCount = 7,
        previousTotalVolume = previousVolume,
        previousWorkingSets = 20,
        volumeChangePercent = change,
        workingSetChangePercent = 20f,
        trainingDays = 8,
        activeWeeks = 4,
        weeksInPeriod = 5,
        endOfMonthStreakDays = 8,
        topMuscles = listOf(MuscleTotal("Back", 10)),
        undertrainedMuscles = emptyList(),
        newPRs = prs,
        averageRating = 4f
    )
}
