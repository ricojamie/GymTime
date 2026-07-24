package com.example.gymtime.smartlog

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeterministicSmartLogPromptExtractorTest {
    private val parser = DeterministicSmartLogPromptExtractor()

    @Test
    fun `parses reps by explicit pound weight`() = runTest {
        val result = parser.extract("Incline dumbbell press, 10 by 65 pounds")

        assertEquals("Incline dumbbell press", result?.exerciseQuery)
        assertEquals(65f, result?.sets?.single()?.weight)
        assertEquals(10, result?.sets?.single()?.reps)
        assertEquals(WeightUnit.POUNDS, result?.sets?.single()?.weightUnit)
    }

    @Test
    fun `expands dumbbell shorthand into queued sets`() = runTest {
        val result = parser.extract("Incline dumbbell press, 32s, 10, 9, 8")

        assertNotNull(result)
        assertEquals(listOf(10, 9, 8), result?.sets?.map { it.reps })
        assertEquals(listOf(32f, 32f, 32f), result?.sets?.map { it.weight })
    }

    @Test
    fun `expands set count notation`() = runTest {
        val result = parser.extract("Bench press 3x10 at 65")

        assertEquals(3, result?.sets?.size)
        assertEquals(10, result?.sets?.first()?.reps)
        assertEquals(65f, result?.sets?.first()?.weight)
    }

    @Test
    fun `parses distance duration and calories phrases`() = runTest {
        val result = parser.extract("Rowing, 2000 meters, 8 minutes, 140 calories")

        assertEquals(2000f, result?.sets?.single()?.distanceValue)
        assertEquals(480, result?.sets?.single()?.durationSeconds)
        assertEquals(140f, result?.sets?.single()?.calories)
    }
}
