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

    @Test
    fun `parseHMSToSeconds parses valid SS MM_SS and HH_MM_SS formats`() {
        assertEquals(45, TimeUtils.parseHMSToSeconds("45"))
        assertEquals(90, TimeUtils.parseHMSToSeconds("01:30"))
        assertEquals(3723, TimeUtils.parseHMSToSeconds("1:02:03"))
        assertEquals(125, TimeUtils.parseHMSToSeconds(" 2:05 "))
    }

    @Test
    fun `parseHMSToSeconds rejects empty segments invalid characters and integer overflow`() {
        assertNull(TimeUtils.parseHMSToSeconds(""))
        assertNull(TimeUtils.parseHMSToSeconds("   "))
        assertNull(TimeUtils.parseHMSToSeconds("1::30"))
        assertNull(TimeUtils.parseHMSToSeconds(":30"))
        assertNull(TimeUtils.parseHMSToSeconds("30:"))
        assertNull(TimeUtils.parseHMSToSeconds("::"))
        assertNull(TimeUtils.parseHMSToSeconds("1a:30"))
        assertNull(TimeUtils.parseHMSToSeconds("-5"))
        assertNull(TimeUtils.parseHMSToSeconds("1:2:3:4"))
        assertNull(TimeUtils.parseHMSToSeconds("999999999:00:00"))
        assertNull(TimeUtils.parseHMSToSeconds("35791395:00"))
        assertNull(TimeUtils.parseHMSToSeconds("99999999999999"))
    }

    @Test
    fun `formatSecondsToHMS clamps negative seconds and distance helpers reject non-finite values`() {
        assertEquals("00:00", TimeUtils.formatSecondsToHMS(-5))
        assertEquals("1:01:05", TimeUtils.formatSecondsToHMS(3665))
        assertNull(TimeUtils.distanceToMeters(Float.NaN, DistanceUnit.MILES))
        assertEquals(0f, TimeUtils.metersToDistance(Float.POSITIVE_INFINITY, DistanceUnit.KILOMETERS), 0f)
        assertEquals("0", TimeUtils.formatDistance(Float.NaN, DistanceUnit.METERS))
        assertEquals("0.00", TimeUtils.formatMiles(Float.NaN))
    }
}
