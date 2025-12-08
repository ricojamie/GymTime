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
}
