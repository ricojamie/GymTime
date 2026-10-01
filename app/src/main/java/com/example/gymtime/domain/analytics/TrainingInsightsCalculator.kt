package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.SetWithExercisePerformanceInfo
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.domain.progression.StrengthPerformanceCalculator
import com.example.gymtime.util.TimeUtils
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

data class TrainingInsightsSource(
    val sets: List<SetWithExercisePerformanceInfo>,
    val exercises: List<Exercise>,
    val workouts: List<Workout>,
    val muscleNames: List<String>
)

/** Pure, deterministic derived evidence. No coaching scores or cross-exercise strength totals. */
object TrainingInsightsCalculator {
    private const val MAX_CHART_POINTS = 80
    private const val COMPARISON_HISTORY_DAYS = 365L
    private const val STALE_DAYS = 42L
    private val defaultMuscles = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Abs", "Legs")

    fun calculate(
        source: TrainingInsightsSource,
        windowDays: Int = 28,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): TrainingInsightsDashboard {
        require(windowDays == 28 || windowDays == 84) { "Choose a 4 or 12 week period." }
        val period = period(windowDays, now, zone)
        val workouts = source.workouts.filter {
            val end = it.endTime?.time
            end != null && end >= it.startTime.time && end <= period.currentEndMs &&
                it.startTime.time < period.currentEndMs
        }.associateBy { it.id }
        val valid = source.sets.asSequence().filter {
            it.set.workoutId in workouts && it.set.timestamp.time < period.currentEndMs &&
                !it.targetMuscle.isWarmupMuscleGroup() && isValidWorkingActivity(it.set, it.logType)
        }.distinctBy { it.set.id }.sortedWith(compareBy({ it.set.timestamp.time }, { it.set.id })).toList()
        val exerciseMap = source.exercises.filterNot { it.targetMuscle.isWarmupMuscleGroup() }
            .associateBy { it.id }
        val rowsByExercise = valid.groupBy { it.set.exerciseId }
        val recordEvents = mutableListOf<TrainingInsightRecordEvent>()
        val exercises = (exerciseMap.keys + rowsByExercise.keys).map { id ->
            buildExercise(id, exerciseMap[id], rowsByExercise[id].orEmpty(), workouts, period, recordEvents)
        }.sortedWith(compareByDescending<TrainingInsightExercise> { it.isStarred }
            .thenByDescending { it.currentWorkingSets }.thenByDescending { it.totalWorkingSets }
            .thenByDescending { it.latestTimestampMs ?: Long.MIN_VALUE }.thenBy { it.name.lowercase(Locale.US) })
        val muscles = buildMuscles(source, exercises, now, zone)
        val cadence = buildCadence(workouts.values.toList(), valid, period, now, zone)
        val recentRecords = recordEvents.sortedWith(
            compareByDescending<TrainingInsightRecordEvent> { it.set.timestampMs }
                .thenByDescending { it.kind == TrainingInsightRecordKind.PERSONAL_RECORD }
                .thenBy { it.metric.kind.ordinal }.thenBy { it.exerciseId }.thenBy { it.set.id }
        )
        return TrainingInsightsDashboard(period, cadence, muscles, exercises, recentRecords,
            observations(recentRecords, muscles))
    }

    private fun period(days: Int, now: Instant, zone: ZoneId): TrainingInsightsPeriod {
        val end = now.toEpochMilli()
        val duration = Duration.ofDays(days.toLong()).toMillis()
        val start = end - duration
        val previousStart = start - duration
        // Dates label the days containing included timestamps, including partial edge days.
        return TrainingInsightsPeriod(days, start, end, previousStart, start,
            date(start, zone), date(end - 1, zone), date(previousStart, zone), date(start - 1, zone), zone.id)
    }

