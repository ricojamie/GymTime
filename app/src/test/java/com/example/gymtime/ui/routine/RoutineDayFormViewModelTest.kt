package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.RoutineDayWithExercises
import com.example.gymtime.data.db.dao.RoutineExerciseWithDetails
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineDayFormViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val repository: RoutineRepository = mockk(relaxed = true)
    private val preferences: UserPreferencesRepository = mockk {
        every { newUiEnabled } returns flowOf(true)
    }
    private val exerciseDao: ExerciseDao = mockk()
    private val bench = Exercise(
        id = 11L,
        name = "Bench Press",
        targetMuscle = "Chest",
        logType = LogType.WEIGHT_REPS,
        isCustom = false,
        notes = null,
        defaultRestSeconds = 90
    )

    @Before
    fun setUp() {
        every { exerciseDao.getAllExercises() } returns flowOf(listOf(bench))
    }

    @Test
    fun `new day is clean after changes are fully reverted`() = runTest {
        val viewModel = RoutineDayFormViewModel(
            repository,
            exerciseDao,
            SavedStateHandle(mapOf("routineId" to 1L)), preferences
        )
        val job = launch { viewModel.hasUnsavedChanges.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.hasUnsavedChanges.value)

        viewModel.updateDayName("Push")
        viewModel.addExercise(bench.id)
        advanceUntilIdle()
        assertTrue(viewModel.hasUnsavedChanges.value)

        viewModel.removeExercise(bench.id)
        viewModel.updateDayName("")
        advanceUntilIdle()
        assertFalse(viewModel.hasUnsavedChanges.value)
        job.cancel()
    }

    @Test
    fun `loaded day edit starts clean and supports reverting targets`() = runTest {
        val day = RoutineDay(id = 3L, routineId = 1L, name = "Push", orderIndex = 0)
        val routineExercise = RoutineExercise(
            id = 4L,
            routineDayId = day.id,
            exerciseId = bench.id,
            orderIndex = 0,
            targetSets = 3
        )
        every { repository.getRoutineDayWithExercises(day.id) } returns flowOf(
            RoutineDayWithExercises(
                day = day,
                exercises = listOf(RoutineExerciseWithDetails(routineExercise, bench))
            )
        )
        val viewModel = RoutineDayFormViewModel(
            repository,
            exerciseDao,
            SavedStateHandle(mapOf("routineId" to 1L, "dayId" to "3")), preferences
        )
        val job = launch { viewModel.hasUnsavedChanges.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.hasUnsavedChanges.value)

        viewModel.updateTargetSets(bench.id, "4")
        advanceUntilIdle()
        assertTrue(viewModel.hasUnsavedChanges.value)

        viewModel.updateTargetSets(bench.id, "3")
        advanceUntilIdle()
        assertFalse(viewModel.hasUnsavedChanges.value)
        job.cancel()
    }

    @Test
    fun `rename preserves all loaded notes and targets in atomic save`() = runTest {
        val notes = "Existing note ".repeat(600)
        val day = RoutineDay(id = 3L, routineId = 1L, name = "Push", orderIndex = 4)
        val row = RoutineExercise(id = 4L, routineDayId = 3L, exerciseId = bench.id,
            orderIndex = 0, targetSets = 4, targetRepsMin = 8, targetRepsMax = 12, targetRestSeconds = 0, notes = notes)
        every { repository.getRoutineDayWithExercises(3L) } returns flowOf(
            RoutineDayWithExercises(day, listOf(RoutineExerciseWithDetails(row, bench))))
        val vm = RoutineDayFormViewModel(repository, exerciseDao,
            SavedStateHandle(mapOf("routineId" to 1L, "dayId" to "3")), preferences)
        advanceUntilIdle()
        vm.updateDayName("New name")
        vm.saveDay()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.saveRoutineDay(1L, 3L, "New name", match {
            it.single().notes == notes && it.single().targetSets == 4 && it.single().targetRestSeconds == 0 &&
                it.single().targetRepsMin == 8 && it.single().targetRepsMax == 12
        }) }
        coVerify(exactly = 0) { repository.deleteAllExercisesForDay(any()) }
    }

    @Test
    fun `failure keeps draft and second rapid tap never starts a second save`() = runTest {
        coEvery { repository.saveRoutineDay(any(), any(), any(), any()) } throws IllegalStateException("Disk full")
        val vm = RoutineDayFormViewModel(repository, exerciseDao, SavedStateHandle(mapOf("routineId" to 1L)), preferences)
        vm.updateDayName("Push")
        vm.addExercise(bench.id)
        vm.updateExerciseNotes(bench.id, "Keep my cues")
        vm.saveDay()
        vm.saveDay()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.saveRoutineDay(any(), any(), any(), any()) }
        assertFalse(vm.isSaving.value)
        assertEquals("Push", vm.dayName.value)
        assertEquals("Keep my cues", vm.notes.value[bench.id])
        assertNotNull(vm.error.value)
        coEvery { repository.saveRoutineDay(any(), any(), any(), any()) } returns 6L
        vm.saveDay()
        advanceUntilIdle()
        coVerify(exactly = 2) { repository.saveRoutineDay(any(), any(), any(), any()) }
    }

    @Test
    fun `holder recreation retains order targets notes superset and picker filter`() = runTest {
        val second = bench.copy(id = 12L, name = "Row", targetMuscle = "Back")
        every { exerciseDao.getAllExercises() } returns flowOf(listOf(bench, second))
        val handle = SavedStateHandle(mapOf("routineId" to 1L))
        val vm = RoutineDayFormViewModel(repository, exerciseDao, handle, preferences)
        vm.updateDayName("Day draft")
        vm.addExercise(bench.id)
        vm.addExercise(second.id)
        vm.updateTargetSets(bench.id, "5")
        vm.updateExerciseNotes(bench.id, "Pause")
        vm.toggleSupersetLink(0)
        vm.updatePickerQuery("Bench")
        vm.updatePickerMuscle("Chest")
        advanceUntilIdle()
        val restored = RoutineDayFormViewModel(repository, exerciseDao, handle, preferences)
        assertEquals("Day draft", restored.dayName.value)
        assertEquals("5", restored.targetSets.value[bench.id])
        assertEquals("Pause", restored.notes.value[bench.id])
        assertEquals(setOf(0), restored.supersetLinks.value)
        assertEquals("Chest", restored.pickerMuscle.value)
        assertEquals("Bench", restored.pickerQuery.value)
    }

    @Test
    fun `invalid rep range keeps draft without saving`() = runTest {
        val vm = RoutineDayFormViewModel(repository, exerciseDao, SavedStateHandle(mapOf("routineId" to 1L)), preferences)
        vm.updateDayName("Push")
        vm.addExercise(bench.id)
        vm.updateTargetRepMin(bench.id, "12")
        vm.updateTargetRepMax(bench.id, "8")
        vm.saveDay()
        advanceUntilIdle()
        assertNotNull(vm.error.value)
        coVerify(exactly = 0) { repository.saveRoutineDay(any(), any(), any(), any()) }
    }
}
