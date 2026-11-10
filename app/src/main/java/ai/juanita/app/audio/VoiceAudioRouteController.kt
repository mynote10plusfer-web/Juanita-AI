package ai.juanita.app.audio

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log

/**
 * Places the app in Android's voice-communication mode for the lifetime of a
 * Gemini Live audio session, then restores the exact mode observed beforehand.
 *
 * We deliberately do not force speakerphone or select an output device: Android
 * and the user retain control of the current route (speaker, wired headset, BT).
 */
class VoiceAudioRouteController(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private var previousMode: Int? = null

    @Synchronized
    fun begin() {
        if (previousMode != null) return
        try {
            previousMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            val outputs = runCatching {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .joinToString { "${it.productName}/type=${it.type}" }
            }.getOrDefault("unavailable")
            Log.i(TAG, "Voice route started: mode=${audioManager.mode}, outputs=[$outputs], " +
                "AEC_available=${runCatching { AcousticEchoCanceler.isAvailable() }.getOrDefault(false)}, " +
                "NS_available=${runCatching { NoiseSuppressor.isAvailable() }.getOrDefault(false)}. " +
                "Effect availability does not prove attachment to Firebase's recorder.")
        } catch (error: Exception) {
            Log.w(TAG, "Could not enter Android communication audio mode", error)
            previousMode = null
        }
    }

    @Synchronized
    fun end() {
        val restoreMode = previousMode ?: return
        previousMode = null
        try {
            audioManager.mode = restoreMode
            Log.i(TAG, "Voice route ended; restored AudioManager mode=$restoreMode")
        } catch (error: Exception) {
            Log.w(TAG, "Could not restore previous Android audio mode", error)
        }
    }

    companion object {
        private const val TAG = "JuanitaAudioRoute"
    }
}
