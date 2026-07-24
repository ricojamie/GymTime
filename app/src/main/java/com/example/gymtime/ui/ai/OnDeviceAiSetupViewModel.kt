package com.example.gymtime.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.ai.AiCapability
import com.example.gymtime.ai.AiDownloadState
import com.example.gymtime.ai.OnDeviceAiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class AiSetupPromptSession @Inject constructor() {
    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    fun dismiss() {
        _dismissed.value = true
    }
}

@HiltViewModel
class OnDeviceAiSetupViewModel @Inject constructor(
    private val aiClient: OnDeviceAiClient,
    private val promptSession: AiSetupPromptSession
) : ViewModel() {
    val capability = aiClient.capability
    val dismissed = promptSession.dismissed

    private val _downloadState = MutableStateFlow<AiDownloadState>(AiDownloadState.Idle)
    val downloadState: StateFlow<AiDownloadState> = _downloadState.asStateFlow()

    init {
        retry()
    }

    fun retry() {
        viewModelScope.launch { aiClient.refreshCapability() }
    }

    fun download() {
        viewModelScope.launch {
            aiClient.downloadModel().collect { _downloadState.value = it }
        }
    }

    fun dismiss() {
        promptSession.dismiss()
    }

    fun shouldShow(capability: AiCapability, downloadState: AiDownloadState, dismissed: Boolean): Boolean {
        if (dismissed || capability is AiCapability.Ready) return false
        return capability == AiCapability.Downloadable ||
            capability == AiCapability.Downloading ||
            downloadState is AiDownloadState.Failed
    }
}
