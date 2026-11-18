package ai.juanita.app.presence

/**
 * Pure, fail-closed lifecycle contract for the offline wake-word detector.
 *
 * This policy never opens audio devices. The service that owns AudioRecord must
 * dispatch events only after the corresponding native resource operation has
 * completed. In particular, KWS audio must be released before Gemini Live starts,
 * and Gemini Live must be fully stopped before KWS is armed again.
 */
enum class WakeWordRuntimeState {
    DISABLED,
    STARTING_DETECTOR,
    ARMED,
    TRIGGERED,
    STARTING_LIVE,
    LIVE_ACTIVE,
    STOPPING,
    ERROR
}

sealed interface WakeWordRuntimeEvent {
    data object Enable : WakeWordRuntimeEvent
    data object DetectorReady : WakeWordRuntimeEvent
    data object WakeDetected : WakeWordRuntimeEvent
    data object DetectorReleased : WakeWordRuntimeEvent
    data object LiveStarted : WakeWordRuntimeEvent
    data object LiveStopped : WakeWordRuntimeEvent
    data object AllAudioReleased : WakeWordRuntimeEvent
    data object Disable : WakeWordRuntimeEvent
    data object Failure : WakeWordRuntimeEvent
    data object Reset : WakeWordRuntimeEvent
}

/** Current platform facts are supplied by the service; this object stores no state. */
data class WakeWordRuntimeConditions(
    val userOptedIn: Boolean,
    val microphonePermissionGranted: Boolean,
    val microphoneForegroundServiceActive: Boolean
) {
    val mayArmDetector: Boolean
        get() = userOptedIn && microphonePermissionGranted && microphoneForegroundServiceActive
}

/**
 * Serial transition table. Any unexpected event is rejected by returning the
 * current state; callers must log it rather than guessing which recorder is open.
 */
object WakeWordRuntimePolicy {
    fun transition(
        state: WakeWordRuntimeState,
        event: WakeWordRuntimeEvent,
        conditions: WakeWordRuntimeConditions
    ): WakeWordRuntimeState = when (state) {
        WakeWordRuntimeState.DISABLED -> when (event) {
            WakeWordRuntimeEvent.Enable ->
                if (conditions.mayArmDetector) WakeWordRuntimeState.STARTING_DETECTOR
                else WakeWordRuntimeState.DISABLED
            else -> state
        }

        WakeWordRuntimeState.STARTING_DETECTOR -> when (event) {
            WakeWordRuntimeEvent.DetectorReady ->
                if (conditions.mayArmDetector) WakeWordRuntimeState.ARMED
                else WakeWordRuntimeState.STOPPING
            WakeWordRuntimeEvent.Disable -> WakeWordRuntimeState.STOPPING
            WakeWordRuntimeEvent.Failure -> WakeWordRuntimeState.ERROR
            else -> state
        }

        WakeWordRuntimeState.ARMED -> when (event) {
            WakeWordRuntimeEvent.WakeDetected -> WakeWordRuntimeState.TRIGGERED
            WakeWordRuntimeEvent.Disable -> WakeWordRuntimeState.STOPPING
            WakeWordRuntimeEvent.Failure -> WakeWordRuntimeState.ERROR
            else -> state
        }

        WakeWordRuntimeState.TRIGGERED -> when (event) {
            WakeWordRuntimeEvent.DetectorReleased -> WakeWordRuntimeState.STARTING_LIVE
            WakeWordRuntimeEvent.Disable -> WakeWordRuntimeState.STOPPING
            WakeWordRuntimeEvent.Failure -> WakeWordRuntimeState.ERROR
            else -> state
        }

        WakeWordRuntimeState.STARTING_LIVE -> when (event) {
            WakeWordRuntimeEvent.LiveStarted -> WakeWordRuntimeState.LIVE_ACTIVE
            WakeWordRuntimeEvent.Failure -> WakeWordRuntimeState.ERROR
            WakeWordRuntimeEvent.Disable -> WakeWordRuntimeState.STOPPING
            else -> state
        }

        WakeWordRuntimeState.LIVE_ACTIVE -> when (event) {
            WakeWordRuntimeEvent.LiveStopped ->
                if (conditions.mayArmDetector) WakeWordRuntimeState.STARTING_DETECTOR
                else WakeWordRuntimeState.DISABLED
            WakeWordRuntimeEvent.Disable -> WakeWordRuntimeState.STOPPING
            WakeWordRuntimeEvent.Failure -> WakeWordRuntimeState.ERROR
            else -> state
        }

        WakeWordRuntimeState.STOPPING -> when (event) {
            WakeWordRuntimeEvent.AllAudioReleased ->
                if (conditions.mayArmDetector) WakeWordRuntimeState.STARTING_DETECTOR
                else WakeWordRuntimeState.DISABLED
            WakeWordRuntimeEvent.Failure -> WakeWordRuntimeState.ERROR
            else -> state
        }

        WakeWordRuntimeState.ERROR -> when (event) {
            WakeWordRuntimeEvent.Reset -> WakeWordRuntimeState.DISABLED
            else -> state
        }
    }
}

/** Basic local validation only; acoustic/language support is verified by the KWS model. */
object WakePhraseValidator {
    const val MAX_LENGTH = 48

    fun validate(phrase: String): String? {
        val normalized = phrase.trim()
        return when {
            normalized.isEmpty() -> "Inserisci una frase di attivazione."
            normalized.length > MAX_LENGTH -> "La frase non può superare $MAX_LENGTH caratteri."
            normalized.any(Char::isISOControl) -> "La frase contiene caratteri non consentiti."
            normalized.split(Regex("\\s+")).size > 6 -> "Usa al massimo sei parole."
            else -> null
        }
    }
}
