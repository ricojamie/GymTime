package com.example.gymtime.domain.report

import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.util.StreakCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

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
    suspend operator fun invoke(reference: Date = Date()): MonthlyReport {
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
        val workouts = allWorkouts
            .filter { it.startTime.time in periodStartMs..periodEndMs && it.endTime != null }
        val previousWorkouts = allWorkouts.filter {
            it.startTime.time in previousStartMs..previousEndMs && it.endTime != null
        }

        val ratings = workouts.mapNotNull { it.rating }
        val averageRating = if (ratings.isNotEmpty()) ratings.average().toFloat() else null

        val muscleSetCounts = workoutDao.getMuscleSetCountsInRange(periodStartMs, periodEndMs)
        val topMuscles = muscleSetCounts.take(5).map { MuscleTotal(it.muscle, it.setVolume) }
        val undertrained = muscleSetCounts.filter { it.setVolume < UNDERTRAINED_SET_THRESHOLD }
            .map { it.muscle }
            .take(3)

        val periodSets = setDao.getSetsWithExerciseInRange(periodStartMs, periodEndMs)
        val workingSets = periodSets.filter { !it.set.isWarmup }
        val completedWorkingSets = workingSets.filter { it.set.isComplete }
        val totalVolume = workingSets.sumOf { info ->
            ((info.set.weight ?: 0f) * (info.set.reps ?: 0)).toDouble()
        }.toFloat()
        val exerciseCount = workingSets.map { it.set.exerciseId }.distinct().size

        val previousWorkingSets = setDao
            .getSetsWithExerciseInRange(previousStartMs, previousEndMs)
            .filter { !it.set.isWarmup }
        val previousTotalVolume = previousWorkingSets.sumOf { info ->
            ((info.set.weight ?: 0f) * (info.set.reps ?: 0)).toDouble()
        }.toFloat()

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
            previousWorkoutCount = previousWorkouts.size,
            previousTotalVolume = previousTotalVolume,
            previousWorkingSets = previousWorkingSets.size,
            volumeChangePercent = percentChange(totalVolume, previousTotalVolume),
            workingSetChangePercent = percentChange(workingSets.size.toFloat(), previousWorkingSets.size.toFloat()),
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

    private fun percentChange(current: Float, previous: Float): Float? {
        if (previous <= 0f) return null
        return (((current - previous) / previous) * 1000f).roundToInt() / 10f
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
