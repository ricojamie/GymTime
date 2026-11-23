package com.example.gymtime.ui.settings.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSecondaryButton
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.theme.ThemeColors
import com.example.gymtime.ui.theme.ThemeFontOption
import com.example.gymtime.ui.theme.ThemePreset
import com.example.gymtime.ui.theme.fontFamilyForOption
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
    val action = LocalLoggerActionColors.current
    val fontOptions = remember { ThemeFontOption.entries.filter { it != ThemeFontOption.CUSTOM } }

    Scaffold(
        containerColor = colors.background,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = IronLogSpace.ScreenHorizontal,
                end = IronLogSpace.ScreenHorizontal,
                top = IronLogSpace.CompactGap,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.SectionGap)
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onSurface,
                    shape = IronLogRadius.CardShape,
                    contentPadding = PaddingValues(IronLogSpace.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                ) {
                    Text(
                        text = "LIVE PREVIEW",
                        style = IronLogType.sectionEyebrow(),
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = "Bench press",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "100 lb × 8",
                        style = MaterialTheme.typography.headlineLarge.tabular(),
                        fontWeight = FontWeight.Bold
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
                PreviewThemeSection("Accent color") {
                    Column(
                        modifier = Modifier.padding(IronLogSpace.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                        ) {
                            ThemePreset.entries.forEach { preset ->
                                val selected = state.accentKey == preset.storageKey
                                val label = remember(preset) {
                                    preset.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = { onPreset(preset.storageKey) },
                                    shape = IronLogRadius.ControlShape,
                                    label = { Text(label) },
                                    leadingIcon = {
                                        Box(
                                            Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(ThemeColors.getScheme(preset.storageKey).primaryAccent)
                                        )
                                    },
                                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                                )
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
                PreviewThemeSection("Display") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .toggleable(value = state.darkMode, role = Role.Switch, onValueChange = onDarkMode)
                            .padding(IronLogSpace.CardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Dark mode", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (state.darkMode) "A softer canvas for evening training" else "Light and clear",
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = state.darkMode, onCheckedChange = null)
                    }
                }
            }

            item(key = "typography") {
                PreviewThemeSection("Typography") {
                    fontOptions.forEachIndexed { index, option ->
                        if (index > 0) {
                            IronLogDivider()
                        }
                        PreviewFontOption(
                            option = option,
                            selected = state.fontKey == option.storageKey,
                            onSelect = { onFont(option.storageKey) }
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
private fun PreviewThemeSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
        IronLogSectionHeader(title = title)
        IronLogGroupedCard(
            showBorder = true,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            content = content
        )
    }
}

@Composable
private fun PreviewFontOption(option: ThemeFontOption, selected: Boolean, onSelect: () -> Unit) {
    val family = fontFamilyForOption(option)
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .background(if (selected) scheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
            .heightIn(min = 64.dp)
            .padding(horizontal = IronLogSpace.CardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
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
