package com.example.gymtime.ui.settings.preview

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.plate.PlateInventoryEditor
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.theme.tabular

data class SettingsPreviewState(
    val userName: String? = null,
    val timerAutoStart: Boolean = true,
    val timerAudio: Boolean = true,
    val timerVibrate: Boolean = true,
    val monthlyReport: Boolean = true,
    val keepScreenOn: Boolean = false,
    val darkMode: Boolean = true,
    val restDays: Int = 2,
    val barWeight: Float = 45f,
    val loadingSides: Int = 2,
    val availablePlates: List<Float> = listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f),
    val plateInventory: Map<Float, Int> = emptyMap(),
    val usePlateInventory: Boolean = false,
    val plateInventoryReady: Boolean = true,
    val transfers: List<SettingsTransferUi> = emptyList(),
    val versionName: String = ""
)

enum class SettingsToggle { AUTO_START, AUDIO, VIBRATE, MONTHLY_REPORT, KEEP_SCREEN_ON, DARK_MODE }
enum class SettingsTransfer { EXPORT, IRONLOG_IMPORT, FITNOTES_IMPORT }
enum class SettingsTransferStatus { IDLE, BUSY, SUCCESS, ERROR }
data class SettingsTransferUi(
    val operation: SettingsTransfer,
    val status: SettingsTransferStatus,
    val title: String = "",
    val lines: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

sealed interface SettingsPreviewAction {
    data object Back : SettingsPreviewAction
    data object Home : SettingsPreviewAction
    data object Theme : SettingsPreviewAction
    data object MonthlyReport : SettingsPreviewAction
    data object MuscleGroups : SettingsPreviewAction
    data class SaveName(val name: String) : SettingsPreviewAction
    data class Toggle(val setting: SettingsToggle, val enabled: Boolean) : SettingsPreviewAction
    data class RestDays(val count: Int) : SettingsPreviewAction
    data class BarWeight(val weight: Float) : SettingsPreviewAction
    data class LoadingSides(val sides: Int) : SettingsPreviewAction
    data class Plate(val weight: Float) : SettingsPreviewAction
    data class PlateCount(val weight: Float, val count: Int) : SettingsPreviewAction
    data class LimitPlates(val enabled: Boolean) : SettingsPreviewAction
    data class PlateCountDelta(val weight: Float, val delta: Int) : SettingsPreviewAction
    data class Transfer(val operation: SettingsTransfer) : SettingsPreviewAction
    data class DismissTransfer(val operation: SettingsTransfer) : SettingsPreviewAction
}

/** Plain rendering boundary: preferences and file operations stay in the screen wiring. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PreviewSettingsContent(
    state: SettingsPreviewState,
    onAction: (SettingsPreviewAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    var nameDraft by rememberSaveable { mutableStateOf("") }
    var nameEdited by rememberSaveable { mutableStateOf(false) }
    var nameFocused by remember { mutableStateOf(false) }
    var pendingSavedName by rememberSaveable { mutableStateOf<String?>(null) }
    var platesExpanded by rememberSaveable { mutableStateOf(false) }
    var backupsExpanded by rememberSaveable { mutableStateOf(false) }
    var showChangelog by rememberSaveable { mutableStateOf(false) }
    val busy = state.transfers.any { it.status == SettingsTransferStatus.BUSY }

    LaunchedEffect(state.userName, nameFocused) {
        val incoming = state.userName ?: return@LaunchedEffect
        if (pendingSavedName != null && incoming == pendingSavedName) {
            pendingSavedName = null
        }
        if (!nameEdited && !nameFocused && pendingSavedName == null) {
            nameDraft = incoming
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentColor = colors.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { onAction(SettingsPreviewAction.Back) },
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onAction(SettingsPreviewAction.Home) },
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) { Text("Home") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            contentPadding = PaddingValues(
                start = IronLogSpace.ScreenHorizontal,
                end = IronLogSpace.ScreenHorizontal,
                top = IronLogSpace.CompactGap,
                bottom = IronLogSpace.BottomNavClearance
            ),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.SectionGap)
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Make it yours",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Your look. Your logging. Your setup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            item(key = "profile") {
                SettingsSection("Profile") {
                    Column(
                        modifier = Modifier.padding(IronLogSpace.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                    ) {
                        OutlinedTextField(
                            value = nameDraft,
                            onValueChange = {
                                nameDraft = it
                                nameEdited = true
                                pendingSavedName = null
                            },
                            label = { Text("Your name") },
                            enabled = state.userName != null,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { nameFocused = it.isFocused },
                            shape = IronLogRadius.ControlShape
                        )
                        if (nameEdited) {
                            IronLogPrimaryButton(
                                text = "Save name",
                                onClick = {
                                    val trimmed = nameDraft.trim()
                                    nameDraft = trimmed
                                    nameEdited = false
                                    pendingSavedName = trimmed
                                    focusManager.clearFocus()
                                    onAction(SettingsPreviewAction.SaveName(trimmed))
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item(key = "look-and-feel") {
                SettingsSection("Look & feel") {
                    SettingsLinkRow("Theme & fonts", "Accent colors, custom colors and type", "Customize") {
                        onAction(SettingsPreviewAction.Theme)
                    }
                    IronLogDivider()
                    SettingsSwitchRow("Dark mode", "A darker canvas for your workouts", state.darkMode) {
                        onAction(SettingsPreviewAction.Toggle(SettingsToggle.DARK_MODE, it))
                    }
                }
            }

            item(key = "while-you-train") {
                SettingsSection("While you train") {
                    SettingsSwitchRow("Auto-start rest timer", "After each logged set", state.timerAutoStart) {
                        onAction(SettingsPreviewAction.Toggle(SettingsToggle.AUTO_START, it))
                    }
                    IronLogDivider()
                    SettingsSwitchRow("Timer sound", "Play a tone when your rest ends", state.timerAudio) {
                        onAction(SettingsPreviewAction.Toggle(SettingsToggle.AUDIO, it))
                    }
                    IronLogDivider()
                    SettingsSwitchRow("Timer vibration", "Feel when your rest ends", state.timerVibrate) {
                        onAction(SettingsPreviewAction.Toggle(SettingsToggle.VIBRATE, it))
                    }
                    IronLogDivider()
                    SettingsSwitchRow("Keep screen awake", "During workouts", state.keepScreenOn) {
                        onAction(SettingsPreviewAction.Toggle(SettingsToggle.KEEP_SCREEN_ON, it))
                    }
                }
            }

            item(key = "consistency") {
                SettingsSection("Consistency") {
                    Column(
                        modifier = Modifier.padding(IronLogSpace.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                    ) {
                        Text("Allowed rest days", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Miss up to this many days each week without breaking your Iron Streak.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            (0..7).forEach { count ->
                                SettingsChoice(
                                    label = "$count",
                                    selected = state.restDays == count,
                                    accessibleLabel = "$count rest days per week"
                                ) {
                                    onAction(SettingsPreviewAction.RestDays(count))
                                }
                            }
                        }
                    }
                }
            }

            item(key = "reports-body-parts") {
                SettingsSection("Reports & body parts") {
                    SettingsSwitchRow("Monthly report reminder", "A summary on the first of each month", state.monthlyReport) {
                        onAction(SettingsPreviewAction.Toggle(SettingsToggle.MONTHLY_REPORT, it))
                    }
                    IronLogDivider()
                    SettingsLinkRow("Last month's report", "Open your existing summary", "Open") {
                        onAction(SettingsPreviewAction.MonthlyReport)
                    }
                    IronLogDivider()
                    SettingsLinkRow("Body parts", "Add, rename or remove muscle groups", "Manage") {
                        onAction(SettingsPreviewAction.MuscleGroups)
                    }
                }
            }

            item(key = "equipment") {
                SettingsSection("Equipment") {
                    SettingsExpansionRow(
                        title = "Plate calculator setup",
                        subtitle = "${state.barWeight.plateLabel()} bar · ${state.loadingSides} loading ${if (state.loadingSides == 1) "side" else "sides"}",
                        expanded = platesExpanded,
                        onClick = { platesExpanded = !platesExpanded }
                    )
                    if (platesExpanded) {
                        IronLogDivider()
                        Column(
                            modifier = Modifier.padding(IronLogSpace.CardPadding),
                            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                        ) {
                            Text("Bar weight", style = MaterialTheme.typography.titleSmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(0f, 25f, 35f, 45f).forEach { weight ->
                                    SettingsChoice(weight.plateLabel(), state.barWeight == weight) {
                                        onAction(SettingsPreviewAction.BarWeight(weight))
                                    }
                                }
                            }
                            Text("Loading sides", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                                listOf(1, 2).forEach { sides ->
                                    SettingsChoice("$sides ${if (sides == 1) "side" else "sides"}", state.loadingSides == sides) {
                                        onAction(SettingsPreviewAction.LoadingSides(sides))
                                    }
                                }
                            }
                            Text("Available plates", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Select the sizes you have.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f).forEach { weight ->
                                    SettingsChoice(weight.plateLabel(), weight in state.availablePlates) {
                                        onAction(SettingsPreviewAction.Plate(weight))
                                    }
                                }
                            }
                            IronLogDivider(insetStart = 0.dp, modifier = Modifier.padding(vertical = 8.dp))
                            if (state.plateInventoryReady) {
                                PlateInventoryEditor(
                                    availablePlates = state.availablePlates,
                                    plateInventory = state.plateInventory,
                                    usePlateInventory = state.usePlateInventory,
                                    onPlateInventoryCountChange = { weight, count ->
                                        onAction(SettingsPreviewAction.PlateCount(weight, count))
                                    },
                                    onUsePlateInventoryChange = {
                                        onAction(SettingsPreviewAction.LimitPlates(it))
                                    },
                                    onPlateInventoryCountDelta = { weight, delta ->
                                        onAction(SettingsPreviewAction.PlateCountDelta(weight, delta))
                                    }
                                )
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            item(key = "your-data") {
                SettingsSection("Your data") {
                    SettingsExpansionRow(
                        title = "Backup & import",
                        subtitle = "IronLog ZIP backups and FitNotes CSV",
                        expanded = backupsExpanded || busy,
                        onClick = { backupsExpanded = !backupsExpanded }
                    )
                    if (backupsExpanded || busy) {
                        IronLogDivider()
                        SettingsTransferRow("Export IronLog backup", "Save workouts, exercises and routines to ZIP", SettingsTransfer.EXPORT, state.transfers, busy, onAction)
                        IronLogDivider()
                        SettingsTransferRow("Import IronLog backup", "Restore from an IronLog ZIP", SettingsTransfer.IRONLOG_IMPORT, state.transfers, busy, onAction)
                        IronLogDivider()
                        SettingsTransferRow("Import from FitNotes", "Bring in your exported CSV workout history", SettingsTransfer.FITNOTES_IMPORT, state.transfers, busy, onAction)
                    }
                }
            }

            item(key = "changelog") {
                TextButton(
                    onClick = { showChangelog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text(
                        text = "IronLog ${state.versionName} · What's new",
                        style = MaterialTheme.typography.labelLarge.tabular()
                    )
                }
            }
        }
    }

    state.transfers.firstOrNull {
        it.status == SettingsTransferStatus.SUCCESS || it.status == SettingsTransferStatus.ERROR
    }?.let { result ->
        AlertDialog(
            onDismissRequest = { onAction(SettingsPreviewAction.DismissTransfer(result.operation)) },
            shape = IronLogRadius.CardShape,
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            title = { Text(result.title) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                ) {
                    result.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium.tabular()) }
                    if (result.errors.isNotEmpty()) {
                        Text(
                            text = "${result.errors.size} ${if (result.errors.size == 1) "issue" else "issues"} reported",
                            style = MaterialTheme.typography.labelLarge.tabular(),
                            fontWeight = FontWeight.Bold
                        )
                        result.errors.forEach { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(SettingsPreviewAction.DismissTransfer(result.operation)) },
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) { Text("Done") }
            }
        )
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            shape = IronLogRadius.CardShape,
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant,
            title = { Text("What's new in ${state.versionName}") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
                ) {
                    Text(
                        text = "Plan it, load it, lift it",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Build a one-off workout. Choose every exercise you plan to do and move forward or back through your plan while logging. No routine required.")
                    Text("Superset exercise pills open the selected exercise in the logger right away.")
                    Text("The plate calculator supports a target weight or manual loading, using your saved bar, plates and loading sides. Bigger controls keep your setup intact when you switch modes.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showChangelog = false },
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) { Text("Done") }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = IronLogSpace.CardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SettingsLinkRow(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(IronLogSpace.CardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingsExpansionRow(title: String, subtitle: String, expanded: Boolean, onClick: () -> Unit) {
    Box(Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
        SettingsLinkRow(title, subtitle, if (expanded) "Hide" else "Show", onClick)
    }
}

@Composable
private fun SettingsChoice(label: String, selected: Boolean, accessibleLabel: String = label, onClick: () -> Unit) {
    val actions = LocalLoggerActionColors.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = IronLogRadius.ControlShape,
        label = { Text(label, style = MaterialTheme.typography.labelLarge.tabular()) },
        modifier = Modifier
            .heightIn(min = IronLogSpace.TouchTargetMin)
            .widthIn(min = IronLogSpace.TouchTargetMin)
            .semantics { contentDescription = accessibleLabel },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = actions.fill,
            selectedLabelColor = actions.onFill
        )
    )
}

@Composable
private fun SettingsTransferRow(
    title: String,
    subtitle: String,
    operation: SettingsTransfer,
    transfers: List<SettingsTransferUi>,
    busy: Boolean,
    onAction: (SettingsPreviewAction) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val inProgress = transfers.any { it.operation == operation && it.status == SettingsTransferStatus.BUSY }
    Column(
        modifier = Modifier.padding(IronLogSpace.CardPadding),
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        OutlinedButton(
            onClick = { onAction(SettingsPreviewAction.Transfer(operation)) },
            enabled = !busy,
            shape = IronLogRadius.ControlShape,
            border = IronLogBorder.stroke(if (!busy) scheme.outlineVariant else scheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = IronLogSpace.TouchTargetMin)
        ) {
            if (inProgress) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(IronLogSpace.CompactGap))
            }
            Text(
                text = if (inProgress) "In progress…" else when (operation) {
                    SettingsTransfer.EXPORT -> "Save ZIP backup"
                    SettingsTransfer.IRONLOG_IMPORT -> "Choose ZIP backup"
                    SettingsTransfer.FITNOTES_IMPORT -> "Choose CSV file"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun Float.plateLabel(): String = if (this % 1f == 0f) "${toInt()} lb" else "$this lb"
