package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.RatedWorkoutSetInfo
import com.example.gymtime.data.db.dao.WorkoutDao
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutRatingUseCaseTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val workoutDao: WorkoutDao = mockk()
    private lateinit var useCase: WorkoutRatingUseCase

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.MAY, 14, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Before
    fun setup() {
        useCase = WorkoutRatingUseCase(workoutDao)
    }

    @Test
    fun `averages ratings for week and month`() = runTest {
        coEvery { workoutDao.getRatedWorkoutSetInfo(any(), any()) } returns listOf(
            row(workoutId = 1L, daysAgo = 1, rating = 5, muscle = "Chest"),
            row(workoutId = 2L, daysAgo = 2, rating = 3, muscle = "Back")
        )

        val result = useCase.getRatingStats(now)

        assertEquals(4f, result.weekAverageRating ?: 0f, 0.01f)
        assertEquals(4f, result.monthAverageRating ?: 0f, 0.01f)
        assertEquals(2, result.ratedWorkoutCount)
    }

    @Test
    fun `rated volume discounts volume by flame rating`() = runTest {
        coEvery { workoutDao.getRatedWorkoutSetInfo(any(), any()) } returns listOf(
            row(workoutId = 1L, daysAgo = 1, rating = 5, muscle = "Chest", weight = 100f, reps = 10),
            row(workoutId = 2L, daysAgo = 2, rating = 3, muscle = "Back", weight = 100f, reps = 10)
        )

        val result = useCase.getRatingStats(now)

        assertEquals(1600f, result.monthRatedVolume, 0.01f)
    }

    @Test
    fun `muscle rating attribution is weighted by working set share`() = runTest {
        coEvery { workoutDao.getRatedWorkoutSetInfo(any(), any()) } returns listOf(
            row(workoutId = 1L, setId = 1L, daysAgo = 1, rating = 5, muscle = "Chest"),
            row(workoutId = 1L, setId = 2L, daysAgo = 1, rating = 5, muscle = "Chest"),
            row(workoutId = 1L, setId = 3L, daysAgo = 1, rating = 5, muscle = "Back"),
            row(workoutId = 2L, setId = 4L, daysAgo = 2, rating = 1, muscle = "Back")
        )

        val result = useCase.getRatingStats(now)
        val chest = result.topMuscles.first { it.muscle == "Chest" }
        val back = result.lowMuscles.first { it.muscle == "Back" }

        assertEquals(5f, chest.averageRating, 0.01f)
        assertEquals(2.0f, back.averageRating, 0.01f)
    }

    @Test
    fun `warmups are excluded from muscle summaries and rated volume`() = runTest {
        coEvery { workoutDao.getRatedWorkoutSetInfo(any(), any()) } returns listOf(
            row(workoutId = 1L, setId = 1L, daysAgo = 1, rating = 5, muscle = "Chest", isWarmup = true, weight = 100f, reps = 10),
            row(workoutId = 1L, setId = 2L, daysAgo = 1, rating = 5, muscle = "Chest", isWarmup = false, weight = 100f, reps = 10)
        )

        val result = useCase.getRatingStats(now)

        assertEquals(1000f, result.monthRatedVolume, 0.01f)
        assertEquals(1, result.topMuscles.first().workingSets)
    }

    @Test
    fun `workouts containing only warmup activity are excluded from rating metrics`() = runTest {
        coEvery { workoutDao.getRatedWorkoutSetInfo(any(), any()) } returns listOf(
            row(
                workoutId = 1L,
                daysAgo = 1,
                rating = 5,
                muscle = "Warmups",
                isWarmup = true
            )
        )

        val result = useCase.getRatingStats(now)

        assertEquals(null, result.weekAverageRating)
        assertEquals(0, result.ratedWorkoutCount)
    }

    @Test
    fun `unrated workouts are absent from dao input and produce empty stats`() = runTest {
        coEvery { workoutDao.getRatedWorkoutSetInfo(any(), any()) } returns emptyList()

        val result = useCase.getRatingStats(now)

        assertTrue(result.weekAverageRating == null)
        assertEquals(0, result.ratedWorkoutCount)
    }

    @Test
    fun `sunday in US locale includes current Monday-Sunday week without jumping forward`() = runTest {
        val zone = ZoneId.of("America/New_York")
        val zonedUseCase = WorkoutRatingUseCase(workoutDao, zone)
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val sundayNoon = ZonedDateTime.of(2026, 5, 17, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
            val mondayMorning = ZonedDateTime.of(2026, 5, 11, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
            coEvery { workoutDao.getRatedWorkoutSetInfo(any(), any()) } returns listOf(
                ratedRowAt(workoutId = 1L, startMs = mondayMorning, rating = 5),
                ratedRowAt(workoutId = 2L, startMs = sundayNoon - 3_600_000L, rating = 3)
            )

            val result = zonedUseCase.getRatingStats(sundayNoon)

            assertEquals(4f, result.weekAverageRating ?: 0f, 0.01f)
            assertEquals(1600f, result.weekRatedVolume, 0.01f)
            assertEquals(1, result.weeklyTrend.size)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `dst spring forward week keeps trend buckets aligned to local Mondays`() = runTest {
        val zone = ZoneId.of("America/New_York")
        val zonedUseCase = WorkoutRatingUseCase(workoutDao, zone)
        // 2026-03-08 is US DST spring forward (23-hour day -> 167-hour week).
        val nowAfterDst = ZonedDateTime.of(2026, 3, 18, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val preDstMonday = ZonedDateTime.of(2026, 3, 2, 0, 30, 0, 0, zone).toInstant().toEpochMilli()
        val postDstMonday = ZonedDateTime.of(2026, 3, 9, 0, 30, 0, 0, zone).toInstant().toEpochMilli()

        coEvery { workoutDao.getRatedWorkoutSetInfo(any(), any()) } returns listOf(
            ratedRowAt(workoutId = 1L, startMs = preDstMonday, rating = 5),
            ratedRowAt(workoutId = 2L, startMs = postDstMonday, rating = 4)
        )

        val result = zonedUseCase.getRatingStats(nowAfterDst)

        assertEquals(2, result.weeklyTrend.size)
        assertEquals(
            Date.from(LocalDate.of(2026, 3, 2).atStartOfDay(zone).toInstant()),
            result.weeklyTrend[0].date
        )
        assertEquals(
            Date.from(LocalDate.of(2026, 3, 9).atStartOfDay(zone).toInstant()),
            result.weeklyTrend[1].date
        )
    }

    private fun ratedRowAt(
        workoutId: Long,
        startMs: Long,
        rating: Int,
        muscle: String = "Chest",
        weight: Float = 100f,
        reps: Int = 10
    ): RatedWorkoutSetInfo {
        return RatedWorkoutSetInfo(
            workoutId = workoutId,
            startTime = Date(startMs),
            endTime = Date(startMs + 60 * 60 * 1000L),
            rating = rating,
            setId = workoutId,
            exerciseId = workoutId,
            targetMuscle = muscle,
            weight = weight,
            reps = reps,
            isWarmup = false
        )
    }

    private fun row(
        workoutId: Long,
        setId: Long = workoutId,
        daysAgo: Int,
        rating: Int,
        muscle: String,
        isWarmup: Boolean = false,
        weight: Float = 100f,
        reps: Int = 10
    ): RatedWorkoutSetInfo {
        return RatedWorkoutSetInfo(
            workoutId = workoutId,
            startTime = Date(now - daysAgo.toLong() * DAY_MS),
            endTime = Date(now - daysAgo.toLong() * DAY_MS + 60 * 60 * 1000),
            rating = rating,
            setId = setId,
            exerciseId = setId,
            targetMuscle = muscle,
            weight = weight,
            reps = reps,
            isWarmup = isWarmup
        )
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
