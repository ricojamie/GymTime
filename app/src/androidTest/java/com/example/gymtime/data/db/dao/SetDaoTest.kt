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
    fun getVolumeInRangeReturnsVolumeInTimeRange() = runTest {
        val workoutId = createTestWorkout()
        val now = System.currentTimeMillis()

        setDao.insertSet(Set(
            workoutId = workoutId, exerciseId = 1L, weight = 200f, reps = 5,
            rpe = null, durationSeconds = null, distanceMeters = null,
            isWarmup = false, isComplete = true, timestamp = Date(now),
            note = null, supersetGroupId = null, supersetOrderIndex = 0
        ))

        val volumeInRange = setDao.getVolumeInRange(now - 10000, now + 10000)
        val volumeOutOfRange = setDao.getVolumeInRange(now + 20000, now + 30000)

        assertEquals(1000f, volumeInRange, 0.1f) // 200 * 5
        assertEquals(0f, volumeOutOfRange, 0.1f)
    }

    @Test
    fun getWorkoutVolumeReturnsVolumeForWorkout() = runTest {
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

        val volume = setDao.getWorkoutVolume(workoutId)

        assertEquals(2250f, volume, 0.1f) // (225*5) + (225*5)
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
}
