package com.example.gymtime.wear

import com.google.android.gms.wearable.DataMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSessionTest {

    @Test
    fun `canLog accepts every supported log type with its required fields`() {
        val validSessions = listOf(
            WearSession(logType = "WEIGHT_REPS", weight = "135", reps = "5"),
            WearSession(logType = "WEIGHT_REPS", weight = "0", reps = "8"),
            WearSession(logType = "REPS_ONLY", reps = "12"),
            WearSession(logType = "DURATION", duration = "00:30"),
            WearSession(logType = "WEIGHT_DISTANCE", weight = "25", distance = "0.5"),
            WearSession(logType = "WEIGHT_DISTANCE", weight = "0", distance = "1.0"),
            WearSession(logType = "DISTANCE_TIME", distance = "1.0", duration = "08:00"),
            WearSession(logType = "WEIGHT_TIME", weight = "45", duration = "01:00"),
            WearSession(logType = "WEIGHT_TIME", weight = "0", duration = "01:00"),
            WearSession(logType = "CALORIES_TIME", calories = "100", duration = "12:00")
        )

        validSessions.forEach { session ->
            assertTrue("${session.logType} should be loggable", session.canLog)
        }
    }

    @Test
    fun `canLog rejects supported log types when a required field is missing`() {
        val invalidSessions = listOf(
            WearSession(logType = "WEIGHT_REPS", weight = "135"),
            WearSession(logType = "WEIGHT_REPS", reps = "5"),
            WearSession(logType = "REPS_ONLY"),
            WearSession(logType = "DURATION"),
            WearSession(logType = "WEIGHT_DISTANCE", weight = "25"),
            WearSession(logType = "WEIGHT_DISTANCE", distance = "0.5"),
            WearSession(logType = "DISTANCE_TIME", distance = "1.0"),
            WearSession(logType = "DISTANCE_TIME", duration = "08:00"),
            WearSession(logType = "WEIGHT_TIME", weight = "45"),
            WearSession(logType = "WEIGHT_TIME", duration = "01:00"),
            WearSession(logType = "CALORIES_TIME", calories = "100"),
            WearSession(logType = "CALORIES_TIME", duration = "12:00")
        )

        invalidSessions.forEach { session ->
            assertFalse("${session.logType} should require all of its fields", session.canLog)
        }
    }

    @Test
    fun `canLog rejects zero non-positive and malformed values aligned with phone validation`() {
        val invalidSessions = listOf(
            WearSession(logType = "WEIGHT_REPS", weight = "135", reps = "0"),
            WearSession(logType = "WEIGHT_REPS", weight = "135", reps = "5.5"),
            WearSession(logType = "WEIGHT_REPS", weight = "-10", reps = "5"),
            WearSession(logType = "WEIGHT_REPS", weight = "NaN", reps = "5"),
            WearSession(logType = "WEIGHT_REPS", weight = "Infinity", reps = "5"),
            WearSession(logType = "REPS_ONLY", reps = "0"),
            WearSession(logType = "DURATION", duration = "0"),
            WearSession(logType = "DURATION", duration = "00:00"),
            WearSession(logType = "DURATION", duration = "0:00:00"),
            WearSession(logType = "DURATION", duration = "::"),
            WearSession(logType = "DURATION", duration = "00:65"),
            WearSession(logType = "DURATION", duration = "1::30"),
            WearSession(logType = "DURATION", duration = "999999999999999"),
            WearSession(logType = "WEIGHT_DISTANCE", weight = "25", distance = "0"),
            WearSession(logType = "WEIGHT_DISTANCE", weight = "25", distance = "0.0"),
            WearSession(logType = "DISTANCE_TIME", distance = "0", duration = "08:00"),
            WearSession(logType = "DISTANCE_TIME", distance = "1.0", duration = "00:00"),
            WearSession(logType = "WEIGHT_TIME", weight = "45", duration = "00:00"),
            WearSession(logType = "CALORIES_TIME", calories = "0", duration = "12:00"),
            WearSession(logType = "CALORIES_TIME", calories = ".", duration = "12:00"),
            WearSession(logType = "CALORIES_TIME", calories = "100", duration = "00:00")
        )

        invalidSessions.forEach { session ->
            assertFalse("Expected invalid session to be rejected: $session", session.canLog)
        }
    }

    @Test
    fun `canLog validates optional rpe range from 0 to 10`() {
        assertTrue(WearSession(logType = "REPS_ONLY", reps = "10", rpe = "").canLog)
        assertTrue(WearSession(logType = "REPS_ONLY", reps = "10", rpe = "0").canLog)
        assertTrue(WearSession(logType = "REPS_ONLY", reps = "10", rpe = "8.5").canLog)
        assertTrue(WearSession(logType = "REPS_ONLY", reps = "10", rpe = "10").canLog)

        assertFalse(WearSession(logType = "REPS_ONLY", reps = "10", rpe = "-0.5").canLog)
        assertFalse(WearSession(logType = "REPS_ONLY", reps = "10", rpe = "10.5").canLog)
        assertFalse(WearSession(logType = "REPS_ONLY", reps = "10", rpe = "11").canLog)
        assertFalse(WearSession(logType = "REPS_ONLY", reps = "10", rpe = "bad").canLog)
    }

    @Test
    fun `canLog rejects an unknown log type`() {
        assertFalse(
            WearSession(
                logType = "UNKNOWN",
                weight = "100",
                reps = "10",
                duration = "01:00",
                distance = "1.0",
                calories = "50"
            ).canLog
        )
    }

    @Test
    fun `command data map round trip preserves every editable field`() {
        val session = WearSession(
            workoutId = 42,
            exerciseId = 7,
            weight = "225",
            reps = "3",
            rpe = "9.5",
            duration = "01:15",
            distance = "0.25",
            calories = "40",
            isWarmup = true
        )

        val restored = WearSession.fromDataMap(session.toDataMap())

        assertEquals(session.workoutId, restored.workoutId)
        assertEquals(session.exerciseId, restored.exerciseId)
        assertEquals(session.weight, restored.weight)
        assertEquals(session.reps, restored.reps)
        assertEquals(session.rpe, restored.rpe)
        assertEquals(session.duration, restored.duration)
        assertEquals(session.distance, restored.distance)
        assertEquals(session.calories, restored.calories)
        assertEquals(session.isWarmup, restored.isWarmup)
    }

    @Test
    fun `missing data map keys produce an inactive safe default session`() {
        assertEquals(WearSession(), WearSession.fromDataMap(DataMap()))
    }
}
