package com.example.gymtime.data

import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeOrbRepositoryTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var setDao: SetDao
    private lateinit var repository: VolumeOrbRepository

    @Before
    fun setup() {
        setDao = mockk(relaxed = true)
        repository = VolumeOrbRepository(setDao)
    }

    @Test
    fun initialStateHasDefaultValues() = runTest {
        val state = repository.orbState.value
        assertEquals(0f, state.lastWeekVolume)
        assertEquals(0f, state.currentWeekVolume)
        assertEquals(0f, state.progressPercent)
        assertTrue(state.isFirstWeek)
        assertFalse(state.hasOverflowed)
        assertFalse(state.justOverflowed)
    }

    @Test
    fun refreshUpdatesStateFromDao() = runTest {
        coEvery { setDao.getVolumeInRange(any(), any()) } returnsMany listOf(10000f, 5000f)

        repository.refresh()
        advanceUntilIdle()

        val state = repository.orbState.value
        assertEquals(10000f, state.lastWeekVolume)
        assertEquals(5000f, state.currentWeekVolume)
        assertFalse(state.isFirstWeek)
    }

    @Test
    fun progressPercentCalculatesCorrectly() = runTest {
        // Last week: 10000, Current: 5000 = 50%
        coEvery { setDao.getVolumeInRange(any(), any()) } returnsMany listOf(10000f, 5000f)

        repository.refresh()
        advanceUntilIdle()

        assertEquals(0.5f, repository.orbState.value.progressPercent, 0.01f)
    }

    @Test
    fun isFirstWeekTrueWhenNoLastWeekVolume() = runTest {
        coEvery { setDao.getVolumeInRange(any(), any()) } returnsMany listOf(0f, 5000f)

        repository.refresh()
        advanceUntilIdle()

        assertTrue(repository.orbState.value.isFirstWeek)
        assertEquals(0f, repository.orbState.value.progressPercent)
    }

    @Test
    fun hasOverflowedTrueWhenCurrentExceedsLast() = runTest {
        coEvery { setDao.getVolumeInRange(any(), any()) } returnsMany listOf(10000f, 15000f)

        repository.refresh()
        advanceUntilIdle()

        assertTrue(repository.orbState.value.hasOverflowed)
        assertTrue(repository.orbState.value.justOverflowed)
    }

    @Test
    fun justOverflowedOnlyTrueOnFirstOverflow() = runTest {
        coEvery { setDao.getVolumeInRange(any(), any()) } returnsMany listOf(10000f, 15000f)

        repository.refresh()
        advanceUntilIdle()
        assertTrue(repository.orbState.value.justOverflowed)

        // Refresh again
        coEvery { setDao.getVolumeInRange(any(), any()) } returnsMany listOf(10000f, 16000f)
        repository.refresh()
        advanceUntilIdle()

        // Still overflowed but not just overflowed
        assertTrue(repository.orbState.value.hasOverflowed)
        assertFalse(repository.orbState.value.justOverflowed)
    }

    @Test
    fun clearOverflowAnimationClearsJustOverflowed() = runTest {
        coEvery { setDao.getVolumeInRange(any(), any()) } returnsMany listOf(10000f, 15000f)

        repository.refresh()
        advanceUntilIdle()
        assertTrue(repository.orbState.value.justOverflowed)

        repository.clearOverflowAnimation()

        assertFalse(repository.orbState.value.justOverflowed)
        assertTrue(repository.orbState.value.hasOverflowed) // Still overflowed, just animation cleared
    }

    @Test
    fun getSessionContributionDelegatesToDao() = runTest {
        coEvery { setDao.getWorkoutVolume(42L) } returns 5000f

        val contribution = repository.getSessionContribution(42L)

        assertEquals(5000f, contribution)
        coVerify { setDao.getWorkoutVolume(42L) }
    }

    @Test
    fun onSetLoggedRefreshesState() = runTest {
        coEvery { setDao.getVolumeInRange(any(), any()) } returns 0f

        repository.onSetLogged()
        advanceUntilIdle()

        coVerify { setDao.getVolumeInRange(any(), any()) }
    }

    @Test
    fun overflowPercentCanExceed100() = runTest {
        // Last week: 10000, Current: 15000 = 150%
        coEvery { setDao.getVolumeInRange(any(), any()) } returnsMany listOf(10000f, 15000f)

        repository.refresh()
        advanceUntilIdle()

        assertEquals(1.5f, repository.orbState.value.progressPercent, 0.01f)
    }
}
