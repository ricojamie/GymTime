package com.example.gymtime.domain.report

import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.MuscleDistribution
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyReportUseCaseTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val workoutDao: WorkoutDao = mockk()
    private val setDao: SetDao = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private val zoneId = ZoneId.of("America/New_York")

    private lateinit var useCase: MonthlyReportUseCase

    @Before
    fun setup() {
        useCase = MonthlyReportUseCase(workoutDao, setDao, userPreferencesRepository, zoneId)
    }

    @Test
    fun `monthly date aggregation uses completed local set days within the target month`() = runTest {
        val januaryStartWorkout = workout(
            start = localDateTime(2026, 1, 31, 23, 55),
            end = localDateTime(2026, 2, 1, 0, 20),
            name = "Late January"
        )
        val februaryWorkout = workout(
            id = 2L,
            start = localDateTime(2026, 2, 28, 23, 50),
            end = localDateTime(2026, 3, 1, 0, 10),
            name = "Late February"
        )
        val warmupOnlyWorkout = workout(
            id = 3L,
            start = localDateTime(2026, 2, 20, 9, 0),
            end = localDateTime(2026, 2, 20, 9, 20),
            name = "Mobility"
        )

        coEvery { workoutDao.getAllWorkoutsSync() } returns listOf(
            januaryStartWorkout,
            februaryWorkout,
            warmupOnlyWorkout
        )
        coEvery { workoutDao.getMuscleSetCountsInRange(any(), any()) } returns listOf(
            MuscleDistribution(muscle = "Legs", setVolume = 2)
        )
        coEvery { workoutDao.getWorkoutDatesWithWorkingSets() } returns listOf("2026-02-28", "2026-02-01")
        coEvery { userPreferencesRepository.restDaysPerWeek } returns flowOf(2)
        coEvery { setDao.getPersonalBestBefore(any(), any()) } returns null
        coEvery { setDao.getSetsWithExerciseInRange(match { it < localDateTime(2026, 2, 1, 0, 0).time }, any(), any(), any()) } returns emptyList()
        coEvery { setDao.getSetsWithExerciseInRange(match { it >= localDateTime(2026, 2, 1, 0, 0).time }, any(), any(), any()) } returns listOf(
            workingSet(
                workoutId = januaryStartWorkout.id,
                timestamp = localDateTime(2026, 2, 1, 0, 5)
            ),
            warmupSet(
                workoutId = warmupOnlyWorkout.id,
                timestamp = localDateTime(2026, 2, 20, 9, 0)
            ),
            incompleteWorkingSet(
                workoutId = januaryStartWorkout.id,
                timestamp = localDateTime(2026, 2, 15, 10, 0)
            ),
            workingSet(
                workoutId = februaryWorkout.id,
                timestamp = localDateTime(2026, 2, 28, 23, 55)
            )
        )

        val report = useCase(reference = localDateTime(2026, 3, 15, 12, 0))

        assertEquals("February 2026", report.monthLabel)
        assertEquals(1, report.workoutCount)
        assertEquals(2, report.trainingDays)
        assertEquals(2, report.activeWeeks)
        assertEquals(Date.from(ZonedDateTime.of(2026, 2, 1, 0, 0, 0, 0, zoneId).toInstant()), report.periodStart)
        assertEquals(Date.from(ZonedDateTime.of(2026, 2, 28, 23, 59, 59, 999_000_000, zoneId).toInstant()), report.periodEnd)
    }

    private fun workout(id: Long = 1L, start: Date, end: Date, name: String): Workout {
        return Workout(
            id = id,
            startTime = start,
            endTime = end,
            name = name,
            note = null,
            rating = null,
            ratingNote = null,
            routineDayId = null
        )
    }

    private fun workingSet(workoutId: Long, timestamp: Date): SetWithExerciseInfo {
        return setWithExercise(
            workoutId = workoutId,
            timestamp = timestamp,
            isWarmup = false,
            isComplete = true
        )
    }

    private fun warmupSet(workoutId: Long, timestamp: Date): SetWithExerciseInfo {
        return setWithExercise(
            workoutId = workoutId,
            timestamp = timestamp,
            isWarmup = true,
            isComplete = true
        )
    }

    private fun incompleteWorkingSet(workoutId: Long, timestamp: Date): SetWithExerciseInfo {
        return setWithExercise(
            workoutId = workoutId,
            timestamp = timestamp,
            isWarmup = false,
            isComplete = false
        )
    }

    private fun setWithExercise(
        workoutId: Long,
        timestamp: Date,
        isWarmup: Boolean,
        isComplete: Boolean
    ): SetWithExerciseInfo {
        return SetWithExerciseInfo(
            set = Set(
                workoutId = workoutId,
                exerciseId = 1L,
                weight = 225f,
                reps = 5,
                rpe = null,
                durationSeconds = null,
                distanceMeters = null,
                isWarmup = isWarmup,
                isComplete = isComplete,
                timestamp = timestamp,
                note = null,
                supersetGroupId = null,
                supersetOrderIndex = 0
            ),
            exerciseName = "Squat",
            targetMuscle = "Legs"
        )
    }

    private fun localDateTime(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Date {
        return Date.from(
            ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId).toInstant()
        )
    }
}
