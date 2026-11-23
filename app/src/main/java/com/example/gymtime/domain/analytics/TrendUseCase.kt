package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.RatedWorkoutSetInfo
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.util.OneRepMaxCalculator
import com.example.gymtime.util.TimeFormatter
import com.example.gymtime.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class TrendMetric(val displayName: String) {
    VOLUME("Volume"),
    SETS("Sets"),
    E1RM("E1RM"),
    AVG_WEIGHT("Avg Weight"),
    DENSITY("Density"),
    REPS("Reps"),
    DURATION("Duration"),
    DISTANCE("Distance"),
    CALORIES("Calories"),
    RATING("Rating"),
    RATED_VOLUME("Rated Volume")
}

enum class TimePeriod(val months: Int?) {
    ONE_MONTH(1), THREE_MONTHS(3), SIX_MONTHS(6), ONE_YEAR(12), ALL(null)
}

enum class AggregateInterval {
    BY_WORKOUT, WEEKLY, MONTHLY
}

data class TrendPoint(
    val label: String,
    val value: Float,
    val date: Date,
    val workoutId: Long? = null
)

data class TrophyPR(
    val exercise: Exercise,
    val weight: Float,
    val reps: Int,
    val date: java.util.Date
)

class TrendUseCase @Inject constructor(
    private val setDao: SetDao,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao
) {
    private var zoneId: ZoneId = ZoneId.systemDefault()
    private var clockMs: () -> Long = System::currentTimeMillis

    internal constructor(
        setDao: SetDao,
        exerciseDao: ExerciseDao,
        workoutDao: WorkoutDao,
        zoneId: ZoneId,
        clockMs: () -> Long = System::currentTimeMillis
    ) : this(setDao, exerciseDao, workoutDao) {
        this.zoneId = zoneId
        this.clockMs = clockMs
    }

    fun getStarredExercises(): Flow<List<Exercise>> {
        return exerciseDao.getStarredExercises()
    }

    suspend fun toggleExerciseStarred(exerciseId: Long, isStarred: Boolean) {
        withContext(Dispatchers.IO) {
            val currentStarred = exerciseDao.getStarredExercises().first()
            if (isStarred && currentStarred.size >= 3) {
                // Should be handled in UI/VM with a message, but safety check here
                return@withContext
            }
            exerciseDao.updateStarredStatus(exerciseId, isStarred)
        }
    }

    suspend fun getTrophyCasePRs(): List<TrophyPR> = withContext(Dispatchers.IO) {
        val starredList = exerciseDao.getStarredExercises().first()

        starredList.mapNotNull { exercise ->
            val pb = setDao.getPersonalBest(exercise.id) ?: return@mapNotNull null
            val weight = pb.weight?.takeIf { it.isFinite() && it > 0f } ?: return@mapNotNull null
            val reps = pb.reps?.takeIf { it > 0 } ?: return@mapNotNull null
            TrophyPR(
                exercise = exercise,
                weight = weight,
                reps = reps,
                date = pb.timestamp
            )
        }
    }

    suspend fun getTrendData(
        metric: TrendMetric,
        period: TimePeriod,
        interval: AggregateInterval,
        muscleGroup: String? = null,
        exerciseId: Long? = null
    ): List<TrendPoint> {
        val endDate = clockMs()
        val startDate = period.months?.let { months ->
            Instant.ofEpochMilli(endDate)
                .atZone(zoneId)
                .minusMonths(months.toLong())
                .toInstant()
                .toEpochMilli()
        } ?: 0L

        if (metric == TrendMetric.RATING || metric == TrendMetric.RATED_VOLUME) {
            val ratedRows = withContext(Dispatchers.IO) {
                workoutDao.getRatedWorkoutSetInfo(startDate, endDate)
            }
            if (ratedRows.isEmpty()) return emptyList()
            return withContext(Dispatchers.Default) {
                buildRatedTrendData(
                    rows = ratedRows,
                    metric = metric,
                    interval = interval,
                    muscleGroup = muscleGroup,
                    exerciseId = exerciseId
                )
            }
        }

        val normalizedMuscle = if (muscleGroup == "All") null else muscleGroup
        val (rawSets, completedWorkoutIds) = withContext(Dispatchers.IO) {
            val fetchedSets = setDao.getSetsWithExerciseInRange(
                startDate = startDate,
                endDate = endDate,
                muscleGroup = normalizedMuscle,
                exerciseId = exerciseId
            )
            if (fetchedSets.isEmpty()) {
                emptyList<SetWithExerciseInfo>() to emptySet<Long>()
            } else {
                val completedIds = workoutDao.getAllWorkoutsSync()
                    .asSequence()
                    .filter { it.endTime != null }
                    .map { it.id }
                    .toHashSet()
                fetchedSets to completedIds
            }
        }

        if (rawSets.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            val sets = rawSets.filter {
                it.set.isComplete &&
                    !it.set.isWarmup &&
                    !it.targetMuscle.isWarmupMuscleGroup() &&
                    it.set.workoutId in completedWorkoutIds
            }
            if (sets.isEmpty()) emptyList() else aggregateSets(sets, metric, interval)
        }
    }

    private fun aggregateSets(
        sets: List<SetWithExerciseInfo>,
        metric: TrendMetric,
        interval: AggregateInterval
    ): List<TrendPoint> {
        return when (interval) {
            AggregateInterval.BY_WORKOUT -> aggregateByWorkout(sets, metric)
            AggregateInterval.WEEKLY -> aggregateByInterval(sets, metric, weekly = true)
            AggregateInterval.MONTHLY -> aggregateByInterval(sets, metric, weekly = false)
        }
    }

    private fun aggregateByWorkout(
        sets: List<SetWithExerciseInfo>,
        metric: TrendMetric
    ): List<TrendPoint> {
        return sets.groupBy { it.set.workoutId }
            .mapNotNull { (workoutId, workoutSets) ->
                val firstSet = workoutSets.minByOrNull { it.set.timestamp.time }
                val date = firstSet?.set?.timestamp ?: return@mapNotNull null

                val value = calculateMetric(workoutSets, metric)
                if (!value.isFinite() || value <= 0f) return@mapNotNull null

                TrendPoint(
                    label = TimeFormatter.formatShortDate(date),
                    value = value,
                    date = date,
                    workoutId = workoutId
                )
            }
            .sortedWith(compareBy<TrendPoint> { it.date.time }.thenBy { it.workoutId ?: Long.MAX_VALUE })
    }

    private fun aggregateByInterval(
        sets: List<SetWithExerciseInfo>,
        metric: TrendMetric,
        weekly: Boolean
    ): List<TrendPoint> {
        val locale = Locale.getDefault()
        val weekFields = WeekFields.of(locale)
        val formatter = DateTimeFormatter.ofPattern(if (weekly) "'W'w" else "MMM yy", locale)

        return sets.groupBy { info ->
            bucketKey(info.set.timestamp.time, weekly, weekFields)
        }
            .mapNotNull { (_, periodSets) ->
                val firstSet = periodSets.minByOrNull { it.set.timestamp.time }
                val date = firstSet?.set?.timestamp ?: return@mapNotNull null

                val value = calculateMetric(periodSets, metric)
                if (!value.isFinite() || value <= 0f) return@mapNotNull null

                val localDate = Instant.ofEpochMilli(date.time).atZone(zoneId).toLocalDate()
                TrendPoint(
                    label = formatter.format(localDate),
                    value = value,
                    date = date
                )
            }
            .sortedBy { it.date.time }
    }

    private fun calculateMetric(sets: List<SetWithExerciseInfo>, metric: TrendMetric): Float {
        val workingSets = sets.filter {
            it.set.isComplete && !it.set.isWarmup && !it.targetMuscle.isWarmupMuscleGroup()
        }
        if (workingSets.isEmpty()) return 0f

        return when (metric) {
            TrendMetric.VOLUME -> {
                workingSets.sumOf { setWithInfo ->
                    val weight = setWithInfo.set.weight?.takeIf { it.isFinite() && it > 0f } ?: return@sumOf 0.0
                    val reps = setWithInfo.set.reps?.takeIf { it > 0 } ?: return@sumOf 0.0
                    weight.toDouble() * reps
                }.toFloat()
            }
            TrendMetric.SETS -> {
                workingSets.size.toFloat()
            }
            TrendMetric.E1RM -> {
                workingSets.maxOfOrNull { setWithInfo ->
                    val w = setWithInfo.set.weight?.takeIf { it.isFinite() && it > 0f } ?: return@maxOfOrNull 0f
                    val r = setWithInfo.set.reps?.takeIf { it in 1..15 } ?: return@maxOfOrNull 0f
                    OneRepMaxCalculator.calculateE1RM(w, r)
                        ?.takeIf { it.isFinite() && it > 0f } ?: 0f
                } ?: 0f
            }
            TrendMetric.AVG_WEIGHT -> {
                var totalVolume = 0.0
                var totalReps = 0L
                for (setWithInfo in workingSets) {
                    val weight = setWithInfo.set.weight?.takeIf { it.isFinite() && it >= 0f } ?: continue
                    val reps = setWithInfo.set.reps?.takeIf { it > 0 } ?: continue
                    totalVolume += weight.toDouble() * reps
                    totalReps += reps
                }
                if (totalReps == 0L) 0f else (totalVolume / totalReps).toFloat()
            }
            TrendMetric.DENSITY -> {
                var totalVol = 0.0
                var strengthCount = 0
                for (setWithInfo in workingSets) {
                    val weight = setWithInfo.set.weight?.takeIf { it.isFinite() && it >= 0f } ?: continue
                    val reps = setWithInfo.set.reps?.takeIf { it > 0 } ?: continue
                    totalVol += weight.toDouble() * reps
                    strengthCount++
                }
                if (strengthCount == 0) 0f else (totalVol / strengthCount).toFloat()
            }
            TrendMetric.REPS -> {
                workingSets.sumOf { it.set.reps?.takeIf { r -> r > 0 } ?: 0 }.toFloat()
            }
            TrendMetric.DURATION -> {
                workingSets.sumOf { (it.set.durationSeconds?.takeIf { d -> d > 0 } ?: 0).toDouble() }
                    .toFloat() / 60f
            }
            TrendMetric.DISTANCE -> {
                calculateDistanceMetric(workingSets)
            }
            TrendMetric.CALORIES -> {
                workingSets.sumOf {
                    it.set.calories?.takeIf { c -> c.isFinite() && c > 0f }?.toDouble() ?: 0.0
                }.toFloat()
            }
            TrendMetric.RATING,
            TrendMetric.RATED_VOLUME -> 0f
        }
    }

    private fun buildRatedTrendData(
        rows: List<RatedWorkoutSetInfo>,
        metric: TrendMetric,
        interval: AggregateInterval,
        muscleGroup: String?,
        exerciseId: Long?
    ): List<TrendPoint> {
        val hasFilter = (muscleGroup != null && muscleGroup != "All") || exerciseId != null
        val snapshots = rows
            .toRatedSnapshots()
            .mapNotNull { snapshot ->
                val working = snapshot.workingRows()
                if (working.isEmpty()) return@mapNotNull null
                if (!hasFilter) {
                    snapshot.copy(cachedWorkingRows = working)
                } else {
                    val filtered = working.filter { row ->
                        val muscleMatches = muscleGroup == null || muscleGroup == "All" ||
                            row.targetMuscle.equals(muscleGroup, ignoreCase = true)
                        val exerciseMatches = exerciseId == null || row.exerciseId == exerciseId
                        muscleMatches && exerciseMatches
                    }
                    if (filtered.isEmpty()) null else snapshot.copy(cachedWorkingRows = filtered)
                }
            }

        if (snapshots.isEmpty()) return emptyList()

        return when (interval) {
            AggregateInterval.BY_WORKOUT -> aggregateRatedByWorkout(snapshots, metric)
            AggregateInterval.WEEKLY -> aggregateRatedByInterval(snapshots, metric, weekly = true)
            AggregateInterval.MONTHLY -> aggregateRatedByInterval(snapshots, metric, weekly = false)
        }
    }

    private fun aggregateRatedByWorkout(
        snapshots: List<TrendRatedSnapshot>,
        metric: TrendMetric
    ): List<TrendPoint> {
        return snapshots.mapNotNull { snapshot ->
            val value = snapshot.ratedMetric(metric)
            if (!value.isFinite() || value <= 0f) return@mapNotNull null
            TrendPoint(
                label = TimeFormatter.formatShortDate(snapshot.startTime),
                value = value,
                date = snapshot.startTime,
                workoutId = snapshot.workoutId
            )
        }.sortedWith(compareBy<TrendPoint> { it.date.time }.thenBy { it.workoutId ?: Long.MAX_VALUE })
    }

    private fun aggregateRatedByInterval(
        snapshots: List<TrendRatedSnapshot>,
        metric: TrendMetric,
        weekly: Boolean
    ): List<TrendPoint> {
        val locale = Locale.getDefault()
        val weekFields = WeekFields.of(locale)
        val formatter = DateTimeFormatter.ofPattern(if (weekly) "'W'w" else "MMM yy", locale)

        return snapshots.groupBy { snapshot ->
            bucketKey(snapshot.startTime.time, weekly, weekFields)
        }.mapNotNull { (_, bucket) ->
            val first = bucket.minByOrNull { it.startTime.time } ?: return@mapNotNull null
            val value = when (metric) {
                TrendMetric.RATING -> bucket.map { it.rating }.average().toFloat()
                TrendMetric.RATED_VOLUME -> bucket.sumOf { it.ratedVolume().toDouble() }.toFloat()
                else -> 0f
            }
            if (!value.isFinite() || value <= 0f) return@mapNotNull null
            val localDate = Instant.ofEpochMilli(first.startTime.time).atZone(zoneId).toLocalDate()
            TrendPoint(
                label = formatter.format(localDate),
                value = value,
                date = first.startTime
            )
        }.sortedBy { it.date.time }
    }

    private fun bucketKey(timestampMs: Long, weekly: Boolean, weekFields: WeekFields): Pair<Int, Int> {
        val localDate = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDate()
        return if (weekly) {
            localDate.get(weekFields.weekBasedYear()) to localDate.get(weekFields.weekOfWeekBasedYear())
        } else {
            localDate.year to localDate.monthValue
        }
    }

    /**
     * Distance trends are safe when the bucket has a single unit.
     * If multiple convertible units are mixed, normalize them to miles.
     * If mixed with non-convertible units like steps/floors, skip the bucket to avoid nonsense totals.
     */
    private fun calculateDistanceMetric(sets: List<SetWithExerciseInfo>): Float {
        val distanceEntries = sets.mapNotNull { setWithInfo ->
            val set = setWithInfo.set
            when {
                set.distanceValue != null && set.distanceUnit != null -> {
                    val value = set.distanceValue.takeIf { it.isFinite() && it > 0f } ?: return@mapNotNull null
                    value to set.distanceUnit
                }
                set.distanceMeters != null -> {
                    val value = set.distanceMeters.takeIf { it.isFinite() && it > 0f } ?: return@mapNotNull null
                    value to DistanceUnit.METERS
                }
                else -> null
            }
        }

        if (distanceEntries.isEmpty()) return 0f

        val distinctUnits = distanceEntries.map { it.second }.distinct()
        if (distinctUnits.size == 1) {
            return distanceEntries.sumOf { it.first.toDouble() }.toFloat()
        }

        if (distinctUnits.all { it.isConvertibleToMeters }) {
            val totalMeters = distanceEntries.sumOf { (value, unit) ->
                TimeUtils.distanceToMeters(value, unit)
                    ?.takeIf { it.isFinite() && it > 0f }
                    ?.toDouble() ?: 0.0
            }
            return (totalMeters / 1609.344).toFloat()
        }

        return 0f
    }
}

