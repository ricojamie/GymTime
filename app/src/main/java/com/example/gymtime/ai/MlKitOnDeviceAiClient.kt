package com.example.gymtime.ai

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
class MlKitOnDeviceAiClient @Inject constructor() : OnDeviceAiClient {
    private val lifecycleMutex = Mutex()
    private val statusMutex = Mutex()
    private val downloadMutex = Mutex()
    private val inferenceMutex = Mutex()
    private val activeDownload = AtomicBoolean(false)
    private val observerRegistered = AtomicBoolean(false)
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var model: GenerativeModel? = null
    private var activeUsers = 0
    private var pendingClose = false

    private val _capability = MutableStateFlow<AiCapability>(AiCapability.Checking)
    override val capability = _capability.asStateFlow()

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            clientScope.launch { close() }
        }
    }

    override suspend fun refreshCapability(): AiCapability {
        ensureLifecycleObserver()
        if (activeDownload.get()) {
            return _capability.value
        }
        return statusMutex.withLock {
            if (activeDownload.get()) {
                return@withLock _capability.value
            }
            if (_capability.value is AiCapability.Checking) {
                _capability.value = AiCapability.Checking
            }
            val status = checkStatusSafely()
            if (!activeDownload.get() && !status.isBackgroundBlockedError()) {
                _capability.value = status
            }
            status
        }
    }

    override fun downloadModel(): Flow<AiDownloadState> = flow {
        ensureLifecycleObserver()
        downloadMutex.withLock {
            var terminalReached = false
            activeDownload.set(true)
            try {
                val status = statusMutex.withLock {
                    val current = checkStatusSafely()
                    if (!current.isBackgroundBlockedError()) {
                        _capability.value = current
                    }
                    current
                }
                when (status) {
                    is AiCapability.Ready -> {
                        terminalReached = true
                        emit(AiDownloadState.Completed(status.modelName))
                        return@withLock
                    }
                    AiCapability.Downloading,
                    AiCapability.Downloadable -> Unit
                    AiCapability.Checking,
                    AiCapability.Unavailable,
                    is AiCapability.Error -> {
                        terminalReached = true
                        val error = (status as? AiCapability.Error)?.error ?: AiError.NOT_AVAILABLE
                        emit(AiDownloadState.Failed(error, (status as? AiCapability.Error)?.message))
                        return@withLock
                    }
                }

                var totalBytes: Long? = null
                _capability.value = AiCapability.Downloading
                withModel { activeModel ->
                    activeModel.download().collect { downloadStatus ->
                        when (downloadStatus) {
                            is DownloadStatus.DownloadStarted -> {
                                totalBytes = downloadStatus.bytesToDownload
                                emit(AiDownloadState.Started(downloadStatus.bytesToDownload))
                            }
                            is DownloadStatus.DownloadProgress -> emit(
                                AiDownloadState.Progress(downloadStatus.totalBytesDownloaded, totalBytes)
                            )
                            DownloadStatus.DownloadCompleted -> {
                                terminalReached = true
                                val ready = AiCapability.Ready(modelNameOrNull(activeModel))
                                _capability.value = ready
                                emit(AiDownloadState.Completed(ready.modelName))
                            }
                            is DownloadStatus.DownloadFailed -> {
                                terminalReached = true
                                val failure = downloadStatus.e.toFailure()
                                _capability.value = AiCapability.Error(failure.error, failure.message)
                                emit(AiDownloadState.Failed(failure.error, failure.message))
                            }
                        }
                    }
                }
                if (!terminalReached) {
                    val settled = checkStatusSafely()
                    if (!settled.isBackgroundBlockedError()) {
                        _capability.value = settled
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    activeDownload.set(false)
                    if (!terminalReached && _capability.value == AiCapability.Downloading) {
                        val settled = checkStatusSafely()
                        _capability.value = if (settled.isBackgroundBlockedError()) {
                            AiCapability.Downloadable
                        } else {
                            settled
                        }
                    }
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
        if (!isAppInForeground()) {
            return AiGenerationResult.Failure(
                error = AiError.BACKGROUND_USE_BLOCKED,
                retryable = true
            )
        }
        return inferenceMutex.withLock {
            if (!isAppInForeground()) {
                return@withLock AiGenerationResult.Failure(
                    error = AiError.BACKGROUND_USE_BLOCKED,
                    retryable = true
                )
            }
            withModel { activeModel ->
                val status = resolveStatusForInference(activeModel)
                val readiness = status as? AiCapability.Ready
                    ?: return@withModel failureForCapability(status)
                try {
                    withTimeout(WARMUP_TIMEOUT_MS) {
                        withContext(Dispatchers.IO) {
                            activeModel.warmup()
                        }
                    }
                    AiGenerationResult.Success(Unit, readiness.modelName)
                } catch (_: TimeoutCancellationException) {
                    AiGenerationResult.Failure(
                        error = AiError.BUSY,
                        message = "Timed out warming up Gemini Nano",
                        retryable = true
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    error.toFailure()
                }
            }
        }
    }

    override suspend fun generateText(request: AiTextRequest): AiGenerationResult<String> {
        if (!isAppInForeground()) {
            return AiGenerationResult.Failure(
                error = AiError.BACKGROUND_USE_BLOCKED,
                retryable = true
            )
        }
        return inferenceMutex.withLock {
            if (!isAppInForeground()) {
                return@withLock AiGenerationResult.Failure(
                    error = AiError.BACKGROUND_USE_BLOCKED,
                    retryable = true
                )
            }
            withModel { activeModel ->
                val status = resolveStatusForInference(activeModel)
                val readiness = status as? AiCapability.Ready
                    ?: return@withModel failureForCapability(status)
                try {
                    val response = withTimeout(INFERENCE_TIMEOUT_MS) {
                        withContext(Dispatchers.IO) {
                            activeModel.generateContent(
                                generateContentRequest(TextPart(request.prompt)) {
                                    temperature = request.temperature
                                    topK = request.topK
                                    candidateCount = 1
                                    maxOutputTokens = request.maxOutputTokens
                                }
                            )
                        }
                    }
                    val text = response.candidates.firstOrNull()?.text?.trim().orEmpty()
                    if (text.isEmpty()) {
                        AiGenerationResult.Failure(AiError.INVALID_RESPONSE, "Gemini Nano returned no text")
                    } else {
                        AiGenerationResult.Success(text, readiness.modelName ?: modelNameOrNull(activeModel))
                    }
                } catch (_: TimeoutCancellationException) {
                    AiGenerationResult.Failure(
                        error = AiError.BUSY,
                        message = "On-device AI generation timed out",
                        retryable = true
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    error.toFailure()
                }
            }
        }
    }

    suspend fun close() {
        val toClose = lifecycleMutex.withLock {
            if (activeUsers == 0) {
                pendingClose = false
                model.also { model = null }
            } else {
                pendingClose = true
                null
            }
        }
        if (toClose != null) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { toClose.close() }
            }
        }
    }

    private suspend fun <T> withModel(block: suspend (GenerativeModel) -> T): T {
        val instance = lifecycleMutex.withLock {
            val current = model ?: withContext(Dispatchers.IO) {
                Generation.getClient()
            }.also { model = it }
            pendingClose = false
            activeUsers += 1
            current
        }
        try {
            return block(instance)
        } finally {
            withContext(NonCancellable) {
                val toClose = lifecycleMutex.withLock {
                    activeUsers = (activeUsers - 1).coerceAtLeast(0)
                    if (pendingClose && activeUsers == 0) {
                        pendingClose = false
                        model.also { model = null }
                    } else {
                        null
                    }
                }
                if (toClose != null) {
                    withContext(Dispatchers.IO) {
                        runCatching { toClose.close() }
                    }
                }
            }
        }
    }

    private suspend fun ensureLifecycleObserver() {
        if (observerRegistered.get()) return
        withContext(Dispatchers.Main.immediate) {
            try {
                val lifecycle = ProcessLifecycleOwner.get().lifecycle
                if (observerRegistered.compareAndSet(false, true)) {
                    lifecycle.addObserver(processObserver)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Safe no-op in pure JVM unit tests without ProcessLifecycleOwner init.
            }
        }
    }

    private suspend fun isAppInForeground(): Boolean = withContext(Dispatchers.Main.immediate) {
        try {
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            if (observerRegistered.compareAndSet(false, true)) {
                lifecycle.addObserver(processObserver)
            }
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun resolveStatusForInference(activeModel: GenerativeModel): AiCapability {
        if (activeDownload.get()) return AiCapability.Downloading
        val status = checkStatusWithModel(activeModel)
        if (!activeDownload.get() && !status.isBackgroundBlockedError()) {
            _capability.value = status
        }
        return status
    }

    private suspend fun checkStatusSafely(): AiCapability = try {
        withModel { activeModel -> checkStatusWithModel(activeModel) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        val failure = error.toFailure()
        AiCapability.Error(failure.error, failure.message)
    }

    private suspend fun checkStatusWithModel(activeModel: GenerativeModel): AiCapability = try {
        withTimeout(STATUS_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                when (activeModel.checkStatus()) {
                    FeatureStatus.UNAVAILABLE -> AiCapability.Unavailable
                    FeatureStatus.DOWNLOADABLE -> AiCapability.Downloadable
                    FeatureStatus.DOWNLOADING -> AiCapability.Downloading
                    FeatureStatus.AVAILABLE -> AiCapability.Ready(modelNameOrNull(activeModel))
                    else -> AiCapability.Error(AiError.UNKNOWN, "Unknown Gemini Nano status")
                }
            }
        }
    } catch (_: TimeoutCancellationException) {
        AiCapability.Error(AiError.BUSY, "Timed out checking Gemini Nano status")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        val failure = error.toFailure()
        AiCapability.Error(failure.error, failure.message)
    }

    private suspend fun modelNameOrNull(activeModel: GenerativeModel): String? = try {
        withTimeout(MODEL_NAME_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                activeModel.getBaseModelName()
            }
        }
    } catch (_: TimeoutCancellationException) {
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    private fun AiCapability.isBackgroundBlockedError(): Boolean =
        this is AiCapability.Error && error == AiError.BACKGROUND_USE_BLOCKED

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

    private companion object {
        const val STATUS_TIMEOUT_MS = 10_000L
        const val MODEL_NAME_TIMEOUT_MS = 5_000L
        const val WARMUP_TIMEOUT_MS = 15_000L
        const val INFERENCE_TIMEOUT_MS = 15_000L
    }
}
