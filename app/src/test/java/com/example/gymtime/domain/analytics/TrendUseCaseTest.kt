package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class TrendUseCaseTest {

    @get:Rule
    val testDispatcherRule = TestDispatcherRule()

    private val setDao: SetDao = mockk()
    private val exerciseDao: ExerciseDao = mockk()
    private val workoutDao: WorkoutDao = mockk()

    private lateinit var trendUseCase: TrendUseCase

    @Before
    fun setup() {
        trendUseCase = TrendUseCase(setDao, exerciseDao, workoutDao)
    }

    @Test
    fun `getTrendData calculates volume correctly`() = runTest {
        val now = Date()
        val sets = listOf(
            SetWithExerciseInfo(
                set = Set(workoutId = 1L, exerciseId = 1L, weight = 100f, reps = 10, timestamp = now, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            ),
            SetWithExerciseInfo(
                set = Set(workoutId = 1L, exerciseId = 1L, weight = 100f, reps = 8, timestamp = now, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            )
        )

        coEvery { setDao.getSetsWithExerciseInRange(any(), any(), any(), any()) } returns sets

        val result = trendUseCase.getTrendData(
            metric = TrendMetric.VOLUME,
            period = TimePeriod.ONE_MONTH,
            interval = AggregateInterval.BY_WORKOUT,
            exerciseId = 1L
        )

        assertEquals(1, result.size)
        assertEquals(1800f, result[0].value)
    }

    @Test
    fun `getTrendData calculates E1RM correctly`() = runTest {
        // E1RM = W * (1 + 0.0333 * R)
        // 100 * (1 + 0.0333 * 10) = 133.3
        val now = Date()
        val sets = listOf(
            SetWithExerciseInfo(
                set = Set(workoutId = 1L, exerciseId = 1L, weight = 100f, reps = 10, timestamp = now, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            )
        )

        coEvery { setDao.getSetsWithExerciseInRange(any(), any(), any(), any()) } returns sets

        val result = trendUseCase.getTrendData(
            metric = TrendMetric.E1RM,
            period = TimePeriod.ONE_MONTH,
            interval = AggregateInterval.BY_WORKOUT,
            exerciseId = 1L
        )

        assertEquals(1, result.size)
        assertEquals(133.3f, result[0].value, 0.1f)
    }

    @Test
    fun `getTrendData returns empty for no sets`() = runTest {
        coEvery { setDao.getSetsWithExerciseInRange(any(), any(), any(), any()) } returns emptyList()

        val result = trendUseCase.getTrendData(
            metric = TrendMetric.VOLUME,
            period = TimePeriod.ONE_MONTH,
            interval = AggregateInterval.BY_WORKOUT
        )

        assertTrue(result.isEmpty())
    }
}
