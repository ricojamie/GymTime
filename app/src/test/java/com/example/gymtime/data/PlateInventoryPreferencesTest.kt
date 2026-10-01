package com.example.gymtime.data

import com.example.gymtime.util.PlateCalculator
import org.junit.Assert.*
import org.junit.Test

class PlateInventoryPreferencesTest {
    @Test
    fun `missing empty and oversized saved value safely default to no stock`() {
        assertEquals(emptyMap<Float, Int>(), PlateInventoryPreferences.decode(null))
        assertEquals(emptyMap<Float, Int>(), PlateInventoryPreferences.decode(""))
        assertEquals(emptyMap<Float, Int>(), PlateInventoryPreferences.decode("45:2,".repeat(10_000)))
    }

    @Test
    fun `inventory round trips deterministically including zero and disabled weights`() {
        val stock = mapOf(2.5f to 2, 45f to 8, 25f to 0, 15f to 3)
        val encoded = PlateInventoryPreferences.encode(stock)
        assertEquals("45:8,25:0,15:3,2.5:2", encoded)
        assertEquals(stock, PlateInventoryPreferences.decode(encoded))
    }

    @Test
    fun `malformed entries are ignored and safe quantities are clamped`() {
        val stock = PlateInventoryPreferences.decode(
            "junk,45:2147483647,25:-5,10:9223372036854775807,5:1:2,2.5:nope,NaN:2,Infinity:5,-1:3,0:2,1001:2"
        )
        assertEquals(mapOf(45f to 100, 25f to 0, 10f to 100), stock)
    }

    @Test
    fun `duplicate canonical denominations use conservative counts`() {
        assertEquals(mapOf(2.5f to 2), PlateInventoryPreferences.decode("2.501:8,2.499:2,2.50:4"))
    }

    @Test
    fun `single denomination update preserves all other saved quantities`() {
        val first = PlateInventoryPreferences.updateCount("45:4,25:2,15:3", 25f, 6)
        val second = PlateInventoryPreferences.updateCount(first, 45f, 8)
        assertEquals(mapOf(45f to 8, 25f to 6, 15f to 3), PlateInventoryPreferences.decode(second))
        assertNull(PlateInventoryPreferences.updateCount(second, Float.NaN, 4))
    }

    @Test
    fun `quantity adjustments accumulate and clamp safely even for extreme deltas`() {
        var saved: String? = "45:2,25:4"
        repeat(6) { saved = PlateInventoryPreferences.adjustCount(saved, 45f, 1) }
        assertEquals(mapOf(45f to 8, 25f to 4), PlateInventoryPreferences.decode(saved))
        saved = PlateInventoryPreferences.adjustCount(saved, 45f, Int.MAX_VALUE)
        assertEquals(PlateCalculator.MAX_PLATE_COUNT, PlateInventoryPreferences.decode(saved)[45f])
        saved = PlateInventoryPreferences.adjustCount(saved, 45f, Int.MIN_VALUE)
        assertEquals(0, PlateInventoryPreferences.decode(saved)[45f])
    }
}
