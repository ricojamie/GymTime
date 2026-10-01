package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.RoutineDayWithExercises
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.repository.WorkoutStartResult
import com.example.gymtime.domain.analytics.RoutineStats
import com.example.gymtime.domain.analytics.RoutineStatsUseCase
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineStartViewModelTest {
    @get:Rule val dispatcherRule = TestDispatcherRule()
    private val repository: RoutineRepository = mockk(relaxed = true)
    private val preferences: UserPreferencesRepository = mockk { every { newUiEnabled } returns flowOf(true) }
    private val statsUseCase: RoutineStatsUseCase = mockk {
        coEvery { getStats(1L) } returns RoutineStats(0, null, null, 0f, null, emptyList())
    }
    private val day = RoutineDayWithExercises(RoutineDay(id = 2L, routineId = 1L, name = "Push", orderIndex = 0), emptyList())

    private fun prepare() {
        every { repository.getRoutineById(1L) } returns flowOf(Routine(id = 1L, name = "Plan"))
        every { repository.getDaysWithExercisesForRoutine(1L) } returns flowOf(listOf(day))
        every { repository.getRoutineDayStats(1L) } returns flowOf(emptyMap())
        every { repository.getRoutineDayWithExercises(2L) } returns flowOf(day)
    }

    @Test fun `chooser guards taps until the workout navigation is acknowledged`() = runTest {
        prepare()
        val result = CompletableDeferred<WorkoutStartResult?>()
        coEvery { repository.startRoutineDay(2L) } coAnswers { result.await() }
        val vm = RoutineDayStartViewModel(repository, SavedStateHandle(mapOf("routineId" to 1L)), preferences)
        vm.startWorkoutFromDay(2L)
        vm.startWorkoutFromDay(2L)
        runCurrent()
        coVerify(exactly = 1) { repository.startRoutineDay(2L) }
        result.complete(WorkoutStartResult(10L, 20L))
        advanceUntilIdle()
        vm.startWorkoutFromDay(2L)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.startRoutineDay(2L) }
        assertEquals(20L, vm.startWorkoutEvent.first())
        vm.onWorkoutOpened()
        vm.startWorkoutFromDay(2L)
        advanceUntilIdle()
        coVerify(exactly = 2) { repository.startRoutineDay(2L) }
    }

    @Test fun `detail guards taps until the workout navigation is acknowledged`() = runTest {
        prepare()
        val result = CompletableDeferred<WorkoutStartResult?>()
        coEvery { repository.startRoutineDay(2L) } coAnswers { result.await() }
        val vm = RoutineDetailViewModel(repository, statsUseCase, SavedStateHandle(mapOf("routineId" to 1L)), preferences)
        vm.startWorkoutFromDay(2L)
        vm.startWorkoutFromDay(2L)
        runCurrent()
        coVerify(exactly = 1) { repository.startRoutineDay(2L) }
        result.complete(WorkoutStartResult(10L, 20L))
        advanceUntilIdle()
        vm.startWorkoutFromDay(2L)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.startRoutineDay(2L) }
        assertEquals(20L, vm.startWorkoutEvent.first())
    }

    @Test fun `chooser start failure is visible and a second attempt can succeed`() = runTest {
        prepare()
        coEvery { repository.startRoutineDay(2L) } throws IllegalStateException("Storage unavailable")
        val vm = RoutineDayStartViewModel(repository, SavedStateHandle(mapOf("routineId" to 1L)), preferences)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.previewState.collect {} }
        vm.startWorkoutFromDay(2L)
        advanceUntilIdle()
        assertNotNull(vm.previewState.value.startError)
        assertNull(vm.previewState.value.startingDayId)
        coEvery { repository.startRoutineDay(2L) } returns WorkoutStartResult(10L, 20L)
        vm.startWorkoutFromDay(2L)
        advanceUntilIdle()
        assertEquals(20L, vm.startWorkoutEvent.first())
        assertNull(vm.previewState.value.startError)
    }

    @Test fun `detail start failure is visible and a second attempt can succeed`() = runTest {
        prepare()
        coEvery { repository.startRoutineDay(2L) } returns null
        val vm = RoutineDetailViewModel(repository, statsUseCase, SavedStateHandle(mapOf("routineId" to 1L)), preferences)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.previewState.collect {} }
        vm.startWorkoutFromDay(2L)
        advanceUntilIdle()
        assertNotNull(vm.previewState.value.actionError)
        assertNull(vm.previewState.value.startingDayId)
        coEvery { repository.startRoutineDay(2L) } returns WorkoutStartResult(10L, 20L)
        vm.startWorkoutFromDay(2L)
        advanceUntilIdle()
        assertEquals(20L, vm.startWorkoutEvent.first())
        assertNull(vm.previewState.value.actionError)
    }

    @Test fun `a day from another routine cannot start from the chooser`() = runTest {
        prepare()
        every { repository.getRoutineDayWithExercises(2L) } returns flowOf(day.copy(day = day.day.copy(routineId = 99L)))
        val vm = RoutineDayStartViewModel(repository, SavedStateHandle(mapOf("routineId" to 1L)), preferences)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.previewState.collect {} }
        vm.startWorkoutFromDay(2L)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.startRoutineDay(any()) }
        assertNotNull(vm.previewState.value.startError)
    }
}
