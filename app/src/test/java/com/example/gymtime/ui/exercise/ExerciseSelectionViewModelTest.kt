package com.example.gymtime.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.ExerciseLastSetRow
import com.example.gymtime.data.db.dao.ExerciseUsageRow
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.Set as LoggedSet
import com.example.gymtime.data.repository.ExerciseRepository
import com.example.gymtime.data.repository.WorkoutPlanEditResult
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseSelectionViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val exerciseRepository: ExerciseRepository = mockk()
    private val workoutRepository: WorkoutRepository = mockk()
    private val supersetManager: SupersetManager = mockk(relaxed = true)
    private val preferences: UserPreferencesRepository = mockk()
    private val newUiEnabled = MutableStateFlow(true)
    private val usageRows = MutableStateFlow<List<ExerciseUsageRow>>(emptyList())
    private val lastSets = MutableStateFlow<List<ExerciseLastSetRow>>(emptyList())
    private val ongoingWorkout = MutableStateFlow<Workout?>(null)
    private var lastCompletedWorkout: Workout? = null
    private val pickerSessionState = ExercisePickerSessionState()
    private val createdViewModels = mutableListOf<ExerciseSelectionViewModel>()

    @Before
    fun setup() {
        every { exerciseRepository.getExercisesWithUsageStats(any()) } returns usageRows
        every { exerciseRepository.observeLastWorkoutSets() } returns lastSets
        every { exerciseRepository.getAllMuscleGroups() } returns flowOf(emptyList())
        every { preferences.newUiEnabled } returns newUiEnabled
        every { workoutRepository.getOngoingWorkoutFlow() } returns ongoingWorkout
        coEvery { workoutRepository.getLastCompletedWorkout() } answers { lastCompletedWorkout }
        every { supersetManager.supersetExercises } returns MutableStateFlow(emptyList())
    }

    @After
    fun clearViewModels() = runTest {
        // stateIn belongs to the ViewModel, not this test's backgroundScope.
        // Wait for its Default-dispatcher children before the rule resets Main.
        val jobs = createdViewModels.mapNotNull { it.viewModelScope.coroutineContext[Job] }
        jobs.forEach { it.cancel() }
        jobs.joinAll()
        createdViewModels.clear()
    }

    @Test
    fun `preview history is not queried while flag is off and stops when disabled`() = runTest {
        newUiEnabled.value = false
        val subscriptions = AtomicInteger()
        every { exerciseRepository.observeLastWorkoutSets() } returns flow {
            subscriptions.incrementAndGet()
            try {
                emit(emptyList())
                awaitCancellation()
            } finally {
                subscriptions.decrementAndGet()
            }
        }
        val vm = viewModel()
        collectPreview(vm)
        advanceUntilIdle()
        assertEquals(0, subscriptions.get())
        verify(exactly = 0) { exerciseRepository.observeLastWorkoutSets() }
        verify(exactly = 0) { workoutRepository.getOngoingWorkoutFlow() }

        newUiEnabled.value = true
        vm.previewPickerState.first { !it.isLoading }
        assertEquals(1, subscriptions.get())

        newUiEnabled.value = false
        vm.previewPickerState.first { it.isLoading }
        assertEquals(0, subscriptions.get())
    }

    @Test
    fun `preview queries start only when observed and stop after collector leaves`() = runTest {
        val stopped = CompletableDeferred<Unit>()
        every { exerciseRepository.observeLastWorkoutSets() } returns flow {
            try {
                emit(emptyList())
                awaitCancellation()
            } finally {
                stopped.complete(Unit)
            }
        }
        val vm = viewModel()
        advanceUntilIdle()
        verify(exactly = 0) { exerciseRepository.observeLastWorkoutSets() }
        verify(exactly = 0) { workoutRepository.getOngoingWorkoutFlow() }

        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.previewPickerState.collect()
        }
        vm.previewPickerState.first { !it.isLoading }
        collector.cancel()
        advanceTimeBy(5_001)
        advanceUntilIdle()
        stopped.await()
        advanceUntilIdle()
        assertTrue(vm.previewPickerState.value.isLoading)
    }

    @Test
    fun `preview shows recently used first followed by unused exercises alphabetically`() = runTest {
        usageRows.value = listOf(row(1, "Bench", 10), row(2, "Curl"), row(3, "Squat", 30))
        val vm = viewModel()
        collectPreview(vm)
        val state = vm.previewPickerState.first { !it.isLoading }
        assertEquals(ExerciseSortMode.RECENTLY_USED, state.sortMode)
        assertEquals(listOf(3L, 1L, 2L), state.ids())
    }

    @Test
    fun `first use automatically displays all exercises alphabetically`() = runTest {
        usageRows.value = listOf(row(1, "Squat"), row(2, "Bench"))
        val vm = viewModel()
        collectPreview(vm)
        val state = vm.previewPickerState.first { !it.isLoading }
        assertEquals(listOf(2L, 1L), state.ids())
    }

    @Test
    fun `search covers unused exercises and normalizes whitespace and token order`() = runTest {
        usageRows.value = listOf(row(1, "Squat", 50), row(2, "Barbell Bench Press"), row(3, "Dumbbell Bench Press"))
        val vm = viewModel()
        vm.updateSearchQuery("  PRESS \n bArBeLl  ")
        collectPreview(vm)
        assertEquals(listOf(2L), vm.previewPickerState.first { !it.isLoading }.ids())

        vm.updateSearchQuery(" \t ")
        assertEquals(listOf(1L, 2L, 3L), vm.previewPickerState.first { it.rows.size == 3 }.ids())
    }

    @Test
    fun `global search still respects muscle filters and clearing restores matches`() = runTest {
        usageRows.value = listOf(row(1, "Barbell Bench", 10, "Chest"), row(2, "Barbell Squat", muscle = "Legs"))
        val vm = viewModel()
        vm.updateSearchQuery("barbell")
        collectPreview(vm)
        vm.previewPickerState.first { !it.isLoading }
        vm.togglePreviewMuscleFilter("Legs")
        assertEquals(listOf(2L), vm.previewPickerState.first { it.selectedMuscles == setOf("Legs") }.ids())

        vm.clearPreviewMuscleFilters()
        assertEquals(listOf(1L, 2L), vm.previewPickerState.first { it.rows.size == 2 }.ids())
    }

    @Test
    fun `muscle filter keeps used and unused exercises and preview sorting leaves legacy untouched`() = runTest {
        usageRows.value = listOf(
            row(1, "Bench", 10), row(2, "Cable Fly"), row(3, "Squat", 30, "Legs"),
            row(4, "Dip", 20), row(5, "Butterfly")
        )
        val vm = viewModel()
        collectPreview(vm)
        vm.previewPickerState.first { !it.isLoading }
        vm.togglePreviewMuscleFilter("Chest")
        assertEquals(listOf(4L, 1L, 5L, 2L), vm.previewPickerState.first { it.selectedMuscles == setOf("Chest") }.ids())
        vm.updatePreviewSortMode(ExerciseSortMode.ALPHABETICAL)
        assertEquals(listOf(1L, 5L, 2L, 4L), vm.previewPickerState.first { it.sortMode == ExerciseSortMode.ALPHABETICAL }.ids())
        assertTrue(vm.selectedMuscles.value.isEmpty())
        assertEquals(ExerciseSortMode.ALPHABETICAL, vm.sortMode.value)
        assertEquals(listOf(1L, 5L, 2L, 4L, 3L), vm.filteredExercises.first().map { it.exercise.id })
    }

    @Test
    fun `explicit sort uses all time and recent counts independently`() = runTest {
        usageRows.value = listOf(
            row(1, "Bench", 10).copy(allTimeSetCount = 30, recentSetCount = 1),
            row(2, "Curl", 20).copy(allTimeSetCount = 5, recentSetCount = 4)
        )
        val vm = viewModel()
        collectPreview(vm)
        vm.previewPickerState.first { !it.isLoading }
        vm.updatePreviewSortMode(ExerciseSortMode.ALL_TIME_SETS)
        assertEquals(listOf(1L, 2L), vm.previewPickerState.first { it.sortMode == ExerciseSortMode.ALL_TIME_SETS }.ids())
        vm.updatePreviewSortMode(ExerciseSortMode.RECENT_SETS)
        assertEquals(listOf(2L, 1L), vm.previewPickerState.first { it.ids() == listOf(2L, 1L) }.ids())
    }

    @Test
    fun `new picker for same workout restores muscles and sort but clears search`() = runTest {
        ongoingWorkout.value = workout(11)
        val first = viewModel()
        collectPreview(first)
        first.previewPickerState.first { !it.isLoading }
        first.togglePreviewMuscleFilter("Chest")
        first.updatePreviewSortMode(ExerciseSortMode.ALL_TIME_SETS)
        first.updateSearchQuery("bench")
        first.viewModelScope.coroutineContext[Job]!!.cancel()

        val next = viewModel()
        collectPreview(next)
        val restored = next.previewPickerState.first { !it.isLoading }
        assertEquals(setOf("Chest"), restored.selectedMuscles)
        assertEquals(ExerciseSortMode.ALL_TIME_SETS, restored.sortMode)
        assertEquals("", next.searchQuery.value)
    }

    @Test
    fun `prestart choices carry into the first workout`() = runTest {
        val first = viewModel()
        collectPreview(first)
        first.previewPickerState.first { !it.isLoading }
        first.togglePreviewMuscleFilter("Back")
        first.updatePreviewSortMode(ExerciseSortMode.RECENT_SETS)
        first.viewModelScope.coroutineContext[Job]!!.cancel()
        ongoingWorkout.value = workout(12)

        val next = viewModel()
        collectPreview(next)
        val restored = next.previewPickerState.first { !it.isLoading }
        assertEquals(setOf("Back"), restored.selectedMuscles)
        assertEquals(ExerciseSortMode.RECENT_SETS, restored.sortMode)
    }

    @Test
    fun `different workout resets remembered preview choices`() = runTest {
        ongoingWorkout.value = workout(11)
        val first = viewModel()
        collectPreview(first)
        first.previewPickerState.first { !it.isLoading }
        first.togglePreviewMuscleFilter("Chest")
        first.updatePreviewSortMode(ExerciseSortMode.ALPHABETICAL)
        first.viewModelScope.coroutineContext[Job]!!.cancel()
        ongoingWorkout.value = workout(12)

        val next = viewModel()
        collectPreview(next)
        val fresh = next.previewPickerState.first { !it.isLoading }
        assertTrue(fresh.selectedMuscles.isEmpty())
        assertEquals(ExerciseSortMode.RECENTLY_USED, fresh.sortMode)
    }

    @Test
    fun `ended workout clears choices on next picker`() = runTest {
        ongoingWorkout.value = workout(11)
        val first = viewModel()
        collectPreview(first)
        first.previewPickerState.first { !it.isLoading }
        first.togglePreviewMuscleFilter("Chest")
        first.viewModelScope.coroutineContext[Job]!!.cancel()
        lastCompletedWorkout = workout(11).copy(endTime = Date(200))
        ongoingWorkout.value = null

        val next = viewModel()
        collectPreview(next)
        assertTrue(next.previewPickerState.first { !it.isLoading }.selectedMuscles.isEmpty())
    }

    @Test
    fun `workout started and completed while picker absent clears prestart choices`() = runTest {
        val first = viewModel()
        collectPreview(first)
        first.previewPickerState.first { !it.isLoading }
        first.togglePreviewMuscleFilter("Chest")
        first.viewModelScope.coroutineContext[Job]!!.cancel()
        lastCompletedWorkout = workout(11).copy(endTime = Date(200))

        val next = viewModel()
        collectPreview(next)
        assertTrue(next.previewPickerState.first { !it.isLoading }.selectedMuscles.isEmpty())
    }

    @Test
    fun `finishing a reopened workout while picker absent resets prestart choices`() = runTest {
        lastCompletedWorkout = workout(11).copy(endTime = Date(200))
        val first = viewModel()
        collectPreview(first)
        first.previewPickerState.first { !it.isLoading }
        first.togglePreviewMuscleFilter("Chest")
        first.viewModelScope.coroutineContext[Job]!!.cancel()
        lastCompletedWorkout = workout(11).copy(endTime = Date(300))
        ongoingWorkout.value = workout(12)

        val next = viewModel()
        collectPreview(next)
        assertTrue(next.previewPickerState.first { !it.isLoading }.selectedMuscles.isEmpty())
    }

    @Test
    fun `changing workouts disables filters before completion snapshot finishes loading`() = runTest {
        ongoingWorkout.value = workout(11)
        val vm = viewModel()
        collectPreview(vm)
        vm.previewPickerState.first { !it.isLoading }
        vm.togglePreviewMuscleFilter("Chest")
        vm.previewPickerState.first { it.selectedMuscles == setOf("Chest") }
        val completionRead = CompletableDeferred<Workout?>()
        coEvery { workoutRepository.getLastCompletedWorkout() } coAnswers { completionRead.await() }
        ongoingWorkout.value = workout(12)

        vm.previewPickerState.first { it.isLoading }
        completionRead.complete(workout(11).copy(endTime = Date(200)))
        assertTrue(vm.previewPickerState.first { !it.isLoading }.selectedMuscles.isEmpty())
    }

    @Test
    fun `waiting for actual workout snapshot does not erase saved filters`() = runTest {
        ongoingWorkout.value = workout(11)
        val first = viewModel()
        collectPreview(first)
        first.previewPickerState.first { !it.isLoading }
        first.togglePreviewMuscleFilter("Chest")
        first.viewModelScope.coroutineContext[Job]!!.cancel()
        val delayedWorkout = MutableSharedFlow<Workout?>(replay = 1)
        every { workoutRepository.getOngoingWorkoutFlow() } returns delayedWorkout

        val next = viewModel()
        collectPreview(next)
        advanceUntilIdle()
        assertTrue(next.previewPickerState.value.isLoading)
        delayedWorkout.emit(workout(11))
        assertEquals(setOf("Chest"), next.previewPickerState.first { !it.isLoading }.selectedMuscles)
    }

    @Test
    fun `latest set summaries update reactively by exercise id`() = runTest {
        usageRows.value = listOf(row(7, "Bench", 10))
        val vm = viewModel()
        collectPreview(vm)
        vm.previewPickerState.first { !it.isLoading }
        val summary = ExerciseLastSetRow(
            set = LoggedSet(
                id = 15, workoutId = 4, exerciseId = 7, weight = 135f, reps = 8,
                rpe = null, durationSeconds = null, distanceMeters = null,
                isWarmup = false, isComplete = true, timestamp = Date(100)
            ),
            workoutStartTime = Date(50)
        )
        lastSets.value = listOf(summary)
        assertEquals(summary, vm.previewPickerState.first { it.lastSets.isNotEmpty() }.lastSets[7L])
    }

    @Test
    fun `ordinary superset builds ordered choices and cancellation clears them`() = runTest {
        val vm = viewModel()
        assertFalse(vm.isImmediateSupersetSelection)
        vm.toggleSupersetMode()
        vm.toggleExerciseSelection(exercise(1, "Bench"))
        vm.toggleExerciseSelection(exercise(2, "Row"))
        assertEquals(2, vm.getSelectionOrder(2))
        assertTrue(vm.canStartSuperset())
        vm.toggleSupersetMode()
        assertFalse(vm.isSupersetModeEnabled.value)
        assertTrue(vm.selectedForSuperset.value.isEmpty())
        verify(exactly = 0) { supersetManager.startSuperset(any(), any()) }
    }

    @Test
    fun `ad hoc superset starts immediately with parent followed by selection`() = runTest {
        val parent = exercise(1, "Bench")
        val selected = exercise(2, "Row")
        every { exerciseRepository.getExercise(1) } returns flowOf(parent)
        val vm = viewModel(args = mapOf("supersetMode" to true, "adHocParentId" to 1L))
        val event = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { vm.supersetStarted.first() }
        assertTrue(vm.isImmediateSupersetSelection)
        vm.toggleExerciseSelection(selected)
        advanceUntilIdle()
        assertEquals(2L, event.await())
        verify { supersetManager.startSuperset(listOf(parent, selected), any()) }
    }

    @Test
    fun `add to existing superset completes immediately`() = runTest {
        val selected = exercise(2, "Row")
        val vm = viewModel(args = mapOf("addToSuperset" to true))
        val event = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { vm.exerciseAddedToSuperset.first() }
        assertTrue(vm.isImmediateSupersetSelection)
        vm.toggleExerciseSelection(selected)
        advanceUntilIdle()
        assertEquals(2L, event.await())
        verify { supersetManager.addExercise(selected) }
    }

    @Test
    fun `superset cap reports reason and keeps ten selections`() = runTest {
        val vm = viewModel()
        (1L..10L).forEach { vm.toggleExerciseSelection(exercise(it, "Exercise $it")) }
        val message = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { vm.selectionMessages.first() }
        vm.toggleExerciseSelection(exercise(11, "Extra"))
        advanceUntilIdle()
        assertEquals(10, vm.selectedForSuperset.value.size)
        assertEquals("A superset can have up to 10 exercises", message.await())
    }

    @Test
    fun `adding to an existing full superset reports limit without navigating`() = runTest {
        every { supersetManager.supersetExercises } returns MutableStateFlow(
            (1L..10L).map { exercise(it, "Exercise $it") }
        )
        val vm = viewModel(args = mapOf("addToSuperset" to true))
        val message = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { vm.selectionMessages.first() }
        vm.toggleExerciseSelection(exercise(11, "Extra"))
        advanceUntilIdle()
        assertEquals("A superset can have up to 10 exercises", message.await())
        verify(exactly = 0) { supersetManager.addExercise(any()) }
    }

    @Test
    fun `PR tracking cap reports reason without updating status`() = runTest {
        every { exerciseRepository.getStarredExercises() } returns flowOf((1L..3L).map { exercise(it, "Tracked $it") })
        val vm = viewModel()
        val message = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { vm.selectionMessages.first() }
        vm.toggleExerciseStarred(exercise(4, "Extra"))
        advanceUntilIdle()
        assertTrue(message.await().contains("up to 3"))
        coVerify(exactly = 0) { exerciseRepository.updateStarredStatus(any(), any()) }
    }

    @Test
    fun `swap mode replaces the plan slot and emits the selected exercise`() = runTest {
        coEvery { workoutRepository.swapWorkoutPlanExercise(41L, 9L) } returns
            WorkoutPlanEditResult.Updated
        val viewModel = viewModel(swapInstanceId = 41L)
        val event = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.swapExerciseEvent.first()
        }

        viewModel.swapPlannedExercise(9L)
        advanceUntilIdle()

        assertTrue(viewModel.isSwapMode)
        assertEquals(
            ExerciseSelectionViewModel.SwapExerciseEvent.Success(9L),
            event.await()
        )
        coVerify(exactly = 1) { workoutRepository.swapWorkoutPlanExercise(41L, 9L) }
    }

    @Test
    fun `duplicate swap stays in picker with a useful error`() = runTest {
        coEvery { workoutRepository.swapWorkoutPlanExercise(41L, 9L) } returns
            WorkoutPlanEditResult.DuplicateExercise
        val viewModel = viewModel(swapInstanceId = 41L)
        val event = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.swapExerciseEvent.first()
        }

        viewModel.swapPlannedExercise(9L)
        advanceUntilIdle()

        assertEquals(
            ExerciseSelectionViewModel.SwapExerciseEvent.Error(
                "That exercise is already in today's plan"
            ),
            event.await()
        )
    }

    private fun viewModel(swapInstanceId: Long = -1, args: Map<String, Any> = emptyMap()) = ExerciseSelectionViewModel(
        exerciseRepository = exerciseRepository,
        workoutRepository = workoutRepository,
        supersetManager = supersetManager,
        pickerSessionState = pickerSessionState,
        userPreferencesRepository = preferences,
        savedStateHandle = SavedStateHandle(
            mapOf(
                "workoutMode" to true,
                "swapInstanceId" to swapInstanceId
            ) + args
        )
    ).also { createdViewModels += it }

    private fun TestScope.collectPreview(vm: ExerciseSelectionViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.previewPickerState.collect() }
    }

    private fun PreviewPickerState.ids() = rows.map { it.exercise.id }

    private fun workout(id: Long) = Workout(id, Date(100), null, null, null)

    private fun row(id: Long, name: String, used: Long? = null, muscle: String = "Chest") =
        ExerciseUsageRow(exercise(id, name, muscle), if (used == null) 0 else 1, 0, used)

    private fun exercise(id: Long, name: String, muscle: String = "Chest") = Exercise(
        id = id, name = name, targetMuscle = muscle, logType = LogType.WEIGHT_REPS,
        isCustom = false, notes = null, defaultRestSeconds = 90
    )
}
