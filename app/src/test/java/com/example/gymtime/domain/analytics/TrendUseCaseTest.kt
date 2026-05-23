package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.RatedWorkoutSetInfo
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
    fun `getTrendData counts working sets and excludes warmups`() = runTest {
        val now = Date()
        val sets = listOf(
            SetWithExerciseInfo(
                set = Set(
                    workoutId = 1L,
                    exerciseId = 1L,
                    weight = 45f,
                    reps = 12,
                    timestamp = now,
                    rpe = null,
                    durationSeconds = null,
                    distanceMeters = null,
                    isWarmup = true,
                    isComplete = true
                ),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            ),
            SetWithExerciseInfo(
                set = Set(
                    workoutId = 1L,
                    exerciseId = 1L,
                    weight = 100f,
                    reps = 10,
                    timestamp = now,
                    rpe = null,
                    durationSeconds = null,
                    distanceMeters = null,
                    isWarmup = false,
                    isComplete = true
                ),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            ),
            SetWithExerciseInfo(
                set = Set(
                    workoutId = 1L,
                    exerciseId = 1L,
                    weight = 105f,
                    reps = 8,
                    timestamp = now,
                    rpe = null,
                    durationSeconds = null,
                    distanceMeters = null,
                    isWarmup = false,
                    isComplete = true
                ),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            )
        )

        coEvery { setDao.getSetsWithExerciseInRange(any(), any(), any(), any()) } returns sets

        val result = trendUseCase.getTrendData(
            metric = TrendMetric.SETS,
            period = TimePeriod.ONE_MONTH,
            interval = AggregateInterval.BY_WORKOUT,
            exerciseId = 1L
        )

        assertEquals(1, result.size)
        assertEquals(2f, result[0].value)
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

    @Test
    fun `getTrendData calculates rating by workout`() = runTest {
        coEvery { workoutDao.getRatedWorkoutSetInfo(any(), any()) } returns listOf(
            ratedRow(workoutId = 1L, rating = 5),
            ratedRow(workoutId = 2L, rating = 3)
        )

        val result = trendUseCase.getTrendData(
            metric = TrendMetric.RATING,
            period = TimePeriod.ONE_MONTH,
            interval = AggregateInterval.BY_WORKOUT
        )

        assertEquals(2, result.size)
        assertEquals(5f, result[0].value)
        assertEquals(3f, result[1].value)
    }

    @Test
    fun `getTrendData calculates rated volume and excludes warmups`() = runTest {
        coEvery { workoutDao.getRatedWorkoutSetInfo(any(), any()) } returns listOf(
            ratedRow(workoutId = 1L, setId = 1L, rating = 3, isWarmup = false, weight = 100f, reps = 10),
            ratedRow(workoutId = 1L, setId = 2L, rating = 3, isWarmup = true, weight = 100f, reps = 10)
        )

        val result = trendUseCase.getTrendData(
            metric = TrendMetric.RATED_VOLUME,
            period = TimePeriod.ONE_MONTH,
            interval = AggregateInterval.BY_WORKOUT
        )

        assertEquals(1, result.size)
        assertEquals(600f, result[0].value)
    }

    private fun ratedRow(
        workoutId: Long,
        setId: Long = workoutId,
        rating: Int,
        isWarmup: Boolean = false,
        weight: Float = 100f,
        reps: Int = 10
    ): RatedWorkoutSetInfo {
        return RatedWorkoutSetInfo(
            workoutId = workoutId,
            startTime = Date(1_700_000_000_000L + workoutId * 1000L),
            endTime = Date(1_700_000_000_000L + workoutId * 1000L + 60_000L),
            rating = rating,
            setId = setId,
            exerciseId = 1L,
            targetMuscle = "Chest",
            weight = weight,
            reps = reps,
            isWarmup = isWarmup
        )
    }
}
