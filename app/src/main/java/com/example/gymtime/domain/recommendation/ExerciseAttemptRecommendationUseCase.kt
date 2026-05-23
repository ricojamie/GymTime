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

            val history = setDao.getExerciseHistoryByWorkout(exercise.id)
                .filter { it.isComplete && !it.isWarmup && it.reps != null }
                .sortedByDescending { it.timestamp.time }

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
        val weightedHistory = history.filter { it.weight != null }
        if (weightedHistory.isEmpty()) return null

        val latestWorkoutId = weightedHistory.first().workoutId
        val latestWorkoutSets = weightedHistory.filter { it.workoutId == latestWorkoutId }
        val bestLatestSet = latestWorkoutSets.maxWithOrNull(
            compareBy<Set> { it.weight ?: 0f }.thenBy { it.reps ?: 0 }
        ) ?: return null

        val lastWeight = bestLatestSet.weight ?: return null
        val bestRepsAtLastWeight = latestWorkoutSets
            .filter { it.weight == lastWeight }
            .maxOfOrNull { it.reps ?: 0 }
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
        val e1rm = history
            .mapNotNull { set ->
                val weight = set.weight ?: return@mapNotNull null
                val reps = set.reps ?: return@mapNotNull null
                OneRepMaxCalculator.calculateE1RM(weight, reps)
            }
            .sortedDescending()
            .take(3)
            .average()
            .toFloat()
            .takeIf { !it.isNaN() }
            ?: return (repTarget - 2).coerceAtLeast(1)

        val estimated = floor(((e1rm / targetWeight) - 1f) * 30f).toInt()
        return estimated.coerceIn(1, repTarget)
    }

    private fun nextRoundedWeight(currentWeight: Float): Float {
        val rounded = OneRepMaxCalculator.formatWeight(currentWeight + 2.5f)
        return if (rounded > currentWeight) rounded else currentWeight + 2.5f
    }

    private fun formatWeight(weight: Float): String {
        return if (weight % 1f == 0f) {
            weight.toInt().toString()
        } else {
            weight.toString()
        }
    }
}
