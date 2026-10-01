package com.example.gymtime.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for both directions of the plate calculator.
 */
class PlateCalculatorTest {

    private val standardPlates = listOf(45f, 35f, 25f, 10f, 5f, 2.5f)

    @Test
    fun `bar only returns empty plates and isExact true`() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 45f,
            availablePlates = standardPlates,
            barWeight = 45f
        )

        assertTrue(result.platesPerSide.isEmpty())
        assertEquals(45f, result.totalWeight)
        assertTrue(result.isExact)
    }

    @Test
    fun `135 lbs returns two 45s per side`() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 135f,
            availablePlates = standardPlates,
            barWeight = 45f
        )

        assertEquals(listOf(45f), result.platesPerSide)
        assertEquals(135f, result.totalWeight)
        assertTrue(result.isExact)
    }

    @Test
    fun `225 lbs returns correct plate combo`() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 225f,
            availablePlates = standardPlates,
            barWeight = 45f
        )

        // 225 = 45 (bar) + 2*90 (plates per side)
        // 90 per side = 45 + 45
        assertEquals(listOf(45f, 45f), result.platesPerSide)
        assertEquals(225f, result.totalWeight)
        assertTrue(result.isExact)
    }

    @Test
    fun `185 lbs returns correct plate combo`() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 185f,
            availablePlates = standardPlates,
            barWeight = 45f
        )

        // 185 = 45 (bar) + 2*70 (plates per side)
        // 70 per side = 45 + 25
        assertEquals(listOf(45f, 25f), result.platesPerSide)
        assertEquals(185f, result.totalWeight)
        assertTrue(result.isExact)
    }

    @Test
    fun `target below bar weight returns bar only with isExact false`() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 30f,
            availablePlates = standardPlates,
            barWeight = 45f
        )

        assertTrue(result.platesPerSide.isEmpty())
        assertEquals(45f, result.totalWeight)
        assertFalse(result.isExact)
    }

    @Test
    fun `inexact weight returns closest achievable`() {
        // 137 lbs can't be achieved exactly with standard plates
        // Closest is 135 (bar + 2*45)
        val result = PlateCalculator.calculatePlates(
            targetWeight = 137f,
            availablePlates = standardPlates,
            barWeight = 45f
        )

        // Should load 45 per side (135 total) since 137 isn't achievable
        assertEquals(135f, result.totalWeight)
        assertFalse(result.isExact)
    }

    @Test
    fun `closest achievable weight may be above target`() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 138f,
            availablePlates = standardPlates,
            barWeight = 45f
        )

        assertEquals(140f, result.totalWeight)
        assertEquals(listOf(45f, 2.5f), result.platesPerSide)
        assertFalse(result.isExact)
    }

    @Test
    fun `non canonical plate combination finds exact load instead of greedy approximation`() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 105f,
            availablePlates = listOf(25f, 15f),
            barWeight = 45f
        )

        assertEquals(listOf(15f, 15f), result.platesPerSide)
        assertEquals(105f, result.totalWeight)
        assertTrue(result.isExact)
    }

    @Test
    fun `complex weight uses greedy algorithm correctly`() {
        // 302.5 lbs = 45 + 2*(128.75)
        // 128.75 = 45 + 45 + 35 + 2.5 + 1.25... wait, no 1.25
        // Actually: 128.75 per side isn't exact
        // Let's do 300: 45 + 2*127.5 per side = 45 + 45 + 35 + 2.5 = 127.5! Yes.
        val result = PlateCalculator.calculatePlates(
            targetWeight = 300f,
            availablePlates = standardPlates,
            barWeight = 45f
        )

        // 300 = 45 + 2*127.5
        // 127.5 = 45 + 45 + 35 + 2.5
        assertEquals(listOf(45f, 45f, 35f, 2.5f), result.platesPerSide)
        assertEquals(300f, result.totalWeight)
        assertTrue(result.isExact)
    }

    @Test
    fun `single side loading calculates correctly`() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 90f, // 45 bar + 45 plate on one side
            availablePlates = standardPlates,
            barWeight = 45f,
            loadingSides = 1
        )

        assertEquals(listOf(45f), result.platesPerSide)
        assertEquals(90f, result.totalWeight)
        assertTrue(result.isExact)
    }

    @Test
    fun `reverse calculation totals manually loaded plates`() {
        val total = PlateCalculator.calculateTotalWeight(
            platesPerSide = listOf(45f, 25f, 10f),
            barWeight = 45f,
            loadingSides = 2
        )

        assertEquals(205f, total)
    }

    @Test
    fun `reverse calculation supports one sided loading`() {
        val total = PlateCalculator.calculateTotalWeight(
            platesPerSide = listOf(45f, 10f),
            barWeight = 45f,
            loadingSides = 1
        )

        assertEquals(100f, total)
    }

    @Test
    fun `invalid loading side count is safely treated as one`() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 90f,
            availablePlates = listOf(45f),
            barWeight = 45f,
            loadingSides = 0
        )

        assertEquals(listOf(45f), result.platesPerSide)
        assertEquals(90f, result.totalWeight)
        assertTrue(result.isExact)
    }

    @Test
    fun `invalid duplicate and nonpositive plate settings are ignored`() {
        val options = listOf(45f, 0f, -5f, 0.001f, 10_001f, Float.NaN, Float.POSITIVE_INFINITY, 45f, 2.5f)

        assertEquals(listOf(45f, 2.5f), PlateCalculator.sanitizePlateOptions(options))
        val result = PlateCalculator.calculatePlates(135f, options, 45f)
        assertEquals(listOf(45f), result.platesPerSide)
        assertEquals(135f, result.totalWeight)
    }

    @Test
    fun `corrupted extreme values are bounded without allocating an unsafe search space`() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = Float.MAX_VALUE,
            availablePlates = listOf(0.01f, Float.MAX_VALUE),
            barWeight = Float.NEGATIVE_INFINITY,
            loadingSides = Int.MAX_VALUE
        )

        assertTrue(result.totalWeight in 0f..PlateCalculator.MAX_SUPPORTED_WEIGHT)
        assertTrue(result.platesPerSide.all { it == 0.01f })
    }

    @Test
    fun `reverse calculation ignores invalid manually loaded plates`() {
        val total = PlateCalculator.calculateTotalWeight(
            platesPerSide = listOf(45f, 0f, -10f, Float.NaN),
            barWeight = 45f,
            loadingSides = 2
        )

        assertEquals(135f, total)
    }

    @Test
    fun `exact forward loadout round trips through reverse calculation`() {
        val loadout = PlateCalculator.calculatePlates(300f, standardPlates, 45f, 2)

        assertEquals(
            loadout.totalWeight,
            PlateCalculator.calculateTotalWeight(loadout.platesPerSide, 45f, 2)
        )
    }

    @Test
    fun `format weight omits unnecessary decimal zero`() {
        assertEquals("135", PlateCalculator.formatWeight(135f))
        assertEquals("137.5", PlateCalculator.formatWeight(137.5f))
    }

    @Test
    fun `formatPlateLoadout with empty plates returns Bar only`() {
        val loadout = PlateLoadout(emptyList(), 45f, true)
        assertEquals("Bar only", PlateCalculator.formatPlateLoadout(loadout))
    }

    @Test
    fun `formatPlateLoadout formats plates correctly`() {
        val loadout = PlateLoadout(listOf(45f, 25f, 10f), 205f, true)
        assertEquals("45 + 25 + 10", PlateCalculator.formatPlateLoadout(loadout))
    }

    @Test
    fun `formatPlateLoadout handles decimal plates`() {
        val loadout = PlateLoadout(listOf(45f, 2.5f), 140f, true)
        assertEquals("45 + 2.5", PlateCalculator.formatPlateLoadout(loadout))
    }

    @Test
    fun `getPlateColor returns correct colors`() {
        assertEquals(0xFF3498DB, PlateCalculator.getPlateColor(45f)) // Blue
        assertEquals(0xFF2ECC71, PlateCalculator.getPlateColor(25f)) // Green
        assertEquals(0xFFF39C12, PlateCalculator.getPlateColor(35f)) // Yellow
        assertEquals(0xFFE74C3C, PlateCalculator.getPlateColor(55f)) // Red
        assertEquals(0xFFECF0F1, PlateCalculator.getPlateColor(10f)) // Light gray
        assertEquals(0xFFBDC3C7, PlateCalculator.getPlateColor(5f))  // Gray
    }

    @Test
    fun `empty available plates returns bar only`() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 135f,
            availablePlates = emptyList(),
            barWeight = 45f
        )

        assertTrue(result.platesPerSide.isEmpty())
        assertEquals(45f, result.totalWeight)
        assertFalse(result.isExact)
    }

    @Test
    fun `finite inventory finds non greedy exact combination`() {
        val inventory = mapOf(9f to 1, 6f to 2)
        val result = PlateCalculator.calculatePlates(12f, listOf(9f, 6f), 0f, 1, inventory)

        assertEquals(listOf(6f, 6f), result.platesPerSide)
        assertEquals(12f, result.totalWeight)
        assertTrue(result.isExact)
        assertWithinInventory(result, inventory, 1)
    }

    @Test
    fun `bounded closest load can exceed target when smallest plate stock is exhausted`() {
        val inventory = mapOf(10f to 1, 1f to 1)
        val result = PlateCalculator.calculatePlates(7f, listOf(10f, 1f), 0f, 1, inventory)

        assertEquals(listOf(10f), result.platesPerSide)
        assertEquals(10f, result.totalWeight)
        assertFalse(result.isExact)
        assertWithinInventory(result, inventory, 1)
    }

    @Test
    fun `odd inventory count cannot be mirrored on both sides`() {
        val inventory = mapOf(45f to 3)
        val result = PlateCalculator.calculatePlates(225f, listOf(45f), 45f, 2, inventory)

        assertEquals(listOf(45f), result.platesPerSide)
        assertEquals(135f, result.totalWeight)
        assertFalse(result.isExact)
        assertWithinInventory(result, inventory, 2)
    }

    @Test
    fun `single sided inventory uses all individual plates`() {
        val inventory = mapOf(45f to 3)
        val result = PlateCalculator.calculatePlates(180f, listOf(45f), 45f, 1, inventory)

        assertEquals(listOf(45f, 45f, 45f), result.platesPerSide)
        assertEquals(180f, result.totalWeight)
        assertTrue(result.isExact)
        assertWithinInventory(result, inventory, 1)
    }

    @Test
    fun `empty missing zero or disabled stock cannot be loaded`() {
        listOf(emptyMap(), mapOf(45f to 0), mapOf(45f to -10), mapOf(25f to 10)).forEach { inventory ->
            val result = PlateCalculator.calculatePlates(135f, listOf(45f), 45f, 2, inventory)
            assertTrue(result.platesPerSide.isEmpty())
            assertEquals(45f, result.totalWeight)
            assertFalse(result.isExact)
        }
    }

    @Test
    fun `finite stock tie chooses lower weight and exact solutions use fewest plates`() {
        val tie = PlateCalculator.calculatePlates(9f, listOf(12f, 6f), 0f, 1, mapOf(12f to 1, 6f to 1))
        assertEquals(6f, tie.totalWeight)
        val inventory = mapOf(6f to 2, 4f to 3, 3f to 4)
        val minimum = PlateCalculator.calculatePlates(12f, listOf(6f, 4f, 3f), 0f, 1, inventory)
        assertEquals(listOf(6f, 6f), minimum.platesPerSide)
        assertWithinInventory(minimum, inventory, 1)
    }

    @Test
    fun `canonical plate aliases do not duplicate stock and invalid weights are rejected`() {
        val inventory = mapOf(2.501f to 6, 2.499f to 2, Float.NaN to 100, Float.POSITIVE_INFINITY to 100)
        assertEquals(mapOf(2.5f to 2), PlateCalculator.sanitizePlateInventory(inventory))
        assertEquals(listOf(2.5f), PlateCalculator.sanitizePlateOptions(listOf(2.501f, 2.499f)))
        val result = PlateCalculator.calculatePlates(10f, listOf(2.501f, 2.499f), 0f, 2, inventory)
        assertEquals(listOf(2.5f), result.platesPerSide)
        assertEquals(5f, result.totalWeight)
    }

    @Test
    fun `extreme inventory quantities and loading sides are bounded without overflow`() {
        val result = PlateCalculator.calculatePlates(
            Float.MAX_VALUE, listOf(1f, Float.MAX_VALUE), Float.NaN, Int.MAX_VALUE,
            mapOf(1f to Int.MAX_VALUE, Float.MAX_VALUE to Int.MAX_VALUE)
        )
        assertEquals(List(6) { 1f }, result.platesPerSide)
        assertEquals(96f, result.totalWeight)
        assertEquals(PlateCalculator.MAX_PLATE_COUNT, PlateCalculator.sanitizePlateInventory(mapOf(1f to Int.MAX_VALUE))[1f])
    }

    @Test
    fun `manual loading helpers conserve stock and retain unlimited mode`() {
        val inventory = mapOf(45f to 3, 2.5f to 4)
        assertEquals(1, PlateCalculator.maxPlatesPerSide(45f, 2, inventory))
        assertEquals(3, PlateCalculator.maxPlatesPerSide(45f, 1, inventory))
        assertEquals(0, PlateCalculator.maxPlatesPerSide(25f, 2, inventory))
        assertFalse(PlateCalculator.canAddPlate(listOf(45f), 45f, 2, inventory))
        assertTrue(PlateCalculator.canAddPlate(listOf(2.5f), 2.5f, 2, inventory))
        assertTrue(PlateCalculator.canAddPlate(List(20) { 45f }, 45f, 2, null))
        assertFalse(PlateCalculator.canAddPlate(emptyList(), Float.NaN, 2, null))
    }

    @Test
    fun `bounded solver matches exhaustive search without exceeding any stock`() {
        val options = listOf(9f, 6f, 4f)
        for (sides in 1..2) {
            for (nines in 0..3) for (sixes in 0..3) for (fours in 0..3) {
                val inventory = mapOf(9f to nines, 6f to sixes, 4f to fours)
                val achievable = buildList {
                    for (a in 0..nines / sides) for (b in 0..sixes / sides) for (c in 0..fours / sides) {
                        add((a * 9 + b * 6 + c * 4) * sides to a + b + c)
                    }
                }
                for (target in 0..30) {
                    val best = achievable.minWith(
                        compareBy<Pair<Int, Int>> { kotlin.math.abs(it.first - target) }
                            .thenBy { if (it.first <= target) 0 else 1 }
                            .thenBy { it.second }
                    )
                    val result = PlateCalculator.calculatePlates(target.toFloat(), options, 0f, sides, inventory)
                    assertEquals("target=$target sides=$sides inventory=$inventory", best.first.toFloat(), result.totalWeight)
                    assertEquals(best.second, result.platesPerSide.size)
                    assertWithinInventory(result, inventory, sides)
                }
            }
        }
    }

    @Test
    fun `maximum total is actual attainable load rather than clamped overshoot`() {
        val result = PlateCalculator.calculatePlates(10_000f, listOf(6f), 0f, 1, null)
        assertEquals(9_996f, result.totalWeight)
        assertFalse(result.isExact)
        assertEquals(result.totalWeight, result.platesPerSide.sum() + 0f)
    }

    @Test
    fun `more than sixty four denominations preserve smallest exact option in unlimited mode`() {
        val options = (65..130).map { it.toFloat() } + 1f
        assertEquals(options.size, PlateCalculator.sanitizePlateOptions(options).size)
        val result = PlateCalculator.calculatePlates(1f, options, 0f, 1)
        assertEquals(listOf(1f), result.platesPerSide)
        assertTrue(result.isExact)
    }

    @Test
    fun `more than sixty four denominations preserve smallest exact option with finite stock`() {
        val options = (65..130).map { it.toFloat() } + 1f
        val inventory = options.associateWith { 1 }
        val result = PlateCalculator.calculatePlates(1f, options, 0f, 1, inventory)
        assertEquals(listOf(1f), result.platesPerSide)
        assertTrue(result.isExact)
        assertWithinInventory(result, inventory, 1)
    }

    private fun assertWithinInventory(loadout: PlateLoadout, inventory: Map<Float, Int>, sides: Int) {
        val stock = PlateCalculator.sanitizePlateInventory(inventory)
        loadout.platesPerSide.groupingBy { it }.eachCount().forEach { (plate, count) ->
            assertTrue("$plate uses $count per side with $sides sides; stock=$stock", count * sides <= (stock[plate] ?: 0))
        }
    }
}
