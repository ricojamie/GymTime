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
    private const val UNREACHABLE = Int.MAX_VALUE / 4

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
        val rawOptionUnits = IntArray(plateOptions.size) { index ->
            max(1, toUnits(plateOptions[index] * safeLoadingSides))
        }
        var unitDivisor = rawOptionUnits[0]
        for (i in 1 until rawOptionUnits.size) {
            unitDivisor = greatestCommonDivisor(unitDivisor, rawOptionUnits[i])
        }
        val normalizedOptionUnits = IntArray(plateOptions.size) { index ->
            rawOptionUnits[index] / unitDivisor
        }
        var minNormalizedUnit = normalizedOptionUnits[0]
        var maxNormalizedUnit = normalizedOptionUnits[0]
        for (i in 1 until normalizedOptionUnits.size) {
            val u = normalizedOptionUnits[i]
            if (u < minNormalizedUnit) minNormalizedUnit = u
            if (u > maxNormalizedUnit) maxNormalizedUnit = u
        }

        val normalizedTarget = targetPlateUnits.toDouble() / unitDivisor
        val maximumLoadUnits = toUnits(MAX_SUPPORTED_WEIGHT - safeBarWeight) / unitDivisor
        val availablePerSide = IntArray(plateOptions.size) { index ->
            (inventory?.get(plateOptions[index]) ?: 0) / safeLoadingSides
        }
        val searchLimit = if (inventory == null) {
            min(maximumLoadUnits, ceil(normalizedTarget).toInt() + minNormalizedUnit)
        } else {
            // A finite supply of the smallest plate may run out. The closest load
            // above the target can be as far as one largest stocked plate away.
            var inventoryUnits = 0L
            for (index in plateOptions.indices) {
                inventoryUnits += normalizedOptionUnits[index].toLong() * availablePerSide[index]
            }
            minOf(
                maximumLoadUnits.toLong(),
                inventoryUnits,
                ceil(normalizedTarget).toLong() + maxNormalizedUnit
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
        normalizedOptionUnits: IntArray,
        targetPlateUnits: Int,
        unitDivisor: Int,
        searchLimit: Int
    ): List<Float> {
        val minimumPlateCount = IntArray(searchLimit + 1) { UNREACHABLE }
        val previousAmount = IntArray(searchLimit + 1) { -1 }
        val previousPlateIndex = IntArray(searchLimit + 1) { -1 }
        minimumPlateCount[0] = 0

        for (amount in 1..searchLimit) {
            for (index in normalizedOptionUnits.indices) {
                val plateUnits = normalizedOptionUnits[index]
                if (plateUnits <= amount && minimumPlateCount[amount - plateUnits] + 1 < minimumPlateCount[amount]) {
                    minimumPlateCount[amount] = minimumPlateCount[amount - plateUnits] + 1
                    previousAmount[amount] = amount - plateUnits
                    previousPlateIndex[amount] = index
                }
            }
        }

        val bestAmount = selectBestAmount(
            minimumCounts = minimumPlateCount,
            targetPlateUnits = targetPlateUnits,
            unitDivisor = unitDivisor,
            searchLimit = searchLimit
        )

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

    private fun selectBestAmount(
        minimumCounts: IntArray,
        targetPlateUnits: Int,
        unitDivisor: Int,
        searchLimit: Int
    ): Int {
        var hasBest = false
        var bestAmount = 0
        var bestDistance = 0
        var bestSidePreference = 0
        var bestCount = UNREACHABLE

        for (amount in 0..searchLimit) {
            val count = minimumCounts[amount]
            if (count >= UNREACHABLE) continue

            val loadedUnits = amount * unitDivisor
            val distance = abs(loadedUnits - targetPlateUnits)
            val sidePreference = if (loadedUnits <= targetPlateUnits) 0 else 1

            val isBetter = !hasBest || when {
                distance < bestDistance -> true
                distance > bestDistance -> false
                sidePreference < bestSidePreference -> true
                sidePreference > bestSidePreference -> false
                else -> count < bestCount
            }
            if (isBetter) {
                hasBest = true
                bestAmount = amount
                bestDistance = distance
                bestSidePreference = sidePreference
                bestCount = count
            }
        }
        return bestAmount
    }

    private class BoundedScratch(capacity: Int) {
        val left = IntArray(capacity)
        val right = IntArray(capacity)
        val work = IntArray(capacity)
        val queueIndices = IntArray(capacity)
        val queueScores = IntArray(capacity)
    }

    /**
     * Bounded minimum-coin DP with monotone queues, followed by divide-and-conquer
     * reconstruction. Working arrays are bounded by the search amount rather than
     * the number of denominations, and reconstruction cannot reuse finite stock.
     */
    private fun solveBounded(
        plateOptions: List<Float>,
        units: IntArray,
        availablePerSide: IntArray,
        targetPlateUnits: Int,
        unitDivisor: Int,
        searchLimit: Int
    ): List<Float> {
        val scratch = BoundedScratch(searchLimit + 1)
        boundedMinimumCountsInto(
            target = scratch.left,
            scratch = scratch,
            optionUnits = units,
            capacities = availablePerSide,
            startIndex = 0,
            endIndex = plateOptions.size,
            searchLimit = searchLimit
        )
        val bestAmount = selectBestAmount(
            minimumCounts = scratch.left,
            targetPlateUnits = targetPlateUnits,
            unitDivisor = unitDivisor,
            searchLimit = searchLimit
        )
        return buildList {
            reconstructBounded(
                plates = plateOptions,
                units = units,
                capacities = availablePerSide,
                startIndex = 0,
                endIndex = plateOptions.size,
                amount = bestAmount,
                scratch = scratch,
                result = this
            )
        }.sortedDescending()
    }

    private fun boundedMinimumCountsInto(
        target: IntArray,
        scratch: BoundedScratch,
        optionUnits: IntArray,
        capacities: IntArray,
        startIndex: Int,
        endIndex: Int,
        searchLimit: Int
    ) {
        target[0] = 0
        for (i in 1..searchLimit) target[i] = UNREACHABLE

        var previous = target
        var current = scratch.work
        val queueIndices = scratch.queueIndices
        val queueScores = scratch.queueScores

        for (plateIndex in startIndex until endIndex) {
            val units = optionUnits[plateIndex]
            val capacity = capacities[plateIndex]
            if (capacity == 0 || units > searchLimit) continue
            for (i in 0..searchLimit) current[i] = UNREACHABLE
            for (residue in 0..min(units - 1, searchLimit)) {
                var head = 0
                var tail = 0
                var ordinal = 0
                var amount = residue
                while (amount <= searchLimit) {
                    while (head < tail && queueIndices[head] < ordinal - capacity) head++
                    if (previous[amount] < UNREACHABLE) {
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

        if (previous !== target) {
            System.arraycopy(previous, 0, target, 0, searchLimit + 1)
        }
    }

    private fun reconstructBounded(
        plates: List<Float>,
        units: IntArray,
        capacities: IntArray,
        startIndex: Int,
        endIndex: Int,
        amount: Int,
        scratch: BoundedScratch,
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
        val leftAmount = findBoundedSplit(units, capacities, startIndex, middle, endIndex, amount, scratch)
        reconstructBounded(plates, units, capacities, startIndex, middle, leftAmount, scratch, result)
        reconstructBounded(plates, units, capacities, middle, endIndex, amount - leftAmount, scratch, result)
    }

    private fun findBoundedSplit(
        units: IntArray,
        capacities: IntArray,
        startIndex: Int,
        middle: Int,
        endIndex: Int,
        amount: Int,
        scratch: BoundedScratch
    ): Int {
        boundedMinimumCountsInto(scratch.left, scratch, units, capacities, startIndex, middle, amount)
        boundedMinimumCountsInto(scratch.right, scratch, units, capacities, middle, endIndex, amount)
        val left = scratch.left
        val right = scratch.right
        var bestAmount = 0
        var bestCount = UNREACHABLE
        for (leftAmount in 0..amount) {
            val leftCount = left[leftAmount]
            val rightCount = right[amount - leftAmount]
            if (leftCount >= UNREACHABLE || rightCount >= UNREACHABLE) continue
            val count = leftCount + rightCount
            if (count <= bestCount) {
                bestCount = count
                bestAmount = leftAmount
            }
        }
        check(bestCount < UNREACHABLE)
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
