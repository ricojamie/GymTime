package com.example.gymtime.domain.report

import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.util.StreakCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToLong

class MonthlyReportUseCase @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private var zoneId: ZoneId = ZoneId.systemDefault()

    internal constructor(
        workoutDao: WorkoutDao,
        setDao: SetDao,
        userPreferencesRepository: UserPreferencesRepository,
        zoneId: ZoneId
    ) : this(workoutDao, setDao, userPreferencesRepository) {
        this.zoneId = zoneId
    }

    /**
     * Builds a [MonthlyReport] for the calendar month immediately preceding [reference].
     * If [reference] is May 2026, the report covers April 1 - April 30 (inclusive).
     */
    suspend operator fun invoke(reference: Date = Date()): MonthlyReport = withContext(Dispatchers.IO) {
        val referenceDate = reference.toInstant().atZone(zoneId).toLocalDate()
        val periodStartDate = referenceDate.withDayOfMonth(1).minusMonths(1)
        val nextMonthStartDate = periodStartDate.plusMonths(1)
        val periodStartMs = periodStartDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val periodEndMs = nextMonthStartDate.atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        val periodStart = Date.from(Instant.ofEpochMilli(periodStartMs))
        val periodEnd = Date.from(Instant.ofEpochMilli(periodEndMs))

        val previousStartMs = periodStartDate.minusMonths(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val previousEndMs = periodStartMs - 1

        val monthLabel = periodStartDate.format(
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        )

        val allWorkouts = workoutDao.getAllWorkoutsSync()
        val completedWorkoutIds = allWorkouts
            .asSequence()
            .filter { it.endTime != null }
            .map { it.id }
            .toHashSet()

        val completedWorkingSets = setDao.getSetsWithExerciseInRange(periodStartMs, periodEndMs)
            .filter {
                it.set.isComplete &&
                    !it.set.isWarmup &&
                    !it.targetMuscle.isWarmupMuscleGroup() &&
                    it.set.workoutId in completedWorkoutIds
            }
        val previousCompletedWorkingSets = setDao
            .getSetsWithExerciseInRange(previousStartMs, previousEndMs)
            .filter {
                it.set.isComplete &&
                    !it.set.isWarmup &&
                    !it.targetMuscle.isWarmupMuscleGroup() &&
                    it.set.workoutId in completedWorkoutIds
            }

        val workoutIdsWithWorkingSets = completedWorkingSets.mapTo(mutableSetOf()) { it.set.workoutId }
        val previousWorkoutIdsWithWorkingSets = previousCompletedWorkingSets
            .mapTo(mutableSetOf()) { it.set.workoutId }
        val workouts = allWorkouts
            .filter {
                it.startTime.time in periodStartMs..periodEndMs &&
                    it.endTime != null &&
                    it.id in workoutIdsWithWorkingSets
            }
        val previousWorkouts = allWorkouts.filter {
            it.startTime.time in previousStartMs..previousEndMs &&
                it.endTime != null &&
                it.id in previousWorkoutIdsWithWorkingSets
        }

        val ratings = workouts.mapNotNull { it.rating }
        val averageRating = if (ratings.isNotEmpty()) ratings.average().toFloat() else null

        val muscleSetCounts = workoutDao.getMuscleSetCountsInRange(periodStartMs, periodEndMs)
            .filterNot { it.muscle.isWarmupMuscleGroup() }
        val topMuscles = muscleSetCounts.take(5).map { MuscleTotal(it.muscle, it.setVolume) }
        val undertrained = muscleSetCounts.filter { it.setVolume < UNDERTRAINED_SET_THRESHOLD }
            .map { it.muscle }
            .take(3)

        val totalVolume = sumFiniteVolume(completedWorkingSets)
        val exerciseCount = completedWorkingSets.map { it.set.exerciseId }.distinct().size
        val previousTotalVolume = sumFiniteVolume(previousCompletedWorkingSets)

        val trainingDays = completedWorkingSets
            .map { localDateOf(it.set.timestamp.time) }
            .distinct()
            .size
        val activeWeeks = completedWorkingSets
            .map { weekKey(it.set.timestamp.time) }
            .distinct()
            .size
        val weeksInPeriod = countCalendarWeeks(periodStartDate, localDateOf(periodEndMs))

        val periodEndDate = localDateOf(periodEndMs)
        val workoutDatesThroughPeriod = workoutDao.getWorkoutDatesWithWorkingSets()
            .mapNotNull(::parseLocalDate)
            .filterNot { it.isAfter(periodEndDate) }
            .map { localDate ->
                Date.from(localDate.atStartOfDay(zoneId).toInstant())
            }
        val endOfMonthStreakDays = StreakCalculator.calculateStreak(
            workoutDates = workoutDatesThroughPeriod,
            allowedSkipsPerWeek = userPreferencesRepository.restDaysPerWeek.first(),
            asOf = periodEnd
        ).streakDays

        // PR detection per exercise: the heaviest completed working set in the period
        // with finite positive weight and reps that beats the prior all-time best.
        val newPRs = mutableListOf<MonthlyPR>()
        val byExercise = completedWorkingSets
            .filter { info ->
                val weight = info.set.weight
                val reps = info.set.reps
                weight != null && weight.isFinite() && weight > 0f && reps != null && reps > 0
            }
            .groupBy { it.set.exerciseId }
        for ((exerciseId, sets) in byExercise) {
            val heaviest = sets.maxWithOrNull(
                compareBy<SetWithExerciseInfo> { it.set.weight!! }
                    .thenBy { it.set.reps!! }
            ) ?: continue
            val w = heaviest.set.weight ?: continue
            val r = heaviest.set.reps ?: continue
            val priorBest = setDao.getPersonalBestBefore(exerciseId, periodStartMs)
                ?.weight
                ?.takeIf { it.isFinite() && it > 0f }
            if (priorBest == null || w > priorBest) {
                newPRs += MonthlyPR(
                    exerciseName = heaviest.exerciseName,
                    weight = w,
                    reps = r
                )
            }
        }
        newPRs.sortWith(compareByDescending<MonthlyPR> { it.weight }.thenByDescending { it.reps })

        MonthlyReport(
            periodStart = periodStart,
            periodEnd = periodEnd,
            monthLabel = monthLabel,
            workoutCount = workouts.size,
            totalVolume = totalVolume,
            totalWorkingSets = completedWorkingSets.size,
            exerciseCount = exerciseCount,
            previousWorkoutCount = previousWorkouts.size,
            previousTotalVolume = previousTotalVolume,
            previousWorkingSets = previousCompletedWorkingSets.size,
            volumeChangePercent = percentChange(totalVolume, previousTotalVolume),
            workingSetChangePercent = percentChange(
                completedWorkingSets.size.toFloat(),
                previousCompletedWorkingSets.size.toFloat()
            ),
            trainingDays = trainingDays,
            activeWeeks = activeWeeks,
            weeksInPeriod = weeksInPeriod,
            endOfMonthStreakDays = endOfMonthStreakDays,
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

    private fun sumFiniteVolume(sets: List<SetWithExerciseInfo>): Float {
        return sets.sumOf { info ->
            val weight = info.set.weight?.takeIf { it.isFinite() && it > 0f } ?: return@sumOf 0.0
            val reps = info.set.reps?.takeIf { it > 0 } ?: return@sumOf 0.0
            weight.toDouble() * reps
        }.toFloat()
    }

    private fun percentChange(current: Float, previous: Float): Float? {
        if (!current.isFinite() || !previous.isFinite() || previous <= 0f) return null
        val scaled = (((current.toDouble() - previous.toDouble()) / previous.toDouble()) * 1000.0)
        if (!scaled.isFinite()) return null
        return (scaled.roundToLong() / 10.0).toFloat()
    }

    private fun localDateOf(timestampMs: Long): LocalDate {
        return Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDate()
    }

    private fun parseLocalDate(value: String): LocalDate? {
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }

    private fun weekKey(timestampMs: Long): Pair<Int, Int> {
        val localDate = localDateOf(timestampMs)
        val weekFields = WeekFields.of(Locale.getDefault())
        return localDate.get(weekFields.weekBasedYear()) to localDate.get(weekFields.weekOfWeekBasedYear())
    }

    private fun countCalendarWeeks(start: LocalDate, end: LocalDate): Int {
        val weekFields = WeekFields.of(Locale.getDefault())
        val weeks = mutableSetOf<Pair<Int, Int>>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            weeks += cursor.get(weekFields.weekBasedYear()) to cursor.get(weekFields.weekOfWeekBasedYear())
            cursor = cursor.plusDays(1)
        }
        return weeks.size
    }
}
