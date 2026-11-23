package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.RatedWorkoutSetInfo
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
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
    private var zoneId: ZoneId = ZoneId.systemDefault()

    internal constructor(
        workoutDao: WorkoutDao,
        zoneId: ZoneId
    ) : this(workoutDao) {
        this.zoneId = zoneId
    }

    suspend fun getRatingStats(nowMs: Long = System.currentTimeMillis()): WorkoutRatingStats =
        withContext(Dispatchers.IO) {
            val nowDate = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
            val startOfWeekDate = nowDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val startOfWeek = startOfWeekDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val startOfMonth = nowDate.withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val trendStartDate = startOfWeekDate.minusWeeks(WEEKS_FOR_TREND.toLong())
            val trendStart = trendStartDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

            val rows = workoutDao.getRatedWorkoutSetInfo(trendStart, nowMs)
            val workouts = rows.toSnapshots().filter {
                it.startTime.time in trendStart..nowMs && it.workingRows().isNotEmpty()
            }

            val weekWorkouts = workouts.filter { it.startTime.time >= startOfWeek }
            val monthWorkouts = workouts.filter { it.startTime.time >= startOfMonth }
            val monthMuscles = buildMuscleSummaries(monthWorkouts)

            WorkoutRatingStats(
                weekAverageRating = weekWorkouts.averageRating(),
                monthAverageRating = monthWorkouts.averageRating(),
                weekRatedVolume = weekWorkouts.sumOf { it.ratedVolume().toDouble() }.toFloat(),
                monthRatedVolume = monthWorkouts.sumOf { it.ratedVolume().toDouble() }.toFloat(),
                ratedWorkoutCount = monthWorkouts.size,
                weeklyTrend = buildWeeklyTrend(workouts, trendStartDate, nowDate, nowMs),
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
            var weightedRating: Double = 0.0,
            var weight: Double = 0.0,
            var ratedVolume: Double = 0.0,
            var workingSets: Int = 0
        )

        val byMuscle = mutableMapOf<String, Accumulator>()
        workouts.forEach { workout ->
            val workingSets = workout.workingRows()
            val totalSets = workingSets.size
            if (totalSets == 0) return@forEach

            workingSets.groupBy { it.targetMuscle?.takeIf { m -> m.isNotBlank() } ?: "Other" }
                .forEach { (muscle, rows) ->
                    val share = rows.size.toDouble() / totalSets
                    val volume = rows.sumOf { row ->
                        val weight = row.weight?.takeIf { it.isFinite() && it >= 0f } ?: return@sumOf 0.0
                        val reps = row.reps?.takeIf { it > 0 } ?: return@sumOf 0.0
                        weight.toDouble() * reps
                    }

                    val acc = byMuscle.getOrPut(muscle) { Accumulator() }
                    acc.weightedRating += workout.rating * share
                    acc.weight += share
                    acc.ratedVolume += volume * (workout.rating / 5.0)
                    acc.workingSets += rows.size
                }
        }

        return byMuscle.mapNotNull { (muscle, acc) ->
            if (acc.weight <= 0.0) return@mapNotNull null
            MuscleRatingSummary(
                muscle = muscle,
                averageRating = (acc.weightedRating / acc.weight).toFloat(),
                ratedVolume = acc.ratedVolume.toFloat(),
                workingSets = acc.workingSets
            )
        }
    }

    private fun buildWeeklyTrend(
        workouts: List<RatedWorkoutSnapshot>,
        trendStartDate: LocalDate,
        nowDate: LocalDate,
        nowMs: Long
    ): List<RatedVolumePoint> {
        val formatter = DateTimeFormatter.ofPattern("'W'w", Locale.getDefault())
        val workoutsByMonday = workouts.groupBy { snapshot ->
            Instant.ofEpochMilli(snapshot.startTime.time)
                .atZone(zoneId)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        val points = mutableListOf<RatedVolumePoint>()
        var cursorDate = trendStartDate

        while (!cursorDate.isAfter(nowDate)) {
            val bucketStartMs = cursorDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            if (bucketStartMs > nowMs) break
            val bucketWorkouts = workoutsByMonday[cursorDate].orEmpty()
            if (bucketWorkouts.isNotEmpty()) {
                points += RatedVolumePoint(
                    label = formatter.format(cursorDate),
                    averageRating = bucketWorkouts.map { it.rating }.average().toFloat(),
                    ratedVolume = bucketWorkouts.sumOf { it.ratedVolume().toDouble() }.toFloat(),
                    date = Date.from(Instant.ofEpochMilli(bucketStartMs))
                )
            }
            cursorDate = cursorDate.plusWeeks(1)
        }

        return points.takeLast(WEEKS_FOR_TREND)
    }

    private fun List<RatedWorkoutSetInfo>.toSnapshots(): List<RatedWorkoutSnapshot> {
        return groupBy { it.workoutId }
            .mapNotNull { (_, rows) ->
                val first = rows.firstOrNull() ?: return@mapNotNull null
                if (first.endTime == null) return@mapNotNull null
                RatedWorkoutSnapshot(
                    workoutId = first.workoutId,
                    startTime = first.startTime,
                    rating = first.rating,
                    rows = rows
                )
            }
            .sortedWith(compareBy<RatedWorkoutSnapshot> { it.startTime.time }.thenBy { it.workoutId })
    }

    companion object {
        private const val WEEKS_FOR_TREND = 8
    }
}

data class RatedWorkoutSnapshot(
    val workoutId: Long,
    val startTime: Date,
    val rating: Int,
    val rows: List<RatedWorkoutSetInfo>
) {
    fun workingRows(): List<RatedWorkoutSetInfo> {
        return rows.filter {
            it.setId != null &&
                it.isWarmup != true &&
                it.targetMuscle?.isWarmupMuscleGroup() != true
        }
    }

    fun volume(): Float {
        return workingRows().sumOf { row ->
            val weight = row.weight?.takeIf { it.isFinite() && it >= 0f } ?: return@sumOf 0.0
            val reps = row.reps?.takeIf { it > 0 } ?: return@sumOf 0.0
            weight.toDouble() * reps
        }.toFloat()
    }

    fun ratedVolume(): Float = volume() * (rating / 5f)
}
