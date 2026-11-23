package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.RoutineSetRow
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.util.OneRepMaxCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
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
    private var clockMs: () -> Long = System::currentTimeMillis

    internal constructor(
        workoutDao: WorkoutDao,
        clockMs: () -> Long
    ) : this(workoutDao) {
        this.clockMs = clockMs
    }

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

        val now = clockMs()
        val fourWeeksAgo = now - 28L * 24 * 60 * 60 * 1000
        val recentCount = workouts.count { it.startTime.time in fourWeeksAgo..now }
        val totalVolume = workouts.sumOf { row ->
            row.totalVolume.takeIf { it.isFinite() && it >= 0f }?.toDouble() ?: 0.0
        }.toFloat()

        RoutineStats(
            timesCompleted = workouts.size,
            lastPerformed = workouts.maxOfOrNull { it.startTime },
            avgDurationMinutes = durations.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            totalVolume = totalVolume,
            workoutsPerWeekRecent = if (recentCount > 0) recentCount / 4f else null,
            exerciseTrends = buildExerciseTrends(routineId)
        )
    }

    private suspend fun buildExerciseTrends(routineId: Long): List<RoutineExerciseTrend> {
        val sets = workoutDao.getWorkingSetsForRoutine(routineId)
            .filter { it.weight.isFinite() && it.weight >= 0f && it.reps > 0 }
        if (sets.isEmpty()) return emptyList()

        return sets.groupBy { it.exerciseId }.mapNotNull { (exerciseId, rows) ->
            val perWorkout = rows.groupBy { it.workoutId }
                .mapNotNull { (workoutId, workoutRows) ->
                    val bestE1rm = workoutRows.mapNotNull { row ->
                        OneRepMaxCalculator.calculateE1RM(row.weight, row.reps)
                            ?.takeIf { it.isFinite() && it > 0f }
                            ?.let { row to it }
                    }.maxWithOrNull(
                        compareBy<Pair<RoutineSetRow, Float>> { it.second }
                            .thenBy { it.first.weight }
                            .thenBy { it.first.reps }
                    )
                    val bestRaw = workoutRows.maxWithOrNull(
                        compareBy<RoutineSetRow> { it.weight }.thenBy { it.reps }
                    ) ?: return@mapNotNull null

                    WorkoutBest(
                        workoutId = workoutId,
                        startTime = workoutRows.first().startTime,
                        displayRow = bestE1rm?.first ?: bestRaw,
                        e1rm = bestE1rm?.second
                    )
                }
                .sortedWith(compareBy<WorkoutBest> { it.startTime.time }.thenBy { it.workoutId })

            val latest = perWorkout.lastOrNull() ?: return@mapNotNull null
            val eligibleWorkouts = perWorkout.filter { it.e1rm != null }
            val latestEligible = eligibleWorkouts.lastOrNull()

            // Baseline: best eligible e1RM from the most recent workout at least 3 weeks
            // older than the latest eligible workout; fall back to the earliest eligible workout.
            val threeWeeksMs = 21L * 24 * 60 * 60 * 1000
            val baselineEligible = if (latestEligible != null && eligibleWorkouts.size > 1) {
                eligibleWorkouts.lastOrNull {
                    it.workoutId != latestEligible.workoutId &&
                        it.startTime.time <= latestEligible.startTime.time - threeWeeksMs
                } ?: eligibleWorkouts.firstOrNull { it.workoutId != latestEligible.workoutId }
            } else {
                null
            }

            val lastE1rm = latestEligible?.e1rm
            val baselineE1rm = baselineEligible?.e1rm

            RoutineExerciseTrend(
                exerciseId = exerciseId,
                exerciseName = rows.first().exerciseName,
                lastBestLabel = "${formatWeight(latest.displayRow.weight)} × ${latest.displayRow.reps}",
                lastE1rm = lastE1rm,
                e1rmDelta = if (lastE1rm != null && baselineE1rm != null) lastE1rm - baselineE1rm else null
            )
        }.sortedBy { it.exerciseName }
    }

    private fun formatWeight(weight: Float): String {
        if (!weight.isFinite()) return "0"
        return if (weight % 1f == 0f) {
            weight.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", weight)
        }
    }

    private data class WorkoutBest(
        val workoutId: Long,
        val startTime: Date,
        val displayRow: RoutineSetRow,
        val e1rm: Float?
    )
}
