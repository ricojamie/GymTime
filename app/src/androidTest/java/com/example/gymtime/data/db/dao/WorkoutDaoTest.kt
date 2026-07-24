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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var database: GymTimeDatabase
    private lateinit var workoutDao: WorkoutDao
    private lateinit var setDao: SetDao
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var originalTimeZone: TimeZone

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
        originalTimeZone = TimeZone.getDefault()
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
        TimeZone.setDefault(originalTimeZone)
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

    @Test
    fun getWorkoutDatesWithWorkingSetsUsesLocalSetDatesAcrossUtcMidnight() = runTest {
        val zoneId = ZoneId.of("America/New_York")
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        exerciseDao.insertExercise(testExercise)

        val currentYear = ZonedDateTime.now(zoneId).year

        val workoutId1 = insertCompletedWorkout(
            startTime = localDateTime(currentYear, 7, 1, 23, 20, zoneId),
            endTime = localDateTime(currentYear, 7, 1, 23, 50, zoneId),
            name = "Late Session"
        )
        val workoutId2 = insertCompletedWorkout(
            startTime = localDateTime(currentYear, 7, 2, 0, 10, zoneId),
            endTime = localDateTime(currentYear, 7, 2, 0, 40, zoneId),
            name = "After Midnight"
        )

        insertWorkingSet(workoutId1, localDateTime(currentYear, 7, 1, 23, 30, zoneId), weight = 225f, reps = 5)
        insertWorkingSet(workoutId2, localDateTime(currentYear, 7, 2, 0, 30, zoneId), weight = 185f, reps = 8)

        val dates = workoutDao.getWorkoutDatesWithWorkingSets()

        assertEquals(listOf("$currentYear-07-02", "$currentYear-07-01"), dates)
    }

    @Test
    fun getYearToDateWorkoutCountUsesLocalWorkoutStartYearBoundaries() = runTest {
        val zoneId = ZoneId.of("America/New_York")
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        exerciseDao.insertExercise(testExercise)

        val currentYear = ZonedDateTime.now(zoneId).year

        val previousYearWorkoutId = insertCompletedWorkout(
            startTime = localDateTime(currentYear - 1, 12, 31, 23, 30, zoneId),
            endTime = localDateTime(currentYear, 1, 1, 0, 15, zoneId),
            name = "Previous Year Local"
        )
        insertWorkingSet(
            workoutId = previousYearWorkoutId,
            timestamp = localDateTime(currentYear - 1, 12, 31, 23, 40, zoneId),
            weight = 135f,
            reps = 10
        )

        val currentYearWorkoutId = insertCompletedWorkout(
            startTime = localDateTime(currentYear, 1, 1, 0, 30, zoneId),
            endTime = localDateTime(currentYear, 1, 1, 1, 15, zoneId),
            name = "Current Year Local"
        )
        insertWorkingSet(
            workoutId = currentYearWorkoutId,
            timestamp = localDateTime(currentYear, 1, 1, 0, 40, zoneId),
            weight = 185f,
            reps = 6
        )

        val warmupOnlyWorkoutId = insertCompletedWorkout(
            startTime = localDateTime(currentYear, 1, 2, 8, 0, zoneId),
            endTime = localDateTime(currentYear, 1, 2, 8, 30, zoneId),
            name = "Warmup Only"
        )
        setDao.insertSet(
            Set(
                workoutId = warmupOnlyWorkoutId,
                exerciseId = 1L,
                weight = 95f,
                reps = 12,
                rpe = null,
                durationSeconds = null,
                distanceMeters = null,
                isWarmup = true,
                isComplete = true,
                timestamp = localDateTime(currentYear, 1, 2, 8, 5, zoneId),
                note = null,
                supersetGroupId = null,
                supersetOrderIndex = 0
            )
        )

        assertEquals(1, workoutDao.getYearToDateWorkoutCount())
    }

    @Test
    fun getDailyVolumeForHeatMapGroupsByLocalSetDay() = runTest {
        val zoneId = ZoneId.of("America/New_York")
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        exerciseDao.insertExercise(testExercise)

        val currentYear = ZonedDateTime.now(zoneId).year
        val workoutId = insertCompletedWorkout(
            startTime = localDateTime(currentYear, 7, 1, 23, 0, zoneId),
            endTime = localDateTime(currentYear, 7, 2, 1, 0, zoneId),
            name = "Split Session"
        )

        insertWorkingSet(workoutId, localDateTime(currentYear, 7, 1, 23, 30, zoneId), weight = 200f, reps = 5)
        insertWorkingSet(workoutId, localDateTime(currentYear, 7, 2, 0, 30, zoneId), weight = 150f, reps = 4)
        setDao.insertSet(
            Set(
                workoutId = workoutId,
                exerciseId = 1L,
                weight = null,
                reps = null,
                rpe = null,
                durationSeconds = 600,
                distanceMeters = null,
                isWarmup = false,
                isComplete = true,
                timestamp = localDateTime(currentYear, 7, 2, 0, 45, zoneId),
                note = null,
                supersetGroupId = null,
                supersetOrderIndex = 0
            )
        )

        val dailyVolumes = workoutDao.getDailyVolumeForHeatMap()
        val july1 = localDateTime(currentYear, 7, 1, 0, 0, zoneId).time
        val july2 = localDateTime(currentYear, 7, 2, 0, 0, zoneId).time

        assertEquals(
            listOf(july1, july2),
            dailyVolumes
                .filter { it.date == july1 || it.date == july2 }
                .map { it.date }
        )
        assertEquals(1000f, dailyVolumes.first { it.date == july1 }.dailyVol, 0.001f)
        assertEquals(1, dailyVolumes.first { it.date == july1 }.workingSetCount)
        assertEquals(600f, dailyVolumes.first { it.date == july2 }.dailyVol, 0.001f)
        assertEquals(2, dailyVolumes.first { it.date == july2 }.workingSetCount)
    }

    private suspend fun insertCompletedWorkout(startTime: Date, endTime: Date, name: String): Long {
        return workoutDao.insertWorkout(
            Workout(
                startTime = startTime,
                endTime = endTime,
                name = name,
                note = null,
                rating = null,
                ratingNote = null,
                routineDayId = null
            )
        )
    }

    private suspend fun insertWorkingSet(workoutId: Long, timestamp: Date, weight: Float, reps: Int) {
        setDao.insertSet(
            Set(
                workoutId = workoutId,
                exerciseId = 1L,
                weight = weight,
                reps = reps,
                rpe = null,
                durationSeconds = null,
                distanceMeters = null,
                isWarmup = false,
                isComplete = true,
                timestamp = timestamp,
                note = null,
                supersetGroupId = null,
                supersetOrderIndex = 0
            )
        )
    }

    private fun localDateTime(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zoneId: ZoneId
    ): Date {
        return Date.from(
            ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId)
                .toInstant()
        )
    }
}
