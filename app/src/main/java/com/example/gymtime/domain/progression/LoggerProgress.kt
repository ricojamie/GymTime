package com.example.gymtime.domain.progression

import com.example.gymtime.data.db.dao.ExerciseHistorySet
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.isWarmupLibraryExercise
import com.example.gymtime.util.TimeUtils
import java.util.Date

data class LoggerSession(
    val workoutId: Long,
    val date: Date,
    val isCurrent: Boolean,
    val sets: List<Set>
)

data class LoggerProgressPoint(
    val workoutId: Long,
    val date: Date,
    val value: Float,
    val set: Set
)

data class LoggerRecord(val label: String, val set: Set, val estimatedValue: Float? = null)

data class LoggerProgressState(
    val isLoading: Boolean = true,
    val allTimeBest: Set? = null,
    val lastWorkoutBest: Set? = null,
    val recordLabel: String = "Heaviest set",
    val metricLabel: String = "Heaviest working set",
    val metricUnit: String = "lb",
    val metricDescription: String? = null,
    val isEstimatedStrength: Boolean = false,
    val sessions: List<LoggerSession> = emptyList(),
    val points: List<LoggerProgressPoint> = emptyList(),
    val records: List<LoggerRecord> = emptyList(),
    val setRecordLabels: Map<Long, List<String>> = emptyMap(),
    val workoutId: Long? = null
)

/** Records and the chart always use completed working sets; history retains every set. */
fun buildLoggerProgress(
    exercise: Exercise,
    history: List<ExerciseHistorySet>,
    currentWorkout: Workout
): LoggerProgressState {
    val currentWorkoutId = currentWorkout.id
    val sessions = history
        .filter { it.workoutEndTime != null || it.set.workoutId == currentWorkoutId }
        .groupBy { it.set.workoutId }
        .map { (workoutId, rows) ->
            LoggerSession(
                workoutId = workoutId,
                date = rows.first().workoutStartTime,
                isCurrent = workoutId == currentWorkoutId,
                sets = rows.map { it.set }.sortedWith(compareBy<Set> { it.timestamp }.thenBy { it.id })
            )
        }
        .sortedWith(compareByDescending<LoggerSession> { it.date }.thenByDescending { it.workoutId })

    val workingSets = sessions.flatMap { it.sets }.filter { it.isComplete && !it.isWarmup }
    // Record cards remain actual logged lifts even when the chart uses estimated strength.
    val metric = metricFor(exercise)
    val comparator = compareBy<Set> { metric.value(it) ?: Float.NEGATIVE_INFINITY }
        .thenBy { if (exercise.logType == LogType.WEIGHT_REPS) it.reps ?: 0 else 0 }
        .thenBy { if (exercise.logType == LogType.WEIGHT_TIME) it.durationSeconds ?: 0 else 0 }
        .thenBy { it.timestamp }

    fun best(sets: List<Set>): Set? = sets
        .filter { it.isComplete && !it.isWarmup && metric.value(it) != null }
        .maxWithOrNull(comparator)

    val allTimeBest = best(workingSets)
    // Choose the actual previous completed session first, even when it has only warmups.
    val lastWorkoutBest = sessions.firstOrNull {
        !it.isCurrent && (it.date.before(currentWorkout.startTime) ||
            (it.date == currentWorkout.startTime && it.workoutId < currentWorkoutId))
    }?.let { best(it.sets) }
    val hasStrengthEstimates = exercise.logType == LogType.WEIGHT_REPS &&
        workingSets.any { StrengthPerformanceCalculator.estimatedOneRepMax(it) != null }
    val chartValue: (Set) -> Float? = if (hasStrengthEstimates) {
        StrengthPerformanceCalculator::estimatedOneRepMax
    } else metric.value
    val points = sessions.asReversed().mapNotNull { session ->
        session.sets.filter { it.isComplete && !it.isWarmup }
            .mapNotNull { set -> chartValue(set)?.let { set to it } }
            .maxWithOrNull(compareBy<Pair<Set, Float>> { it.second }.thenBy { it.first.timestamp })
            ?.let { (set, value) -> LoggerProgressPoint(session.workoutId, session.date, value, set) }
    }
    val records = buildList {
        allTimeBest?.let { add(LoggerRecord(metric.recordLabel, it)) }
        if (exercise.logType == LogType.WEIGHT_REPS) {
            workingSets.mapNotNull { set ->
                StrengthPerformanceCalculator.estimatedOneRepMax(set)
                    ?.let { set to it }
            }.maxByOrNull { it.second }?.let { (set, estimate) ->
                add(LoggerRecord("Estimated one-rep max", set, estimate))
            }
            workingSets.filter { it.weight?.isFinite() == true && it.weight >= 0f && (it.reps ?: 0) > 0 }
                .groupBy { it.reps!! }.toSortedMap()
                .forEach { (reps, sets) ->
                    sets.maxWithOrNull(comparator)?.let { add(LoggerRecord("Best at $reps reps", it)) }
                }
        }
    }
    return LoggerProgressState(
        isLoading = false,
        allTimeBest = allTimeBest,
        lastWorkoutBest = lastWorkoutBest,
        recordLabel = metric.recordLabel,
        metricLabel = if (hasStrengthEstimates) "Estimated strength (1RM)" else metric.label,
        metricUnit = metric.unit,
        isEstimatedStrength = hasStrengthEstimates,
        metricDescription = when {
            hasStrengthEstimates -> "Best estimated one-rep max per workout, using weight and reps. Only positive-weight sets of 1–15 reps qualify. Higher-rep sets stay in Records and History; workouts without an eligible set have no dot."
            exercise.logType == LogType.WEIGHT_REPS -> "No eligible strength estimates yet. This chart shows logged weight only; reps are shown below. Estimated strength needs positive weight and 1–15 reps. All completed working sets remain in Records and History."
            else -> null
        },
        sessions = sessions,
        points = points,
        records = records,
        workoutId = currentWorkoutId,
        setRecordLabels = buildMap {
            val tracker = LoggerRecordTracker(exercise)
            workingSets.sortedWith(compareBy<Set> { it.timestamp }.thenBy { it.id }).forEach { set ->
                val labels = tracker.add(set)
                if (set.workoutId == currentWorkoutId && labels.isNotEmpty()) put(set.id, labels)
            }
        }
    )
}

