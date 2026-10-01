package com.example.gymtime.ui.settings.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.R
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.theme.ThemeColors
import com.example.gymtime.ui.theme.ThemeFontOption
import com.example.gymtime.ui.theme.ThemePreset

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
    Scaffold(
        containerColor = colors.background,
        contentColor = colors.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Theme", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = onHome) { Text("Home") } },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Make it yours", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Your color. Your type. Your training.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = colors.primaryContainer, contentColor = colors.onSurface, tonalElevation = 0.dp) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LIVE PREVIEW", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        Text("Bench press", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("100 lb × 8", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Surface(color = action.fill, contentColor = action.onFill, shape = RoundedCornerShape(14.dp)) {
                            Text("Log set", Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("Sample set · changes apply as you choose", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            item {
                PreviewThemeSection("Accent color") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreset.entries.forEach { preset ->
                            val selected = state.accentKey == preset.storageKey
                            FilterChip(
                                selected = selected,
                                onClick = { onPreset(preset.storageKey) },
                                label = { Text(preset.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)) },
                                leadingIcon = {
                                    Box(Modifier.size(16.dp).clip(CircleShape).background(ThemeColors.getScheme(preset.storageKey).primaryAccent))
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                    OutlinedButton(onClick = { colorDialogOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (state.accentKey == "custom") "Custom · ${state.customAccentHex.orEmpty()} · Edit" else "Choose a custom color")
                    }
                    if (state.customAccentHex != null) {
                        TextButton(onClick = onClearCustomAccent, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear custom color") }
                    }
                }
            }
            item {
                PreviewThemeSection("Display") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                            .toggleable(value = state.darkMode, role = Role.Switch, onValueChange = onDarkMode),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Dark mode", style = MaterialTheme.typography.titleMedium)
                            Text(if (state.darkMode) "A softer canvas for evening training" else "Light and clear", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = state.darkMode, onCheckedChange = null)
                    }
                }
            }
            item {
                PreviewThemeSection("Typography") {
                    ThemeFontOption.entries.filter { it != ThemeFontOption.CUSTOM }.forEach { option ->
                        PreviewFontOption(option, selected = state.fontKey == option.storageKey, onSelect = { onFont(option.storageKey) })
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                    Text("Custom font", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(state.customFontName ?: "Use a .ttf or .otf file from your device.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = onUploadFont,
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = action.fill, contentColor = action.onFill)
                        ) { Text(if (state.customFontName == null) "Choose font" else "Replace font") }
                        if (state.customFontName != null) {
                            OutlinedButton(onClick = { onFont(ThemeFontOption.CUSTOM.storageKey) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(if (state.fontKey == "custom") "Custom selected" else "Use custom")
                            }
                            TextButton(onClick = onClearCustomFont, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear") }
                        }
                    }
                    state.fontImportError?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    if (colorDialogOpen) {
        PreviewThemeColorDialog(
            initialHex = state.customAccentHex ?: previewColorToHex(action.fill),
            onDismiss = { colorDialogOpen = false },
            onSave = { onCustomAccent(it); colorDialogOpen = false }
        )
    }
}

@Composable
private fun PreviewThemeSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun PreviewFontOption(option: ThemeFontOption, selected: Boolean, onSelect: () -> Unit) {
    val family = remember(option) {
        when (option) {
            ThemeFontOption.BEBAS_NEUE -> FontFamily(Font(R.font.bebas_neue_regular))
            ThemeFontOption.OSWALD -> FontFamily(Font(R.font.oswald_variable))
            ThemeFontOption.RALEWAY -> FontFamily(Font(R.font.raleway_variable))
            ThemeFontOption.SPACE_GROTESK -> FontFamily(Font(R.font.space_grotesk_variable))
            ThemeFontOption.PACIFICO -> FontFamily(Font(R.font.pacifico_regular))
            ThemeFontOption.CUSTOM -> FontFamily.Default
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .heightIn(min = 64.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(option.displayName, fontFamily = family, style = MaterialTheme.typography.titleLarge)
            Text("The quick brown fox · 123", fontFamily = family, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
