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
import java.time.ZoneId
import java.time.ZonedDateTime
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
    fun `getHeatMapData calculates levels correctly from weighted volume percentiles`() = runTest {
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
        assertEquals(0, todayData?.workingSetCount)
        assertEquals(1, todayData?.level) // <= 200

        val level3Data = result.find { it.volume == 600f }
        assertEquals(3, level3Data?.level) // > 400
    }

    @Test
    fun `getHeatMapData keeps non-weighted activity at zero volume`() = runTest {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        coEvery { workoutDao.getDailyVolumeForHeatMap() } returns listOf(
            DailyVolume(dailyVol = 0f, date = today, workingSetCount = 3)
        )

        val result = consistencyUseCase.getHeatMapData()

        val todayData = result.find { it.date == today }
        assertEquals(0f, todayData?.volume)
        assertEquals(3, todayData?.workingSetCount)
        assertEquals(0, todayData?.level)
    }

    @Test
    fun `getHeatMapData excludes zero-volume days from percentile thresholds`() = runTest {
        val zone = ZoneId.of("UTC")
        val nowZdt = ZonedDateTime.of(2026, 6, 15, 12, 0, 0, 0, zone)
        val zonedUseCase = ConsistencyUseCase(workoutDao, setDao, userPreferencesRepository, zone) {
            nowZdt.toInstant().toEpochMilli()
        }

        val dayMs = { daysAgo: Long -> nowZdt.minusDays(daysAgo).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() }
        coEvery { workoutDao.getDailyVolumeForHeatMap() } returns listOf(
            DailyVolume(0f, dayMs(10), workingSetCount = 4),
            DailyVolume(0f, dayMs(9), workingSetCount = 4),
            DailyVolume(0f, dayMs(8), workingSetCount = 4),
            DailyVolume(0f, dayMs(7), workingSetCount = 4),
            DailyVolume(0f, dayMs(6), workingSetCount = 4),
            DailyVolume(100f, dayMs(3), workingSetCount = 5),
            DailyVolume(200f, dayMs(2), workingSetCount = 5),
            DailyVolume(300f, dayMs(1), workingSetCount = 5)
        )

        val result = zonedUseCase.getHeatMapData()

        assertEquals(1, result.first { it.date == dayMs(3) }.level)
        assertEquals(2, result.first { it.date == dayMs(2) }.level)
        assertEquals(3, result.first { it.date == dayMs(1) }.level)
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
        coEvery { userPreferencesRepository.restDaysPerWeek } returns flowOf(2)
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
    fun `getConsistencyStats clamps 52 Sunday-Saturday weeks at 100 and ignores future weeks`() = runTest {
        val zone = ZoneId.of("UTC")
        // 2026-05-17 is Sunday.
        val sundayNoon = ZonedDateTime.of(2026, 5, 17, 12, 0, 0, 0, zone)
        val zonedUseCase = ConsistencyUseCase(workoutDao, setDao, userPreferencesRepository, zone) {
            sundayNoon.toInstant().toEpochMilli()
        }

        // 55 past weeks + 1 future week
        val rawData = (-1..54).map { weeksAgo ->
            DailyVolume(100f, sundayNoon.minusWeeks(weeksAgo.toLong()).toInstant().toEpochMilli())
        }
        coEvery { workoutDao.getDailyVolumeForHeatMap() } returns rawData
        coEvery { workoutDao.getWorkoutDatesWithWorkingSets() } returns emptyList()
        coEvery { userPreferencesRepository.restDaysPerWeek } returns flowOf(2)
        coEvery { userPreferencesRepository.bestStreak } returns flowOf(0)
        coEvery { workoutDao.getYearToDateWorkoutCount() } returns 52
        coEvery { setDao.getTotalVolume(any(), any()) } returns 5200f

        val result = zonedUseCase.getConsistencyStats()
        assertEquals(100, result.consistencyScore)

        // Saturday May 16 and Sunday May 17 are in different Sun-Sat weeks (2 active weeks),
        // while Sunday May 17 and Monday May 18 are in the same Sun-Sat week.
        val mondayNoon = ZonedDateTime.of(2026, 5, 18, 12, 0, 0, 0, zone)
        val mondayUseCase = ConsistencyUseCase(workoutDao, setDao, userPreferencesRepository, zone) {
            mondayNoon.toInstant().toEpochMilli()
        }
        coEvery { workoutDao.getDailyVolumeForHeatMap() } returns listOf(
            DailyVolume(100f, ZonedDateTime.of(2026, 5, 16, 10, 0, 0, 0, zone).toInstant().toEpochMilli()),
            DailyVolume(100f, ZonedDateTime.of(2026, 5, 17, 10, 0, 0, 0, zone).toInstant().toEpochMilli()),
            DailyVolume(100f, ZonedDateTime.of(2026, 5, 18, 10, 0, 0, 0, zone).toInstant().toEpochMilli())
        )
        val boundaryStats = mondayUseCase.getConsistencyStats()
        assertEquals(((2f / 52f) * 100).toInt(), boundaryStats.consistencyScore)
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
