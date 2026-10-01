package com.example.gymtime.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
}
