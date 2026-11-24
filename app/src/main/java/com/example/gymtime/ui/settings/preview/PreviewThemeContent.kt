package com.example.gymtime.ui.settings.preview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSecondaryButton
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.components.IronLogTactileSwitch
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.MachinedPressIndication
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.ui.theme.ThemeColors
import com.example.gymtime.ui.theme.ThemeFontOption
import com.example.gymtime.ui.theme.ThemePreset
import com.example.gymtime.ui.theme.fontFamilyForOption
import com.example.gymtime.ui.theme.staggeredEntranceOnce
import com.example.gymtime.ui.theme.tabular

@Immutable
data class PreviewThemeUiState(
    val accentKey: String,
    val customAccentHex: String?,
    val fontKey: String,
    val customFontName: String?,
    val darkMode: Boolean,
    val fontImportError: String? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PreviewThemeContent(
    state: PreviewThemeUiState,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onPreset: (String) -> Unit,
    onCustomAccent: (String) -> Unit,
    onClearCustomAccent: () -> Unit,
    onFont: (String) -> Unit,
    onUploadFont: () -> Unit,
    onClearCustomFont: () -> Unit,
    onDarkMode: (Boolean) -> Unit
) {
    var colorDialogOpen by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val action = LocalLoggerActionColors.current
    val haptics = LocalIronHaptics.current
    val fontOptions = remember { ThemeFontOption.entries.filter { it != ThemeFontOption.CUSTOM } }
    val presetRows = remember { ThemePreset.entries.chunked(2) }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = colors.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Theme", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onHome,
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) { Text("Home") }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = IronLogSpace.ScreenHorizontal,
                end = IronLogSpace.ScreenHorizontal,
                top = IronLogSpace.CompactGap,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.SectionGap)
        ) {
            item(key = "intro") {
                Column(
                    modifier = Modifier.staggeredEntranceOnce("theme", 0),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IronLogLedPip(color = surfaces.ledGlow, size = 5.dp)
                        Text(
                            text = "SURFACE & CHASSIS",
                            style = IronLogType.technicalEyebrow(),
                            color = surfaces.accent
                        )
                    }
                    Text(
                        text = "Make it yours",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Your color. Your type. Your training.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            item(key = "live-preview") {
                IronLogCard(
                    highlighted = true,
                    spotlight = true,
                    showBorder = true,
                    shape = IronLogRadius.PlateShape,
                    contentPadding = PaddingValues(IronLogSpace.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                    modifier = Modifier.staggeredEntranceOnce("theme", 1)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IronLogLedPip(color = surfaces.ledGlow, size = 5.dp, pulsing = true)
                        Text(
                            text = "LIVE PREVIEW",
                            style = IronLogType.technicalEyebrow(),
                            color = surfaces.accent
                        )
                    }
                    Text(
                        text = "Bench press",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OdometerText(
                        text = "100 lb × 8",
                        style = IronLogType.monumentalNumeral(MaterialTheme.typography.headlineLarge).copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = surfaces.accent
                    )
                    Surface(
                        color = action.fill,
                        contentColor = action.onFill,
                        shape = IronLogRadius.ControlShape
                    ) {
                        Text(
                            text = "Log set",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Sample set · changes apply as you choose",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            item(key = "accent-color") {
                PreviewThemeSection(
                    title = "Accent color",
                    modifier = Modifier.staggeredEntranceOnce("theme", 2)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(IronLogSpace.CardPadding)
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            presetRows.forEach { rowPresets ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowPresets.forEach { preset ->
                                        ThemeSwatchCard(
                                            preset = preset,
                                            selected = state.accentKey == preset.storageKey,
                                            darkMode = state.darkMode,
                                            onSelect = {
                                                haptics.segmentTick()
                                                onPreset(preset.storageKey)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowPresets.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        IronLogSecondaryButton(
                            text = if (state.accentKey == "custom") {
                                "Custom · ${state.customAccentHex.orEmpty()} · Edit"
                            } else {
                                "Choose a custom color"
                            },
                            onClick = { colorDialogOpen = true },
                            outlined = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (state.customAccentHex != null) {
                            TextButton(
                                onClick = onClearCustomAccent,
                                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                            ) { Text("Clear custom color") }
                        }
                    }
                }
            }

            item(key = "display") {
                PreviewThemeSection(
                    title = "Display",
                    modifier = Modifier.staggeredEntranceOnce("theme", 3)
                ) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressIndication = remember {
                        MachinedPressIndication(pressedScale = 0.985f, highlightAlpha = 0.05f, cornerRadius = IronLogRadius.Control)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .toggleable(
                                value = state.darkMode,
                                role = Role.Switch,
                                interactionSource = interactionSource,
                                indication = pressIndication,
                                onValueChange = { next ->
                                    haptics.toggle(next)
                                    onDarkMode(next)
                                }
                            )
                            .padding(IronLogSpace.CardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Dark mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (state.darkMode) "Obsidian Telemetry · deep smoked slate" else "Machined Ceramic · anodized silver",
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IronLogTactileSwitch(
                            checked = state.darkMode,
                            onCheckedChange = null,
                            accentColor = surfaces.accent,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                    }
                }
            }

            item(key = "typography") {
                PreviewThemeSection(
                    title = "Typography",
                    modifier = Modifier.staggeredEntranceOnce("theme", 4)
                ) {
                    fontOptions.forEachIndexed { index, option ->
                        if (index > 0) {
                            IronLogDivider()
                        }
                        PreviewFontOption(
                            option = option,
                            selected = state.fontKey == option.storageKey,
                            onSelect = {
                                haptics.segmentTick()
                                onFont(option.storageKey)
                            }
                        )
                    }
                    IronLogDivider(insetStart = 0.dp)
                    Column(
                        modifier = Modifier.padding(IronLogSpace.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                    ) {
                        Text("Custom font", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = state.customFontName ?: "Use a .ttf or .otf file from your device.",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                        ) {
                            IronLogPrimaryButton(
                                text = if (state.customFontName == null) "Choose font" else "Replace font",
                                onClick = onUploadFont
                            )
                            if (state.customFontName != null) {
                                IronLogSecondaryButton(
                                    text = if (state.fontKey == "custom") "Custom selected" else "Use custom",
                                    onClick = { onFont(ThemeFontOption.CUSTOM.storageKey) },
                                    outlined = true
                                )
                                TextButton(
                                    onClick = onClearCustomFont,
                                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                                ) { Text("Clear") }
                            }
                        }
                        state.fontImportError?.let { error ->
                            Text(error, color = colors.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (colorDialogOpen) {
        PreviewThemeColorDialog(
            initialHex = state.customAccentHex ?: previewColorToHex(action.fill),
            onDismiss = { colorDialogOpen = false },
            onSave = {
                onCustomAccent(it)
                colorDialogOpen = false
            }
        )
    }
}

@Composable
private fun PreviewThemeSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
        Text(
            text = title.uppercase(),
            style = IronLogType.technicalEyebrow(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        IronLogGroupedCard(
            showBorder = true,
            shape = IronLogRadius.PlateShape,
            content = content
        )
    }
}

@Composable
private fun ThemeSwatchCard(
    preset: ThemePreset,
    selected: Boolean,
    darkMode: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val surfaces = LocalIronLogSurfaces.current
    val presetScheme = remember(preset) { ThemeColors.getScheme(preset.storageKey) }
    val presetAccent = presetScheme.primaryAccent
    val label = remember(preset) {
        preset.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
    }

    val scaleState = animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = IronMotion.orSnap(reducedMotion, IronMotion.RewardPop),
        label = "swatchScale_${preset.storageKey}"
    )

    val cardTop = remember(darkMode, presetAccent) {
        if (darkMode) lerp(Color(0xFF171A22), presetAccent, 0.045f) else Color.White
    }
    val cardBottom = remember(darkMode, presetAccent) {
        if (darkMode) lerp(Color(0xFF0B0D12), presetAccent, 0.02f) else lerp(Color(0xFFF0F3F7), presetAccent, 0.03f)
    }
    val readableAccent = remember(presetAccent, cardTop, cardBottom, darkMode) {
        readableSwatchAccent(presetAccent, if (darkMode) cardTop else cardBottom)
    }
    val rimBorder = remember(selected, readableAccent, surfaces) {
        BorderStroke(
            width = if (selected) 1.5.dp else IronLogBorder.Hairline,
            brush = Brush.verticalGradient(
                colors = listOf(
                    if (selected) readableAccent.copy(alpha = 0.88f) else readableAccent.copy(alpha = 0.36f),
                    if (surfaces.isDark) Color.Black.copy(alpha = 0.50f) else Color(0xFFC4CAD2)
                )
            )
        )
    }
    val labelColor = if (darkMode) Color(0xFFF3F6FA) else Color(0xFF14171A)
    val subLabelColor = if (darkMode) Color(0xFF9EA6B2) else Color(0xFF4F5661)

    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(pressedScale = 0.95f, highlightAlpha = 0.10f, cornerRadius = IronLogRadius.Control)
    }

    Box(
        modifier = modifier
            .heightIn(min = 76.dp)
            .graphicsLayer {
                val s = scaleState.value
                scaleX = s
                scaleY = s
            }
            .clip(IronLogRadius.ControlShape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = pressIndication,
                onClick = onSelect
            )
            .background(remember(cardTop, cardBottom) { Brush.verticalGradient(listOf(cardTop, cardBottom)) })
            .drawWithCache {
                val glowBrush = Brush.radialGradient(
                    colors = listOf(
                        presetAccent.copy(alpha = if (selected) 0.24f else 0.09f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.18f),
                    radius = size.width * 0.9f
                )
                onDrawBehind {
                    drawRect(brush = glowBrush)
                }
            }
            .border(rimBorder, IronLogRadius.ControlShape)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(presetAccent)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                )
                if (selected) {
                    IronLogLedPip(color = readableAccent, size = 6.dp, pulsing = true)
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(subLabelColor.copy(alpha = 0.35f))
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (selected) "ACTIVE" else "PRESET",
                    style = IronLogType.technicalEyebrow(),
                    color = if (selected) readableAccent else subLabelColor
                )
            }
        }
    }
}

private fun swatchContrast(a: Color, b: Color): Float =
    (max(a.luminance(), b.luminance()) + 0.05f) /
        (min(a.luminance(), b.luminance()) + 0.05f)

private fun readableSwatchAccent(
    accent: Color,
    background: Color,
    minimum: Float = 4.6f
): Color {
    if (swatchContrast(accent, background) >= minimum) return accent
    val destination = if (swatchContrast(Color.Black, background) >= swatchContrast(Color.White, background)) {
        Color.Black
    } else {
        Color.White
    }
    var low = 0f
    var high = 1f
    repeat(18) {
        val middle = (low + high) / 2f
        if (swatchContrast(lerp(accent, destination, middle), background) >= minimum) {
            high = middle
        } else {
            low = middle
        }
    }
    return lerp(accent, destination, high)
}

@Composable
private fun PreviewFontOption(option: ThemeFontOption, selected: Boolean, onSelect: () -> Unit) {
    val family = fontFamilyForOption(option)
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(pressedScale = 0.985f, highlightAlpha = 0.06f, cornerRadius = IronLogRadius.Control)
    }

    val selectedBg = remember(selected, surfaces) {
        if (selected) {
            Brush.verticalGradient(listOf(surfaces.plateElevatedTop, surfaces.plateElevatedBottom))
        } else {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = pressIndication,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .then(
                if (selectedBg != null) Modifier.background(selectedBg) else Modifier
            )
            .padding(horizontal = IronLogSpace.CardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
        if (selected) {
            IronLogLedPip(color = surfaces.ledGlow, size = 5.dp, pulsing = true)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(option.displayName, fontFamily = family, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "The quick brown fox · 123",
                fontFamily = family,
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = scheme.onSurfaceVariant
            )
        }
        if (selected) {
            IronLogStatusChip(
                label = "Selected",
                tone = IronLogStatusTone.ACTIVE
            )
        }
    }
}
