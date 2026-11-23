package com.example.gymtime.smartlog

import com.example.gymtime.data.db.entity.DistanceUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `expands dumbbell shorthand with explicit kilogram unit`() = runTest {
        val result = parser.extract("Incline DB press, 32 kg, 10, 9, 8")

        assertEquals("Incline DB press", result?.exerciseQuery)
        assertEquals(listOf(10, 9, 8), result?.sets?.map { it.reps })
        assertEquals(listOf(32f, 32f, 32f), result?.sets?.map { it.weight })
        assertTrue(result?.sets?.all { it.weightUnit == WeightUnit.KILOGRAMS } == true)
    }

    @Test
    fun `expands set count notation`() = runTest {
        val result = parser.extract("Bench press 3x10 at 65")

        assertEquals(3, result?.sets?.size)
        assertEquals(10, result?.sets?.first()?.reps)
        assertEquals(65f, result?.sets?.first()?.weight)
    }

    @Test
    fun `parses no space kilogram and pound units`() = runTest {
        val single = parser.extract("Bench press 100kg, 8 reps")
        assertEquals(100f, single?.sets?.single()?.weight)
        assertEquals(WeightUnit.KILOGRAMS, single?.sets?.single()?.weightUnit)
        assertEquals(8, single?.sets?.single()?.reps)

        val multi = parser.extract("Bench press 3x10 at 60kg")
        assertEquals(3, multi?.sets?.size)
        assertEquals(60f, multi?.sets?.first()?.weight)
        assertEquals(WeightUnit.KILOGRAMS, multi?.sets?.first()?.weightUnit)

        val xForm = parser.extract("Squat 100kg x 5")
        assertEquals(100f, xForm?.sets?.single()?.weight)
        assertEquals(WeightUnit.KILOGRAMS, xForm?.sets?.single()?.weightUnit)
        assertEquals(5, xForm?.sets?.single()?.reps)

        val lbsForm = parser.extract("Bench press 135lbs x 8")
        assertEquals(135f, lbsForm?.sets?.single()?.weight)
        assertEquals(WeightUnit.POUNDS, lbsForm?.sets?.single()?.weightUnit)
        assertEquals(8, lbsForm?.sets?.single()?.reps)
    }

    @Test
    fun `parses count by reps without at when weight unit is explicit`() = runTest {
        val result = parser.extract("Bench press 3x10 135 lbs")

        assertEquals("Bench press", result?.exerciseQuery)
        assertEquals(3, result?.sets?.size)
        assertEquals(135f, result?.sets?.first()?.weight)
        assertEquals(10, result?.sets?.first()?.reps)
        assertEquals(WeightUnit.POUNDS, result?.sets?.first()?.weightUnit)
    }

    @Test
    fun `preserves over-limit set count for validator and rejects zero count`() = runTest {
        val overflow = parser.extract("Bench press 25x10 at 135")
        assertEquals(21, overflow?.sets?.size)
        assertEquals(135f, overflow?.sets?.first()?.weight)

        val zero = parser.extract("Bench press 0x10 at 135")
        assertNull(zero)
    }

    @Test
    fun `does not steal weight distance or weight duration phrases as reps`() = runTest {
        val sled = parser.extract("Sled push 180 lb for 40 yards")
        assertEquals("Sled push", sled?.exerciseQuery)
        assertEquals(180f, sled?.sets?.single()?.weight)
        assertEquals(40f, sled?.sets?.single()?.distanceValue)
        assertEquals(DistanceUnit.YARDS, sled?.sets?.single()?.distanceUnit)
        assertNull(sled?.sets?.single()?.reps)

        val walkSeconds = parser.extract("Farmer walk 70 lb for 45 seconds")
        assertEquals("Farmer walk", walkSeconds?.exerciseQuery)
        assertEquals(70f, walkSeconds?.sets?.single()?.weight)
        assertEquals(45, walkSeconds?.sets?.single()?.durationSeconds)
        assertNull(walkSeconds?.sets?.single()?.reps)

        val walkShortS = parser.extract("Farmer walk 70 lb for 45s")
        assertEquals(70f, walkShortS?.sets?.single()?.weight)
        assertEquals(45, walkShortS?.sets?.single()?.durationSeconds)
        assertNull(walkShortS?.sets?.single()?.reps)
    }

    @Test
    fun `retains warmup flag for leading warmup prefix and keeps library suffix`() = runTest {
        val withoutName = parser.extract("Warmup 135 x 10")
        assertNull(withoutName?.exerciseQuery)
        assertTrue(withoutName?.sets?.single()?.isWarmup == true)
        assertEquals(135f, withoutName?.sets?.single()?.weight)
        assertEquals(10, withoutName?.sets?.single()?.reps)

        val withName = parser.extract("Warmup bench press 135 x 10")
        assertEquals("bench press", withName?.exerciseQuery)
        assertTrue(withName?.sets?.single()?.isWarmup == true)
        assertEquals(135f, withName?.sets?.single()?.weight)
        assertEquals(10, withName?.sets?.single()?.reps)

        val suffixName = parser.extract("Shoulder Warmup 10 reps")
        assertEquals("Shoulder Warmup", suffixName?.exerciseQuery)
        assertEquals(10, suffixName?.sets?.single()?.reps)
    }

    @Test
    fun `strips command prepositions cleanly from exercise query`() = runTest {
        val logSet = parser.extract("Log set 3x10 at 135")
        assertNull(logSet?.exerciseQuery)
        assertEquals(3, logSet?.sets?.size)

        val trailingAt = parser.extract("Bench press at 135 x 10")
        assertEquals("Bench press", trailingAt?.exerciseQuery)
        assertEquals(135f, trailingAt?.sets?.single()?.weight)
        assertEquals(10, trailingAt?.sets?.single()?.reps)
    }

    @Test
    fun `parses decimal durations bare seconds invalid mmss and thousands commas`() = runTest {
        val decimalMin = parser.extract("Plank 1.5 minutes")
        assertEquals(90, decimalMin?.sets?.single()?.durationSeconds)

        val bareSeconds = parser.extract("Plank 45s")
        assertEquals(45, bareSeconds?.sets?.single()?.durationSeconds)

        assertNull(parser.extract("Plank 1:90"))
        assertNull(parser.extract("Plank 0:00"))

        val rowing = parser.extract("Rowing 1,000 meters")
        assertEquals(1000f, rowing?.sets?.single()?.distanceValue)
        assertEquals(DistanceUnit.METERS, rowing?.sets?.single()?.distanceUnit)
    }

    @Test
    fun `parses bare integer reps and out of range rpe for validation`() = runTest {
        val pullups = parser.extract("Pull-ups 12")
        assertEquals("Pull-ups", pullups?.exerciseQuery)
        assertEquals(12, pullups?.sets?.single()?.reps)

        val highRpe = parser.extract("Bench press 135 x 8 rpe 10.5")
        assertEquals(10.5f, highRpe?.sets?.single()?.rpe)
    }

    @Test
    fun `parses distance duration and calories phrases`() = runTest {
        val result = parser.extract("Rowing, 2000 meters, 8 minutes, 140 calories")

        assertEquals(2000f, result?.sets?.single()?.distanceValue)
        assertEquals(480, result?.sets?.single()?.durationSeconds)
        assertEquals(140f, result?.sets?.single()?.calories)
    }
}
