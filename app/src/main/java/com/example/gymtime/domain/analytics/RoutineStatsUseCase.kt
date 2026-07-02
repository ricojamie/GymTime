package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.util.OneRepMaxCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

data class RoutineExerciseTrend(
    val exerciseId: Long,
    val exerciseName: String,
    val lastBestLabel: String, // e.g. "100 × 8"
    val lastE1rm: Float?,
    val e1rmDelta: Float? // vs best from ~4 weeks earlier; null if not enough history
)

data class RoutineStats(
    val timesCompleted: Int,
    val lastPerformed: Date?,
    val avgDurationMinutes: Int?,
    val totalVolume: Float,
    val workoutsPerWeekRecent: Float?, // last 4 weeks
    val exerciseTrends: List<RoutineExerciseTrend>
)

/**
 * Aggregates history for workouts started from a given routine
 * (linked via workouts.routineId).
 */
class RoutineStatsUseCase @Inject constructor(
    private val workoutDao: WorkoutDao
) {
    suspend fun getStats(routineId: Long): RoutineStats = withContext(Dispatchers.IO) {
        // Only count workouts where something was actually logged.
        val workouts = workoutDao.getCompletedWorkoutsForRoutine(routineId)
            .filter { it.workingSetCount > 0 }

        val durations = workouts.mapNotNull { row ->
            row.endTime?.let { end ->
                val minutes = (end.time - row.startTime.time) / 60_000L
                minutes.takeIf { it in 1..360 }
            }
        }

        val fourWeeksAgo = System.currentTimeMillis() - 28L * 24 * 60 * 60 * 1000
        val recentCount = workouts.count { it.startTime.time >= fourWeeksAgo }

        RoutineStats(
            timesCompleted = workouts.size,
            lastPerformed = workouts.maxOfOrNull { it.startTime },
            avgDurationMinutes = durations.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            totalVolume = workouts.map { it.totalVolume }.sum(),
            workoutsPerWeekRecent = if (recentCount > 0) recentCount / 4f else null,
            exerciseTrends = buildExerciseTrends(routineId)
        )
    }

    private suspend fun buildExerciseTrends(routineId: Long): List<RoutineExerciseTrend> {
        val sets = workoutDao.getWorkingSetsForRoutine(routineId)
        if (sets.isEmpty()) return emptyList()

        return sets.groupBy { it.exerciseId }.mapNotNull { (exerciseId, rows) ->
            // Best e1RM per workout, in chronological order.
            val perWorkout = rows.groupBy { it.workoutId }
                .map { (_, workoutRows) ->
                    val best = workoutRows.maxByOrNull {
                        OneRepMaxCalculator.calculateE1RM(it.weight, it.reps) ?: 0f
                    } ?: return@mapNotNull null
                    best
                }
                .sortedBy { it.startTime }
            val last = perWorkout.lastOrNull() ?: return@mapNotNull null
            val lastE1rm = OneRepMaxCalculator.calculateE1RM(last.weight, last.reps)

            // Baseline: best e1RM from the most recent workout at least 3 weeks
            // older than the last one; fall back to the earliest workout.
            val threeWeeksMs = 21L * 24 * 60 * 60 * 1000
            val baselineRow = perWorkout.lastOrNull { it.startTime.time <= last.startTime.time - threeWeeksMs }
                ?: perWorkout.firstOrNull().takeIf { perWorkout.size > 1 }
            val baselineE1rm = baselineRow?.let { OneRepMaxCalculator.calculateE1RM(it.weight, it.reps) }

            RoutineExerciseTrend(
                exerciseId = exerciseId,
                exerciseName = rows.first().exerciseName,
                lastBestLabel = "${formatWeight(last.weight)} × ${last.reps}",
                lastE1rm = lastE1rm,
                e1rmDelta = if (lastE1rm != null && baselineE1rm != null) lastE1rm - baselineE1rm else null
            )
        }.sortedBy { it.exerciseName }
    }

    private fun formatWeight(weight: Float): String =
        if (weight % 1f == 0f) weight.toInt().toString() else "%.1f".format(weight)
}
