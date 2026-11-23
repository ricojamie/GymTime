package com.example.gymtime.domain.share

import com.example.gymtime.ai.GeneratedNarrativeRepository
import com.example.gymtime.ai.NarrativeKind
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.util.WorkoutShareFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ShareWorkoutUseCase @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val generatedNarrativeRepository: GeneratedNarrativeRepository
) {

    /**
     * Builds the plain-text share body for the given workout. Returns null if
     * the workout has no completed working sets (nothing meaningful to share).
     */
    suspend operator fun invoke(workoutId: Long): String? {
        return buildShareableWorkout(workoutId)?.let { WorkoutShareFormatter.format(it) }
    }

    suspend fun buildShareableWorkout(workoutId: Long, recap: String? = null): ShareableWorkout? =
        withContext(Dispatchers.IO) {
            val workout = workoutDao.getWorkoutByIdSync(workoutId) ?: return@withContext null
            val completedSets = setDao.getWorkoutSetsWithExercises(workoutId)
                .filter { it.set.isComplete && !it.targetMuscle.isWarmupMuscleGroup() }
            val workingSets = completedSets.filter { !it.set.isWarmup }
            if (workingSets.isEmpty()) return@withContext null

            val workoutStartMs = workout.startTime.time
            val durationMinutes = workout.endTime?.let { end ->
                ((end.time - workoutStartMs) / 1000 / 60).toInt().coerceAtLeast(0)
            }

            // Group preserving the order of first appearance for exercises that have working sets.
            val eligibleExerciseIds = workingSets.mapTo(HashSet()) { it.set.exerciseId }
            val eligibleSets = completedSets.filter { it.set.exerciseId in eligibleExerciseIds }
            val grouped = eligibleSets.groupBy { it.set.exerciseId }
            val orderedExerciseIds = eligibleSets.map { it.set.exerciseId }.distinct()

            val totalVolume = workingSets.sumOf { info ->
                val weight = info.set.weight?.takeIf { it.isFinite() && it > 0f } ?: return@sumOf 0.0
                val reps = info.set.reps?.takeIf { it > 0 } ?: return@sumOf 0.0
                weight.toDouble() * reps
            }.toFloat()

            val exercises = orderedExerciseIds.mapNotNull { exerciseId ->
                val infos = grouped[exerciseId] ?: return@mapNotNull null
                buildExercise(exerciseId, workoutStartMs, infos)
            }

            val effectiveRecap = recap ?: generatedNarrativeRepository
                .getLatest(NarrativeKind.POST_WORKOUT, workoutId.toString())
                ?.text

            ShareableWorkout(
                date = workout.startTime,
                durationMinutes = durationMinutes,
                totalVolume = totalVolume,
                totalWorkingSets = workingSets.size,
                exercises = exercises,
                recap = effectiveRecap?.trim()?.takeIf { it.isNotEmpty() }
            )
        }

    private suspend fun buildExercise(
        exerciseId: Long,
        workoutStartMs: Long,
        infos: List<SetWithExerciseInfo>
    ): ShareableExercise {
        val name = infos.firstOrNull()?.exerciseName.orEmpty()
        val muscle = infos.firstOrNull()?.targetMuscle.orEmpty()
        val hasEligibleWeightedSet = infos.any { info ->
            val s = info.set
            !s.isWarmup &&
                s.isComplete &&
                s.weight?.let { it.isFinite() && it > 0f } == true &&
                (s.reps ?: 0) > 0
        }
        val personalRecordSetIds = if (hasEligibleWeightedSet) {
            detectRepPersonalRecords(exerciseId, workoutStartMs, infos)
        } else {
            emptySet()
        }

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
            .asSequence()
            .filter { it.reps > 0 && it.maxWeight.isFinite() && it.maxWeight > 0f }
            .associate { it.reps to it.maxWeight }
            .toMutableMap()
        val personalRecordSetIds = mutableSetOf<Long>()

        infos.sortedBy { it.set.timestamp.time }.forEach { info ->
            val set = info.set
            if (set.isWarmup || !set.isComplete) return@forEach
            val reps = set.reps?.takeIf { it > 0 } ?: return@forEach
            val weight = set.weight?.takeIf { it.isFinite() && it > 0f } ?: return@forEach

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
            .filter {
                it.id in personalRecordSetIds &&
                    it.weight?.let { w -> w.isFinite() && w > 0f } == true &&
                    (it.reps ?: 0) > 0
            }

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
