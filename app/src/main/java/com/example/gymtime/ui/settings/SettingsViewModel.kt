package com.example.gymtime.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val userName = userPreferencesRepository.userName
    val themeColor = userPreferencesRepository.themeColor
    val timerAutoStart = userPreferencesRepository.timerAutoStart

    // Plate calculator settings
    val barWeight = userPreferencesRepository.barWeight
    val loadingSides = userPreferencesRepository.loadingSides

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
}
