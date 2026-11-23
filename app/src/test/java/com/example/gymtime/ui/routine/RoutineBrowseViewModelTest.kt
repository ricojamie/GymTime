package com.example.gymtime.ui.routine

import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.dao.RoutineBrowseSummary
import com.example.gymtime.ui.library.LibraryViewModel
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineBrowseViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val repository: RoutineRepository = mockk(relaxed = true)

    @Test
    fun `routine list uses single browse summaries query without per-routine observers`() = runTest {
        every { repository.getRoutineBrowseSummaries() } returns flowOf(
            listOf(
                RoutineBrowseSummary(
                    id = 1L,
                    name = "PPL",
                    isActive = true,
                    dayCount = 3,
                    exerciseCount = 15,
                    nextDayName = "Push"
                )
            )
        )

        val vm = RoutineListViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.previewState.collect {} }
        advanceUntilIdle()

        val state = vm.previewState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.routines.size)
        assertEquals("PPL", state.routines.single().name)
        assertEquals(3, state.routines.single().dayCount)
        assertEquals(15, state.routines.single().exerciseCount)
        assertEquals("Push", state.routines.single().nextDayName)
        assertTrue(state.routines.single().isActive)
        verify(exactly = 0) { repository.getDaysWithExercisesForRoutine(any()) }
    }

    @Test
    fun `library routine tab maps summaries and surfaces load retry and duplicate errors`() = runTest {
        every { repository.getRoutineBrowseSummaries() } returns flow { error("DB failed") }
        val vm = LibraryViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.routinePreviewState.collect {} }
        advanceUntilIdle()

        assertFalse(vm.routinePreviewState.value.isLoading)
        assertNotNull(vm.routinePreviewState.value.loadError)

        every { repository.getRoutineBrowseSummaries() } returns flowOf(
            listOf(RoutineBrowseSummary(1L, "Upper Lower", false, 2, 10, "Upper"))
        )
        vm.retryRoutines()
        advanceUntilIdle()

        assertNull(vm.routinePreviewState.value.loadError)
        assertEquals("Upper Lower", vm.routinePreviewState.value.routines.single().name)

        coEvery { repository.duplicateRoutine(1L) } throws IllegalArgumentException("You can have up to 10 routines.")
        vm.duplicateRoutine(1L)
        advanceUntilIdle()

        assertEquals("You can have up to 10 routines.", vm.routinePreviewState.value.actionError)
    }
}