/** Same rules for the persistent badge and the one-time, post-save celebration. */
fun loggerRecordLabels(exercise: Exercise, candidate: Set, previousSets: List<Set>): List<String> {
    val tracker = LoggerRecordTracker(exercise)
    previousSets.forEach { tracker.add(it) }
    return tracker.add(candidate)
}

private class LoggerRecordTracker(private val exercise: Exercise) {
    private val metric = metricFor(exercise)
    private var highest: Float? = null
    private var highestSecondary = 0
    private var estimatedMax: Float? = null
    private val bestByReps = mutableMapOf<Int, Float>()

    fun add(set: Set): List<String> {
        if (exercise.isWarmupLibraryExercise || set.isWarmup || !set.isComplete) return emptyList()
        return buildList {
            val value = metric.value(set)
            val secondary = when (exercise.logType) {
                LogType.WEIGHT_REPS -> set.reps ?: 0
                LogType.WEIGHT_TIME -> set.durationSeconds ?: 0
                else -> 0
            }
            if (value != null) {
                val previous = highest
                if (previous == null || value > previous || (value == previous && secondary > highestSecondary)) {
                    add(metric.recordLabel)
                    highest = value
                    highestSecondary = secondary
                }
            }
            if (exercise.logType == LogType.WEIGHT_REPS) {
                val weight = set.weight?.takeIf { it.isFinite() && it >= 0f }
                val reps = set.reps?.takeIf { it > 0 }
                if (weight != null && reps != null) {
                    // A lift achieved for more reps also sets the baseline at this rep count.
                    val previousWeight = bestByReps.asSequence()
                        .filter { it.key >= reps }
                        .maxOfOrNull { it.value }
                    val improvesWeightAtReps = previousWeight == null || weight > previousWeight
                    if (improvesWeightAtReps) {
                        add("Best at $reps reps")
                    }
                    bestByReps[reps] = maxOf(bestByReps[reps] ?: weight, weight)
                    val estimate = StrengthPerformanceCalculator.estimatedOneRepMax(set)
                    if (estimate != null && (estimatedMax == null || estimate > estimatedMax!!)) {
                        // Higher-rep history may dominate this lift without an eligible estimate.
                        if (improvesWeightAtReps) add("Estimated 1RM")
                        estimatedMax = estimate
                    }
                }
            }
        }
    }
}

private data class ProgressMetric(
    val recordLabel: String,
    val label: String,
    val unit: String,
    val value: (Set) -> Float?
)

private fun metricFor(exercise: Exercise): ProgressMetric = when (exercise.logType) {
    LogType.WEIGHT_REPS, LogType.WEIGHT_DISTANCE, LogType.WEIGHT_TIME ->
        ProgressMetric("Heaviest set", "Heaviest working set", "lb") { set ->
            set.weight?.takeIf { it.isFinite() && it >= 0f }
        }
    LogType.REPS_ONLY -> ProgressMetric("Most reps", "Most reps in a working set", "reps") {
        it.reps?.takeIf { value -> value > 0 }?.toFloat()
    }
    LogType.DURATION -> ProgressMetric("Longest set", "Longest working set", "sec") {
        it.durationSeconds?.takeIf { value -> value > 0 }?.toFloat()
    }
    LogType.CALORIES_TIME -> ProgressMetric("Most calories", "Most calories in a working set", "cal") {
        it.calories?.takeIf { value -> value.isFinite() && value > 0f }
    }
    LogType.DISTANCE_TIME -> ProgressMetric(
        "Farthest set", "Farthest working set", exercise.defaultDistanceUnit.progressLabel()
    ) { set -> distanceInUnit(set, exercise.defaultDistanceUnit) }
}

private fun distanceInUnit(set: Set, unit: DistanceUnit): Float? {
    val value = if (unit.isConvertibleToMeters) {
        // Never compare steps or floors with physical distance, even if malformed imported data has meters.
        if (set.distanceUnit?.isConvertibleToMeters == false) return null
        val meters = set.distanceValue?.let { raw ->
            set.distanceUnit?.let { TimeUtils.distanceToMeters(raw, it) }
        } ?: set.distanceMeters ?: return null
        TimeUtils.metersToDistance(meters, unit)
    } else {
        if (set.distanceUnit != unit) return null
        set.distanceValue ?: return null
    }
    return value.takeIf { it.isFinite() && it > 0f }
}

fun DistanceUnit.progressLabel(): String = when (this) {
    DistanceUnit.METERS -> "m"
    DistanceUnit.KILOMETERS -> "km"
    DistanceUnit.YARDS -> "yd"
    DistanceUnit.FEET -> "ft"
    DistanceUnit.MILES -> "mi"
    DistanceUnit.STEPS -> "steps"
    DistanceUnit.FLOORS -> "floors"
}
