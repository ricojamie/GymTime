package com.example.gymtime.ui.components.plate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.components.PlateCalculatorMode
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.theme.tabular
import com.example.gymtime.util.PlateCalculator
import kotlin.math.abs

/** Plain, previewable rendering: no repositories, effects, modal state, or navigation objects. */
@Composable
internal fun PlateCalculatorPreviewLayout(
    state: PlateCalculatorUiState,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onUseWeight: () -> Unit,
    onSelectMode: (PlateCalculatorMode) -> Unit,
    onTargetChange: (String) -> Unit,
    onTargetStep: (Int) -> Unit,
    onAddPlate: (Float) -> Unit,
    onRemovePlate: (Float) -> Unit,
    onClear: () -> Unit,
    onInventoryExpand: () -> Unit,
    onPlateInventoryCountChange: (Float, Int) -> Unit,
    onUsePlateInventoryChange: (Boolean) -> Unit,
    onPlateInventoryCountDelta: ((Float, Int) -> Unit)?,
    onInventoryEditingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val hasEnabledPlateStock = remember(state.availablePlates, state.plateInventory) {
        state.availablePlates.any { (state.plateInventory[it] ?: 0) > 0 }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = IronLogSpace.ScreenHorizontal, end = 4.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Load your bar",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = scheme.onSurface
                )
                Text(
                    text = "${PlateCalculator.formatWeight(state.barWeight)} lb bar · ${state.loadingSides} loaded " +
                        if (state.loadingSides == 1) "side" else "sides",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = scheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { focusManager.clearFocus(); onNavigateToSettings() },
                modifier = Modifier
                    .size(IronLogSpace.TouchTargetMin)
                    .testTag("plate_open_settings")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Bar and plate settings",
                    tint = scheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { focusManager.clearFocus(); onDismiss() },
                modifier = Modifier.size(IronLogSpace.TouchTargetMin)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close plate calculator",
                    tint = scheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = IronLogSpace.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CardPadding)
        ) {
            CalculatorModes(state.mode, onSelectMode)
            if (state.mode == PlateCalculatorMode.TARGET) {
                TargetInput(state.targetText, onTargetChange, onTargetStep)
            }
            LoadedWeight(state)
            OlympicBarbell(state.loadedPlates, state.barWeight, state.loadingSides)
            if (state.mode == PlateCalculatorMode.BUILD) {
                PlateRack(state, onAddPlate, onNavigateToSettings)
            }
            LoadList(state, onAddPlate, onRemovePlate, onClear)

            IronLogCard(
                showBorder = true,
                containerColor = scheme.surfaceContainerLow,
                shape = IronLogRadius.CardShape,
                contentPadding = PaddingValues(IronLogSpace.ItemGap),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                InventoryLimitRow(state.usePlateInventory, onUsePlateInventoryChange)
                TextButton(
                    onClick = { focusManager.clearFocus(); onInventoryExpand() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = IronLogSpace.TouchTargetMin)
                        .testTag("plate_inventory_expand")
                        .semantics { stateDescription = if (state.inventoryExpanded) "Expanded" else "Collapsed" }
                ) {
                    Text(
                        text = if (state.inventoryExpanded) "Hide my plate counts" else "Set my plate counts",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                    Icon(
                        imageVector = if (state.inventoryExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null
                    )
                }
                if (state.inventoryExpanded) {
                    PlateInventoryEditor(
                        availablePlates = state.availablePlates,
                        plateInventory = state.plateInventory,
                        usePlateInventory = state.usePlateInventory,
                        onPlateInventoryCountChange = onPlateInventoryCountChange,
                        onUsePlateInventoryChange = onUsePlateInventoryChange,
                        showLimitSwitch = false,
                        onPlateInventoryCountDelta = onPlateInventoryCountDelta,
                        onEditingChange = onInventoryEditingChange
                    )
                } else if (state.usePlateInventory && !hasEnabledPlateStock) {
                    Text(
                        text = "No plates counted yet. Add your counts above; the calculator currently uses the bare bar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
        }

        Surface(
            color = scheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                IronLogDivider(insetStart = 0.dp)
                if (state.inventoryEditing || state.inventoryPending) {
                    Text(
                        text = if (state.inventoryEditing) "Finish editing plate counts with Done" else "Updating your plates…",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = IronLogSpace.ScreenHorizontal, vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
                IronLogPrimaryButton(
                    text = "Use ${PlateCalculator.formatWeight(state.loadedTotal)} lb",
                    onClick = onUseWeight,
                    enabled = state.canUse,
                    shape = IronLogRadius.ControlShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = IronLogSpace.ScreenHorizontal, vertical = 12.dp)
                        .testTag("plate_use_weight")
                )
            }
        }
    }
}

@Composable
private fun CalculatorModes(mode: PlateCalculatorMode, onSelect: (PlateCalculatorMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val action = LocalLoggerActionColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerHigh, IronLogRadius.ControlShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PlateCalculatorMode.entries.forEach { option ->
            val isSelected = mode == option
            Surface(
                color = if (isSelected) action.fill else Color.Transparent,
                shape = IronLogRadius.MicroShape,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = IronLogSpace.TouchTargetMin)
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .testTag(if (option == PlateCalculatorMode.TARGET) "plate_mode_target" else "plate_mode_build")
                    .semantics { selected = isSelected }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (option == PlateCalculatorMode.TARGET) "Find plates" else "Build weight",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) action.onFill else scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetInput(text: String, onChange: (String) -> Unit, onStep: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
        CountButton(Icons.Rounded.Remove, "Decrease target weight", "target_decrease", true) { onStep(-1) }
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            label = { Text("Target weight") },
            suffix = { Text("lb") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.headlineLarge.tabular().copy(
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            ),
            shape = IronLogRadius.ControlShape,
            modifier = Modifier
                .weight(1f)
                .testTag("target_weight_input")
        )
        CountButton(Icons.Rounded.Add, "Increase target weight", "target_increase", true) { onStep(1) }
    }
}

@Composable
private fun LoadedWeight(state: PlateCalculatorUiState) {
    val scheme = MaterialTheme.colorScheme
    val isTarget = state.mode == PlateCalculatorMode.TARGET
    val exact = !isTarget || (state.target != null && state.targetLoadout.isExact)
    val difference = state.target?.let { state.loadedTotal - it }
    val status = when {
        !isTarget -> "Built by you"
        state.target == null -> "Enter a target"
        exact -> "Exact match"
        difference != null && difference < 0f -> "${PlateCalculator.formatWeight(abs(difference))} lb under target"
        else -> "${PlateCalculator.formatWeight(abs(difference ?: 0f))} lb over target"
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "ACTUALLY LOADED",
            style = IronLogType.sectionEyebrow(),
            color = scheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            Text(
                text = PlateCalculator.formatWeight(state.loadedTotal),
                style = MaterialTheme.typography.displayMedium.tabular(),
                fontWeight = FontWeight.ExtraBold,
                color = scheme.onSurface,
                modifier = Modifier.testTag("plate_total")
            )
            Text(
                text = "lb",
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        IronLogStatusChip(
            label = status,
            tone = if (isTarget && state.target != null && exact) {
                IronLogStatusTone.ACTIVE
            } else {
                IronLogStatusTone.NEUTRAL
            },
            uppercase = false
        )
        if (isTarget && !exact && state.target != null) {
            Text(
                text = if (state.usePlateInventory) {
                    "Closest load with your available plates. The button uses this actual total."
                } else {
                    "Closest load with enabled sizes. The button uses this actual total."
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlateRack(state: PlateCalculatorUiState, onAdd: (Float) -> Unit, onSettings: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
        Text(
            text = "ADD TO ${if (state.loadingSides == 1) "LOADED SIDE" else "EACH SIDE"}",
            style = IronLogType.sectionEyebrow(),
            color = scheme.onSurfaceVariant
        )
        if (state.availablePlates.isEmpty()) {
            TextButton(
                onClick = onSettings,
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
            ) { Text("Choose your plate sizes") }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                state.availablePlates.forEach { plate ->
                    val enabled = canAdd(state, plate)
                    val weight = PlateCalculator.formatWeight(plate)
                    val physicalColor = Color(PlateCalculator.getPlateColor(plate))
                    val textColor = if (physicalColor.luminance() > 0.179f) Color.Black else Color.White
                    val outOfStock = state.usePlateInventory && !PlateCalculator.canAddPlate(
                        state.manualPlates,
                        plate,
                        state.loadingSides,
                        state.plateInventory
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(64.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (enabled) physicalColor else scheme.surfaceContainerHigh)
                                .border(2.dp, if (enabled) physicalColor else scheme.outlineVariant, CircleShape)
                                .clickable(enabled = enabled, role = Role.Button) { onAdd(plate) }
                                .testTag("plate_add_$weight")
                                .semantics {
                                    contentDescription = "Add $weight pound plate to ${state.loadingSides} loaded sides"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = weight,
                                style = MaterialTheme.typography.titleLarge.tabular(),
                                fontWeight = FontWeight.ExtraBold,
                                color = if (enabled) textColor else scheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = when {
                                outOfStock && state.loadingSides == 2 -> "No pair"
                                outOfStock -> "No stock"
                                !enabled -> "At limit"
                                else -> "lb"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Text(
            text = if (state.loadingSides == 1) {
                "Each tap adds one physical plate."
            } else {
                "Each tap adds one plate per side (${state.loadingSides} individual plates)."
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadList(
    state: PlateCalculatorUiState,
    onAdd: (Float) -> Unit,
    onRemove: (Float) -> Unit,
    onClear: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val groups = remember(state.loadedPlates) {
        state.loadedPlates.groupingBy { it }.eachCount().toList().sortedByDescending { it.first }
    }
    val build = state.mode == PlateCalculatorMode.BUILD
    Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (state.loadingSides == 1) "ON THE LOADED SIDE" else "ON EACH SIDE",
                style = IronLogType.sectionEyebrow(),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (build && groups.isNotEmpty()) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier
                        .heightIn(min = IronLogSpace.TouchTargetMin)
                        .testTag("plate_clear")
                ) { Text("Clear") }
            }
        }
        if (groups.isEmpty()) {
            Text(
                text = when {
                    state.availablePlates.isEmpty() -> "No plate sizes enabled. Choose sizes in settings."
                    state.mode == PlateCalculatorMode.TARGET && state.target == null -> "Enter a target above to find a load."
                    build -> "Bare bar. Tap a plate to start building."
                    else -> "Bare bar — no plates in this load."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        } else if (build) {
            IronLogGroupedCard(
                showBorder = true,
                containerColor = scheme.surfaceContainerLow
            ) {
                groups.forEachIndexed { index, (plate, count) ->
                    if (index > 0) {
                        IronLogDivider()
                    }
                    val weightLabel = PlateCalculator.formatWeight(plate)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlateDot(plate)
                        Text(
                            text = "$weightLabel lb",
                            style = MaterialTheme.typography.titleMedium.tabular(),
                            color = scheme.onSurface,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f)
                        )
                        CountButton(
                            icon = Icons.Rounded.Remove,
                            description = "Remove $weightLabel pound plate from each side",
                            tag = "plate_remove_$weightLabel",
                            enabled = true
                        ) { onRemove(plate) }
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.titleMedium.tabular(),
                            color = scheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(32.dp)
                        )
                        CountButton(
                            icon = Icons.Rounded.Add,
                            description = "Add $weightLabel pound plate to each side",
                            tag = "plate_increment_$weightLabel",
                            enabled = canAdd(state, plate)
                        ) { onAdd(plate) }
                    }
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                groups.forEach { (plate, count) ->
                    Surface(
                        color = scheme.surfaceContainerLow,
                        shape = IronLogRadius.ControlShape,
                        border = IronLogBorder.stroke(scheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlateDot(plate)
                            Text(
                                text = "${PlateCalculator.formatWeight(plate)} lb × $count",
                                style = MaterialTheme.typography.titleSmall.tabular(),
                                color = scheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        if (groups.isNotEmpty()) {
            Text(
                text = "${PlateCalculator.formatWeight(state.loadedPlates.sum())} lb in plates per loaded side · " +
                    "${state.loadedPlates.size * state.loadingSides} individual plates total",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = scheme.onSurfaceVariant
            )
        }
    }
}

private fun canAdd(state: PlateCalculatorUiState, plate: Float): Boolean =
    PlateCalculator.canAddPlate(
        state.manualPlates,
        plate,
        state.loadingSides,
        state.plateInventory.takeIf { state.usePlateInventory }
    ) && state.barWeight.toDouble() + (state.manualPlates.sumOf { it.toDouble() } + plate) * state.loadingSides <=
        PlateCalculator.MAX_SUPPORTED_WEIGHT

@Composable
private fun PlateDot(plate: Float) {
    Box(
        Modifier
            .size(12.dp)
            .background(Color(PlateCalculator.getPlateColor(plate)), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}

@Composable
private fun CountButton(
    icon: ImageVector,
    description: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(IronLogSpace.TouchTargetMin)
            .testTag(tag)
            .background(scheme.surfaceContainerHigh, IronLogRadius.ControlShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.55f)
        )
    }
}

@Preview(name = "Olympic load dark", widthDp = 375, heightDp = 820, showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Olympic load light", widthDp = 375, heightDp = 820, showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun LoadedCalculatorPreview() {
    IronLogTheme(darkMode = isSystemInDarkTheme()) {
        PreviewCalculator(185f)
    }
}

@Preview(name = "Bare bar", widthDp = 375, heightDp = 820, showBackground = true)
@Composable
private fun BareCalculatorPreview() {
    IronLogTheme { PreviewCalculator(45f) }
}

@Preview(name = "One sleeve", widthDp = 375, heightDp = 820, showBackground = true)
@Composable
private fun OneSleevePreview() {
    IronLogTheme { PreviewCalculator(95f, sides = 1) }
}

@Composable
private fun PreviewCalculator(weight: Float, sides: Int = 2) {
    com.example.gymtime.ui.components.PlateCalculatorContent(
        initialWeight = weight,
        barWeight = 45f,
        availablePlates = listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f),
        loadingSides = sides,
        onDismiss = {},
        onNavigateToSettings = {},
        onUseWeight = {},
        modifier = Modifier.heightIn(min = 780.dp)
    )
}
