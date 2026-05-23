package com.example.gymtime.util

import com.example.gymtime.domain.share.ShareableExercise
import com.example.gymtime.domain.share.ShareableSet
import com.example.gymtime.domain.share.ShareableWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class WorkoutShareFormatterTest {

    private val locale = Locale.US
    private val date = SimpleDateFormat("yyyy-MM-dd", locale).parse("2026-05-19")!!

    @Test
    fun `formats header with duration, volume, working sets and exercise count`() {
        val output = WorkoutShareFormatter.format(
            ShareableWorkout(
                date = date,
                durationMinutes = 58,
                totalVolume = 8420f,
                totalWorkingSets = 5,
                exercises = listOf(
                    ShareableExercise(
                        name = "Bench Press",
                        targetMuscle = "Chest",
                        sets = listOf(
                            ShareableSet(60f, 8, isWarmup = false, isPersonalRecord = false),
                            ShareableSet(80f, 5, isWarmup = false, isPersonalRecord = false),
                            ShareableSet(90f, 3, isWarmup = false, isPersonalRecord = true)
                        )
                    ),
                    ShareableExercise(
                        name = "Squat",
                        targetMuscle = "Legs",
                        sets = listOf(
                            ShareableSet(100f, 8, isWarmup = false, isPersonalRecord = false),
                            ShareableSet(120f, 5, isWarmup = false, isPersonalRecord = false)
                        )
                    )
                )
            ),
            locale = locale
        )

        val lines = output.lines()
        assertEquals("GymTime — 19 May 2026", lines[0])
        assertEquals("Duration: 58m  |  Volume: 8,420 lbs  |  5 sets across 2 exercises", lines[1])
    }

    @Test
    fun `excludes warmups from rendered set list and PR marker only on heaviest`() {
        val output = WorkoutShareFormatter.format(
            ShareableWorkout(
                date = date,
                durationMinutes = 30,
                totalVolume = 540f,
                totalWorkingSets = 2,
                exercises = listOf(
                    ShareableExercise(
                        name = "Bench Press",
                        targetMuscle = "Chest",
                        sets = listOf(
                            ShareableSet(40f, 10, isWarmup = true, isPersonalRecord = false),
                            ShareableSet(60f, 5, isWarmup = false, isPersonalRecord = false),
                            ShareableSet(70f, 3, isWarmup = false, isPersonalRecord = true)
                        )
                    )
                )
            ),
            locale = locale
        )

        assertFalse("Warmup must not be rendered", output.contains("40 lbs × 10"))
        assertTrue("Working sets are rendered", output.contains("60 lbs × 5"))
        assertTrue("PR marker on heaviest", output.contains("70 lbs × 3  🏆 PR"))
        assertEquals(1, output.split("🏆 PR").size - 1)
    }

    @Test
    fun `omits duration when null and renders hours when over 60m`() {
        val noDuration = WorkoutShareFormatter.format(
            ShareableWorkout(
                date = date,
                durationMinutes = null,
                totalVolume = 100f,
                totalWorkingSets = 1,
                exercises = emptyList()
            ),
            locale = locale
        )
        assertFalse(noDuration.contains("Duration:"))

        val long = WorkoutShareFormatter.format(
            ShareableWorkout(
                date = date,
                durationMinutes = 75,
                totalVolume = 100f,
                totalWorkingSets = 1,
                exercises = emptyList()
            ),
            locale = locale
        )
        assertTrue(long.contains("Duration: 1h 15m"))
    }

    @Test
    fun `trims trailing zero on whole-number weights`() {
        val output = WorkoutShareFormatter.format(
            ShareableWorkout(
                date = date,
                durationMinutes = 10,
                totalVolume = 100f,
                totalWorkingSets = 1,
                exercises = listOf(
                    ShareableExercise(
                        name = "Curl",
                        targetMuscle = "Biceps",
                        sets = listOf(ShareableSet(25f, 10, isWarmup = false, isPersonalRecord = false))
                    )
                )
            ),
            locale = locale
        )
        assertTrue(output.contains("25 lbs × 10"))
        assertFalse(output.contains("25.0 lbs"))
    }
}
