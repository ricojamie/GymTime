package com.example.gymtime.ui.components.plate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
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
import com.example.gymtime.ui.components.PlateCalculatorMode
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.LocalLoggerActionColors
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
    val action = LocalLoggerActionColors.current
    val focusManager = LocalFocusManager.current
    Column(modifier.fillMaxWidth().background(scheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Load your bar", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold, color = scheme.onSurface)
                Text("${PlateCalculator.formatWeight(state.barWeight)} lb bar · ${state.loadingSides} loaded " +
                    if (state.loadingSides == 1) "side" else "sides",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            IconButton(onClick = { focusManager.clearFocus(); onNavigateToSettings() }, modifier = Modifier.size(48.dp).testTag("plate_open_settings")) {
                Icon(Icons.Rounded.Settings, contentDescription = "Bar and plate settings", tint = scheme.onSurfaceVariant)
            }
            IconButton(onClick = { focusManager.clearFocus(); onDismiss() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close plate calculator", tint = scheme.onSurface)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
            Surface(
                color = scheme.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InventoryLimitRow(state.usePlateInventory, onUsePlateInventoryChange)
                    TextButton(
                        onClick = { focusManager.clearFocus(); onInventoryExpand() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("plate_inventory_expand")
                            .semantics { stateDescription = if (state.inventoryExpanded) "Expanded" else "Collapsed" }
                    ) {
                        Text(if (state.inventoryExpanded) "Hide my plate counts" else "Set my plate counts",
                            modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        Icon(if (state.inventoryExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null)
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
                    } else if (state.usePlateInventory && state.plateInventory.values.none { it > 0 }) {
                        Text("No plates counted yet. Add your counts above; the calculator currently uses the bare bar.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.size(4.dp))
        }
        Surface(color = scheme.background, shadowElevation = 2.dp) {
            Column {
                if (state.inventoryEditing || state.inventoryPending) {
                    Text(if (state.inventoryEditing) "Finish editing plate counts with Done" else "Updating your plates…",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center)
                }
                Button(
                    onClick = onUseWeight,
                    enabled = state.canUse,
                    colors = ButtonDefaults.buttonColors(containerColor = action.fill, contentColor = action.onFill),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                        .heightIn(min = 56.dp).testTag("plate_use_weight")
                ) {
                    Text("Use ${PlateCalculator.formatWeight(state.loadedTotal)} lb", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CalculatorModes(mode: PlateCalculatorMode, onSelect: (PlateCalculatorMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val action = LocalLoggerActionColors.current
    Row(Modifier.fillMaxWidth().background(scheme.surfaceContainerHigh, RoundedCornerShape(18.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PlateCalculatorMode.entries.forEach { option ->
            val isSelected = mode == option
            Surface(
                color = if (isSelected) action.fill else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .testTag(if (option == PlateCalculatorMode.TARGET) "plate_mode_target" else "plate_mode_build")
                    .semantics { selected = isSelected }
            ) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(if (option == PlateCalculatorMode.TARGET) "Find plates" else "Build weight",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = if (isSelected) action.onFill else scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun TargetInput(text: String, onChange: (String) -> Unit, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CountButton(Icons.Rounded.Remove, "Decrease target weight", "target_decrease", true) { onStep(-1) }
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            label = { Text("Target weight") },
            suffix = { Text("lb") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.weight(1f).testTag("target_weight_input")
        )
        CountButton(Icons.Rounded.Add, "Increase target weight", "target_increase", true) { onStep(1) }
    }
}

@Composable
private fun LoadedWeight(state: PlateCalculatorUiState) {
    val scheme = MaterialTheme.colorScheme
    val isTarget = state.mode == PlateCalculatorMode.TARGET
    val exact = !isTarget || state.target != null && state.targetLoadout.isExact
    val difference = state.target?.let { state.loadedTotal - it }
    val status = when {
        !isTarget -> "Built by you"
        state.target == null -> "Enter a target"
        exact -> "Exact match"
        difference != null && difference < 0f -> "${PlateCalculator.formatWeight(abs(difference))} lb under target"
        else -> "${PlateCalculator.formatWeight(abs(difference ?: 0f))} lb over target"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text("ACTUALLY LOADED", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(PlateCalculator.formatWeight(state.loadedTotal), style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold, color = scheme.onSurface, modifier = Modifier.testTag("plate_total"))
            Text("lb", style = MaterialTheme.typography.titleLarge, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp))
        }
        Surface(color = if (exact) scheme.primaryContainer else scheme.surfaceContainerHigh,
            shape = RoundedCornerShape(12.dp)) {
            Text(status, style = MaterialTheme.typography.labelLarge,
                color = if (exact) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        if (isTarget && !exact && state.target != null) {
            Text(if (state.usePlateInventory) "Closest load with your available plates. The button uses this actual total."
                else "Closest load with enabled sizes. The button uses this actual total.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlateRack(state: PlateCalculatorUiState, onAdd: (Float) -> Unit, onSettings: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ADD TO ${if (state.loadingSides == 1) "LOADED SIDE" else "EACH SIDE"}",
            style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        if (state.availablePlates.isEmpty()) {
            TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Choose your plate sizes") }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.availablePlates.forEach { plate ->
                    val enabled = canAdd(state, plate)
                    val weight = PlateCalculator.formatWeight(plate)
                    val physicalColor = Color(PlateCalculator.getPlateColor(plate))
                    val textColor = if (physicalColor.luminance() > 0.179f) Color.Black else Color.White
                    val outOfStock = state.usePlateInventory && !PlateCalculator.canAddPlate(
                        state.manualPlates, plate, state.loadingSides, state.plateInventory)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape)
                                .background(if (enabled) physicalColor else scheme.surfaceContainerHigh)
                                .border(2.dp, if (enabled) physicalColor else scheme.outlineVariant, CircleShape)
                                .clickable(enabled = enabled, role = Role.Button) { onAdd(plate) }
                                .testTag("plate_add_$weight")
                                .semantics { contentDescription = "Add $weight pound plate to ${state.loadingSides} loaded sides" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(weight, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                                color = if (enabled) textColor else scheme.onSurfaceVariant)
                        }
                        Text(when {
                            outOfStock && state.loadingSides == 2 -> "No pair"
                            outOfStock -> "No stock"
                            !enabled -> "At limit"
                            else -> "lb"
                        },
                            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Text(if (state.loadingSides == 1) "Each tap adds one physical plate." else
            "Each tap adds one plate per side (${state.loadingSides} individual plates).",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadList(state: PlateCalculatorUiState, onAdd: (Float) -> Unit, onRemove: (Float) -> Unit, onClear: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val groups = state.loadedPlates.groupingBy { it }.eachCount().toList().sortedByDescending { it.first }
    val build = state.mode == PlateCalculatorMode.BUILD
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.loadingSides == 1) "ON THE LOADED SIDE" else "ON EACH SIDE",
                style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (build && groups.isNotEmpty()) {
                TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp).testTag("plate_clear")) { Text("Clear") }
            }
        }
        if (groups.isEmpty()) {
            Text(when {
                state.availablePlates.isEmpty() -> "No plate sizes enabled. Choose sizes in settings."
                state.mode == PlateCalculatorMode.TARGET && state.target == null -> "Enter a target above to find a load."
                build -> "Bare bar. Tap a plate to start building."
                else -> "Bare bar — no plates in this load."
            }, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        } else if (build) {
            groups.forEach { (plate, count) ->
                Surface(color = scheme.surface, shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, scheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        PlateDot(plate)
                        Text("${PlateCalculator.formatWeight(plate)} lb", style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        CountButton(Icons.Rounded.Remove, "Remove ${PlateCalculator.formatWeight(plate)} pound plate from each side",
                            "plate_remove_${PlateCalculator.formatWeight(plate)}", true) { onRemove(plate) }
                        Text("$count", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface,
                            textAlign = TextAlign.Center, modifier = Modifier.width(32.dp))
                        CountButton(Icons.Rounded.Add, "Add ${PlateCalculator.formatWeight(plate)} pound plate to each side",
                            "plate_increment_${PlateCalculator.formatWeight(plate)}", canAdd(state, plate)) { onAdd(plate) }
                    }
                }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { (plate, count) ->
                    Surface(color = scheme.surface, shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, scheme.outlineVariant)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlateDot(plate)
                            Text("${PlateCalculator.formatWeight(plate)} lb × $count", style = MaterialTheme.typography.titleSmall,
                                color = scheme.onSurface, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        if (groups.isNotEmpty()) {
            Text("${PlateCalculator.formatWeight(state.loadedPlates.sum())} lb in plates per loaded side · " +
                "${state.loadedPlates.size * state.loadingSides} individual plates total",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

private fun canAdd(state: PlateCalculatorUiState, plate: Float): Boolean =
    PlateCalculator.canAddPlate(state.manualPlates, plate, state.loadingSides, state.plateInventory.takeIf { state.usePlateInventory }) &&
        state.barWeight.toDouble() + (state.manualPlates.sumOf { it.toDouble() } + plate) * state.loadingSides <= PlateCalculator.MAX_SUPPORTED_WEIGHT

@Composable
private fun PlateDot(plate: Float) {
    Box(Modifier.size(12.dp).background(Color(PlateCalculator.getPlateColor(plate)), CircleShape)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
}

@Composable
private fun CountButton(icon: ImageVector, description: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(48.dp).testTag(tag).background(scheme.surfaceContainerHigh, RoundedCornerShape(14.dp))) {
        Icon(icon, contentDescription = description,
            tint = if (enabled) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.55f))
    }
}

@Preview(name = "Olympic load dark", widthDp = 375, heightDp = 820, showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Olympic load light", widthDp = 375, heightDp = 820, showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun LoadedCalculatorPreview() {
    IronLogTheme(darkMode = androidx.compose.foundation.isSystemInDarkTheme(), newUiEnabled = true) {
        PreviewCalculator(185f)
    }
}

@Preview(name = "Bare bar", widthDp = 375, heightDp = 820, showBackground = true)
@Composable
private fun BareCalculatorPreview() {
    IronLogTheme(newUiEnabled = true) { PreviewCalculator(45f) }
}

@Preview(name = "One sleeve", widthDp = 375, heightDp = 820, showBackground = true)
@Composable
private fun OneSleevePreview() {
    IronLogTheme(newUiEnabled = true) { PreviewCalculator(95f, sides = 1) }
}

@Composable
private fun PreviewCalculator(weight: Float, sides: Int = 2) {
    com.example.gymtime.ui.components.PlateCalculatorContent(
        initialWeight = weight, barWeight = 45f, availablePlates = listOf(45f, 35f, 25f, 10f, 5f, 2.5f),
        loadingSides = sides, onDismiss = {}, onNavigateToSettings = {}, onUseWeight = {},
        modifier = Modifier.heightIn(min = 780.dp)
    )
}
