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
import java.text.SimpleDateFormat
import java.util.*

@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var database: GymTimeDatabase
    private lateinit var workoutDao: WorkoutDao
    private lateinit var setDao: SetDao
    private lateinit var exerciseDao: ExerciseDao

    private val testExercise = Exercise(
        id = 1L,
        name = "Squat",
        targetMuscle = "Legs",
        logType = LogType.WEIGHT_REPS,
        isCustom = false,
        notes = null,
        defaultRestSeconds = 120
    )

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GymTimeDatabase::class.java
        ).allowMainThreadQueries().build()

        workoutDao = database.workoutDao()
        setDao = database.setDao()
        exerciseDao = database.exerciseDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveWorkout() = runTest {
        val workout = Workout(
            startTime = Date(),
            endTime = null,
            name = "Morning Workout",
            note = "Felt good",
            rating = null,
            ratingNote = null,
            routineDayId = null
        )

        val workoutId = workoutDao.insertWorkout(workout)
        val retrieved = workoutDao.getWorkoutById(workoutId).first()

        assertEquals("Morning Workout", retrieved.name)
        assertEquals("Felt good", retrieved.note)
    }

    @Test
    fun getOngoingWorkoutReturnsOpenWorkout() = runTest {
        val openWorkout = Workout(
            startTime = Date(),
            endTime = null,
            name = "In Progress",
            note = null,
            rating = null,
            ratingNote = null,
            routineDayId = null
        )

        workoutDao.insertWorkout(openWorkout)
        val ongoing = workoutDao.getOngoingWorkout().first()

        assertNotNull(ongoing)
        assertEquals("In Progress", ongoing?.name)
        assertNull(ongoing?.endTime)
    }

    @Test
    fun getOngoingWorkoutReturnsNullWhenNoneOpen() = runTest {
        val closedWorkout = Workout(
            startTime = Date(System.currentTimeMillis() - 3600000),
            endTime = Date(),
            name = "Completed",
            note = null,
            rating = null,
            ratingNote = null,
            routineDayId = null
        )

        workoutDao.insertWorkout(closedWorkout)
        val ongoing = workoutDao.getOngoingWorkout().first()

        assertNull(ongoing)
    }

    @Test
    fun updateWorkoutPersistsChanges() = runTest {
        val workout = Workout(
            startTime = Date(),
            endTime = null,
            name = "Test",
            note = null,
            rating = null,
            ratingNote = null,
            routineDayId = null
        )

        val workoutId = workoutDao.insertWorkout(workout)
        val updatedWorkout = workout.copy(
            id = workoutId,
            endTime = Date(),
            rating = 5,
            ratingNote = "Great session!"
        )

        workoutDao.updateWorkout(updatedWorkout)
        val retrieved = workoutDao.getWorkoutById(workoutId).first()

        assertEquals(5, retrieved.rating)
        assertEquals("Great session!", retrieved.ratingNote)
        assertNotNull(retrieved.endTime)
    }

    @Test
    fun getYearToDateWorkoutCountReturnsCorrectCount() = runTest {
        // Create a workout this year
        val thisYear = Calendar.getInstance()
        thisYear.set(Calendar.MONTH, Calendar.JANUARY)
        thisYear.set(Calendar.DAY_OF_MONTH, 15)

        // Insert an exercise to be referenced by sets
        exerciseDao.insertExercise(testExercise)

        val workout1 = Workout(
            startTime = thisYear.time,
            endTime = Date(thisYear.timeInMillis + 3600000),
            name = "YTD Workout",
            note = null,
            rating = null,
            ratingNote = null,
            routineDayId = null
        )

        workoutDao.insertWorkout(workout1)
        
        // Add working sets to ensure they are counted
        val workoutId1 = workoutDao.insertWorkout(workout1)
        setDao.insertSet(Set(
            workoutId = workoutId1,
            exerciseId = 1L,
            weight = 100f,
            reps = 10,
            rpe = null,
            durationSeconds = null,
            distanceMeters = null,
            isWarmup = false,
            isComplete = true,
            timestamp = Date(),
            note = null,
            supersetGroupId = null,
            supersetOrderIndex = 0
        ))

        val workout2 = workout1.copy(id = 0, startTime = Date(), name = "YTD Workout 2")
        val workoutId2 = workoutDao.insertWorkout(workout2)
        setDao.insertSet(Set(
            workoutId = workoutId2,
            exerciseId = 1L,
            weight = 100f,
            reps = 10,
            rpe = null,
            durationSeconds = null,
            distanceMeters = null,
            isWarmup = false,
            isComplete = true,
            timestamp = Date(),
            note = null,
            supersetGroupId = null,
            supersetOrderIndex = 0
        ))

        val count = workoutDao.getYearToDateWorkoutCount()
        assertEquals(2, count)
    }

    @Test
    fun getWorkoutDatesWithWorkingSetsReturnsOnlyDatesWithSets() = runTest {
        exerciseDao.insertExercise(testExercise)

        // Workout with working sets
        val workout1 = Workout(
            startTime = Date(),
            endTime = Date(),
            name = "With Sets",
            note = null,
            rating = null,
            ratingNote = null,
            routineDayId = null
        )
        val workoutId1 = workoutDao.insertWorkout(workout1)

        // Add a working set
        setDao.insertSet(Set(
            workoutId = workoutId1,
            exerciseId = 1L,
            weight = 225f,
            reps = 5,
            rpe = null,
            durationSeconds = null,
            distanceMeters = null,
            isWarmup = false,
            isComplete = true,
            timestamp = Date(),
            note = null,
            supersetGroupId = null,
            supersetOrderIndex = 0
        ))

        // Workout with only warmup sets
        val workout2 = Workout(
            startTime = Date(),
            endTime = Date(),
            name = "Warmup Only",
            note = null,
            rating = null,
            ratingNote = null,
            routineDayId = null
        )
        val workoutId2 = workoutDao.insertWorkout(workout2)

        setDao.insertSet(Set(
            workoutId = workoutId2,
            exerciseId = 1L,
            weight = 135f,
            reps = 10,
            rpe = null,
            durationSeconds = null,
            distanceMeters = null,
            isWarmup = true,
            isComplete = true,
            timestamp = Date(),
            note = null,
            supersetGroupId = null,
            supersetOrderIndex = 0
        ))

        val dates = workoutDao.getWorkoutDatesWithWorkingSets()

        // Should only return the date of the workout with working sets
        assertEquals(1, dates.size)
    }
}
