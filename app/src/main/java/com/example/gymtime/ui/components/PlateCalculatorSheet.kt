package com.example.gymtime.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.components.plate.PlateCalculatorPreviewContent
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogTheme

internal enum class PlateCalculatorMode {
    TARGET,
    BUILD
}

/**
 * Bidirectional plate calculator. Target mode finds a loadout; Build mode lets
 * the lifter tap physical plates and see the loaded total immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateCalculatorSheet(
    initialWeight: Float,
    barWeight: Float,
    availablePlates: List<Float>,
    loadingSides: Int,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onUseWeight: (Float) -> Unit,
    plateInventory: Map<Float, Int> = emptyMap(),
    usePlateInventory: Boolean = false,
    onPlateInventoryCountChange: (Float, Int) -> Unit = { _, _ -> },
    onUsePlateInventoryChange: (Boolean) -> Unit = {},
    onPlateInventoryCountDelta: ((Float, Int) -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = IronLogRadius.Sheet, topEnd = IronLogRadius.Sheet),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        dragHandle = null
    ) {
        PlateCalculatorContent(
            initialWeight = initialWeight,
            barWeight = barWeight,
            availablePlates = availablePlates,
            loadingSides = loadingSides,
            onDismiss = onDismiss,
            onNavigateToSettings = onNavigateToSettings,
            onUseWeight = onUseWeight,
            modifier = Modifier.fillMaxHeight(0.94f),
            plateInventory = plateInventory,
            usePlateInventory = usePlateInventory,
            onPlateInventoryCountChange = onPlateInventoryCountChange,
            onUsePlateInventoryChange = onUsePlateInventoryChange,
            onPlateInventoryCountDelta = onPlateInventoryCountDelta
        )
    }
}

@Composable
internal fun PlateCalculatorContent(
    initialWeight: Float,
    barWeight: Float,
    availablePlates: List<Float>,
    loadingSides: Int,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onUseWeight: (Float) -> Unit,
    modifier: Modifier = Modifier,
    initialMode: PlateCalculatorMode = PlateCalculatorMode.TARGET,
    plateInventory: Map<Float, Int> = emptyMap(),
    usePlateInventory: Boolean = false,
    onPlateInventoryCountChange: (Float, Int) -> Unit = { _, _ -> },
    onUsePlateInventoryChange: (Boolean) -> Unit = {},
    onPlateInventoryCountDelta: ((Float, Int) -> Unit)? = null
) {
    PlateCalculatorPreviewContent(
        initialWeight = initialWeight,
        barWeight = barWeight,
        availablePlates = availablePlates,
        loadingSides = loadingSides,
        onDismiss = onDismiss,
        onNavigateToSettings = onNavigateToSettings,
        onUseWeight = onUseWeight,
        modifier = modifier,
        initialMode = initialMode,
        plateInventory = plateInventory,
        usePlateInventory = usePlateInventory,
        onPlateInventoryCountChange = onPlateInventoryCountChange,
        onUsePlateInventoryChange = onUsePlateInventoryChange,
        onPlateInventoryCountDelta = onPlateInventoryCountDelta
    )
}

@Preview(widthDp = 390, heightDp = 820, showBackground = true)
@Composable
private fun PlateCalculatorTargetPreview() {
    IronLogTheme {
        PlateCalculatorContent(
            initialWeight = 185f,
            barWeight = 45f,
            availablePlates = listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f),
            loadingSides = 2,
            onDismiss = {},
            onNavigateToSettings = {},
            onUseWeight = {},
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 780.dp)
        )
    }
}

@Preview(widthDp = 390, heightDp = 820, showBackground = true)
@Composable
private fun PlateCalculatorBuildPreview() {
    IronLogTheme {
        PlateCalculatorContent(
            initialWeight = 135f,
            barWeight = 45f,
            availablePlates = listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f),
            loadingSides = 2,
            onDismiss = {},
            onNavigateToSettings = {},
            onUseWeight = {},
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 780.dp),
            initialMode = PlateCalculatorMode.BUILD
        )
    }
}
