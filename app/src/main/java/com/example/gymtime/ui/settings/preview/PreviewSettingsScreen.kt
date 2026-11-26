package com.example.gymtime.ui.settings.preview

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymtime.BuildConfig
import com.example.gymtime.ui.settings.SettingsViewModel
import com.example.gymtime.util.IronLogImporter
import java.time.LocalDate

private val ZIP_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream"
)

private val CSV_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/csv",
    "application/vnd.ms-excel",
    "application/octet-stream"
)

/** App wiring owns persistence, navigation and platform document pickers. */
@Composable
fun PreviewSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onTheme: () -> Unit,
    onMonthlyReport: () -> Unit,
    onMuscleGroups: () -> Unit
) {
    val userName by viewModel.userName.collectAsStateWithLifecycle(initialValue = null)
    val timerAutoStart by viewModel.timerAutoStart.collectAsStateWithLifecycle(initialValue = true)
    val timerAudio by viewModel.timerAudioEnabled.collectAsStateWithLifecycle(initialValue = true)
    val timerVibrate by viewModel.timerVibrateEnabled.collectAsStateWithLifecycle(initialValue = true)
    val monthlyReport by viewModel.monthlyReportEnabled.collectAsStateWithLifecycle(initialValue = true)
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle(initialValue = false)
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle(initialValue = true)
    val restDays by viewModel.restDaysPerWeek.collectAsStateWithLifecycle(initialValue = 2)
    val barWeight by viewModel.barWeight.collectAsStateWithLifecycle(initialValue = 45f)
    val loadingSides by viewModel.loadingSides.collectAsStateWithLifecycle(initialValue = 2)
    val availablePlates by viewModel.availablePlates.collectAsStateWithLifecycle(initialValue = listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f))
    val plateInventorySettings by viewModel.plateInventorySettings.collectAsStateWithLifecycle(initialValue = null)
    val fitNotesState by viewModel.importState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val ironLogState by viewModel.ironLogImportState.collectAsStateWithLifecycle()
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val fitNotesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importFitNotes(uri)
        }
    }
    val backupCreator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            viewModel.exportData(uri)
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImportUri = uri
        }
    }

    PreviewSettingsContent(
        state = SettingsPreviewState(
            userName = userName,
            timerAutoStart = timerAutoStart,
            timerAudio = timerAudio,
            timerVibrate = timerVibrate,
            monthlyReport = monthlyReport,
            keepScreenOn = keepScreenOn,
            darkMode = darkMode,
            restDays = restDays,
            barWeight = barWeight,
            loadingSides = loadingSides,
            availablePlates = availablePlates,
            plateInventory = plateInventorySettings?.counts.orEmpty(),
            usePlateInventory = plateInventorySettings?.enabled ?: false,
            plateInventoryReady = plateInventorySettings != null,
            transfers = listOf(exportState.previewUi(), ironLogState.previewUi(), fitNotesState.previewUi()),
            showImportModePrompt = pendingImportUri != null,
            versionName = BuildConfig.VERSION_NAME
        ),
        onAction = { action ->
            when (action) {
                SettingsPreviewAction.Back -> onBack()
                SettingsPreviewAction.Home -> onHome()
                SettingsPreviewAction.Theme -> onTheme()
                SettingsPreviewAction.MonthlyReport -> onMonthlyReport()
                SettingsPreviewAction.MuscleGroups -> onMuscleGroups()
                is SettingsPreviewAction.SaveName -> viewModel.setUserName(action.name)
                is SettingsPreviewAction.Toggle -> when (action.setting) {
                    SettingsToggle.AUTO_START -> viewModel.setTimerAutoStart(action.enabled)
                    SettingsToggle.AUDIO -> viewModel.setTimerAudioEnabled(action.enabled)
                    SettingsToggle.VIBRATE -> viewModel.setTimerVibrateEnabled(action.enabled)
                    SettingsToggle.MONTHLY_REPORT -> viewModel.setMonthlyReportEnabled(action.enabled)
                    SettingsToggle.KEEP_SCREEN_ON -> viewModel.setKeepScreenOn(action.enabled)
                    SettingsToggle.DARK_MODE -> viewModel.setDarkMode(action.enabled)
                }
                is SettingsPreviewAction.RestDays -> viewModel.setRestDaysPerWeek(action.count)
                is SettingsPreviewAction.BarWeight -> viewModel.setBarWeight(action.weight)
                is SettingsPreviewAction.LoadingSides -> viewModel.setLoadingSides(action.sides)
                is SettingsPreviewAction.Plate -> viewModel.togglePlate(action.weight)
                is SettingsPreviewAction.PlateCount -> viewModel.setPlateInventoryCount(action.weight, action.count)
                is SettingsPreviewAction.LimitPlates -> viewModel.setUsePlateInventory(action.enabled)
                is SettingsPreviewAction.PlateCountDelta -> viewModel.adjustPlateInventoryCount(action.weight, action.delta)
                is SettingsPreviewAction.Transfer -> when (action.operation) {
                    SettingsTransfer.EXPORT -> backupCreator.launch("ironlog_backup_${LocalDate.now()}.zip")
                    SettingsTransfer.IRONLOG_IMPORT -> backupPicker.launch(ZIP_MIME_TYPES)
                    SettingsTransfer.FITNOTES_IMPORT -> fitNotesPicker.launch(CSV_MIME_TYPES)
                }
                is SettingsPreviewAction.ConfirmIronLogImport -> {
                    val uri = pendingImportUri
                    pendingImportUri = null
                    if (uri != null) {
                        val mode = when (action.mode) {
                            SettingsImportMode.REPLACE -> IronLogImporter.ImportMode.REPLACE
                            SettingsImportMode.MERGE -> IronLogImporter.ImportMode.MERGE
                        }
                        viewModel.importIronLog(uri, mode)
                    }
                }
                SettingsPreviewAction.DismissImportModePrompt -> {
                    pendingImportUri = null
                }
                is SettingsPreviewAction.DismissTransfer -> when (action.operation) {
                    SettingsTransfer.EXPORT -> viewModel.clearExportState()
                    SettingsTransfer.IRONLOG_IMPORT -> viewModel.clearIronLogImportState()
                    SettingsTransfer.FITNOTES_IMPORT -> viewModel.clearImportState()
                }
            }
        }
    )
}

