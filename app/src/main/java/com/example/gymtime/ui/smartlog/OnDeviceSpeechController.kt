package com.example.gymtime.ui.smartlog

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class OnDeviceSpeechController(
    context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onPartialText: (String) -> Unit,
    private val onFinalText: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var audioFocusRequest: AudioFocusRequest? = null
    private var recognizer: SpeechRecognizer? = null
    private var activeSessionId: Long = 0L
    private var isSessionActive: Boolean = false
    private var isStopping: Boolean = false
    private var destroyed: Boolean = false

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (destroyed || !isSessionActive) return
            onListeningChanged(true)
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (destroyed || !isSessionActive) return
            abandonAudioFocus()
            onListeningChanged(false)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            if (destroyed || !isSessionActive) return
            bestResult(partialResults)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(onPartialText)
        }

        override fun onResults(results: Bundle?) {
            if (destroyed || !isSessionActive) return
            isSessionActive = false
            isStopping = false
            abandonAudioFocus()
            onListeningChanged(false)
            val text = bestResult(results)?.trim()
            if (text.isNullOrEmpty()) {
                onError("No speech was recognized. Try again or type the set.")
            } else {
                onFinalText(text)
            }
        }

        override fun onError(error: Int) {
            if (destroyed || !isSessionActive) return
            isSessionActive = false
            isStopping = false
            abandonAudioFocus()
            onListeningChanged(false)
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "No speech was recognized. Try again or type the set."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Microphone permission is needed for voice input."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "Voice input is busy. Wait a moment and try again."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "On-device speech is temporarily unavailable. You can still type the set."
                else -> "Voice input stopped. Try again or type the set."
            }
            onError(message)
        }
    }

    val isAvailable: Boolean = runCatching {
        SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
    }.getOrDefault(false)

    fun start() {
        runOnMain {
            if (destroyed || isSessionActive) return@runOnMain
            if (!isAvailable) {
                onError("On-device speech isn't available. You can still type the set.")
                return@runOnMain
            }
            val service = ensureRecognizer() ?: run {
                onError("On-device speech isn't available. You can still type the set.")
                return@runOnMain
            }
            val session = ++activeSessionId
            isSessionActive = true
            isStopping = false
            onListeningChanged(true)
            requestAudioFocus()
            try {
                service.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                )
            } catch (_: Throwable) {
                if (session == activeSessionId) {
                    isSessionActive = false
                    isStopping = false
                    abandonAudioFocus()
                    onListeningChanged(false)
                    onError("Voice input couldn't start. Try again or type the set.")
                }
            }
        }
    }

    fun stop() {
        runOnMain {
            if (destroyed || !isSessionActive || isStopping) return@runOnMain
            isStopping = true
            onListeningChanged(false)
            runCatching { recognizer?.stopListening() }.onFailure {
                isSessionActive = false
                isStopping = false
                abandonAudioFocus()
            }
        }
    }

    fun cancel() {
        runOnMain {
            if (destroyed) return@runOnMain
            isSessionActive = false
            isStopping = false
            activeSessionId += 1
            abandonAudioFocus()
            onListeningChanged(false)
            runCatching { recognizer?.cancel() }
        }
    }

    fun destroy() {
        runOnMain {
            if (destroyed) return@runOnMain
            destroyed = true
            isSessionActive = false
            isStopping = false
            activeSessionId += 1
            abandonAudioFocus()
            onListeningChanged(false)
            recognizer?.let { activeRecognizer ->
                runCatching { activeRecognizer.setRecognitionListener(NoOpRecognitionListener) }
                runCatching { activeRecognizer.cancel() }
                runCatching { activeRecognizer.destroy() }
            }
            recognizer = null
        }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        return runCatching {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext).also {
                it.setRecognitionListener(listener)
                recognizer = it
            }
        }.getOrNull()
    }

    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        if (audioFocusRequest != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ focusChange ->
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                    focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    stop()
                }
            }, mainHandler)
            .build()
        val result = runCatching { manager.requestAudioFocus(request) }.getOrNull()
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = request
        }
    }

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        audioFocusRequest = null
        runCatching { audioManager?.abandonAudioFocusRequest(request) }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun bestResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private object NoOpRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onError(error: Int) = Unit
        override fun onResults(results: Bundle?) = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
