package com.example.gymtime.ui.summary

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.gymtime.ai.NarrativeGenerator
import com.example.gymtime.ai.NarrativeGeneratorType
import com.example.gymtime.ai.NarrativeRequest
import com.example.gymtime.ai.NarrativeResult
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.domain.share.ShareWorkoutUseCase
import com.example.gymtime.domain.share.ShareableWorkout
import com.example.gymtime.domain.summary.WorkoutRecapFacts
import com.example.gymtime.domain.summary.WorkoutRecapFactsUseCase
import com.example.gymtime.util.ShareImagePalette
import com.example.gymtime.util.TestDispatcherRule
import com.example.gymtime.util.WorkoutShareImageGenerator
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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
class PostWorkoutSummaryViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var workoutDao: WorkoutDao
    private lateinit var setDao: SetDao
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var muscleGroupDao: MuscleGroupDao
    private lateinit var volumeOrbRepository: VolumeOrbRepository
    private lateinit var shareWorkoutUseCase: ShareWorkoutUseCase
    private lateinit var workoutShareImageGenerator: WorkoutShareImageGenerator
    private lateinit var workoutRecapFactsUseCase: WorkoutRecapFactsUseCase
    private lateinit var narrativeGenerator: NarrativeGenerator

    private val testWorkoutId = 1L
    private val testWorkout = Workout(
        id = testWorkoutId,
        startTime = Date(System.currentTimeMillis() - 3600000),
        endTime = Date(),
        name = "Test Workout",
        note = null,
        rating = null,
        ratingNote = null,
        routineDayId = null
    )

    private val testExercise = Exercise(
        id = 1L,
        name = "Bench Press",
        targetMuscle = "Chest",
        logType = LogType.WEIGHT_REPS,
        isCustom = false,
        notes = null,
        defaultRestSeconds = 90
    )

    private val testSets = listOf(
        Set(
            id = 1, workoutId = testWorkoutId, exerciseId = 1L, weight = 135f, reps = 10,
            rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = true,
            isComplete = true, timestamp = Date(), note = null, supersetGroupId = null, supersetOrderIndex = 0
        ),
        Set(
            id = 2, workoutId = testWorkoutId, exerciseId = 1L, weight = 185f, reps = 8,
            rpe = 8f, durationSeconds = null, distanceMeters = null, isWarmup = false,
            isComplete = true, timestamp = Date(), note = null, supersetGroupId = null, supersetOrderIndex = 0
        ),
        Set(
            id = 3, workoutId = testWorkoutId, exerciseId = 1L, weight = 185f, reps = 8,
            rpe = 8.5f, durationSeconds = null, distanceMeters = null, isWarmup = false,
            isComplete = true, timestamp = Date(), note = null, supersetGroupId = null, supersetOrderIndex = 0
        ),
        Set(
            id = 4, workoutId = testWorkoutId, exerciseId = 1L, weight = 185f, reps = 6,
            rpe = 9f, durationSeconds = null, distanceMeters = null, isWarmup = false,
            isComplete = true, timestamp = Date(), note = null, supersetGroupId = null, supersetOrderIndex = 0
        )
    )

    private val testSetInfos = testSets.map {
        SetWithExerciseInfo(it, testExercise.name, testExercise.targetMuscle)
    }

    private val testPalette = ShareImagePalette(
        background = 0xFF101010.toInt(),
        card = 0xFF1C1C1C.toInt(),
        accent = 0xFFA3E635.toInt(),
        accentSoft = 0xFF283314.toInt(),
        textPrimary = 0xFFFFFFFF.toInt(),
        textMuted = 0xFF9E9E9E.toInt(),
        onAccent = 0xFF000000.toInt()
    )

    @Before
    fun setup() {
        savedStateHandle = SavedStateHandle(mapOf("workoutId" to testWorkoutId))
        workoutDao = mockk(relaxed = true)
        setDao = mockk(relaxed = true)
        exerciseDao = mockk(relaxed = true)
        muscleGroupDao = mockk(relaxed = true)
        volumeOrbRepository = mockk(relaxed = true)
        shareWorkoutUseCase = mockk(relaxed = true)
        workoutShareImageGenerator = mockk(relaxed = true)
        workoutRecapFactsUseCase = mockk(relaxed = true)
        narrativeGenerator = mockk(relaxed = true)

        every { workoutDao.getWorkoutById(testWorkoutId) } returns flowOf(testWorkout)
        coEvery { setDao.getWorkoutSetsWithExercises(testWorkoutId) } returns testSetInfos
        coEvery { exerciseDao.getAllExercisesSync() } returns listOf(testExercise)
        coEvery { muscleGroupDao.getAllMuscleGroupNames() } returns listOf("Chest", "Legs")
        every { volumeOrbRepository.orbState } returns kotlinx.coroutines.flow.MutableStateFlow(
            VolumeOrbState(
                currentWeekVolume = 5000f,
                lastWeekVolume = 4000f,
                progressPercent = 125f,
                isFirstWeek = false,
                hasOverflowed = false,
                justOverflowed = false
            )
        )
        coEvery { volumeOrbRepository.refresh() } just Runs
        coEvery { volumeOrbRepository.getSessionContribution(any()) } returns 1000f
        coEvery { workoutRecapFactsUseCase(testWorkoutId) } returns null
    }

    private fun createViewModel(): PostWorkoutSummaryViewModel {
        return PostWorkoutSummaryViewModel(
            savedStateHandle = savedStateHandle,
            workoutDao = workoutDao,
            setDao = setDao,
            exerciseDao = exerciseDao,
            muscleGroupDao = muscleGroupDao,
            volumeOrbRepository = volumeOrbRepository,
            shareWorkoutUseCase = shareWorkoutUseCase,
            workoutShareImageGenerator = workoutShareImageGenerator,
            workoutRecapFactsUseCase = workoutRecapFactsUseCase,
            narrativeGenerator = narrativeGenerator
        )
    }

    @Test
    fun initLoadsWorkoutStats() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertNotNull(viewModel.workoutStats.value)
        coVerify(exactly = 1) { setDao.getWorkoutSetsWithExercises(testWorkoutId) }
        coVerify(exactly = 0) { exerciseDao.getExerciseByIdSync(any()) }
    }

    @Test
    fun workoutStatsExcludesWarmupSetsFromVolume() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val stats = viewModel.workoutStats.value
        assertNotNull(stats)
        assertEquals(4070f, stats!!.totalVolume, 0.1f)
    }

    @Test
    fun workoutStatsCountsOnlyWorkingSets() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(3, viewModel.workoutStats.value!!.totalSets)
    }

    @Test
    fun workoutStatsExcludesWarmupLibraryExercisesFromExerciseAndMuscleMetrics() = runTest {
        val warmupExercise = testExercise.copy(
            id = 2L,
            name = "Leg Swings",
            targetMuscle = "Warmups",
            logType = LogType.REPS_ONLY
        )
        val warmupActivity = Set(
            id = 5L,
            workoutId = testWorkoutId,
            exerciseId = warmupExercise.id,
            weight = null,
            reps = 10,
            rpe = null,
            durationSeconds = null,
            distanceMeters = null,
            isWarmup = true,
            isComplete = true,
            timestamp = Date()
        )
        coEvery { setDao.getWorkoutSetsWithExercises(testWorkoutId) } returns (
            testSetInfos + SetWithExerciseInfo(warmupActivity, warmupExercise.name, warmupExercise.targetMuscle)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val stats = viewModel.workoutStats.value!!
        assertEquals(1, stats.exerciseCount)
        assertEquals(listOf("Chest"), stats.muscleGroups)
    }

    @Test
    fun updateRatingTogglesValue() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateRating(4)
        assertEquals(4, viewModel.selectedRating.value)
        viewModel.updateRating(4)
        assertNull(viewModel.selectedRating.value)
    }

    @Test
    fun updateRatingNoteUpdatesState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateRatingNote("Great workout!")
        assertEquals("Great workout!", viewModel.ratingNote.value)
    }

    @Test
    fun saveAndFinishUpdatesWorkout() = runTest {
        coEvery { workoutDao.updateWorkout(any()) } just Runs
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateRating(5)
        viewModel.saveAndFinish()
        advanceUntilIdle()
        coVerify { workoutDao.updateWorkout(match { it.rating == 5 }) }
        // U8: stays saving once exited rather than flickering false in finally.
        assertTrue(viewModel.uiState.value.isSaving)
    }

    @Test
    fun skipAndFinishDoesNotSave() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.skipAndFinish()
        advanceUntilIdle()
        coVerify(exactly = 0) { workoutDao.updateWorkout(any()) }
    }

    @Test
    fun sessionContributionLoadsFromRepository() = runTest {
        coEvery { volumeOrbRepository.getSessionContribution(testWorkoutId) } returns 2500f
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(2500f, viewModel.sessionContribution.value)
    }

    @Test
    fun unfinishedSetsDoNotAffectSummaryTotalsOrMuscles() = runTest {
        val unfinished = testSets.last().copy(
            id = 8L, exerciseId = 2L, weight = 500f, reps = 20, isComplete = false
        )
        coEvery { setDao.getWorkoutSetsWithExercises(testWorkoutId) } returns (
            testSetInfos + SetWithExerciseInfo(unfinished, "Squat", "Legs")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(4070f, viewModel.workoutStats.value!!.totalVolume, .1f)
        assertEquals(3, viewModel.workoutStats.value!!.totalSets)
        assertEquals(1, viewModel.workoutStats.value!!.exerciseCount)
        assertEquals(listOf("Chest"), viewModel.workoutStats.value!!.muscleGroups)
    }

    @Test
    fun existingFeedbackIsRestoredAndDonePreservesIt() = runTest {
        every { workoutDao.getWorkoutById(testWorkoutId) } returns flowOf(
            testWorkout.copy(rating = 4, ratingNote = "Keep this cue")
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(4, viewModel.selectedRating.value)
        assertEquals("Keep this cue", viewModel.ratingNote.value)
        assertFalse(viewModel.uiState.value.hasUnsavedFeedback)

        viewModel.saveAndFinish()
        advanceUntilIdle()
        coVerify(exactly = 1) { workoutDao.updateWorkout(match { it.rating == 4 && it.ratingNote == "Keep this cue" }) }
    }

    @Test
    fun restoredDraftWinsOverSavedFeedbackAndSurvivesRetry() = runTest {
        savedStateHandle = SavedStateHandle(
            mapOf(
                "workoutId" to testWorkoutId,
                "summary_draft_ready" to true,
                "summary_rating_draft" to 2,
                "summary_note_draft" to "Pending feedback"
            )
        )
        every { workoutDao.getWorkoutById(testWorkoutId) } returns flowOf(testWorkout.copy(rating = 5, ratingNote = "Old feedback"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.retryLoadStats()
        advanceUntilIdle()

        assertEquals(2, viewModel.selectedRating.value)
        assertEquals("Pending feedback", viewModel.ratingNote.value)
        assertTrue(viewModel.uiState.value.hasUnsavedFeedback)
    }

    @Test
    fun feedbackChangesAreKeptInSavedState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateRating(3)
        viewModel.updateRatingNote("Save this draft")

        assertEquals(3, savedStateHandle.get<Int>("summary_rating_draft"))
        assertEquals("Save this draft", savedStateHandle.get<String>("summary_note_draft"))
    }

    @Test
    fun doubleDoneWritesOnceAndNavigatesOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { workoutDao.updateWorkout(any()) } coAnswers { gate.await() }
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.navigationEvent.test {
            viewModel.saveAndFinish()
            viewModel.saveAndFinish()
            advanceUntilIdle()
            viewModel.skipAndFinish()
            expectNoEvents()
            gate.complete(Unit)
            advanceUntilIdle()
            awaitItem()
            viewModel.saveAndFinish()
            viewModel.skipAndFinish()
            advanceUntilIdle()
            expectNoEvents()
        }
        coVerify(exactly = 1) { workoutDao.updateWorkout(any()) }
    }

    @Test
    fun saveFailureKeepsDraftAndAllowsRetryWithoutNavigation() = runTest {
        coEvery { workoutDao.updateWorkout(any()) } throws IllegalStateException("Write failed")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateRating(4)
        viewModel.updateRatingNote("Still here")
        viewModel.navigationEvent.test {
            viewModel.saveAndFinish()
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isSaving)
            assertNotNull(viewModel.uiState.value.saveError)
            assertEquals("Still here", viewModel.ratingNote.value)
            expectNoEvents()

            coEvery { workoutDao.updateWorkout(any()) } just Runs
            viewModel.saveAndFinish()
            advanceUntilIdle()
            awaitItem()
        }
        coVerify(exactly = 2) { workoutDao.updateWorkout(match { it.rating == 4 && it.ratingNote == "Still here" }) }
    }

    @Test
    fun statsFailureIsExplicitAndRetryRefreshesOrbAndRecapToo() = runTest {
        every { workoutDao.getWorkoutById(testWorkoutId) } returns flow { error("Read failed") }
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.stats)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.loadError)

        every { workoutDao.getWorkoutById(testWorkoutId) } returns flowOf(testWorkout)
        viewModel.retryLoadStats()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.stats)
        assertNull(viewModel.uiState.value.loadError)
        coVerify(exactly = 2) { volumeOrbRepository.refresh() }
        coVerify(exactly = 2) { workoutRecapFactsUseCase(testWorkoutId) }
    }

    @Test
    fun missingWorkoutIsHandledWithoutCrash() = runTest {
        every { workoutDao.getWorkoutById(testWorkoutId) } returns flowOf(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.stats)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.loadError)
    }

    @Test
    fun doneBeforeInitialStatsLoadCannotOverwriteSavedFeedback() = runTest {
        val gate = CompletableDeferred<Unit>()
        every { workoutDao.getWorkoutById(testWorkoutId) } returns flow {
            gate.await()
            emit(testWorkout.copy(rating = 5, ratingNote = "Existing feedback"))
        }
        val viewModel = createViewModel()
        viewModel.saveAndFinish()
        advanceUntilIdle()
        coVerify(exactly = 0) { workoutDao.updateWorkout(any()) }

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(5, viewModel.selectedRating.value)
        assertEquals("Existing feedback", viewModel.ratingNote.value)
    }

    @Test
    fun restoredClearedRatingIsNotReplacedBySavedRating() = runTest {
        savedStateHandle = SavedStateHandle(
            mapOf(
                "workoutId" to testWorkoutId, "summary_draft_ready" to true,
                "summary_rating_draft" to null, "summary_note_draft" to "Existing note"
            )
        )
        every { workoutDao.getWorkoutById(testWorkoutId) } returns flowOf(testWorkout.copy(rating = 5, ratingNote = "Existing note"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.selectedRating.value)
        assertTrue(viewModel.uiState.value.hasUnsavedFeedback)
        viewModel.saveAndFinish()
        advanceUntilIdle()
        coVerify { workoutDao.updateWorkout(match { it.rating == null && it.ratingNote == "Existing note" }) }
    }

    @Test
    fun noteEditedDuringLoadDoesNotClearUneditedSavedRating() = runTest {
        val gate = CompletableDeferred<Unit>()
        every { workoutDao.getWorkoutById(testWorkoutId) } returns flow {
            gate.await()
            emit(testWorkout.copy(rating = 5, ratingNote = "Old note"))
        }
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateRatingNote("New note")
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(5, viewModel.selectedRating.value)
        assertEquals("New note", viewModel.ratingNote.value)
    }

    @Test
    fun recapValidationPopulatesKnownMusclesAndExercises() = runTest {
        val facts = WorkoutRecapFacts(
            workoutId = testWorkoutId,
            totalVolume = 4070f,
            workingSetCount = 3,
            exerciseCount = 1,
            muscles = listOf("Chest"),
            personalRecordExercises = listOf("Bench Press"),
            leadingMuscle = "Chest",
            isHighestComparableVolumeInSixWeeks = false,
            comparableWorkoutCount = 0
        )
        val squat = testExercise.copy(id = 2L, name = "Barbell Squat", targetMuscle = "Legs")
        coEvery { workoutRecapFactsUseCase(testWorkoutId) } returns facts
        coEvery { exerciseDao.getAllExercisesSync() } returns listOf(testExercise, squat)
        coEvery { muscleGroupDao.getAllMuscleGroupNames() } returns listOf("Chest", "Legs", "Back")
        val requestSlot = slot<NarrativeRequest>()
        every { narrativeGenerator.generate(capture(requestSlot)) } returns flowOf(
            NarrativeResult("PR on Bench Press during a 3-set session.", NarrativeGeneratorType.TEMPLATE)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val knownNames = requestSlot.captured.validationRules.knownNames
        assertTrue("Barbell Squat" in knownNames)
        assertTrue("Bench Press" in knownNames)
        assertTrue("Legs" in knownNames)
        assertTrue("Back" in knownNames)
        assertTrue("Chest" in knownNames)
    }

    @Test
    fun shareAwaitsInitialRecapAndGuardsDoubleTaps() = runTest {
        val recapGate = CompletableDeferred<WorkoutRecapFacts?>()
        val imageGate = CompletableDeferred<Uri>()
        val facts = WorkoutRecapFacts(
            workoutId = testWorkoutId,
            totalVolume = 4070f,
            workingSetCount = 3,
            exerciseCount = 1,
            muscles = listOf("Chest"),
            personalRecordExercises = emptyList(),
            leadingMuscle = "Chest",
            isHighestComparableVolumeInSixWeeks = false,
            comparableWorkoutCount = 0
        )
        coEvery { workoutRecapFactsUseCase(testWorkoutId) } coAnswers { recapGate.await() }
        every { narrativeGenerator.generate(any()) } returns flowOf(
            NarrativeResult("Logged 3 working sets.", NarrativeGeneratorType.TEMPLATE)
        )
        val shareable = ShareableWorkout(
            date = Date(),
            durationMinutes = 60,
            totalVolume = 4070f,
            totalWorkingSets = 3,
            exercises = emptyList(),
            recap = "Logged 3 working sets."
        )
        coEvery { shareWorkoutUseCase.buildShareableWorkout(testWorkoutId, "Logged 3 working sets.") } returns shareable
        coEvery { workoutShareImageGenerator.generate(shareable, testPalette) } coAnswers { imageGate.await() }

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Share awaits initial recap readiness before building payload.
        viewModel.onShareClicked(testPalette)
        viewModel.onShareClicked(testPalette)
        advanceUntilIdle()
        coVerify(exactly = 0) { shareWorkoutUseCase.buildShareableWorkout(any(), any()) }

        recapGate.complete(facts)
        advanceUntilIdle()
        coVerify(exactly = 1) { shareWorkoutUseCase.buildShareableWorkout(testWorkoutId, "Logged 3 working sets.") }
        coVerify(exactly = 1) { workoutShareImageGenerator.generate(shareable, testPalette) }

        val mockUri = mockk<Uri>()
        imageGate.complete(mockUri)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isExporting)
    }
}
