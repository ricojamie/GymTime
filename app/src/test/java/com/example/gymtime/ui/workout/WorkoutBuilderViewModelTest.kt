package com.example.gymtime.ui.workout

import androidx.lifecycle.SavedStateHandle
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.repository.ExerciseRepository
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.data.repository.WorkoutStartResult
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutBuilderViewModelTest {

    @get:Rule
    val testDispatcherRule = TestDispatcherRule()

    private val exerciseRepository: ExerciseRepository = mockk()
    private val workoutRepository: WorkoutRepository = mockk()
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: WorkoutBuilderViewModel

    private val bench = exercise(1L, "Bench Press", "Chest")
    private val row = exercise(2L, "Barbell Row", "Back")
    private val squat = exercise(3L, "Squat", "Legs")

    @Before
    fun setup() {
        savedStateHandle = SavedStateHandle()
        every { exerciseRepository.getAllExercises() } returns flowOf(listOf(bench, row, squat))
        viewModel = WorkoutBuilderViewModel(exerciseRepository, workoutRepository, savedStateHandle)
    }

    @Test
    fun `selection order can be changed and exercises removed`() {
        viewModel.toggleExercise(bench.id)
        viewModel.toggleExercise(row.id)
        viewModel.toggleExercise(squat.id)

        viewModel.moveExercise(squat.id, -1)
        assertEquals(listOf(bench.id, squat.id, row.id), viewModel.selectedExerciseIds.value)

        viewModel.toggleExercise(squat.id)
        assertEquals(listOf(bench.id, row.id), viewModel.selectedExerciseIds.value)
    }

    @Test
    fun `newly created exercise is appended once to the workout plan`() {
        viewModel.toggleExercise(bench.id)

        viewModel.addExercise(99L)
        viewModel.addExercise(99L)

        assertEquals(listOf(bench.id, 99L), viewModel.selectedExerciseIds.value)
    }

    @Test
    fun `search and muscle filter narrow the exercise library`() = runTest {
        viewModel.updateSearchQuery("bar")
        assertEquals(listOf(row), viewModel.filteredExercises.first())

        viewModel.updateSearchQuery("")
        viewModel.selectMuscle("Legs")
        assertEquals(listOf(squat), viewModel.filteredExercises.first())
    }

    @Test
    fun `start workout persists selected order and emits first exercise`() = runTest {
        viewModel.toggleExercise(row.id)
        viewModel.toggleExercise(bench.id)
        val expected = WorkoutStartResult(workoutId = 42L, firstExerciseId = row.id)
        coEvery { workoutRepository.startPlannedWorkout(listOf(row.id, bench.id)) } returns expected
        val startEvent = async { viewModel.workoutStarted.first() }

        viewModel.startWorkout()
        advanceUntilIdle()

        assertEquals(expected, startEvent.await())
        assertTrue(viewModel.errorMessage.value == null)
        coVerify(exactly = 1) { workoutRepository.startPlannedWorkout(listOf(row.id, bench.id)) }
    }

    @Test
    fun `start workout sets isStarting synchronously, blocks double-tap, and clears only on open or failure`() = runTest {
        viewModel.toggleExercise(bench.id)
        val gate = CompletableDeferred<WorkoutStartResult>()
        coEvery { workoutRepository.startPlannedWorkout(listOf(bench.id)) } coAnswers { gate.await() }

        viewModel.startWorkout()
        assertTrue(viewModel.isStarting.value)
        viewModel.startWorkout()
        runCurrent()
        coVerify(exactly = 1) { workoutRepository.startPlannedWorkout(any()) }

        val expected = WorkoutStartResult(workoutId = 42L, firstExerciseId = bench.id)
        gate.complete(expected)
        advanceUntilIdle()
        assertTrue(viewModel.isStarting.value)
        assertEquals(expected, viewModel.workoutStarted.first())

        viewModel.onWorkoutOpened()
        assertFalse(viewModel.isStarting.value)

        coEvery { workoutRepository.startPlannedWorkout(listOf(bench.id)) } throws IllegalStateException("Active workout exists")
        viewModel.startWorkout()
        advanceUntilIdle()
        assertFalse(viewModel.isStarting.value)
        assertNotNull(viewModel.errorMessage.value)

        viewModel.clearError()
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun `selections, search query, and muscle filter survive SavedStateHandle recreation`() = runTest {
        viewModel.updateSearchQuery("press")
        viewModel.selectMuscle("Chest")
        viewModel.toggleExercise(squat.id)
        viewModel.toggleExercise(bench.id)

        val restored = WorkoutBuilderViewModel(exerciseRepository, workoutRepository, savedStateHandle)
        assertEquals("press", restored.searchQuery.value)
        assertEquals("Chest", restored.selectedMuscle.value)
        assertEquals(listOf(squat.id, bench.id), restored.selectedExerciseIds.value)
    }

    private fun exercise(id: Long, name: String, muscle: String) = Exercise(
        id = id,
        name = name,
        targetMuscle = muscle,
        logType = LogType.WEIGHT_REPS,
        isCustom = false,
        notes = null,
        defaultRestSeconds = 90
    )
}
