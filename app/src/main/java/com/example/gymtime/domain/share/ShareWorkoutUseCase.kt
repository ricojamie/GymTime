package com.example.gymtime.domain.share

import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.util.WorkoutShareFormatter
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ShareWorkoutUseCase @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao
) {

    /**
     * Builds the plain-text share body for the given workout. Returns null if
     * the workout has no sets (nothing meaningful to share).
     */
    suspend operator fun invoke(workoutId: Long): String? {
        val workout = workoutDao.getWorkoutById(workoutId).first()
        val rawSets = setDao.getWorkoutSetsWithExercises(workoutId)
        if (rawSets.isEmpty()) return null

        val workoutStartMs = workout.startTime.time
        val durationMinutes = workout.endTime?.let { end ->
            ((end.time - workoutStartMs) / 1000 / 60).toInt().coerceAtLeast(0)
        }

        // Group preserving the order of first appearance for each exercise.
        val grouped = rawSets.groupBy { it.set.exerciseId }
        val orderedExerciseIds = rawSets.map { it.set.exerciseId }.distinct()

        val workingSets = rawSets.filter { !it.set.isWarmup }
        val totalVolume = workingSets.sumOf { info ->
            ((info.set.weight ?: 0f) * (info.set.reps ?: 0)).toDouble()
        }.toFloat()

        val exercises = orderedExerciseIds.mapNotNull { exerciseId ->
            val infos = grouped[exerciseId] ?: return@mapNotNull null
            buildExercise(exerciseId, workoutStartMs, infos)
        }

        val shareable = ShareableWorkout(
            date = workout.startTime,
            durationMinutes = durationMinutes,
            totalVolume = totalVolume,
            totalWorkingSets = workingSets.size,
            exercises = exercises
        )

        return WorkoutShareFormatter.format(shareable)
    }

    private suspend fun buildExercise(
        exerciseId: Long,
        workoutStartMs: Long,
        infos: List<SetWithExerciseInfo>
    ): ShareableExercise {
        // Pre-workout baseline: heaviest non-warmup set strictly before this workout.
        val priorBest = setDao.getPersonalBestBefore(exerciseId, workoutStartMs)?.weight
        val name = infos.firstOrNull()?.exerciseName.orEmpty()
        val muscle = infos.firstOrNull()?.targetMuscle.orEmpty()

        // Mark only the single heaviest working set as the PR (if it beats prior best).
        val heaviestWorkingSetId = infos
            .filter { !it.set.isWarmup && it.set.weight != null }
            .maxByOrNull { it.set.weight ?: 0f }
            ?.takeIf { winner ->
                val w = winner.set.weight ?: return@takeIf false
                priorBest == null || w > priorBest
            }
            ?.set?.id

        val sets = infos.map { info ->
            val s = info.set
            ShareableSet(
                weight = s.weight,
                reps = s.reps,
                isWarmup = s.isWarmup,
                isPersonalRecord = s.id == heaviestWorkingSetId
            )
        }
        return ShareableExercise(name = name, targetMuscle = muscle, sets = sets)
    }
}
