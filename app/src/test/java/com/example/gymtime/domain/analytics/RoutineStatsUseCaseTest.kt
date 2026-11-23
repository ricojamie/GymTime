package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.RoutineSetRow
import com.example.gymtime.data.db.dao.RoutineWorkoutSummaryRow
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineStatsUseCaseTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val workoutDao: WorkoutDao = mockk()
    private val nowMs = 1_750_000_000_000L
    private lateinit var useCase: RoutineStatsUseCase

    @Before
    fun setup() {
        useCase = RoutineStatsUseCase(workoutDao) { nowMs }
    }

    @Test
    fun `intervening high-rep workout does not suppress e1rmDelta against earlier qualifying baseline`() = runTest {
        coEvery { workoutDao.getCompletedWorkoutsForRoutine(1L) } returns listOf(
            summary(1L, daysAgo = 35, volume = 1000f),
            summary(2L, daysAgo = 25, volume = 1600f),
            summary(3L, daysAgo = 1, volume = 1100f)
        )
        coEvery { workoutDao.getWorkingSetsForRoutine(1L) } returns listOf(
            setRow(workoutId = 1L, daysAgo = 35, weight = 100f, reps = 10), // e1rm = 133.33
            setRow(workoutId = 2L, daysAgo = 25, weight = 80f, reps = 20),  // >15 reps (no e1rm)
            setRow(workoutId = 3L, daysAgo = 1, weight = 110f, reps = 10)   // e1rm = 146.67
        )

        val stats = useCase.getStats(1L)
        val trend = stats.exerciseTrends.single()

        assertNotNull(trend.e1rmDelta)
        assertEquals(13.33f, trend.e1rmDelta!!, 0.1f)
        assertEquals("110 × 10", trend.lastBestLabel)
    }

    @Test
    fun `latest high-rep session keeps latest label and computes e1rmDelta from qualifying sessions`() = runTest {
        coEvery { workoutDao.getCompletedWorkoutsForRoutine(1L) } returns listOf(
            summary(1L, daysAgo = 30, volume = 1000f),
            summary(2L, daysAgo = 5, volume = 1100f),
            summary(3L, daysAgo = 1, volume = 1600f)
        )
        coEvery { workoutDao.getWorkingSetsForRoutine(1L) } returns listOf(
            setRow(workoutId = 1L, daysAgo = 30, weight = 100f, reps = 10),
            setRow(workoutId = 2L, daysAgo = 5, weight = 110f, reps = 10),
            setRow(workoutId = 3L, daysAgo = 1, weight = 80f, reps = 20)
        )

        val trend = useCase.getStats(1L).exerciseTrends.single()

        assertEquals("80 × 20", trend.lastBestLabel)
        assertNotNull(trend.e1rmDelta)
        assertEquals(13.33f, trend.e1rmDelta!!, 0.1f)
    }

    @Test
    fun `non-finite weights and future workout timestamps are handled safely`() = runTest {
        coEvery { workoutDao.getCompletedWorkoutsForRoutine(1L) } returns listOf(
            summary(1L, daysAgo = -5, volume = 1000f),
            summary(2L, daysAgo = 2, volume = Float.NaN)
        )
        coEvery { workoutDao.getWorkingSetsForRoutine(1L) } returns listOf(
            setRow(workoutId = 2L, daysAgo = 2, weight = Float.NaN, reps = 8),
            setRow(workoutId = 2L, daysAgo = 2, weight = 100f, reps = 8)
        )

        val stats = useCase.getStats(1L)

        assertEquals(1000f, stats.totalVolume, 0.01f)
        assertEquals(0.25f, stats.workoutsPerWeekRecent ?: 0f, 0.01f)
        assertTrue(stats.exerciseTrends.single().lastE1rm!!.isFinite())
    }

    private fun summary(workoutId: Long, daysAgo: Long, volume: Float): RoutineWorkoutSummaryRow {
        val start = nowMs - TimeUnit.DAYS.toMillis(daysAgo)
        return RoutineWorkoutSummaryRow(
            workoutId = workoutId,
            routineDayId = 1L,
            startTime = Date(start),
            endTime = Date(start + 3_600_000L),
            totalVolume = volume,
            workingSetCount = 3
        )
    }

    private fun setRow(workoutId: Long, daysAgo: Long, weight: Float, reps: Int): RoutineSetRow {
        return RoutineSetRow(
            exerciseId = 1L,
            exerciseName = "Bench Press",
            workoutId = workoutId,
            startTime = Date(nowMs - TimeUnit.DAYS.toMillis(daysAgo)),
            weight = weight,
            reps = reps
        )
    }
}
