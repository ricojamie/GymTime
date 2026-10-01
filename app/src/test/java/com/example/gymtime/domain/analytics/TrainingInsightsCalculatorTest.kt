package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.SetWithExercisePerformanceInfo
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Date

class TrainingInsightsCalculatorTest {
    private val now = Instant.parse("2026-10-01T16:00:00Z")
    private val zone = ZoneId.of("UTC")

    @Test
    fun `period endpoints split equal windows without overlap`() {
        val start = now.minus(Duration.ofDays(28)).toEpochMilli()
        val previous = now.minus(Duration.ofDays(56)).toEpochMilli()
        val rows = listOf(row(1, at = previous - 1), row(2, at = previous), row(3, at = start - 1),
            row(4, at = start), row(5, at = now.toEpochMilli() - 1), row(6, at = now.toEpochMilli()))
        val result = calculate(source(rows))

        assertEquals(2, result.cadence.currentWorkingSets)
        assertEquals(2, result.cadence.previousWorkingSets)
        assertEquals(result.period.currentStartMs, result.period.previousEndMs)
        assertEquals(result.period.currentEndMs - result.period.currentStartMs,
            result.period.previousEndMs - result.period.previousStartMs)
        assertEquals(5, result.exercises.single().totalWorkingSets)
    }

    @Test
    fun `workout start owns cadence across midnight and several workouts share one active day`() {
        val first = Instant.parse("2026-09-29T23:50:00Z").toEpochMilli()
        val second = Instant.parse("2026-09-30T00:10:00Z").toEpochMilli()
        val rows = listOf(row(1, workoutId = 10, at = first), row(2, workoutId = 10, at = second),
            row(3, workoutId = 11, at = second + 60_000), row(4, workoutId = 12, at = second + 120_000))
        val workouts = listOf(workout(10, first, second + 1), workout(11, second), workout(12, second + 120_000))
        val cadence = calculate(source(rows, workouts = workouts)).cadence

        assertEquals(3, cadence.currentWorkouts)
        assertEquals(2, cadence.currentActiveDays)
        assertEquals(4, cadence.currentWorkingSets)
        assertEquals(3, cadence.thisWeekWorkouts)
        assertEquals(3, cadence.weeks.sumOf { it.workouts })
    }

    @Test
    fun `calendar bars include both clipped first week and current week so far`() {
        val result = calculate(source(listOf(row(1, at = ago(27)), row(2, at = ago(1)))))

        assertEquals(5, result.cadence.weeks.size)
        assertTrue(result.cadence.weeks.first().isPartialWeek)
        assertFalse(result.cadence.weeks.first().isCurrentWeek)
        assertTrue(result.cadence.weeks.last().isPartialWeek)
        assertTrue(result.cadence.weeks.last().isCurrentWeek)
        assertEquals(result.period.currentStartDate, result.cadence.weeks.first().startDate)
        assertEquals(result.period.currentEndDate, result.cadence.weeks.last().endDate)
    }

    @Test
    fun `daylight saving does not change equal period duration or double count a workout`() {
        val clock = Instant.parse("2026-11-15T17:00:00Z")
        val newYork = ZoneId.of("America/New_York")
        val first = Instant.parse("2026-11-01T05:30:00Z").toEpochMilli()
        val second = Instant.parse("2026-11-01T06:30:00Z").toEpochMilli()
        val rows = listOf(row(1, workoutId = 10, at = first), row(2, workoutId = 10, at = second))
        val result = TrainingInsightsCalculator.calculate(source(rows, workouts = listOf(workout(10, first, second + 1))),
            28, clock, newYork)

        assertEquals(Duration.ofDays(28).toMillis(), result.period.currentEndMs - result.period.currentStartMs)
        assertEquals(1, result.cadence.currentWorkouts)
        assertEquals(1, result.cadence.currentActiveDays)
        assertEquals(1, result.cadence.weeks.sumOf { it.workouts })
    }