    private fun buildExercise(
        id: Long,
        metadata: Exercise?,
        rows: List<SetWithExercisePerformanceInfo>,
        workouts: Map<Long, Workout>,
        period: TrainingInsightsPeriod,
        events: MutableList<TrainingInsightRecordEvent>
    ): TrainingInsightExercise {
        val sample = rows.lastOrNull()
        val name = metadata?.name ?: sample?.exerciseName ?: "Exercise $id"
        val muscle = canonicalMuscle(metadata?.targetMuscle ?: sample?.targetMuscle ?: "Uncategorized")
        val logType = metadata?.logType ?: sample?.logType ?: LogType.WEIGHT_REPS
        val sets = rows.map { setDto(it.set, it.logType) }
        val bySetId = sets.associateBy { it.id }
        val allRecords = linkedMapOf<TrainingInsightMetric, TrainingInsightRecord>()
        rows.forEach { row ->
            val set = bySetId.getValue(row.set.id)
            metrics(row.set, row.logType).forEach { (metric, value) ->
                val prior = allRecords[metric]
                if (prior == null || value > prior.value) {
                    allRecords[metric] = TrainingInsightRecord(metric, value, set)
                    val meaningfulRawEvent = metric.kind != TrainingInsightMetricKind.ESTIMATED_ONE_REP_MAX &&
                        (metric.kind !in setOf(TrainingInsightMetricKind.WEIGHT, TrainingInsightMetricKind.WEIGHT_AT_REPS) || value > 0.0)
                    if (period.containsCurrent(set.timestampMs) && meaningfulRawEvent) {
                        events += TrainingInsightRecordEvent(id, name, muscle,
                            if (prior == null) TrainingInsightRecordKind.BENCHMARK else TrainingInsightRecordKind.PERSONAL_RECORD,
                            metric, value, prior?.value, set)
                    }
                }
            }
        }
        val history = rows.groupBy { it.set.workoutId }.map { (workoutId, sessionRows) ->
            val workout = workouts.getValue(workoutId)
            TrainingInsightSession(workoutId, workout.name, workout.startTime.time,
                sessionRows.maxOf { it.set.timestamp.time }, sessionRows.map { bySetId.getValue(it.set.id) })
        }.sortedByDescending { it.timestampMs }
        val metric = primaryMetric(logType, rows)
        val sessionPoints = if (metric == null) emptyList() else rows.groupBy { it.set.workoutId }.mapNotNull { (workoutId, sessionRows) ->
            val best = sessionRows.mapNotNull { row ->
                metrics(row.set, row.logType)[metric]?.let { value -> row to value }
            }.maxWithOrNull(compareBy<Pair<SetWithExercisePerformanceInfo, Double>> { it.second }
                .thenBy { it.first.set.timestamp.time }.thenBy { it.first.set.id }) ?: return@mapNotNull null
            TrainingInsightChartPoint(workoutId, sessionRows.maxOf { it.set.timestamp.time },
                best.second, bySetId.getValue(best.first.set.id))
        }.sortedWith(compareBy({ it.timestampMs }, { it.workoutId }))
        val chart = sessionPoints.filter { period.containsCurrent(it.timestampMs) }
        val latest = sessionPoints.lastOrNull()
        val latestSessionRows = history.firstOrNull()?.workoutId?.let { workoutId -> rows.filter { it.set.workoutId == workoutId } }.orEmpty()
        val actualMetric = actualBestMetric(logType, latestSessionRows)
        val actualBest = actualMetric?.let { selectedMetric -> latestSessionRows.mapNotNull { row ->
            metrics(row.set, row.logType)[selectedMetric]?.let { value -> row to value }
        }.maxWithOrNull(compareBy<Pair<SetWithExercisePerformanceInfo, Double>> { it.second }
            .thenBy { it.first.set.reps ?: 0 }.thenBy { it.first.set.durationSeconds ?: 0 }
            .thenBy { it.first.set.timestamp.time }.thenBy { it.first.set.id }) }
        val comparable = metric?.kind == TrainingInsightMetricKind.ESTIMATED_ONE_REP_MAX ||
            logType == LogType.REPS_ONLY && metric?.kind == TrainingInsightMetricKind.REPS
        val comparisonPoints = sessionPoints.filter {
            it.timestampMs >= period.currentEndMs - Duration.ofDays(COMPARISON_HISTORY_DAYS).toMillis()
        }
        val comparison = comparison(comparisonPoints, comparable, period.currentEndMs)
        val explanation = when {
            metric == null -> "No valid completed working sets to chart yet."
            metric.kind == TrainingInsightMetricKind.ESTIMATED_ONE_REP_MAX ->
                "Estimated max uses positive weight and 1–15 reps. It is a within-exercise estimate, not a measured max. Session comparison uses up to 3 recent and 3 preceding sessions from the last 365 days."
            logType == LogType.REPS_ONLY ->
                "Best logged reps per completed session. Session comparison uses up to 3 recent and 3 preceding sessions from the last 365 days."
            else -> "${metric.label} per completed session. This is a raw logged metric; no estimated strength trend is calculated."
        }
        return TrainingInsightExercise(id, name, muscle, logType, metadata?.isStarred == true,
            sets.count { period.containsCurrent(it.timestampMs) }, sets.count { period.containsPrevious(it.timestampMs) },
            sets.size, sets.maxOfOrNull { it.timestampMs },
            TrainingInsightProgress(metric, actualBest?.let { bySetId.getValue(it.first.set.id) }, actualBest?.second,
                sampleChart(chart), chart.size, comparison, explanation, actualMetric, latest?.set, latest?.value),
            allRecords.values.sortedWith(compareBy({ it.metric.kind.ordinal }, { it.metric.reps ?: 0 }, { it.metric.unit })), history)
    }

