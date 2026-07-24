package com.example.gymtime.smartlog

import com.example.gymtime.data.db.dao.ExerciseUsageRow
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMatcherTest {
    private val matcher = ExerciseMatcher()
    private val incline = exercise(1, "Incline Dumbbell Press")
    private val bench = exercise(2, "Barbell Bench Press")

    @Test
    fun `exact normalized name auto matches`() {
        val result = matcher.match("incline dumbbell press", rows(incline, bench))
        assertEquals(incline, (result as ExerciseMatchResult.Matched).candidate.exercise)
    }

    @Test
    fun `abbreviation and reordered tokens match`() {
        val result = matcher.match("dumbbell incline press", rows(incline, bench))
        assertEquals(incline, (result as ExerciseMatchResult.Matched).candidate.exercise)
    }

    @Test
    fun `ambiguous short query requires confirmation`() {
        val result = matcher.match(
            "press",
            rows(incline, bench, exercise(3, "Overhead Press"))
        )
        assertTrue(result is ExerciseMatchResult.NeedsConfirmation)
    }

    @Test
    fun `missing query uses current exercise only`() {
        val result = matcher.match(null, rows(incline, bench), currentExercise = bench)
        assertEquals(bench, (result as ExerciseMatchResult.Matched).candidate.exercise)
    }

    private fun rows(vararg exercises: Exercise) = exercises.map {
        ExerciseUsageRow(it, allTimeSetCount = 0, recentSetCount = 0, lastUsedMs = null)
    }

    private fun exercise(id: Long, name: String) = Exercise(
        id = id,
        name = name,
        targetMuscle = "Chest",
        logType = LogType.WEIGHT_REPS,
        isCustom = false,
        notes = null,
        defaultRestSeconds = 90
    )
}