private fun SettingsViewModel.ExportState.previewUi(): SettingsTransferUi = when (this) {
    SettingsViewModel.ExportState.Idle -> SettingsTransferUi(SettingsTransfer.EXPORT, SettingsTransferStatus.IDLE)
    SettingsViewModel.ExportState.InProgress -> SettingsTransferUi(SettingsTransfer.EXPORT, SettingsTransferStatus.BUSY)
    is SettingsViewModel.ExportState.Error -> SettingsTransferUi(SettingsTransfer.EXPORT, SettingsTransferStatus.ERROR, "Export failed", listOf(message))
    is SettingsViewModel.ExportState.Success -> SettingsTransferUi(
        SettingsTransfer.EXPORT, SettingsTransferStatus.SUCCESS, "Backup saved",
        buildList {
            add("${result.exerciseCount} exercises")
            add("${result.workoutCount} workouts")
            add("${result.setCount} sets")
            if (result.routineCount > 0) add("${result.routineCount} routines (${result.routineDayCount} days)")
            if (result.muscleGroupCount > 0) add("${result.muscleGroupCount} body parts")
            if (result.preferencesIncluded) add("Settings & equipment preferences included")
        }
    )
}

private fun SettingsViewModel.IronLogImportState.previewUi(): SettingsTransferUi = when (this) {
    SettingsViewModel.IronLogImportState.Idle -> SettingsTransferUi(SettingsTransfer.IRONLOG_IMPORT, SettingsTransferStatus.IDLE)
    SettingsViewModel.IronLogImportState.InProgress -> SettingsTransferUi(SettingsTransfer.IRONLOG_IMPORT, SettingsTransferStatus.BUSY)
    is SettingsViewModel.IronLogImportState.Error -> SettingsTransferUi(SettingsTransfer.IRONLOG_IMPORT, SettingsTransferStatus.ERROR, "Import failed", listOf(message))
    is SettingsViewModel.IronLogImportState.Success -> SettingsTransferUi(
        SettingsTransfer.IRONLOG_IMPORT, SettingsTransferStatus.SUCCESS, "Backup imported",
        buildList {
            add("${result.exercisesImported} exercises imported")
            if (result.exercisesUpdated > 0) add("${result.exercisesUpdated} exercises updated")
            add("${result.workoutsImported} workouts imported")
            add("${result.setsImported} sets imported")
            if (result.routinesImported > 0) add("${result.routinesImported} routines imported (${result.routineDaysImported} days)")
            if (result.muscleGroupsImported > 0) add("${result.muscleGroupsImported} body parts imported")
            if (result.workoutExerciseInstancesImported > 0) add("${result.workoutExerciseInstancesImported} workout plan items imported")
            if (result.duplicatesSkipped > 0) add("${result.duplicatesSkipped} existing duplicates skipped")
            if (result.preferencesRestored) add("Settings & equipment preferences restored")
        },
        errors = result.errors
    )
}

private fun SettingsViewModel.ImportState.previewUi(): SettingsTransferUi = when (this) {
    SettingsViewModel.ImportState.Idle -> SettingsTransferUi(SettingsTransfer.FITNOTES_IMPORT, SettingsTransferStatus.IDLE)
    SettingsViewModel.ImportState.InProgress -> SettingsTransferUi(SettingsTransfer.FITNOTES_IMPORT, SettingsTransferStatus.BUSY)
    is SettingsViewModel.ImportState.Error -> SettingsTransferUi(SettingsTransfer.FITNOTES_IMPORT, SettingsTransferStatus.ERROR, "Import failed", listOf(message))
    is SettingsViewModel.ImportState.Success -> SettingsTransferUi(
        SettingsTransfer.FITNOTES_IMPORT, SettingsTransferStatus.SUCCESS, "FitNotes imported",
        buildList {
            add("${result.workoutsImported} workouts imported")
            add("${result.setsImported} sets imported")
            add("${result.exercisesCreated} new exercises created")
            if (result.duplicatesSkipped > 0) add("${result.duplicatesSkipped} duplicates skipped")
        },
        errors = result.errors
    )
}
