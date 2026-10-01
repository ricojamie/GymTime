package com.example.gymtime.ui.components.plate

import com.example.gymtime.util.PlateCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualPlateInventoryTest {
    @Test
    fun `stock changes remove excess and disabled plates but preserve the remaining draft`() {
        val actual = validManualPlates(
            draft = listOf(45f, 45f, 25f, 10f, 5f),
            availablePlates = listOf(45f, 25f, 5f),
            loadingSides = 2,
            inventory = mapOf(45f to 3, 25f to 2, 5f to 2),
            barWeight = 45f
        )

        assertEquals(listOf(45f, 25f, 5f), actual)
        assertEquals(195f, PlateCalculator.calculateTotalWeight(actual, 45f, 2), 0f)
    }

    @Test
    fun `single sided setup can use an odd physical plate that cannot make a pair`() {
        val draft = listOf(25f, 25f, 25f)
        val inventory = mapOf(25f to 3)

        assertEquals(listOf(25f), validManualPlates(draft, listOf(25f), 2, inventory))
        assertEquals(draft, validManualPlates(draft, listOf(25f), 1, inventory))
    }

    @Test
    fun `loaded plates cannot create a capped total that conceals an overweight bar`() {
        val actual = validManualPlates(listOf(45f, 45f), listOf(45f), 1, null, barWeight = 9955f)

        assertEquals(listOf(45f), actual)
        assertEquals(10000f, PlateCalculator.calculateTotalWeight(actual, 9955f, 1), 0f)
    }
}
