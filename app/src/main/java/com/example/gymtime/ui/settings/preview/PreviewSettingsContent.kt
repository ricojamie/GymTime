package com.example.gymtime.ui.settings.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.components.IronLogCalibrationBar
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogFilterPill
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogMascotBadge
import com.example.gymtime.ui.components.IronLogMascotStyle
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSculptedKeycap
import com.example.gymtime.ui.components.IronLogSegmentedControl
import com.example.gymtime.ui.components.IronLogTactileSwitch
import com.example.gymtime.ui.components.KeycapHaptic
import com.example.gymtime.ui.components.plate.PlateInventoryEditor
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.MachinedPressIndication
import com.example.gymtime.ui.theme.staggeredEntranceOnce
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
    val surfaces = LocalIronLogSurfaces.current
    val reducedMotion = LocalReducedMotion.current
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
        containerColor = Color.Transparent,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                Column(
                    modifier = Modifier.staggeredEntranceOnce("settings", 0),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IronLogLedPip(color = surfaces.ledGlow, size = 5.dp)
                        Text(
                            text = "INSTRUMENT CALIBRATION",
                            style = IronLogType.technicalEyebrow(),
                            color = surfaces.accent
                        )
                    }
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
                SettingsSection(
                    title = "Profile",
                    modifier = Modifier.staggeredEntranceOnce("settings", 1)
                ) {
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
                            shape = IronLogRadius.KeycapShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = surfaces.wellFill,
                                unfocusedContainerColor = surfaces.wellFill,
                                focusedBorderColor = surfaces.accent,
                                unfocusedBorderColor = colors.outlineVariant.copy(alpha = 0.65f)
                            )
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
                SettingsSection(
                    title = "Look & feel",
                    modifier = Modifier.staggeredEntranceOnce("settings", 2)
                ) {
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
                SettingsSection(
                    title = "While you train",
                    modifier = Modifier.staggeredEntranceOnce("settings", 3)
                ) {
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
                SettingsSection(
                    title = "Consistency",
                    modifier = Modifier.staggeredEntranceOnce("settings", 4)
                ) {
                    Column(
                        modifier = Modifier.padding(IronLogSpace.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                    ) {
                        Text("Allowed rest days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                SettingsSection(
                    title = "Reports & body parts",
                    modifier = Modifier.staggeredEntranceOnce("settings", 5)
                ) {
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
                SettingsSection(
                    title = "Equipment",
                    modifier = Modifier.staggeredEntranceOnce("settings", 6)
                ) {
                    SettingsExpansionRow(
                        title = "Plate calculator setup",
                        subtitle = "${state.barWeight.plateLabel()} bar · ${state.loadingSides} loading ${if (state.loadingSides == 1) "side" else "sides"}",
                        expanded = platesExpanded,
                        onClick = { platesExpanded = !platesExpanded }
                    )
                    AnimatedVisibility(
                        visible = platesExpanded,
                        enter = fadeIn(IronMotion.orSnap(reducedMotion, IronMotion.EffectDefault)) +
                            expandVertically(IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize)),
                        exit = fadeOut(IronMotion.orSnap(reducedMotion, IronMotion.EffectFast)) +
                            shrinkVertically(IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize))
                    ) {
                        Column {
                            IronLogDivider()
                            Column(
                                modifier = Modifier.padding(IronLogSpace.CardPadding),
                                verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                            ) {
                                Text("BAR WEIGHT", style = IronLogType.technicalEyebrow(), color = colors.onSurfaceVariant)
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
                                Text("LOADING SIDES", style = IronLogType.technicalEyebrow(), color = colors.onSurfaceVariant)
                                IronLogSegmentedControl(
                                    options = listOf("1 side", "2 sides"),
                                    selectedIndex = if (state.loadingSides == 1) 0 else 1,
                                    onSelect = { idx ->
                                        onAction(SettingsPreviewAction.LoadingSides(if (idx == 0) 1 else 2))
                                    }
                                )
                                Text("AVAILABLE PLATES", style = IronLogType.technicalEyebrow(), color = colors.onSurfaceVariant)
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
                                    IronLogCalibrationBar(
                                        progress = 0.5f,
                                        accentColor = surfaces.accent,
                                        height = 8.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "your-data") {
                SettingsSection(
                    title = "Your data",
                    modifier = Modifier.staggeredEntranceOnce("settings", 6)
                ) {
                    SettingsExpansionRow(
                        title = "Backup & import",
                        subtitle = "IronLog ZIP backups and FitNotes CSV",
                        expanded = backupsExpanded || busy,
                        onClick = { backupsExpanded = !backupsExpanded }
                    )
                    AnimatedVisibility(
                        visible = backupsExpanded || busy,
                        enter = fadeIn(IronMotion.orSnap(reducedMotion, IronMotion.EffectDefault)) +
                            expandVertically(IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize)),
                        exit = fadeOut(IronMotion.orSnap(reducedMotion, IronMotion.EffectFast)) +
                            shrinkVertically(IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize))
                    ) {
                        Column {
                            IronLogDivider()
                            SettingsTransferRow("Export IronLog backup", "Save workouts, exercises and routines to ZIP", SettingsTransfer.EXPORT, state.transfers, busy, onAction)
                            IronLogDivider()
                            SettingsTransferRow("Import IronLog backup", "Restore from an IronLog ZIP", SettingsTransfer.IRONLOG_IMPORT, state.transfers, busy, onAction)
                            IronLogDivider()
                            SettingsTransferRow("Import from FitNotes", "Bring in your exported CSV workout history", SettingsTransfer.FITNOTES_IMPORT, state.transfers, busy, onAction)
                        }
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

            item(key = "brand-stamp") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = IronLogSpace.CompactGap, bottom = IronLogSpace.CompactGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IronLogMascotBadge(
                        size = 36.dp,
                        style = IronLogMascotStyle.SUBTLE
                    )
                    Text(
                        text = "IRONLOG · LOCAL-FIRST",
                        style = IronLogType.technicalEyebrow(),
                        color = colors.onSurfaceVariant
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
            containerColor = surfaces.plateBottom,
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
            containerColor = surfaces.plateBottom,
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
private fun SettingsSection(
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
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(pressedScale = 0.985f, highlightAlpha = 0.05f, cornerRadius = IronLogRadius.Control)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = pressIndication,
                onValueChange = { next ->
                    haptics.toggle(next)
                    onChange(next)
                }
            )
            .padding(horizontal = IronLogSpace.CardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IronLogTactileSwitch(
            checked = checked,
            onCheckedChange = null,
            accentColor = surfaces.accent,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

@Composable
private fun SettingsLinkRow(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(pressedScale = 0.985f, highlightAlpha = 0.06f, cornerRadius = IronLogRadius.Control)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = pressIndication,
                role = Role.Button,
                onClick = {
                    haptics.tick()
                    onClick()
                }
            )
            .padding(IronLogSpace.CardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = action.uppercase(),
            style = IronLogType.telemetryLabel().copy(fontWeight = FontWeight.Bold),
            color = surfaces.accent
        )
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
    IronLogFilterPill(
        label = label,
        selected = selected,
        onClick = onClick,
        showLedPip = false,
        modifier = Modifier.semantics { contentDescription = accessibleLabel }
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
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        IronLogSculptedKeycap(
            onClick = { onAction(SettingsPreviewAction.Transfer(operation)) },
            enabled = !busy,
            hapticType = KeycapHaptic.KEY_TAP,
            shape = IronLogRadius.ControlShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
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
}

private fun Float.plateLabel(): String = if (this % 1f == 0f) "${toInt()} lb" else "$this lb"
