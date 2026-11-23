package com.example.gymtime.util

import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.WorkoutPlanDao
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutExerciseInstance
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class IronLogBackupRoundTripTest {

    private lateinit var sourceDb: FakeDataStore
    private lateinit var targetDb: FakeDataStore

    private class FakeDataStore {
        val database: GymTimeDatabase = mockk(relaxed = true)
        val exerciseDao: ExerciseDao = mockk(relaxed = true)
        val workoutDao: WorkoutDao = mockk(relaxed = true)
        val setDao: SetDao = mockk(relaxed = true)
        val routineDao: RoutineDao = mockk(relaxed = true)
        val muscleGroupDao: MuscleGroupDao = mockk(relaxed = true)
        val workoutPlanDao: WorkoutPlanDao = mockk(relaxed = true)

        val muscleGroups = mutableListOf<MuscleGroup>()
        val exercises = mutableListOf<Exercise>()
        val workouts = mutableListOf<Workout>()
        val sets = mutableListOf<Set>()
        val routines = mutableListOf<Routine>()
        val routineDays = mutableListOf<RoutineDay>()
        val routineExercises = mutableListOf<RoutineExercise>()
        val workoutInstances = mutableListOf<WorkoutExerciseInstance>()

        private var nextExerciseId = 100L
        private var nextWorkoutId = 200L
        private var nextSetId = 300L
        private var nextRoutineId = 400L
        private var nextRoutineDayId = 500L
        private var nextRoutineExerciseId = 600L
        private var nextInstanceId = 700L
        var transactionCount = 0

        init {
            coEvery { muscleGroupDao.getAllMuscleGroupNames() } answers { muscleGroups.map { it.name } }
            val mgCheckSlot = slot<String>()
            coEvery { muscleGroupDao.muscleGroupExists(capture(mgCheckSlot)) } answers {
                muscleGroups.count { it.name.equals(mgCheckSlot.captured, ignoreCase = true) }
            }
            val mgInsertSlot = slot<MuscleGroup>()
            coEvery { muscleGroupDao.insertMuscleGroup(capture(mgInsertSlot)) } answers {
                if (muscleGroups.none { it.name.equals(mgInsertSlot.captured.name, ignoreCase = true) }) {
                    muscleGroups.add(mgInsertSlot.captured)
                }
            }

            coEvery { exerciseDao.getAllExercisesSync() } answers { exercises.toList() }
            val exNameSlot = slot<String>()
            coEvery { exerciseDao.getExerciseByName(capture(exNameSlot)) } answers {
                exercises.firstOrNull { it.name.equals(exNameSlot.captured, ignoreCase = true) }
            }
            val exSlot = slot<Exercise>()
            coEvery { exerciseDao.insertExercise(capture(exSlot)) } answers {
                val saved = exSlot.captured.copy(id = nextExerciseId++)
                exercises.add(saved)
                saved.id
            }

            coEvery { workoutDao.getAllWorkoutsSync() } answers { workouts.toList() }
            val wSlot = slot<Workout>()
            coEvery { workoutDao.insertWorkout(capture(wSlot)) } answers {
                val saved = wSlot.captured.copy(id = nextWorkoutId++)
                workouts.add(saved)
                saved.id
            }

            coEvery { setDao.getAllSets() } answers { sets.toList() }
            val sSlot = slot<Set>()
            coEvery { setDao.insertSet(capture(sSlot)) } answers {
                val saved = sSlot.captured.copy(id = nextSetId++)
                sets.add(saved)
                saved.id
            }

            coEvery { routineDao.getAllRoutinesSync() } answers { routines.toList() }
            coEvery { routineDao.getActiveRoutineSync() } answers { routines.firstOrNull { it.isActive } }
            coEvery { routineDao.clearActiveRoutine() } answers {
                for (i in routines.indices) {
                    routines[i] = routines[i].copy(isActive = false)
                }
            }
            val rSlot = slot<Routine>()
            coEvery { routineDao.insertRoutine(capture(rSlot)) } answers {
                val saved = rSlot.captured.copy(id = nextRoutineId++)
                routines.add(saved)
                saved.id
            }

            coEvery { routineDao.getAllRoutineDays() } answers { routineDays.toList() }
            val rdSlot = slot<RoutineDay>()
            coEvery { routineDao.insertRoutineDay(capture(rdSlot)) } answers {
                val saved = rdSlot.captured.copy(id = nextRoutineDayId++)
                routineDays.add(saved)
                saved.id
            }

            coEvery { routineDao.getAllRoutineExercises() } answers { routineExercises.toList() }
            val reSlot = slot<RoutineExercise>()
            coEvery { routineDao.insertRoutineExercise(capture(reSlot)) } answers {
                val saved = reSlot.captured.copy(id = nextRoutineExerciseId++)
                routineExercises.add(saved)
            }

            coEvery { workoutPlanDao.getAllInstancesSync() } answers { workoutInstances.toList() }
            val instSlot = slot<WorkoutExerciseInstance>()
            coEvery { workoutPlanDao.insertInstance(capture(instSlot)) } answers {
                val saved = instSlot.captured.copy(id = nextInstanceId++)
                workoutInstances.add(saved)
                saved.id
            }
        }

        fun exporter(): IronLogExporter = IronLogExporter(
            exerciseDao = exerciseDao,
            workoutDao = workoutDao,
            setDao = setDao,
            routineDao = routineDao,
            muscleGroupDao = muscleGroupDao,
            workoutPlanDao = workoutPlanDao
        )

        fun importer(): IronLogImporter = IronLogImporter(
            database = database,
            exerciseDao = exerciseDao,
            workoutDao = workoutDao,
            setDao = setDao,
            routineDao = routineDao,
            muscleGroupDao = muscleGroupDao,
            workoutPlanDao = workoutPlanDao,
            runInTransaction = { block ->
                transactionCount++
                block()
            }
        )
    }

    @Before
    fun setup() {
        sourceDb = FakeDataStore()
        targetDb = FakeDataStore()
    }

    @Test
    fun `round trips multiline notes carriage returns quotes and workout exercise instances`() = runTest {
        sourceDb.muscleGroups.add(MuscleGroup("Back"))
        sourceDb.exercises.add(
            Exercise(
                id = 10L,
                name = "Row, \"Barbell\"",
                targetMuscle = "Back",
                logType = LogType.WEIGHT_REPS,
                defaultDistanceUnit = DistanceUnit.MILES,
                isCustom = true,
                notes = "Line 1\nLine 2\r\nLine \"3\"",
                defaultRestSeconds = 120,
                isStarred = true,
                repTarget = 8
            )
        )
        sourceDb.routines.add(Routine(id = 20L, name = "PPL", isActive = true, nextDayOrderIndex = 1))
        sourceDb.routineDays.add(RoutineDay(id = 30L, routineId = 20L, name = "Pull Day", orderIndex = 0))
        sourceDb.routineExercises.add(
            RoutineExercise(
                id = 40L,
                routineDayId = 30L,
                exerciseId = 10L,
                orderIndex = 0,
                targetSets = 4,
                targetRepsMin = 6,
                targetRepsMax = 10,
                targetRestSeconds = 120,
                notes = "Pause at top\nControl negative"
            )
        )
        sourceDb.workouts.add(
            Workout(
                id = 50L,
                startTime = Date(1_700_000_000_000L),
                endTime = Date(1_700_000_360_000L),
                name = "Pull, Heavy",
                note = "Felt strong\nSecond line\r\nThird \"quoted\" line",
                rating = 5,
                ratingNote = "PR day\rKeep volume",
                routineDayId = 30L,
                routineId = 20L,
                routineNameSnapshot = "PPL",
                routineDayNameSnapshot = "Pull Day",
                startedFromRoutine = true
            )
        )
        sourceDb.sets.add(
            Set(
                id = 60L,
                workoutId = 50L,
                exerciseId = 10L,
                weight = 185f,
                reps = 8,
                rpe = 8.5f,
                durationSeconds = null,
                distanceMeters = null,
                isWarmup = false,
                isComplete = true,
                timestamp = Date(1_700_000_060_000L),
                note = "Strict form,\nno straps"
            )
        )
        sourceDb.workoutInstances.add(
            WorkoutExerciseInstance(
                id = 70L,
                workoutId = 50L,
                exerciseId = 10L,
                routineExerciseId = 40L,
                orderIndex = 0,
                plannedSets = 4,
                repMin = 6,
                repMax = 10,
                restSeconds = 120,
                notes = "Instance note\nline 2",
                supersetGroupId = "ss-1",
                supersetOrderIndex = 1,
                isSkipped = false,
                addedDuringWorkout = true
            )
        )

        val zipBytes = ByteArrayOutputStream()
        val exportResult = sourceDb.exporter().export(zipBytes)
        assertEquals(1, exportResult.workoutExerciseInstanceCount)

        val importResult = targetDb.importer().import(ByteArrayInputStream(zipBytes.toByteArray()))
        assertEquals(1, targetDb.transactionCount)
        assertTrue(importResult.errors.isEmpty())
        assertEquals(1, importResult.exercisesImported)
        assertEquals(1, importResult.workoutsImported)
        assertEquals(1, importResult.setsImported)
        assertEquals(1, importResult.routinesImported)
        assertEquals(1, importResult.routineDaysImported)
        assertEquals(1, importResult.routineExercisesImported)
        assertEquals(1, importResult.workoutExerciseInstancesImported)

        val importedWorkout = targetDb.workouts.single()
        assertEquals("Felt strong\nSecond line\r\nThird \"quoted\" line", importedWorkout.note)
        assertEquals("PR day\rKeep volume", importedWorkout.ratingNote)
        assertEquals("Strict form,\nno straps", targetDb.sets.single().note)
        assertEquals("Line 1\nLine 2\r\nLine \"3\"", targetDb.exercises.single().notes)

        val importedInstance = targetDb.workoutInstances.single()
        assertEquals(importedWorkout.id, importedInstance.workoutId)
        assertEquals(targetDb.exercises.single().id, importedInstance.exerciseId)
        assertEquals(targetDb.routineExercises.single().id, importedInstance.routineExerciseId)
        assertEquals("Instance note\nline 2", importedInstance.notes)
        assertTrue(importedInstance.addedDuringWorkout)
    }

    @Test
    fun `exporter closes outputStream even when DAO query throws`() = runTest {
        coEvery { sourceDb.exerciseDao.getAllExercisesSync() } throws IllegalStateException("DB read error")

        var closed = false
        val trackingStream = object : ByteArrayOutputStream() {
            override fun close() {
                closed = true
                super.close()
            }
        }

        val thrown = runCatching { sourceDb.exporter().export(trackingStream) }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertTrue("outputStream must be closed on DAO failure", closed)
    }

    @Test
    fun `importer rejects NaN and Infinity floats and enforces single active routine`() = runTest {
        // Pre-existing active routine in target DB
        targetDb.routines.add(Routine(id = 1L, name = "Existing Active", isActive = true, nextDayOrderIndex = 0))

        val zipBytes = ByteArrayOutputStream()
        ZipOutputStream(zipBytes).use { zip ->
            zip.putNextEntry(ZipEntry("exercises.csv"))
            zip.write(
                (
                    "id,name,targetMuscle,logType,defaultDistanceUnit,isCustom,notes,defaultRestSeconds,isStarred,repTarget\n" +
                        "1,Squat,Legs,WEIGHT_REPS,MILES,false,,90,false,\n"
                    ).toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("routines.csv"))
            zip.write(
                (
                    "id,name,isActive,nextDayOrderIndex\n" +
                        "1,Routine A,true,0\n" +
                        "2,Routine B,true,1\n"
                    ).toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("workouts.csv"))
            zip.write(
                (
                    "id,startTime,endTime,name,note,rating,ratingNote,routineDayId,routineId,routineNameSnapshot,routineDayNameSnapshot,startedFromRoutine\n" +
                        "1,1700000000000,1700003600000,Leg Day,,,,,,,,false\n"
                    ).toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("sets.csv"))
            zip.write(
                (
                    "id,workoutId,exerciseId,weight,calories,reps,rpe,durationSeconds,distanceValue,distanceUnit,distanceMeters,isWarmup,isComplete,timestamp,note,supersetGroupId,supersetOrderIndex\n" +
                        "1,1,1,NaN,Infinity,5,-Infinity,,NaN,MILES,Infinity,false,true,1700000060000,,,0\n"
                    ).toByteArray()
            )
            zip.closeEntry()
        }

        val result = targetDb.importer().import(ByteArrayInputStream(zipBytes.toByteArray()))
        assertTrue(result.errors.isEmpty())
        assertEquals(0, result.workoutExerciseInstancesImported)

        // At most one active routine in DB across existing + imported routines
        assertEquals(1, targetDb.routines.count { it.isActive })
        assertEquals("Routine A", targetDb.routines.first { it.isActive }.name)

        // Non-finite floats become null
        val importedSet = targetDb.sets.single()
        assertNull(importedSet.weight)
        assertNull(importedSet.calories)
        assertNull(importedSet.rpe)
        assertNull(importedSet.distanceValue)
        assertNull(importedSet.distanceMeters)
    }

    @Test
    fun `importer handles legacy backup without workout_exercise_instances and out-of-order entries`() = runTest {
        val zipBytes = ByteArrayOutputStream()
        ZipOutputStream(zipBytes).use { zip ->
            // Put sets.csv & workouts.csv BEFORE exercises.csv & routines.csv (like legacy or alphabetical re-zip)
            zip.putNextEntry(ZipEntry("sets.csv"))
            zip.write(
                (
                    "id,workoutId,exerciseId,weight,calories,reps,rpe,durationSeconds,distanceValue,distanceUnit,distanceMeters,isWarmup,isComplete,timestamp,note,supersetGroupId,supersetOrderIndex\n" +
                        "1,10,5,135.0,,10,8.0,,,,,false,true,1700000060000,clean,,0\n"
                    ).toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("workouts.csv"))
            zip.write(
                (
                    "id,startTime,endTime,name,note,rating,ratingNote,routineDayId,routineId,routineNameSnapshot,routineDayNameSnapshot,startedFromRoutine\n" +
                        "10,1700000000000,1700003600000,Upper,,,,2,1,PPL,Push,true\n"
                    ).toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("routine_days.csv"))
            zip.write("id,routineId,name,orderIndex\n2,1,Push,0\n".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("routines.csv"))
            zip.write("id,name,isActive,nextDayOrderIndex\n1,PPL,true,0\n".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("exercises.csv"))
            zip.write("id,name,targetMuscle,logType,defaultDistanceUnit,isCustom,notes,defaultRestSeconds,isStarred,repTarget\n5,Bench Press,Chest,WEIGHT_REPS,MILES,false,,90,false,\n".toByteArray())
            zip.closeEntry()
        }

        val result = targetDb.importer().import(ByteArrayInputStream(zipBytes.toByteArray()))
        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.exercisesImported)
        assertEquals(1, result.workoutsImported)
        assertEquals(1, result.setsImported)
        assertEquals(1, result.routinesImported)
        assertEquals(1, result.routineDaysImported)
        assertEquals(0, result.workoutExerciseInstancesImported)
        assertEquals(targetDb.workouts.single().id, targetDb.sets.single().workoutId)
        assertEquals(targetDb.exercises.single().id, targetDb.sets.single().exerciseId)
        assertEquals(targetDb.routineDays.single().id, targetDb.workouts.single().routineDayId)
    }
}
