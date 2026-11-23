package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
class RoutineFormViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val repository: RoutineRepository = mockk(relaxed = true)

    @Test
    fun `new routine is dirty only while name differs from baseline`() = runTest {
        val viewModel = RoutineFormViewModel(repository, SavedStateHandle())
        val job = launch { viewModel.hasUnsavedChanges.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.hasUnsavedChanges.value)

        viewModel.updateRoutineName("Push Pull")
        advanceUntilIdle()
        assertTrue(viewModel.hasUnsavedChanges.value)

        viewModel.updateRoutineName("")
        advanceUntilIdle()
        assertFalse(viewModel.hasUnsavedChanges.value)
        job.cancel()
    }

    @Test
    fun `loaded routine edit starts clean and supports reverting`() = runTest {
        every { repository.getRoutineById(5L) } returns flowOf(Routine(id = 5L, name = "Strength"))
        val viewModel = RoutineFormViewModel(
            repository,
            SavedStateHandle(mapOf("routineId" to "5"))
        )
        val job = launch { viewModel.hasUnsavedChanges.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.hasUnsavedChanges.value)

        viewModel.updateRoutineName("Strength A")
        advanceUntilIdle()
        assertTrue(viewModel.hasUnsavedChanges.value)

        viewModel.updateRoutineName("Strength")
        advanceUntilIdle()
        assertFalse(viewModel.hasUnsavedChanges.value)
        job.cancel()
    }

    @Test
    fun `two save taps create one active first routine`() = runTest {
        every { repository.getAllRoutines() } returns flowOf(emptyList())
        coEvery { repository.insertRoutine(any()) } returns 7L
        val vm = RoutineFormViewModel(repository, SavedStateHandle())
        vm.updateRoutineName("My plan")
        vm.saveRoutine()
        vm.saveRoutine()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.insertRoutine(match { it.name == "My plan" && it.isActive }) }
        coVerify(exactly = 0) { repository.setActiveRoutine(any()) }
    }

    @Test
    fun `failed save retains name and allows retry`() = runTest {
        every { repository.getAllRoutines() } returns flowOf(emptyList())
        coEvery { repository.insertRoutine(any()) } throws IllegalStateException("Disk full")
        val vm = RoutineFormViewModel(repository, SavedStateHandle())
        vm.updateRoutineName("Keep this name")
        vm.saveRoutine()
        advanceUntilIdle()
        assertEquals("Keep this name", vm.routineName.value)
        assertFalse(vm.isSaving.value)
        assertNotNull(vm.error.value)
        coEvery { repository.insertRoutine(any()) } returns 8L
        vm.saveRoutine()
        advanceUntilIdle()
        coVerify(exactly = 2) { repository.insertRoutine(any()) }
    }

    @Test
    fun `create respects routine cap`() = runTest {
        every { repository.getAllRoutines() } returns flowOf((1L..10L).map { Routine(id = it, name = "Routine $it") })
        val vm = RoutineFormViewModel(repository, SavedStateHandle())
        vm.updateRoutineName("Extra")
        vm.saveRoutine()
        advanceUntilIdle()
        assertNotNull(vm.error.value)
        coVerify(exactly = 0) { repository.insertRoutine(any()) }
    }

    @Test
    fun `draft name survives holder recreation`() = runTest {
        val handle = SavedStateHandle()
        val vm = RoutineFormViewModel(repository, handle)
        vm.updateRoutineName("A draft")
        val restored = RoutineFormViewModel(repository, handle)
        assertEquals("A draft", restored.routineName.value)
    }

    @Test
    fun `edit mode load failure locks input and preserves reload error until retry succeeds`() = runTest {
        every { repository.getRoutineById(5L) } returns flow { error("Read failed") }
        val vm = RoutineFormViewModel(repository, SavedStateHandle(mapOf("routineId" to "5")))
        val saveJob = launch { vm.isSaveEnabled.collect {} }
        advanceUntilIdle()

        assertFalse(vm.canEdit.value)
        assertFalse(vm.isSaveEnabled.value)
        assertNotNull(vm.error.value)

        // Typing must not clear the load error or mutate the locked form.
        vm.updateRoutineName("New Name")
        advanceUntilIdle()
        assertEquals("", vm.routineName.value)
        assertNotNull(vm.error.value)
        assertFalse(vm.isSaveEnabled.value)

        every { repository.getRoutineById(5L) } returns flowOf(Routine(id = 5L, name = "Strength"))
        vm.retryLoad()
        advanceUntilIdle()

        assertTrue(vm.canEdit.value)
        assertTrue(vm.isSaveEnabled.value)
        assertNull(vm.error.value)
        assertEquals("Strength", vm.routineName.value)
        saveJob.cancel()
    }
}
