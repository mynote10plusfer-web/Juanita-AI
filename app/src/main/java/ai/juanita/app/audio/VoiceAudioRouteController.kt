package ai.juanita.app.audio

import android.content.Context
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.os.Build
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log

/**
 * Places the app in Android's voice-communication mode for the lifetime of a
 * Gemini Live audio session, then restores the exact mode observed beforehand.
 *
 * On Android 12+ we choose an available communication output, preferring a connected
 * headset and falling back to the built-in speaker. The original selection is restored.
 */
class VoiceAudioRouteController(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private var previousMode: Int? = null
    private var previousCommunicationDevice: AudioDeviceInfo? = null

    @Synchronized
    fun begin() {
        if (previousMode != null) return
        try {
            previousMode = audioManager.mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                previousCommunicationDevice = audioManager.communicationDevice
            }
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Keep a connected headset when present; otherwise explicitly select the
                // built-in loudspeaker so voice-communication playback and capture share
                // Android's communication route and its available echo-processing path.
                val available = audioManager.availableCommunicationDevices
                val preferredTypes = listOf(
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                )
                val selected = preferredTypes.firstNotNullOfOrNull { type ->
                    available.firstOrNull { it.type == type }
                }
                if (selected != null && !audioManager.setCommunicationDevice(selected)) {
                    Log.w(TAG, "Android refused communication output route type=${selected.type}")
                }
            }

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
            val restoreMode = previousMode
            previousMode = null
            previousCommunicationDevice = null
            if (restoreMode != null) runCatching { audioManager.mode = restoreMode }
        }
    }

    @Synchronized
    fun end() {
        val restoreMode = previousMode ?: return
        previousMode = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val previousDevice = previousCommunicationDevice
                previousCommunicationDevice = null
                val stillAvailable = previousDevice?.let { old ->
                    audioManager.availableCommunicationDevices.firstOrNull { it.id == old.id }
                }
                if (stillAvailable != null) {
                    audioManager.setCommunicationDevice(stillAvailable)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            }
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
