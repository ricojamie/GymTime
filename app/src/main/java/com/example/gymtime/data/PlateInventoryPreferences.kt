package com.example.gymtime.data

import com.example.gymtime.util.PlateCalculator

data class PlateInventorySettings(
    val counts: Map<Float, Int>,
    val enabled: Boolean
)

/** Local preferences encoding; quantities are total individual physical plates. */
object PlateInventoryPreferences {
    private const val MAX_ENCODED_LENGTH = 32_768

    fun decode(encoded: String?): Map<Float, Int> {
        if (encoded.isNullOrBlank() || encoded.length > MAX_ENCODED_LENGTH) return emptyMap()
        val inventory = mutableMapOf<Float, Int>()
        encoded.split(',').forEach { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return@forEach
            val weight = parts[0].trim().toFloatOrNull()
                ?.let(PlateCalculator::normalizePlateWeight) ?: return@forEach
            val count = parts[1].trim().toLongOrNull()
                ?.coerceIn(0L, PlateCalculator.MAX_PLATE_COUNT.toLong())?.toInt() ?: return@forEach
            // Duplicate / rounded aliases cannot inflate the saved stock.
            inventory[weight] = minOf(inventory[weight] ?: count, count)
        }
        return inventory.toSortedMap(compareByDescending { it })
    }

    fun encode(inventory: Map<Float, Int>): String =
        PlateCalculator.sanitizePlateInventory(inventory).toSortedMap(compareByDescending { it })
            .entries.joinToString(",") { (weight, count) -> "${PlateCalculator.formatWeight(weight)}:$count" }

    fun updateCount(encoded: String?, weight: Float, count: Int): String? {
        val plate = PlateCalculator.normalizePlateWeight(weight) ?: return null
        val inventory = decode(encoded).toMutableMap()
        inventory[plate] = count.coerceIn(0, PlateCalculator.MAX_PLATE_COUNT)
        return encode(inventory)
    }

    fun adjustCount(encoded: String?, weight: Float, delta: Int): String? {
        val plate = PlateCalculator.normalizePlateWeight(weight) ?: return null
        val inventory = decode(encoded).toMutableMap()
        val count = ((inventory[plate] ?: 0).toLong() + delta.toLong())
            .coerceIn(0L, PlateCalculator.MAX_PLATE_COUNT.toLong()).toInt()
        inventory[plate] = count
        return encode(inventory)
    }
}
