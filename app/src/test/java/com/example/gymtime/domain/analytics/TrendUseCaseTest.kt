package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.RatedWorkoutSetInfo
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.every
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
import java.util.Date
import java.util.Locale

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
        coEvery { workoutDao.getAllWorkoutsSync() } returns listOf(
            Workout(id = 1L, startTime = Date(), endTime = Date(), name = null, note = null),
            Workout(id = 2L, startTime = Date(), endTime = Date(), name = null, note = null)
        )
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

    @Test
    fun `weekly trend keys late december and early january separately`() = runTest {
        val zone = ZoneId.of("America/New_York")
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val nowMs = ZonedDateTime.of(2027, 1, 15, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
            val jan2026 = Date.from(ZonedDateTime.of(2026, 1, 2, 12, 0, 0, 0, zone).toInstant())
            val dec2026 = Date.from(ZonedDateTime.of(2026, 12, 29, 12, 0, 0, 0, zone).toInstant())
            val zonedUseCase = TrendUseCase(setDao, exerciseDao, workoutDao, zone) { nowMs }

            coEvery { setDao.getSetsWithExerciseInRange(any(), any(), any(), any()) } returns listOf(
                SetWithExerciseInfo(
                    set = Set(workoutId = 1L, exerciseId = 1L, weight = 100f, reps = 10, timestamp = jan2026, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true),
                    exerciseName = "Bench Press",
                    targetMuscle = "Chest"
                ),
                SetWithExerciseInfo(
                    set = Set(workoutId = 2L, exerciseId = 1L, weight = 200f, reps = 10, timestamp = dec2026, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true),
                    exerciseName = "Bench Press",
                    targetMuscle = "Chest"
                )
            )

            val result = zonedUseCase.getTrendData(
                metric = TrendMetric.VOLUME,
                period = TimePeriod.ALL,
                interval = AggregateInterval.WEEKLY
            )

            assertEquals(2, result.size)
            assertEquals(1000f, result[0].value, 0.01f)
            assertEquals(2000f, result[1].value, 0.01f)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `AVG_WEIGHT excludes bodyweight null-weight sets from rep denominator`() = runTest {
        val now = Date()
        coEvery { setDao.getSetsWithExerciseInRange(any(), any(), any(), any()) } returns listOf(
            SetWithExerciseInfo(
                set = Set(workoutId = 1L, exerciseId = 1L, weight = 100f, reps = 10, timestamp = now, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            ),
            SetWithExerciseInfo(
                set = Set(workoutId = 1L, exerciseId = 2L, weight = null, reps = 20, timestamp = now, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true),
                exerciseName = "Push-Up",
                targetMuscle = "Chest"
            )
        )

        val result = trendUseCase.getTrendData(
            metric = TrendMetric.AVG_WEIGHT,
            period = TimePeriod.ONE_MONTH,
            interval = AggregateInterval.BY_WORKOUT
        )

        assertEquals(1, result.size)
        assertEquals(100f, result[0].value, 0.01f)
    }

    @Test
    fun `incomplete sets, unfinished workouts, warmups muscle, over-15 E1RM, and NaN are excluded`() = runTest {
        val now = Date()
        coEvery { workoutDao.getAllWorkoutsSync() } returns listOf(
            Workout(id = 1L, startTime = now, endTime = now, name = null, note = null),
            Workout(id = 2L, startTime = now, endTime = null, name = null, note = null)
        )
        coEvery { setDao.getSetsWithExerciseInRange(any(), any(), any(), any()) } returns listOf(
            SetWithExerciseInfo(
                set = Set(workoutId = 1L, exerciseId = 1L, weight = 100f, reps = 10, timestamp = now, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = false),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            ),
            SetWithExerciseInfo(
                set = Set(workoutId = 2L, exerciseId = 1L, weight = 300f, reps = 10, timestamp = now, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            ),
            SetWithExerciseInfo(
                set = Set(workoutId = 1L, exerciseId = 2L, weight = 50f, reps = 10, timestamp = now, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true),
                exerciseName = "Band Pull Apart",
                targetMuscle = "Warmups"
            ),
            SetWithExerciseInfo(
                set = Set(workoutId = 1L, exerciseId = 1L, weight = 500f, reps = 20, timestamp = now, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            ),
            SetWithExerciseInfo(
                set = Set(workoutId = 1L, exerciseId = 1L, weight = Float.NaN, reps = 5, timestamp = now, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            )
        )

        val e1rm = trendUseCase.getTrendData(
            metric = TrendMetric.E1RM,
            period = TimePeriod.ONE_MONTH,
            interval = AggregateInterval.BY_WORKOUT
        )
        assertTrue(e1rm.isEmpty())

        val volume = trendUseCase.getTrendData(
            metric = TrendMetric.VOLUME,
            period = TimePeriod.ONE_MONTH,
            interval = AggregateInterval.BY_WORKOUT
        )
        assertEquals(1, volume.size)
        assertEquals(10_000f, volume[0].value, 0.01f)
    }

    @Test
    fun `getTrophyCasePRs filters out non-positive or non-finite personal bests`() = runTest {
        val validEx = Exercise(1L, "Bench", "Chest", LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90, isStarred = true)
        val zeroEx = Exercise(2L, "Zero", "Chest", LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90, isStarred = true)
        val nanEx = Exercise(3L, "NaN", "Chest", LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90, isStarred = true)
        every { exerciseDao.getStarredExercises() } returns flowOf(listOf(validEx, zeroEx, nanEx))
        coEvery { setDao.getPersonalBest(1L) } returns Set(workoutId = 1L, exerciseId = 1L, weight = 225f, reps = 5, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true, timestamp = Date())
        coEvery { setDao.getPersonalBest(2L) } returns Set(workoutId = 1L, exerciseId = 2L, weight = 0f, reps = 10, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true, timestamp = Date())
        coEvery { setDao.getPersonalBest(3L) } returns Set(workoutId = 1L, exerciseId = 3L, weight = Float.NaN, reps = 5, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true, timestamp = Date())

        val prs = trendUseCase.getTrophyCasePRs()

        assertEquals(1, prs.size)
        assertEquals(1L, prs.single().exercise.id)
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
