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
        return buildShareableWorkout(workoutId)?.let { WorkoutShareFormatter.format(it) }
    }

    suspend fun buildShareableWorkout(workoutId: Long): ShareableWorkout? {
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

        return ShareableWorkout(
            date = workout.startTime,
            durationMinutes = durationMinutes,
            totalVolume = totalVolume,
            totalWorkingSets = workingSets.size,
            exercises = exercises
        )
    }

    private suspend fun buildExercise(
        exerciseId: Long,
        workoutStartMs: Long,
        infos: List<SetWithExerciseInfo>
    ): ShareableExercise {
        val name = infos.firstOrNull()?.exerciseName.orEmpty()
        val muscle = infos.firstOrNull()?.targetMuscle.orEmpty()
        val personalRecordSetIds = detectRepPersonalRecords(exerciseId, workoutStartMs, infos)

        val sets = infos.map { info ->
            val s = info.set
            ShareableSet(
                weight = s.weight,
                reps = s.reps,
                isWarmup = s.isWarmup,
                isPersonalRecord = s.id in personalRecordSetIds,
                durationSeconds = s.durationSeconds,
                distanceMeters = s.distanceMeters,
                calories = s.calories
            )
        }
        return ShareableExercise(name = name, targetMuscle = muscle, sets = sets)
    }

    private suspend fun detectRepPersonalRecords(
        exerciseId: Long,
        workoutStartMs: Long,
        infos: List<SetWithExerciseInfo>
    ): Set<Long> {
        val runningBestByReps = setDao
            .getPersonalBestsWithTimestampsBefore(exerciseId, workoutStartMs)
            .associate { it.reps to it.maxWeight }
            .toMutableMap()
        val personalRecordSetIds = mutableSetOf<Long>()

        infos.sortedBy { it.set.timestamp.time }.forEach { info ->
            val set = info.set
            val reps = set.reps ?: return@forEach
            val weight = set.weight ?: return@forEach
            if (set.isWarmup) return@forEach

            val priorBest = runningBestByReps[reps]
            if (priorBest == null || weight > priorBest) {
                personalRecordSetIds += set.id
                runningBestByReps[reps] = weight
            }
        }

        return removeDominatedPersonalRecords(infos, personalRecordSetIds)
    }

    private fun removeDominatedPersonalRecords(
        infos: List<SetWithExerciseInfo>,
        personalRecordSetIds: Set<Long>
    ): Set<Long> {
        val recordSets = infos
            .map { it.set }
            .filter { it.id in personalRecordSetIds && it.weight != null && it.reps != null }

        return recordSets
            .filterNot { candidate ->
                recordSets.any { challenger ->
                    if (candidate.id == challenger.id) return@any false
                    val candidateWeight = candidate.weight ?: return@any false
                    val candidateReps = candidate.reps ?: return@any false
                    val challengerWeight = challenger.weight ?: return@any false
                    val challengerReps = challenger.reps ?: return@any false
                    val atLeastAsGood = challengerWeight >= candidateWeight && challengerReps >= candidateReps
                    val strictlyBetter = challengerWeight > candidateWeight || challengerReps > candidateReps
                    atLeastAsGood && strictlyBetter
                }
            }
            .map { it.id }
            .toSet()
    }
}
