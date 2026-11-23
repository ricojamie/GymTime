package com.example.gymtime.domain.summary

import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.domain.share.ShareWorkoutUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class WorkoutRecapFacts(
    val workoutId: Long,
    val totalVolume: Float,
    val workingSetCount: Int,
    val exerciseCount: Int,
    val muscles: List<String>,
    val personalRecordExercises: List<String>,
    val leadingMuscle: String?,
    val isHighestComparableVolumeInSixWeeks: Boolean,
    val comparableWorkoutCount: Int
) {
    fun canonicalFacts(): String = buildString {
        appendLine("workout_id=$workoutId")
        appendLine("total_volume_lb=${format(totalVolume)}")
        appendLine("working_sets=$workingSetCount")
        appendLine("exercise_count=$exerciseCount")
        appendLine("muscles=${muscles.joinToString("|")}")
        appendLine("pr_exercises=${personalRecordExercises.joinToString("|")}")
        appendLine("leading_muscle=${leadingMuscle.orEmpty()}")
        appendLine("highest_comparable_volume_in_6_weeks=$isHighestComparableVolumeInSixWeeks")
        append("comparable_workouts=$comparableWorkoutCount")
    }

    fun templateNarrative(): String = when {
        personalRecordExercises.isNotEmpty() && isHighestComparableVolumeInSixWeeks -> {
            val prs = personalRecordExercises.take(2).joinToString(" and ")
            "PR on $prs, plus your highest-volume ${leadingMuscle.orEmpty()} session in six weeks."
        }
        personalRecordExercises.isNotEmpty() -> {
            "PR on ${personalRecordExercises.take(2).joinToString(" and ")} during a $workingSetCount-set session."
        }
        isHighestComparableVolumeInSixWeeks -> {
            "Your highest-volume ${leadingMuscle.orEmpty()} session in six weeks: ${format(totalVolume)} lb across $workingSetCount working sets."
        }
        totalVolume > 0f -> "Logged $workingSetCount working sets and ${format(totalVolume)} lb across $exerciseCount exercises."
        else -> "Logged $workingSetCount working sets across $exerciseCount exercises."
    }

    private fun format(value: Float): String {
        val safe = if (value.isFinite()) value else 0f
        return if (safe % 1f == 0f) safe.toInt().toString() else String.format(Locale.US, "%.1f", safe)
    }
}

class WorkoutRecapFactsUseCase @Inject constructor(
    private val shareWorkoutUseCase: ShareWorkoutUseCase,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao
) {
    suspend operator fun invoke(workoutId: Long): WorkoutRecapFacts? = withContext(Dispatchers.IO) {
        val workout = workoutDao.getWorkoutByIdSync(workoutId) ?: return@withContext null
        val shareable = shareWorkoutUseCase.buildShareableWorkout(workoutId) ?: return@withContext null
        if (shareable.totalWorkingSets <= 0) return@withContext null

        val currentWorkingSets = shareable.exercises
            .filterNot { it.targetMuscle.isWarmupMuscleGroup() }
            .flatMap { exercise ->
                exercise.sets.filterNot { it.isWarmup }.map { exercise.targetMuscle to it }
            }
        if (currentWorkingSets.isEmpty()) return@withContext null

        val muscleCounts = currentWorkingSets.groupingBy { it.first }.eachCount()
        val leadingMuscle = muscleCounts.maxWithOrNull(
            compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
        )?.key
        val prExercises = shareable.exercises
            .filter { exercise -> exercise.sets.any { !it.isWarmup && it.isPersonalRecord } }
            .map { it.name }
            .distinct()

        val currentLeadingMuscleVolume = if (leadingMuscle == null) {
            0f
        } else {
            currentWorkingSets.sumOf { (muscle, set) ->
                if (!muscle.equals(leadingMuscle, ignoreCase = true)) return@sumOf 0.0
                val weight = set.weight?.takeIf { it.isFinite() && it > 0f } ?: return@sumOf 0.0
                val reps = set.reps?.takeIf { it > 0 } ?: return@sumOf 0.0
                weight.toDouble() * reps
            }.toFloat()
        }

        val comparableVolumes = if (leadingMuscle == null || currentLeadingMuscleVolume <= 0f) {
            emptyList()
        } else {
            val historyStart = workout.startTime.time - TimeUnit.DAYS.toMillis(42)
            val priorSets = setDao.getPerformanceSetsWithExerciseInRange(historyStart, workout.startTime.time - 1)
                .filter {
                    it.set.workoutId != workoutId &&
                        it.set.isComplete &&
                        !it.set.isWarmup &&
                        !it.targetMuscle.isWarmupMuscleGroup() &&
                        it.targetMuscle.equals(leadingMuscle, ignoreCase = true)
                }
            priorSets.groupBy { it.set.workoutId }.mapNotNull { (_, sets) ->
                val volume = sets.sumOf { row ->
                    val weight = row.set.weight?.takeIf { it.isFinite() && it > 0f } ?: return@sumOf 0.0
                    val reps = row.set.reps?.takeIf { it > 0 } ?: return@sumOf 0.0
                    weight.toDouble() * reps
                }.toFloat()
                volume.takeIf { it.isFinite() && it > 0f }
            }
        }

        val eligibleExercises = shareable.exercises.filter { exercise ->
            !exercise.targetMuscle.isWarmupMuscleGroup() && exercise.sets.any { set -> !set.isWarmup }
        }

        WorkoutRecapFacts(
            workoutId = workoutId,
            totalVolume = shareable.totalVolume,
            workingSetCount = shareable.totalWorkingSets,
            exerciseCount = eligibleExercises.size,
            muscles = eligibleExercises
                .map { it.targetMuscle }
                .distinct()
                .sorted(),
            personalRecordExercises = prExercises,
            leadingMuscle = leadingMuscle,
            isHighestComparableVolumeInSixWeeks = shareable.totalVolume > 0f &&
                currentLeadingMuscleVolume > 0f &&
                comparableVolumes.isNotEmpty() &&
                comparableVolumes.all { currentLeadingMuscleVolume > it },
            comparableWorkoutCount = comparableVolumes.size
        )
    }
}
