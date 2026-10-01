package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.LogType
import java.time.LocalDate

/** All timestamps are milliseconds since epoch. Periods include start and exclude end. */
data class TrainingInsightsPeriod(
    val windowDays: Int,
    val currentStartMs: Long,
    val currentEndMs: Long,
    val previousStartMs: Long,
    val previousEndMs: Long,
    val currentStartDate: LocalDate,
    val currentEndDate: LocalDate,
    val previousStartDate: LocalDate,
    val previousEndDate: LocalDate,
    val zoneId: String
)

data class TrainingInsightsDashboard(
    val period: TrainingInsightsPeriod,
    val cadence: TrainingInsightCadence,
    val muscles: List<TrainingInsightMuscle>,
    val exercises: List<TrainingInsightExercise>,
    val recentRecords: List<TrainingInsightRecordEvent>,
    val observations: List<TrainingInsightObservation>
)

data class TrainingInsightCadence(
    val totalCompletedWorkouts: Int,
    val currentWorkouts: Int,
    val previousWorkouts: Int,
    val currentActiveDays: Int,
    val previousActiveDays: Int,
    val currentWorkingSets: Int,
    val previousWorkingSets: Int,
    val thisWeekWorkouts: Int,
    val thisWeekStartDate: LocalDate,
    val latestWorkoutStartMs: Long?,
    val weeks: List<TrainingInsightWeek>
)

data class TrainingInsightWeek(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val workouts: Int,
    val isCurrentWeek: Boolean,
    val isPartialWeek: Boolean
)

data class TrainingInsightMuscle(
    val name: String,
    val currentWorkingSets: Int,
    val previousWorkingSets: Int,
    val latestTimestampMs: Long?,
    val daysSinceLastLogged: Long?,
    val contributors: List<TrainingInsightMuscleContribution>
)

data class TrainingInsightMuscleContribution(
    val exerciseId: Long,
    val exerciseName: String,
    val currentWorkingSets: Int,
    val previousWorkingSets: Int,
    val totalWorkingSets: Int,
    val latestTimestampMs: Long?
)

enum class TrainingInsightMetricKind {
    ESTIMATED_ONE_REP_MAX, WEIGHT, WEIGHT_AT_REPS, REPS, DURATION, DISTANCE, CALORIES
}

/** DISTANCE uses meters for convertible units; steps and floors are separate metrics. */
data class TrainingInsightMetric(
    val kind: TrainingInsightMetricKind,
    val label: String,
    val unit: String,
    val reps: Int? = null,
    val distanceUnit: DistanceUnit? = null
)

data class TrainingInsightSet(
    val id: Long,
    val workoutId: Long,
    val timestampMs: Long,
    val weight: Float?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceValue: Float?,
    val distanceUnit: DistanceUnit?,
    val distanceMeters: Float?,
    val calories: Float?,
    val note: String?,
    val estimatedOneRepMax: Float?,
    val isWarmup: Boolean = false,
    val isComplete: Boolean = true
)

data class TrainingInsightSession(
    val workoutId: Long,
    val workoutName: String?,
    val workoutStartMs: Long,
    val timestampMs: Long,
    val sets: List<TrainingInsightSet>
)

data class TrainingInsightChartPoint(
    val workoutId: Long,
    val timestampMs: Long,
    val value: Double,
    val set: TrainingInsightSet
)

enum class TrainingInsightProgressStatus { READY, BUILDING_BASELINE, STALE, NO_COMPARABLE_DATA }
enum class TrainingInsightDirection { UP, STABLE, DOWN }

/** Compares recent sessions with preceding sessions, independently of the dashboard date window. */
data class TrainingInsightComparison(
    val status: TrainingInsightProgressStatus,
    val recentSessionCount: Int,
    val baselineSessionCount: Int,
    val recentMedian: Double?,
    val baselineMedian: Double?,
    val percentChange: Double?,
    val direction: TrainingInsightDirection?
)

data class TrainingInsightProgress(
    val metric: TrainingInsightMetric?,
    val latestBestSet: TrainingInsightSet?,
    val latestBestValue: Double?,
    val chartPoints: List<TrainingInsightChartPoint>,
    val chartSessionCount: Int,
    val comparison: TrainingInsightComparison,
    val explanation: String,
    val latestBestMetric: TrainingInsightMetric? = null,
    val latestComparableSet: TrainingInsightSet? = null,
    val latestComparableValue: Double? = null
)

data class TrainingInsightRecord(
    val metric: TrainingInsightMetric,
    val value: Double,
    val set: TrainingInsightSet
)

enum class TrainingInsightRecordKind { BENCHMARK, PERSONAL_RECORD }

data class TrainingInsightRecordEvent(
    val exerciseId: Long,
    val exerciseName: String,
    val muscle: String,
    val kind: TrainingInsightRecordKind,
    val metric: TrainingInsightMetric,
    val value: Double,
    val previousValue: Double?,
    val set: TrainingInsightSet
)

data class TrainingInsightExercise(
    val id: Long,
    val name: String,
    val muscle: String,
    val logType: LogType,
    val isStarred: Boolean,
    val currentWorkingSets: Int,
    val previousWorkingSets: Int,
    val totalWorkingSets: Int,
    val latestTimestampMs: Long?,
    val progress: TrainingInsightProgress,
    val records: List<TrainingInsightRecord>,
    /** Complete read-only working history; only chart points are sampled. Newest session first. */
    val history: List<TrainingInsightSession>
)

data class TrainingInsightObservation(
    val text: String,
    val exerciseId: Long? = null,
    val muscle: String? = null
)