private data class TrendRatedSnapshot(
    val workoutId: Long,
    val startTime: Date,
    val rating: Int,
    val rows: List<RatedWorkoutSetInfo>,
    val cachedWorkingRows: List<RatedWorkoutSetInfo>? = null
) {
    fun workingRows(): List<RatedWorkoutSetInfo> {
        return cachedWorkingRows ?: rows.filter {
            it.endTime != null &&
                it.setId != null &&
                it.isWarmup != true &&
                it.targetMuscle?.isWarmupMuscleGroup() != true
        }
    }

    fun ratedMetric(metric: TrendMetric): Float {
        return when (metric) {
            TrendMetric.RATING -> rating.toFloat()
            TrendMetric.RATED_VOLUME -> ratedVolume()
            else -> 0f
        }
    }

    fun ratedVolume(): Float = volume() * (rating / 5f)

    private fun volume(): Float {
        return workingRows().sumOf { row ->
            val weight = row.weight?.takeIf { it.isFinite() && it > 0f } ?: return@sumOf 0.0
            val reps = row.reps?.takeIf { it > 0 } ?: return@sumOf 0.0
            weight.toDouble() * reps
        }.toFloat()
    }
}

private fun List<RatedWorkoutSetInfo>.toRatedSnapshots(): List<TrendRatedSnapshot> {
    return groupBy { it.workoutId }
        .mapNotNull { (_, rows) ->
            val first = rows.firstOrNull() ?: return@mapNotNull null
            if (first.endTime == null) return@mapNotNull null
            TrendRatedSnapshot(
                workoutId = first.workoutId,
                startTime = first.startTime,
                rating = first.rating,
                rows = rows
            )
        }
        .sortedWith(compareBy<TrendRatedSnapshot> { it.startTime.time }.thenBy { it.workoutId })
}
