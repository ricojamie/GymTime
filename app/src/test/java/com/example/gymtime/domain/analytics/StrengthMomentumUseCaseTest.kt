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
    fun `cardio log types do not color body muscles`() = runTest {
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
        val cardio = result.muscles.first { it.muscle == "Cardio" }

        assertNull(legs.percentChange)
        assertTrue(cardio.percentChange!! > 0f)
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
                durationSeconds = null,
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
}
