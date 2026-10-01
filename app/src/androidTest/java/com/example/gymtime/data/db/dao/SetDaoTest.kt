package com.example.gymtime.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class SetDaoTest {

    private lateinit var database: GymTimeDatabase
    private lateinit var setDao: SetDao
    private lateinit var workoutDao: WorkoutDao
    private lateinit var exerciseDao: ExerciseDao

    private val testExercise = Exercise(
        id = 1L,
        name = "Bench Press",
        targetMuscle = "Chest",
        logType = LogType.WEIGHT_REPS,
        isCustom = false,
        notes = null,
        defaultRestSeconds = 90
    )

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GymTimeDatabase::class.java
        ).allowMainThreadQueries().build()

        setDao = database.setDao()
        workoutDao = database.workoutDao()
        exerciseDao = database.exerciseDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private suspend fun createTestWorkout(): Long {
        exerciseDao.insertExercise(testExercise)
        val workout = Workout(
            startTime = Date(),
            endTime = null,
            name = "Test Workout",
            note = null,
            rating = null,
            ratingNote = null,
            routineDayId = null
        )
        return workoutDao.insertWorkout(workout)
    }

    @Test
    fun insertAndRetrieveSet() = runTest {
        val workoutId = createTestWorkout()
        val testSet = Set(
            workoutId = workoutId,
            exerciseId = 1L,
            weight = 135f,
            reps = 10,
            rpe = 7f,
            durationSeconds = null,
            distanceMeters = null,
            isWarmup = false,
            isComplete = true,
            timestamp = Date(),
            note = null,
            supersetGroupId = null,
            supersetOrderIndex = 0
        )

        val setId = setDao.insertSet(testSet)
        val sets = setDao.getSetsForWorkout(workoutId).first()

        assertEquals(1, sets.size)
        assertEquals(135f, sets[0].weight)
        assertEquals(10, sets[0].reps)
    }

    @Test
    fun getTotalVolumeCalculatesCorrectly() = runTest {
        val workoutId = createTestWorkout()
        val now = System.currentTimeMillis()

        // Insert working sets
        setDao.insertSet(Set(
            workoutId = workoutId, exerciseId = 1L, weight = 185f, reps = 8,
            rpe = null, durationSeconds = null, distanceMeters = null,
            isWarmup = false, isComplete = true, timestamp = Date(now),
            note = null, supersetGroupId = null, supersetOrderIndex = 0
        ))
        setDao.insertSet(Set(
            workoutId = workoutId, exerciseId = 1L, weight = 185f, reps = 8,
            rpe = null, durationSeconds = null, distanceMeters = null,
            isWarmup = false, isComplete = true, timestamp = Date(now),
            note = null, supersetGroupId = null, supersetOrderIndex = 0
        ))
        // Insert warmup set (should be excluded)
        setDao.insertSet(Set(
            workoutId = workoutId, exerciseId = 1L, weight = 135f, reps = 10,
            rpe = null, durationSeconds = null, distanceMeters = null,
            isWarmup = true, isComplete = true, timestamp = Date(now),
            note = null, supersetGroupId = null, supersetOrderIndex = 0
        ))

        val volume = setDao.getTotalVolume(now - 10000, now + 10000)

        // Only working sets: (185*8) + (185*8) = 2960
        assertEquals(2960f, volume ?: 0f, 0.1f)
    }

    @Test
    fun getVolumeInRangeIncludesOnlyCompletedWorkingSetsInTimeRange() = runTest {
        val workoutId = createTestWorkout()
        val now = System.currentTimeMillis()

        setDao.insertSet(Set(
            workoutId = workoutId, exerciseId = 1L, weight = 200f, reps = 5,
            rpe = null, durationSeconds = null, distanceMeters = null,
            isWarmup = false, isComplete = true, timestamp = Date(now),
            note = null, supersetGroupId = null, supersetOrderIndex = 0
        ))

        setDao.insertSet(Set(
            workoutId = workoutId, exerciseId = 1L, weight = 300f, reps = 10,
            rpe = null, durationSeconds = null, distanceMeters = null,
            isWarmup = false, isComplete = false, timestamp = Date(now),
            note = null, supersetGroupId = null, supersetOrderIndex = 0
        ))
        setDao.insertSet(Set(
            workoutId = workoutId, exerciseId = 1L, weight = 100f, reps = 10,
            rpe = null, durationSeconds = null, distanceMeters = null,
            isWarmup = true, isComplete = true, timestamp = Date(now),
            note = null, supersetGroupId = null, supersetOrderIndex = 0
        ))

        val volumeInRange = setDao.getVolumeInRange(now - 10000, now + 10000)
        val volumeOutOfRange = setDao.getVolumeInRange(now + 20000, now + 30000)

        assertEquals(1000f, volumeInRange, 0.1f) // 200 * 5
        assertEquals(0f, volumeOutOfRange, 0.1f)
    }

    @Test
    fun getWorkoutVolumeIncludesOnlyCompletedWorkingSetsForWorkout() = runTest {
        val workoutId = createTestWorkout()

        setDao.insertSet(Set(
            workoutId = workoutId, exerciseId = 1L, weight = 225f, reps = 5,
            rpe = null, durationSeconds = null, distanceMeters = null,
            isWarmup = false, isComplete = true, timestamp = Date(),
            note = null, supersetGroupId = null, supersetOrderIndex = 0
        ))
        setDao.insertSet(Set(
            workoutId = workoutId, exerciseId = 1L, weight = 225f, reps = 5,
            rpe = null, durationSeconds = null, distanceMeters = null,
            isWarmup = false, isComplete = true, timestamp = Date(),
            note = null, supersetGroupId = null, supersetOrderIndex = 0
        ))

        setDao.insertSet(Set(
            workoutId = workoutId, exerciseId = 1L, weight = 300f, reps = 10,
            rpe = null, durationSeconds = null, distanceMeters = null,
            isWarmup = false, isComplete = false, timestamp = Date(),
            note = null, supersetGroupId = null, supersetOrderIndex = 0
        ))
        setDao.insertSet(Set(
            workoutId = workoutId, exerciseId = 1L, weight = 100f, reps = 10,
            rpe = null, durationSeconds = null, distanceMeters = null,
            isWarmup = true, isComplete = true, timestamp = Date(),
            note = null, supersetGroupId = null, supersetOrderIndex = 0
        ))

        val volume = setDao.getWorkoutVolume(workoutId)

        assertEquals(2250f, volume, 0.1f) // (225*5) + (225*5)
    }

    @Test
    fun priorPersonalBestsExcludeUnfinishedSetsAndUnfinishedAchievementDates() = runTest {
        val workoutId = createTestWorkout()
        val cutoff = System.currentTimeMillis()
        val completedAt = cutoff - 2000L
        val completedSet = Set(
            workoutId = workoutId, exerciseId = 1L, weight = 225f, reps = 8,
            rpe = null, durationSeconds = null, distanceMeters = null,
            isWarmup = false, isComplete = true, timestamp = Date(completedAt)
        )
        setDao.insertSet(completedSet)
        setDao.insertSet(completedSet.copy(weight = 300f, isComplete = false, timestamp = Date(cutoff - 3000L)))
        setDao.insertSet(completedSet.copy(isComplete = false, timestamp = Date(cutoff - 6000L)))
        setDao.insertSet(completedSet.copy(weight = 400f, isWarmup = true, timestamp = Date(cutoff - 4000L)))
        setDao.insertSet(completedSet.copy(weight = 500f, timestamp = Date(cutoff + 1000L)))

        val best = setDao.getPersonalBestsWithTimestampsBefore(1L, cutoff).single()

        assertEquals(8, best.reps)
        assertEquals(225f, best.maxWeight, 0.1f)
        assertEquals(completedAt, best.firstAchievedAt)
    }

    @Test
    fun exerciseUsageStatsCountsCompletedWorkingSetsOnly() = runTest {
        val workoutId = createTestWorkout()
        exerciseDao.insertExercise(testExercise.copy(id = 2L, name = "Squat", targetMuscle = "Legs"))
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60L * 60L * 1000L
        val recentStart = now - (90L * dayMs)

        setDao.insertSet(testSet(workoutId, exerciseId = 1L, weight = 100f, reps = 10, timestamp = now - dayMs))
        setDao.insertSet(testSet(workoutId, exerciseId = 1L, weight = 90f, reps = 10, timestamp = now - (120L * dayMs)))
        setDao.insertSet(testSet(workoutId, exerciseId = 1L, weight = 50f, reps = 10, isWarmup = true, timestamp = now))
        setDao.insertSet(testSet(workoutId, exerciseId = 1L, weight = 110f, reps = 10, isComplete = false, timestamp = now))
        setDao.insertSet(testSet(workoutId, exerciseId = 2L, weight = 200f, reps = 5, timestamp = now))

        val rows = exerciseDao.getExercisesWithUsageStats(recentStart).first().associateBy { it.exercise.id }

        assertEquals(2, rows[1L]?.allTimeSetCount)
        assertEquals(1, rows[1L]?.recentSetCount)
        assertEquals(1, rows[2L]?.allTimeSetCount)
        assertEquals(1, rows[2L]?.recentSetCount)
        // lastUsedMs reflects the most recent completed working set, ignoring the
        // warmup and incomplete sets logged at `now` for exercise 1.
        assertEquals(now - dayMs, rows[1L]?.lastUsedMs)
        assertEquals(now, rows[2L]?.lastUsedMs)
    }

    @Test
    fun muscleWeeklyVolumeComparisonExcludesWarmups() = runTest {
        val workoutId = createTestWorkout()
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60L * 60L * 1000L
        val currentWeekStart = now - (7L * dayMs)
        val previousWeekStart = now - (14L * dayMs)

        setDao.insertSet(testSet(workoutId, exerciseId = 1L, weight = 100f, reps = 10, timestamp = now - dayMs))
        setDao.insertSet(testSet(workoutId, exerciseId = 1L, weight = 50f, reps = 10, timestamp = now - (10L * dayMs)))
        setDao.insertSet(testSet(workoutId, exerciseId = 1L, weight = 500f, reps = 10, isWarmup = true, timestamp = now - (10L * dayMs)))

        val rows = setDao.getMuscleWeeklyVolumeComparison(
            previousWeekStartMs = previousWeekStart,
            currentWeekStartMs = currentWeekStart,
            nowMs = now + 1L
        )

        val chest = rows.single { it.muscle == "Chest" }
        assertEquals(1000f, chest.currentWeekVolume, 0.1f)
        assertEquals(500f, chest.previousWeekVolume, 0.1f)
    }

    @Test
    fun deleteSetRemovesFromDatabase() = runTest {
        val workoutId = createTestWorkout()
        val testSet = Set(
            workoutId = workoutId, exerciseId = 1L, weight = 135f, reps = 10,
            rpe = null, durationSeconds = null, distanceMeters = null,
            isWarmup = false, isComplete = true, timestamp = Date(),
            note = null, supersetGroupId = null, supersetOrderIndex = 0
        )

        val setId = setDao.insertSet(testSet)
        setDao.deleteSet(testSet.copy(id = setId))

        val sets = setDao.getSetsForWorkout(workoutId).first()
        assertTrue(sets.isEmpty())
    }

    @Test
    fun performanceQueryReturnsOnlyCompletedWorkingSets() = runTest {
        val workoutId = createTestWorkout()
        val now = System.currentTimeMillis()
        val workout = workoutDao.getWorkoutByIdSync(workoutId)!!
        workoutDao.updateWorkout(workout.copy(endTime = Date(now + 1L)))
        setDao.insertSet(testSet(workoutId, 1L, 100f, 8, timestamp = now - 2L))
        setDao.insertSet(testSet(workoutId, 1L, 200f, 8, isWarmup = true, timestamp = now - 1L))
        setDao.insertSet(testSet(workoutId, 1L, 300f, 8, isComplete = false, timestamp = now))

        val result = setDao.getPerformanceSetsWithExerciseInRange(now - 10L, now + 10L)

        assertEquals(1, result.size)
        assertEquals(100f, result.single().set.weight)
    }

    @Test
    fun performanceQueryExcludesActiveAndAbandonedWorkoutsUntilFinished() = runTest {
        exerciseDao.insertExercise(testExercise)
        val now = System.currentTimeMillis()
        val completed = Workout(startTime = Date(now - 1000L), endTime = Date(now), name = null, note = null)
        val active = completed.copy(endTime = null, name = "Current workout")
        val abandoned = completed.copy(startTime = Date(now - 100_000L), endTime = null, name = "Abandoned")
        val finishedId = workoutDao.insertWorkout(completed)
        val activeId = workoutDao.insertWorkout(active)
        val abandonedId = workoutDao.insertWorkout(abandoned)
        setDao.insertSet(testSet(finishedId, 1L, 100f, 8, timestamp = now - 3L))
        setDao.insertSet(testSet(activeId, 1L, 200f, 8, timestamp = now - 2L))
        setDao.insertSet(testSet(abandonedId, 1L, 300f, 8, timestamp = now - 1L))

        val beforeFinish = setDao.getPerformanceSetsWithExerciseInRange(now - 10L, now + 10L)
        assertEquals(listOf(finishedId), beforeFinish.map { it.set.workoutId })

        workoutDao.updateWorkout(active.copy(id = activeId, endTime = Date(now)))
        val afterFinish = setDao.getPerformanceSetsWithExerciseInRange(now - 10L, now + 10L)
        assertEquals(listOf(finishedId, activeId), afterFinish.map { it.set.workoutId })
    }

    @Test
    fun analyticsInsightQueryReturnsOnlyCompletedWorkingSetsWithLocalBucketingFields() = runTest {
        val workoutId = createTestWorkout()
        val now = System.currentTimeMillis()
        setDao.insertSet(testSet(workoutId, 1L, 100f, 8, timestamp = now - 2L))
        setDao.insertSet(testSet(workoutId, 1L, 200f, 8, isWarmup = true, timestamp = now - 1L))
        setDao.insertSet(testSet(workoutId, 1L, 300f, 8, isComplete = false, timestamp = now))

        val result = setDao.getAnalyticsInsightSets(now - 10L, now + 10L)

        assertEquals(1, result.size)
        assertEquals(workoutId, result.single().workoutId)
        assertEquals("Chest", result.single().muscle)
        assertEquals(now - 2L, result.single().timestampMs)
    }

    @Test
    fun fullExerciseHistoryIsUncappedAndUsesWorkoutDates() = runTest {
        exerciseDao.insertExercise(testExercise)
        exerciseDao.insertExercise(testExercise.copy(id = 2L, name = "Row"))
        val earlierWorkout = Workout(startTime = Date(1_000L), endTime = Date(2_000L), name = null, note = null)
        val laterWorkout = Workout(startTime = Date(3_000L), endTime = null, name = null, note = null)
        val earlierId = workoutDao.insertWorkout(earlierWorkout)
        val laterId = workoutDao.insertWorkout(laterWorkout)
        repeat(60) { index ->
            setDao.insertSet(testSet(earlierId, 1L, 100f, 8, timestamp = 10_000L + index))
        }
        // Warmups and unfinished rows are still part of full history, in timestamp order.
        val warmupId = setDao.insertSet(testSet(laterId, 1L, 45f, 8, isWarmup = true, timestamp = 501L))
        val unfinishedId = setDao.insertSet(testSet(laterId, 1L, 105f, 8, isComplete = false, timestamp = 500L))
        setDao.insertSet(testSet(laterId, 2L, 500f, 8, timestamp = 499L))

        val history = setDao.observeFullExerciseHistory(1L).first()

        assertEquals(62, history.size)
        assertEquals(listOf(unfinishedId, warmupId), history.take(2).map { it.set.id })
        assertEquals(laterWorkout.startTime, history.first().workoutStartTime)
        assertNull(history.first().workoutEndTime)
        assertEquals(earlierWorkout.startTime, history.last().workoutStartTime)
        assertEquals(earlierWorkout.endTime, history.last().workoutEndTime)
        assertTrue(history.all { it.set.exerciseId == 1L })
    }

    @Test
    fun fullExerciseHistoryReactsToSetEditsWorkoutCompletionAndDeletion() = runBlocking {
        exerciseDao.insertExercise(testExercise)
        val workout = Workout(startTime = Date(1_000L), endTime = null, name = null, note = null)
        val workoutId = workoutDao.insertWorkout(workout)
        val original = testSet(workoutId, 1L, 100f, 8, timestamp = 1_100L)
        val setId = setDao.insertSet(original)
        val observations = Channel<List<ExerciseHistorySet>>(Channel.UNLIMITED)
        val collection = launch {
            setDao.observeFullExerciseHistory(1L).collect { observations.send(it) }
        }
        suspend fun awaitHistory(predicate: (List<ExerciseHistorySet>) -> Boolean): List<ExerciseHistorySet> =
            withTimeout(5_000L) {
                var rows = observations.receive()
                while (!predicate(rows)) rows = observations.receive()
                rows
            }
        try {
            assertEquals(100f, awaitHistory { it.size == 1 }.single().set.weight)

            setDao.updateSet(original.copy(id = setId, weight = 110f, rpe = 8f, note = "Edited note"))
            val updated = awaitHistory { it.singleOrNull()?.set?.weight == 110f }.single().set
            assertEquals(8f, updated.rpe)
            assertEquals("Edited note", updated.note)

            val completionTime = Date(2_000L)
            workoutDao.updateWorkout(workout.copy(id = workoutId, endTime = completionTime))
            assertEquals(completionTime, awaitHistory { it.singleOrNull()?.workoutEndTime == completionTime }.single().workoutEndTime)

            setDao.deleteSetById(setId)
            assertTrue(awaitHistory { it.isEmpty() }.isEmpty())
        } finally {
            collection.cancel()
            observations.close()
        }
    }

    private fun testSet(
        workoutId: Long,
        exerciseId: Long,
        weight: Float,
        reps: Int,
        isWarmup: Boolean = false,
        isComplete: Boolean = true,
        timestamp: Long
    ): Set {
        return Set(
            workoutId = workoutId,
            exerciseId = exerciseId,
            weight = weight,
            reps = reps,
            rpe = null,
            durationSeconds = null,
            distanceMeters = null,
            isWarmup = isWarmup,
            isComplete = isComplete,
            timestamp = Date(timestamp),
            note = null,
            supersetGroupId = null,
            supersetOrderIndex = 0
        )
    }
}
