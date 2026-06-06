package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExercisePerformanceInfo
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class StrengthMomentumUseCaseTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val setDao: SetDao = mockk()
    private val muscleGroupDao: MuscleGroupDao = mockk()
    private lateinit var useCase: StrengthMomentumUseCase

    private val now = TimeUnit.DAYS.toMillis(100)

    @Before
    fun setup() {
        coEvery { muscleGroupDao.getAllMuscleGroupNames() } returns listOf(
            "Chest",
            "Back",
            "Legs",
            "Cardio"
        )
        coEvery { setDao.getMuscleWeeklyVolumeComparison(any(), any(), any()) } returns emptyList()
        useCase = StrengthMomentumUseCase(setDao, muscleGroupDao)
    }

    @Test
    fun `E1RM improvement maps to positive muscle momentum`() = runTest {
        coEvery { setDao.getPerformanceSetsWithExerciseInRange(any(), any()) } returns listOf(
            strengthSet(weight = 100f, reps = 10, daysAgo = 40, muscle = "Chest"),
            strengthSet(weight = 110f, reps = 10, daysAgo = 7, muscle = "Chest")
        )

        val result = useCase.getStrengthMomentum(now)
        val chest = result.muscles.first { it.muscle == "Chest" }

        assertNotNull(chest.percentChange)
        assertTrue(chest.percentChange!! > 0f)
        assertEquals(MomentumDirection.STRONG_UP, chest.direction)
    }

    @Test
    fun `decline maps to negative muscle momentum`() = runTest {
        coEvery { setDao.getPerformanceSetsWithExerciseInRange(any(), any()) } returns listOf(
            strengthSet(weight = 100f, reps = 10, daysAgo = 40, muscle = "Legs"),
            strengthSet(weight = 90f, reps = 10, daysAgo = 7, muscle = "Legs")
        )

        val result = useCase.getStrengthMomentum(now)
        val legs = result.muscles.first { it.muscle == "Legs" }

        assertNotNull(legs.percentChange)
        assertTrue(legs.percentChange!! < 0f)
        assertEquals(MomentumDirection.STRONG_DOWN, legs.direction)
    }

    @Test
    fun `positive momentum never maps to declining direction when history was stronger`() {
        val direction = directionFor(
            percent = 7.6f,
            history = listOf(18f, 20f, 22f, 24f, 26f)
        )

        assertEquals(MomentumDirection.STRONG_UP, direction)
    }

    @Test
    fun `positive momentum never maps to flat direction when history is similar`() {
        val direction = directionFor(
            percent = 4.7f,
            history = listOf(2f, 4f, 5f, 6f, 6.5f)
        )

        assertEquals(MomentumDirection.UP, direction)
    }

    @Test
    fun `negative momentum never maps to improving direction when history was worse`() {
        val direction = directionFor(
            percent = -3f,
            history = listOf(-20f, -22f, -24f, -26f, -28f)
        )

        assertEquals(MomentumDirection.DOWN, direction)
    }

    @Test
    fun `warmup sets are ignored`() = runTest {
        coEvery { setDao.getPerformanceSetsWithExerciseInRange(any(), any()) } returns listOf(
            strengthSet(weight = 100f, reps = 10, daysAgo = 40, muscle = "Chest", isWarmup = true),
            strengthSet(weight = 120f, reps = 10, daysAgo = 7, muscle = "Chest")
        )

        val result = useCase.getStrengthMomentum(now)
        val chest = result.muscles.first { it.muscle == "Chest" }

        assertNull(chest.percentChange)
        assertEquals(MomentumDirection.NO_BASELINE, chest.direction)
    }

    @Test
    fun `custom exercise contributes through target muscle`() = runTest {
        coEvery { setDao.getPerformanceSetsWithExerciseInRange(any(), any()) } returns listOf(
            strengthSet(
                exerciseId = 99L,
                exerciseName = "Cable High Row",
                weight = 100f,
                reps = 10,
                daysAgo = 40,
                muscle = "Back"
            ),
            strengthSet(
                exerciseId = 99L,
                exerciseName = "Cable High Row",
                weight = 110f,
                reps = 10,
                daysAgo = 7,
                muscle = "Back"
            )
        )

        val result = useCase.getStrengthMomentum(now)
        val back = result.muscles.first { it.muscle == "Back" }

        assertTrue(back.percentChange!! > 0f)
        assertEquals("Cable High Row", back.contributingExercises.first().exerciseName)
    }

    @Test
    fun `missing baseline returns no baseline state`() = runTest {
        coEvery { setDao.getPerformanceSetsWithExerciseInRange(any(), any()) } returns listOf(
            strengthSet(weight = 110f, reps = 10, daysAgo = 7, muscle = "Chest")
        )

        val result = useCase.getStrengthMomentum(now)
        val chest = result.muscles.first { it.muscle == "Chest" }

        assertNull(chest.percentChange)
        assertEquals(MomentumDirection.NO_BASELINE, chest.direction)
    }

    @Test
    fun `cardio log types are excluded from strength momentum`() = runTest {
        coEvery { setDao.getPerformanceSetsWithExerciseInRange(any(), any()) } returns listOf(
            performanceSet(
                exerciseId = 3L,
                exerciseName = "Hill Sprint",
                logType = LogType.DISTANCE_TIME,
                daysAgo = 40,
                muscle = "Legs",
                distanceMeters = 1000f
            ),
            performanceSet(
                exerciseId = 3L,
                exerciseName = "Hill Sprint",
                logType = LogType.DISTANCE_TIME,
                daysAgo = 7,
                muscle = "Legs",
                distanceMeters = 1200f
            )
        )

        val result = useCase.getStrengthMomentum(now)
        val legs = result.muscles.first { it.muscle == "Legs" }

        assertNull(legs.percentChange)
        assertTrue(result.muscles.none { it.muscle == "Cardio" })
    }

    @Test
    fun `cardio target muscle is excluded from strength momentum`() = runTest {
        coEvery { setDao.getPerformanceSetsWithExerciseInRange(any(), any()) } returns listOf(
            performanceSet(
                exerciseId = 4L,
                exerciseName = "Stair Climber",
                logType = LogType.DURATION,
                daysAgo = 40,
                muscle = "Cardio",
                durationSeconds = 600
            ),
            performanceSet(
                exerciseId = 4L,
                exerciseName = "Stair Climber",
                logType = LogType.DURATION,
                daysAgo = 7,
                muscle = "Cardio",
                durationSeconds = 900
            )
        )

        val result = useCase.getStrengthMomentum(now)

        assertTrue(result.muscles.none { it.muscle == "Cardio" })
        assertTrue(result.topImproving.none { it.muscle == "Cardio" })
    }

    @Test
    fun `extra reps on secondary sets at same weight register as progress`() = runTest {
        coEvery { setDao.getPerformanceSetsWithExerciseInRange(any(), any()) } returns listOf(
            strengthSet(weight = 60f, reps = 9, daysAgo = 40, muscle = "Shoulders"),
            strengthSet(weight = 60f, reps = 8, daysAgo = 40, muscle = "Shoulders"),
            strengthSet(weight = 60f, reps = 8, daysAgo = 40, muscle = "Shoulders"),
            strengthSet(weight = 60f, reps = 9, daysAgo = 7, muscle = "Shoulders"),
            strengthSet(weight = 60f, reps = 9, daysAgo = 7, muscle = "Shoulders"),
            strengthSet(weight = 60f, reps = 9, daysAgo = 7, muscle = "Shoulders")
        )

        val result = useCase.getStrengthMomentum(now)
        val shoulders = result.muscles.first { it.muscle == "Shoulders" }

        assertNotNull(shoulders.percentChange)
        assertTrue(
            "Expected positive momentum, was ${shoulders.percentChange}",
            shoulders.percentChange!! > 0f
        )
        assertEquals(MomentumDirection.UP, shoulders.direction)
    }

    @Test
    fun `mixed exercise changes expose improving and declining contributors`() = runTest {
        coEvery { setDao.getPerformanceSetsWithExerciseInRange(any(), any()) } returns listOf(
            strengthSet(
                exerciseId = 1L,
                exerciseName = "Bench Press",
                weight = 100f,
                reps = 10,
                daysAgo = 40,
                muscle = "Chest"
            ),
            strengthSet(
                exerciseId = 1L,
                exerciseName = "Bench Press",
                weight = 120f,
                reps = 10,
                daysAgo = 7,
                muscle = "Chest"
            ),
            strengthSet(
                exerciseId = 2L,
                exerciseName = "Incline Press",
                weight = 100f,
                reps = 10,
                daysAgo = 40,
                muscle = "Chest"
            ),
            strengthSet(
                exerciseId = 2L,
                exerciseName = "Incline Press",
                weight = 90f,
                reps = 10,
                daysAgo = 7,
                muscle = "Chest"
            )
        )

        val result = useCase.getStrengthMomentum(now)
        val chest = result.muscles.first { it.muscle == "Chest" }

        assertTrue(chest.hasMixedContributors)
        assertEquals("Bench Press", chest.improvingContributors.first().exerciseName)
        assertEquals("Incline Press", chest.decliningContributors.first().exerciseName)
    }

    private fun strengthSet(
        exerciseId: Long = 1L,
        exerciseName: String = "Bench Press",
        weight: Float,
        reps: Int,
        daysAgo: Int,
        muscle: String,
        isWarmup: Boolean = false
    ): SetWithExercisePerformanceInfo {
        return performanceSet(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            logType = LogType.WEIGHT_REPS,
            daysAgo = daysAgo,
            muscle = muscle,
            weight = weight,
            reps = reps,
            isWarmup = isWarmup
        )
    }

    private fun performanceSet(
        exerciseId: Long,
        exerciseName: String,
        logType: LogType,
        daysAgo: Int,
        muscle: String,
        weight: Float? = null,
        reps: Int? = null,
        durationSeconds: Int? = null,
        distanceMeters: Float? = null,
        isWarmup: Boolean = false
    ): SetWithExercisePerformanceInfo {
        return SetWithExercisePerformanceInfo(
            set = Set(
                workoutId = daysAgo.toLong(),
                exerciseId = exerciseId,
                weight = weight,
                reps = reps,
                rpe = null,
                durationSeconds = durationSeconds,
                distanceMeters = distanceMeters,
                isWarmup = isWarmup,
                isComplete = true,
                timestamp = Date(now - TimeUnit.DAYS.toMillis(daysAgo.toLong()))
            ),
            exerciseName = exerciseName,
            targetMuscle = muscle,
            logType = logType
        )
    }

    private fun directionFor(percent: Float, history: List<Float>): MomentumDirection {
        val method = StrengthMomentumUseCase::class.java.getDeclaredMethod(
            "directionFor",
            Float::class.javaPrimitiveType,
            List::class.java
        )
        method.isAccessible = true
        return method.invoke(useCase, percent, history) as MomentumDirection
    }
}
