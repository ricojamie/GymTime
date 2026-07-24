package com.example.gymtime.smartlog

import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLogValidatorAndStoreTest {
    private val validator = SmartLogValidator()

    @Test
    fun `converts kilograms and fills default distance unit`() {
        val exercise = exercise(LogType.WEIGHT_DISTANCE, DistanceUnit.YARDS)
        val result = validator.validate(
            exercise,
            ParsedLoggingCommand(sets = listOf(SetDraft(weight = 10f, weightUnit = WeightUnit.KILOGRAMS, distanceValue = 20f)))
        )

        assertTrue(result.isValid)
        assertEquals(22.0462f, result.draft?.sets?.single()?.weight ?: 0f, 0.0001f)
        assertEquals(DistanceUnit.YARDS, result.draft?.sets?.single()?.distanceUnit)
    }

    @Test
    fun `rejects missing and incompatible fields`() {
        val result = validator.validate(
            exercise(LogType.REPS_ONLY),
            ParsedLoggingCommand(sets = listOf(SetDraft(weight = 50f)))
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("needs reps") })
        assertTrue(result.errors.any { it.contains("includes weight") })
    }

    @Test
    fun `store advances one confirmed set at a time`() {
        val store = SmartLogDraftStore()
        val exercise = exercise(LogType.WEIGHT_REPS)
        val token = store.put(
            ValidatedSmartLogDraft(exercise, listOf(SetDraft(weight = 50f, reps = 10), SetDraft(weight = 50f, reps = 8)))
        )

        assertEquals(0, store.peek(token, exercise.id)?.currentIndex)
        assertEquals(1, store.advance(token, exercise.id)?.currentIndex)
        assertNull(store.advance(token, exercise.id))
        assertNull(store.peek(token, exercise.id))
    }

    private fun exercise(logType: LogType, unit: DistanceUnit = DistanceUnit.MILES) = Exercise(
        id = 1,
        name = "Test",
        targetMuscle = "Test",
        logType = logType,
        defaultDistanceUnit = unit,
        isCustom = true,
        notes = null,
        defaultRestSeconds = 90
    )
}
