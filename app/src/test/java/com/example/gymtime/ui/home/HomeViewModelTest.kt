package com.example.gymtime.ui.home

import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.domain.analytics.StrengthMomentumState
import com.example.gymtime.domain.analytics.StrengthMomentumUseCase
import com.example.gymtime.util.StreakCalculator
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var routineRepository: RoutineRepository
    private lateinit var volumeOrbRepository: VolumeOrbRepository
    private lateinit var strengthMomentumUseCase: StrengthMomentumUseCase

    private class MutableClock(
        private var currentInstant: Instant,
        private val zoneId: ZoneId
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)
        override fun instant(): Instant = currentInstant
        fun setInstant(instant: Instant) {
            currentInstant = instant
        }
    }

    @Before
    fun setup() {
        userPreferencesRepository = mockk(relaxed = true)
        workoutRepository = mockk(relaxed = true)
        routineRepository = mockk(relaxed = true)
        volumeOrbRepository = mockk(relaxed = true)
        strengthMomentumUseCase = mockk(relaxed = true)

        every { userPreferencesRepository.userName } returns flowOf("Test User")
        every { userPreferencesRepository.bestStreak } returns flowOf(0)
        every { userPreferencesRepository.restDaysPerWeek } returns flowOf(2)
        every { workoutRepository.getOngoingWorkoutFlow() } returns flowOf(null)
        every { routineRepository.getActiveRoutineStatus() } returns flowOf(null)
        every { volumeOrbRepository.orbState } returns MutableStateFlow(VolumeOrbState())

        coEvery { workoutRepository.getTotalVolume(any(), any()) } returns 0f
        coEvery { workoutRepository.getWorkoutDatesWithWorkingSets() } returns emptyList()
        coEvery { workoutRepository.getYearToDateWorkoutCount() } returns 0
        coEvery { volumeOrbRepository.refresh() } just Runs
        coEvery { strengthMomentumUseCase.getStrengthMomentum(any()) } returns StrengthMomentumState()
    }

    private fun createViewModel(
        clock: Clock = Clock.fixed(
            LocalDateTime.of(2026, 5, 15, 10, 0).atZone(ZoneId.of("America/New_York")).toInstant(),
            ZoneId.of("America/New_York")
        )
    ): HomeViewModel {
        return HomeViewModel(
            userPreferencesRepository = userPreferencesRepository,
            workoutRepository = workoutRepository,
            routineRepository = routineRepository,
            volumeOrbRepository = volumeOrbRepository,
            strengthMomentumUseCase = strengthMomentumUseCase,
            clock = clock,
            computeDispatcher = dispatcherRule.testDispatcher
        )
    }

    @Test
    fun initCallsDataLoadingMethods() = runTest {
        createViewModel()
        advanceUntilIdle()

        coVerify { workoutRepository.getTotalVolume(any(), any()) }
        coVerify { workoutRepository.getWorkoutDatesWithWorkingSets() }
        coVerify { workoutRepository.getYearToDateWorkoutCount() }
        coVerify { volumeOrbRepository.refresh() }
        coVerify { strengthMomentumUseCase.getStrengthMomentum(any()) }
    }

    @Test
    fun weeklyVolumeUpdatesFromRepository() = runTest {
        coEvery { workoutRepository.getTotalVolume(any(), any()) } returns 15000f
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(15000f, viewModel.weeklyVolume.value)
    }

    @Test
    fun sundayWeeklyVolumeQueriesFromPreviousMondayInSundayFirstLocale() = runTest {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val zone = ZoneId.of("America/New_York")
            // Sunday, May 17, 2026 at 14:30 -> week should start Monday, May 11, 2026 at 00:00
            val sundayClock = Clock.fixed(
                LocalDateTime.of(2026, 5, 17, 14, 30).atZone(zone).toInstant(),
                zone
            )
            val expectedMondayStart = LocalDate.of(2026, 5, 11).atStartOfDay(zone).toInstant().toEpochMilli()
            val expectedEnd = sundayClock.millis()
            coEvery { workoutRepository.getTotalVolume(expectedMondayStart, expectedEnd) } returns 12450f

            val viewModel = createViewModel(clock = sundayClock)
            advanceUntilIdle()

            assertEquals(12450f, viewModel.weeklyVolume.value)
            coVerify(exactly = 1) { workoutRepository.getTotalVolume(expectedMondayStart, expectedEnd) }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun ytdWorkoutsUpdatesFromRepository() = runTest {
        coEvery { workoutRepository.getYearToDateWorkoutCount() } returns 42
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(42, viewModel.ytdWorkouts.value)
    }

    @Test
    fun emptyWorkoutDatesReturnsRestingStreak() = runTest {
        coEvery { workoutRepository.getWorkoutDatesWithWorkingSets() } returns emptyList()
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(StreakCalculator.StreakState.RESTING, viewModel.streakResult.value.state)
        assertEquals(0, viewModel.streakResult.value.streakDays)
    }

    @Test
    fun singleLoadOnLaunchDoesNotDuplicateOnInitialResume() = runTest {
        val viewModel = createViewModel()
        viewModel.refreshOnResume()
        advanceUntilIdle()

        // 3 total volume queries: weekly, YTD, prior year (no unused 7-day trend queries, no resume double-load)
        coVerify(exactly = 3) { workoutRepository.getTotalVolume(any(), any()) }
        coVerify(exactly = 1) { workoutRepository.getWorkoutDatesWithWorkingSets() }
        coVerify(exactly = 1) { workoutRepository.getYearToDateWorkoutCount() }
        coVerify(exactly = 1) { volumeOrbRepository.refresh() }
        coVerify(exactly = 1) { strengthMomentumUseCase.getStrengthMomentum(any()) }
    }

    @Test
    fun nonDuplicateResumeRefreshReloadsOnlyAfterPauseOrDateChange() = runTest {
        val zone = ZoneId.of("America/New_York")
        val clock = MutableClock(
            LocalDateTime.of(2026, 5, 15, 10, 0).atZone(zone).toInstant(),
            zone
        )
        val viewModel = createViewModel(clock = clock)
        advanceUntilIdle()
        clearMocks(workoutRepository, volumeOrbRepository, strengthMomentumUseCase, answers = false)

        // Same-day resume without prior pause is a no-op
        viewModel.refreshOnResume()
        advanceUntilIdle()
        coVerify(exactly = 0) { workoutRepository.getTotalVolume(any(), any()) }
        coVerify(exactly = 0) { volumeOrbRepository.refresh() }
        coVerify(exactly = 0) { strengthMomentumUseCase.getStrengthMomentum(any()) }

        // Pause then resume on same day triggers exactly 1 refresh
        viewModel.onScreenPaused()
        viewModel.refreshOnResume()
        advanceUntilIdle()
        coVerify(exactly = 3) { workoutRepository.getTotalVolume(any(), any()) }
        coVerify(exactly = 1) { volumeOrbRepository.refresh() }
        coVerify(exactly = 1) { strengthMomentumUseCase.getStrengthMomentum(any()) }

        // Second resume without another pause does not duplicate
        viewModel.refreshOnResume()
        advanceUntilIdle()
        coVerify(exactly = 3) { workoutRepository.getTotalVolume(any(), any()) }
        coVerify(exactly = 1) { volumeOrbRepository.refresh() }
        coVerify(exactly = 1) { strengthMomentumUseCase.getStrengthMomentum(any()) }

        // Date rollover triggers refresh on resume even without explicit pause
        clock.setInstant(LocalDateTime.of(2026, 5, 16, 8, 0).atZone(zone).toInstant())
        viewModel.refreshOnResume()
        advanceUntilIdle()
        coVerify(exactly = 6) { workoutRepository.getTotalVolume(any(), any()) }
        coVerify(exactly = 2) { volumeOrbRepository.refresh() }
        coVerify(exactly = 2) { strengthMomentumUseCase.getStrengthMomentum(any()) }
    }

    @Test
    fun refreshDataReloadsAllData() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(workoutRepository, volumeOrbRepository, answers = false)
        clearMocks(strengthMomentumUseCase, answers = false)

        viewModel.refreshData()
        advanceUntilIdle()

        coVerify { workoutRepository.getTotalVolume(any(), any()) }
        coVerify { workoutRepository.getWorkoutDatesWithWorkingSets() }
        coVerify { workoutRepository.getYearToDateWorkoutCount() }
        coVerify { volumeOrbRepository.refresh() }
        coVerify { strengthMomentumUseCase.getStrengthMomentum(any()) }
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
        coEvery { workoutRepository.getWorkoutDatesWithWorkingSets() } returns emptyList()
        createViewModel()
        advanceUntilIdle()
        coVerify { userPreferencesRepository.updateBestStreakIfNeeded(any()) }
    }
}
