package com.example.gymtime.ui.components.plate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.gymtime.ui.components.PlateCalculatorMode
import com.example.gymtime.util.PlateCalculator
import com.example.gymtime.util.PlateLoadout
import kotlin.math.roundToInt

internal data class PlateCalculatorUiState(
    val mode: PlateCalculatorMode,
    val barWeight: Float,
    val loadingSides: Int,
    val availablePlates: List<Float>,
    val targetText: String,
    val target: Float?,
    val targetLoadout: PlateLoadout,
    val manualPlates: List<Float>,
    val loadedTotal: Float,
    val plateInventory: Map<Float, Int>,
    val usePlateInventory: Boolean,
    val inventoryExpanded: Boolean,
    val canUse: Boolean,
    val inventoryEditing: Boolean,
    val inventoryPending: Boolean
) {
    val loadedPlates: List<Float>
        get() = if (mode == PlateCalculatorMode.TARGET) targetLoadout.platesPerSide else manualPlates
}

/** Owns only calculator drafts. Persistence and navigation stay with the caller. */
@Composable
internal fun PlateCalculatorPreviewContent(
    initialWeight: Float,
    barWeight: Float,
    availablePlates: List<Float>,
    loadingSides: Int,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onUseWeight: (Float) -> Unit,
    modifier: Modifier,
    initialMode: PlateCalculatorMode,
    plateInventory: Map<Float, Int>,
    usePlateInventory: Boolean,
    onPlateInventoryCountChange: (Float, Int) -> Unit,
    onUsePlateInventoryChange: (Boolean) -> Unit,
    onPlateInventoryCountDelta: ((Float, Int) -> Unit)?
) {
    val safeBar = barWeight.takeIf(Float::isFinite)?.coerceIn(0f, PlateCalculator.MAX_SUPPORTED_WEIGHT) ?: 0f
    val sides = loadingSides.coerceIn(1, 16)
    val options = remember(availablePlates) { PlateCalculator.sanitizePlateOptions(availablePlates) }
    val inventory = plateInventory.takeIf { usePlateInventory }
    val start = initialWeight.takeIf { it.isFinite() && it >= safeBar }
        ?.coerceAtMost(PlateCalculator.MAX_SUPPORTED_WEIGHT) ?: safeBar
    var modeName by rememberSaveable { mutableStateOf(initialMode.name) }
    val mode = PlateCalculatorMode.valueOf(modeName)
    var targetText by rememberSaveable { mutableStateOf(PlateCalculator.formatWeight(start)) }
    var manualVisited by rememberSaveable { mutableStateOf(initialMode == PlateCalculatorMode.BUILD) }
    var inventoryExpanded by rememberSaveable { mutableStateOf(false) }
    var inventoryEditing by remember { mutableStateOf(false) }
    var pendingCounts by remember { mutableStateOf<Map<Float, Int>>(emptyMap()) }
    var pendingLimit by remember { mutableStateOf<Boolean?>(null) }
    val normalizedInventory = remember(plateInventory) { PlateCalculator.sanitizePlateInventory(plateInventory) }
    LaunchedEffect(normalizedInventory, pendingCounts) {
        val remaining = pendingCounts.filter { (plate, count) -> (normalizedInventory[plate] ?: 0) != count }
        if (remaining != pendingCounts) pendingCounts = remaining
    }
    LaunchedEffect(usePlateInventory, pendingLimit) {
        if (pendingLimit == usePlateInventory) pendingLimit = null
    }
    var manualDraft by rememberSaveable {
        mutableStateOf(ArrayList(
            if (initialMode == PlateCalculatorMode.BUILD) {
                PlateCalculator.calculatePlates(start, options, safeBar, sides, inventory).platesPerSide
            } else emptyList()
        ))
    }
    val manual = validManualPlates(manualDraft, options, sides, inventory, safeBar)
    // Constrained snapshot is rendered immediately, before this effect reconciles the local draft.
    // Removing stock/options never briefly presents an unavailable load as usable.
    LaunchedEffect(options, sides, inventory, safeBar) {
        val current = validManualPlates(manualDraft, options, sides, inventory, safeBar)
        if (manualDraft != current) manualDraft = ArrayList(current)
    }
    val target = targetText.toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..PlateCalculator.MAX_SUPPORTED_WEIGHT }
    val targetLoadout = remember(target, options, safeBar, sides, inventory) {
        PlateCalculator.calculatePlates(target ?: safeBar, options, safeBar, sides, inventory)
    }
    val manualTotal = PlateCalculator.calculateTotalWeight(manual, safeBar, sides)
    val total = if (mode == PlateCalculatorMode.TARGET) targetLoadout.totalWeight else manualTotal
    val inventoryPending = pendingCounts.isNotEmpty() || pendingLimit != null
    val canUse = total.isFinite() && total >= safeBar &&
        (mode == PlateCalculatorMode.BUILD || target != null) && !inventoryEditing && !inventoryPending

    fun setPlateCount(plate: Float, count: Int) {
        val key = PlateCalculator.normalizePlateWeight(plate) ?: return
        val safeCount = count.coerceIn(0, PlateCalculator.MAX_PLATE_COUNT)
        pendingCounts = pendingCounts + (key to safeCount)
        onPlateInventoryCountChange(key, safeCount)
    }

    fun addPlate(plate: Float) {
        val current = validManualPlates(manualDraft, options, sides, inventory, safeBar)
        if (PlateCalculator.canAddPlate(current, plate, sides, inventory) &&
            safeBar.toDouble() + (current.sumOf { it.toDouble() } + plate) * sides <= PlateCalculator.MAX_SUPPORTED_WEIGHT
        ) manualDraft = ArrayList(current + plate)
    }

    PlateCalculatorPreviewLayout(
        state = PlateCalculatorUiState(mode, safeBar, sides, options, targetText, target,
            targetLoadout, manual, total, plateInventory, usePlateInventory, inventoryExpanded, canUse,
            inventoryEditing, inventoryPending),
        onDismiss = onDismiss,
        onNavigateToSettings = onNavigateToSettings,
        onUseWeight = {
            if (inventoryEditing || pendingCounts.isNotEmpty() || pendingLimit != null) return@PlateCalculatorPreviewLayout
            // Read the latest draft on the event, rather than the last rendered frame's total.
            if (PlateCalculatorMode.valueOf(modeName) == PlateCalculatorMode.BUILD) {
                val current = validManualPlates(manualDraft, options, sides, inventory, safeBar)
                onUseWeight(PlateCalculator.calculateTotalWeight(current, safeBar, sides))
            } else {
                targetText.toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..PlateCalculator.MAX_SUPPORTED_WEIGHT }?.let {
                    onUseWeight(PlateCalculator.calculatePlates(it, options, safeBar, sides, inventory).totalWeight)
                }
            }
        },
        onSelectMode = { next ->
            if (next.name != modeName) {
                if (next == PlateCalculatorMode.BUILD && !manualVisited) {
                    manualDraft = ArrayList(PlateCalculator.calculatePlates(
                        targetText.toFloatOrNull() ?: safeBar, options, safeBar, sides, inventory).platesPerSide)
                    manualVisited = true
                } else if (next == PlateCalculatorMode.TARGET) {
                    targetText = PlateCalculator.formatWeight(PlateCalculator.calculateTotalWeight(
                        validManualPlates(manualDraft, options, sides, inventory, safeBar), safeBar, sides))
                }
                modeName = next.name
            }
        },
        onTargetChange = { value ->
            val parsed = value.toFloatOrNull()
            if (value.length <= 8 && value.count { it == '.' } <= 1 &&
                value.all { it.isDigit() || it == '.' } &&
                (parsed == null || parsed <= PlateCalculator.MAX_SUPPORTED_WEIGHT)
            ) targetText = value
        },
        onTargetStep = { direction ->
            val increment = (options.minOrNull() ?: 2.5f) * sides
            val next = ((targetText.toFloatOrNull() ?: safeBar) + increment * direction).coerceIn(safeBar, PlateCalculator.MAX_SUPPORTED_WEIGHT)
            targetText = PlateCalculator.formatWeight(next)
        },
        onAddPlate = ::addPlate,
        onRemovePlate = { plate ->
            val next = validManualPlates(manualDraft, options, sides, inventory, safeBar).toMutableList()
            val index = next.indexOfLast { it == plate }
            if (index >= 0) next.removeAt(index)
            manualDraft = ArrayList(next)
        },
        onClear = { manualDraft = ArrayList() },
        onInventoryExpand = { inventoryExpanded = !inventoryExpanded },
        onPlateInventoryCountChange = ::setPlateCount,
        onUsePlateInventoryChange = { enabled ->
            pendingLimit = enabled
            onUsePlateInventoryChange(enabled)
        },
        onPlateInventoryCountDelta = { plate, delta ->
            val key = PlateCalculator.normalizePlateWeight(plate)
            if (key != null) {
                val current = pendingCounts[key] ?: normalizedInventory[key] ?: 0
                val next = (current + delta).coerceIn(0, PlateCalculator.MAX_PLATE_COUNT)
                if (onPlateInventoryCountDelta != null) {
                    pendingCounts = pendingCounts + (key to next)
                    onPlateInventoryCountDelta(key, delta)
                } else setPlateCount(key, next)
            }
        },
        onInventoryEditingChange = { inventoryEditing = it },
        modifier = modifier
    )
}

/** Prune disabled denominations and excess stock without replacing the user's remaining plates. */
internal fun validManualPlates(
    draft: List<Float>,
    availablePlates: List<Float>,
    loadingSides: Int,
    inventory: Map<Float, Int>?,
    barWeight: Float = 0f
): List<Float> {
    val kept = mutableListOf<Float>()
    val sides = loadingSides.coerceIn(1, 16)
    val denominations = availablePlates.associateBy { (it * 100f).roundToInt() }
    val capacities = availablePlates.associateWith { PlateCalculator.maxPlatesPerSide(it, sides, inventory) }
    val counts = mutableMapOf<Float, Int>()
    var plateWeight = 0.0
    draft.forEach { raw ->
        if (!raw.isFinite()) return@forEach
        val plate = denominations[(raw * 100f).roundToInt()] ?: return@forEach
        val count = counts[plate] ?: 0
        if (count < capacities.getValue(plate) &&
            barWeight.toDouble() + (plateWeight + plate) * sides <= PlateCalculator.MAX_SUPPORTED_WEIGHT
        ) {
            kept.add(plate)
            plateWeight += plate
            counts[plate] = count + 1
        }
    }
    return kept
}
