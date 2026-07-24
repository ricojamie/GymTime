package com.example.gymtime.domain.report

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object MonthlyReportNarrative {
    fun canonicalFacts(report: MonthlyReport): String = buildString {
        appendLine("month=${report.monthLabel}")
        appendLine("workouts=${report.workoutCount}")
        appendLine("previous_workouts=${report.previousWorkoutCount}")
        appendLine("volume_lb=${format(report.totalVolume)}")
        appendLine("previous_volume_lb=${format(report.previousTotalVolume)}")
        appendLine("volume_change_percent=${report.volumeChangePercent?.let(::format).orEmpty()}")
        appendLine("working_sets=${report.totalWorkingSets}")
        appendLine("previous_working_sets=${report.previousWorkingSets}")
        appendLine("training_days=${report.trainingDays}")
        appendLine("active_weeks=${report.activeWeeks}")
        appendLine("weeks_in_period=${report.weeksInPeriod}")
        appendLine("end_of_month_iron_streak_days=${report.endOfMonthStreakDays}")
        appendLine("top_muscles=${report.topMuscles.joinToString("|") { "${it.muscle}:${it.setCount}" }}")
        appendLine("undertrained_muscles=${report.undertrainedMuscles.joinToString("|")}")
        append("new_prs=${report.newPRs.joinToString("|") { "${it.exerciseName}:${format(it.weight)}x${it.reps}" }}")
    }

    fun template(report: MonthlyReport): String {
        if (report.workoutCount == 0) {
            return "No completed workouts landed in ${report.monthLabel}, so there was no training trend to summarize."
        }

        val volumePhrase = report.volumeChangePercent?.let { change ->
            val direction = if (change >= 0f) "up" else "down"
            "${format(report.totalVolume)} lb of volume, $direction ${abs(change).roundToInt()}% from the prior month"
        } ?: "${format(report.totalVolume)} lb of volume while establishing a monthly baseline"

        val consistencyPhrase = if (report.weeksInPeriod > 0) {
            "trained on ${report.trainingDays} days across ${report.activeWeeks} of ${report.weeksInPeriod} calendar weeks"
        } else {
            "trained on ${report.trainingDays} days"
        }

        val prPhrase = when (report.newPRs.size) {
            0 -> ""
            1 -> " A new PR landed on ${report.newPRs.first().exerciseName}."
            else -> " ${report.newPRs.size} new PRs landed, led by ${report.newPRs.first().exerciseName}."
        }

        return "You completed ${report.workoutCount} workouts and $consistencyPhrase, with $volumePhrase.$prPhrase"
    }

    private fun format(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}
