package com.example.gymtime.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.util.FitNotesImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val fitNotesImporter: FitNotesImporter
) : ViewModel() {

    val userName = userPreferencesRepository.userName
    val themeColor = userPreferencesRepository.themeColor
    val timerAutoStart = userPreferencesRepository.timerAutoStart
    val timerAudioEnabled = userPreferencesRepository.timerAudioEnabled
    val timerVibrateEnabled = userPreferencesRepository.timerVibrateEnabled

    // Display settings
    val keepScreenOn = userPreferencesRepository.keepScreenOn
    val darkMode = userPreferencesRepository.darkMode

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
}
