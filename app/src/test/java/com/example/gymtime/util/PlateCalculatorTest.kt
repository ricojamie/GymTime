package com.example.gymtime.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for PlateCalculator - verifies the greedy plate selection algorithm.
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
}
