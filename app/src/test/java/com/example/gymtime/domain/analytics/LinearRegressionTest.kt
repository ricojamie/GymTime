package com.example.gymtime.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinearRegressionTest {

    private val epsilon = 0.0001f

    @Test
    fun `fit returns null for empty list`() {
        assertNull(LinearRegression.fit(emptyList()))
    }

    @Test
    fun `fit returns null for single point`() {
        assertNull(LinearRegression.fit(listOf(42f)))
    }

    @Test
    fun `perfect ascending line has exact slope and intercept`() {
        val line = LinearRegression.fit(listOf(1f, 2f, 3f))!!
        assertEquals(1f, line.slope, epsilon)
        assertEquals(1f, line.intercept, epsilon)
    }

    @Test
    fun `all-equal values yield zero slope`() {
        val line = LinearRegression.fit(listOf(5f, 5f, 5f, 5f))!!
        assertEquals(0f, line.slope, epsilon)
        assertEquals(5f, line.intercept, epsilon)
    }

    @Test
    fun `descending line has negative slope`() {
        val line = LinearRegression.fit(listOf(10f, 8f, 6f, 4f))!!
        assertEquals(-2f, line.slope, epsilon)
        assertEquals(10f, line.intercept, epsilon)
    }

    @Test
    fun `noisy data fits within tolerance`() {
        // y ≈ 2x + 1 with noise: exact least-squares result computed by hand.
        // points: 1.1, 2.9, 5.2, 6.8, 9.1 → slope 1.99, intercept 1.04
        val line = LinearRegression.fit(listOf(1.1f, 2.9f, 5.2f, 6.8f, 9.1f))!!
        assertEquals(1.99f, line.slope, 0.001f)
        assertEquals(1.04f, line.intercept, 0.001f)
    }

    @Test
    fun `trendValues is empty when no fit`() {
        assertTrue(LinearRegression.trendValues(emptyList()).isEmpty())
        assertTrue(LinearRegression.trendValues(listOf(3f)).isEmpty())
    }

    @Test
    fun `trendValues length matches input and follows fitted line`() {
        val values = listOf(2f, 4f, 6f, 8f)
        val trend = LinearRegression.trendValues(values)
        assertEquals(values.size, trend.size)
        values.forEachIndexed { i, expected ->
            assertEquals(expected, trend[i], epsilon)
        }
    }

    @Test
    fun `non-finite inputs return null from fit and empty from trendValues`() {
        assertNull(LinearRegression.fit(listOf(10f, Float.NaN, 30f)))
        assertNull(LinearRegression.fit(listOf(10f, Float.POSITIVE_INFINITY, 30f)))
        assertNull(LinearRegression.fit(listOf(10f, Float.NEGATIVE_INFINITY)))
        assertTrue(LinearRegression.trendValues(listOf(10f, Float.NaN, 30f)).isEmpty())
        assertTrue(LinearRegression.trendValues(listOf(10f, Float.POSITIVE_INFINITY)).isEmpty())
    }

    @Test
    fun `large constant offset preserves centered OLS precision`() {
        val values = listOf(1_000_000f, 1_000_002f, 1_000_004f, 1_000_006f)
        val line = LinearRegression.fit(values)!!
        assertEquals(2f, line.slope, 0.01f)
        assertEquals(1_000_000f, line.intercept, 0.5f)
    }
}
