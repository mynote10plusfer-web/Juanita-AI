package ai.juanita.app.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log

/**
 * Owns Android communication routing and transient audio focus for one Gemini Live session.
 * Audio focus is abandoned and the previous route/mode restored on every confirmed teardown.
 */
class VoiceAudioRouteController(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private var previousMode: Int? = null
    private var previousCommunicationDevice: AudioDeviceInfo? = null
    private var focusRequest: AudioFocusRequest? = null
    private var focusGranted = false
    private var onFocusLost: (() -> Unit)? = null
    private var deviceCallbackRegistered = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.w(TAG, "Voice session lost audio focus: change=$change")
                onFocusLost?.invoke()
            }
            AudioManager.AUDIOFOCUS_GAIN -> Log.i(TAG, "Voice session regained audio focus")
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            selectCommunicationDevice()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            selectCommunicationDevice()
        }
    }

    /**
     * Returns false if another app owns audio focus. In that case the caller must not
     * start microphone capture. Focus loss is reported to the service for orderly shutdown.
     */
    @Synchronized
    fun begin(onAudioFocusLost: () -> Unit): Boolean {
        if (previousMode != null) return focusGranted
        onFocusLost = onAudioFocusLost
        if (!requestAudioFocus()) {
            onFocusLost = null
            Log.w(TAG, "Audio focus was denied; refusing to start the Live microphone")
            return false
        }

        try {
            previousMode = audioManager.mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                previousCommunicationDevice = audioManager.communicationDevice
            }
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
                deviceCallbackRegistered = true
                selectCommunicationDevice()
            }

            val outputs = runCatching {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .joinToString { "${it.productName}/type=${it.type}" }
            }.getOrDefault("unavailable")
            Log.i(TAG, "Voice route started with audio focus: mode=${audioManager.mode}, outputs=[$outputs], " +
                "AEC_available=${runCatching { AcousticEchoCanceler.isAvailable() }.getOrDefault(false)}, " +
                "NS_available=${runCatching { NoiseSuppressor.isAvailable() }.getOrDefault(false)}. " +
                "Effect availability does not prove attachment to Firebase's recorder.")
            return true
        } catch (error: Exception) {
            Log.w(TAG, "Could not enter Android communication audio mode", error)
            end()
            return false
        }
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusListener, Handler(Looper.getMainLooper()))
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        focusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focusGranted
    }

    @Synchronized
    private fun selectCommunicationDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || previousMode == null) return
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
        if (selected != null && audioManager.communicationDevice?.id != selected.id &&
            !audioManager.setCommunicationDevice(selected)
        ) {
            Log.w(TAG, "Android refused communication output route type=${selected.type}")
        }
    }

    @Synchronized
    fun end() {
        val restoreMode = previousMode
        previousMode = null
        onFocusLost = null
        try {
            if (deviceCallbackRegistered) {
                runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
                    .onFailure { Log.w(TAG, "Could not unregister audio-device listener", it) }
                deviceCallbackRegistered = false
            }
            if (restoreMode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val previousDevice = previousCommunicationDevice
                previousCommunicationDevice = null
                runCatching {
                    val stillAvailable = previousDevice?.let { old ->
                        audioManager.availableCommunicationDevices.firstOrNull { it.id == old.id }
                    }
                    if (stillAvailable != null) audioManager.setCommunicationDevice(stillAvailable)
                    else audioManager.clearCommunicationDevice()
                }.onFailure { Log.w(TAG, "Could not restore previous communication device", it) }
            }
            // Restore MODE even if unregistering a listener or restoring a headset route failed.
            if (restoreMode != null) {
                runCatching { audioManager.mode = restoreMode }
                    .onSuccess { Log.i(TAG, "Voice route ended; restored AudioManager mode=$restoreMode") }
                    .onFailure { Log.w(TAG, "Could not restore previous Android audio mode", it) }
            }
        } finally {
            if (focusGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
                } else {
                    @Suppress("DEPRECATION")
                    runCatching { audioManager.abandonAudioFocus(focusListener) }
                }
            }
            focusRequest = null
            focusGranted = false
        }
    }

    companion object {
        private const val TAG = "JuanitaAudioRoute"
    }
}
