package com.example.gymtime.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutExerciseInstance
import com.example.gymtime.data.repository.ExerciseRepository
import com.example.gymtime.data.repository.MuscleGroupDeleteResult
import com.example.gymtime.data.repository.MuscleGroupMutationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/** Real Room coverage for the editor's atomic replace operation; root decides device execution. */
@RunWith(AndroidJUnit4::class)
class RoutineDaySaveTest {
    private lateinit var database: GymTimeDatabase
    private lateinit var repository: RoutineRepository
    private val exercise = Exercise(id = 1L, name = "Bench", targetMuscle = "Chest",
        logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90)

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),
            GymTimeDatabase::class.java).allowMainThreadQueries().build()
        repository = RoutineRepository(database, database.routineDao(), database.workoutDao(),
            database.workoutPlanDao(), database.setDao())
    }

    @After
    fun close() { database.close() }

    @Test
    fun failedReplacementRollsBackNameAndOriginalExercise() = runTest {
        database.exerciseDao().insertExercise(exercise)
        val routineId = repository.insertRoutine(Routine(name = "Plan"))
        val originalId = repository.insertRoutineDay(RoutineDay(routineId = routineId, name = "Original", orderIndex = 4))
        repository.insertRoutineExercise(RoutineExercise(routineDayId = originalId, exerciseId = exercise.id,
            orderIndex = 0, targetSets = 5, notes = "Do not lose this note"))
        var failed = false
        try {
            repository.saveRoutineDay(routineId, originalId, "Replacement", listOf(
                RoutineExercise(routineDayId = originalId, exerciseId = 9999L, orderIndex = 0)))
        } catch (_: Exception) { failed = true }
        assertTrue("Missing exercise must fail the transaction", failed)
        val restored = database.routineDao().getRoutineDayWithExercisesSync(originalId)!!
        assertEquals("Original", restored.day.name)
        assertEquals(4, restored.day.orderIndex)
        assertEquals(5, restored.exercises.single().routineExercise.targetSets)
        assertEquals("Do not lose this note", restored.exercises.single().routineExercise.notes)
    }

    @Test
    fun failedNewDayLeavesNoPartialDay() = runTest {
        val routineId = repository.insertRoutine(Routine(name = "Plan"))
        var failed = false
        try {
            repository.saveRoutineDay(routineId, null, "Never committed", listOf(
                RoutineExercise(routineDayId = 0L, exerciseId = 9999L, orderIndex = 0)))
        } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertTrue(database.routineDao().getDaysForRoutineSync(routineId).isEmpty())
    }

    @Test
    fun successfulSaveKeepsLinkageOrderFullNotesAndNullableDefaults() = runTest {
        database.exerciseDao().insertExercise(exercise)
        database.exerciseDao().insertExercise(exercise.copy(id = 2L, name = "Row"))
        val routineId = repository.insertRoutine(Routine(name = "Plan", isActive = true))
        val notes = "Existing notes ".repeat(800)
        val dayId = repository.saveRoutineDay(routineId, null, "Day", listOf(
            RoutineExercise(routineDayId = 0L, exerciseId = 2L, orderIndex = 9,
                notes = notes, supersetGroupId = "pair", supersetOrderIndex = 0),
            RoutineExercise(routineDayId = 0L, exerciseId = 1L, orderIndex = 12,
                targetRestSeconds = 0, supersetGroupId = "pair", supersetOrderIndex = 1)))
        val saved = database.routineDao().getRoutineDayWithExercisesSync(dayId)!!
        assertEquals(routineId, saved.day.routineId)
        val rows = saved.exercises.map { it.routineExercise }.sortedBy { it.orderIndex }
        assertEquals(listOf(2L, 1L), rows.map { it.exerciseId })
        assertEquals(listOf(0, 1), rows.map { it.orderIndex })
        assertEquals(notes, rows[0].notes)
        assertNull(rows[0].targetRestSeconds)
        assertEquals(0, rows[1].targetRestSeconds)
        assertEquals(rows[0].supersetGroupId, rows[1].supersetGroupId)
        assertEquals(0, database.routineDao().getRoutineByIdSync(routineId)!!.nextDayOrderIndex)
    }

    @Test
    fun wrongParentAndFullRoutineRejectWithoutChangingSavedDays() = runTest {
        database.exerciseDao().insertExercise(exercise)
        val first = repository.insertRoutine(Routine(name = "First"))
        val second = repository.insertRoutine(Routine(name = "Second"))
        val row = RoutineExercise(routineDayId = 0L, exerciseId = exercise.id, orderIndex = 0)
        val day = repository.saveRoutineDay(first, null, "Original", listOf(row))
        var wrongParent = false
        try { repository.saveRoutineDay(second, day, "Wrong", listOf(row)) }
        catch (_: IllegalArgumentException) { wrongParent = true }
        assertTrue(wrongParent)
        assertEquals("Original", database.routineDao().getRoutineDayWithExercisesSync(day)!!.day.name)
        repeat(9) { repository.saveRoutineDay(first, null, "Day $it", listOf(row)) }
        var capped = false
        try { repository.saveRoutineDay(first, null, "Extra", listOf(row)) }
        catch (_: IllegalArgumentException) { capped = true }
        assertTrue(capped)
        assertEquals(10, database.routineDao().getDaysForRoutineSync(first).size)
        var cloneCapped = false
        try { repository.duplicateDay(day) }
        catch (_: IllegalArgumentException) { cloneCapped = true }
        assertTrue(cloneCapped)
        assertEquals(10, database.routineDao().getDaysForRoutineSync(first).size)
    }

    @Test
    fun routineCloneCannotExceedCap() = runTest {
        val original = repository.insertRoutine(Routine(name = "Original"))
        repeat(9) { repository.insertRoutine(Routine(name = "Routine $it")) }
        var capped = false
        try { repository.duplicateRoutine(original) }
        catch (_: IllegalArgumentException) { capped = true }
        assertTrue(capped)
        assertEquals(10, database.routineDao().getAllRoutinesSync().size)
    }

    @Test
    fun insertRoutineEnforcesSingleActiveAndStartGuardsOngoingWorkout() = runTest {
        database.exerciseDao().insertExercise(exercise)
        val firstId = repository.insertRoutine(Routine(name = "First", isActive = true))
        val secondId = repository.insertRoutine(Routine(name = "Second", isActive = true))

        val routines = database.routineDao().getAllRoutinesSync().associateBy { it.id }
        assertFalse(routines.getValue(firstId).isActive)
        assertTrue(routines.getValue(secondId).isActive)

        val dayId = repository.saveRoutineDay(
            secondId,
            null,
            "Push",
            listOf(RoutineExercise(routineDayId = 0L, exerciseId = exercise.id, orderIndex = 0))
        )

        val summaries = repository.getRoutineBrowseSummaries().first().associateBy { it.id }
        assertEquals(1, summaries.getValue(secondId).dayCount)
        assertEquals(1, summaries.getValue(secondId).exerciseCount)
        assertEquals("Push", summaries.getValue(secondId).nextDayName)

        // Start ongoing workout and verify routine start methods fail cleanly without creating a duplicate.
        database.workoutDao().insertWorkout(Workout(startTime = Date(), endTime = null, name = "Open", note = null))

        var startDayBlocked = false
        try {
            repository.startRoutineDay(dayId)
        } catch (e: IllegalStateException) {
            startDayBlocked = e.message == "Finish your current workout first"
        }
        assertTrue(startDayBlocked)

        var startNextBlocked = false
        try {
            repository.startNextRoutineWorkout()
        } catch (e: IllegalStateException) {
            startNextBlocked = e.message == "Finish your current workout first"
        }
        assertTrue(startNextBlocked)
        assertEquals(1, database.workoutDao().getAllWorkoutsSync().size)
    }

    @Test
    fun createRoutineDayFromBlankWorkoutUsesFallbackNameAndDissolvesSingleSuperset() = runTest {
        database.exerciseDao().insertExercise(exercise)
        val routineId = repository.insertRoutine(Routine(name = "Plan"))
        val workoutId = database.workoutDao().insertWorkout(
            Workout(startTime = Date(1_700_000_000_000L), endTime = Date(1_700_000_100_000L), name = "   ", note = null)
        )
        database.setDao().insertSet(
            Set(
                workoutId = workoutId,
                exerciseId = exercise.id,
                weight = 185f,
                reps = 5,
                rpe = null,
                durationSeconds = null,
                distanceMeters = null,
                isWarmup = false,
                isComplete = true,
                timestamp = Date(1_700_000_010_000L),
                supersetGroupId = "solo-superset",
                supersetOrderIndex = 1
            )
        )

        val added = repository.createRoutineDayFromWorkout(workoutId, routineId) as AddToRoutineResult.Added
        val savedDay = database.routineDao().getRoutineDayWithExercisesSync(added.dayId)!!

        assertTrue(savedDay.day.name.startsWith("Workout "))
        assertNull(savedDay.exercises.single().routineExercise.supersetGroupId)
        assertEquals(0, savedDay.exercises.single().routineExercise.supersetOrderIndex)
    }

    @Test
    fun deleteExerciseCleansOrphanedSupersetsAndMuscleGroupHelpersAreAtomic() = runTest {
        val ex2 = exercise.copy(id = 2L, name = "Flyes")
        database.exerciseDao().insertExercise(exercise)
        database.exerciseDao().insertExercise(ex2)
        val routineId = repository.insertRoutine(Routine(name = "Plan"))
        val dayId = repository.saveRoutineDay(
            routineId,
            null,
            "Push",
            listOf(
                RoutineExercise(routineDayId = 0L, exerciseId = exercise.id, orderIndex = 0, supersetGroupId = "g1", supersetOrderIndex = 0),
                RoutineExercise(routineDayId = 0L, exerciseId = ex2.id, orderIndex = 1, supersetGroupId = "g1", supersetOrderIndex = 1)
            )
        )
        val workoutId = database.workoutDao().insertWorkout(Workout(startTime = Date(), endTime = null, name = "W", note = null))
        val planId1 = database.workoutPlanDao().insertInstance(
            WorkoutExerciseInstance(workoutId = workoutId, exerciseId = exercise.id, orderIndex = 0, supersetGroupId = "wg1", supersetOrderIndex = 0)
        )
        val planId2 = database.workoutPlanDao().insertInstance(
            WorkoutExerciseInstance(workoutId = workoutId, exerciseId = ex2.id, orderIndex = 1, supersetGroupId = "wg1", supersetOrderIndex = 1)
        )

        val exerciseRepo = ExerciseRepository(database, database.exerciseDao(), database.setDao(), database.muscleGroupDao())
        exerciseRepo.deleteExercise(exercise.id)

        val remainingRoutineEx = database.routineDao().getRoutineDayWithExercisesSync(dayId)!!.exercises.single().routineExercise
        assertNull(remainingRoutineEx.supersetGroupId)
        assertEquals(0, remainingRoutineEx.supersetOrderIndex)
        val remainingPlan = database.workoutPlanDao().getInstanceById(planId2)!!
        assertNull(remainingPlan.supersetGroupId)
        assertEquals(0, remainingPlan.supersetOrderIndex)
        assertNull(database.workoutPlanDao().getInstanceById(planId1))

        database.muscleGroupDao().insertMuscleGroup(MuscleGroup("Chest"))
        assertEquals(MuscleGroupMutationResult.Success, exerciseRepo.renameMuscleGroup("chest", "Pecs"))
        assertEquals("Pecs", database.exerciseDao().getExerciseByIdSync(ex2.id)!!.targetMuscle)

        val deleteBlocked = exerciseRepo.deleteMuscleGroupSafely("Pecs", reassignTo = null)
        assertEquals(MuscleGroupDeleteResult.HasExercises(1), deleteBlocked)

        assertEquals(MuscleGroupDeleteResult.Deleted, exerciseRepo.deleteMuscleGroupSafely("pecs", reassignTo = "Uncategorized"))
        assertEquals("Uncategorized", database.exerciseDao().getExerciseByIdSync(ex2.id)!!.targetMuscle)
        assertTrue(database.muscleGroupDao().getAllMuscleGroupNames().contains("Uncategorized"))
    }
}
