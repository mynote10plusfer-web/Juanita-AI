package ai.juanita.app.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ActivityDetectionConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.RealtimeInputConfig
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import com.google.firebase.ai.type.realtimeInputConfig

/**
 * Lifecycle wrapper for Firebase AI Logic Gemini Live audio.
 *
 * Uses server-side voice activity detection and Gemini's native interruption
 * handling. The SDK owns microphone capture and speaker playback.
 * Microphone permission must be granted by the Activity before start().
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

                // Natural turn-taking: detect the user's speech automatically,
                // but tolerate short pauses and avoid reacting to tiny noises.
                realtimeInputConfig = realtimeInputConfig {
                    automaticActivityDetection = ActivityDetectionConfig.builder()
                        .setStartSensitivity(ActivityDetectionConfig.Sensitivity.LOW)
                        .setEndSensitivity(ActivityDetectionConfig.Sensitivity.LOW)
                        .setPrefixPaddingMs(40)
                        .setSilenceDurationMs(SILENCE_DURATION_MS)
                        .build()
                    activityHandling = RealtimeInputConfig.ActivityHandling.INTERRUPT
                    turnCoverage = RealtimeInputConfig.TurnCoverage.ONLY_ACTIVITY
                }
            }
        )

        val connected = model.connect()
        session = connected

        // The Firebase SDK manages microphone capture and speaker playback.
        // Native interruptions let the user speak over Juanita when supported
        // by the device's audio stack.
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
        private const val SILENCE_DURATION_MS = 800
    }
}
