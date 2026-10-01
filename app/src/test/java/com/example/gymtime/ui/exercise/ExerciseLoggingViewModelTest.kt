package com.example.gymtime.ui.exercise

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.PlateInventorySettings
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.db.dao.WorkoutPlanSummary
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WARMUP_MUSCLE_GROUP
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val plateInventorySettings = MutableStateFlow(PlateInventorySettings(emptyMap(), false))

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
        plateInventorySettings.value = PlateInventorySettings(emptyMap(), false)
        every { userPreferencesRepository.plateInventorySettings } returns plateInventorySettings

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
    fun `one-off plan exposes previous and next exercise navigation`() = runTest {
        val secondExercise = testExercise.copy(id = 2L, name = "Row", targetMuscle = "Back")
        val planItems = listOf(
            planSummary(instanceId = 11L, exerciseId = 1L, name = "Bench Press", orderIndex = 0),
            planSummary(instanceId = 12L, exerciseId = 2L, name = "Row", orderIndex = 1),
            planSummary(instanceId = 13L, exerciseId = 3L, name = "Squat", orderIndex = 2)
        )

        val plannedViewModel = buildViewModel(
            exercise = secondExercise,
            planItems = planItems
        )
        advanceUntilIdle()

        assertFalse(plannedViewModel.currentWorkout.value!!.startedFromRoutine)
        assertEquals(1L, plannedViewModel.previousExerciseId.value)
        assertEquals(3L, plannedViewModel.nextExerciseId.value)
        assertEquals(2, plannedViewModel.planPosition.value?.index)
        assertEquals(3, plannedViewModel.planPosition.value?.total)
        assertNull(plannedViewModel.planPosition.value?.dayName)
    }

    @Test
    fun `tapped superset exercise updates active cursor`() = runTest {
        val secondExercise = testExercise.copy(id = 2L, name = "Row")
        supersetManager.startSuperset(listOf(testExercise, secondExercise))

        assertTrue(viewModel.selectSupersetExercise(secondExercise.id))
        assertEquals(1, viewModel.currentSupersetIndex.value)
        assertEquals(secondExercise.id, supersetManager.getCurrentExerciseId())
    }

    @Test
    fun `opening an unrelated exercise exits superset and logs without rotating`() = runTest {
        advanceUntilIdle()
        supersetManager.startSuperset(listOf(testExercise, testExercise.copy(id = 2L)))
        val standalone = buildViewModel(exercise = testExercise.copy(id = 3L))
        val switches = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            standalone.autoSwitchEvent.collect { switches += it }
        }
        advanceUntilIdle()

        assertFalse(standalone.isInSupersetMode.value)
        standalone.updateWeight("100")
        standalone.updateReps("10")
        standalone.logSet()
        advanceUntilIdle()

        coVerify { workoutRepository.logSet(match {
            it.exerciseId == 3L && it.supersetGroupId == null && it.supersetOrderIndex == 0
        }) }
        assertTrue(switches.isEmpty())
    }

    @Test
    fun `opening another superset member preserves grouping and rotation`() = runTest {
        advanceUntilIdle()
        val second = testExercise.copy(id = 2L)
        supersetManager.startSuperset(listOf(testExercise, second), "group")
        val member = buildViewModel(exercise = second)
        val switches = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            member.autoSwitchEvent.collect { switches += it }
        }
        advanceUntilIdle()

        assertTrue(member.isInSupersetMode.value)
        assertEquals(1, member.currentSupersetIndex.value)
        member.updateWeight("100")
        member.updateReps("10")
        member.logSet()
        advanceUntilIdle()

        coVerify { workoutRepository.logSet(match {
            it.exerciseId == 2L && it.supersetGroupId == "group" && it.supersetOrderIndex == 1
        }) }
        assertEquals(listOf(1L), switches)
    }

    @Test
    fun `explicit exit survives plan updates and stops planned superset rotation`() = runTest {
        advanceUntilIdle()
        val second = testExercise.copy(id = 2L)
        every { exerciseRepository.getExercise(2L) } returns flowOf(second)
        val plans = MutableStateFlow(listOf(
            planSummary(11L, 1L, "Bench", 0).copy(supersetGroupId = "planned"),
            planSummary(12L, 2L, "Row", 1).copy(supersetGroupId = "planned", supersetOrderIndex = 1)
        ))
        val planned = buildViewModel(exercise = second)
        every { workoutRepository.getWorkoutPlanSummaries(testWorkout.id) } returns plans
        val switches = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            planned.autoSwitchEvent.collect { switches += it }
        }
        advanceUntilIdle()
        assertTrue(planned.isInSupersetMode.value)
        assertEquals(1L, planned.nextExerciseId.value)

        planned.exitSupersetMode()
        advanceUntilIdle()
        plans.value = plans.value.map { it.copy(setCount = 1) }
        advanceUntilIdle()
        assertFalse(planned.isInSupersetMode.value)
        assertNull(planned.nextExerciseId.value)

        planned.updateWeight("100")
        planned.updateReps("10")
        planned.logSet()
        advanceUntilIdle()
        coVerify { workoutRepository.logSet(match {
            it.exerciseId == 2L && it.supersetGroupId == null
        }) }
        assertTrue(switches.isEmpty())
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
    fun `warmup library exercise always logs excluded entries`() = runTest {
        val warmupExercise = testExercise.copy(
            name = "Hip Switches",
            targetMuscle = WARMUP_MUSCLE_GROUP,
            logType = LogType.REPS_ONLY
        )
        var capturedSet: Set? = null
        coEvery { workoutRepository.logSet(any()) } coAnswers {
            capturedSet = firstArg()
            Unit
        }
        viewModel = buildViewModel(exercise = warmupExercise)
        advanceUntilIdle()

        assertTrue(viewModel.isWarmup.value)
        viewModel.toggleWarmup()
        assertTrue(viewModel.isWarmup.value)
        viewModel.updateReps("10")

        viewModel.logSet()
        advanceUntilIdle()

        assertTrue(capturedSet?.isWarmup == true)
        assertTrue(viewModel.isWarmup.value)
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
    fun `weight-only edit preserves saved RPE and note and restores next set draft`() = runTest {
        advanceUntilIdle()
        viewModel.updateWeight("125")
        viewModel.updateReps("12")
        viewModel.updateRpe("6")
        viewModel.updateSetNote("Next set draft")
        val existingSet = existingSetFor(LogType.WEIGHT_REPS).copy(rpe = 8.5f, note = "Pause at the bottom")
        var updated: Set? = null
        coEvery { workoutRepository.updateSet(any()) } coAnswers {
            updated = firstArg()
            Unit
        }

        viewModel.startEditingSet(existingSet)
        assertEquals("8.5", viewModel.rpe.value)
        assertEquals("Pause at the bottom", viewModel.setNote.value)
        viewModel.updateWeight("110")
        viewModel.saveEditedSet()
        advanceUntilIdle()

        assertEquals(existingSet.copy(weight = 110f), updated)
        assertNull(viewModel.editingSet.value)
        assertEquals("125", viewModel.weight.value)
        assertEquals("12", viewModel.reps.value)
        assertEquals("6", viewModel.rpe.value)
        assertEquals("Next set draft", viewModel.setNote.value)
    }

    @Test
    fun `cancel edit restores every draft field even after switching edited rows`() = runTest {
        advanceUntilIdle()
        viewModel.updateWeight("125")
        viewModel.updateReps("12")
        viewModel.updateRpe("6")
        viewModel.updateCalories("50")
        viewModel.updateDuration("2:30")
        viewModel.updateDistance("3.2")
        viewModel.updateSelectedDistanceUnit(DistanceUnit.KILOMETERS)
        viewModel.toggleWarmup()
        viewModel.updateSetNote("Keep this draft")

        viewModel.startEditingSet(existingSetFor(LogType.WEIGHT_REPS))
        viewModel.updateSetNote("Discard this change")
        viewModel.startEditingSet(existingSetFor(LogType.WEIGHT_REPS).copy(id = 11, rpe = null, note = null))
        assertEquals("", viewModel.rpe.value)
        assertEquals("", viewModel.setNote.value)
        viewModel.cancelEditing()

        assertNull(viewModel.editingSet.value)
        assertEquals("125", viewModel.weight.value)
        assertEquals("12", viewModel.reps.value)
        assertEquals("6", viewModel.rpe.value)
        assertEquals("50", viewModel.calories.value)
        assertEquals("2:30", viewModel.duration.value)
        assertEquals("3.2", viewModel.distance.value)
        assertEquals(DistanceUnit.KILOMETERS, viewModel.selectedDistanceUnit.value)
        assertTrue(viewModel.isWarmup.value)
        assertEquals("Keep this draft", viewModel.setNote.value)
        coVerify(exactly = 0) { workoutRepository.updateSet(any()) }
    }

    @Test
    fun `failed edit keeps loaded metadata and original draft available for cancel`() = runTest {
        advanceUntilIdle()
        viewModel.updateWeight("125")
        viewModel.updateReps("12")
        viewModel.updateSetNote("Unsaved next set")
        val existingSet = existingSetFor(LogType.WEIGHT_REPS)
        coEvery { workoutRepository.updateSet(any()) } throws IllegalStateException("Storage unavailable")

        viewModel.startEditingSet(existingSet)
        viewModel.updateWeight("110")
        viewModel.saveEditedSet()
        advanceUntilIdle()

        assertEquals(existingSet, viewModel.editingSet.value)
        assertEquals("110", viewModel.weight.value)
        assertEquals("8.0", viewModel.rpe.value)
        assertEquals("good", viewModel.setNote.value)
        viewModel.cancelEditing()
        assertEquals("125", viewModel.weight.value)
        assertEquals("12", viewModel.reps.value)
        assertEquals("Unsaved next set", viewModel.setNote.value)
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

    @Test
    fun `plate calculation uses saved individual inventory counts`() = runTest {
        plateInventorySettings.value = PlateInventorySettings(mapOf(45f to 2), true)

        val loadout = viewModel.calculatePlates(225f)

        assertEquals(listOf(45f), loadout.platesPerSide)
        assertEquals(135f, loadout.totalWeight, 0f)
        assertFalse(loadout.isExact)
    }

    @Test
    fun `turning off inventory limits restores unlimited calculation`() = runTest {
        plateInventorySettings.value = PlateInventorySettings(mapOf(45f to 2), false)

        val loadout = viewModel.calculatePlates(225f)

        assertEquals(listOf(45f, 45f), loadout.platesPerSide)
        assertEquals(225f, loadout.totalWeight, 0f)
        assertTrue(loadout.isExact)
    }

    private fun buildViewModel(
        exercise: Exercise = testExercise,
        draftToken: String? = null,
        planItems: List<WorkoutPlanSummary> = emptyList()
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
        coEvery { workoutRepository.ensureWorkoutPlanInstance(testWorkout.id, exercise.id) } returns null
        every { workoutRepository.getWorkoutPlanSummaries(testWorkout.id) } returns flowOf(planItems)
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

    private fun planSummary(
        instanceId: Long,
        exerciseId: Long,
        name: String,
        orderIndex: Int
    ) = WorkoutPlanSummary(
        instanceId = instanceId,
        exerciseId = exerciseId,
        exerciseName = name,
        targetMuscle = "Test",
        setCount = 0,
        bestWeight = null,
        totalVolume = 0f,
        orderIndex = orderIndex,
        plannedSets = null,
        repMin = null,
        repMax = null,
        restSeconds = 90,
        notes = null,
        supersetGroupId = null,
        supersetOrderIndex = 0,
        isSkipped = false,
        addedDuringWorkout = false
    )

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
