package com.example.gymtime.domain.progression

import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class StrengthPerformanceCalculatorTest {
    @Test
    fun `estimated strength includes weight and reps through fifteen reps`() {
        assertEquals(126.66667f, StrengthPerformanceCalculator.estimatedOneRepMax(set(reps = 8))!!, 0.0001f)
        assertEquals(133.33334f, StrengthPerformanceCalculator.estimatedOneRepMax(set(reps = 10))!!, 0.0001f)
        assertEquals(150f, StrengthPerformanceCalculator.estimatedOneRepMax(set(reps = 15))!!, 0.0001f)
    }

    @Test
    fun `estimates reject high reps zero load and nonfinite numbers rather than returning zero`() {
        listOf(
            set(reps = 16), set(reps = 0), set(weight = 0f), set(weight = -100f),
            set(weight = Float.NaN), set(weight = Float.POSITIVE_INFINITY), set(weight = Float.MAX_VALUE, reps = 15)
        ).forEach { assertNull(StrengthPerformanceCalculator.estimatedOneRepMax(it)) }
    }

    @Test
    fun `unfinished and warmup sets cannot produce strength scores`() {
        listOf(set().copy(isComplete = false), set().copy(isWarmup = true)).forEach {
            assertNull(StrengthPerformanceCalculator.strengthValue(it, LogType.WEIGHT_REPS))
            assertNull(StrengthPerformanceCalculator.strengthValue(it, LogType.REPS_ONLY))
        }
    }

    @Test
    fun `reps only has no fifteen rep ceiling and other types are not estimated strength`() {
        assertEquals(25f, StrengthPerformanceCalculator.strengthValue(set(weight = 0f, reps = 25), LogType.REPS_ONLY)!!, 0f)
        LogType.entries.filter { it != LogType.WEIGHT_REPS && it != LogType.REPS_ONLY }.forEach {
            assertNull(StrengthPerformanceCalculator.strengthValue(set(), it))
        }
    }

    private fun set(weight: Float = 100f, reps: Int = 8) = Set(
        workoutId = 1, exerciseId = 1, weight = weight, reps = reps, rpe = null,
        durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true,
        timestamp = Date(0)
    )
}
