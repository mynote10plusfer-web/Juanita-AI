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

/**
 * Small lifecycle wrapper for the Firebase AI Logic Gemini Live audio session.
 * Microphone permission must be granted by the Activity before start() is called.
 */
@OptIn(PublicPreviewAPI::class)
class GeminiLiveAssistant {
    private var session: LiveSession? = null

    suspend fun start(systemInstruction: String) {
        stop()
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = MODEL_NAME,
            systemInstruction = content { text(systemInstruction) },
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice("Kore"))
            }
        )
        val connected = model.connect()
        session = connected
        connected.startAudioConversation(enableInterruptions = true)
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
    }
}
