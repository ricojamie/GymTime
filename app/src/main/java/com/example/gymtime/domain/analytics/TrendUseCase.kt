package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
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
    CALORIES("Calories")
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
    fun getStarredExercises(): Flow<List<Exercise>> {
        return exerciseDao.getStarredExercises()
    }

    suspend fun toggleExerciseStarred(exerciseId: Long, isStarred: Boolean) {
        val currentStarred = exerciseDao.getStarredExercises().first()
        if (isStarred && currentStarred.size >= 3) {
            // Should be handled in UI/VM with a message, but safety check here
            return
        }
        exerciseDao.updateStarredStatus(exerciseId, isStarred)
    }

    suspend fun getTrophyCasePRs(): List<TrophyPR> {
        val starredList = exerciseDao.getStarredExercises().first()
        
        return starredList.mapNotNull { exercise ->
            val pb = setDao.getPersonalBest(exercise.id)
            if (pb != null) {
                TrophyPR(
                    exercise = exercise,
                    weight = pb.weight ?: 0f,
                    reps = pb.reps ?: 0,
                    date = pb.timestamp
                )
            } else null
        }
    }

    suspend fun getTrendData(
        metric: TrendMetric,
        period: TimePeriod,
        interval: AggregateInterval,
        muscleGroup: String? = null,
        exerciseId: Long? = null
    ): List<TrendPoint> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis
        val startDate = period.months?.let {
            calendar.add(Calendar.MONTH, -it)
            calendar.timeInMillis
        } ?: 0L

        val sets = setDao.getSetsWithExerciseInRange(
            startDate = startDate,
            endDate = endDate,
            muscleGroup = if (muscleGroup == "All") null else muscleGroup,
            exerciseId = exerciseId
        )

        if (sets.isEmpty()) return emptyList()

        return aggregateSets(sets, metric, interval)
    }

    private fun aggregateSets(
        sets: List<SetWithExerciseInfo>,
        metric: TrendMetric,
        interval: AggregateInterval
    ): List<TrendPoint> {
        return when (interval) {
            AggregateInterval.BY_WORKOUT -> aggregateByWorkout(sets, metric)
            AggregateInterval.WEEKLY -> aggregateByInterval(sets, metric, Calendar.WEEK_OF_YEAR)
            AggregateInterval.MONTHLY -> aggregateByInterval(sets, metric, Calendar.MONTH)
        }
    }

    private fun aggregateByWorkout(
        sets: List<SetWithExerciseInfo>,
        metric: TrendMetric
    ): List<TrendPoint> {
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        return sets.groupBy { it.set.workoutId }
            .mapNotNull { (workoutId, workoutSets) ->
                val firstSet = workoutSets.minByOrNull { it.set.timestamp.time }
                val date = firstSet?.set?.timestamp ?: return@mapNotNull null
                
                val value = calculateMetric(workoutSets, metric)
                if (value == 0f) return@mapNotNull null

                TrendPoint(
                    label = sdf.format(date),
                    value = value,
                    date = date,
                    workoutId = workoutId
                )
            }
            .sortedBy { it.date }
    }

    private fun aggregateByInterval(
        sets: List<SetWithExerciseInfo>,
        metric: TrendMetric,
        calendarField: Int
    ): List<TrendPoint> {
        val sdf = if (calendarField == Calendar.WEEK_OF_YEAR) {
            SimpleDateFormat("'W'w", Locale.getDefault())
        } else {
            SimpleDateFormat("MMM yy", Locale.getDefault())
        }

        return sets.groupBy {
            val cal = Calendar.getInstance().apply { time = it.set.timestamp }
            val year = cal.get(Calendar.YEAR)
            val period = cal.get(calendarField)
            "$year-$period"
        }
        .mapNotNull { (_, periodSets) ->
            val firstSet = periodSets.minByOrNull { it.set.timestamp.time }
            val date = firstSet?.set?.timestamp ?: return@mapNotNull null
            
            val value = calculateMetric(periodSets, metric)
            if (value == 0f) return@mapNotNull null

            TrendPoint(
                label = sdf.format(date),
                value = value,
                date = date
            )
        }
        .sortedBy { it.date }
    }

    private fun calculateMetric(sets: List<SetWithExerciseInfo>, metric: TrendMetric): Float {
        val workingSets = sets.filter { !it.set.isWarmup }

        return when (metric) {
            TrendMetric.VOLUME -> {
                workingSets.sumOf { setWithInfo ->
                    val weight = setWithInfo.set.weight ?: return@sumOf 0.0
                    val reps = setWithInfo.set.reps ?: return@sumOf 0.0
                    weight.toDouble() * reps
                }.toFloat()
            }
            TrendMetric.SETS -> {
                workingSets.size.toFloat()
            }
            TrendMetric.E1RM -> {
                workingSets.maxOfOrNull {
                    val w = it.set.weight ?: 0f
                    val r = it.set.reps ?: 0
                    if (r == 0) 0f else w * (1 + 0.0333f * r) 
                } ?: 0f
            }
            TrendMetric.AVG_WEIGHT -> {
                val totalReps = workingSets.sumOf { it.set.reps ?: 0 }
                if (totalReps == 0) 0f 
                else (
                    workingSets.sumOf { setWithInfo ->
                        val weight = setWithInfo.set.weight ?: return@sumOf 0.0
                        val reps = setWithInfo.set.reps ?: return@sumOf 0.0
                        weight.toDouble() * reps
                    } / totalReps
                ).toFloat()
            }
            TrendMetric.DENSITY -> {
                // Volume per strength set.
                val strengthSets = workingSets.filter { it.set.weight != null && it.set.reps != null }
                val totalVol = strengthSets.sumOf { setWithInfo ->
                    val weight = setWithInfo.set.weight ?: return@sumOf 0.0
                    val reps = setWithInfo.set.reps ?: return@sumOf 0.0
                    weight.toDouble() * reps
                }
                if (strengthSets.isEmpty()) 0f else (totalVol / strengthSets.size).toFloat()
            }
            TrendMetric.REPS -> {
                workingSets.sumOf { it.set.reps ?: 0 }.toFloat()
            }
            TrendMetric.DURATION -> {
                workingSets.sumOf { it.set.durationSeconds ?: 0 }.toFloat() / 60f
            }
            TrendMetric.DISTANCE -> {
                calculateDistanceMetric(workingSets)
            }
            TrendMetric.CALORIES -> {
                workingSets.sumOf { it.set.calories?.toDouble() ?: 0.0 }.toFloat()
            }
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
                set.distanceValue != null && set.distanceUnit != null -> set.distanceValue to set.distanceUnit
                set.distanceMeters != null -> set.distanceMeters to DistanceUnit.METERS
                else -> null
            }
        }

        if (distanceEntries.isEmpty()) return 0f

        val distinctUnits = distanceEntries.map { it.second }.distinct()
        if (distinctUnits.size == 1) {
            return distanceEntries.sumOf { it.first.toDouble() }.toFloat()
        }

        if (distinctUnits.all { it.isConvertibleToMeters }) {
            return distanceEntries.sumOf { (value, unit) ->
                TimeUtils.distanceToMeters(value, unit)?.toDouble() ?: 0.0
            }.toFloat() / 1609.344f
        }

        return 0f
    }
}
