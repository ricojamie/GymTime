package com.example.gymtime.util

import com.example.gymtime.data.db.entity.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun `distanceToMeters converts miles correctly`() {
        val result = TimeUtils.distanceToMeters(1f, DistanceUnit.MILES)

        assertEquals(1609.344f, result ?: 0f, 0.001f)
    }

    @Test
    fun `distanceToMeters returns null for non-convertible units`() {
        assertNull(TimeUtils.distanceToMeters(5_000f, DistanceUnit.STEPS))
        assertNull(TimeUtils.distanceToMeters(12f, DistanceUnit.FLOORS))
    }

    @Test
    fun `metersToDistance converts meters to yards correctly`() {
        val result = TimeUtils.metersToDistance(91.44f, DistanceUnit.YARDS)

        assertEquals(100f, result, 0.001f)
    }

    @Test
    fun `formatDistance uses whole numbers for steps and floors`() {
        assertEquals("1235", TimeUtils.formatDistance(1234.56f, DistanceUnit.STEPS))
        assertEquals("8", TimeUtils.formatDistance(7.6f, DistanceUnit.FLOORS))
    }

    @Test
    fun `formatDistance keeps decimals for miles and kilometers`() {
        assertEquals("1.25", TimeUtils.formatDistance(1.25f, DistanceUnit.MILES))
        assertEquals("5.50", TimeUtils.formatDistance(5.5f, DistanceUnit.KILOMETERS))
    }
}
