package com.example.gymtime.ui.smartlog

import com.example.gymtime.data.db.dao.ExerciseUsageRow
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.repository.ExerciseRepository
import com.example.gymtime.smartlog.ExerciseMatcher
import com.example.gymtime.smartlog.ParsedLoggingCommand
import com.example.gymtime.smartlog.SetDraft
import com.example.gymtime.smartlog.SmartLogCommandInterpreter
import com.example.gymtime.smartlog.SmartLogDraftStore
import com.example.gymtime.smartlog.SmartLogValidator
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmartLogViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val exerciseRepository: ExerciseRepository = mockk()
    private val interpreter: SmartLogCommandInterpreter = mockk()
    private val matcher = ExerciseMatcher()
    private val validator = SmartLogValidator()
    private val draftStore = SmartLogDraftStore()

    private val incline = exercise(1L, "Incline Dumbbell Press")
    private val bench = exercise(2L, "Barbell Bench Press")
    private val overhead = exercise(3L, "Overhead Press")

    @Before
    fun setup() {
        every { exerciseRepository.getExercisesWithUsageStats(0L) } returns flowOf(
            rows(incline, bench, overhead)
        )
    }

    @Test
    fun `matched query still offers create exercise action`() = runTest {
        val viewModel = createViewModel()
        val input = "incline dumbbell press 50 x 8"
        coEvery { interpreter.extract(input) } returns parsed("incline dumbbell press")

        viewModel.updateInput(input)
        viewModel.submit(currentExercise = null)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("incline dumbbell press", state.createExerciseName)
        assertEquals(incline, state.review?.exercise)
        assertTrue(state.candidates.isEmpty())
        assertEquals(null, state.error)
    }

    @Test
    fun `ambiguous query keeps create exercise action visible`() = runTest {
        val viewModel = createViewModel()
        val input = "press 50 x 8"
        coEvery { interpreter.extract(input) } returns parsed("press")

        viewModel.updateInput(input)
        viewModel.submit(currentExercise = null)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("press", state.createExerciseName)
        assertTrue(state.review == null)
        assertTrue(state.candidates.isNotEmpty())
        assertEquals(null, state.error)
    }

    @Test
    fun `no match still offers create exercise action`() = runTest {
        val viewModel = createViewModel()
        val input = "sled drag 50 x 8"
        coEvery { interpreter.extract(input) } returns parsed("sled drag")

        viewModel.updateInput(input)
        viewModel.submit(currentExercise = null)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("sled drag", state.createExerciseName)
        assertTrue(state.review == null)
        assertTrue(state.candidates.isEmpty())
        assertEquals("No close match was found in your exercise library.", state.error)
    }

    @Test
    fun `typed and voice equivalent final inputs reach the same create exercise state`() = runTest {
        val typedViewModel = createViewModel()
        val voiceViewModel = createViewModel()
        val finalInput = "incline dumbbell press 50 x 8"
        coEvery { interpreter.extract(finalInput) } returns parsed("incline dumbbell press")

        typedViewModel.updateInput("incline")
        typedViewModel.updateInput("incline dumbbell")
        typedViewModel.updateInput(finalInput)
        typedViewModel.submit(currentExercise = null)

        voiceViewModel.updateInput(finalInput)
        voiceViewModel.submit(currentExercise = null)
        advanceUntilIdle()

        val typedState = typedViewModel.state.value
        val voiceState = voiceViewModel.state.value
        assertEquals(typedState.createExerciseName, voiceState.createExerciseName)
        assertEquals(typedState.review, voiceState.review)
        assertEquals(typedState.candidates, voiceState.candidates)
        assertEquals(typedState.error, voiceState.error)
    }

    @Test
    fun `restricted exercise set still offers create exercise action without bypassing superset guard`() = runTest {
        val viewModel = createViewModel()
        val input = "barbell bench press 50 x 8"
        coEvery { interpreter.extract(input) } returns parsed("barbell bench press")

        viewModel.updateInput(input)
        viewModel.submit(currentExercise = null, allowedExerciseIds = setOf(incline.id))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("barbell bench press", state.createExerciseName)
        assertTrue(state.review == null)
        assertTrue(state.candidates.isEmpty())
        assertEquals(
            "That exercise isn't in the active superset. Open Smart Log outside the superset to switch to it.",
            state.error
        )
    }

    @Test
    fun `submit failure clears isProcessing instead of sticking`() = runTest {
        val viewModel = createViewModel()
        val input = "bench press 135 x 8"
        coEvery { interpreter.extract(input) } throws IllegalStateException("boom")

        viewModel.updateInput(input)
        viewModel.submit(currentExercise = null)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isProcessing)
        assertEquals("Couldn't parse that set right now. Try again.", state.error)
    }

    @Test
    fun `updateInput cancels in flight submit so stale results do not overwrite`() = runTest {
        val viewModel = createViewModel()
        val gate = CompletableDeferred<ParsedLoggingCommand?>()
        coEvery { interpreter.extract("incline dumbbell press 50 x 8") } coAnswers { gate.await() }

        viewModel.updateInput("incline dumbbell press 50 x 8")
        viewModel.submit(currentExercise = null)
        viewModel.updateInput("bench")
        gate.complete(parsed("incline dumbbell press"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("bench", state.input)
        assertFalse(state.isProcessing)
        assertNull(state.review)
        assertNull(state.createExerciseName)
    }

    @Test
    fun `confirmReview resets state and guards against double tap`() = runTest {
        val viewModel = createViewModel()
        val events = mutableListOf<SmartLogNavigationEvent>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.navigation.collect { events += it }
        }
        val input = "incline dumbbell press 50 x 8"
        coEvery { interpreter.extract(input) } returns parsed("incline dumbbell press")

        viewModel.updateInput(input)
        viewModel.submit(currentExercise = null)
        advanceUntilIdle()

        viewModel.confirmReview()
        viewModel.confirmReview()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(incline.id, events.single().exerciseId)
        assertEquals(SmartLogUiState(), viewModel.state.value)
        collectJob.cancel()
    }

    private fun createViewModel(): SmartLogViewModel = SmartLogViewModel(
        exerciseRepository = exerciseRepository,
        interpreter = interpreter,
        matcher = matcher,
        validator = validator,
        draftStore = draftStore,
        computeDispatcher = dispatcherRule.testDispatcher
    )

    private fun parsed(query: String) = ParsedLoggingCommand(
        exerciseQuery = query,
        sets = listOf(SetDraft(weight = 50f, reps = 8))
    )

    private fun rows(vararg exercises: Exercise) = exercises.map {
        ExerciseUsageRow(it, allTimeSetCount = 0, recentSetCount = 0, lastUsedMs = null)
    }

    private fun exercise(id: Long, name: String) = Exercise(
        id = id,
        name = name,
        targetMuscle = "Chest",
        logType = LogType.WEIGHT_REPS,
        isCustom = false,
        notes = null,
        defaultRestSeconds = 90
    )
}
