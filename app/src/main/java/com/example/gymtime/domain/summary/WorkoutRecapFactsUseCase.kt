package com.example.gymtime.domain.summary

import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.domain.share.ShareWorkoutUseCase
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

    private fun format(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}

class WorkoutRecapFactsUseCase @Inject constructor(
    private val shareWorkoutUseCase: ShareWorkoutUseCase,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao
) {
    suspend operator fun invoke(workoutId: Long): WorkoutRecapFacts? {
        val workout = workoutDao.getWorkoutByIdSync(workoutId) ?: return null
        val shareable = shareWorkoutUseCase.buildShareableWorkout(workoutId) ?: return null
        val currentWorkingSets = shareable.exercises.flatMap { exercise ->
            exercise.sets.filterNot { it.isWarmup }.map { exercise.targetMuscle to it }
        }
        val muscleCounts = currentWorkingSets.groupingBy { it.first }.eachCount()
        val leadingMuscle = muscleCounts.maxWithOrNull(
            compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
        )?.key
        val prExercises = shareable.exercises
            .filter { exercise -> exercise.sets.any { !it.isWarmup && it.isPersonalRecord } }
            .map { it.name }
            .distinct()

        val historyStart = workout.startTime.time - TimeUnit.DAYS.toMillis(42)
        val priorSets = setDao.getSetsWithExerciseInRange(historyStart, workout.startTime.time - 1)
            .filter { !it.set.isWarmup && it.set.isComplete }
        val comparableVolumes = if (leadingMuscle == null) {
            emptyList()
        } else {
            priorSets.groupBy { it.set.workoutId }.mapNotNull { (_, sets) ->
                if (sets.none { it.targetMuscle.equals(leadingMuscle, ignoreCase = true) }) {
                    return@mapNotNull null
                }
                sets.sumOf {
                    ((it.set.weight ?: 0f) * (it.set.reps ?: 0)).toDouble()
                }.toFloat()
            }
        }

        return WorkoutRecapFacts(
            workoutId = workoutId,
            totalVolume = shareable.totalVolume,
            workingSetCount = shareable.totalWorkingSets,
            exerciseCount = shareable.exercises.count { it.sets.any { set -> !set.isWarmup } },
            muscles = shareable.exercises
                .filter { exercise -> exercise.sets.any { set -> !set.isWarmup } }
                .map { it.targetMuscle }
                .distinct()
                .sorted(),
            personalRecordExercises = prExercises,
            leadingMuscle = leadingMuscle,
            isHighestComparableVolumeInSixWeeks = shareable.totalVolume > 0f &&
                comparableVolumes.isNotEmpty() &&
                comparableVolumes.all { shareable.totalVolume > it },
            comparableWorkoutCount = comparableVolumes.size
        )
    }
}
