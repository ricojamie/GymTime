package com.example.gymtime.util

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.WARMUP_MUSCLE_GROUP
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutExerciseInstance
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Date

/**
 * Real in-memory Room integration test for IronLog ZIP backup export & import:
 * exercises SQLite foreign-key constraints, Room `withTransaction`, starter-exercise
 * restoration, and idempotent re-import.
 */
@RunWith(AndroidJUnit4::class)
class IronLogBackupRoomIntegrationTest {

    private lateinit var sourceDb: GymTimeDatabase
    private lateinit var targetDb: GymTimeDatabase
    private lateinit var prefs: UserPreferencesRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        sourceDb = Room.inMemoryDatabaseBuilder(context, GymTimeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        targetDb = Room.inMemoryDatabaseBuilder(context, GymTimeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = UserPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun fullRoomRoundTripReplaceAndIdempotentMerge() = runTest {
        sourceDb.muscleGroupDao().insertAll(listOf(MuscleGroup("Chest"), MuscleGroup("Back"), MuscleGroup(WARMUP_MUSCLE_GROUP)))
        val benchId = sourceDb.exerciseDao().insertExercise(
            Exercise(
                name = "Barbell Bench Press",
                targetMuscle = "Chest",
                logType = LogType.WEIGHT_REPS,
                defaultDistanceUnit = DistanceUnit.MILES,
                isCustom = false,
                notes = "Competition pause",
                defaultRestSeconds = 240,
                isStarred = true,
                repTarget = 5
            )
        )
        val routineId = sourceDb.routineDao().insertRoutine(
            Routine(name = "Powerbuilding", isActive = true, nextDayOrderIndex = 0)
        )
        val dayId = sourceDb.routineDao().insertRoutineDay(
            RoutineDay(routineId = routineId, name = "Upper", orderIndex = 0)
        )
        val routineExId = sourceDb.routineDao().insertRoutineExercise(
            RoutineExercise(
                routineDayId = dayId,
                exerciseId = benchId,
                orderIndex = 0,
                targetSets = 4,
                targetRepsMin = 5,
                targetRepsMax = 5,
                targetRestSeconds = 240,
                notes = "Top set RPE 8"
            )
        )
        val workoutId = sourceDb.workoutDao().insertWorkout(
            Workout(
                startTime = Date(1_700_000_000_000L),
                endTime = Date(1_700_003_600_000L),
                name = "Upper",
                note = "Solid session",
                rating = 5,
                ratingNote = "New PR",
                routineDayId = dayId,
                routineId = routineId,
                routineNameSnapshot = "Powerbuilding",
                routineDayNameSnapshot = "Upper",
                startedFromRoutine = true
            )
        )
        sourceDb.setDao().insertSet(
            Set(
                workoutId = workoutId,
                exerciseId = benchId,
                weight = 225f,
                reps = 5,
                rpe = 8f,
                durationSeconds = null,
                distanceMeters = null,
                isWarmup = false,
                isComplete = true,
                timestamp = Date(1_700_000_300_000L),
                note = "Fast bar speed"
            )
        )
        sourceDb.workoutPlanDao().insertInstance(
            WorkoutExerciseInstance(
                workoutId = workoutId,
                exerciseId = benchId,
                routineExerciseId = routineExId,
                orderIndex = 0,
                plannedSets = 4,
                repMin = 5,
                repMax = 5,
                restSeconds = 240,
                notes = "Top set RPE 8"
            )
        )

        val exporter = IronLogExporter(
            database = sourceDb,
            exerciseDao = sourceDb.exerciseDao(),
            workoutDao = sourceDb.workoutDao(),
            setDao = sourceDb.setDao(),
            routineDao = sourceDb.routineDao(),
            muscleGroupDao = sourceDb.muscleGroupDao(),
            workoutPlanDao = sourceDb.workoutPlanDao(),
            userPreferencesRepository = prefs
        )
        val zipOut = ByteArrayOutputStream()
        exporter.export(zipOut)

        // Seed target DB with unedited starter exercise to mimic fresh install
        targetDb.muscleGroupDao().insertMuscleGroup(MuscleGroup("Chest"))
        targetDb.exerciseDao().insertExercise(
            Exercise(
                name = "Barbell Bench Press",
                targetMuscle = "Chest",
                logType = LogType.WEIGHT_REPS,
                isCustom = false,
                notes = null,
                defaultRestSeconds = 180,
                isStarred = false,
                repTarget = null
            )
        )

        val importer = IronLogImporter(
            database = targetDb,
            exerciseDao = targetDb.exerciseDao(),
            workoutDao = targetDb.workoutDao(),
            setDao = targetDb.setDao(),
            routineDao = targetDb.routineDao(),
            muscleGroupDao = targetDb.muscleGroupDao(),
            workoutPlanDao = targetDb.workoutPlanDao(),
            generatedNarrativeDao = targetDb.generatedNarrativeDao(),
            userPreferencesRepository = prefs,
            volumeOrbRepository = VolumeOrbRepository(targetDb.setDao())
        )

        val replaceResult = importer.import(
            ByteArrayInputStream(zipOut.toByteArray()),
            IronLogImporter.ImportMode.REPLACE
        )
        assertTrue(replaceResult.errors.isEmpty())
        assertEquals(1, targetDb.exerciseDao().getAllExercisesSync().size)
        val restoredBench = targetDb.exerciseDao().getAllExercisesSync().single()
        assertTrue(restoredBench.isStarred)
        assertEquals(5, restoredBench.repTarget)
        assertEquals("Competition pause", restoredBench.notes)
        assertEquals(1, targetDb.workoutDao().getAllWorkoutsSync().size)
        assertEquals(1, targetDb.setDao().getAllSets().size)
        assertEquals(1, targetDb.workoutPlanDao().getAllInstancesSync().size)
        assertEquals(
            targetDb.routineDao().getAllRoutineExercises().single().id,
            targetDb.workoutPlanDao().getAllInstancesSync().single().routineExerciseId
        )

        // Re-importing the exact same archive in MERGE mode must be a complete no-op
        val mergeAgain = importer.import(
            ByteArrayInputStream(zipOut.toByteArray()),
            IronLogImporter.ImportMode.MERGE
        )
        assertTrue(mergeAgain.errors.isEmpty())
        assertEquals(0, mergeAgain.workoutsImported)
        assertEquals(0, mergeAgain.setsImported)
        assertEquals(0, mergeAgain.routinesImported)
        assertEquals(1, targetDb.workoutDao().getAllWorkoutsSync().size)
        assertEquals(1, targetDb.setDao().getAllSets().size)
    }
}