    private fun comparison(
        points: List<TrainingInsightChartPoint>, comparable: Boolean, nowMs: Long
    ): TrainingInsightComparison {
        if (!comparable || points.isEmpty()) return TrainingInsightComparison(
            TrainingInsightProgressStatus.NO_COMPARABLE_DATA, 0, 0, null, null, null, null)
        val count = minOf(3, points.size / 2)
        val latest = points.last()
        val stale = nowMs - latest.timestampMs > Duration.ofDays(STALE_DAYS).toMillis()
        if (stale || count < 2) return TrainingInsightComparison(
            if (stale) TrainingInsightProgressStatus.STALE else TrainingInsightProgressStatus.BUILDING_BASELINE,
            minOf(3, points.size), 0, null, null, null, null)
        val descending = points.asReversed()
        val recent = median(descending.take(count).map { it.value })
        val baseline = median(descending.drop(count).take(count).map { it.value })
        // Match Home's displayed precision and direction threshold.
        val percent = (((recent - baseline) / baseline * 100.0) * 10.0).roundToInt() / 10.0
        return TrainingInsightComparison(TrainingInsightProgressStatus.READY, count, count, recent, baseline,
            percent, when { percent >= 2.0 -> TrainingInsightDirection.UP
                percent <= -2.0 -> TrainingInsightDirection.DOWN
                else -> TrainingInsightDirection.STABLE })
    }

