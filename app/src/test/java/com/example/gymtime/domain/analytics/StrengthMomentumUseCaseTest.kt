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
    private val now = TimeUnit.DAYS.toMillis(500)

    @Before
    fun setup() {
        coEvery { muscleGroupDao.getAllMuscleGroupNames() } returns listOf(
            "Chest", "Back", "Shoulders", "Biceps", "Triceps", "Core", "Legs", "Cardio"
        )
        coEvery { setDao.getMuscleWeeklyVolumeComparison(any(), any(), any()) } returns emptyList()
        useCase = StrengthMomentumUseCase(setDao, muscleGroupDao)
    }

    @Test
    fun `three recent sessions beat the preceding three by estimated strength`() = runTest {
        stubSets(
            sessionSeries(weight = 110f, reps = 8, daysAgo = listOf(1, 5, 9)) +
                sessionSeries(weight = 100f, reps = 8, daysAgo = listOf(15, 19, 23))
        )

        val chest = resultFor("Chest")

        assertEquals(MomentumDataStatus.READY, chest.status)
        assertEquals(MomentumConfidence.STANDARD, chest.confidence)
        assertEquals(MomentumDirection.STRONG_UP, chest.direction)
        assertTrue(chest.percentChange!! > 5f)
    }

    @Test
    fun `adding unchanged sets does not create strength progress`() = runTest {
        val recent = listOf(1, 5).flatMap { day ->
            listOf(
                strengthSet(weight = 100f, reps = 8, daysAgo = day, setIndex = 0),
                strengthSet(weight = 100f, reps = 8, daysAgo = day, setIndex = 1),
                strengthSet(weight = 100f, reps = 8, daysAgo = day, setIndex = 2)
            )
        }
        val baseline = listOf(15, 19).flatMap { day ->
            listOf(
                strengthSet(weight = 100f, reps = 8, daysAgo = day, setIndex = 0),
                strengthSet(weight = 100f, reps = 8, daysAgo = day, setIndex = 1)
            )
        }
        stubSets(recent + baseline)

        val chest = resultFor("Chest")

        assertEquals(0f, chest.percentChange)
        assertEquals(MomentumDirection.FLAT, chest.direction)
    }

    @Test
    fun `stronger secondary sets register without adding set count`() = runTest {
        val recent = listOf(1, 5).flatMap { day ->
            listOf(9, 9, 9).mapIndexed { index, reps ->
                strengthSet(weight = 60f, reps = reps, daysAgo = day, muscle = "Shoulders", setIndex = index)
            }
        }
        val baseline = listOf(15, 19).flatMap { day ->
            listOf(9, 6, 6).mapIndexed { index, reps ->
                strengthSet(weight = 60f, reps = reps, daysAgo = day, muscle = "Shoulders", setIndex = index)
            }
        }
        stubSets(recent + baseline)

        val shoulders = resultFor("Shoulders")

        assertTrue(shoulders.percentChange!! >= 2f)
        assertTrue(shoulders.direction == MomentumDirection.UP || shoulders.direction == MomentumDirection.STRONG_UP)
    }

    @Test
    fun `unfinished and warmup sets are ignored defensively`() = runTest {
        val valid = sessionSeries(weight = 100f, reps = 8, daysAgo = listOf(1, 5, 15, 19))
        val noise = listOf(
            strengthSet(weight = 300f, reps = 8, daysAgo = 1, setIndex = 8, isComplete = false),
            strengthSet(weight = 300f, reps = 8, daysAgo = 5, setIndex = 9, isWarmup = true)
        )
        stubSets(valid + noise)

        val chest = resultFor("Chest")

        assertEquals(0f, chest.percentChange)
        assertEquals(MomentumDirection.FLAT, chest.direction)
    }

    @Test
    fun `reps only exercises qualify but duration exercises do not`() = runTest {
        val pushUps = listOf(1, 5, 15, 19).mapIndexed { index, day ->
            performanceSet(
                exerciseId = 2L,
                exerciseName = "Push-Ups",
                logType = LogType.REPS_ONLY,
                daysAgo = day,
                muscle = "Chest",
                reps = if (index < 2) 20 else 15
            )
        }
        val plank = listOf(1, 5, 15, 19).mapIndexed { index, day ->
            performanceSet(
                exerciseId = 3L,
                exerciseName = "Plank",
                logType = LogType.DURATION,
                daysAgo = day,
                muscle = "Abs",
                durationSeconds = if (index < 2) 90 else 60
            )
        }
        stubSets(pushUps + plank)

        val state = useCase.getStrengthMomentum(now)

        assertTrue(state.muscles.first { it.muscle == "Chest" }.percentChange!! > 0f)
        assertNull(state.muscles.first { it.muscle == "Abs" }.percentChange)
    }

    @Test
    fun `two matched sessions produce an early trend`() = runTest {
        stubSets(
            sessionSeries(weight = 105f, reps = 8, daysAgo = listOf(1, 5)) +
                sessionSeries(weight = 100f, reps = 8, daysAgo = listOf(15, 19))
        )

        val chest = resultFor("Chest")

        assertEquals(MomentumDataStatus.READY, chest.status)
        assertEquals(MomentumConfidence.LOW, chest.confidence)
    }

    @Test
    fun `fewer than two sessions per side builds baseline`() = runTest {
        stubSets(sessionSeries(weight = 100f, reps = 8, daysAgo = listOf(1, 15)))

        val chest = resultFor("Chest")

        assertEquals(MomentumDataStatus.BUILDING_BASELINE, chest.status)
        assertEquals(MomentumDirection.NO_BASELINE, chest.direction)
        assertNull(chest.percentChange)
    }

    @Test
    fun `qualifying history older than six weeks is stale`() = runTest {
        stubSets(sessionSeries(weight = 100f, reps = 8, daysAgo = listOf(43, 50, 57, 64, 71, 78)))

        val chest = resultFor("Chest")

        assertEquals(MomentumDataStatus.STALE, chest.status)
        assertNull(chest.percentChange)
    }

    @Test
    fun `muscle aggregation caps outliers and exposes mixed contributors`() = runTest {
        val bench = sessionSeries(
            exerciseId = 1L,
            exerciseName = "Bench Press",
            weight = 200f,
            reps = 8,
            daysAgo = listOf(1, 5)
        ) + sessionSeries(
            exerciseId = 1L,
            exerciseName = "Bench Press",
            weight = 100f,
            reps = 8,
            daysAgo = listOf(15, 19)
        )
        val fly = sessionSeries(
            exerciseId = 2L,
            exerciseName = "Fly",
            weight = 90f,
            reps = 8,
            daysAgo = listOf(1, 5)
        ) + sessionSeries(
            exerciseId = 2L,
            exerciseName = "Fly",
            weight = 100f,
            reps = 8,
            daysAgo = listOf(15, 19)
        )
        stubSets(bench + fly)

        val chest = resultFor("Chest")

        assertEquals(5f, chest.percentChange)
        assertTrue(chest.hasMixedContributors)
        assertEquals(1, chest.improvingContributors.size)
        assertEquals(1, chest.decliningContributors.size)
    }

    @Test
    fun `all leg exercises remain in one combined muscle result`() = runTest {
        stubSets(
            sessionSeries(
                exerciseName = "Squat",
                weight = 110f,
                reps = 8,
                daysAgo = listOf(1, 5),
                muscle = "Legs"
            ) + sessionSeries(
                exerciseName = "Squat",
                weight = 100f,
                reps = 8,
                daysAgo = listOf(15, 19),
                muscle = "Legs"
            )
        )

        val state = useCase.getStrengthMomentum(now)

        assertEquals(1, state.muscles.count { it.muscle == "Legs" })
        assertTrue(state.muscles.first { it.muscle == "Legs" }.percentChange!! > 0f)
    }

    @Test
    fun `stable noise is excluded from top progress and decline lists`() = runTest {
        stubSets(
            sessionSeries(weight = 101f, reps = 8, daysAgo = listOf(1, 5)) +
                sessionSeries(weight = 100f, reps = 8, daysAgo = listOf(15, 19))
        )

        val state = useCase.getStrengthMomentum(now)

        assertEquals(MomentumDirection.FLAT, state.muscles.first { it.muscle == "Chest" }.direction)
        assertTrue(state.topImproving.isEmpty())
        assertTrue(state.topDeclining.isEmpty())
    }

    private suspend fun resultFor(muscle: String): MuscleMomentum =
        useCase.getStrengthMomentum(now).muscles.first { it.muscle == muscle }

    private fun stubSets(sets: List<SetWithExercisePerformanceInfo>) {
        coEvery { setDao.getPerformanceSetsWithExerciseInRange(any(), any()) } returns sets
    }

    private fun sessionSeries(
        exerciseId: Long = 1L,
        exerciseName: String = "Bench Press",
        weight: Float,
        reps: Int,
        daysAgo: List<Int>,
        muscle: String = "Chest"
    ): List<SetWithExercisePerformanceInfo> = daysAgo.map { day ->
        strengthSet(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            weight = weight,
            reps = reps,
            daysAgo = day,
            muscle = muscle
        )
    }

    private fun strengthSet(
        exerciseId: Long = 1L,
        exerciseName: String = "Bench Press",
        weight: Float,
        reps: Int,
        daysAgo: Int,
        muscle: String = "Chest",
        setIndex: Int = 0,
        isWarmup: Boolean = false,
        isComplete: Boolean = true
    ): SetWithExercisePerformanceInfo = performanceSet(
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        logType = LogType.WEIGHT_REPS,
        daysAgo = daysAgo,
        muscle = muscle,
        weight = weight,
        reps = reps,
        setIndex = setIndex,
        isWarmup = isWarmup,
        isComplete = isComplete
    )

    private fun performanceSet(
        exerciseId: Long,
        exerciseName: String,
        logType: LogType,
        daysAgo: Int,
        muscle: String,
        weight: Float? = null,
        reps: Int? = null,
        durationSeconds: Int? = null,
        setIndex: Int = 0,
        isWarmup: Boolean = false,
        isComplete: Boolean = true
    ): SetWithExercisePerformanceInfo = SetWithExercisePerformanceInfo(
        set = Set(
            workoutId = daysAgo.toLong(),
            exerciseId = exerciseId,
            weight = weight,
            reps = reps,
            rpe = null,
            durationSeconds = durationSeconds,
            distanceMeters = null,
            isWarmup = isWarmup,
            isComplete = isComplete,
            timestamp = Date(now - TimeUnit.DAYS.toMillis(daysAgo.toLong()) + setIndex)
        ),
        exerciseName = exerciseName,
        targetMuscle = muscle,
        logType = logType
    )
}
