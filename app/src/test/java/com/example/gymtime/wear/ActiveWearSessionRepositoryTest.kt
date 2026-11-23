package com.example.gymtime.wear

import android.content.Context
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.LogType
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWearSessionRepositoryTest {

    @Test
    fun `isRoutineTimerTick returns true only for exact 1 second countdown ticks`() {
        val runningAt90 = sampleSnapshot(timerRemainingSeconds = 90, timerRunning = true)
        val runningAt89 = runningAt90.copy(timerRemainingSeconds = 89)

        assertTrue(ActiveWearSessionRepository.isRoutineTimerTick(runningAt90, runningAt89))
    }

    @Test
    fun `isRoutineTimerTick returns false for watch minus 30s and phone minus 5s adjustments`() {
        val runningAt90 = sampleSnapshot(timerRemainingSeconds = 90, timerRunning = true)

        assertFalse(
            ActiveWearSessionRepository.isRoutineTimerTick(
                runningAt90,
                runningAt90.copy(timerRemainingSeconds = 60)
            )
        )
        assertFalse(
            ActiveWearSessionRepository.isRoutineTimerTick(
                runningAt90,
                runningAt90.copy(timerRemainingSeconds = 85)
            )
        )
        assertFalse(
            ActiveWearSessionRepository.isRoutineTimerTick(
                runningAt90,
                runningAt90.copy(timerRemainingSeconds = 95)
            )
        )
        assertFalse(
            ActiveWearSessionRepository.isRoutineTimerTick(
                runningAt90,
                runningAt90.copy(timerRemainingSeconds = 120)
            )
        )
        assertFalse(
            ActiveWearSessionRepository.isRoutineTimerTick(
                runningAt90,
                runningAt90.copy(timerRemainingSeconds = 86)
            )
        )
    }

    @Test
    fun `isRoutineTimerTick returns false when timer state stops or any other field changes`() {
        val runningAt1 = sampleSnapshot(timerRemainingSeconds = 1, timerRunning = true)
        val completedAt0 = runningAt1.copy(timerRemainingSeconds = 0, timerRunning = false)
        assertFalse(ActiveWearSessionRepository.isRoutineTimerTick(runningAt1, completedAt0))

        val runningAt90 = sampleSnapshot(timerRemainingSeconds = 90, timerRunning = true)
        assertFalse(
            ActiveWearSessionRepository.isRoutineTimerTick(
                runningAt90,
                runningAt90.copy(timerRemainingSeconds = 89, weight = "145")
            )
        )
        assertFalse(
            ActiveWearSessionRepository.isRoutineTimerTick(
                runningAt90,
                runningAt90.copy(timerRemainingSeconds = 90)
            )
        )
    }

    @Test
    fun `publish throttles 1s ticks but publishes minus 30s and minus 5s immediately`() {
        val published = mutableListOf<WearSessionSnapshot>()
        val repo = ActiveWearSessionRepository(mockk<Context>(relaxed = true)) { snapshot, _, _, _ ->
            published += snapshot
        }
        val publisherId = repo.beginPublishing()

        val initial = sampleSnapshot(timerRemainingSeconds = 90, timerRunning = true)
        repo.publish(publisherId, initial)
        repo.publish(publisherId, initial.copy(timerRemainingSeconds = 89))
        assertEquals(listOf(90), published.map { it.timerRemainingSeconds })

        repo.publish(publisherId, initial.copy(timerRemainingSeconds = 59))
        assertEquals(listOf(90, 59), published.map { it.timerRemainingSeconds })

        repo.publish(publisherId, initial.copy(timerRemainingSeconds = 54))
        assertEquals(listOf(90, 59, 54), published.map { it.timerRemainingSeconds })
    }

    @Test
    fun `confirmSetSaved produces strictly increasing ids above 1`() {
        val saveIds = mutableListOf<Long>()
        val repo = ActiveWearSessionRepository(mockk<Context>(relaxed = true)) { _, _, saveId, _ ->
            saveIds += saveId
        }
        val publisherId = repo.beginPublishing()
        repo.publish(publisherId, sampleSnapshot(timerRemainingSeconds = 0, timerRunning = false))
        repo.confirmSetSaved()
        repo.confirmSetSaved()

        assertEquals(3, saveIds.size)
        assertEquals(0L, saveIds[0])
        assertTrue(saveIds[1] > 1L)
        assertTrue(saveIds[2] > saveIds[1])
    }

    @Test
    fun `nextMonotonicId advances monotonically across process restart clocks and tight bursts`() {
        val counter = AtomicLong(0)
        val firstAfterRestart = ActiveWearSessionRepository.nextMonotonicId(counter, nowMillis = 1_700_000_000_000L)
        val sameMsBurst = ActiveWearSessionRepository.nextMonotonicId(counter, nowMillis = 1_700_000_000_000L)
        val laterMs = ActiveWearSessionRepository.nextMonotonicId(counter, nowMillis = 1_700_000_000_500L)

        assertEquals(1_700_000_000_000L, firstAfterRestart)
        assertEquals(1_700_000_000_001L, sameMsBurst)
        assertEquals(1_700_000_000_500L, laterMs)
    }

    @Test
    fun `hasActivePublisher requires both an active publisher id and logRequests subscriber`() = runTest {
        val repo = ActiveWearSessionRepository(mockk<Context>(relaxed = true)) { _, _, _, _ -> }

        assertFalse(repo.hasActivePublisher())

        val publisherId = repo.beginPublishing()
        assertFalse(repo.hasActivePublisher())

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.logRequests.collect { }
        }
        assertTrue(repo.hasActivePublisher())

        repo.stopPublishing(publisherId)
        assertFalse(repo.hasActivePublisher())

        val secondPublisherId = repo.beginPublishing()
        assertTrue(repo.hasActivePublisher())

        collectJob.cancel()
        assertFalse(repo.hasActivePublisher())

        repo.stopPublishing(secondPublisherId)
    }

    private fun sampleSnapshot(
        timerRemainingSeconds: Int,
        timerRunning: Boolean
    ) = WearSessionSnapshot(
        active = true,
        workoutId = 1L,
        exerciseId = 2L,
        exerciseName = "Bench Press",
        targetMuscle = "Chest",
        logType = LogType.WEIGHT_REPS,
        distanceUnit = DistanceUnit.MILES,
        setNumber = 1,
        weight = "135",
        reps = "8",
        rpe = "",
        duration = "",
        distance = "",
        calories = "",
        isWarmup = false,
        restSeconds = 90,
        timerRemainingSeconds = timerRemainingSeconds,
        timerRunning = timerRunning
    )
}
