package com.example.gymtime.ui.settings.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.components.plate.PlateInventoryEditor

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
    val actions = LocalLoggerActionColors.current
    var nameDraft by rememberSaveable { mutableStateOf("") }
    var nameEdited by rememberSaveable { mutableStateOf(false) }
    var platesExpanded by rememberSaveable { mutableStateOf(false) }
    var backupsExpanded by rememberSaveable { mutableStateOf(false) }
    var showChangelog by rememberSaveable { mutableStateOf(false) }
    val busy = state.transfers.any { it.status == SettingsTransferStatus.BUSY }
    LaunchedEffect(state.userName) {
        if (!nameEdited) state.userName?.let { nameDraft = it }
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
                    IconButton(onClick = { onAction(SettingsPreviewAction.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { onAction(SettingsPreviewAction.Home) }) { Text("Home") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Make it yours", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Your look. Your logging. Your setup.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            item {
                SettingsSection("Profile") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it; nameEdited = true },
                            label = { Text("Your name") },
                            enabled = state.userName != null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )
                        if (nameEdited) {
                            Button(
                                onClick = {
                                    nameDraft = nameDraft.trim()
                                    onAction(SettingsPreviewAction.SaveName(nameDraft))
                                    nameEdited = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = actions.fill, contentColor = actions.onFill),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) { Text("Save name") }
                        }
                    }
                }
            }
            item {
                SettingsSection("Look & feel") {
                    SettingsLinkRow("Theme & fonts", "Accent colors, custom colors and type", "Customize") { onAction(SettingsPreviewAction.Theme) }
                    SettingsDivider()
                    SettingsSwitchRow("Dark mode", "A darker canvas for your workouts", state.darkMode) {
                        onAction(SettingsPreviewAction.Toggle(SettingsToggle.DARK_MODE, it))
                    }
                }
            }
            item {
                SettingsSection("While you train") {
                    SettingsSwitchRow("Auto-start rest timer", "After each logged set", state.timerAutoStart) {
                        onAction(SettingsPreviewAction.Toggle(SettingsToggle.AUTO_START, it))
                    }
                    SettingsDivider()
                    SettingsSwitchRow("Timer sound", "Play a tone when your rest ends", state.timerAudio) {
                        onAction(SettingsPreviewAction.Toggle(SettingsToggle.AUDIO, it))
                    }
                    SettingsDivider()
                    SettingsSwitchRow("Timer vibration", "Feel when your rest ends", state.timerVibrate) {
                        onAction(SettingsPreviewAction.Toggle(SettingsToggle.VIBRATE, it))
                    }
                    SettingsDivider()
                    SettingsSwitchRow("Keep screen awake", "During workouts", state.keepScreenOn) {
                        onAction(SettingsPreviewAction.Toggle(SettingsToggle.KEEP_SCREEN_ON, it))
                    }
                }
            }
            item {
                SettingsSection("Consistency") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Allowed rest days", style = MaterialTheme.typography.titleMedium)
                        Text("Miss up to this many days each week without breaking your Iron Streak.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            (0..7).forEach { count ->
                                SettingsChoice("$count", state.restDays == count, "$count rest days per week") {
                                    onAction(SettingsPreviewAction.RestDays(count))
                                }
                            }
                        }
                    }
                }
            }
            item {
                SettingsSection("Reports & body parts") {
                    SettingsSwitchRow("Monthly report reminder", "A summary on the first of each month", state.monthlyReport) {
                        onAction(SettingsPreviewAction.Toggle(SettingsToggle.MONTHLY_REPORT, it))
                    }
                    SettingsDivider()
                    SettingsLinkRow("Last month's report", "Open your existing summary", "Open") { onAction(SettingsPreviewAction.MonthlyReport) }
                    SettingsDivider()
                    SettingsLinkRow("Body parts", "Add, rename or remove muscle groups", "Manage") { onAction(SettingsPreviewAction.MuscleGroups) }
                }
            }
            item {
                SettingsSection("Equipment") {
                    SettingsExpansionRow(
                        "Plate calculator setup",
                        "${state.barWeight.plateLabel()} bar · ${state.loadingSides} loading ${if (state.loadingSides == 1) "side" else "sides"}",
                        platesExpanded,
                        { platesExpanded = !platesExpanded }
                    )
                    if (platesExpanded) {
                        SettingsDivider()
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Bar weight", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(0f, 25f, 35f, 45f).forEach { weight ->
                                    SettingsChoice(weight.plateLabel(), state.barWeight == weight) { onAction(SettingsPreviewAction.BarWeight(weight)) }
                                }
                            }
                            Text("Loading sides", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 2).forEach { sides ->
                                    SettingsChoice("$sides ${if (sides == 1) "side" else "sides"}", state.loadingSides == sides) {
                                        onAction(SettingsPreviewAction.LoadingSides(sides))
                                    }
                                }
                            }
                            Text("Available plates", style = MaterialTheme.typography.titleSmall)
                            Text("Select the sizes you have.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f).forEach { weight ->
                                    SettingsChoice(weight.plateLabel(), weight in state.availablePlates) { onAction(SettingsPreviewAction.Plate(weight)) }
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.outlineVariant)
                            if (state.plateInventoryReady) PlateInventoryEditor(
                                availablePlates = state.availablePlates,
                                plateInventory = state.plateInventory,
                                usePlateInventory = state.usePlateInventory,
                                onPlateInventoryCountChange = { weight, count -> onAction(SettingsPreviewAction.PlateCount(weight, count)) },
                                onUsePlateInventoryChange = { onAction(SettingsPreviewAction.LimitPlates(it)) },
                                onPlateInventoryCountDelta = { weight, delta -> onAction(SettingsPreviewAction.PlateCountDelta(weight, delta)) }
                            ) else LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            item {
                SettingsSection("Your data") {
                    SettingsExpansionRow("Backup & import", "IronLog ZIP backups and FitNotes CSV", backupsExpanded || busy) { backupsExpanded = !backupsExpanded }
                    if (backupsExpanded || busy) {
                        SettingsDivider()
                        SettingsTransferRow("Export IronLog backup", "Save workouts, exercises and routines to ZIP", SettingsTransfer.EXPORT, state.transfers, busy, onAction)
                        SettingsDivider()
                        SettingsTransferRow("Import IronLog backup", "Restore from an IronLog ZIP", SettingsTransfer.IRONLOG_IMPORT, state.transfers, busy, onAction)
                        SettingsDivider()
                        SettingsTransferRow("Import from FitNotes", "Bring in your exported CSV workout history", SettingsTransfer.FITNOTES_IMPORT, state.transfers, busy, onAction)
                    }
                }
            }
            item {
                TextButton(onClick = { showChangelog = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("IronLog ${state.versionName} · What's new")
                }
            }
        }
    }

    state.transfers.firstOrNull { it.status == SettingsTransferStatus.SUCCESS || it.status == SettingsTransferStatus.ERROR }?.let { result ->
        AlertDialog(
            onDismissRequest = { onAction(SettingsPreviewAction.DismissTransfer(result.operation)) },
            title = { Text(result.title) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.lines.forEach { Text(it) }
                    if (result.errors.isNotEmpty()) {
                        Text("${result.errors.size} ${if (result.errors.size == 1) "issue" else "issues"} reported", fontWeight = FontWeight.Bold)
                        result.errors.forEach { Text(it, color = colors.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onAction(SettingsPreviewAction.DismissTransfer(result.operation)) }) { Text("Done") }
            }
        )
    }
    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("What's new in ${state.versionName}") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Plan it, load it, lift it", fontWeight = FontWeight.Bold)
                    Text("Build a one-off workout. Choose every exercise you plan to do and move forward or back through your plan while logging. No routine required.")
                    Text("Superset exercise pills open the selected exercise in the logger right away.")
                    Text("The plate calculator supports a target weight or manual loading, using your saved bar, plates and loading sides. Bigger controls keep your setup intact when you switch modes.")
                }
            },
            confirmButton = { TextButton(onClick = { showChangelog = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        label = { Text(label) },
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).semantics { contentDescription = accessibleLabel },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = actions.fill,
            selectedLabelColor = actions.onFill
        )
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingsTransferRow(
    title: String, subtitle: String, operation: SettingsTransfer,
    transfers: List<SettingsTransferUi>, busy: Boolean,
    onAction: (SettingsPreviewAction) -> Unit
) {
    val inProgress = transfers.any { it.operation == operation && it.status == SettingsTransferStatus.BUSY }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = { onAction(SettingsPreviewAction.Transfer(operation)) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            if (inProgress) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (inProgress) "In progress…" else when (operation) {
                SettingsTransfer.EXPORT -> "Save ZIP backup"
                SettingsTransfer.IRONLOG_IMPORT -> "Choose ZIP backup"
                SettingsTransfer.FITNOTES_IMPORT -> "Choose CSV file"
            })
        }
    }
}

private fun Float.plateLabel(): String = if (this % 1f == 0f) "${toInt()} lb" else "$this lb"