    @Test
    fun `incomplete unfinished warmup library and corrupt activity never enter evidence`() {
        val good = row(1)
        val rows = listOf(good, row(2).let { it.copy(set = it.set.copy(isComplete = false)) },
            row(3).let { it.copy(set = it.set.copy(isWarmup = true)) }, row(4, muscle = "Warm-up"),
            row(5, weight = Float.NaN), row(6, weight = Float.POSITIVE_INFINITY), row(7, weight = -1f),
            row(8, reps = 0), row(9))
        val workouts = source(rows).workouts.map { if (it.id == 9L) it.copy(endTime = null) else it }
        val result = calculate(source(rows, workouts = workouts))

        assertEquals(1, result.cadence.totalCompletedWorkouts)
        assertEquals(1, result.cadence.currentWorkingSets)
        assertEquals(1, result.exercises.sumOf { it.totalWorkingSets })
        assertFalse(result.muscles.any { it.name.contains("Warm", ignoreCase = true) })
    }

    @Test
    fun `all muscle groups appear at zero and Abs Core aliases share contributors`() {
        val rows = listOf(row(1, exerciseId = 1, muscle = "Core"), row(2, exerciseId = 2, muscle = "Abs"))
        val result = calculate(source(rows, muscleNames = listOf("Core", "Abs", "Grip", "Warmups")))
        val abs = result.muscles.single { it.name == "Abs" }

        assertEquals(2, abs.currentWorkingSets)
        assertEquals(2, abs.contributors.size)
        assertEquals(0, result.muscles.single { it.name == "Grip" }.currentWorkingSets)
        assertNull(result.muscles.single { it.name == "Grip" }.daysSinceLastLogged)
        assertEquals(1L, abs.daysSinceLastLogged)
        assertTrue(result.muscles.map { it.name }.containsAll(listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Legs")))
    }

    @Test
    fun `each session contributes its best eligible estimate and three against three median`() {
        val rows = (1..6).flatMap { index ->
            val weight = if (index <= 3) 110f else 100f
            listOf(row(index.toLong(), workoutId = index.toLong(), weight = weight, at = ago(index)),
                row((index + 10).toLong(), workoutId = index.toLong(), weight = 10f, at = ago(index) + 1))
        }
        val progress = calculate(source(rows)).exercises.single().progress

        assertEquals(6, progress.chartSessionCount)
        assertEquals(3, progress.comparison.recentSessionCount)
        assertEquals(3, progress.comparison.baselineSessionCount)
        assertEquals(10.0, progress.comparison.percentChange!!, 0.01)
        assertEquals(TrainingInsightDirection.UP, progress.comparison.direction)
        assertEquals(110f, progress.latestBestSet!!.weight)
    }

    @Test
    fun `four and five sessions compare two against two without unequal samples`() {
        for (count in listOf(4, 5)) {
            val rows = (1..count).map { row(it.toLong(), at = ago(it), weight = if (it <= 2) 110f else 100f) }
            val comparison = calculate(source(rows)).exercises.single().progress.comparison
            assertEquals(TrainingInsightProgressStatus.READY, comparison.status)
            assertEquals(2, comparison.recentSessionCount)
            assertEquals(2, comparison.baselineSessionCount)
            assertEquals(10.0, comparison.percentChange!!, 0.01)
        }
    }

    @Test
    fun `sparse comparable history has no claimed trend`() {
        val comparison = calculate(source((1L..3L).map { row(it, at = ago(it.toInt())) }))
            .exercises.single().progress.comparison

        assertEquals(TrainingInsightProgressStatus.BUILDING_BASELINE, comparison.status)
        assertNull(comparison.percentChange)
        assertNull(comparison.direction)
    }

    @Test
    fun `comparison is stale only beyond forty two days and excludes history older than one year`() {
        val atThreshold = (0L..3L).map { row(it + 1, at = ago(42 + it.toInt())) }
        assertEquals(TrainingInsightProgressStatus.READY,
            calculate(source(atThreshold)).exercises.single().progress.comparison.status)
        val stale = atThreshold.map { it.copy(set = it.set.copy(timestamp = Date(it.set.timestamp.time - 1))) }
        assertEquals(TrainingInsightProgressStatus.STALE,
            calculate(source(stale)).exercises.single().progress.comparison.status)
        val sparse = listOf(row(1, at = ago(1)), row(2, at = ago(2)), row(3, at = ago(370)), row(4, at = ago(371)))
        assertEquals(TrainingInsightProgressStatus.BUILDING_BASELINE,
            calculate(source(sparse)).exercises.single().progress.comparison.status)
    }

    @Test
    fun `stable and down directions use the same displayed two percent threshold as Home`() {
        for ((recent, direction) in listOf(101f to TrainingInsightDirection.STABLE,
            102f to TrainingInsightDirection.UP, 98f to TrainingInsightDirection.DOWN)) {
            val rows = (1L..4L).map { row(it, at = ago(it.toInt()), weight = if (it <= 2) recent else 100f) }
            assertEquals(direction, calculate(source(rows)).exercises.single().progress.comparison.direction)
        }
    }

    @Test
    fun `estimated charts do not mix high rep or zero weight raw records into estimates`() {
        val rows = listOf(row(1, weight = 100f, reps = 10, at = ago(1)),
            row(2, weight = 110f, reps = 1, at = ago(1), workoutId = 1),
            row(3, weight = 500f, reps = 20, at = ago(2)), row(4, weight = 0f, reps = 50, at = ago(3)))
        val lift = calculate(source(rows)).exercises.single()

        assertEquals(TrainingInsightMetricKind.ESTIMATED_ONE_REP_MAX, lift.progress.metric!!.kind)
        assertEquals(1, lift.progress.chartPoints.size)
        assertEquals(110f, lift.progress.latestBestSet!!.weight)
        assertEquals(100f, lift.progress.latestComparableSet!!.weight)
        assertEquals(500.0, lift.records.single { it.metric.kind == TrainingInsightMetricKind.WEIGHT }.value, 0.0)
        assertEquals(0.0, lift.records.single { it.metric.reps == 50 }.value, 0.0)
        assertEquals(4, lift.history.sumOf { it.sets.size })
    }

    @Test
    fun `high rep and zero weight only history use explicit raw fallback without estimated trend`() {
        val high = calculate(source(listOf(row(1, weight = 100f, reps = 20)))).exercises.single()
        assertEquals(TrainingInsightMetricKind.WEIGHT, high.progress.metric!!.kind)
        assertEquals(TrainingInsightProgressStatus.NO_COMPARABLE_DATA, high.progress.comparison.status)
        assertNull(high.history.single().sets.single().estimatedOneRepMax)
        val zeroDashboard = calculate(source(listOf(row(1, weight = 0f, reps = 20))))
        val zero = zeroDashboard.exercises.single()
        assertEquals(TrainingInsightMetricKind.REPS, zero.progress.metric!!.kind)
        assertEquals(20.0, zero.progress.chartPoints.single().value, 0.0)
        assertTrue(zeroDashboard.recentRecords.all { it.metric.kind == TrainingInsightMetricKind.REPS })
    }

    @Test
    fun `latest actual high rep set remains visible while older estimated graph stays separate`() {
        val rows = listOf(row(1, weight = 100f, reps = 8, at = ago(10)),
            row(2, weight = 110f, reps = 20, at = ago(1)))
        val result = calculate(source(rows))
        val progress = result.exercises.single().progress

        assertEquals(110f, progress.latestBestSet!!.weight)
        assertEquals(20, progress.latestBestSet.reps)
        assertEquals(TrainingInsightMetricKind.WEIGHT, progress.latestBestMetric!!.kind)
        assertEquals(110.0, progress.latestBestValue!!, 0.0)
        assertEquals(100f, progress.latestComparableSet!!.weight)
        assertEquals(1, progress.chartSessionCount)
        assertTrue(result.recentRecords.none { it.metric.kind == TrainingInsightMetricKind.ESTIMATED_ONE_REP_MAX })
    }

    @Test
    fun `recent records compare against all earlier completed history and ties are not records`() {
        val rows = listOf(row(1, weight = 200f, reps = 8, at = ago(700)),
            row(2, weight = 150f, reps = 8, at = ago(10)), row(3, weight = 200f, reps = 8, at = ago(5)),
            row(4, weight = 205f, reps = 8, at = ago(1)))
        val result = calculate(source(rows))
        val weightEvents = result.recentRecords.filter { it.metric.kind == TrainingInsightMetricKind.WEIGHT }

        assertEquals(1, weightEvents.size)
        assertEquals(4L, weightEvents.single().set.id)
        assertEquals(TrainingInsightRecordKind.PERSONAL_RECORD, weightEvents.single().kind)
        assertEquals(200.0, weightEvents.single().previousValue!!, 0.0)
    }

    @Test
    fun `first observation at a new rep count is benchmark and strict later increase is record`() {
        val rows = listOf(row(1, weight = 200f, reps = 8, at = ago(50)),
            row(2, weight = 100f, reps = 20, at = ago(10)), row(3, weight = 100f, reps = 20, at = ago(5)),
            row(4, weight = 105f, reps = 20, at = ago(1)))
        val events = calculate(source(rows)).recentRecords.filter { it.metric.reps == 20 }

        assertEquals(2, events.size)
        assertEquals(TrainingInsightRecordKind.PERSONAL_RECORD, events.first().kind)
        assertEquals(TrainingInsightRecordKind.BENCHMARK, events.last().kind)
        assertNull(events.last().previousValue)
    }

    @Test
    fun `all seven log types have truthful raw history and record metrics`() {
        val rows = LogType.entries.mapIndexed { index, type -> row((index + 1).toLong(),
            exerciseId = (index + 1).toLong(), type = type, duration = 90, distance = 2f,
            unit = DistanceUnit.KILOMETERS, calories = 42f) }
        val result = calculate(source(rows))

        assertEquals(7, result.cadence.currentWorkingSets)
        assertEquals(7, result.exercises.size)
        result.exercises.forEach { assertNotNull(it.progress.metric); assertEquals(1, it.history.size) }
        val cardio = result.exercises.single { it.logType == LogType.CALORIES_TIME }
        assertEquals(42.0, cardio.records.single { it.metric.kind == TrainingInsightMetricKind.CALORIES }.value, 0.0)
        assertFalse(result.exercises.filter { it.logType != LogType.WEIGHT_REPS }
            .flatMap { it.records }.any { it.metric.kind == TrainingInsightMetricKind.ESTIMATED_ONE_REP_MAX })
    }

    @Test
    fun `cardio invalid metrics and finished workouts without valid working activity do not count`() {
        val rows = listOf(row(1, type = LogType.DURATION, duration = 0),
            row(2, type = LogType.CALORIES_TIME, duration = 60, calories = Float.POSITIVE_INFINITY),
            row(3, type = LogType.CALORIES_TIME, duration = -1, calories = 40f),
            row(4, type = LogType.DISTANCE_TIME, duration = 60, distance = -1f, unit = DistanceUnit.STEPS),
            row(5, type = LogType.WEIGHT_DISTANCE, weight = Float.NaN, distance = 100f, unit = DistanceUnit.METERS))
        val result = calculate(source(rows, workouts = source(rows).workouts + workout(99, ago(1))))

        assertEquals(0, result.cadence.totalCompletedWorkouts)
        assertEquals(0, result.cadence.currentWorkingSets)
        assertTrue(result.recentRecords.isEmpty())
        assertTrue(result.exercises.all { it.history.isEmpty() })
    }

    @Test
    fun `meters miles steps and floors remain distinct while convertible units share record`() {
        val rows = listOf(row(1, type = LogType.DISTANCE_TIME, duration = 60, distance = 1f, unit = DistanceUnit.MILES, at = ago(5)),
            row(2, type = LogType.DISTANCE_TIME, duration = 60, distance = 2f, unit = DistanceUnit.KILOMETERS, at = ago(4)),
            row(3, type = LogType.DISTANCE_TIME, duration = 60, distance = 3000f, unit = DistanceUnit.STEPS, at = ago(3)),
            row(4, type = LogType.DISTANCE_TIME, duration = 60, distance = 10f, unit = DistanceUnit.FLOORS, at = ago(1)))
        val lift = calculate(source(rows)).exercises.single()
        val distances = lift.records.filter { it.metric.kind == TrainingInsightMetricKind.DISTANCE }

        assertEquals(3, distances.size)
        assertEquals(2000.0, distances.single { it.metric.unit == "m" }.value, 0.01)
        assertEquals("floors", lift.progress.metric!!.unit)
        assertEquals(1, lift.progress.chartPoints.size)
        assertEquals(10.0, lift.progress.chartPoints.single().value, 0.0)
        assertTrue(lift.progress.chartPoints.none { it.value == 3000.0 || it.value == 2000.0 })
    }

    @Test
    fun `legacy meters remain valid but invalid typed distance cannot borrow stale normalized value`() {
        val legacy = row(1, type = LogType.DISTANCE_TIME, duration = 60).let {
            it.copy(set = it.set.copy(distanceMeters = 500f))
        }
        val corrupt = row(2, type = LogType.DISTANCE_TIME, duration = 60, distance = Float.NaN, unit = DistanceUnit.MILES)
            .let { it.copy(set = it.set.copy(distanceMeters = 5000f)) }
        val result = calculate(source(listOf(legacy, corrupt)))

        assertEquals(1, result.cadence.currentWorkingSets)
        assertEquals(500.0, result.exercises.single().progress.latestBestValue!!, 0.0)
    }

    @Test
    fun `charts are bounded while complete history and old records remain accurate`() {
        val rows = (1L..100L).map { row(it, at = now.minusSeconds(it * 60).toEpochMilli(), weight = it.toFloat()) }
        val lift = calculate(source(rows)).exercises.single()

        assertEquals(100, lift.progress.chartSessionCount)
        assertEquals(80, lift.progress.chartPoints.size)
        assertEquals(100, lift.history.size)
        assertEquals(100.0, lift.records.single { it.metric.kind == TrainingInsightMetricKind.WEIGHT }.value, 0.0)
        assertEquals(100L, lift.progress.chartPoints.first().set.id)
        assertEquals(1L, lift.progress.chartPoints.last().set.id)
    }

    @Test
    fun `unused lifts and empty groups remain selectable without invented observations`() {
        val result = calculate(source(emptyList(), exercises = listOf(exercise(99, starred = true)), muscleNames = listOf("Grip")))

        assertEquals(0, result.cadence.totalCompletedWorkouts)
        assertEquals(99L, result.exercises.single().id)
        assertNull(result.exercises.single().progress.metric)
        assertTrue(result.exercises.single().history.isEmpty())
        assertTrue(result.observations.isEmpty())
        assertTrue(result.recentRecords.isEmpty())
        assertTrue(result.muscles.all { it.currentWorkingSets == 0 })
    }

    @Test
    fun `twelve week overview keeps old training distinct from quiet current period`() {
        val rows = listOf(row(1, at = ago(90)))
        val result = TrainingInsightsCalculator.calculate(source(rows), 84, now, zone)

        assertEquals(84, result.period.windowDays)
        assertEquals(1, result.cadence.totalCompletedWorkouts)
        assertEquals(0, result.cadence.currentWorkouts)
        assertEquals(1, result.cadence.previousWorkouts)
        assertEquals(0, result.exercises.single().progress.chartSessionCount)
        assertTrue(result.observations.isEmpty())
    }

    private fun calculate(source: TrainingInsightsSource) = TrainingInsightsCalculator.calculate(source, 28, now, zone)
    private fun ago(days: Int) = now.minus(Duration.ofDays(days.toLong())).toEpochMilli()

    private fun source(
        rows: List<SetWithExercisePerformanceInfo>,
        exercises: List<Exercise> = rows.distinctBy { it.set.exerciseId }.map { exercise(it.set.exerciseId, it.logType, it.targetMuscle) },
        workouts: List<Workout> = rows.groupBy { it.set.workoutId }.map { (id, sets) ->
            workout(id, sets.minOf { it.set.timestamp.time }, now.toEpochMilli() - 1)
        },
        muscleNames: List<String> = listOf("Chest", "Back", "Core")
    ) = TrainingInsightsSource(rows, exercises, workouts, muscleNames)

    private fun workout(id: Long, start: Long, end: Long = now.toEpochMilli() - 1) =
        Workout(id, Date(start), Date(end), "Workout $id", null)

    private fun exercise(id: Long, type: LogType = LogType.WEIGHT_REPS, muscle: String = "Chest", starred: Boolean = false) =
        Exercise(id, "Exercise $id", muscle, type, isCustom = true, notes = null, defaultRestSeconds = 60, isStarred = starred)

    private fun row(
        id: Long,
        workoutId: Long = id,
        exerciseId: Long = 1,
        type: LogType = LogType.WEIGHT_REPS,
        muscle: String = "Chest",
        weight: Float = 100f,
        reps: Int = 8,
        duration: Int? = null,
        distance: Float? = null,
        unit: DistanceUnit? = null,
        calories: Float? = null,
        at: Long = ago(1)
    ) = SetWithExercisePerformanceInfo(Set(id, workoutId, exerciseId, weight, calories, reps, null, duration,
        distance, unit, null, false, true, Date(at)), "Exercise $exerciseId", muscle, type)
}
