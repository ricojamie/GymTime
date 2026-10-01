package com.example.gymtime.ui.settings.preview

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymtime.BuildConfig
import com.example.gymtime.ui.settings.SettingsViewModel

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
    val context = LocalContext.current

    val fitNotesPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("The selected CSV file could not be opened.")
                viewModel.importFitNotes(input)
            } catch (error: Exception) {
                viewModel.reportFitNotesFileError(error.message ?: "The selected CSV file could not be opened.")
            }
        }
    }
    val backupCreator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            try {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("The backup file could not be opened for writing.")
                viewModel.exportData(output)
            } catch (error: Exception) {
                viewModel.reportExportFileError(error.message ?: "The backup file could not be opened for writing.")
            }
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("The selected ZIP backup could not be opened.")
                viewModel.importIronLog(input)
            } catch (error: Exception) {
                viewModel.reportIronLogFileError(error.message ?: "The selected ZIP backup could not be opened.")
            }
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
                is SettingsPreviewAction.Plate -> viewModel.togglePlate(action.weight, availablePlates)
                is SettingsPreviewAction.PlateCount -> viewModel.setPlateInventoryCount(action.weight, action.count)
                is SettingsPreviewAction.LimitPlates -> viewModel.setUsePlateInventory(action.enabled)
                is SettingsPreviewAction.PlateCountDelta -> viewModel.adjustPlateInventoryCount(action.weight, action.delta)
                is SettingsPreviewAction.Transfer -> when (action.operation) {
                    SettingsTransfer.EXPORT -> backupCreator.launch("ironlog_backup.zip")
                    SettingsTransfer.IRONLOG_IMPORT -> backupPicker.launch("application/zip")
                    SettingsTransfer.FITNOTES_IMPORT -> fitNotesPicker.launch("text/*")
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
            if (result.routineCount > 0) add("${result.routineCount} routines")
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
            add("${result.workoutsImported} workouts imported")
            add("${result.setsImported} sets imported")
            if (result.routinesImported > 0) add("${result.routinesImported} routines imported")
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
