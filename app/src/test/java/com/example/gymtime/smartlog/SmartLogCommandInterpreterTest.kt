package com.example.gymtime.smartlog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLogCommandInterpreterTest {

    @Test
    fun `rethrows CancellationException instead of falling back`() = runTest {
        val cancellingExtractor = object : SmartLogPromptExtractor {
            override val priority: Int = 100
            override suspend fun extract(commandText: String): ParsedLoggingCommand? {
                throw CancellationException("cancelled")
            }
        }
        val interpreter = SmartLogCommandInterpreter(
            setOf(cancellingExtractor, DeterministicSmartLogPromptExtractor())
        )

        var caught: CancellationException? = null
        try {
            interpreter.extract("Bench press 135 x 10")
        } catch (e: CancellationException) {
            caught = e
        }
        assertNotNull(caught)
    }

    @Test
    fun `falls back to deterministic extractor on non cancellation error`() = runTest {
        val failingExtractor = object : SmartLogPromptExtractor {
            override val priority: Int = 100
            override suspend fun extract(commandText: String): ParsedLoggingCommand? {
                throw IllegalStateException("ai error")
            }
        }
        val interpreter = SmartLogCommandInterpreter(
            setOf(failingExtractor, DeterministicSmartLogPromptExtractor())
        )

        val parsed = interpreter.extract("Bench press 100kg x 5")
        assertNotNull(parsed)
        assertEquals("Bench press", parsed?.exerciseQuery)
        assertEquals(100f, parsed?.sets?.single()?.weight)
        assertEquals(WeightUnit.KILOGRAMS, parsed?.sets?.single()?.weightUnit)
        assertEquals(5, parsed?.sets?.single()?.reps)
    }

    @Test
    fun `skips higher priority result with empty sets`() = runTest {
        val emptySetsExtractor = object : SmartLogPromptExtractor {
            override val priority: Int = 100
            override suspend fun extract(commandText: String): ParsedLoggingCommand =
                ParsedLoggingCommand(exerciseQuery = "Bench press", sets = emptyList())
        }
        val interpreter = SmartLogCommandInterpreter(
            setOf(emptySetsExtractor, DeterministicSmartLogPromptExtractor())
        )

        val parsed = interpreter.extract("Bench press 135 x 8")
        assertTrue(parsed?.sets?.isNotEmpty() == true)
        assertEquals(8, parsed?.sets?.single()?.reps)
    }
}
