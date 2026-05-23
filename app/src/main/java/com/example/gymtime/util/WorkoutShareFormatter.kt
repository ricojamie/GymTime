package com.example.gymtime.util

import com.example.gymtime.domain.share.ShareableSet
import com.example.gymtime.domain.share.ShareableWorkout
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

object WorkoutShareFormatter {

    /**
     * Renders a [ShareableWorkout] as plain text suitable for Android's share sheet.
     * Warmup sets and exercises with zero working sets are excluded from the body.
     */
    fun format(workout: ShareableWorkout, locale: Locale = Locale.getDefault()): String {
        val dateFormat = SimpleDateFormat("d MMM yyyy", locale)
        val volumeFormat = NumberFormat.getNumberInstance(locale)
        val builder = StringBuilder()

        builder.append("GymTime — ").append(dateFormat.format(workout.date)).append('\n')

        val headerParts = mutableListOf<String>()
        workout.durationMinutes?.let { headerParts += formatDuration(it) }
        headerParts += "Volume: ${volumeFormat.format(workout.totalVolume.toLong())} lbs"
        val exerciseCount = workout.exercises.count { it.sets.any { s -> !s.isWarmup } }
        headerParts += "${workout.totalWorkingSets} sets across $exerciseCount exercises"
        builder.append(headerParts.joinToString("  |  ")).append("\n\n")

        workout.exercises.forEach { exercise ->
            val workingSets = exercise.sets.filter { !it.isWarmup }
            if (workingSets.isEmpty()) return@forEach
            builder.append(exercise.name).append('\n')
            workingSets.forEach { set ->
                builder.append("  ").append(formatSet(set))
                if (set.isPersonalRecord) builder.append("  🏆 PR")
                builder.append('\n')
            }
        }

        builder.append("\nShared from GymTime")
        return builder.toString()
    }

    private fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "Duration: ${h}h ${m}m" else "Duration: ${m}m"
    }

    private fun formatSet(set: ShareableSet): String {
        val weight = set.weight
        val reps = set.reps
        return when {
            weight != null && reps != null -> "${trim(weight)} lbs × $reps"
            weight != null -> "${trim(weight)} lbs"
            reps != null -> "$reps reps"
            else -> "—"
        }
    }

    private fun trim(value: Float): String {
        // Drop trailing .0 for whole-number weights ("60 lbs" not "60.0 lbs").
        return if (value % 1f == 0f) value.toInt().toString() else value.toString()
    }
}
