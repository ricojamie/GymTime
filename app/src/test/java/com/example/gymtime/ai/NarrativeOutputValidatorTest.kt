package com.example.gymtime.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrativeOutputValidatorTest {
    private val validator = NarrativeOutputValidator()

    @Test
    fun `accepts grounded names and numbers`() {
        val result = validator.validate(
            "Chest volume rose 30% across 4 workouts.",
            NarrativeValidationRules(
                maxWords = 12,
                maxSentences = 1,
                allowedNumbers = setOf("30%", "4"),
                allowedNames = setOf("Chest"),
                knownNames = setOf("Chest", "Back")
            )
        )

        assertTrue(result is NarrativeValidationResult.Valid)
    }

    @Test
    fun `keeps percentages distinct from plain numbers`() {
        val result = validator.validate(
            "Volume rose 30%.",
            NarrativeValidationRules(
                maxWords = 5,
                maxSentences = 1,
                allowedNumbers = setOf("30")
            )
        )

        assertEquals(
            NarrativeValidationResult.Invalid("Narrative contains an unsupported number"),
            result
        )
    }

    @Test
    fun `rejects a known but unsupported exercise or muscle name`() {
        val result = validator.validate(
            "Back led the month.",
            NarrativeValidationRules(
                maxWords = 8,
                maxSentences = 1,
                allowedNames = setOf("Chest"),
                knownNames = setOf("Chest", "Back")
            )
        )

        assertEquals(
            NarrativeValidationResult.Invalid("Narrative contains an unsupported name"),
            result
        )
    }

    @Test
    fun `fingerprint is stable and changes with facts`() {
        val first = NarrativeFingerprint.sha256("workouts=4|volume=1000")
        val second = NarrativeFingerprint.sha256("workouts=4|volume=1000")
        val changed = NarrativeFingerprint.sha256("workouts=5|volume=1000")

        assertEquals(first, second)
        assertTrue(first != changed)
        assertEquals(64, first.length)
    }
}
