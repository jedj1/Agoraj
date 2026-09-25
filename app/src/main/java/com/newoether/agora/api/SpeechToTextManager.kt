package com.newoether.agora.api

import android.content.Context
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class SpeechToTextManager(
    private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun startListening(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit): Boolean {
        if (!hasRecordPermission()) {
            onError("Microphone permission is required")
            return false
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this device")
            return false
        }

        stopListening()
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext ?: context)
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                onError("STT error: $error")
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.orEmpty()
                if (text.isNullOrBlank().not()) {
                    onFinal(text)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.orEmpty()
                if (text.isNullOrBlank().not()) {
                    onPartial(text)
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer?.startListening(intent)
        return true
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
        } catch (_: Throwable) {
        }
    }

    fun cancel() {
        try {
            recognizer?.cancel()
        } catch (_: Throwable) {
        }
        recognizer = null
    }

    fun release() = cancel()
}
