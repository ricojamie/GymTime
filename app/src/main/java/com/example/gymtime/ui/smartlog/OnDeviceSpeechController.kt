package com.example.gymtime.ui.smartlog

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onListeningChanged(true)
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = onListeningChanged(false)
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            bestResult(partialResults)?.let(onPartialText)
        }

        override fun onResults(results: Bundle?) {
            onListeningChanged(false)
            val text = bestResult(results)
            if (text.isNullOrBlank()) onError("No speech was recognized. Try again or type the set.")
            else onFinalText(text)
        }

        override fun onError(error: Int) {
            onListeningChanged(false)
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was recognized. Try again or type the set."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice input."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice input is busy. Wait a moment and try again."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "On-device speech is temporarily unavailable. You can still type the set."
                else -> "Voice input stopped. Try again or type the set."
            }
            onError(message)
        }
    }

    val isAvailable: Boolean = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    private val recognizer: SpeechRecognizer? = if (isAvailable) {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { it.setRecognitionListener(listener) }
    } else null

    fun start() {
        val service = recognizer ?: run {
            onError("On-device speech isn't available. You can still type the set.")
            return
        }
        service.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.cancel()
        recognizer?.destroy()
    }

    private fun bestResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
