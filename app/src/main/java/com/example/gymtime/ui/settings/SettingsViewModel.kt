package com.example.gymtime.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Typeface as AndroidTypeface
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.notifications.MonthlyReportScheduler
import com.example.gymtime.ui.theme.ThemeFontOption
import com.example.gymtime.ui.theme.ThemePreset
import com.example.gymtime.util.FitNotesImporter
import com.example.gymtime.util.IronLogExporter
import com.example.gymtime.util.IronLogImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val fitNotesImporter: FitNotesImporter,
    private val ironLogExporter: IronLogExporter,
    private val ironLogImporter: IronLogImporter,
    private val monthlyReportScheduler: MonthlyReportScheduler
) : ViewModel() {

    val userName = userPreferencesRepository.userName
    val themeColor = userPreferencesRepository.themeColor
    val customThemeColor = userPreferencesRepository.customThemeColor
    val themeFont = userPreferencesRepository.themeFont
    val customFontUri = userPreferencesRepository.customFontUri
    val timerAutoStart = userPreferencesRepository.timerAutoStart
    val timerAudioEnabled = userPreferencesRepository.timerAudioEnabled
    val timerVibrateEnabled = userPreferencesRepository.timerVibrateEnabled

    // Display settings
    val keepScreenOn = userPreferencesRepository.keepScreenOn
    val darkMode = userPreferencesRepository.darkMode
    val restDaysPerWeek = userPreferencesRepository.restDaysPerWeek
    val monthlyReportEnabled = userPreferencesRepository.monthlyReportEnabled

    // Plate calculator settings
    val barWeight = userPreferencesRepository.barWeight
    val loadingSides = userPreferencesRepository.loadingSides
    val availablePlates = userPreferencesRepository.availablePlates
    val plateInventorySettings = userPreferencesRepository.plateInventorySettings

    private val transferMutex = Mutex()
    private var accentWriteJob: Job? = null
    private var fontWriteJob: Job? = null

    private val _fontImportError = MutableStateFlow<String?>(null)
    val fontImportError: StateFlow<String?> = _fontImportError.asStateFlow()

    fun setPlateInventoryCount(weight: Float, count: Int) {
        viewModelScope.launch { userPreferencesRepository.setPlateInventoryCount(weight, count) }
    }

    fun setUsePlateInventory(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setUsePlateInventory(enabled) }
    }

    fun adjustPlateInventoryCount(weight: Float, delta: Int) {
        viewModelScope.launch { userPreferencesRepository.adjustPlateInventoryCount(weight, delta) }
    }

    fun setUserName(name: String) {
        viewModelScope.launch {
            userPreferencesRepository.setUserName(name)
        }
    }

    fun setThemeColor(color: String) {
        accentWriteJob?.cancel()
        accentWriteJob = viewModelScope.launch {
            userPreferencesRepository.setThemeColor(color)
        }
    }

    fun setCustomThemeColor(colorHex: String?) {
        accentWriteJob?.cancel()
        accentWriteJob = viewModelScope.launch {
            userPreferencesRepository.setCustomThemeColor(colorHex)
            if (!colorHex.isNullOrBlank()) {
                userPreferencesRepository.setThemeColor("custom")
            }
        }
    }

    fun clearCustomThemeColor(currentThemeColor: String = "") {
        accentWriteJob?.cancel()
        accentWriteJob = viewModelScope.launch {
            if (currentThemeColor == "custom") {
                userPreferencesRepository.setThemeColor(ThemePreset.SUMMER_SHRED.storageKey)
            }
            userPreferencesRepository.setCustomThemeColor(null)
        }
    }

    fun setThemeFont(font: String) {
        fontWriteJob?.cancel()
        _fontImportError.value = null
        fontWriteJob = viewModelScope.launch {
            userPreferencesRepository.setThemeFont(font)
        }
    }

    fun importCustomFont(uri: Uri) {
        fontWriteJob?.cancel()
        _fontImportError.value = null
        fontWriteJob = viewModelScope.launch {
            try {
                val errorMessage = withContext(Dispatchers.IO) {
                    val persisted = runCatching {
                        appContext.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    if (persisted.isFailure) {
                        return@withContext "Couldn't keep access to this font. Choose a file from your device storage."
                    }
                    val typeface = runCatching {
                        appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            AndroidTypeface.Builder(pfd.fileDescriptor).build()
                        }
                    }.getOrNull()
                    if (typeface == null) {
                        return@withContext "Couldn't load that font file. Choose a valid .ttf or .otf file."
                    }
                    null
                }
                if (errorMessage != null) {
                    _fontImportError.value = errorMessage
                    return@launch
                }
                userPreferencesRepository.setCustomFontUri(uri.toString())
                userPreferencesRepository.setThemeFont(ThemeFontOption.CUSTOM.storageKey)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _fontImportError.value = "Couldn't load that font file. Choose a valid .ttf or .otf file."
            }
        }
    }

    fun clearCustomFont(currentFontKey: String = "") {
        fontWriteJob?.cancel()
        _fontImportError.value = null
        fontWriteJob = viewModelScope.launch {
            if (currentFontKey == ThemeFontOption.CUSTOM.storageKey) {
                userPreferencesRepository.setThemeFont(ThemeFontOption.BEBAS_NEUE.storageKey)
            }
            userPreferencesRepository.setCustomFontUri(null)
        }
    }

    fun clearFontImportError() {
        _fontImportError.value = null
    }

    fun setTimerAutoStart(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTimerAutoStart(enabled)
        }
    }

    fun setTimerAudioEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTimerAudioEnabled(enabled)
        }
    }

    fun setTimerVibrateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTimerVibrateEnabled(enabled)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setKeepScreenOn(enabled)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDarkMode(enabled)
        }
    }

    fun setRestDaysPerWeek(days: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setRestDaysPerWeek(days)
        }
    }

    fun setMonthlyReportEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setMonthlyReportEnabled(enabled)
            if (enabled) monthlyReportScheduler.scheduleNext() else monthlyReportScheduler.cancel()
        }
    }

    fun setBarWeight(weight: Float) {
        viewModelScope.launch {
            userPreferencesRepository.setBarWeight(weight)
        }
    }

    fun setLoadingSides(sides: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setLoadingSides(sides)
        }
    }

    fun togglePlate(plate: Float) {
        viewModelScope.launch {
            userPreferencesRepository.togglePlate(plate)
        }
    }

    // FitNotes Import State
    sealed class ImportState {
        object Idle : ImportState()
        object InProgress : ImportState()
        data class Success(val result: FitNotesImporter.ImportResult) : ImportState()
        data class Error(val message: String) : ImportState()
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    fun importFitNotes(uri: Uri) {
        launchTransfer(
            stateFlow = _importState,
            inProgressState = ImportState.InProgress,
            idleState = ImportState.Idle,
            errorState = ImportState::Error,
            fallbackError = "Unknown error during import"
        ) {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: error("The selected CSV file could not be opened.")
            val result = input.use { stream -> fitNotesImporter.importCsv(stream) }
            ImportState.Success(result)
        }
    }

    fun clearImportState() {
        _importState.value = ImportState.Idle
    }

    // IronLog Export State
    sealed class ExportState {
        object Idle : ExportState()
        object InProgress : ExportState()
        data class Success(val result: IronLogExporter.ExportResult) : ExportState()
        data class Error(val message: String) : ExportState()
    }

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun exportData(uri: Uri) {
        launchTransfer(
            stateFlow = _exportState,
            inProgressState = ExportState.InProgress,
            idleState = ExportState.Idle,
            errorState = ExportState::Error,
            fallbackError = "Unknown error during export"
        ) {
            val output = appContext.contentResolver.openOutputStream(uri)
                ?: error("The backup file could not be opened for writing.")
            val result = output.use { stream -> ironLogExporter.export(stream) }
            ExportState.Success(result)
        }
    }

    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }

    // IronLog Import State
    sealed class IronLogImportState {
        object Idle : IronLogImportState()
        object InProgress : IronLogImportState()
        data class Success(val result: IronLogImporter.ImportResult) : IronLogImportState()
        data class Error(val message: String) : IronLogImportState()
    }

    private val _ironLogImportState = MutableStateFlow<IronLogImportState>(IronLogImportState.Idle)
    val ironLogImportState: StateFlow<IronLogImportState> = _ironLogImportState.asStateFlow()

    fun importIronLog(uri: Uri) {
        launchTransfer(
            stateFlow = _ironLogImportState,
            inProgressState = IronLogImportState.InProgress,
            idleState = IronLogImportState.Idle,
            errorState = IronLogImportState::Error,
            fallbackError = "Unknown error during import"
        ) {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: error("The selected ZIP backup could not be opened.")
            val result = input.use { stream -> ironLogImporter.import(stream) }
            IronLogImportState.Success(result)
        }
    }

    fun clearIronLogImportState() {
        _ironLogImportState.value = IronLogImportState.Idle
    }

    private inline fun <S> launchTransfer(
        stateFlow: MutableStateFlow<S>,
        inProgressState: S,
        idleState: S,
        crossinline errorState: (String) -> S,
        fallbackError: String,
        crossinline block: suspend () -> S
    ) {
        if (!transferMutex.tryLock()) return
        stateFlow.value = inProgressState
        val job = viewModelScope.launch {
            try {
                stateFlow.value = withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) {
                stateFlow.value = idleState
                throw e
            } catch (e: Exception) {
                stateFlow.value = errorState(e.message?.takeIf { it.isNotBlank() } ?: fallbackError)
            } finally {
                if (transferMutex.isLocked) {
                    transferMutex.unlock()
                }
            }
        }
        if (job.isCancelled) {
            stateFlow.value = idleState
            if (transferMutex.isLocked) {
                transferMutex.unlock()
            }
        }
    }
}
