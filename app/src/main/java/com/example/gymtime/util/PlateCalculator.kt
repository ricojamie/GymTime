package com.example.gymtime.util

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
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
    /** Inventory counts are total individual plates, not pairs. */
    const val MAX_PLATE_COUNT = 100
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
     * @param plateInventory Total individual plates available per denomination; null means unlimited.
     * @return PlateLoadout with plates per side and whether it's exact match
     */
    fun calculatePlates(
        targetWeight: Float,
        availablePlates: List<Float>,
        barWeight: Float = 45f,
        loadingSides: Int = 2,
        plateInventory: Map<Float, Int>? = null
    ): PlateLoadout {
        val safeBarWeight = barWeight.takeIf { it.isFinite() }
            ?.coerceIn(0f, MAX_SUPPORTED_WEIGHT)
            ?.let { fromUnits(toUnits(it)) }
            ?: 0f
        val safeTargetWeight = targetWeight.takeIf { it.isFinite() }
            ?.coerceIn(0f, MAX_SUPPORTED_WEIGHT)
            ?: safeBarWeight
        val safeLoadingSides = loadingSides.coerceIn(1, MAX_LOADING_SIDES)
        val inventory = plateInventory?.let(::sanitizePlateInventory)
        val plateOptions = sanitizePlateOptions(availablePlates).filter { plate ->
            inventory == null || (inventory[plate] ?: 0) / safeLoadingSides > 0
        }

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
        val maximumLoadUnits = toUnits(MAX_SUPPORTED_WEIGHT - safeBarWeight) / unitDivisor
        val availablePerSide = plateOptions.map { plate ->
            (inventory?.get(plate) ?: 0) / safeLoadingSides
        }
        val searchLimit = if (inventory == null) {
            min(maximumLoadUnits, ceil(normalizedTarget).toInt() + (normalizedOptionUnits.values.minOrNull() ?: 0))
        } else {
            // A finite supply of the smallest plate may run out. The closest load
            // above the target can be as far as one largest stocked plate away.
            val inventoryUnits = plateOptions.indices.sumOf { index ->
                normalizedOptionUnits.getValue(plateOptions[index]).toLong() * availablePerSide[index]
            }
            minOf(
                maximumLoadUnits.toLong(),
                inventoryUnits,
                ceil(normalizedTarget).toLong() + normalizedOptionUnits.values.max()
            ).toInt()
        }
        val selectedPlates = if (inventory == null) {
            solveUnlimited(plateOptions, normalizedOptionUnits, targetPlateUnits, unitDivisor, searchLimit)
        } else {
            solveBounded(plateOptions, normalizedOptionUnits, availablePerSide, targetPlateUnits, unitDivisor, searchLimit)
        }

        val actualTotalWeight = calculateTotalWeight(
            platesPerSide = selectedPlates,
            barWeight = safeBarWeight,
            loadingSides = safeLoadingSides
        )

        return PlateLoadout(
            platesPerSide = selectedPlates,
            totalWeight = actualTotalWeight,
            isExact = abs(actualTotalWeight - safeTargetWeight) < EXACT_TOLERANCE
        )
    }

    private fun solveUnlimited(
        plateOptions: List<Float>,
        normalizedOptionUnits: Map<Float, Int>,
        targetPlateUnits: Int,
        unitDivisor: Int,
        searchLimit: Int
    ): List<Float> {
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

        return buildList {
            var amount = bestAmount
            while (amount > 0) {
                val plateIndex = previousPlateIndex[amount]
                if (plateIndex !in plateOptions.indices) break
                add(plateOptions[plateIndex])
                amount = previousAmount[amount]
            }
        }.sortedDescending()

    }

    /**
     * Bounded minimum-coin DP with monotone queues, followed by divide-and-conquer
     * reconstruction. Working arrays are bounded by the search amount rather than
     * the number of denominations, and reconstruction cannot reuse finite stock.
     */
    private fun solveBounded(
        plateOptions: List<Float>,
        optionUnits: Map<Float, Int>,
        availablePerSide: List<Int>,
        targetPlateUnits: Int,
        unitDivisor: Int,
        searchLimit: Int
    ): List<Float> {
        val units = plateOptions.map { optionUnits.getValue(it) }
        val unreachable = Int.MAX_VALUE / 4
        val minimumCounts = boundedMinimumCounts(units, availablePerSide, 0, plateOptions.size, searchLimit)
        val bestAmount = (0..searchLimit).asSequence()
            .filter { minimumCounts[it] < unreachable }
            .minWithOrNull(
                compareBy<Int> { abs(it * unitDivisor - targetPlateUnits) }
                    .thenBy { if (it * unitDivisor <= targetPlateUnits) 0 else 1 }
                    .thenBy { minimumCounts[it] }
            ) ?: 0
        return buildList {
            reconstructBounded(plateOptions, units, availablePerSide, 0, plateOptions.size, bestAmount, this)
        }.sortedDescending()
    }

    private fun boundedMinimumCounts(
        optionUnits: List<Int>,
        capacities: List<Int>,
        startIndex: Int,
        endIndex: Int,
        searchLimit: Int
    ): IntArray {
        val unreachable = Int.MAX_VALUE / 4
        var previous = IntArray(searchLimit + 1) { unreachable }.also { it[0] = 0 }
        var current = IntArray(searchLimit + 1)
        val queueIndices = IntArray(searchLimit + 1)
        val queueScores = IntArray(searchLimit + 1)
        for (plateIndex in startIndex until endIndex) {
            val units = optionUnits[plateIndex]
            val capacity = capacities[plateIndex]
            if (capacity == 0 || units > searchLimit) continue
            current.fill(unreachable)
            for (residue in 0..min(units - 1, searchLimit)) {
                var head = 0
                var tail = 0
                var ordinal = 0
                var amount = residue
                while (amount <= searchLimit) {
                    while (head < tail && queueIndices[head] < ordinal - capacity) head++
                    if (previous[amount] < unreachable) {
                        val score = previous[amount] - ordinal
                        // Equal-cost loads prefer fewer of this (smaller) plate.
                        while (head < tail && queueScores[tail - 1] >= score) tail--
                        queueIndices[tail] = ordinal
                        queueScores[tail] = score
                        tail++
                    }
                    if (head < tail) {
                        current[amount] = ordinal + queueScores[head]
                    }
                    ordinal++
                    amount += units
                }
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous
    }

    private fun reconstructBounded(
        plates: List<Float>,
        units: List<Int>,
        capacities: List<Int>,
        startIndex: Int,
        endIndex: Int,
        amount: Int,
        result: MutableList<Float>
    ) {
        if (amount == 0 || startIndex >= endIndex) return
        if (endIndex - startIndex == 1) {
            val count = amount / units[startIndex]
            check(amount % units[startIndex] == 0 && count <= capacities[startIndex])
            repeat(count) { result.add(plates[startIndex]) }
            return
        }
        val middle = (startIndex + endIndex) / 2
        // Split arrays live only inside this helper; recursive branches retain no
        // amount-sized arrays from earlier reconstruction levels.
        val leftAmount = findBoundedSplit(units, capacities, startIndex, middle, endIndex, amount)
        reconstructBounded(plates, units, capacities, startIndex, middle, leftAmount, result)
        reconstructBounded(plates, units, capacities, middle, endIndex, amount - leftAmount, result)
    }

    private fun findBoundedSplit(
        units: List<Int>,
        capacities: List<Int>,
        startIndex: Int,
        middle: Int,
        endIndex: Int,
        amount: Int
    ): Int {
        val unreachable = Int.MAX_VALUE / 4
        val left = boundedMinimumCounts(units, capacities, startIndex, middle, amount)
        val right = boundedMinimumCounts(units, capacities, middle, endIndex, amount)
        var bestAmount = 0
        var bestCount = unreachable
        for (leftAmount in 0..amount) {
            if (left[leftAmount] >= unreachable || right[amount - leftAmount] >= unreachable) continue
            val count = left[leftAmount] + right[amount - leftAmount]
            if (count <= bestCount) {
                bestCount = count
                bestAmount = leftAmount
            }
        }
        check(bestCount < unreachable)
        return bestAmount
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
            .mapNotNull(::normalizePlateWeight)
            .distinct()
            .sortedDescending()
            .toList()

    /** A denomination is identified at the same hundredth precision as loading math. */
    fun normalizePlateWeight(weight: Float): Float? =
        weight.takeIf { it.isFinite() && it in MIN_SUPPORTED_PLATE..MAX_SUPPORTED_PLATE }
            ?.let { fromUnits(toUnits(it)) }

    /** Canonical aliases do not create extra physical stock. */
    fun sanitizePlateInventory(inventory: Map<Float, Int>): Map<Float, Int> = buildMap {
        inventory.forEach { (weight, count) ->
            val plate = normalizePlateWeight(weight) ?: return@forEach
            val safeCount = count.coerceIn(0, MAX_PLATE_COUNT)
            put(plate, min(get(plate) ?: safeCount, safeCount))
        }
    }

    fun maxPlatesPerSide(
        weight: Float,
        loadingSides: Int = 2,
        plateInventory: Map<Float, Int>? = null
    ): Int {
        val plate = normalizePlateWeight(weight) ?: return 0
        if (plateInventory == null) return Int.MAX_VALUE
        return (sanitizePlateInventory(plateInventory)[plate] ?: 0) / loadingSides.coerceIn(1, MAX_LOADING_SIDES)
    }

    fun canAddPlate(
        platesPerSide: List<Float>,
        weight: Float,
        loadingSides: Int = 2,
        plateInventory: Map<Float, Int>? = null
    ): Boolean {
        val plate = normalizePlateWeight(weight) ?: return false
        val used = platesPerSide.count { normalizePlateWeight(it) == plate }
        return used < maxPlatesPerSide(plate, loadingSides, plateInventory)
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
            formatWeight(it)
        }
    }

    fun formatWeight(weight: Float): String {
        val safeWeight = weight.takeIf { it.isFinite() }?.coerceIn(0f, MAX_SUPPORTED_WEIGHT) ?: 0f
        val rounded = fromUnits(toUnits(safeWeight))
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
