package com.example.gymtime.domain.recommendation

import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseAttemptRecommendationUseCaseTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val setDao: SetDao = mockk()
    private lateinit var useCase: ExerciseAttemptRecommendationUseCase

    @Before
    fun setup() {
        useCase = ExerciseAttemptRecommendationUseCase(setDao)
    }

    @Test
    fun `no rep target returns no recommendation`() = runTest {
        val exercise = exercise(repTarget = null)
        coEvery { setDao.getExerciseHistoryByWorkout(1L) } returns listOf(weightSet(100f, 8))

        assertNull(useCase.getRecommendation(exercise))
    }

    @Test
    fun `weighted exercise below target recommends one more rep at same weight`() = runTest {
        val exercise = exercise(repTarget = 10)
        coEvery { setDao.getExerciseHistoryByWorkout(1L) } returns listOf(weightSet(100f, 8))

        val result = useCase.getRecommendation(exercise)

        assertEquals(100f, result?.targetWeight)
        assertEquals(9, result?.targetReps)
        assertTrue(result?.canApply == true)
    }

    @Test
    fun `weighted exercise at target recommends next rounded weight`() = runTest {
        val exercise = exercise(repTarget = 10)
        coEvery { setDao.getExerciseHistoryByWorkout(1L) } returns listOf(weightSet(100f, 10))

        val result = useCase.getRecommendation(exercise)

        assertEquals(102.5f, result?.targetWeight)
        assertTrue((result?.targetReps ?: 0) in 1..10)
        assertTrue(result?.canApply == true)
    }

    @Test
    fun `reps only below target recommends one more rep`() = runTest {
        val exercise = exercise(logType = LogType.REPS_ONLY, repTarget = 12)
        coEvery { setDao.getExerciseHistoryByWorkout(1L) } returns listOf(repSet(10))

        val result = useCase.getRecommendation(exercise)

        assertEquals(11, result?.targetReps)
        assertTrue(result?.canApply == true)
    }

    @Test
    fun `reps only at target returns non-applicable target hit message`() = runTest {
        val exercise = exercise(logType = LogType.REPS_ONLY, repTarget = 12)
        coEvery { setDao.getExerciseHistoryByWorkout(1L) } returns listOf(repSet(12))

        val result = useCase.getRecommendation(exercise)

        assertFalse(result?.canApply ?: true)
        assertNull(result?.targetReps)
    }

    private fun exercise(
        logType: LogType = LogType.WEIGHT_REPS,
        repTarget: Int?
    ): Exercise {
        return Exercise(
            id = 1L,
            name = "Bench Press",
            targetMuscle = "Chest",
            logType = logType,
            isCustom = false,
            notes = null,
            defaultRestSeconds = 90,
            repTarget = repTarget
        )
    }

    private fun weightSet(weight: Float, reps: Int): Set {
        return Set(
            workoutId = 1L,
            exerciseId = 1L,
            weight = weight,
            reps = reps,
            rpe = null,
            durationSeconds = null,
            distanceMeters = null,
            isWarmup = false,
            isComplete = true,
            timestamp = Date()
        )
    }

    private fun repSet(reps: Int): Set {
        return weightSet(weight = 0f, reps = reps).copy(weight = null)
    }
}
