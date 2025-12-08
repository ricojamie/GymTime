package com.example.gymtime.util

/**
 * Result of plate calculation
 * @param platesPerSide List of plate weights for one side (e.g., [45, 25, 10])
 * @param totalWeight Total calculated weight (bar + all plates)
 * @param isExact Whether the calculated weight exactly matches the target
 */
data class PlateLoadout(
    val platesPerSide: List<Float>,
    val totalWeight: Float,
    val isExact: Boolean
)

/**
 * Calculates the optimal plate configuration to load a barbell
 * Uses greedy algorithm: selects largest available plates first
 */
object PlateCalculator {

    /**
     * Calculate plates needed to reach target weight
     *
     * @param targetWeight Desired total weight (bar + all plates)
     * @param availablePlates List of plate weights available (should be sorted descending)
     * @param barWeight Weight of the bar (default 45 lbs)
     * @param loadingSides Number of sides to load plates on (usually 2)
     * @return PlateLoadout with plates per side and whether it's exact match
     */
    fun calculatePlates(
        targetWeight: Float,
        availablePlates: List<Float>,
        barWeight: Float = 45f,
        loadingSides: Int = 2
    ): PlateLoadout {
        // If target is less than or equal to bar weight, no plates needed
        if (targetWeight <= barWeight) {
            return PlateLoadout(
                platesPerSide = emptyList(),
                totalWeight = barWeight,
                isExact = targetWeight == barWeight
            )
        }

        // Calculate weight needed on plates (excluding bar)
        val weightOnPlates = targetWeight - barWeight

        // Calculate weight needed per side
        val weightPerSide = weightOnPlates / loadingSides

        // Sort plates descending (largest first) for greedy algorithm
        val sortedPlates = availablePlates.sortedDescending()

        // Use greedy algorithm to select plates
        val selectedPlates = mutableListOf<Float>()
        var remainingWeight = weightPerSide

        for (plate in sortedPlates) {
            // Add as many of this plate as possible
            while (remainingWeight >= plate && (remainingWeight - plate) >= -0.01f) {
                selectedPlates.add(plate)
                remainingWeight -= plate
            }
        }

        // Calculate actual total weight achieved
        val actualWeightPerSide = selectedPlates.sum()
        val actualTotalWeight = barWeight + (actualWeightPerSide * loadingSides)

        // Check if we got an exact match (within 0.01 tolerance for floating point)
        val isExact = kotlin.math.abs(actualTotalWeight - targetWeight) < 0.01f

        return PlateLoadout(
            platesPerSide = selectedPlates,
            totalWeight = actualTotalWeight,
            isExact = isExact
        )
    }

    /**
     * Format plate loadout as human-readable string
     * Example: "45 + 25 + 10 + 5" for one side
     */
    fun formatPlateLoadout(loadout: PlateLoadout): String {
        if (loadout.platesPerSide.isEmpty()) {
            return "Bar only"
        }
        return loadout.platesPerSide.joinToString(" + ") {
            if (it % 1.0f == 0f) {
                it.toInt().toString()
            } else {
                it.toString()
            }
        }
    }

    /**
     * Get color for plate based on standard weightlifting plate colors
     * Red = 55 lbs/25 kg
     * Blue = 45 lbs/20 kg
     * Yellow = 35 lbs/15 kg
     * Green = 25 lbs/10 kg
     * White = smaller plates
     */
    fun getPlateColor(weight: Float): Long {
        return when {
            weight >= 55f -> 0xFFE74C3C // Red - 55 lbs
            weight >= 45f -> 0xFF3498DB // Blue - 45 lbs
            weight >= 35f -> 0xFFF39C12 // Yellow - 35 lbs
            weight >= 25f -> 0xFF2ECC71 // Green - 25 lbs
            weight >= 10f -> 0xFFECF0F1 // Light gray - 10 lbs
            else -> 0xFFBDC3C7 // Gray - small plates
        }
    }
}
