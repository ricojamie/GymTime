package com.example.gymtime.ui.home

import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.util.StreakCalculator
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
class HomeViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var workoutDao: WorkoutDao
    private lateinit var routineDao: RoutineDao
    private lateinit var setDao: SetDao
    private lateinit var volumeOrbRepository: VolumeOrbRepository

    @Before
    fun setup() {
        userPreferencesRepository = mockk(relaxed = true)
        workoutDao = mockk(relaxed = true)
        routineDao = mockk(relaxed = true)
        setDao = mockk(relaxed = true)
        volumeOrbRepository = mockk(relaxed = true)

        every { userPreferencesRepository.userName } returns flowOf("Test User")
        every { userPreferencesRepository.activeRoutineId } returns flowOf(null)
        every { userPreferencesRepository.bestStreak } returns flowOf(0)
        every { workoutDao.getOngoingWorkout() } returns flowOf(null)
        every { volumeOrbRepository.orbState } returns MutableStateFlow(VolumeOrbState())

        coEvery { setDao.getTotalVolume(any(), any()) } returns 0f
        coEvery { workoutDao.getWorkoutDatesWithWorkingSets() } returns emptyList()
        coEvery { workoutDao.getYearToDateWorkoutCount() } returns 0
        coEvery { volumeOrbRepository.refresh() } just Runs
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(userPreferencesRepository, workoutDao, routineDao, setDao, volumeOrbRepository)
    }

    @Test
    fun initCallsDataLoadingMethods() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { setDao.getTotalVolume(any(), any()) }
        coVerify { workoutDao.getWorkoutDatesWithWorkingSets() }
        coVerify { workoutDao.getYearToDateWorkoutCount() }
        coVerify { volumeOrbRepository.refresh() }
    }

    @Test
    fun weeklyVolumeUpdatesFromSetDao() = runTest {
        coEvery { setDao.getTotalVolume(any(), any()) } returns 15000f
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(15000f, viewModel.weeklyVolume.value)
    }

    @Test
    fun ytdWorkoutsUpdatesFromWorkoutDao() = runTest {
        coEvery { workoutDao.getYearToDateWorkoutCount() } returns 42
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(42, viewModel.ytdWorkouts.value)
    }

    @Test
    fun emptyWorkoutDatesReturnsRestingStreak() = runTest {
        coEvery { workoutDao.getWorkoutDatesWithWorkingSets() } returns emptyList()
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(StreakCalculator.StreakState.RESTING, viewModel.streakResult.value.state)
        assertEquals(0, viewModel.streakResult.value.streakDays)
    }

    @Test
    fun refreshDataReloadsAllData() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(setDao, workoutDao, volumeOrbRepository, answers = false)
        
        viewModel.refreshData()
        advanceUntilIdle()

        coVerify { setDao.getTotalVolume(any(), any()) }
        coVerify { workoutDao.getWorkoutDatesWithWorkingSets() }
        coVerify { workoutDao.getYearToDateWorkoutCount() }
        coVerify { volumeOrbRepository.refresh() }
    }

    @Test
    fun clearOrbOverflowAnimationDelegatesToRepository() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.clearOrbOverflowAnimation()
        verify { volumeOrbRepository.clearOverflowAnimation() }
    }

    @Test
    fun bestStreakUpdateIsCalledAfterStreakCalculation() = runTest {
        coEvery { workoutDao.getWorkoutDatesWithWorkingSets() } returns emptyList()
        val viewModel = createViewModel()
        advanceUntilIdle()
        coVerify { userPreferencesRepository.updateBestStreakIfNeeded(any()) }
    }
}
