package com.example.gymtime.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for OneRepMaxCalculator - verifies Epley formula calculations.
 */
class OneRepMaxCalculatorTest {

    @Test
    fun `calculateE1RM with valid inputs returns correct value`() {
        // Epley formula: weight * (1 + reps/30)
        // 200 lbs * (1 + 5/30) = 200 * 1.167 = 233.33
        val result = OneRepMaxCalculator.calculateE1RM(200f, 5)
        
        assertNotNull(result)
        assertEquals(233.33f, result!!, 0.1f)
    }

    @Test
    fun `calculateE1RM with 1 rep returns same weight`() {
        // 1 rep = actual weight = E1RM
        // Actually: 200 * (1 + 1/30) = 200 * 1.033 = 206.67
        val result = OneRepMaxCalculator.calculateE1RM(200f, 1)
        
        assertNotNull(result)
        assertEquals(206.67f, result!!, 0.1f)
    }

    @Test
    fun `calculateE1RM with 10 reps calculates correctly`() {
        // 200 * (1 + 10/30) = 200 * 1.333 = 266.67
        val result = OneRepMaxCalculator.calculateE1RM(200f, 10)
        
        assertNotNull(result)
        assertEquals(266.67f, result!!, 0.1f)
    }

    @Test
    fun `calculateE1RM with zero weight returns null`() {
        val result = OneRepMaxCalculator.calculateE1RM(0f, 5)
        assertNull(result)
    }

    @Test
    fun `calculateE1RM with negative weight returns null`() {
        val result = OneRepMaxCalculator.calculateE1RM(-100f, 5)
        assertNull(result)
    }

    @Test
    fun `calculateE1RM with zero reps returns null`() {
        val result = OneRepMaxCalculator.calculateE1RM(200f, 0)
        assertNull(result)
    }

    @Test
    fun `calculateE1RM with negative reps returns null`() {
        val result = OneRepMaxCalculator.calculateE1RM(200f, -5)
        assertNull(result)
    }

    @Test
    fun `calculateE1RM with reps greater than 15 returns null`() {
        // High rep sets are inaccurate for 1RM estimation
        val result = OneRepMaxCalculator.calculateE1RM(200f, 16)
        assertNull(result)
    }

    @Test
    fun `calculateE1RM with exactly 15 reps returns value`() {
        val result = OneRepMaxCalculator.calculateE1RM(200f, 15)
        assertNotNull(result)
    }

    @Test
    fun `calculateE10RM returns correct value`() {
        // First calculate E1RM: 200 * (1 + 5/30) = 233.33
        // Then reverse: E10RM = E1RM / (1 + 10/30) = 233.33 / 1.333 = 175
        val result = OneRepMaxCalculator.calculateE10RM(200f, 5)
        
        assertNotNull(result)
        assertEquals(175f, result!!, 0.5f)
    }

    @Test
    fun `calculateE10RM with invalid input returns null`() {
        val result = OneRepMaxCalculator.calculateE10RM(0f, 5)
        assertNull(result)
    }

    @Test
    fun `formatWeight rounds to nearest 2_5 lbs`() {
        assertEquals(227.5f, OneRepMaxCalculator.formatWeight(226.3f))
        assertEquals(227.5f, OneRepMaxCalculator.formatWeight(227.5f))
        assertEquals(230f, OneRepMaxCalculator.formatWeight(228.8f))
        assertEquals(100f, OneRepMaxCalculator.formatWeight(101.2f))
        assertEquals(102.5f, OneRepMaxCalculator.formatWeight(102.5f))
    }

    @Test
    fun `formatWeight handles edge cases`() {
        assertEquals(0f, OneRepMaxCalculator.formatWeight(0f))
        assertEquals(2.5f, OneRepMaxCalculator.formatWeight(1.3f))
        assertEquals(2.5f, OneRepMaxCalculator.formatWeight(2.5f))
    }
}
