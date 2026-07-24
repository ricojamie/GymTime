package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.AnalyticsInsightSetRow
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.MuscleFreshness
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyAnalyticsInsightUseCaseTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val setDao: SetDao = mockk()
    private val workoutDao: WorkoutDao = mockk()
    private val muscleGroupDao: MuscleGroupDao = mockk()
    private val strengthMomentumUseCase: StrengthMomentumUseCase = mockk()
    private lateinit var useCase: WeeklyAnalyticsInsightUseCase

    private val zone = ZoneId.of("America/New_York")
    private val now = ZonedDateTime.of(2026, 7, 17, 12, 0, 0, 0, zone)

    @Before
    fun setup() {
        coEvery { setDao.getAnalyticsInsightSets(any(), any()) } returns emptyList()
        coEvery { workoutDao.getMuscleLastTrainedDates() } returns emptyList()
        coEvery { muscleGroupDao.getAllMuscleGroupNames() } returns listOf(
            "Chest", "Back", "Shoulders", "Biceps", "Triceps", "Legs", "Cardio", "Rear Delts", "Upper Body"
        )
        coEvery { strengthMomentumUseCase.getStrengthMomentum(any()) } returns StrengthMomentumState()
        useCase = WeeklyAnalyticsInsightUseCase(setDao, workoutDao, muscleGroupDao, strengthMomentumUseCase)
    }

    @Test
    fun `persistent push pull gap ranks first and requires all three complete weeks`() = runTest {
        val rows = buildList {
            for (weeksAgo in 1..3) {
                val timestamp = now
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))
                    .minusWeeks(weeksAgo.toLong())
                    .plusDays(2)
                repeat(4) { add(row(timestamp.plusMinutes(it.toLong()), "Chest", 100f, 10)) }
                repeat(4) { add(row(timestamp.plusHours(1).plusMinutes(it.toLong()), "Back", 50f, 10)) }
            }
        }
        coEvery { setDao.getAnalyticsInsightSets(any(), any()) } returns rows

        val result = useCase(now.toInstant().toEpochMilli(), zone)

        assertEquals(WeeklyAnalyticsInsightKind.PUSH_PULL_GAP, result.kind)
        assertTrue(result.fallbackText.contains("Pull volume trailed Push by about 50%"))
        coVerify(exactly = 0) { strengthMomentumUseCase.getStrengthMomentum(any()) }
    }

    @Test
    fun `standard confidence strength decline ranks ahead of freshness`() = runTest {
        coEvery { strengthMomentumUseCase.getStrengthMomentum(any()) } returns momentumState("Back", -7.4f)
        coEvery { workoutDao.getMuscleLastTrainedDates() } returns listOf(
            MuscleFreshness("Legs", now.minusDays(30).toInstant().toEpochMilli())
        )

        val result = useCase(now.toInstant().toEpochMilli(), zone)

        assertEquals(WeeklyAnalyticsInsightKind.STRENGTH_DECLINE, result.kind)
        assertTrue(result.fallbackText.contains("Back strength momentum is down 7%"))
        coVerify(exactly = 0) { workoutDao.getMuscleLastTrainedDates() }
    }

    @Test
    fun `stale muscle includes custom muscles but excludes cardio`() = runTest {
        coEvery { workoutDao.getMuscleLastTrainedDates() } returns listOf(
            MuscleFreshness("Cardio", now.minusDays(60).toInstant().toEpochMilli()),
            MuscleFreshness("Rear Delts", now.minusDays(20).toInstant().toEpochMilli())
        )

        val result = useCase(now.toInstant().toEpochMilli(), zone)

        assertEquals(WeeklyAnalyticsInsightKind.STALE_MUSCLE, result.kind)
        assertEquals("Rear Delts has not been trained in 20 days.", result.fallbackText)
    }

    @Test
    fun `week to date compares the same local wall clock span last week`() = runTest {
        val currentStart = now
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))
            .toLocalDate()
            .atStartOfDay(zone)
        val previousStart = currentStart.minusWeeks(1)
        val rows = buildList {
            repeat(5) { add(row(currentStart.plusDays(1).plusMinutes(it.toLong()), "Legs", 100f, 10)) }
            repeat(5) { add(row(previousStart.plusDays(1).plusMinutes(it.toLong()), "Legs", 50f, 10)) }
        }
        coEvery { setDao.getAnalyticsInsightSets(any(), any()) } returns rows

        val result = useCase(now.toInstant().toEpochMilli(), zone)

        assertEquals(WeeklyAnalyticsInsightKind.WEEKLY_VOLUME_CHANGE, result.kind)
        assertEquals("Your volume is up 100% versus the same point last week.", result.fallbackText)
    }

    @Test
    fun `standard confidence strength improvement is used after higher priority candidates`() = runTest {
        coEvery { strengthMomentumUseCase.getStrengthMomentum(any()) } returns momentumState("Chest", 8.1f)

        val result = useCase(now.toInstant().toEpochMilli(), zone)

        assertEquals(WeeklyAnalyticsInsightKind.STRENGTH_IMPROVEMENT, result.kind)
        assertTrue(result.fallbackText.contains("Chest strength momentum is up 8%"))
    }

    @Test
    fun `custom muscles are not silently classified as push or pull`() = runTest {
        val rows = buildList {
            for (weeksAgo in 1..3) {
                val timestamp = now
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))
                    .minusWeeks(weeksAgo.toLong())
                    .plusDays(1)
                repeat(4) { add(row(timestamp.plusMinutes(it.toLong()), "Upper Body", 100f, 10)) }
                repeat(3) { add(row(timestamp.plusHours(1).plusMinutes(it.toLong()), "Back", 50f, 10)) }
            }
        }
        coEvery { setDao.getAnalyticsInsightSets(any(), any()) } returns rows

        val result = useCase(now.toInstant().toEpochMilli(), zone)

        assertEquals(WeeklyAnalyticsInsightKind.MOST_TRAINED_MUSCLE, result.kind)
        assertTrue(result.fallbackText.startsWith("Upper Body is your most-trained muscle"))
    }

    @Test
    fun `mixed momentum contributors are not promoted as a strength claim`() = runTest {
        coEvery { strengthMomentumUseCase.getStrengthMomentum(any()) } returns momentumState(
            muscle = "Legs",
            percent = -10f,
            mixed = true
        )

        val result = useCase(now.toInstant().toEpochMilli(), zone)

        assertEquals(WeeklyAnalyticsInsightKind.BUILDING_HISTORY, result.kind)
    }

    private fun row(
        timestamp: ZonedDateTime,
        muscle: String,
        weight: Float?,
        reps: Int?
    ) = AnalyticsInsightSetRow(
        workoutId = timestamp.toEpochSecond(),
        timestampMs = timestamp.toInstant().toEpochMilli(),
        muscle = muscle,
        weight = weight,
        reps = reps
    )

    private fun momentumState(
        muscle: String,
        percent: Float,
        mixed: Boolean = false
    ): StrengthMomentumState {
        return StrengthMomentumState(
            muscles = listOf(
                MuscleMomentum(
                    muscle = muscle,
                    percentChange = percent,
                    direction = if (percent < 0) MomentumDirection.STRONG_DOWN else MomentumDirection.STRONG_UP,
                    contributingExercises = emptyList(),
                    hasMixedContributors = mixed,
                    status = MomentumDataStatus.READY,
                    confidence = MomentumConfidence.STANDARD
                )
            )
        )
    }
}
