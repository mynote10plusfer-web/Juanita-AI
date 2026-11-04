package ai.juanita.app.speech

import java.util.Locale

/**
 * Provider-neutral speech output contract.
 * Android TTS, sherpa-onnx, and optional cloud voices can implement this
 * without changing the conversation or UI layers.
 */
interface SpeechOutputProvider {
    fun speak(
        text: String,
        locale: Locale,
        onStarted: (() -> Unit)? = null,
        onCompleted: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    )

    fun stop()
    fun close()
}

/**
 * Provider-neutral recognition result. Audio capture and recognition
 * implementations must report errors explicitly rather than returning
 * fabricated transcripts.
 */
data class SpeechRecognitionResult(
    val text: String,
    val languageTag: String? = null,
    val isFinal: Boolean = true
)

interface SpeechRecognitionProvider {
    fun start(
        languageTag: String,
        onPartialResult: (SpeechRecognitionResult) -> Unit,
        onFinalResult: (SpeechRecognitionResult) -> Unit,
        onError: (String) -> Unit
    )

    fun stop()
    fun close()
}
