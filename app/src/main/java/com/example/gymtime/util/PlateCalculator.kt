package com.example.gymtime.util

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

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
 * Pure plate-loading calculations shared by both calculator directions.
 */
object PlateCalculator {

    private const val WEIGHT_SCALE = 100
    private const val EXACT_TOLERANCE = 0.01f
    const val MAX_SUPPORTED_WEIGHT = 10_000f
    private const val MIN_SUPPORTED_PLATE = 0.01f
    private const val MAX_SUPPORTED_PLATE = 1_000f
    private const val MAX_LOADING_SIDES = 16

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
        val safeBarWeight = barWeight.takeIf { it.isFinite() }
            ?.coerceIn(0f, MAX_SUPPORTED_WEIGHT)
            ?: 0f
        val safeTargetWeight = targetWeight.takeIf { it.isFinite() }
            ?.coerceIn(0f, MAX_SUPPORTED_WEIGHT)
            ?: safeBarWeight
        val safeLoadingSides = loadingSides.coerceIn(1, MAX_LOADING_SIDES)
        val plateOptions = sanitizePlateOptions(availablePlates)

        if (safeTargetWeight <= safeBarWeight || plateOptions.isEmpty()) {
            return PlateLoadout(
                platesPerSide = emptyList(),
                totalWeight = safeBarWeight,
                isExact = abs(safeTargetWeight - safeBarWeight) < EXACT_TOLERANCE
            )
        }

        // Solve in hundredths of a pound so arbitrary enabled denominations remain
        // deterministic. Each selected plate is mirrored across loadingSides.
        val targetPlateUnits = toUnits(safeTargetWeight - safeBarWeight)
        val optionUnits = plateOptions.associateWith { plate ->
            max(1, toUnits(plate * safeLoadingSides))
        }
        val unitDivisor = optionUnits.values.reduce(::greatestCommonDivisor)
        val normalizedOptionUnits = optionUnits.mapValues { (_, units) -> units / unitDivisor }
        val normalizedTarget = targetPlateUnits.toDouble() / unitDivisor
        val searchLimit = ceil(normalizedTarget).toInt() + (normalizedOptionUnits.values.minOrNull() ?: 0)
        val unreachable = Int.MAX_VALUE / 4
        val minimumPlateCount = IntArray(searchLimit + 1) { unreachable }
        val previousAmount = IntArray(searchLimit + 1) { -1 }
        val previousPlateIndex = IntArray(searchLimit + 1) { -1 }
        minimumPlateCount[0] = 0

        for (amount in 1..searchLimit) {
            plateOptions.forEachIndexed { index, plate ->
                val plateUnits = normalizedOptionUnits.getValue(plate)
                if (plateUnits <= amount && minimumPlateCount[amount - plateUnits] + 1 < minimumPlateCount[amount]) {
                    minimumPlateCount[amount] = minimumPlateCount[amount - plateUnits] + 1
                    previousAmount[amount] = amount - plateUnits
                    previousPlateIndex[amount] = index
                }
            }
        }

        val bestAmount = (0..searchLimit)
            .asSequence()
            .filter { minimumPlateCount[it] < unreachable }
            .minWithOrNull(
                compareBy<Int> { abs(it * unitDivisor - targetPlateUnits) }
                    // If two loads are equally close, prefer the safer lower load.
                    .thenBy { if (it * unitDivisor <= targetPlateUnits) 0 else 1 }
                    .thenBy { minimumPlateCount[it] }
            ) ?: 0

        val selectedPlates = buildList {
            var amount = bestAmount
            while (amount > 0) {
                val plateIndex = previousPlateIndex[amount]
                if (plateIndex !in plateOptions.indices) break
                add(plateOptions[plateIndex])
                amount = previousAmount[amount]
            }
        }.sortedDescending()

        val actualTotalWeight = calculateTotalWeight(
            platesPerSide = selectedPlates,
            barWeight = safeBarWeight,
            loadingSides = safeLoadingSides
        )

        val isExact = abs(actualTotalWeight - safeTargetWeight) < EXACT_TOLERANCE

        return PlateLoadout(
            platesPerSide = selectedPlates,
            totalWeight = actualTotalWeight,
            isExact = isExact
        )
    }

    /** Calculates total loaded weight from the plates placed on each side. */
    fun calculateTotalWeight(
        platesPerSide: List<Float>,
        barWeight: Float = 45f,
        loadingSides: Int = 2
    ): Float {
        val safeBarWeight = barWeight.takeIf { it.isFinite() }
            ?.coerceIn(0f, MAX_SUPPORTED_WEIGHT)
            ?: 0f
        val safeLoadingSides = loadingSides.coerceIn(1, MAX_LOADING_SIDES)
        val plateWeightPerSide = platesPerSide
            .asSequence()
            .filter { it.isFinite() && it in MIN_SUPPORTED_PLATE..MAX_SUPPORTED_PLATE }
            .map { it.toDouble() }
            .sum()
        val total = (safeBarWeight.toDouble() + plateWeightPerSide * safeLoadingSides)
            .coerceAtMost(MAX_SUPPORTED_WEIGHT.toDouble())
            .toFloat()
        return fromUnits(toUnits(total))
    }

    /** Returns enabled denominations in the stable order used throughout the UI. */
    fun sanitizePlateOptions(availablePlates: List<Float>): List<Float> =
        availablePlates
            .asSequence()
            .filter { it.isFinite() && it in MIN_SUPPORTED_PLATE..MAX_SUPPORTED_PLATE }
            .distinct()
            .sortedDescending()
            .toList()

    /**
     * Format plate loadout as human-readable string
     * Example: "45 + 25 + 10 + 5" for one side
     */
    fun formatPlateLoadout(loadout: PlateLoadout): String {
        if (loadout.platesPerSide.isEmpty()) {
            return "Bar only"
        }
        return loadout.platesPerSide.joinToString(" + ") {
            formatWeight(it)
        }
    }

    fun formatWeight(weight: Float): String {
        val rounded = fromUnits(toUnits(weight))
        return if (rounded % 1f == 0f) {
            rounded.toInt().toString()
        } else {
            rounded.toString().trimEnd('0').trimEnd('.')
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

    private fun toUnits(weight: Float): Int = (weight * WEIGHT_SCALE).roundToInt().coerceAtLeast(0)

    private fun fromUnits(units: Int): Float = units.toFloat() / WEIGHT_SCALE

    private fun greatestCommonDivisor(left: Int, right: Int): Int {
        var a = left
        var b = right
        while (b != 0) {
            val remainder = a % b
            a = b
            b = remainder
        }
        return a.coerceAtLeast(1)
    }
}
