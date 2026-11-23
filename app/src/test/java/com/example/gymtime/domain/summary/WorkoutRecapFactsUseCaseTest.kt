package com.example.gymtime.domain.summary

import com.example.gymtime.ai.GeneratedNarrativeRepository
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.SetWithExercisePerformanceInfo
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.domain.share.ShareWorkoutUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class WorkoutRecapFactsUseCaseTest {

    private val workoutDao: WorkoutDao = mockk()
    private val setDao: SetDao = mockk()
    private val generatedNarrativeRepository: GeneratedNarrativeRepository = mockk()
    private lateinit var shareWorkoutUseCase: ShareWorkoutUseCase
    private lateinit var useCase: WorkoutRecapFactsUseCase

    private val nowMs = 1_750_000_000_000L

    @Before
    fun setup() {
        coEvery { generatedNarrativeRepository.getLatest(any(), any()) } returns null
        coEvery { setDao.getPersonalBestsWithTimestampsBefore(any(), any()) } returns emptyList()
        shareWorkoutUseCase = ShareWorkoutUseCase(workoutDao, setDao, generatedNarrativeRepository)
        useCase = WorkoutRecapFactsUseCase(shareWorkoutUseCase, workoutDao, setDao)
    }

    @Test
    fun `warmup-only workout returns null`() = runTest {
        val workout = Workout(id = 1L, startTime = Date(nowMs), endTime = Date(nowMs + 1000L), name = null, note = null)
        coEvery { workoutDao.getWorkoutByIdSync(1L) } returns workout
        coEvery { setDao.getWorkoutSetsWithExercises(1L) } returns listOf(
            SetWithExerciseInfo(
                set = Set(id = 1L, workoutId = 1L, exerciseId = 1L, weight = 100f, reps = 10, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = true, isComplete = true, timestamp = Date(nowMs)),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            )
        )

        assertNull(useCase(1L))
    }

    @Test
    fun `comparable volume only counts completed positive same-muscle prior workouts`() = runTest {
        val workout = Workout(id = 10L, startTime = Date(nowMs), endTime = Date(nowMs + 1000L), name = null, note = null)
        coEvery { workoutDao.getWorkoutByIdSync(10L) } returns workout
        // Current Push workout: Chest 3000 + Triceps 1000
        coEvery { setDao.getWorkoutSetsWithExercises(10L) } returns listOf(
            SetWithExerciseInfo(
                set = Set(id = 1L, workoutId = 10L, exerciseId = 1L, weight = 100f, reps = 10, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true, timestamp = Date(nowMs)),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            ),
            SetWithExerciseInfo(
                set = Set(id = 2L, workoutId = 10L, exerciseId = 1L, weight = 200f, reps = 10, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true, timestamp = Date(nowMs + 100L)),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            ),
            SetWithExerciseInfo(
                set = Set(id = 3L, workoutId = 10L, exerciseId = 2L, weight = 100f, reps = 10, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true, timestamp = Date(nowMs + 200L)),
                exerciseName = "Triceps Pushdown",
                targetMuscle = "Triceps"
            )
        )

        // Prior workouts:
        // Workout 1 (7 days ago): Full body with Chest 2000 + Legs 5000 -> same-muscle Chest is 2000 (< 3000!)
        // Workout 2 (14 days ago): Bodyweight Push-Ups 0 lb Chest -> excluded (0f volume)
        val sevenDaysAgo = nowMs - TimeUnit.DAYS.toMillis(7)
        val fourteenDaysAgo = nowMs - TimeUnit.DAYS.toMillis(14)
        coEvery { setDao.getPerformanceSetsWithExerciseInRange(any(), any()) } returns listOf(
            SetWithExercisePerformanceInfo(
                set = Set(id = 10L, workoutId = 1L, exerciseId = 1L, weight = 100f, reps = 20, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true, timestamp = Date(sevenDaysAgo)),
                exerciseName = "Bench Press",
                targetMuscle = "Chest",
                logType = LogType.WEIGHT_REPS
            ),
            SetWithExercisePerformanceInfo(
                set = Set(id = 11L, workoutId = 1L, exerciseId = 3L, weight = 500f, reps = 10, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true, timestamp = Date(sevenDaysAgo + 100L)),
                exerciseName = "Squat",
                targetMuscle = "Legs",
                logType = LogType.WEIGHT_REPS
            ),
            SetWithExercisePerformanceInfo(
                set = Set(id = 12L, workoutId = 2L, exerciseId = 4L, weight = null, reps = 20, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true, timestamp = Date(fourteenDaysAgo)),
                exerciseName = "Push-Ups",
                targetMuscle = "Chest",
                logType = LogType.REPS_ONLY
            )
        )

        val facts = useCase(10L)!!

        assertEquals("Chest", facts.leadingMuscle)
        assertEquals(1, facts.comparableWorkoutCount)
        assertTrue(facts.isHighestComparableVolumeInSixWeeks)
    }

    @Test
    fun `first weighted session after bodyweight sessions does not claim six-week high`() = runTest {
        val workout = Workout(id = 10L, startTime = Date(nowMs), endTime = Date(nowMs + 1000L), name = null, note = null)
        coEvery { workoutDao.getWorkoutByIdSync(10L) } returns workout
        coEvery { setDao.getWorkoutSetsWithExercises(10L) } returns listOf(
            SetWithExerciseInfo(
                set = Set(id = 1L, workoutId = 10L, exerciseId = 1L, weight = 100f, reps = 10, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true, timestamp = Date(nowMs)),
                exerciseName = "Bench Press",
                targetMuscle = "Chest"
            )
        )
        val sevenDaysAgo = nowMs - TimeUnit.DAYS.toMillis(7)
        coEvery { setDao.getPerformanceSetsWithExerciseInRange(any(), any()) } returns listOf(
            SetWithExercisePerformanceInfo(
                set = Set(id = 12L, workoutId = 2L, exerciseId = 4L, weight = 0f, reps = 20, rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true, timestamp = Date(sevenDaysAgo)),
                exerciseName = "Push-Ups",
                targetMuscle = "Chest",
                logType = LogType.REPS_ONLY
            )
        )

        val facts = useCase(10L)!!

        assertEquals(0, facts.comparableWorkoutCount)
        assertFalse(facts.isHighestComparableVolumeInSixWeeks)
    }
}
