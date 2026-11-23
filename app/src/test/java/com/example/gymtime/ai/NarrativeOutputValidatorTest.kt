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
    fun `does not reject substrings inside ordinary words`() {
        val result = validator.validate(
            "Strong comeback for Chest with a great feedback score.",
            NarrativeValidationRules(
                maxWords = 12,
                maxSentences = 1,
                allowedNames = setOf("Chest"),
                knownNames = setOf("Chest", "Back", "Core")
            )
        )

        assertTrue(result is NarrativeValidationResult.Valid)
    }

    @Test
    fun `masks longer allowed names before checking shorter known names`() {
        val allowedOnly = validator.validate(
            "Upper Chest volume climbed.",
            NarrativeValidationRules(
                maxWords = 8,
                maxSentences = 1,
                allowedNames = setOf("Upper Chest"),
                knownNames = setOf("Upper Chest", "Chest")
            )
        )
        assertTrue(allowedOnly is NarrativeValidationResult.Valid)

        val alsoMentionsDisallowedShorterName = validator.validate(
            "Upper Chest and Chest volume climbed.",
            NarrativeValidationRules(
                maxWords = 10,
                maxSentences = 1,
                allowedNames = setOf("Upper Chest"),
                knownNames = setOf("Upper Chest", "Chest")
            )
        )
        assertEquals(
            NarrativeValidationResult.Invalid("Narrative contains an unsupported name"),
            alsoMentionsDisallowedShorterName
        )
    }

    @Test
    fun `ignores decimal points when counting sentences`() {
        val result = validator.validate(
            "Volume reached 12,450.5 lbs, up 19.6%.",
            NarrativeValidationRules(
                maxWords = 10,
                maxSentences = 1,
                allowedNumbers = setOf("12450.5", "19.6%")
            )
        )

        assertTrue(result is NarrativeValidationResult.Valid)
    }

    @Test
    fun `catches attached numbers and normalizes leading dot decimals`() {
        val attached = validator.validate(
            "You hit 5x100 at RPE8 in week12.",
            NarrativeValidationRules(
                maxWords = 12,
                maxSentences = 1,
                allowedNumbers = setOf("5")
            )
        )
        assertEquals(
            NarrativeValidationResult.Invalid("Narrative contains an unsupported number"),
            attached
        )

        val leadingDotAllowed = validator.validate(
            "Volume ticked up .5%.",
            NarrativeValidationRules(
                maxWords = 8,
                maxSentences = 1,
                allowedNumbers = setOf("0.5%")
            )
        )
        assertTrue(leadingDotAllowed is NarrativeValidationResult.Valid)
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
