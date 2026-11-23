package com.example.gymtime.util

import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class FitNotesImporterTest {

    private lateinit var database: GymTimeDatabase
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var workoutDao: WorkoutDao
    private lateinit var setDao: SetDao

    private val storedExercises = mutableListOf<Exercise>()
    private val storedWorkouts = mutableListOf<Workout>()
    private val storedSets = mutableListOf<Set>()
    private var nextExerciseId = 1L
    private var nextWorkoutId = 1L
    private var nextSetId = 1L
    private var transactionCalls = 0

    private lateinit var importer: FitNotesImporter

    @Before
    fun setup() {
        storedExercises.clear()
        storedWorkouts.clear()
        storedSets.clear()
        nextExerciseId = 1L
        nextWorkoutId = 1L
        nextSetId = 1L
        transactionCalls = 0

        database = mockk(relaxed = true)
        exerciseDao = mockk(relaxed = true)
        workoutDao = mockk(relaxed = true)
        setDao = mockk(relaxed = true)

        val nameSlot = slot<String>()
        coEvery { exerciseDao.getExerciseByName(capture(nameSlot)) } answers {
            storedExercises.firstOrNull { it.name.equals(nameSlot.captured, ignoreCase = true) }
        }
        val exerciseSlot = slot<Exercise>()
        coEvery { exerciseDao.insertExercise(capture(exerciseSlot)) } answers {
            val saved = exerciseSlot.captured.copy(id = nextExerciseId++)
            storedExercises.add(saved)
            saved.id
        }

        val workoutSlot = slot<Workout>()
        coEvery { workoutDao.insertWorkout(capture(workoutSlot)) } answers {
            val saved = workoutSlot.captured.copy(id = nextWorkoutId++)
            storedWorkouts.add(saved)
            saved.id
        }

        val setSlot = slot<Set>()
        coEvery { setDao.insertSet(capture(setSlot)) } answers {
            val saved = setSlot.captured.copy(id = nextSetId++)
            storedSets.add(saved)
            saved.id
        }

        val startSlot = slot<Long>()
        val endSlot = slot<Long>()
        coEvery {
            setDao.getSetsWithExerciseInRange(capture(startSlot), capture(endSlot), any(), any())
        } answers {
            storedSets
                .filter { it.timestamp.time in startSlot.captured..endSlot.captured }
                .map { s ->
                    val ex = storedExercises.first { it.id == s.exerciseId }
                    SetWithExerciseInfo(set = s, exerciseName = ex.name, targetMuscle = ex.targetMuscle)
                }
        }

        importer = FitNotesImporter(
            database = database,
            exerciseDao = exerciseDao,
            workoutDao = workoutDao,
            setDao = setDao,
            runInTransaction = { block ->
                transactionCalls++
                block()
            }
        )
    }

    @Test
    fun `imports all identical straight sets on first import`() = runTest {
        val csv = """
            Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time
            2025-01-20,Bench Press,Chest,225.0,lbs,5,,,
            2025-01-20,Bench Press,Chest,225.0,lbs,5,,,
            2025-01-20,Bench Press,Chest,225.0,lbs,5,,,
        """.trimIndent()

        val result = importer.importCsv(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))

        assertEquals(1, transactionCalls)
        assertEquals(1, result.workoutsImported)
        assertEquals(3, result.setsImported)
        assertEquals(1, result.exercisesCreated)
        assertEquals(0, result.duplicatesSkipped)
        assertTrue(result.errors.isEmpty())
        assertEquals(1, storedWorkouts.size)
        assertEquals(3, storedSets.size)
    }

    @Test
    fun `reimporting same csv skips duplicates and leaves zero orphan workouts`() = runTest {
        val csv = """
            Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time
            2025-01-20,Bench Press,Chest,225.0,lbs,5,,,
            2025-01-20,Bench Press,Chest,225.0,lbs,5,,,
            2025-01-20,Bench Press,Chest,225.0,lbs,5,,,
        """.trimIndent()

        importer.importCsv(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))
        val secondResult = importer.importCsv(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))

        assertEquals(0, secondResult.workoutsImported)
        assertEquals(0, secondResult.setsImported)
        assertEquals(0, secondResult.exercisesCreated)
        assertEquals(3, secondResult.duplicatesSkipped)
        assertEquals(1, storedWorkouts.size)
        assertEquals(3, storedSets.size)

        // Adding a 4th set imports only that 1 extra set
        val extendedCsv = csv + "\n2025-01-20,Bench Press,Chest,225.0,lbs,5,,,"
        val thirdResult = importer.importCsv(ByteArrayInputStream(extendedCsv.toByteArray(Charsets.UTF_8)))

        assertEquals(1, thirdResult.workoutsImported)
        assertEquals(1, thirdResult.setsImported)
        assertEquals(3, thirdResult.duplicatesSkipped)
        assertEquals(4, storedSets.size)
    }

    @Test
    fun `parses RFC-4180 quoted commas escaped quotes and embedded newlines`() = runTest {
        val csv = "Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time\n" +
            "2025-01-21,\"Row, \"\"Barbell\"\"\",\"Back\nUpper\",185.0,lbs,8,,,\n"

        val result = importer.importCsv(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))

        assertEquals(1, result.workoutsImported)
        assertEquals(1, result.setsImported)
        assertTrue(result.errors.isEmpty())
        val exercise = storedExercises.single()
        assertEquals("Row, \"Barbell\"", exercise.name)
        assertEquals("Back\nUpper", exercise.targetMuscle)
        assertEquals(185f, storedSets.single().weight!!, 0.001f)
        assertEquals(8, storedSets.single().reps)
    }

    @Test
    fun `differentiates cardio sets by duration and distance and converts miles using exact constant`() = runTest {
        val csv = """
            Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time
            2025-01-22,Treadmill Run,Cardio,,,,1.0,mi,08:00
            2025-01-22,Treadmill Run,Cardio,,,,2.0,mi,17:30
        """.trimIndent()

        val result = importer.importCsv(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))

        assertEquals(1, result.workoutsImported)
        assertEquals(2, result.setsImported)
        assertEquals(0, result.duplicatesSkipped)
        assertEquals(480, storedSets[0].durationSeconds)
        assertEquals(1050, storedSets[1].durationSeconds)
        assertEquals(DistanceUnit.MILES, storedSets[0].distanceUnit)
        assertNotNull(storedSets[0].distanceMeters)
        assertEquals(1609.344f, storedSets[0].distanceMeters!!, 0.001f)

        // Re-importing skips both without orphan workout
        val secondResult = importer.importCsv(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))
        assertEquals(0, secondResult.workoutsImported)
        assertEquals(2, secondResult.duplicatesSkipped)
    }
}
