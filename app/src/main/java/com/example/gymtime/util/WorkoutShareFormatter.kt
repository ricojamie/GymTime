package com.example.gymtime.util

import com.example.gymtime.domain.share.ShareableSet
import com.example.gymtime.domain.share.ShareableWorkout
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object WorkoutShareFormatter {
    private val dateFormatters = ConcurrentHashMap<Locale, DateTimeFormatter>()

    /**
     * Renders a [ShareableWorkout] as plain text suitable for Android's share sheet.
     * Warmup sets and exercises with zero working sets are excluded from the body.
     */
    fun format(workout: ShareableWorkout, locale: Locale = Locale.getDefault()): String {
        val formattedDate = formatShareDate(workout.date, locale)
        val volumeFormat = NumberFormat.getNumberInstance(locale)
        val builder = StringBuilder()

        builder.append("GymTime — ").append(formattedDate).append('\n')

        val headerParts = mutableListOf<String>()
        workout.durationMinutes?.let { headerParts += formatDuration(it) }
        headerParts += "Volume: ${volumeFormat.format(workout.totalVolume.toLong())} lbs"
        val exerciseCount = workout.exercises.count { it.sets.any { s -> !s.isWarmup } }
        headerParts += "${workout.totalWorkingSets} sets across $exerciseCount exercises"
        builder.append(headerParts.joinToString("  |  ")).append("\n\n")

        workout.recap?.takeIf { it.isNotBlank() }?.let { recap ->
            builder.append(recap.trim()).append("\n\n")
        }

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

    internal fun formatShareDate(date: Date, locale: Locale = Locale.getDefault()): String {
        val formatter = dateFormatters.computeIfAbsent(locale) { loc ->
            DateTimeFormatter.ofPattern("d MMM yyyy", loc)
        }
        return Instant.ofEpochMilli(date.time)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(formatter)
    }

    private fun formatDuration(minutes: Int): String {
        val safeMinutes = minutes.coerceAtLeast(0)
        val h = safeMinutes / 60
        val m = safeMinutes % 60
        return if (h > 0) "Duration: ${h}h ${m}m" else "Duration: ${m}m"
    }

    internal fun formatDurationSeconds(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val minutes = safeSeconds / 60
        val remaining = safeSeconds % 60
        return if (minutes > 0) "${minutes}m ${remaining}s" else "${remaining}s"
    }

    internal fun formatSet(
        set: ShareableSet,
        multiplicationSymbol: String = "×",
        emptyFallback: String = "—"
    ): String {
        val weight = set.weight?.takeIf { it.isFinite() }
        val reps = set.reps
        val duration = set.durationSeconds?.takeIf { it >= 0 }
        val distance = set.distanceMeters?.takeIf { it.isFinite() }
        val calories = set.calories?.takeIf { it.isFinite() }
        return when {
            weight != null && reps != null -> "${trim(weight)} lbs $multiplicationSymbol $reps"
            weight != null && duration != null -> "${trim(weight)} lbs · ${formatDurationSeconds(duration)}"
            weight != null && distance != null -> "${trim(weight)} lbs · ${trim(distance)} m"
            weight != null -> "${trim(weight)} lbs"
            reps != null -> "$reps reps"
            distance != null && duration != null -> "${trim(distance)} m · ${formatDurationSeconds(duration)}"
            calories != null && duration != null -> "${trim(calories)} cal · ${formatDurationSeconds(duration)}"
            duration != null -> formatDurationSeconds(duration)
            distance != null -> "${trim(distance)} m"
            calories != null -> "${trim(calories)} cal"
            else -> emptyFallback
        }
    }

    private fun trim(value: Float): String {
        if (!value.isFinite()) return "0"
        // Drop trailing .0 for whole-number weights ("60 lbs" not "60.0 lbs").
        return if (value % 1f == 0f) value.toInt().toString() else value.toString()
    }
}
