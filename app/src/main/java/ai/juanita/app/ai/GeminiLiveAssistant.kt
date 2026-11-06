package ai.juanita.app.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import android.graphics.Bitmap
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.withTimeoutOrNull

/** Stable Gemini Live wrapper; transcript text is exposed without storing audio. */
@OptIn(PublicPreviewAPI::class)
class GeminiLiveAssistant {
    private var session: LiveSession? = null

    suspend fun start(
        systemInstruction: String,
        voiceName: String = DEFAULT_VOICE,
        onTranscript: (role: String, text: String) -> Unit = { _, _ -> }
    ) {
        stop()
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = MODEL_NAME,
            systemInstruction = content { text(systemInstruction) },
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                inputAudioTranscription = AudioTranscriptionConfig()
                outputAudioTranscription = AudioTranscriptionConfig()
                speechConfig = SpeechConfig(voice = Voice(voiceName.ifBlank { DEFAULT_VOICE }))
            }
        )
        val connected = model.connect()
        session = connected

        // Keep the known-good SDK-managed microphone/speaker path and server VAD.
        connected.startAudioConversation(
            transcriptHandler = { input, output ->
                input?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { onTranscript("USER", it) }
                output?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { onTranscript("ASSISTANT", it) }
            },
            enableInterruptions = false
        )
    }

    suspend fun sendVideoFrame(bitmap: Bitmap) {
        val activeSession = session ?: return
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 78, output)
        activeSession.sendVideoRealtime(InlineData(output.toByteArray(), "image/jpeg"))
    }

    /** Stop microphone capture synchronously before any slower cleanup begins. */
    fun stopAudioCapture() {
        session?.let { active ->
            runCatching { active.stopAudioConversation() }
        }
    }

    suspend fun stop() {
        val active = session ?: return
        // Detach first so a repeated stop is harmless and cannot revive this session.
        session = null
        runCatching { active.stopAudioConversation() }
        // Closing the websocket is bounded; the microphone is already stopped above.
        withTimeoutOrNull(4_000L) {
            runCatching { active.close() }
        }
    }

    companion object {
        const val MODEL_NAME = "gemini-3.1-flash-live-preview"
        const val DEFAULT_VOICE = "Kore"
    }
}
