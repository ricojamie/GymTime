package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.RoutineDayWithExercises
import com.example.gymtime.data.db.dao.RoutineExerciseWithDetails
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineDayFormViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val repository: RoutineRepository = mockk(relaxed = true)
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
            SavedStateHandle(mapOf("routineId" to 1L))
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
            SavedStateHandle(mapOf("routineId" to 1L, "dayId" to "3"))
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
}
