package com.example.gymtime.domain.analytics

import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.DailyVolume
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class ConsistencyUseCaseTest {

    @get:Rule
    val testDispatcherRule = TestDispatcherRule()

    private val workoutDao: WorkoutDao = mockk()
    private val setDao: SetDao = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()

    private lateinit var consistencyUseCase: ConsistencyUseCase

    @Before
    fun setup() {
        consistencyUseCase = ConsistencyUseCase(workoutDao, setDao, userPreferencesRepository)
    }

    @Test
    fun `getHeatMapData calculates levels correctly based on percentiles`() = runTest {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val rawData = listOf(
            DailyVolume(100f, today),
            DailyVolume(200f, today - 86400000 * 1),
            DailyVolume(300f, today - 86400000 * 2),
            DailyVolume(400f, today - 86400000 * 3),
            DailyVolume(500f, today - 86400000 * 4),
            DailyVolume(600f, today - 86400000 * 5)
        )
        // Percentiles: p33 ~ 200, p66 ~ 400

        coEvery { workoutDao.getDailyVolumeForHeatMap() } returns rawData

        val result = consistencyUseCase.getHeatMapData()

        // Result covers the full calendar year (Jan 1 - Dec 31)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val isLeapYear = (currentYear % 4 == 0 && currentYear % 100 != 0) || (currentYear % 400 == 0)
        val expectedDays = if (isLeapYear) 366 else 365
        assertEquals(expectedDays, result.size)

        // Find today's data in the result
        val todayData = result.find { it.date == today }
        assertEquals(today, todayData?.date)
        assertEquals(100f, todayData?.volume)
        assertEquals(1, todayData?.level) // <= 200

        val level3Data = result.find { it.volume == 600f }
        assertEquals(3, level3Data?.level) // > 400
    }

    @Test
    fun `getConsistencyStats calculates score based on active weeks`() = runTest {
        val now = System.currentTimeMillis()
        val oneWeekAgo = now - (1000L * 60 * 60 * 24 * 7)
        val twoWeeksAgo = now - (1000L * 60 * 60 * 24 * 14)

        val rawData = listOf(
            DailyVolume(100f, now),
            DailyVolume(100f, oneWeekAgo),
            DailyVolume(100f, twoWeeksAgo)
        )

        coEvery { workoutDao.getDailyVolumeForHeatMap() } returns rawData
        coEvery { workoutDao.getWorkoutDatesWithWorkingSets() } returns emptyList()
        coEvery { userPreferencesRepository.bestStreak } returns flowOf(0)
        coEvery { workoutDao.getYearToDateWorkoutCount() } returns 3
        coEvery { setDao.getTotalVolume(any(), any()) } returns 300f

        val result = consistencyUseCase.getConsistencyStats()

        // 3 active weeks out of 52
        val expectedScore = ((3f / 52f) * 100).toInt()
        assertEquals(expectedScore, result.consistencyScore)
        assertEquals(3, result.ytdWorkouts)
        assertEquals(300f, result.ytdVolume)
    }

    @Test
    fun `getHeatMapData returns full calendar year with zero volume when no workout data`() = runTest {
        coEvery { workoutDao.getDailyVolumeForHeatMap() } returns emptyList()

        val result = consistencyUseCase.getHeatMapData()

        // Result still contains full calendar year, but all past days have level 0
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val isLeapYear = (currentYear % 4 == 0 && currentYear % 100 != 0) || (currentYear % 400 == 0)
        val expectedDays = if (isLeapYear) 366 else 365
        assertEquals(expectedDays, result.size)

        // All past/present days should have level 0 (no volume)
        val pastDays = result.filter { it.level != -1 } // Exclude future days
        assertTrue(pastDays.all { it.level == 0 })
        assertTrue(pastDays.all { it.volume == 0f })
    }
}
