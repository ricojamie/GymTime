package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class TrendMetric {
    VOLUME, E1RM, AVG_WEIGHT, DENSITY, REPS
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
                    date = pb.timestamp ?: java.util.Date()
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
                val firstSet = workoutSets.minByOrNull { it.set.timestamp?.time ?: 0L }
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
            val cal = Calendar.getInstance().apply { time = it.set.timestamp ?: Date() }
            val year = cal.get(Calendar.YEAR)
            val period = cal.get(calendarField)
            "$year-$period"
        }
        .mapNotNull { (_, periodSets) ->
            val firstSet = periodSets.minByOrNull { it.set.timestamp?.time ?: 0L }
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
        return when (metric) {
            TrendMetric.VOLUME -> {
                sets.sumOf { (it.set.weight?.toDouble() ?: 0.0) * (it.set.reps ?: 0) }.toFloat()
            }
            TrendMetric.E1RM -> {
                sets.maxOfOrNull { 
                    val w = it.set.weight ?: 0f
                    val r = it.set.reps ?: 0
                    if (r == 0) 0f else w * (1 + 0.0333f * r) 
                } ?: 0f
            }
            TrendMetric.AVG_WEIGHT -> {
                val totalReps = sets.sumOf { it.set.reps ?: 0 }
                if (totalReps == 0) 0f 
                else (sets.sumOf { (it.set.weight?.toDouble() ?: 0.0) * (it.set.reps ?: 0) } / totalReps).toFloat()
            }
            TrendMetric.DENSITY -> {
                 // Volume per set (simplified density for now without reliable workout duration here)
                val totalVol = sets.sumOf { (it.set.weight?.toDouble() ?: 0.0) * (it.set.reps ?: 0) }
                if (sets.isEmpty()) 0f else (totalVol / sets.size).toFloat()
            }
            TrendMetric.REPS -> {
                sets.sumOf { it.set.reps ?: 0 }.toFloat()
            }
        }
    }
}
