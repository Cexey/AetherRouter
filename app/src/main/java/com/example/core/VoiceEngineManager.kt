package com.example.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class VoiceUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val speakingMessageId: String? = null,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val amplitude: Float = 0f,
    val errorMessage: String? = null,
    val isRecognizerAvailable: Boolean = true,
    val isTtsReady: Boolean = false,
)

/**
 * Native on-device Android SpeechRecognizer + TextToSpeech controller.
 * Keeps all SpeechRecognizer calls on the Main thread and strips Markdown punctuation for natural TTS.
 */
class VoiceEngineManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var onFinalSpeechCallback: ((String) -> Unit)? = null

    private val _state = MutableStateFlow(
        VoiceUiState(isRecognizerAvailable = SpeechRecognizer.isRecognitionAvailable(context)),
    )
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    init {
        initTts()
    }

    private fun initTts() {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                val ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.language = Locale.getDefault()
                    tts?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                _state.value = _state.value.copy(isSpeaking = true)
                            }

                            override fun onDone(utteranceId: String?) {
                                _state.value = _state.value.copy(isSpeaking = false, speakingMessageId = null)
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                _state.value = _state.value.copy(isSpeaking = false, speakingMessageId = null)
                            }
                        },
                    )
                }
                _state.value = _state.value.copy(isTtsReady = ready)
            }
        }
    }

    fun startListening(onFinalResult: (String) -> Unit) {
        mainHandler.post {
            stopSpeaking()
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _state.value = _state.value.copy(
                    isRecognizerAvailable = false,
                    errorMessage = "Speech recognition service is not installed on this device.",
                )
                return@post
            }
            onFinalSpeechCallback = onFinalResult
            _state.value = _state.value.copy(
                isListening = true,
                partialTranscript = "",
                finalTranscript = "",
                amplitude = 0.08f,
                errorMessage = null,
            )

            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.value = _state.value.copy(isListening = true, errorMessage = null)
                    }

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                        _state.value = _state.value.copy(amplitude = normalized)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        _state.value = _state.value.copy(amplitude = 0.05f)
                    }

                    override fun onError(error: Int) {
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech network unavailable"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — tap mic to try again"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out"
                            else -> "Speech recognition paused"
                        }
                        _state.value = _state.value.copy(
                            isListening = false,
                            amplitude = 0f,
                            errorMessage = msg,
                        )
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        _state.value = _state.value.copy(
                            isListening = false,
                            amplitude = 0f,
                            partialTranscript = text,
                            finalTranscript = text,
                        )
                        if (text.isNotEmpty()) {
                            onFinalSpeechCallback?.invoke(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim().orEmpty()
                        if (partial.isNotEmpty()) {
                            _state.value = _state.value.copy(partialTranscript = partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                },
            )

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            runCatching { recognizer.startListening(intent) }.onFailure { err ->
                _state.value = _state.value.copy(
                    isListening = false,
                    amplitude = 0f,
                    errorMessage = err.message ?: "Unable to start microphone",
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            _state.value = _state.value.copy(isListening = false, amplitude = 0f)
        }
    }

    fun clearTranscript() {
        _state.value = _state.value.copy(partialTranscript = "", finalTranscript = "", errorMessage = null)
    }

    fun speakText(messageId: String?, rawMarkdown: String) {
        val engine = tts ?: return
        val clean = sanitizeForSpeech(rawMarkdown)
        if (clean.isBlank()) return
        val utteranceId = messageId ?: UUID.randomUUID().toString()
        _state.value = _state.value.copy(isSpeaking = true, speakingMessageId = messageId)
        engine.speak(clean.take(3800), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
        _state.value = _state.value.copy(isSpeaking = false, speakingMessageId = null)
    }

    fun release() {
        mainHandler.post {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }

    private fun sanitizeForSpeech(markdown: String): String {
        return markdown
            .replace(Regex("```[\\s\\S]*?```"), " Code snippet omitted. ")
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
            .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("[#*_`~>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