    private fun buildMuscles(
        source: TrainingInsightsSource,
        exercises: List<TrainingInsightExercise>,
        now: Instant,
        zone: ZoneId
    ): List<TrainingInsightMuscle> {
        val names = (defaultMuscles + source.muscleNames + exercises.map { it.muscle })
            .filterNot { it.isWarmupMuscleGroup() }.map(::canonicalMuscle).distinctBy { it.lowercase(Locale.US) }
            .sortedWith(compareBy<String> { defaultMuscles.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                .thenBy { it.lowercase(Locale.US) })
        return names.map { name ->
            val contributors = exercises.filter { it.muscle.equals(name, ignoreCase = true) && it.totalWorkingSets > 0 }
                .map { TrainingInsightMuscleContribution(it.id, it.name, it.currentWorkingSets,
                    it.previousWorkingSets, it.totalWorkingSets, it.latestTimestampMs) }
                .sortedWith(compareByDescending<TrainingInsightMuscleContribution> { it.currentWorkingSets }
                    .thenByDescending { it.totalWorkingSets }.thenBy { it.exerciseName.lowercase(Locale.US) })
            val latest = contributors.mapNotNull { it.latestTimestampMs }.maxOrNull()
            TrainingInsightMuscle(name, contributors.sumOf { it.currentWorkingSets }, contributors.sumOf { it.previousWorkingSets },
                latest, latest?.let { ChronoUnit.DAYS.between(date(it, zone), now.atZone(zone).toLocalDate()).coerceAtLeast(0) }, contributors)
        }
    }

    private fun buildCadence(
        workouts: List<Workout>, rows: List<SetWithExercisePerformanceInfo>,
        period: TrainingInsightsPeriod, now: Instant, zone: ZoneId
    ): TrainingInsightCadence {
        val validWorkoutIds = rows.map { it.set.workoutId }.toSet()
        val completed = workouts.filter { it.id in validWorkoutIds }
        val current = completed.filter { period.containsCurrent(it.startTime.time) }
        val previous = completed.filter { period.containsPrevious(it.startTime.time) }
        val today = now.atZone(zone).toLocalDate()
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val currentWeekStartMs = currentWeekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val weeks = mutableListOf<TrainingInsightWeek>()
        var week = period.currentStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        while (!week.isAfter(period.currentEndDate)) {
            val next = week.plusWeeks(1)
            val start = maxOf(week.atStartOfDay(zone).toInstant().toEpochMilli(), period.currentStartMs)
            val end = minOf(next.atStartOfDay(zone).toInstant().toEpochMilli(), period.currentEndMs)
            if (start < end) weeks += TrainingInsightWeek(date(start, zone), date(end - 1, zone),
                completed.count { it.startTime.time >= start && it.startTime.time < end },
                week == currentWeekStart, start != week.atStartOfDay(zone).toInstant().toEpochMilli() ||
                    end != next.atStartOfDay(zone).toInstant().toEpochMilli())
            week = next
        }
        return TrainingInsightCadence(completed.size, current.size, previous.size,
            current.map { date(it.startTime.time, zone) }.distinct().size,
            previous.map { date(it.startTime.time, zone) }.distinct().size,
            rows.count { period.containsCurrent(it.set.timestamp.time) }, rows.count { period.containsPrevious(it.set.timestamp.time) },
            completed.count { it.startTime.time >= currentWeekStartMs && it.startTime.time < period.currentEndMs },
            currentWeekStart, completed.maxOfOrNull { it.startTime.time }, weeks)
    }

    private fun observations(
        records: List<TrainingInsightRecordEvent>, muscles: List<TrainingInsightMuscle>
    ): List<TrainingInsightObservation> = buildList {
        records.firstOrNull { it.kind == TrainingInsightRecordKind.PERSONAL_RECORD }?.let {
            add(TrainingInsightObservation("${it.exerciseName} has a new ${it.metric.label.lowercase(Locale.US)} record in this period.", it.exerciseId, it.muscle))
        }
        muscles.filter { it.currentWorkingSets > 0 }.maxByOrNull { it.currentWorkingSets }?.let {
            add(TrainingInsightObservation("${it.name}: ${it.currentWorkingSets} working sets logged in this period.", muscle = it.name))
        }
    }.take(2)

    private fun primaryMetric(type: LogType, rows: List<SetWithExercisePerformanceInfo>): TrainingInsightMetric? {
        if (rows.isEmpty()) return null
        return when (type) {
            LogType.WEIGHT_REPS -> when {
                rows.any { StrengthPerformanceCalculator.estimatedOneRepMax(it.set) != null } -> estimateMetric
                rows.any { (it.set.weight ?: 0f) > 0f } -> weightMetric
                else -> repsMetric
            }
            LogType.REPS_ONLY -> repsMetric
            LogType.DURATION, LogType.WEIGHT_TIME -> durationMetric
            LogType.WEIGHT_DISTANCE, LogType.DISTANCE_TIME -> distance(rows.last().set)?.first
            LogType.CALORIES_TIME -> caloriesMetric
        }
    }

    private fun actualBestMetric(type: LogType, rows: List<SetWithExercisePerformanceInfo>): TrainingInsightMetric? {
        if (rows.isEmpty()) return null
        return when (type) {
            LogType.WEIGHT_REPS -> if (rows.any { (it.set.weight ?: 0f) > 0f }) weightMetric else repsMetric
            LogType.REPS_ONLY -> repsMetric
            LogType.DURATION -> durationMetric
            LogType.WEIGHT_DISTANCE, LogType.DISTANCE_TIME -> distance(rows.last().set)?.first
            LogType.WEIGHT_TIME -> weightMetric
            LogType.CALORIES_TIME -> caloriesMetric
        }
    }

    private fun metrics(set: Set, type: LogType): Map<TrainingInsightMetric, Double> = buildMap {
        when (type) {
            LogType.WEIGHT_REPS -> {
                put(weightMetric, set.weight!!.toDouble())
                put(TrainingInsightMetric(TrainingInsightMetricKind.WEIGHT_AT_REPS, "Weight at ${set.reps} reps", "lb", reps = set.reps), set.weight.toDouble())
                put(repsMetric, set.reps!!.toDouble())
                StrengthPerformanceCalculator.estimatedOneRepMax(set)?.let { put(estimateMetric, it.toDouble()) }
            }
            LogType.REPS_ONLY -> put(repsMetric, set.reps!!.toDouble())
            LogType.DURATION -> put(durationMetric, set.durationSeconds!!.toDouble())
            LogType.WEIGHT_DISTANCE -> {
                put(weightMetric, set.weight!!.toDouble())
                distance(set)?.let { put(it.first, it.second) }
            }
            LogType.DISTANCE_TIME -> {
                distance(set)?.let { put(it.first, it.second) }
                put(durationMetric, set.durationSeconds!!.toDouble())
            }
            LogType.WEIGHT_TIME -> {
                put(weightMetric, set.weight!!.toDouble())
                put(durationMetric, set.durationSeconds!!.toDouble())
            }
            LogType.CALORIES_TIME -> {
                put(caloriesMetric, set.calories!!.toDouble())
                put(durationMetric, set.durationSeconds!!.toDouble())
            }
        }
    }

    private fun isValidWorkingActivity(set: Set, type: LogType): Boolean {
        if (!set.isComplete || set.isWarmup) return false
        val weight = set.weight?.let { it.isFinite() && it >= 0f } == true
        val reps = (set.reps ?: 0) > 0
        val duration = (set.durationSeconds ?: 0) > 0
        return when (type) {
            LogType.WEIGHT_REPS -> weight && reps
            LogType.REPS_ONLY -> reps
            LogType.DURATION -> duration
            LogType.WEIGHT_DISTANCE -> weight && distance(set) != null
            LogType.DISTANCE_TIME -> duration && distance(set) != null
            LogType.WEIGHT_TIME -> weight && duration
            LogType.CALORIES_TIME -> set.calories?.let { it.isFinite() && it > 0f } == true && duration
        }
    }

    private fun distance(set: Set): Pair<TrainingInsightMetric, Double>? {
        val rawUnit = set.distanceUnit
        if (rawUnit == DistanceUnit.STEPS || rawUnit == DistanceUnit.FLOORS) {
            val value = set.distanceValue?.takeIf { it.isFinite() && it > 0f } ?: return null
            val unit = if (rawUnit == DistanceUnit.STEPS) "steps" else "floors"
            return TrainingInsightMetric(TrainingInsightMetricKind.DISTANCE, "Longest distance", unit, distanceUnit = rawUnit) to value.toDouble()
        }
        val normalized = if (set.distanceValue != null && rawUnit != null) {
            set.distanceValue.takeIf { it.isFinite() && it > 0f }?.let { TimeUtils.distanceToMeters(it, rawUnit) }
        } else set.distanceMeters
        val value = normalized?.takeIf { it.isFinite() && it > 0f } ?: return null
        return TrainingInsightMetric(TrainingInsightMetricKind.DISTANCE, "Longest distance", "m", distanceUnit = DistanceUnit.METERS) to value.toDouble()
    }

    private fun setDto(set: Set, type: LogType) = TrainingInsightSet(set.id, set.workoutId, set.timestamp.time,
        set.weight?.takeIf { it.isFinite() }, set.reps, set.durationSeconds,
        set.distanceValue?.takeIf { it.isFinite() }, set.distanceUnit, set.distanceMeters?.takeIf { it.isFinite() },
        set.calories?.takeIf { it.isFinite() }, set.note,
        if (type == LogType.WEIGHT_REPS) StrengthPerformanceCalculator.estimatedOneRepMax(set) else null)

    private fun sampleChart(points: List<TrainingInsightChartPoint>): List<TrainingInsightChartPoint> {
        if (points.size <= MAX_CHART_POINTS) return points
        return (0 until MAX_CHART_POINTS).map { index ->
            points[(index.toLong() * (points.size - 1) / (MAX_CHART_POINTS - 1)).toInt()]
        }
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
    }

    private fun canonicalMuscle(raw: String): String = when {
        raw.trim().equals("Core", true) || raw.trim().equals("Abs", true) -> "Abs"
        raw.isBlank() -> "Uncategorized"
        else -> raw.trim()
    }

    private fun date(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    private fun TrainingInsightsPeriod.containsCurrent(ms: Long) = ms >= currentStartMs && ms < currentEndMs
    private fun TrainingInsightsPeriod.containsPrevious(ms: Long) = ms >= previousStartMs && ms < previousEndMs
    private val estimateMetric = TrainingInsightMetric(TrainingInsightMetricKind.ESTIMATED_ONE_REP_MAX, "Estimated max", "lb")
    private val weightMetric = TrainingInsightMetric(TrainingInsightMetricKind.WEIGHT, "Heaviest weight", "lb")
    private val repsMetric = TrainingInsightMetric(TrainingInsightMetricKind.REPS, "Most reps", "reps")
    private val durationMetric = TrainingInsightMetric(TrainingInsightMetricKind.DURATION, "Longest duration", "seconds")
    private val caloriesMetric = TrainingInsightMetric(TrainingInsightMetricKind.CALORIES, "Most logged calories", "cal")
}
