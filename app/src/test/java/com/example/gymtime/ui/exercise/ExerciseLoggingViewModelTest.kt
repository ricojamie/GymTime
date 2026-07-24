package com.example.gymtime.ui.exercise

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.repository.ExerciseRepository
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.domain.recommendation.ExerciseAttemptRecommendationUseCase
import com.example.gymtime.smartlog.SetDraft
import com.example.gymtime.smartlog.SmartLogDraftStore
import com.example.gymtime.smartlog.ValidatedSmartLogDraft
import com.example.gymtime.util.TestDispatcherRule
import com.example.gymtime.wear.ActiveWearSessionRepository
import com.example.gymtime.wear.WearSessionSnapshot
import com.example.gymtime.wear.WearDraftPatch
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseLoggingViewModelTest {

    @get:Rule
    val testDispatcherRule = TestDispatcherRule()

    private val context: Context = mockk(relaxed = true)
    private val exerciseRepository: ExerciseRepository = mockk()
    private val workoutRepository: WorkoutRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val volumeOrbRepository: VolumeOrbRepository = mockk(relaxed = true)
    private val supersetManager = SupersetManager()
    private val routineRepository: RoutineRepository = mockk()
    private val recommendationUseCase: ExerciseAttemptRecommendationUseCase = mockk()
    private val activeWearSessionRepository: ActiveWearSessionRepository = mockk(relaxed = true)
    private val smartLogDraftStore = SmartLogDraftStore()
    private val wearDraftPatches = MutableSharedFlow<WearDraftPatch>()
    private val wearLogRequests = MutableSharedFlow<WearDraftPatch?>()

    private lateinit var viewModel: ExerciseLoggingViewModel

    private val testExercise = Exercise(
        id = 1L,
        name = "Bench Press",
        targetMuscle = "Chest",
        logType = LogType.WEIGHT_REPS,
        defaultDistanceUnit = DistanceUnit.MILES,
        isCustom = false,
        notes = null,
        defaultRestSeconds = 90
    )

    private val testWorkout = Workout(
        id = 1L,
        startTime = Date(),
        endTime = null,
        name = null,
        note = null
    )

    @Before
    fun setup() {
        every { userPreferencesRepository.timerAudioEnabled } returns MutableStateFlow(true)
        every { userPreferencesRepository.timerVibrateEnabled } returns MutableStateFlow(true)
        every { userPreferencesRepository.timerAutoStart } returns MutableStateFlow(false)
        every { userPreferencesRepository.barWeight } returns MutableStateFlow(45f)
        every { userPreferencesRepository.loadingSides } returns MutableStateFlow(2)
        every { userPreferencesRepository.availablePlates } returns MutableStateFlow(listOf(45f, 25f, 10f, 5f, 2.5f))

        every { volumeOrbRepository.orbState } returns MutableStateFlow(mockk(relaxed = true))
        every { activeWearSessionRepository.draftPatches } returns wearDraftPatches
        every { activeWearSessionRepository.logRequests } returns wearLogRequests
        every { activeWearSessionRepository.beginPublishing() } returns 42L
        every { activeWearSessionRepository.publish(42L, any()) } just Runs
        every { activeWearSessionRepository.stopPublishing(42L) } just Runs
        every { activeWearSessionRepository.clear() } just Runs
        every { activeWearSessionRepository.confirmSetSaved() } just Runs
        coEvery { workoutRepository.logSet(any()) } just Runs
        coEvery { workoutRepository.updateSet(any()) } just Runs

        viewModel = buildViewModel()
    }

    @Test
    fun `viewModel initializes and loads exercise`() = runTest {
        advanceUntilIdle()

        assertEquals(testExercise, viewModel.exercise.value)
        assertEquals(testWorkout, viewModel.currentWorkout.value)
    }

    @Test
    fun `wear publishing starts only when requested and stops with active owner`() = runTest {
        advanceUntilIdle()
        val publishedSnapshots = mutableListOf<WearSessionSnapshot>()
        every { activeWearSessionRepository.publish(42L, any()) } answers {
            publishedSnapshots += secondArg<WearSessionSnapshot>()
            Unit
        }

        verify(exactly = 0) { activeWearSessionRepository.beginPublishing() }
        verify(exactly = 0) { activeWearSessionRepository.publish(any<Long>(), any()) }

        viewModel.startWearPublishing()
        advanceUntilIdle()

        verify(exactly = 1) { activeWearSessionRepository.beginPublishing() }
        verify(atLeast = 1) { activeWearSessionRepository.publish(42L, any()) }
        assertTrue(publishedSnapshots.any { it.exerciseId == 1L && it.exerciseName == "Bench Press" })

        viewModel.stopWearPublishing()

        verify(exactly = 1) { activeWearSessionRepository.stopPublishing(42L) }
    }

    @Test
    fun `logSet calls repository with correct data`() = runTest {
        var capturedSet: Set? = null
        coEvery { workoutRepository.logSet(any()) } coAnswers {
            capturedSet = firstArg()
            Unit
        }

        advanceUntilIdle()
        applyValidInputs(viewModel, LogType.WEIGHT_REPS)
        viewModel.updateRpe("8")

        viewModel.logSet()
        advanceUntilIdle()

        coVerify { workoutRepository.logSet(any()) }
        assertNotNull(capturedSet)
        assertEquals(100f, capturedSet?.weight)
        assertEquals(10, capturedSet?.reps)
        assertEquals(8f, capturedSet?.rpe)
        assertEquals(1L, capturedSet?.exerciseId)
        assertTrue(capturedSet?.isComplete == true)
        verify(exactly = 1) { activeWearSessionRepository.confirmSetSaved() }
    }

    @Test
    fun `logSet clears RPE and note but keeps weight and reps for next set`() = runTest {
        advanceUntilIdle()
        applyValidInputs(viewModel, LogType.WEIGHT_REPS)
        viewModel.updateRpe("8")
        viewModel.updateSetNote("Felt heavy")

        viewModel.logSet()
        advanceUntilIdle()

        assertEquals("100", viewModel.weight.value)
        assertEquals("10", viewModel.reps.value)
        assertEquals("", viewModel.rpe.value)
        assertEquals("", viewModel.setNote.value)
    }

    @Test
    fun `invalid inputs across log types do not log completed rows`() = runTest {
        val invalidCases = listOf(
            LogType.WEIGHT_REPS to { vm: ExerciseLoggingViewModel ->
                vm.updateWeight(".")
                vm.updateReps("10")
            },
            LogType.REPS_ONLY to { vm: ExerciseLoggingViewModel ->
                vm.updateReps("10.5")
            },
            LogType.DURATION to { vm: ExerciseLoggingViewModel ->
                vm.updateDuration("::")
            },
            LogType.WEIGHT_DISTANCE to { vm: ExerciseLoggingViewModel ->
                vm.updateWeight("100")
                vm.updateDistance("1..")
            },
            LogType.DISTANCE_TIME to { vm: ExerciseLoggingViewModel ->
                vm.updateDistance("0")
                vm.updateDuration("12:00")
            },
            LogType.WEIGHT_TIME to { vm: ExerciseLoggingViewModel ->
                vm.updateWeight("100")
                vm.updateDuration("::")
            },
            LogType.CALORIES_TIME to { vm: ExerciseLoggingViewModel ->
                vm.updateCalories(".")
                vm.updateDuration("12:00")
            }
        )

        invalidCases.forEach { (logType, applyInvalidValues) ->
            viewModel = buildViewModel(exercise = exerciseFor(logType))
            advanceUntilIdle()

            applyInvalidValues(viewModel)

            assertFalse("Expected invalid input for $logType", viewModel.isCurrentInputValid())

            viewModel.logSet()
            advanceUntilIdle()
        }

        coVerify(exactly = 0) { workoutRepository.logSet(any()) }
        verify(exactly = 0) { activeWearSessionRepository.confirmSetSaved() }
    }

    @Test
    fun `saveEditedSet rejects invalid edited values`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

        val existingSet = existingSetFor(LogType.WEIGHT_REPS)
        viewModel.startEditingSet(existingSet)
        viewModel.updateReps("10.5")

        assertFalse(viewModel.isCurrentInputValid())

        viewModel.saveEditedSet()
        advanceUntilIdle()

        coVerify(exactly = 0) { workoutRepository.updateSet(any()) }
        assertNotNull(viewModel.editingSet.value)
    }

    @Test
    fun `smart log create exercise exits restricted superset mode`() = runTest {
        advanceUntilIdle()
        val secondExercise = testExercise.copy(id = 2L, name = "Barbell Row", targetMuscle = "Back")
        supersetManager.startSuperset(listOf(testExercise, secondExercise), explicitGroupId = "99")

        assertTrue(viewModel.isInSupersetMode.value)

        viewModel.prepareForSmartLogExerciseCreation(restrictedToSuperset = true)

        assertFalse(viewModel.isInSupersetMode.value)
    }

    @Test
    fun `smart log create exercise keeps ordinary logging context unchanged`() = runTest {
        advanceUntilIdle()
        val secondExercise = testExercise.copy(id = 2L, name = "Barbell Row", targetMuscle = "Back")
        supersetManager.startSuperset(listOf(testExercise, secondExercise), explicitGroupId = "99")

        viewModel.prepareForSmartLogExerciseCreation(restrictedToSuperset = false)

        assertTrue(viewModel.isInSupersetMode.value)
    }

    @Test
    fun `rapid re-entrant logSet only persists once while save is in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { workoutRepository.logSet(any()) } coAnswers {
            gate.await()
            Unit
        }

        advanceUntilIdle()
        applyValidInputs(viewModel, LogType.WEIGHT_REPS)

        viewModel.logSet()
        viewModel.logSet()
        advanceUntilIdle()

        assertTrue(viewModel.isPersistingSet.value)
        coVerify(exactly = 1) { workoutRepository.logSet(any()) }
        verify(exactly = 0) { activeWearSessionRepository.confirmSetSaved() }

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.isPersistingSet.value)
        verify(exactly = 1) { activeWearSessionRepository.confirmSetSaved() }
    }

    @Test
    fun `identical second set is allowed after first save completes`() = runTest {
        val capturedSets = mutableListOf<Set>()
        coEvery { workoutRepository.logSet(any()) } coAnswers {
            capturedSets += firstArg<Set>()
            Unit
        }

        advanceUntilIdle()
        applyValidInputs(viewModel, LogType.WEIGHT_REPS)

        viewModel.logSet()
        advanceUntilIdle()

        viewModel.logSet()
        advanceUntilIdle()

        coVerify(exactly = 2) { workoutRepository.logSet(any()) }
        assertEquals(2, capturedSets.size)
        assertTrue(capturedSets.all { it.weight == 100f && it.reps == 10 })
        verify(exactly = 2) { activeWearSessionRepository.confirmSetSaved() }
    }

    @Test
    fun `wear duplicate request is single-flight and only confirms once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { workoutRepository.logSet(any()) } coAnswers {
            gate.await()
            Unit
        }

        advanceUntilIdle()

        val patch = WearDraftPatch(
            workoutId = testWorkout.id,
            exerciseId = testExercise.id,
            weight = "100",
            reps = "10"
        )

        wearLogRequests.emit(patch)
        wearLogRequests.emit(patch)
        advanceUntilIdle()

        assertTrue(viewModel.isPersistingSet.value)
        coVerify(exactly = 1) { workoutRepository.logSet(any()) }
        verify(exactly = 0) { activeWearSessionRepository.confirmSetSaved() }

        gate.complete(Unit)
        advanceUntilIdle()

        verify(exactly = 1) { activeWearSessionRepository.confirmSetSaved() }
    }

    @Test
    fun `smart log queue advances only once for re-entrant attempt`() = runTest {
        val token = smartLogDraftStore.put(
            ValidatedSmartLogDraft(
                exercise = testExercise,
                sets = listOf(
                    SetDraft(weight = 100f, reps = 10),
                    SetDraft(weight = 100f, reps = 8)
                )
            )
        )
        val gate = CompletableDeferred<Unit>()
        coEvery { workoutRepository.logSet(any()) } coAnswers {
            gate.await()
            Unit
        }

        viewModel = buildViewModel(draftToken = token)
        advanceUntilIdle()

        assertEquals(1, viewModel.smartLogQueue.value?.currentSetNumber)
        assertEquals("10", viewModel.reps.value)

        viewModel.logSet()
        viewModel.logSet()
        advanceUntilIdle()

        coVerify(exactly = 1) { workoutRepository.logSet(any()) }
        assertEquals(1, viewModel.smartLogQueue.value?.currentSetNumber)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, viewModel.smartLogQueue.value?.currentSetNumber)
        assertEquals("8", viewModel.reps.value)
    }

    @Test
    fun `guard resets after persistence failure and retry succeeds`() = runTest {
        var shouldFail = true
        coEvery { workoutRepository.logSet(any()) } coAnswers {
            if (shouldFail) {
                shouldFail = false
                throw IllegalStateException("boom")
            }
            Unit
        }

        advanceUntilIdle()
        applyValidInputs(viewModel, LogType.WEIGHT_REPS)

        viewModel.logSet()
        advanceUntilIdle()

        assertFalse(viewModel.isPersistingSet.value)
        verify(exactly = 0) { activeWearSessionRepository.confirmSetSaved() }

        viewModel.logSet()
        advanceUntilIdle()

        assertFalse(viewModel.isPersistingSet.value)
        coVerify(exactly = 2) { workoutRepository.logSet(any()) }
        verify(exactly = 1) { activeWearSessionRepository.confirmSetSaved() }
    }

    @Test
    fun `wear invalid request does not persist or acknowledge`() = runTest {
        advanceUntilIdle()

        wearLogRequests.emit(
            WearDraftPatch(
                workoutId = testWorkout.id,
                exerciseId = testExercise.id,
                weight = "100",
                reps = "10.5"
            )
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { workoutRepository.logSet(any()) }
        verify(exactly = 0) { activeWearSessionRepository.confirmSetSaved() }
        assertFalse(viewModel.isPersistingSet.value)
    }

    @Test
    fun `validation message stays hidden for pristine and incomplete forms`() = runTest {
        advanceUntilIdle()

        assertNull(viewModel.currentInputValidationMessage())

        viewModel.updateWeight("100")

        assertNull(viewModel.currentInputValidationMessage())
    }

    @Test
    fun `validation messages explain malformed inputs by log type`() = runTest {
        val invalidCases = listOf(
            Triple(LogType.WEIGHT_REPS, { vm: ExerciseLoggingViewModel ->
                vm.updateWeight("100")
                vm.updateReps("10.5")
            }, "Enter whole-number reps."),
            Triple(LogType.DURATION, { vm: ExerciseLoggingViewModel ->
                vm.updateDuration("::")
            }, "Enter time as seconds, mm:ss, or hh:mm:ss."),
            Triple(LogType.WEIGHT_DISTANCE, { vm: ExerciseLoggingViewModel ->
                vm.updateWeight("100")
                vm.updateDistance("0")
            }, "Enter a positive distance."),
            Triple(LogType.CALORIES_TIME, { vm: ExerciseLoggingViewModel ->
                vm.updateCalories(".")
                vm.updateDuration("12:00")
            }, "Enter positive calories.")
        )

        invalidCases.forEach { (logType, applyInvalidValues, expectedMessage) ->
            viewModel = buildViewModel(exercise = exerciseFor(logType))
            advanceUntilIdle()

            applyInvalidValues(viewModel)

            assertEquals(expectedMessage, viewModel.currentInputValidationMessage())
            assertFalse(viewModel.isCurrentInputValid())
        }
    }

    @Test
    fun `validation message explains invalid rpe separately`() = runTest {
        advanceUntilIdle()
        applyValidInputs(viewModel, LogType.WEIGHT_REPS)
        viewModel.updateRpe("11")

        assertEquals("Enter RPE from 0 to 10.", viewModel.currentInputValidationMessage())
        assertFalse(viewModel.isCurrentInputValid())
    }

    private fun buildViewModel(
        exercise: Exercise = testExercise,
        draftToken: String? = null
    ): ExerciseLoggingViewModel {
        val savedStateHandle = SavedStateHandle(
            buildMap {
                put("exerciseId", exercise.id)
                draftToken?.let { put("draftToken", it) }
            }
        )

        coEvery { exerciseRepository.getExercise(exercise.id) } returns flowOf(exercise)
        coEvery { exerciseRepository.getPersonalBestsByReps(exercise.id) } returns emptyMap()
        coEvery { exerciseRepository.getHeaviestSet(exercise.id) } returns null
        coEvery { workoutRepository.getCurrentWorkout() } returns testWorkout
        coEvery { workoutRepository.getSetsForWorkout(testWorkout.id) } returns flowOf(emptyList())
        coEvery { workoutRepository.getLastWorkoutSetsForExercise(exercise.id, any()) } returns emptyList()
        coEvery { routineRepository.getRoutineDayWithExercises(any()) } returns flowOf(null)
        coEvery { recommendationUseCase.getRecommendation(any()) } returns null

        return ExerciseLoggingViewModel(
            context = context,
            savedStateHandle = savedStateHandle,
            exerciseRepository = exerciseRepository,
            workoutRepository = workoutRepository,
            userPreferencesRepository = userPreferencesRepository,
            volumeOrbRepository = volumeOrbRepository,
            supersetManager = supersetManager,
            routineRepository = routineRepository,
            recommendationUseCase = recommendationUseCase,
            activeWearSessionRepository = activeWearSessionRepository,
            smartLogDraftStore = smartLogDraftStore
        )
    }

    private fun exerciseFor(logType: LogType): Exercise {
        return testExercise.copy(logType = logType)
    }

    private fun applyValidInputs(
        vm: ExerciseLoggingViewModel,
        logType: LogType
    ) {
        when (logType) {
            LogType.WEIGHT_REPS -> {
                vm.updateWeight("100")
                vm.updateReps("10")
            }
            LogType.REPS_ONLY -> {
                vm.updateReps("12")
            }
            LogType.DURATION -> {
                vm.updateDuration("12:34")
            }
            LogType.WEIGHT_DISTANCE -> {
                vm.updateWeight("100")
                vm.updateDistance("2.5")
            }
            LogType.DISTANCE_TIME -> {
                vm.updateDistance("2.5")
                vm.updateDuration("12:34")
            }
            LogType.WEIGHT_TIME -> {
                vm.updateWeight("100")
                vm.updateDuration("12:34")
            }
            LogType.CALORIES_TIME -> {
                vm.updateCalories("250")
                vm.updateDuration("12:34")
            }
        }
    }

    private fun existingSetFor(logType: LogType): Set {
        return when (logType) {
            LogType.WEIGHT_REPS -> Set(
                id = 10L,
                workoutId = testWorkout.id,
                exerciseId = testExercise.id,
                weight = 100f,
                reps = 10,
                rpe = 8f,
                durationSeconds = null,
                distanceMeters = null,
                isWarmup = false,
                isComplete = true,
                timestamp = Date(),
                note = "good"
            )
            LogType.REPS_ONLY -> Set(
                id = 10L,
                workoutId = testWorkout.id,
                exerciseId = testExercise.id,
                weight = null,
                reps = 20,
                rpe = null,
                durationSeconds = null,
                distanceMeters = null,
                isWarmup = false,
                isComplete = true,
                timestamp = Date()
            )
            LogType.DURATION -> Set(
                id = 10L,
                workoutId = testWorkout.id,
                exerciseId = testExercise.id,
                weight = null,
                reps = null,
                rpe = null,
                durationSeconds = 600,
                distanceMeters = null,
                isWarmup = false,
                isComplete = true,
                timestamp = Date()
            )
            LogType.WEIGHT_DISTANCE -> Set(
                id = 10L,
                workoutId = testWorkout.id,
                exerciseId = testExercise.id,
                weight = 100f,
                reps = null,
                rpe = null,
                durationSeconds = null,
                distanceValue = 2.5f,
                distanceUnit = DistanceUnit.MILES,
                distanceMeters = 4023.36f,
                isWarmup = false,
                isComplete = true,
                timestamp = Date()
            )
            LogType.DISTANCE_TIME -> Set(
                id = 10L,
                workoutId = testWorkout.id,
                exerciseId = testExercise.id,
                weight = null,
                reps = null,
                rpe = null,
                durationSeconds = 600,
                distanceValue = 2.5f,
                distanceUnit = DistanceUnit.MILES,
                distanceMeters = 4023.36f,
                isWarmup = false,
                isComplete = true,
                timestamp = Date()
            )
            LogType.WEIGHT_TIME -> Set(
                id = 10L,
                workoutId = testWorkout.id,
                exerciseId = testExercise.id,
                weight = 100f,
                reps = null,
                rpe = null,
                durationSeconds = 600,
                distanceMeters = null,
                isWarmup = false,
                isComplete = true,
                timestamp = Date()
            )
            LogType.CALORIES_TIME -> Set(
                id = 10L,
                workoutId = testWorkout.id,
                exerciseId = testExercise.id,
                weight = null,
                calories = 250f,
                reps = null,
                rpe = null,
                durationSeconds = 600,
                distanceMeters = null,
                isWarmup = false,
                isComplete = true,
                timestamp = Date()
            )
        }
    }
}
