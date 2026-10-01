package com.example.gymtime.ui.analytics.preview

import androidx.lifecycle.SavedStateHandle
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.domain.analytics.*
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingInsightsViewModelTest {
    @get:Rule val dispatcherRule = TestDispatcherRule()
    private val insights: TrainingInsightsUseCase = mockk()
    private val preferences: UserPreferencesRepository = mockk { every { newUiEnabled } returns flowOf(true) }

    private fun dashboard(days: Int = 28, exercises: List<TrainingInsightExercise> = emptyList()): TrainingInsightsDashboard {
        val date = LocalDate.of(2026, 10, 1)
        return TrainingInsightsDashboard(
            TrainingInsightsPeriod(days, 0, 1, -1, 0, date.minusDays(days.toLong() - 1), date,
                date.minusDays(days.toLong() * 2 - 1), date.minusDays(days.toLong()), "America/New_York"),
            TrainingInsightCadence(0, 0, 0, 0, 0, 0, 0, 0, date, null, emptyList()),
            emptyList(), exercises, emptyList(), emptyList()
        )
    }

    @Test fun `gate starts unresolved and does not load data while only preferences are observed`() = runTest {
        val gate = MutableSharedFlow<Boolean>()
        every { preferences.newUiEnabled } returns gate
        var observingTables = false
        every { insights.sourceChanges } returns flow { observingTables = true; emit(Unit) }
        coEvery { insights.load(any(), any(), any()) } returns dashboard()
        val vm = TrainingInsightsViewModel(insights, preferences, SavedStateHandle())
        assertNull(vm.newUiEnabled.value)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.newUiEnabled.collect {} }
        runCurrent()
        assertNull(vm.newUiEnabled.value)
        gate.emit(true)
        runCurrent()
        assertEquals(true, vm.newUiEnabled.value)
        assertFalse(observingTables)
        coVerify(exactly = 0) { insights.load(any(), any(), any()) }
        job.cancel()
        runCurrent()
    }

    @Test fun `latest selected period wins over a cancelled older projection`() = runTest {
        every { insights.sourceChanges } returns flowOf(Unit)
        val older = CompletableDeferred<TrainingInsightsDashboard>()
        coEvery { insights.load(28, any(), any()) } coAnswers { older.await() }
        coEvery { insights.load(84, any(), any()) } returns dashboard(84)
        val vm = TrainingInsightsViewModel(insights, preferences, SavedStateHandle())
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        vm.selectWindow(84)
        runCurrent()
        assertEquals(84, vm.uiState.value.dashboard?.period?.windowDays)
        older.complete(dashboard(28))
        runCurrent()
        assertEquals(84, vm.uiState.value.dashboard?.period?.windowDays)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
        job.cancel()
        runCurrent()
    }

    @Test fun `failed period refresh retains loaded period and retry can replace it`() = runTest {
        every { insights.sourceChanges } returns flowOf(Unit)
        coEvery { insights.load(28, any(), any()) } returns dashboard(28)
        coEvery { insights.load(84, any(), any()) } throws IllegalStateException("Read failed")
        val vm = TrainingInsightsViewModel(insights, preferences, SavedStateHandle())
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        vm.selectWindow(84)
        runCurrent()
        assertEquals(84, vm.uiState.value.windowDays)
        assertEquals(28, vm.uiState.value.dashboard?.period?.windowDays)
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        coEvery { insights.load(84, any(), any()) } returns dashboard(84)
        vm.refresh()
        runCurrent()
        assertEquals(84, vm.uiState.value.dashboard?.period?.windowDays)
        assertNull(vm.uiState.value.error)
        job.cancel()
        runCurrent()
    }

    @Test fun `stopping dashboard collection stops reacting to Room invalidations`() = runTest {
        val changes = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        every { insights.sourceChanges } returns changes
        coEvery { insights.load(any(), any(), any()) } returns dashboard()
        val vm = TrainingInsightsViewModel(insights, preferences, SavedStateHandle())
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        coVerify(exactly = 1) { insights.load(any(), any(), any()) }
        job.cancel()
        runCurrent()
        changes.emit(Unit)
        runCurrent()
        coVerify(exactly = 1) { insights.load(any(), any(), any()) }
    }

    @Test fun `saved exercise selection and frequent filters survive holder recreation`() = runTest {
        val selected: TrainingInsightExercise = mockk(relaxed = true) { every { id } returns 42L }
        every { insights.sourceChanges } returns flowOf(Unit)
        coEvery { insights.load(any(), any(), any()) } returns dashboard(84, listOf(selected))
        val saved = SavedStateHandle()
        val first = TrainingInsightsViewModel(insights, preferences, saved)
        first.selectWindow(84)
        first.selectExercise(42L)
        first.setExerciseQuery("press")
        first.setMuscleFilter("Chest")
        val restored = TrainingInsightsViewModel(insights, preferences, SavedStateHandle(mapOf(
            "insightWindowDays" to saved.get<Int>("insightWindowDays"),
            "insightExerciseId" to saved.get<Long>("insightExerciseId"),
            "insightExerciseQuery" to saved.get<String>("insightExerciseQuery"),
            "insightMuscleFilter" to saved.get<String>("insightMuscleFilter")
        )))
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.uiState.collect {} }
        runCurrent()
        assertEquals(84, restored.uiState.value.windowDays)
        assertEquals(42L, restored.uiState.value.selectedExerciseId)
        assertEquals("press", restored.uiState.value.exerciseQuery)
        assertEquals("Chest", restored.uiState.value.muscleFilter)
        job.cancel()
        runCurrent()
    }
}
