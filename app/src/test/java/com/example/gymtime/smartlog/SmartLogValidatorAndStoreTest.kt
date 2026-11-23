package com.example.gymtime.smartlog

import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.WARMUP_MUSCLE_GROUP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLogValidatorAndStoreTest {
    private val validator = SmartLogValidator()

    @Test
    fun `converts kilograms rounded to 2 decimals and fills default distance unit`() {
        val exercise = exercise(LogType.WEIGHT_DISTANCE, DistanceUnit.YARDS)
        val result = validator.validate(
            exercise,
            ParsedLoggingCommand(sets = listOf(SetDraft(weight = 10f, weightUnit = WeightUnit.KILOGRAMS, distanceValue = 20f)))
        )

        assertTrue(result.isValid)
        assertEquals(22.05f, result.draft?.sets?.single()?.weight ?: 0f, 0.0001f)
        assertEquals(DistanceUnit.YARDS, result.draft?.sets?.single()?.distanceUnit)

        val sixtyKg = validator.validate(
            exercise(LogType.WEIGHT_REPS),
            ParsedLoggingCommand(sets = listOf(SetDraft(weight = 60f, weightUnit = WeightUnit.KILOGRAMS, reps = 10)))
        )
        assertTrue(sixtyKg.isValid)
        assertEquals(132.28f, sixtyKg.draft?.sets?.single()?.weight ?: 0f, 0.0001f)
    }

    @Test
    fun `deduplicates zero rpe error and rejects over 20 sets`() {
        val zeroRpe = validator.validate(
            exercise(LogType.WEIGHT_REPS),
            ParsedLoggingCommand(sets = listOf(SetDraft(weight = 135f, reps = 8, rpe = 0f)))
        )
        assertFalse(zeroRpe.isValid)
        assertEquals(1, zeroRpe.errors.size)
        assertEquals("Set RPE must be between 1 and 10.", zeroRpe.errors.single())

        val tooMany = validator.validate(
            exercise(LogType.WEIGHT_REPS),
            ParsedLoggingCommand(sets = List(21) { SetDraft(weight = 135f, reps = 10) })
        )
        assertFalse(tooMany.isValid)
        assertEquals(listOf("Smart Log supports up to 20 sets at a time."), tooMany.errors)
    }

    @Test
    fun `marks warmups category exercises as warmup in review`() {
        val warmupExercise = exercise(LogType.REPS_ONLY, targetMuscle = WARMUP_MUSCLE_GROUP)
        val result = validator.validate(
            warmupExercise,
            ParsedLoggingCommand(sets = listOf(SetDraft(reps = 15, isWarmup = false)))
        )

        assertTrue(result.isValid)
        assertTrue(result.draft?.sets?.single()?.isWarmup == true)
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

    @Test
    fun `store evicts least recently used draft beyond cap`() {
        val store = SmartLogDraftStore()
        val exercise = exercise(LogType.WEIGHT_REPS)
        val draft = ValidatedSmartLogDraft(exercise, listOf(SetDraft(weight = 50f, reps = 10)))

        val tokens = (1..SmartLogDraftStore.MAX_DRAFTS).map { store.put(draft) }
        // Touch the first token so token[1] becomes the least recently used entry.
        assertNotNull(store.peek(tokens.first(), exercise.id))

        val newest = store.put(draft)
        assertNotNull(store.peek(newest, exercise.id))
        assertNotNull(store.peek(tokens.first(), exercise.id))
        assertNull(store.peek(tokens[1], exercise.id))
    }

    private fun exercise(
        logType: LogType,
        unit: DistanceUnit = DistanceUnit.MILES,
        targetMuscle: String = "Test"
    ) = Exercise(
        id = 1,
        name = "Test",
        targetMuscle = targetMuscle,
        logType = logType,
        defaultDistanceUnit = unit,
        isCustom = true,
        notes = null,
        defaultRestSeconds = 90
    )
}
