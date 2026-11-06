package ai.juanita.app.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig

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

    suspend fun stop() {
        session?.let {
            it.stopAudioConversation()
            it.close()
        }
        session = null
    }

    companion object {
        const val MODEL_NAME = "gemini-3.1-flash-live-preview"
        const val DEFAULT_VOICE = "Kore"
    }
}
