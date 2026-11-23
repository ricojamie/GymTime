package com.example.gymtime.domain.recommendation

import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.util.OneRepMaxCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.floor

data class ExerciseAttemptRecommendation(
    val exerciseId: Long,
    val targetWeight: Float?,
    val targetReps: Int?,
    val title: String,
    val message: String,
    val canApply: Boolean
)

class ExerciseAttemptRecommendationUseCase @Inject constructor(
    private val setDao: SetDao
) {
    suspend fun getRecommendation(exercise: Exercise): ExerciseAttemptRecommendation? =
        withContext(Dispatchers.IO) {
            val repTarget = exercise.repTarget?.takeIf { it > 0 } ?: return@withContext null
            if (exercise.logType != LogType.WEIGHT_REPS && exercise.logType != LogType.REPS_ONLY) {
                return@withContext null
            }

            // SetDao.getExerciseHistoryByWorkout already orders by workout startTime DESC.
            val history = setDao.getExerciseHistoryByWorkout(exercise.id)
                .filter { it.isComplete && !it.isWarmup && (it.reps ?: 0) > 0 }

            if (history.isEmpty()) return@withContext null

            when (exercise.logType) {
                LogType.WEIGHT_REPS -> recommendWeighted(exercise, repTarget, history)
                LogType.REPS_ONLY -> recommendRepsOnly(exercise, repTarget, history)
                else -> null
            }
        }

    private fun recommendWeighted(
        exercise: Exercise,
        repTarget: Int,
        history: List<Set>
    ): ExerciseAttemptRecommendation? {
        val weightedHistory = history.filter { set ->
            val weight = set.weight
            weight != null && weight.isFinite() && weight > 0f
        }
        if (weightedHistory.isEmpty()) return null

        val latestWorkoutId = weightedHistory.first().workoutId
        val latestWorkoutSets = weightedHistory.filter { it.workoutId == latestWorkoutId }
        val bestLatestSet = latestWorkoutSets.maxWithOrNull(
            compareBy<Set> { it.weight ?: 0f }.thenBy { it.reps ?: 0 }
        ) ?: return null

        val lastWeight = bestLatestSet.weight?.takeIf { it.isFinite() && it > 0f } ?: return null
        val bestRepsAtLastWeight = latestWorkoutSets
            .filter { it.weight == lastWeight }
            .maxOfOrNull { it.reps ?: 0 }
            ?.takeIf { it > 0 }
            ?: return null

        return if (bestRepsAtLastWeight >= repTarget) {
            val nextWeight = nextRoundedWeight(lastWeight)
            val estimatedReps = estimateRepsForWeight(weightedHistory, nextWeight, repTarget)
            ExerciseAttemptRecommendation(
                exerciseId = exercise.id,
                targetWeight = nextWeight,
                targetReps = estimatedReps,
                title = "Today's attempt",
                message = "You hit $repTarget reps at ${formatWeight(lastWeight)}. Try ${formatWeight(nextWeight)} for $estimatedReps.",
                canApply = true
            )
        } else {
            val nextReps = (bestRepsAtLastWeight + 1).coerceAtMost(repTarget)
            ExerciseAttemptRecommendation(
                exerciseId = exercise.id,
                targetWeight = lastWeight,
                targetReps = nextReps,
                title = "Today's attempt",
                message = "Build toward $repTarget reps before increasing weight.",
                canApply = true
            )
        }
    }

    private fun recommendRepsOnly(
        exercise: Exercise,
        repTarget: Int,
        history: List<Set>
    ): ExerciseAttemptRecommendation {
        val latestWorkoutId = history.first().workoutId
        val bestLatestReps = history
            .filter { it.workoutId == latestWorkoutId }
            .maxOf { it.reps ?: 0 }

        return if (bestLatestReps >= repTarget) {
            ExerciseAttemptRecommendation(
                exerciseId = exercise.id,
                targetWeight = null,
                targetReps = null,
                title = "Target hit",
                message = "You reached $repTarget reps. Consider a harder variation or added load.",
                canApply = false
            )
        } else {
            val nextReps = (bestLatestReps + 1).coerceAtMost(repTarget)
            ExerciseAttemptRecommendation(
                exerciseId = exercise.id,
                targetWeight = null,
                targetReps = nextReps,
                title = "Today's attempt",
                message = "Aim for $nextReps reps on the way to $repTarget.",
                canApply = true
            )
        }
    }

    private fun estimateRepsForWeight(history: List<Set>, targetWeight: Float, repTarget: Int): Int {
        if (!targetWeight.isFinite() || targetWeight <= 0f) return (repTarget - 2).coerceAtLeast(1)
        val topEstimates = history
            .mapNotNull { set ->
                val weight = set.weight?.takeIf { it.isFinite() && it > 0f } ?: return@mapNotNull null
                val reps = set.reps?.takeIf { it > 0 } ?: return@mapNotNull null
                OneRepMaxCalculator.calculateE1RM(weight, reps)
                    ?.takeIf { it.isFinite() && it > 0f }
            }
            .sortedDescending()
            .take(3)
        if (topEstimates.isEmpty()) return (repTarget - 2).coerceAtLeast(1)

        val e1rm = topEstimates.average().toFloat()
        if (!e1rm.isFinite() || e1rm <= 0f) return (repTarget - 2).coerceAtLeast(1)

        val rawEstimate = ((e1rm / targetWeight) - 1f) * 30f
        if (!rawEstimate.isFinite()) return (repTarget - 2).coerceAtLeast(1)
        val estimated = floor(rawEstimate).toInt()
        return estimated.coerceIn(1, repTarget)
    }

    private fun nextRoundedWeight(currentWeight: Float): Float {
        val rounded = OneRepMaxCalculator.formatWeight(currentWeight + 2.5f)
        return if (rounded.isFinite() && rounded > currentWeight) rounded else currentWeight + 2.5f
    }

    private fun formatWeight(weight: Float): String {
        if (!weight.isFinite()) return "0"
        return if (weight % 1f == 0f) {
            weight.toInt().toString()
        } else {
            weight.toString()
        }
    }
}
