package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.RatedWorkoutSetInfo
import com.example.gymtime.data.db.dao.WorkoutDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class WorkoutRatingStats(
    val weekAverageRating: Float?,
    val monthAverageRating: Float?,
    val weekRatedVolume: Float,
    val monthRatedVolume: Float,
    val ratedWorkoutCount: Int,
    val weeklyTrend: List<RatedVolumePoint>,
    val topMuscles: List<MuscleRatingSummary>,
    val lowMuscles: List<MuscleRatingSummary>
)

data class MuscleRatingSummary(
    val muscle: String,
    val averageRating: Float,
    val ratedVolume: Float,
    val workingSets: Int
)

data class RatedVolumePoint(
    val label: String,
    val averageRating: Float,
    val ratedVolume: Float,
    val date: Date
)

class WorkoutRatingUseCase @Inject constructor(
    private val workoutDao: WorkoutDao
) {
    suspend fun getRatingStats(nowMs: Long = System.currentTimeMillis()): WorkoutRatingStats =
        withContext(Dispatchers.IO) {
            val startOfWeek = startOfCurrentWeek(nowMs)
            val startOfMonth = startOfCurrentMonth(nowMs)
            val trendStart = startOfWeek - WEEKS_FOR_TREND * WEEK_MS
            val rows = workoutDao.getRatedWorkoutSetInfo(trendStart, nowMs)
            val workouts = rows.toSnapshots()

            val weekWorkouts = workouts.filter { it.startTime.time >= startOfWeek }
            val monthWorkouts = workouts.filter { it.startTime.time >= startOfMonth }
            val monthMuscles = buildMuscleSummaries(monthWorkouts)

            WorkoutRatingStats(
                weekAverageRating = weekWorkouts.averageRating(),
                monthAverageRating = monthWorkouts.averageRating(),
                weekRatedVolume = weekWorkouts.sumOf { it.ratedVolume().toDouble() }.toFloat(),
                monthRatedVolume = monthWorkouts.sumOf { it.ratedVolume().toDouble() }.toFloat(),
                ratedWorkoutCount = monthWorkouts.size,
                weeklyTrend = buildWeeklyTrend(workouts, trendStart, nowMs),
                topMuscles = monthMuscles.sortedByDescending { it.averageRating }.take(3),
                lowMuscles = monthMuscles.sortedBy { it.averageRating }.take(3)
            )
        }

    private fun List<RatedWorkoutSnapshot>.averageRating(): Float? {
        if (isEmpty()) return null
        return map { it.rating }.average().toFloat()
    }

    private fun buildMuscleSummaries(workouts: List<RatedWorkoutSnapshot>): List<MuscleRatingSummary> {
        data class Accumulator(
            var weightedRating: Float = 0f,
            var weight: Float = 0f,
            var ratedVolume: Float = 0f,
            var workingSets: Int = 0
        )

        val byMuscle = mutableMapOf<String, Accumulator>()
        workouts.forEach { workout ->
            val workingSets = workout.workingRows()
            val totalSets = workingSets.size
            if (totalSets == 0) return@forEach

            workingSets.groupBy { it.targetMuscle ?: "Other" }.forEach { (muscle, rows) ->
                val share = rows.size.toFloat() / totalSets
                val volume = rows.sumOf { row ->
                    val weight = row.weight ?: return@sumOf 0.0
                    val reps = row.reps ?: return@sumOf 0.0
                    weight.toDouble() * reps
                }.toFloat()

                val acc = byMuscle.getOrPut(muscle) { Accumulator() }
                acc.weightedRating += workout.rating * share
                acc.weight += share
                acc.ratedVolume += volume * (workout.rating / 5f)
                acc.workingSets += rows.size
            }
        }

        return byMuscle.mapNotNull { (muscle, acc) ->
            if (acc.weight <= 0f) return@mapNotNull null
            MuscleRatingSummary(
                muscle = muscle,
                averageRating = acc.weightedRating / acc.weight,
                ratedVolume = acc.ratedVolume,
                workingSets = acc.workingSets
            )
        }
    }

    private fun buildWeeklyTrend(
        workouts: List<RatedWorkoutSnapshot>,
        startMs: Long,
        endMs: Long
    ): List<RatedVolumePoint> {
        val formatter = SimpleDateFormat("'W'w", Locale.getDefault())
        val points = mutableListOf<RatedVolumePoint>()
        var bucketStart = startMs

        while (bucketStart <= endMs) {
            val bucketEnd = bucketStart + WEEK_MS
            val bucketWorkouts = workouts.filter {
                it.startTime.time >= bucketStart && it.startTime.time < bucketEnd
            }
            if (bucketWorkouts.isNotEmpty()) {
                points += RatedVolumePoint(
                    label = formatter.format(Date(bucketStart)),
                    averageRating = bucketWorkouts.map { it.rating }.average().toFloat(),
                    ratedVolume = bucketWorkouts.sumOf { it.ratedVolume().toDouble() }.toFloat(),
                    date = Date(bucketStart)
                )
            }
            bucketStart = bucketEnd
        }

        return points.takeLast(WEEKS_FOR_TREND)
    }

    private fun startOfCurrentWeek(nowMs: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMs
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun startOfCurrentMonth(nowMs: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun List<RatedWorkoutSetInfo>.toSnapshots(): List<RatedWorkoutSnapshot> {
        return groupBy { it.workoutId }
            .mapNotNull { (_, rows) ->
                val first = rows.firstOrNull() ?: return@mapNotNull null
                RatedWorkoutSnapshot(
                    workoutId = first.workoutId,
                    startTime = first.startTime,
                    rating = first.rating,
                    rows = rows
                )
            }
            .sortedBy { it.startTime }
    }

    companion object {
        private const val WEEKS_FOR_TREND = 8
        private const val WEEK_MS = 7L * 24L * 60L * 60L * 1000L
    }
}

data class RatedWorkoutSnapshot(
    val workoutId: Long,
    val startTime: Date,
    val rating: Int,
    val rows: List<RatedWorkoutSetInfo>
) {
    fun workingRows(): List<RatedWorkoutSetInfo> {
        return rows.filter { it.setId != null && it.isWarmup != true }
    }

    fun volume(): Float {
        return workingRows().sumOf { row ->
            val weight = row.weight ?: return@sumOf 0.0
            val reps = row.reps ?: return@sumOf 0.0
            weight.toDouble() * reps
        }.toFloat()
    }

    fun ratedVolume(): Float = volume() * (rating / 5f)
}
