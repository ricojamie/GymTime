package com.example.gymtime.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ExercisePickerDaoTest {
    private lateinit var database: GymTimeDatabase
    private lateinit var exercises: ExerciseDao
    private lateinit var workouts: WorkoutDao
    private lateinit var sets: SetDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GymTimeDatabase::class.java
        ).allowMainThreadQueries().build()
        exercises = database.exerciseDao()
        workouts = database.workoutDao()
        sets = database.setDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun summariesExcludeActiveWorkoutsAndReturnOneRowPerExercise() = runTest {
        val bench = insertExercise("Bench press")
        val row = insertExercise("Row")
        val unused = insertExercise("Unused exercise")
        val finished = insertWorkout(1_000L)
        val active = insertWorkout(2_000L, finished = false)
        val benchSet = sets.insertSet(testSet(finished, bench, 1_010L))
        val rowSet = sets.insertSet(testSet(finished, row, 1_020L))
        sets.insertSet(testSet(active, bench, 2_010L, weight = 200f))
        sets.insertSet(testSet(active, unused, 2_020L))

        val summaries = exercises.observeLastWorkoutSets().first().associateBy { it.set.exerciseId }

        assertEquals(setOf(bench, row), summaries.keys)
        assertEquals(benchSet, summaries.getValue(bench).set.id)
        assertEquals(rowSet, summaries.getValue(row).set.id)
        assertEquals(Date(1_000L), summaries.getValue(bench).workoutStartTime)
    }

    @Test
    fun latestWorkoutUsesWorkoutStartInsteadOfSetTimestampOrCompletionTime() = runTest {
        val exercise = insertExercise("Bench press")
        val older = workouts.insertWorkout(workout(1_000L).copy(endTime = Date(9_000L)))
        val newer = insertWorkout(2_000L)
        // Imported sets can have timestamps that disagree with their workout dates.
        sets.insertSet(testSet(older, exercise, 20_000L, weight = 300f))
        val expected = sets.insertSet(testSet(newer, exercise, 500L, weight = 100f))

        val summary = exercises.observeLastWorkoutSets().first().single()

        assertEquals(expected, summary.set.id)
        assertEquals(Date(2_000L), summary.workoutStartTime)
    }

    @Test
    fun tiedWorkoutDatesUseTheHigherWorkoutId() = runTest {
        val exercise = insertExercise("Bench press")
        val firstWorkout = insertWorkout(1_000L)
        val secondWorkout = insertWorkout(1_000L)
        sets.insertSet(testSet(firstWorkout, exercise, 9_000L))
        val expected = sets.insertSet(testSet(secondWorkout, exercise, 2_000L))

        assertEquals(expected, exercises.observeLastWorkoutSets().first().single().set.id)
    }

    @Test
    fun warmupOnlyOrIncompleteOnlyLatestWorkoutDoesNotFallBackToOlderStats() = runTest {
        val bench = insertExercise("Bench press")
        val row = insertExercise("Row")
        val squat = insertExercise("Squat")
        val older = insertWorkout(1_000L)
        val newer = insertWorkout(2_000L)
        sets.insertSet(testSet(older, bench, 1_010L))
        sets.insertSet(testSet(older, row, 1_020L))
        val squatSet = sets.insertSet(testSet(older, squat, 1_030L))
        sets.insertSet(testSet(newer, bench, 2_010L).copy(isWarmup = true))
        sets.insertSet(testSet(newer, row, 2_020L).copy(isComplete = false))
        // A newer finished workout with no sets for squat must not hide its summary.
        insertWorkout(3_000L)

        val summaries = exercises.observeLastWorkoutSets().first()

        assertEquals(listOf(squatSet), summaries.map { it.set.id })
    }

    @Test
    fun latestEligibleSetUsesTimestampThenIdAndKeepsItsExactFields() = runTest {
        val exercise = insertExercise("Bench press")
        val workout = insertWorkout(1_000L)
        sets.insertSet(testSet(workout, exercise, 1_010L, weight = 250f))
        sets.insertSet(testSet(workout, exercise, 1_020L, weight = 200f))
        val lastSet = testSet(workout, exercise, 1_020L, weight = 175f).copy(
            reps = 6,
            rpe = 8.5f,
            note = "Paused reps"
        )
        val expected = sets.insertSet(lastSet)
        sets.insertSet(testSet(workout, exercise, 1_030L).copy(isWarmup = true))
        sets.insertSet(testSet(workout, exercise, 1_040L).copy(isComplete = false))

        val summary = exercises.observeLastWorkoutSets().first().single()

        assertEquals(lastSet.copy(id = expected), summary.set)
    }

    @Test
    fun summariesReactToSetEditsWorkoutCompletionAndDeletion() = runBlocking {
        val exercise = insertExercise("Bench press")
        val originalWorkout = insertWorkout(1_000L)
        val originalSet = testSet(originalWorkout, exercise, 1_010L)
        val originalSetId = sets.insertSet(originalSet)
        val activeWorkout = workout(2_000L, finished = false)
        val activeWorkoutId = workouts.insertWorkout(activeWorkout)
        val newerSetId = sets.insertSet(testSet(activeWorkoutId, exercise, 2_010L, weight = 125f))
        val observations = Channel<List<ExerciseLastSetRow>>(Channel.UNLIMITED)
        val collection = launch {
            exercises.observeLastWorkoutSets().collect { observations.send(it) }
        }
        suspend fun awaitSummary(predicate: (List<ExerciseLastSetRow>) -> Boolean) =
            withTimeout(5_000L) {
                var rows = observations.receive()
                while (!predicate(rows)) rows = observations.receive()
                rows
            }
        try {
            assertEquals(originalSetId, awaitSummary { it.isNotEmpty() }.single().set.id)

            sets.updateSet(originalSet.copy(id = originalSetId, weight = 110f))
            assertEquals(110f, awaitSummary { it.singleOrNull()?.set?.weight == 110f }.single().set.weight)

            workouts.updateWorkout(activeWorkout.copy(id = activeWorkoutId, endTime = Date(2_100L)))
            val afterFinish = awaitSummary { it.singleOrNull()?.set?.id == newerSetId }.single()
            assertEquals(Date(2_000L), afterFinish.workoutStartTime)

            sets.deleteSetById(newerSetId)
            assertEquals(originalSetId, awaitSummary { it.singleOrNull()?.set?.id == originalSetId }.single().set.id)

            exercises.deleteExerciseById(exercise)
            assertTrue(awaitSummary { it.isEmpty() }.isEmpty())
        } finally {
            collection.cancel()
            observations.close()
        }
    }

    private suspend fun insertExercise(name: String): Long = exercises.insertExercise(
        Exercise(
            name = name,
            targetMuscle = "Chest",
            logType = LogType.WEIGHT_REPS,
            isCustom = true,
            notes = null,
            defaultRestSeconds = 90
        )
    )

    private fun workout(startTime: Long, finished: Boolean = true) = Workout(
        startTime = Date(startTime),
        endTime = if (finished) Date(startTime + 100L) else null,
        name = null,
        note = null
    )

    private suspend fun insertWorkout(startTime: Long, finished: Boolean = true): Long =
        workouts.insertWorkout(workout(startTime, finished))

    private fun testSet(workoutId: Long, exerciseId: Long, timestamp: Long, weight: Float = 100f) = Set(
        workoutId = workoutId,
        exerciseId = exerciseId,
        weight = weight,
        reps = 8,
        rpe = null,
        durationSeconds = null,
        distanceMeters = null,
        isWarmup = false,
        isComplete = true,
        timestamp = Date(timestamp)
    )
}
