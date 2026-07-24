package com.example.gymtime.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface AiCapability {
    data object Checking : AiCapability
    data object Unavailable : AiCapability
    data object Downloadable : AiCapability
    data object Downloading : AiCapability
    data class Ready(val modelName: String? = null) : AiCapability
    data class Error(val error: AiError, val message: String? = null) : AiCapability
}

sealed interface AiDownloadState {
    data object Idle : AiDownloadState
    data class Started(val totalBytes: Long) : AiDownloadState
    data class Progress(val bytesDownloaded: Long, val totalBytes: Long?) : AiDownloadState
    data class Completed(val modelName: String? = null) : AiDownloadState
    data class Failed(val error: AiError, val message: String? = null) : AiDownloadState
}

enum class AiError {
    NOT_AVAILABLE,
    BUSY,
    QUOTA_EXCEEDED,
    BACKGROUND_USE_BLOCKED,
    NOT_ENOUGH_DISK_SPACE,
    SYSTEM_UPDATE_REQUIRED,
    REQUEST_TOO_LARGE,
    CANCELLED,
    INVALID_RESPONSE,
    UNKNOWN
}

data class AiTextRequest(
    val prompt: String,
    val temperature: Float = 0.2f,
    val topK: Int = 10,
    val maxOutputTokens: Int = 96
) {
    init {
        require(prompt.isNotBlank()) { "Prompt cannot be blank" }
        require(temperature in 0f..2f) { "Temperature must be between 0 and 2" }
        require(topK > 0) { "topK must be positive" }
        require(maxOutputTokens > 0) { "maxOutputTokens must be positive" }
    }
}

sealed interface AiGenerationResult<out T> {
    data class Success<T>(val value: T, val modelName: String? = null) : AiGenerationResult<T>
    data class Failure(
        val error: AiError,
        val message: String? = null,
        val retryable: Boolean = false
    ) : AiGenerationResult<Nothing>
}

interface OnDeviceAiClient {
    val capability: StateFlow<AiCapability>

    suspend fun refreshCapability(): AiCapability

    fun downloadModel(): Flow<AiDownloadState>

    suspend fun warmUp(): AiGenerationResult<Unit>

    suspend fun generateText(request: AiTextRequest): AiGenerationResult<String>
}
