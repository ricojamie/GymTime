package com.example.gymtime.ui.components.plate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gymtime.util.PlateCalculator

/** Reusable, repository-free editor. Stock counts are total individual physical plates, never pairs. */
@Composable
fun PlateInventoryEditor(
    availablePlates: List<Float>,
    plateInventory: Map<Float, Int>,
    usePlateInventory: Boolean,
    onPlateInventoryCountChange: (Float, Int) -> Unit,
    onUsePlateInventoryChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showLimitSwitch: Boolean = true,
    onPlateInventoryCountDelta: ((Float, Int) -> Unit)? = null,
    onEditingChange: (Boolean) -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val plates = PlateCalculator.sanitizePlateOptions(availablePlates)
    val normalizedInventory = remember(plateInventory) { PlateCalculator.sanitizePlateInventory(plateInventory) }
    val editedCounts = remember { mutableStateMapOf<Float, Boolean>() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showLimitSwitch) {
            InventoryLimitRow(usePlateInventory, onUsePlateInventoryChange)
        }
        Text(
            "Count every individual plate, including both sides. Four 45 lb plates means 4 here.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (plates.isEmpty()) {
            Text("Enable plate sizes in Plate settings first.", color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
        plates.forEach { plate ->
            key(plate) {
                val count = normalizedInventory[plate] ?: 0
                InventoryCountRow(
                    plate = plate,
                    count = count,
                    onCountChange = { newCount -> onPlateInventoryCountChange(plate, newCount) },
                    onCountDelta = onPlateInventoryCountDelta?.let { deltaCallback ->
                        { delta: Int -> deltaCallback(plate, delta) }
                    },
                    onEditingChange = { edited ->
                        if (edited) editedCounts[plate] = true else editedCounts.remove(plate)
                        onEditingChange(editedCounts.values.any { it })
                    }
                )
            }
        }
    }
}

@Composable
internal fun InventoryLimitRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Limit to my plates", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Text(if (enabled) "Only use the counts below" else "Calculate with unlimited plates",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onChange,
            modifier = Modifier.testTag("plate_inventory_limit").semantics {
                contentDescription = "Limit calculations to my available plates"
            })
    }
}

@Composable
private fun InventoryCountRow(
    plate: Float,
    count: Int,
    onCountChange: (Int) -> Unit,
    onCountDelta: ((Int) -> Unit)?,
    onEditingChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val weight = PlateCalculator.formatWeight(plate)
    var countText by rememberSaveable(plate) { mutableStateOf(count.toString()) }
    var draftEdited by rememberSaveable(plate) { mutableStateOf(false) }
    var awaitingCount by remember(plate) { mutableStateOf<Int?>(null) }
    var focused by remember { mutableStateOf(false) }
    val latestOnCountChange by rememberUpdatedState(onCountChange)
    val latestOnEditingChange by rememberUpdatedState(onEditingChange)
    val latestCount by rememberUpdatedState(count)
    val focusManager = LocalFocusManager.current

    fun commitDraft() {
        if (!draftEdited) return
        val committed = countText.toIntOrNull()?.coerceIn(0, PlateCalculator.MAX_PLATE_COUNT)
        draftEdited = false
        latestOnEditingChange(false)
        if (committed != null) {
            countText = committed.toString()
            awaitingCount = committed
            latestOnCountChange(committed)
        } else {
            countText = (awaitingCount ?: latestCount).toString()
        }
    }
    DisposableEffect(plate) {
        latestOnEditingChange(draftEdited)
        onDispose {
            // Close, hide, or navigating to settings can remove the field before a
            // focus effect runs. Commit the current draft exactly once on removal.
            commitDraft()
            latestOnEditingChange(false)
        }
    }
    LaunchedEffect(count, focused) {
        // A text replacement is one edit, not a series of stock changes. Commit only
        // after leaving the field. The pending value protects it from the old Flow
        // snapshot while DataStore is saving. A blank draft cancels the edit.
        if (!focused) commitDraft()
        if (awaitingCount == count) awaitingCount = null
        if (!focused && !draftEdited && awaitingCount == null) countText = count.toString()
    }

    fun step(delta: Int) {
        val parsedDraft = countText.toIntOrNull()
        val ownsBaseline = (draftEdited && parsedDraft != null) || awaitingCount != null
        val next = ((parsedDraft ?: awaitingCount ?: count) + delta).coerceIn(0, PlateCalculator.MAX_PLATE_COUNT)
        countText = next.toString()
        draftEdited = false
        latestOnEditingChange(false)
        // A step on an edited count commits its final value once. Clearing focus
        // schedules the effect above, which now sees a clean draft and cannot
        // duplicate a stale absolute write after the step.
        if (ownsBaseline || onCountDelta == null) {
            awaitingCount = next
            onCountChange(next)
        } else {
            onCountDelta(delta)
        }
        focusManager.clearFocus()
    }
    val displayedCount = (countText.toIntOrNull() ?: count).coerceIn(0, PlateCalculator.MAX_PLATE_COUNT)
    Surface(
        color = scheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(Color(PlateCalculator.getPlateColor(plate)), CircleShape))
            Text("$weight lb", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = scheme.onSurface, modifier = Modifier.weight(1f).padding(start = 8.dp))
            IconButton(
                onClick = { step(-1) },
                enabled = displayedCount > 0,
                modifier = Modifier.size(48.dp).testTag("plate_stock_remove_$weight")
            ) { Icon(Icons.Rounded.Remove, contentDescription = "One fewer $weight pound plate") }
            OutlinedTextField(
                value = countText,
                onValueChange = { value ->
                    if (value.length <= 3 && value.all(Char::isDigit)) {
                        val next = (value.toIntOrNull() ?: 0).coerceIn(0, PlateCalculator.MAX_PLATE_COUNT)
                        countText = if (value.toIntOrNull()?.let { it > PlateCalculator.MAX_PLATE_COUNT } == true) {
                            next.toString()
                        } else value
                        draftEdited = true
                        onEditingChange(true)
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.width(64.dp).heightIn(min = 48.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .testTag("plate_stock_count_$weight")
                    .semantics { contentDescription = "Total $weight pound plates available" }
            )
            IconButton(
                onClick = { step(1) },
                enabled = displayedCount < PlateCalculator.MAX_PLATE_COUNT,
                modifier = Modifier.size(48.dp).testTag("plate_stock_add_$weight")
            ) { Icon(Icons.Rounded.Add, contentDescription = "One more $weight pound plate") }
        }
    }
}
