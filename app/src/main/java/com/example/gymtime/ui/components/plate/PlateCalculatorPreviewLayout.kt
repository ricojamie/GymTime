package com.example.gymtime.ui.components.plate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogRecessedWell
import com.example.gymtime.ui.components.IronLogSculptedKeycap
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.components.KeycapHaptic
import com.example.gymtime.ui.components.PlateCalculatorMode
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.MachinedPressIndication
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.ui.theme.anodizedMicroGrain
import com.example.gymtime.ui.theme.obsidianAmbientBackdrop
import com.example.gymtime.ui.theme.tabular
import com.example.gymtime.util.PlateCalculator
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current
    val focusManager = LocalFocusManager.current
    val hasEnabledPlateStock = remember(state.availablePlates, state.plateInventory) {
        state.availablePlates.any { (state.plateInventory[it] ?: 0) > 0 }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .obsidianAmbientBackdrop(accent = surfaces.accent, isDark = surfaces.isDark)
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
                    style = IronLogType.telemetryLabel(MaterialTheme.typography.bodySmall),
                    color = scheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    haptics.keyTap()
                    focusManager.clearFocus()
                    onNavigateToSettings()
                },
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
                onClick = {
                    haptics.tick()
                    focusManager.clearFocus()
                    onDismiss()
                },
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
                shape = IronLogRadius.PlateShape,
                contentPadding = PaddingValues(IronLogSpace.ItemGap),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                modifier = Modifier.animateContentSize(
                    IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize)
                )
            ) {
                InventoryLimitRow(state.usePlateInventory, onUsePlateInventoryChange)
                TextButton(
                    onClick = {
                        haptics.tick()
                        focusManager.clearFocus()
                        onInventoryExpand()
                    },
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
                AnimatedVisibility(
                    visible = state.inventoryExpanded,
                    enter = if (reducedMotion) {
                        fadeIn(tween(80))
                    } else {
                        fadeIn(IronMotion.EffectDefault) + expandVertically(IronMotion.SpatialLayoutSize)
                    },
                    exit = if (reducedMotion) {
                        fadeOut(tween(80))
                    } else {
                        fadeOut(IronMotion.EffectFast) + shrinkVertically(IronMotion.SpatialLayoutSize)
                    }
                ) {
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
                }
                if (!state.inventoryExpanded && state.usePlateInventory && !hasEnabledPlateStock) {
                    Text(
                        text = "No plates counted yet. Add your counts above; the calculator currently uses the bare bar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaces.glassDockFill)
                .anodizedMicroGrain(alpha = if (surfaces.isDark) 0.02f else 0.012f)
                .drawBehind {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, surfaces.rimTop, Color.Transparent)
                        ),
                        start = Offset(0f, 0.5.dp.toPx()),
                        end = Offset(size.width, 0.5.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                    onClick = {
                        haptics.confirm()
                        onUseWeight()
                    },
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

/**
 * Recessed machined segmented control matching `IronLogSegmentedControl` physics and visuals
 * while preserving the exact `plate_mode_target` / `plate_mode_build` `testTag`s.
 */
@Composable
private fun CalculatorModes(mode: PlateCalculatorMode, onSelect: (PlateCalculatorMode) -> Unit) {
    val surfaces = LocalIronLogSurfaces.current
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current
    val options = PlateCalculatorMode.entries
    val selectedIndex = options.indexOf(mode).coerceAtLeast(0)

    val pixelThumbSpec = remember { IronMotion.magneticDetent<Float>(visibilityThreshold = 0.5f) }
    val segmentBounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val thumbX = remember { Animatable(0f) }
    val thumbW = remember { Animatable(0f) }
    var hasInitializedThumb by remember { mutableStateOf(false) }

    val targetBounds = segmentBounds[selectedIndex]
    LaunchedEffect(selectedIndex, targetBounds, reducedMotion) {
        val (targetLeft, targetWidth) = targetBounds ?: return@LaunchedEffect
        if (!hasInitializedThumb || reducedMotion) {
            thumbX.snapTo(targetLeft)
            thumbW.snapTo(targetWidth)
            hasInitializedThumb = true
        } else {
            coroutineScope {
                launch { thumbX.animateTo(targetLeft, pixelThumbSpec) }
                launch { thumbW.animateTo(targetWidth, pixelThumbSpec) }
            }
        }
    }

    val trackBorderColor = remember(surfaces) {
        if (surfaces.isDark) Color.Black.copy(alpha = 0.72f) else Color(0xFFBFC5CE)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IronLogRadius.CapsuleShape)
            .background(surfaces.wellFill)
            .border(width = IronLogBorder.Hairline, color = trackBorderColor, shape = IronLogRadius.CapsuleShape)
            .drawWithCache {
                val insetPx = 3.dp.toPx()
                val shadowHeight = 4.dp.toPx().coerceAtMost(size.height * 0.35f)
                val shadowSize = Size(size.width, shadowHeight)
                val topShadowBrush = Brush.verticalGradient(
                    colors = listOf(surfaces.wellInnerShadow, Color.Transparent),
                    startY = 0f,
                    endY = shadowHeight
                )
                val thumbTop = insetPx
                val thumbHeight = (size.height - insetPx * 2f).coerceAtLeast(1f)
                val corner = CornerRadius(thumbHeight * 0.5f, thumbHeight * 0.5f)
                val thumbFillBrush = Brush.verticalGradient(
                    colors = listOf(surfaces.plateElevatedTop, surfaces.plateElevatedBottom),
                    startY = thumbTop,
                    endY = thumbTop + thumbHeight
                )
                val thumbRimBrush = IronLogBorder.specularRimBrush(surfaces, highlighted = true)
                val thumbRimStroke = Stroke(width = IronLogBorder.Thin.toPx())

                onDrawWithContent {
                    drawRect(brush = topShadowBrush, size = shadowSize)

                    val activeBounds = segmentBounds[selectedIndex]
                    val x = if (hasInitializedThumb) thumbX.value else (activeBounds?.first ?: 0f)
                    val w = if (hasInitializedThumb) thumbW.value else (activeBounds?.second ?: 0f)
                    if (w > 1f) {
                        val topLeft = Offset(insetPx + x, thumbTop)
                        val thumbSize = Size(w, thumbHeight)
                        drawRoundRect(
                            brush = thumbFillBrush,
                            topLeft = topLeft,
                            size = thumbSize,
                            cornerRadius = corner
                        )
                        drawRoundRect(
                            brush = thumbRimBrush,
                            topLeft = topLeft,
                            size = thumbSize,
                            cornerRadius = corner,
                            style = thumbRimStroke
                        )
                    }

                    drawContent()
                }
            }
            .padding(3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val textColorState = animateColorAsState(
                    targetValue = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                    animationSpec = if (reducedMotion) snap() else IronMotion.effectFast(),
                    label = "plateModeColor_$index"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = IronLogSpace.TouchTargetMin)
                        .onPlaced { coords ->
                            val left = coords.positionInParent().x
                            val width = coords.size.width.toFloat()
                            val prev = segmentBounds[index]
                            if (prev == null || prev.first != left || prev.second != width) {
                                segmentBounds[index] = left to width
                            }
                        }
                        .clip(IronLogRadius.CapsuleShape)
                        .testTag(if (option == PlateCalculatorMode.TARGET) "plate_mode_target" else "plate_mode_build")
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (!isSelected) {
                                    haptics.segmentTick()
                                    onSelect(option)
                                }
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected) {
                        IronLogLedPip(color = surfaces.ledGlow, size = 5.dp)
                    }
                    BasicText(
                        text = if (option == PlateCalculatorMode.TARGET) "Find plates" else "Build weight",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        ),
                        color = { textColorState.value },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetInput(text: String, onChange: (String) -> Unit, onStep: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
        CountButton(Icons.Rounded.Remove, "Decrease target weight", "target_decrease", true) { onStep(-1) }
        IronLogRecessedWell(
            active = focused,
            accentColor = surfaces.accent,
            shape = IronLogRadius.KeycapShape,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "TARGET WEIGHT",
                    style = IronLogType.technicalEyebrow(),
                    color = if (focused) surfaces.accent else scheme.onSurfaceVariant,
                    maxLines = 1
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = onChange,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = IronLogType.monumentalNumeral(MaterialTheme.typography.headlineLarge).copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = scheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(surfaces.accent),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focused = it.isFocused }
                            .testTag("target_weight_input"),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (text.isEmpty()) {
                                    Text(
                                        text = "—",
                                        style = IronLogType.monumentalNumeral(
                                            MaterialTheme.typography.headlineLarge
                                        ).copy(fontWeight = FontWeight.ExtraBold),
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    Text(
                        text = "lb",
                        style = IronLogType.telemetryLabel(MaterialTheme.typography.labelLarge),
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
        CountButton(Icons.Rounded.Add, "Increase target weight", "target_increase", true) { onStep(1) }
    }
}

@Composable
private fun LoadedWeight(state: PlateCalculatorUiState) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
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

    IronLogCard(
        highlighted = isTarget && state.target != null && exact,
        spotlight = true,
        showBorder = true,
        shape = IronLogRadius.PlateShape,
        contentPadding = PaddingValues(IronLogSpace.CardPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isTarget && state.target != null && exact) {
                IronLogLedPip(color = surfaces.ledGlow, size = 5.dp)
            }
            Text(
                text = "ACTUALLY LOADED",
                style = IronLogType.technicalEyebrow(),
                color = if (isTarget && state.target != null && exact) surfaces.accent else scheme.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            Box(
                modifier = Modifier
                    .testTag("plate_total")
                    .semantics(mergeDescendants = true) {}
            ) {
                OdometerText(
                    text = PlateCalculator.formatWeight(state.loadedTotal),
                    style = IronLogType.monumentalNumeral(MaterialTheme.typography.displayMedium).copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = scheme.onSurface
                )
            }
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
    val haptics = LocalIronHaptics.current
    Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
        Text(
            text = "ADD TO ${if (state.loadingSides == 1) "LOADED SIDE" else "EACH SIDE"}",
            style = IronLogType.technicalEyebrow(),
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
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressIndication = remember {
                        MachinedPressIndication(
                            pressedScale = 0.91f,
                            highlightAlpha = 0.14f,
                            cornerRadius = 28.dp
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(64.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = pressIndication,
                                    enabled = enabled,
                                    role = Role.Button
                                ) {
                                    haptics.keyTap()
                                    onAdd(plate)
                                }
                                .background(if (enabled) physicalColor else scheme.surfaceContainerHigh)
                                .border(
                                    width = 1.5.dp,
                                    brush = if (enabled) {
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.42f),
                                                physicalColor,
                                                Color.Black.copy(alpha = 0.45f)
                                            )
                                        )
                                    } else {
                                        SolidColor(scheme.outlineVariant)
                                    },
                                    shape = CircleShape
                                )
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
                            style = IronLogType.telemetryLabel(MaterialTheme.typography.labelSmall),
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
    val haptics = LocalIronHaptics.current
    val groups = remember(state.loadedPlates) {
        state.loadedPlates.groupingBy { it }.eachCount().toList().sortedByDescending { it.first }
    }
    val build = state.mode == PlateCalculatorMode.BUILD
    Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (state.loadingSides == 1) "ON THE LOADED SIDE" else "ON EACH SIDE",
                style = IronLogType.technicalEyebrow(),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (build && groups.isNotEmpty()) {
                TextButton(
                    onClick = {
                        haptics.tick()
                        onClear()
                    },
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
                shape = IronLogRadius.PlateShape
            ) {
                groups.forEachIndexed { index, (plate, count) ->
                    if (index > 0) {
                        IronLogDivider()
                    }
                    val weightLabel = PlateCalculator.formatWeight(plate)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PlateDot(plate)
                        Text(
                            text = "$weightLabel lb",
                            style = MaterialTheme.typography.titleMedium.tabular(),
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            modifier = Modifier
                                .padding(start = 4.dp)
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
                            fontWeight = FontWeight.Bold,
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
                    IronLogCard(
                        modifier = Modifier.width(124.dp),
                        showBorder = true,
                        shape = IronLogRadius.KeycapShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PlateDot(plate)
                            Text(
                                text = "${PlateCalculator.formatWeight(plate)} lb × $count",
                                style = MaterialTheme.typography.titleSmall.tabular(),
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                maxLines = 1
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
                style = IronLogType.telemetryLabel(MaterialTheme.typography.bodySmall),
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
    IronLogSculptedKeycap(
        onClick = onClick,
        enabled = enabled,
        hapticType = KeycapHaptic.TICK,
        shape = IronLogRadius.KeycapShape,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .size(IronLogSpace.TouchTargetMin)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.45f)
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
