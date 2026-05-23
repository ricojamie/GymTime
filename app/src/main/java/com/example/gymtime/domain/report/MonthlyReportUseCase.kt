package com.example.gymtime.domain.report

import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class MonthlyReportUseCase @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao
) {

    /**
     * Builds a [MonthlyReport] for the calendar month immediately preceding [reference].
     * If [reference] is May 2026, the report covers April 1 — April 30 (inclusive).
     */
    suspend operator fun invoke(reference: Date = Date()): MonthlyReport {
        val cal = Calendar.getInstance().apply { time = reference }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // Roll back one month to land in the previous calendar month.
        cal.add(Calendar.MONTH, -1)
        val periodStart = cal.time
        val periodStartMs = periodStart.time

        cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        val periodEnd = cal.time
        val periodEndMs = periodEnd.time

        val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(periodStart)

        val workouts = workoutDao.getAllWorkoutsSync()
            .filter { it.startTime.time in periodStartMs..periodEndMs && it.endTime != null }

        val ratings = workouts.mapNotNull { it.rating }
        val averageRating = if (ratings.isNotEmpty()) ratings.average().toFloat() else null

        val muscleSetCounts = workoutDao.getMuscleSetCountsInRange(periodStartMs, periodEndMs)
        val topMuscles = muscleSetCounts.take(5).map { MuscleTotal(it.muscle, it.setVolume) }
        val undertrained = muscleSetCounts.filter { it.setVolume < UNDERTRAINED_SET_THRESHOLD }
            .map { it.muscle }
            .take(3)

        val periodSets = setDao.getSetsWithExerciseInRange(periodStartMs, periodEndMs)
        val workingSets = periodSets.filter { !it.set.isWarmup }
        val totalVolume = workingSets.sumOf { info ->
            ((info.set.weight ?: 0f) * (info.set.reps ?: 0)).toDouble()
        }.toFloat()
        val exerciseCount = workingSets.map { it.set.exerciseId }.distinct().size

        // PR detection per exercise: the heaviest working set in the period that
        // beats the all-time best from before the period.
        val newPRs = mutableListOf<MonthlyPR>()
        val byExercise = workingSets
            .filter { it.set.weight != null }
            .groupBy { it.set.exerciseId }
        for ((exerciseId, sets) in byExercise) {
            val heaviest = sets.maxByOrNull { it.set.weight ?: 0f } ?: continue
            val priorBest = setDao.getPersonalBestBefore(exerciseId, periodStartMs)?.weight
            val w = heaviest.set.weight ?: continue
            if (priorBest == null || w > priorBest) {
                newPRs += MonthlyPR(
                    exerciseName = heaviest.exerciseName,
                    weight = w,
                    reps = heaviest.set.reps ?: 0
                )
            }
        }
        newPRs.sortByDescending { it.weight }

        return MonthlyReport(
            periodStart = periodStart,
            periodEnd = periodEnd,
            monthLabel = monthLabel,
            workoutCount = workouts.size,
            totalVolume = totalVolume,
            totalWorkingSets = workingSets.size,
            exerciseCount = exerciseCount,
            topMuscles = topMuscles,
            undertrainedMuscles = undertrained,
            newPRs = newPRs,
            averageRating = averageRating
        )
    }

    companion object {
        // Muscles with fewer than this many sets in the month are flagged as
        // "could use more attention". Calibrated against typical session volume.
        private const val UNDERTRAINED_SET_THRESHOLD = 6
    }
}
