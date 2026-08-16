package com.example.gymtime.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.LocalAppColors
import com.example.gymtime.util.PlateCalculator
import com.example.gymtime.util.PlateLoadout
import kotlin.math.abs
import kotlin.math.max

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
    onUseWeight: (Float) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LocalAppColors.current.backgroundCanvas,
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
            modifier = Modifier.fillMaxHeight(0.94f)
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
    initialMode: PlateCalculatorMode = PlateCalculatorMode.TARGET
) {
    val colors = LocalAppColors.current
    val accent = MaterialTheme.colorScheme.primary
    val safeBarWeight = barWeight.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeLoadingSides = loadingSides.coerceAtLeast(1)
    val plates = remember(availablePlates) {
        PlateCalculator.sanitizePlateOptions(availablePlates)
    }
    var mode by remember { mutableStateOf(initialMode) }
    val startingWeight = initialWeight
        .takeIf { it.isFinite() && it >= safeBarWeight }
        ?: safeBarWeight
    var targetText by remember { mutableStateOf(PlateCalculator.formatWeight(startingWeight)) }
    val startingLoadout = remember(startingWeight, plates, safeBarWeight, safeLoadingSides) {
        PlateCalculator.calculatePlates(startingWeight, plates, safeBarWeight, safeLoadingSides)
    }
    val manualPlates = remember {
        mutableStateListOf<Float>().apply {
            if (initialMode == PlateCalculatorMode.BUILD) addAll(startingLoadout.platesPerSide)
        }
    }
    var manualModeVisited by remember { mutableStateOf(initialMode == PlateCalculatorMode.BUILD) }

    val target = targetText.toFloatOrNull()
    val targetLoadout = PlateCalculator.calculatePlates(
        targetWeight = target ?: safeBarWeight,
        availablePlates = plates,
        barWeight = safeBarWeight,
        loadingSides = safeLoadingSides
    )
    val manualTotal = PlateCalculator.calculateTotalWeight(
        platesPerSide = manualPlates,
        barWeight = safeBarWeight,
        loadingSides = safeLoadingSides
    )
    val activeTotal = if (mode == PlateCalculatorMode.TARGET) targetLoadout.totalWeight else manualTotal
    val activePlates = if (mode == PlateCalculatorMode.TARGET) targetLoadout.platesPerSide else manualPlates.toList()
    val canUseWeight = activeTotal.isFinite() && activeTotal >= safeBarWeight &&
        (mode == PlateCalculatorMode.BUILD || target != null)

    fun selectMode(newMode: PlateCalculatorMode) {
        if (newMode == mode) return
        if (newMode == PlateCalculatorMode.BUILD && !manualModeVisited) {
            manualPlates.clear()
            manualPlates.addAll(targetLoadout.platesPerSide)
            manualModeVisited = true
        } else if (newMode == PlateCalculatorMode.TARGET) {
            targetText = PlateCalculator.formatWeight(manualTotal)
        }
        mode = newMode
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.backgroundCanvas)
    ) {
        CalculatorHeader(onDismiss = onDismiss)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SetupSummary(
                barWeight = safeBarWeight,
                loadingSides = safeLoadingSides,
                plateCount = plates.size,
                onNavigateToSettings = onNavigateToSettings
            )

            ModeSwitch(
                selected = mode,
                onSelect = ::selectMode
            )

            AnimatedContent(
                targetState = mode,
                label = "plate-calculator-mode"
            ) { selectedMode ->
                when (selectedMode) {
                    PlateCalculatorMode.TARGET -> TargetMode(
                        targetText = targetText,
                        onTargetChange = { value ->
                            val parsed = value.toFloatOrNull()
                            if (value.length <= 8 && value.count { it == '.' } <= 1 &&
                                value.all { it.isDigit() || it == '.' } &&
                                (parsed == null || parsed <= PlateCalculator.MAX_SUPPORTED_WEIGHT)
                            ) {
                                targetText = value
                            }
                        },
                        onStep = { direction ->
                            val increment = (plates.minOrNull() ?: 2.5f) * safeLoadingSides
                            val current = targetText.toFloatOrNull() ?: safeBarWeight
                            val next = max(safeBarWeight, current + increment * direction)
                            targetText = PlateCalculator.formatWeight(next)
                        },
                        target = target,
                        loadout = targetLoadout,
                        barWeight = safeBarWeight,
                        loadingSides = safeLoadingSides,
                        hasPlateOptions = plates.isNotEmpty()
                    )

                    PlateCalculatorMode.BUILD -> BuildMode(
                        totalWeight = manualTotal,
                        platesPerSide = manualPlates,
                        availablePlates = plates,
                        barWeight = safeBarWeight,
                        loadingSides = safeLoadingSides,
                        onAddPlate = { plate ->
                            val nextTotal = PlateCalculator.calculateTotalWeight(
                                platesPerSide = manualPlates + plate,
                                barWeight = safeBarWeight,
                                loadingSides = safeLoadingSides
                            )
                            if (nextTotal < PlateCalculator.MAX_SUPPORTED_WEIGHT || manualTotal < PlateCalculator.MAX_SUPPORTED_WEIGHT) {
                                manualPlates.add(plate)
                            }
                        },
                        onRemovePlate = { plate ->
                            val index = manualPlates.indexOfLast { it == plate }
                            if (index >= 0) manualPlates.removeAt(index)
                        },
                        onClear = { manualPlates.clear() },
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
        }

        Surface(
            color = colors.backgroundCanvas,
            shadowElevation = 12.dp
        ) {
            Button(
                onClick = { onUseWeight(activeTotal) },
                enabled = canUseWeight,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = colors.inputBackground,
                    disabledContentColor = colors.textTertiary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("plate_use_weight")
            ) {
                Text(
                    text = "Use ${PlateCalculator.formatWeight(activeTotal)} lb",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CalculatorHeader(onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 10.dp, top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PLATE CALCULATOR",
                color = colors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "Load faster. Lift with confidence.",
                color = colors.textSecondary,
                fontSize = 14.sp
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Close plate calculator", tint = colors.textPrimary)
        }
    }
    HorizontalDivider(color = colors.inputBackground)
}

@Composable
private fun SetupSummary(
    barWeight: Float,
    loadingSides: Int,
    plateCount: Int,
    onNavigateToSettings: () -> Unit
) {
    val colors = LocalAppColors.current
    Surface(
        color = colors.surfaceCards,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.inputBackground),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SetupValue("BAR", "${PlateCalculator.formatWeight(barWeight)} lb", Modifier.weight(1f))
            SetupValue("LOAD", "$loadingSides ${if (loadingSides == 1) "side" else "sides"}", Modifier.weight(1f))
            SetupValue(
                "PLATES",
                "$plateCount ${if (plateCount == 1) "size" else "sizes"}",
                Modifier.weight(1f)
            )
            IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Settings, contentDescription = "Plate settings", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SetupValue(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(modifier = modifier) {
        Text(label, color = colors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        Text(value, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ModeSwitch(selected: PlateCalculatorMode, onSelect: (PlateCalculatorMode) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.inputBackground, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeOption(
            label = "Find plates",
            selected = selected == PlateCalculatorMode.TARGET,
            modifier = Modifier.weight(1f).testTag("plate_mode_target"),
            onClick = { onSelect(PlateCalculatorMode.TARGET) }
        )
        ModeOption(
            label = "Build weight",
            selected = selected == PlateCalculatorMode.BUILD,
            modifier = Modifier.weight(1f).testTag("plate_mode_build"),
            onClick = { onSelect(PlateCalculatorMode.BUILD) }
        )
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Surface(
        color = if (selected) colors.surfaceCards else Color.Transparent,
        shape = RoundedCornerShape(11.dp),
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
            .height(44.dp)
            .clickable(role = Role.Tab, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.primary else colors.textSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun TargetMode(
    targetText: String,
    onTargetChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    target: Float?,
    loadout: PlateLoadout,
    barWeight: Float,
    loadingSides: Int,
    hasPlateOptions: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("TARGET WEIGHT")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StepButton(Icons.Rounded.Remove, "Decrease target", "target_decrease", onClick = { onStep(-1) })
                Surface(
                    color = LocalAppColors.current.surfaceCards,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, LocalAppColors.current.inputBackground),
                    modifier = Modifier.weight(1f).height(82.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        BasicTextField(
                            value = targetText,
                            onValueChange = onTargetChange,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = SolidColor(LocalAppColors.current.cursor),
                            textStyle = TextStyle(
                                color = LocalAppColors.current.textPrimary,
                                fontSize = 42.sp,
                                lineHeight = 44.sp,
                                textAlign = TextAlign.End,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("target_weight_input")
                        )
                        Text("lb", color = LocalAppColors.current.textSecondary, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                StepButton(Icons.Rounded.Add, "Increase target", "target_increase", onClick = { onStep(1) })
            }
        }

        TotalHero(
            total = loadout.totalWeight,
            status = targetStatus(target, loadout),
            exact = target != null && loadout.isExact
        )

        BarbellVisual(platesPerSide = loadout.platesPerSide, barWeight = barWeight)

        LoadoutBreakdown(
            platesPerSide = loadout.platesPerSide,
            loadingSides = loadingSides,
            emptyMessage = when {
                !hasPlateOptions -> "No plate sizes enabled. Open settings to choose your plates."
                target == null -> "Enter a target weight to calculate your loadout."
                target <= barWeight -> "Bar only — no plates needed."
                else -> "No plates needed."
            }
        )
    }
}

@Composable
private fun BuildMode(
    totalWeight: Float,
    platesPerSide: List<Float>,
    availablePlates: List<Float>,
    barWeight: Float,
    loadingSides: Int,
    onAddPlate: (Float) -> Unit,
    onRemovePlate: (Float) -> Unit,
    onClear: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TotalHero(
            total = totalWeight,
            status = if (platesPerSide.isEmpty()) "Bar only" else "${platesPerSide.size} plate${if (platesPerSide.size == 1) "" else "s"} per side",
            exact = true
        )
        BarbellVisual(platesPerSide = platesPerSide, barWeight = barWeight)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("ADD A PLATE", Modifier.weight(1f))
                if (platesPerSide.isNotEmpty()) {
                    Text(
                        "Clear",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClear)
                            .testTag("plate_clear")
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
            if (availablePlates.isEmpty()) {
                EmptyPlateSettings(onNavigateToSettings)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    availablePlates.forEach { plate ->
                        PlateRackButton(plate = plate, onClick = { onAddPlate(plate) })
                    }
                }
                Text(
                    "Tap a plate to add one to each ${if (loadingSides == 1) "loaded side" else "side"}.",
                    color = LocalAppColors.current.textTertiary,
                    fontSize = 12.sp
                )
            }
        }

        ManualLoadoutControls(
            platesPerSide = platesPerSide,
            loadingSides = loadingSides,
            onRemovePlate = onRemovePlate,
            onAddPlate = onAddPlate
        )
    }
}

@Composable
private fun TotalHero(total: Float, status: String, exact: Boolean) {
    val colors = LocalAppColors.current
    Surface(
        color = colors.surfaceCards,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (exact) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else colors.inputBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("LOADED TOTAL", color = colors.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        PlateCalculator.formatWeight(total),
                        color = colors.textPrimary,
                        fontSize = 38.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.testTag("plate_total")
                    )
                    Text(" lb", color = colors.textSecondary, fontSize = 16.sp, modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            Surface(
                color = if (exact) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else colors.inputBackground,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    status,
                    color = if (exact) MaterialTheme.colorScheme.primary else colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun BarbellVisual(platesPerSide: List<Float>, barWeight: Float) {
    val colors = LocalAppColors.current
    val shownPlates = platesPerSide.sortedDescending().take(8)
    Surface(
        color = colors.inputBackground.copy(alpha = 0.55f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().height(116.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(5.dp).background(colors.textTertiary, RoundedCornerShape(50)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState(), reverseScrolling = true),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        shownPlates.reversed().forEach { PlateOnBar(it) }
                    }
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(18.dp)
                            .background(colors.textSecondary, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(PlateCalculator.formatWeight(barWeight), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        shownPlates.forEach { PlateOnBar(it) }
                    }
                }
            }
            if (platesPerSide.size > shownPlates.size) {
                Text("+${platesPerSide.size - shownPlates.size} more per side", color = colors.textTertiary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PlateOnBar(weight: Float) {
    val color = Color(PlateCalculator.getPlateColor(weight))
    val height = plateHeight(weight)
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .width(if (weight >= 25f) 12.dp else 9.dp)
            .height(height)
            .background(color, RoundedCornerShape(3.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.22f), RoundedCornerShape(3.dp))
    )
}

private fun plateHeight(weight: Float): Dp = when {
    weight >= 45f -> 68.dp
    weight >= 35f -> 62.dp
    weight >= 25f -> 56.dp
    weight >= 15f -> 49.dp
    weight >= 10f -> 43.dp
    weight >= 5f -> 36.dp
    else -> 30.dp
}

@Composable
private fun LoadoutBreakdown(platesPerSide: List<Float>, loadingSides: Int, emptyMessage: String) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("LOAD PER SIDE")
        if (platesPerSide.isEmpty()) {
            Text(emptyMessage, color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            val groups = platesPerSide.groupingBy { it }.eachCount().toList().sortedByDescending { it.first }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groups.forEach { (plate, count) ->
                    PlateCountChip(plate, count)
                }
            }
            Text(
                "Repeat this load on ${if (loadingSides == 1) "the loaded side" else "both sides"}.",
                color = colors.textTertiary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PlateCountChip(plate: Float, count: Int) {
    val color = Color(PlateCalculator.getPlateColor(plate))
    Surface(
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(
                "  ${PlateCalculator.formatWeight(plate)} lb  × $count",
                color = LocalAppColors.current.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun PlateRackButton(plate: Float, onClick: () -> Unit) {
    val color = Color(PlateCalculator.getPlateColor(plate))
    val textColor = if (plate < 45f) Color.Black else Color.White
    Box(
        modifier = Modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("plate_add_${PlateCalculator.formatWeight(plate)}"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(PlateCalculator.formatWeight(plate), color = textColor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text("+ EACH", color = textColor.copy(alpha = 0.78f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ManualLoadoutControls(
    platesPerSide: List<Float>,
    loadingSides: Int,
    onRemovePlate: (Float) -> Unit,
    onAddPlate: (Float) -> Unit
) {
    val colors = LocalAppColors.current
    val groups = platesPerSide.groupingBy { it }.eachCount().toList().sortedByDescending { it.first }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("LOADED PER SIDE")
        if (groups.isEmpty()) {
            Text("Bar only. Tap a plate above to start building.", color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            groups.forEach { (plate, count) ->
                Surface(color = colors.surfaceCards, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(12.dp).background(Color(PlateCalculator.getPlateColor(plate)), CircleShape))
                        Text(
                            "${PlateCalculator.formatWeight(plate)} lb",
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 10.dp).weight(1f)
                        )
                        SmallCountButton(
                            icon = Icons.Rounded.Remove,
                            description = "Remove ${PlateCalculator.formatWeight(plate)} pound plate",
                            tag = "plate_remove_${PlateCalculator.formatWeight(plate)}",
                            onClick = { onRemovePlate(plate) }
                        )
                        Text("$count", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(38.dp))
                        SmallCountButton(
                            icon = Icons.Rounded.Add,
                            description = "Add another ${PlateCalculator.formatWeight(plate)} pound plate",
                            tag = "plate_increment_${PlateCalculator.formatWeight(plate)}",
                            onClick = { onAddPlate(plate) }
                        )
                    }
                }
            }
            Text(
                "Counts shown per side · ${if (loadingSides == 1) "1 side loaded" else "$loadingSides sides loaded"}",
                color = colors.textTertiary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun EmptyPlateSettings(onNavigateToSettings: () -> Unit) {
    val colors = LocalAppColors.current
    Surface(color = colors.surfaceCards, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("No plate sizes are enabled.", color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                "Open settings",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onNavigateToSettings).padding(8.dp)
                    .testTag("plate_open_settings")
            )
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tag: String,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Surface(
        color = colors.surfaceCards,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, colors.inputBackground),
        modifier = Modifier.size(54.dp).clickable(role = Role.Button, onClick = onClick).testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SmallCountButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LocalAppColors.current.inputBackground)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = LocalAppColors.current.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp,
        modifier = modifier
    )
}

private fun targetStatus(target: Float?, loadout: PlateLoadout): String {
    if (target == null) return "Enter target"
    if (loadout.isExact) return "Exact match"
    val difference = loadout.totalWeight - target
    return if (difference < 0f) {
        "${PlateCalculator.formatWeight(abs(difference))} lb under"
    } else {
        "${PlateCalculator.formatWeight(difference)} lb over"
    }
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
            modifier = Modifier.fillMaxSize()
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
            modifier = Modifier.fillMaxSize(),
            initialMode = PlateCalculatorMode.BUILD
        )
    }
}
