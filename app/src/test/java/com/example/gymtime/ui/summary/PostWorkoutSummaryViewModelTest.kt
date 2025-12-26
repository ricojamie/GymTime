package com.example.gymtime.ui.summary

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
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
    private lateinit var volumeOrbRepository: VolumeOrbRepository

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
        Set(id = 1, workoutId = testWorkoutId, exerciseId = 1L, weight = 135f, reps = 10, 
            rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = true, 
            isComplete = true, timestamp = Date(), note = null, supersetGroupId = null, supersetOrderIndex = 0),
        Set(id = 2, workoutId = testWorkoutId, exerciseId = 1L, weight = 185f, reps = 8, 
            rpe = 8f, durationSeconds = null, distanceMeters = null, isWarmup = false, 
            isComplete = true, timestamp = Date(), note = null, supersetGroupId = null, supersetOrderIndex = 0),
        Set(id = 3, workoutId = testWorkoutId, exerciseId = 1L, weight = 185f, reps = 8, 
            rpe = 8.5f, durationSeconds = null, distanceMeters = null, isWarmup = false, 
            isComplete = true, timestamp = Date(), note = null, supersetGroupId = null, supersetOrderIndex = 0),
        Set(id = 4, workoutId = testWorkoutId, exerciseId = 1L, weight = 185f, reps = 6, 
            rpe = 9f, durationSeconds = null, distanceMeters = null, isWarmup = false, 
            isComplete = true, timestamp = Date(), note = null, supersetGroupId = null, supersetOrderIndex = 0)
    )

    @Before
    fun setup() {
        savedStateHandle = SavedStateHandle(mapOf("workoutId" to testWorkoutId))
        workoutDao = mockk(relaxed = true)
        setDao = mockk(relaxed = true)
        exerciseDao = mockk(relaxed = true)
        volumeOrbRepository = mockk(relaxed = true)

        every { workoutDao.getWorkoutById(testWorkoutId) } returns flowOf(testWorkout)
        every { setDao.getSetsForWorkout(testWorkoutId) } returns flowOf(testSets)
        coEvery { exerciseDao.getExerciseByIdSync(1L) } returns testExercise
        every { volumeOrbRepository.orbState } returns MutableStateFlow(
            VolumeOrbState(currentWeekVolume = 5000f, lastWeekVolume = 4000f, progressPercent = 125f, isFirstWeek = false, hasOverflowed = false, justOverflowed = false)
        )
        coEvery { volumeOrbRepository.refresh() } just Runs
        coEvery { volumeOrbRepository.getSessionContribution(any()) } returns 1000f
    }

    private fun createViewModel(): PostWorkoutSummaryViewModel {
        return PostWorkoutSummaryViewModel(savedStateHandle, workoutDao, setDao, exerciseDao, volumeOrbRepository)
    }

    @Test
    fun initLoadsWorkoutStats() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertNotNull(viewModel.workoutStats.value)
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
}
