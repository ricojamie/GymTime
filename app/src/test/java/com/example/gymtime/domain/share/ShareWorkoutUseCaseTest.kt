package com.example.gymtime.domain.share

import com.example.gymtime.data.db.dao.PBWithTimestamp
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class ShareWorkoutUseCaseTest {

    private val workoutDao: WorkoutDao = mockk()
    private val setDao: SetDao = mockk()
    private lateinit var useCase: ShareWorkoutUseCase

    private val workoutStart = 1_000_000L
    private val workout = Workout(
        id = 1L,
        startTime = Date(workoutStart),
        endTime = Date(workoutStart + 3_600_000L),
        name = null,
        note = null
    )

    @Before
    fun setup() {
        useCase = ShareWorkoutUseCase(workoutDao, setDao)
        every { workoutDao.getWorkoutById(1L) } returns flowOf(workout)
    }

    @Test
    fun `marks rep-count PRs instead of only the heaviest set`() = runTest {
        coEvery { setDao.getWorkoutSetsWithExercises(1L) } returns listOf(
            setInfo(id = 1L, weight = 255f, reps = 5, timestamp = workoutStart + 1_000L),
            setInfo(id = 2L, weight = 225f, reps = 8, timestamp = workoutStart + 2_000L)
        )
        coEvery {
            setDao.getPersonalBestsWithTimestampsBefore(1L, workoutStart)
        } returns listOf(
            PBWithTimestamp(reps = 5, maxWeight = 260f, firstAchievedAt = 1L),
            PBWithTimestamp(reps = 8, maxWeight = 200f, firstAchievedAt = 1L)
        )

        val result = useCase.buildShareableWorkout(1L)!!
        val sets = result.exercises.single().sets

        assertFalse("Heaviest set is below its rep PR", sets[0].isPersonalRecord)
        assertTrue("8-rep improvement should be marked", sets[1].isPersonalRecord)
    }

    @Test
    fun `warmups and ties are not marked as PRs`() = runTest {
        coEvery { setDao.getWorkoutSetsWithExercises(1L) } returns listOf(
            setInfo(id = 1L, weight = 225f, reps = 8, isWarmup = true, timestamp = workoutStart + 1_000L),
            setInfo(id = 2L, weight = 200f, reps = 8, timestamp = workoutStart + 2_000L)
        )
        coEvery {
            setDao.getPersonalBestsWithTimestampsBefore(1L, workoutStart)
        } returns listOf(PBWithTimestamp(reps = 8, maxWeight = 200f, firstAchievedAt = 1L))

        val result = useCase.buildShareableWorkout(1L)!!
        val sets = result.exercises.single().sets

        assertFalse(sets[0].isPersonalRecord)
        assertFalse(sets[1].isPersonalRecord)
    }

    @Test
    fun `same-workout dominated rep PRs are not highlighted`() = runTest {
        coEvery { setDao.getWorkoutSetsWithExercises(1L) } returns listOf(
            setInfo(id = 1L, weight = 45f, reps = 10, timestamp = workoutStart + 1_000L),
            setInfo(id = 2L, weight = 45f, reps = 12, timestamp = workoutStart + 2_000L)
        )
        coEvery {
            setDao.getPersonalBestsWithTimestampsBefore(1L, workoutStart)
        } returns listOf(
            PBWithTimestamp(reps = 10, maxWeight = 40f, firstAchievedAt = 1L),
            PBWithTimestamp(reps = 12, maxWeight = 40f, firstAchievedAt = 1L)
        )

        val result = useCase.buildShareableWorkout(1L)!!
        val sets = result.exercises.single().sets

        assertFalse("45x10 is dominated by 45x12", sets[0].isPersonalRecord)
        assertTrue("45x12 is the true PR", sets[1].isPersonalRecord)
    }

    private fun setInfo(
        id: Long,
        weight: Float,
        reps: Int,
        isWarmup: Boolean = false,
        timestamp: Long
    ): SetWithExerciseInfo {
        return SetWithExerciseInfo(
            set = Set(
                id = id,
                workoutId = 1L,
                exerciseId = 1L,
                weight = weight,
                reps = reps,
                rpe = null,
                durationSeconds = null,
                distanceMeters = null,
                isWarmup = isWarmup,
                isComplete = true,
                timestamp = Date(timestamp)
            ),
            exerciseName = "Bench Press",
            targetMuscle = "Chest"
        )
    }
}
