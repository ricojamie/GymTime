package com.example.gymtime.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.notifications.MonthlyReportScheduler
import com.example.gymtime.util.FitNotesImporter
import com.example.gymtime.util.IronLogExporter
import com.example.gymtime.util.IronLogImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
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

    fun setUserName(name: String) {
        viewModelScope.launch {
            userPreferencesRepository.setUserName(name)
        }
    }

    fun setThemeColor(color: String) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeColor(color)
        }
    }

    fun setCustomThemeColor(colorHex: String?) {
        viewModelScope.launch {
            userPreferencesRepository.setCustomThemeColor(colorHex)
            if (!colorHex.isNullOrBlank()) {
                userPreferencesRepository.setThemeColor("custom")
            }
        }
    }

    fun clearCustomThemeColor() {
        viewModelScope.launch {
            userPreferencesRepository.setCustomThemeColor(null)
        }
    }

    fun setThemeFont(font: String) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeFont(font)
        }
    }

    fun setCustomFontUri(uri: String?) {
        viewModelScope.launch {
            userPreferencesRepository.setCustomFontUri(uri)
            if (!uri.isNullOrBlank()) {
                userPreferencesRepository.setThemeFont("custom")
            }
        }
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

    fun togglePlate(plate: Float, currentPlates: List<Float>) {
        viewModelScope.launch {
            userPreferencesRepository.togglePlate(plate, currentPlates)
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

    fun importFitNotes(inputStream: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            _importState.value = ImportState.InProgress
            try {
                val result = fitNotesImporter.importCsv(inputStream)
                _importState.value = ImportState.Success(result)
            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.message ?: "Unknown error during import")
            }
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

    fun exportData(outputStream: OutputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            _exportState.value = ExportState.InProgress
            try {
                val result = ironLogExporter.export(outputStream)
                _exportState.value = ExportState.Success(result)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Unknown error during export")
            }
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

    fun importIronLog(inputStream: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            _ironLogImportState.value = IronLogImportState.InProgress
            try {
                val result = ironLogImporter.import(inputStream)
                _ironLogImportState.value = IronLogImportState.Success(result)
            } catch (e: Exception) {
                _ironLogImportState.value = IronLogImportState.Error(e.message ?: "Unknown error during import")
            }
        }
    }

    fun clearIronLogImportState() {
        _ironLogImportState.value = IronLogImportState.Idle
    }
}
