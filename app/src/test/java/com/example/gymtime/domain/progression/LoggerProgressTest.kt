package com.example.gymtime.domain.progression

import com.example.gymtime.data.db.dao.ExerciseHistorySet
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class LoggerProgressTest {
    private val exercise = Exercise(
        id = 7, name = "Bench press", targetMuscle = "Chest", logType = LogType.WEIGHT_REPS,
        isCustom = false, notes = null, defaultRestSeconds = 90
    )

    @Test
    fun `records prefer heavier weight then reps and exclude warmups and unfinished sets`() {
        val rows = listOf(
            row(set(1, 1, 100f, 12)),
            row(set(2, 1, 150f, 3)),
            row(set(3, 1, 150f, 8)),
            row(set(4, 2, 200f, 8).copy(isWarmup = true)),
            row(set(5, 2, 300f, 8).copy(isComplete = false))
        )

        val result = buildLoggerProgress(exercise, rows, workout(3))

        assertEquals(3L, result.allTimeBest?.id)
        assertEquals(listOf(1L), result.points.map { it.workoutId })
        assertEquals(5, result.sessions.sumOf { it.sets.size })
        assertFalse(result.records.any { it.set.id == 4L || it.set.id == 5L })
    }

    @Test
    fun `last workout is one session not the greatest result across previous sessions`() {
        val rows = listOf(row(set(1, 1, 300f, 4)), row(set(2, 2, 150f, 6)))

        val result = buildLoggerProgress(exercise, rows, workout(3))

        assertEquals(1L, result.allTimeBest?.id)
        assertEquals(2L, result.lastWorkoutBest?.id)
    }

    @Test
    fun `warmup-only previous workout does not borrow a best set from an older workout`() {
        val rows = listOf(row(set(1, 1, 200f, 6)), row(set(2, 2, 150f, 6).copy(isWarmup = true)))
        val result = buildLoggerProgress(exercise, rows, workout(3))

        assertNull(result.lastWorkoutBest)
    }

    @Test
    fun `reopened older workout uses preceding session instead of a later workout`() {
        val rows = listOf(
            row(set(1, 1, 100f, 6)), row(set(2, 2, 150f, 6), completed = false),
            row(set(3, 3, 300f, 6))
        )
        val result = buildLoggerProgress(exercise, rows, workout(2))

        assertEquals(1L, result.lastWorkoutBest?.id)
        assertEquals(3L, result.allTimeBest?.id)
        assertEquals(listOf(1L, 2L, 3L), result.points.map { it.workoutId })
    }

    @Test
    fun `history and records retain more than fifty sets and refresh when best set is deleted`() {
        val rows = (1L..80L).map { id -> row(set(id, id, id.toFloat(), 5)) }
        val before = buildLoggerProgress(exercise, rows, workout(81))
        val after = buildLoggerProgress(exercise, rows.dropLast(1), workout(81))

        assertEquals(80, before.sessions.size)
        assertEquals(80, before.points.size)
        assertEquals(80L, before.allTimeBest?.id)
        assertEquals(79L, after.allTimeBest?.id)
    }

    @Test
    fun `current workout counts for records but other unfinished workouts do not`() {
        val rows = listOf(
            row(set(1, 1, 100f, 6)),
            row(set(2, 2, 500f, 6), completed = false),
            row(set(3, 3, 200f, 6), completed = false)
        )
        val result = buildLoggerProgress(exercise, rows, workout(3))

        assertEquals(3L, result.allTimeBest?.id)
        assertEquals(1L, result.lastWorkoutBest?.id)
        assertEquals(listOf(3L, 1L), result.sessions.map { it.workoutId })
    }

    @Test
    fun `distance progression converts physical units and excludes steps`() {
        val cardio = exercise.copy(logType = LogType.DISTANCE_TIME, defaultDistanceUnit = DistanceUnit.KILOMETERS)
        val rows = listOf(
            row(set(1, 1).copy(distanceValue = 1f, distanceUnit = DistanceUnit.MILES)),
            row(set(2, 2).copy(distanceValue = 1000f, distanceUnit = DistanceUnit.METERS)),
            row(set(3, 3).copy(distanceValue = 10000f, distanceUnit = DistanceUnit.STEPS, distanceMeters = 9000f))
        )
        val result = buildLoggerProgress(cardio, rows, workout(4))

        assertEquals("km", result.metricUnit)
        assertEquals(1L, result.allTimeBest?.id)
        assertEquals(1.609344f, result.points.first().value, 0.00001f)
        assertEquals(2, result.points.size)
    }

    @Test
    fun `steps and floors are kept separate`() {
        val cardio = exercise.copy(logType = LogType.DISTANCE_TIME, defaultDistanceUnit = DistanceUnit.STEPS)
        val rows = listOf(
            row(set(1, 1).copy(distanceValue = 100f, distanceUnit = DistanceUnit.STEPS)),
            row(set(2, 2).copy(distanceValue = 1000f, distanceUnit = DistanceUnit.FLOORS))
        )
        val result = buildLoggerProgress(cardio, rows, workout(3))

        assertEquals("steps", result.metricUnit)
        assertEquals(1L, result.allTimeBest?.id)
        assertEquals(1, result.points.size)
    }

    @Test
    fun `non-weighted log types use their own records rather than weight`() {
        val rows = listOf(
            row(set(1, 1, 500f, 3).copy(durationSeconds = 30, calories = 20f)),
            row(set(2, 2, 100f, 12).copy(durationSeconds = 60, calories = 40f))
        )
        listOf(LogType.REPS_ONLY, LogType.DURATION, LogType.CALORIES_TIME).forEach { type ->
            val result = buildLoggerProgress(exercise.copy(logType = type), rows, workout(3))
            assertEquals(type.name, 2L, result.allTimeBest?.id)
            assertEquals(type.name, 1, result.records.size)
        }
    }

    @Test
    fun `same weight with more reps moves the strength graph upward`() {
        val rows = listOf(row(set(1, 1, 100f, 8)), row(set(2, 2, 100f, 10)))

        val result = buildLoggerProgress(exercise, rows, workout(3))

        assertEquals("Estimated strength (1RM)", result.metricLabel)
        assertEquals("lb", result.metricUnit)
        assertEquals(126.66667f, result.points[0].value, 0.0001f)
        assertEquals(133.33334f, result.points[1].value, 0.0001f)
        assertTrue(result.points[1].value > result.points[0].value)
    }

    @Test
    fun `strength graph uses best estimate while record cards stay actual heaviest lift`() {
        val rows = listOf(
            row(set(1, 1, 200f, 1)),
            row(set(2, 1, 180f, 10)),
            row(set(3, 1, 100f, 5)),
            row(set(4, 1, 250f, 8).copy(isWarmup = true)),
            row(set(5, 1, 300f, 8).copy(isComplete = false))
        )

        val result = buildLoggerProgress(exercise, rows, workout(2))

        assertEquals(1L, result.allTimeBest?.id)
        assertEquals(1L, result.lastWorkoutBest?.id)
        assertEquals(2L, result.points.single().set.id)
        assertEquals(240f, result.points.single().value, 0.0001f)
        assertEquals(1L, result.records.first { it.label == "Heaviest set" }.set.id)
        assertEquals(2L, result.records.first { it.label == "Best at 10 reps" }.set.id)
    }

    @Test
    fun `mixed high reps and eligible estimates do not mix raw weights into strength line`() {
        val rows = listOf(
            row(set(1, 1, 100f, 15)),
            row(set(2, 2, 300f, 16)),
            row(set(3, 3, 0f, 20))
        )

        val result = buildLoggerProgress(exercise, rows, workout(4))

        assertEquals("Estimated strength (1RM)", result.metricLabel)
        assertEquals(listOf(1L), result.points.map { it.workoutId })
        assertEquals(150f, result.points.single().value, 0.0001f)
        assertEquals(2L, result.allTimeBest?.id)
        assertEquals(2L, result.records.first { it.label == "Best at 16 reps" }.set.id)
        assertEquals(3L, result.records.first { it.label == "Best at 20 reps" }.set.id)
        assertEquals(1L, result.records.single { it.estimatedValue != null }.set.id)
        assertEquals(3, result.sessions.size)
    }

    @Test
    fun `high rep only workouts show clearly labelled raw weight chart without invented estimates`() {
        val rows = listOf(row(set(1, 1, 100f, 20)), row(set(2, 2, 110f, 25)))

        val result = buildLoggerProgress(exercise, rows, workout(3))

        assertEquals("Heaviest working set", result.metricLabel)
        assertTrue(result.metricDescription!!.contains("logged weight only"))
        assertEquals(listOf(100f, 110f), result.points.map { it.value })
        assertFalse(result.records.any { it.estimatedValue != null })
        assertEquals(2L, result.allTimeBest?.id)
    }

    @Test
    fun `estimated chart includes completed current sets but no other unfinished workout`() {
        val rows = listOf(
            row(set(1, 1, 100f, 8)),
            row(set(2, 2, 500f, 8), completed = false),
            row(set(3, 3, 100f, 10), completed = false)
        )

        val result = buildLoggerProgress(exercise, rows, workout(3))

        assertEquals(listOf(1L, 3L), result.points.map { it.workoutId })
        assertEquals(133.33334f, result.points.last().value, 0.0001f)
        assertEquals(1L, result.lastWorkoutBest?.id)
    }

    @Test
    fun `weighted distance and weighted duration continue graphing logged weight`() {
        val rows = listOf(
            row(set(1, 1, 200f, 1).copy(durationSeconds = 30)),
            row(set(2, 2, 180f, 10).copy(durationSeconds = 60))
        )
        listOf(LogType.WEIGHT_DISTANCE, LogType.WEIGHT_TIME).forEach { type ->
            val result = buildLoggerProgress(exercise.copy(logType = type), rows, workout(3))
            assertEquals("Heaviest working set", result.metricLabel)
            assertEquals(listOf(200f, 180f), result.points.map { it.value })
            assertEquals(1L, result.allTimeBest?.id)
        }
    }

    @Test
    fun `matching an existing lift exactly does not celebrate another record`() {
        val original = set(1, 1, 100f, 10)
        val tied = set(2, 2, 100f, 10)

        assertTrue(loggerRecordLabels(exercise, tied, listOf(original)).isEmpty())
    }

    @Test
    fun `first current set below previous weight and reps does not earn a PR`() {
        val previous = set(1, 1, 200f, 10)
        val candidate = set(2, 2, 100f, 8)

        assertNoCurrentSetRecord(candidate, listOf(previous))
    }

    @Test
    fun `matching previous weight with fewer reps does not earn a PR`() {
        val previous = set(1, 1, 200f, 10)
        val candidate = set(2, 2, 200f, 8)

        assertNoCurrentSetRecord(candidate, listOf(previous))
    }

    @Test
    fun `improving exact rep bucket does not earn a PR when another lift dominates it`() {
        val stronger = set(1, 1, 200f, 10)
        val previousAtSameReps = set(2, 2, 100f, 8)
        val candidate = set(3, 3, 150f, 8)

        assertNoCurrentSetRecord(candidate, listOf(previousAtSameReps, stronger))
    }

    @Test
    fun `first eligible estimate does not earn a PR below an existing high rep lift`() {
        val previous = set(1, 1, 200f, 20)
        val candidate = set(2, 2, 100f, 8)

        assertNoCurrentSetRecord(candidate, listOf(previous))
    }

    @Test
    fun `same weight with more reps still earns a current workout PR`() {
        val previous = set(1, 1, 100f, 8)
        val candidate = set(2, 2, 100f, 10)

        assertCurrentSetRecord(candidate, listOf(previous))
    }

    @Test
    fun `heavier weight with same reps still earns a current workout PR`() {
        val previous = set(1, 1, 100f, 8)
        val candidate = set(2, 2, 110f, 8)

        assertCurrentSetRecord(candidate, listOf(previous))
    }

    @Test
    fun `warmup and incomplete history do not block a genuine current workout PR`() {
        val previous = set(1, 1, 100f, 8)
        val warmup = set(2, 1, 300f, 20).copy(isWarmup = true)
        val incomplete = set(3, 1, 400f, 20).copy(isComplete = false)
        val candidate = set(4, 2, 110f, 8)

        assertCurrentSetRecord(candidate, listOf(incomplete, previous, warmup))
    }

    @Test
    fun `first ever working set keeps its baseline record labels`() {
        val candidate = set(1, 1, 100f, 8)

        assertCurrentSetRecord(candidate, emptyList())
    }

    private fun assertNoCurrentSetRecord(candidate: Set, previousSets: List<Set>) {
        assertTrue(loggerRecordLabels(exercise, candidate, previousSets).isEmpty())
        val result = progressWithCurrentSetFirst(candidate, previousSets)
        assertFalse(result.setRecordLabels.containsKey(candidate.id))
    }

    private fun assertCurrentSetRecord(candidate: Set, previousSets: List<Set>) {
        val labels = loggerRecordLabels(exercise, candidate, previousSets)
        assertEquals(listOf("Heaviest set", "Best at ${candidate.reps} reps", "Estimated 1RM"), labels)
        val result = progressWithCurrentSetFirst(candidate, previousSets)
        assertEquals(labels, result.setRecordLabels[candidate.id])
    }

    private fun progressWithCurrentSetFirst(candidate: Set, previousSets: List<Set>) =
        buildLoggerProgress(
            exercise,
            listOf(row(candidate, completed = false)) + previousSets.map { row(it) },
            workout(candidate.workoutId)
        )

    private fun workout(id: Long) = Workout(
        id = id, startTime = Date(id * 100_000), endTime = null, name = null, note = null
    )

    private fun row(set: Set, completed: Boolean = true) = ExerciseHistorySet(
        set = set,
        workoutStartTime = Date(set.workoutId * 100_000),
        workoutEndTime = if (completed) Date(set.workoutId * 100_000 + 50_000) else null
    )

    private fun set(id: Long, workoutId: Long, weight: Float = 100f, reps: Int = 5) = Set(
        id = id, workoutId = workoutId, exerciseId = exercise.id, weight = weight, reps = reps,
        rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false,
        isComplete = true, timestamp = Date(workoutId * 100_000 + id)
    )
}
