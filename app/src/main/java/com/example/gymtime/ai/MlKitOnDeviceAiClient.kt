package com.example.gymtime.ai

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class MlKitOnDeviceAiClient @Inject constructor() : OnDeviceAiClient {
    private val model: GenerativeModel = Generation.getClient()
    private val inferenceMutex = Mutex()
    private val _capability = MutableStateFlow<AiCapability>(AiCapability.Checking)
    override val capability = _capability.asStateFlow()

    override suspend fun refreshCapability(): AiCapability {
        _capability.value = AiCapability.Checking
        val status = try {
            when (model.checkStatus()) {
                FeatureStatus.UNAVAILABLE -> AiCapability.Unavailable
                FeatureStatus.DOWNLOADABLE -> AiCapability.Downloadable
                FeatureStatus.DOWNLOADING -> AiCapability.Downloading
                FeatureStatus.AVAILABLE -> AiCapability.Ready(modelNameOrNull())
                else -> AiCapability.Error(AiError.UNKNOWN, "Unknown Gemini Nano status")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val failure = error.toFailure()
            AiCapability.Error(failure.error, failure.message)
        }
        _capability.value = status
        return status
    }

    override fun downloadModel(): Flow<AiDownloadState> = flow {
        val status = refreshCapability()
        when (status) {
            is AiCapability.Ready -> {
                emit(AiDownloadState.Completed(status.modelName))
                return@flow
            }
            AiCapability.Downloading,
            AiCapability.Downloadable -> Unit
            AiCapability.Checking,
            AiCapability.Unavailable,
            is AiCapability.Error -> {
                val error = (status as? AiCapability.Error)?.error ?: AiError.NOT_AVAILABLE
                emit(AiDownloadState.Failed(error, (status as? AiCapability.Error)?.message))
                return@flow
            }
        }

        var totalBytes: Long? = null
        _capability.value = AiCapability.Downloading
        model.download().collect { downloadStatus ->
            when (downloadStatus) {
                is DownloadStatus.DownloadStarted -> {
                    totalBytes = downloadStatus.bytesToDownload
                    emit(AiDownloadState.Started(downloadStatus.bytesToDownload))
                }
                is DownloadStatus.DownloadProgress -> emit(
                    AiDownloadState.Progress(downloadStatus.totalBytesDownloaded, totalBytes)
                )
                DownloadStatus.DownloadCompleted -> {
                    val ready = AiCapability.Ready(modelNameOrNull())
                    _capability.value = ready
                    emit(AiDownloadState.Completed(ready.modelName))
                }
                is DownloadStatus.DownloadFailed -> {
                    val failure = downloadStatus.e.toFailure()
                    _capability.value = AiCapability.Error(failure.error, failure.message)
                    emit(AiDownloadState.Failed(failure.error, failure.message))
                }
            }
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        val failure = error.toFailure()
        _capability.value = AiCapability.Error(failure.error, failure.message)
        emit(AiDownloadState.Failed(failure.error, failure.message))
    }

    override suspend fun warmUp(): AiGenerationResult<Unit> {
        val readiness = requireForegroundReady() ?: return failureForCapability(_capability.value)
        return inferenceMutex.withLock {
            try {
                model.warmup()
                AiGenerationResult.Success(Unit, readiness.modelName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                error.toFailure()
            }
        }
    }

    override suspend fun generateText(request: AiTextRequest): AiGenerationResult<String> {
        val readiness = requireForegroundReady() ?: return failureForCapability(_capability.value)
        return inferenceMutex.withLock {
            try {
                val response = model.generateContent(
                    generateContentRequest(TextPart(request.prompt)) {
                        temperature = request.temperature
                        topK = request.topK
                        candidateCount = 1
                        maxOutputTokens = request.maxOutputTokens
                    }
                )
                val text = response.candidates.firstOrNull()?.text?.trim().orEmpty()
                if (text.isEmpty()) {
                    AiGenerationResult.Failure(AiError.INVALID_RESPONSE, "Gemini Nano returned no text")
                } else {
                    AiGenerationResult.Success(text, readiness.modelName ?: modelNameOrNull())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                error.toFailure()
            }
        }
    }

    private suspend fun requireForegroundReady(): AiCapability.Ready? {
        if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            _capability.value = AiCapability.Error(AiError.BACKGROUND_USE_BLOCKED)
            return null
        }
        return refreshCapability() as? AiCapability.Ready
    }

    private suspend fun modelNameOrNull(): String? = runCatching { model.getBaseModelName() }.getOrNull()

    private fun failureForCapability(capability: AiCapability): AiGenerationResult.Failure = when (capability) {
        AiCapability.Downloadable,
        AiCapability.Downloading,
        AiCapability.Unavailable,
        AiCapability.Checking -> AiGenerationResult.Failure(AiError.NOT_AVAILABLE)
        is AiCapability.Ready -> AiGenerationResult.Failure(AiError.UNKNOWN)
        is AiCapability.Error -> AiGenerationResult.Failure(
            error = capability.error,
            message = capability.message,
            retryable = capability.error == AiError.BUSY
        )
    }

    private fun Throwable.toFailure(): AiGenerationResult.Failure {
        val genAi = this as? GenAiException
        val error = when (genAi?.errorCode) {
            GenAiException.ErrorCode.NOT_AVAILABLE -> AiError.NOT_AVAILABLE
            GenAiException.ErrorCode.BUSY -> AiError.BUSY
            GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> AiError.QUOTA_EXCEEDED
            GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> AiError.BACKGROUND_USE_BLOCKED
            GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> AiError.NOT_ENOUGH_DISK_SPACE
            GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE,
            GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> AiError.SYSTEM_UPDATE_REQUIRED
            GenAiException.ErrorCode.REQUEST_TOO_LARGE -> AiError.REQUEST_TOO_LARGE
            GenAiException.ErrorCode.CANCELLED -> AiError.CANCELLED
            else -> AiError.UNKNOWN
        }
        return AiGenerationResult.Failure(
            error = error,
            message = message,
            retryable = error == AiError.BUSY || error == AiError.UNKNOWN
        )
    }
}
