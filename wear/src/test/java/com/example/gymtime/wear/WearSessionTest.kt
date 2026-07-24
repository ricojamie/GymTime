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
            WearSession(logType = "REPS_ONLY", reps = "12"),
            WearSession(logType = "DURATION", duration = "00:30"),
            WearSession(logType = "WEIGHT_DISTANCE", weight = "25", distance = "0.5"),
            WearSession(logType = "DISTANCE_TIME", distance = "1.0", duration = "08:00"),
            WearSession(logType = "WEIGHT_TIME", weight = "45", duration = "01:00"),
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
