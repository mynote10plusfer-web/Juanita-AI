package ai.juanita.app.presence

/**
 * Fail-closed policy for the optional conversational-presence experiment.
 *
 * This class makes decisions only; it does not open the microphone, start a
 * network session, or schedule speech. The audio owner must enforce the result.
 */
data class ConversationalPresenceSettings(
    val enabled: Boolean = false,
    val proactiveInitiativesEnabled: Boolean = false,
    val maxInitiativesPerHour: Int = 2,
    val minimumMinutesBetweenInitiatives: Int = 30,
    val minimumIdleMinutesBeforeInitiative: Int = 3,
    val suppressInitiativesWhileRiding: Boolean = true
) {
    fun normalized() = copy(
        maxInitiativesPerHour = maxInitiativesPerHour.coerceIn(0, 4),
        minimumMinutesBetweenInitiatives = minimumMinutesBetweenInitiatives.coerceIn(10, 180),
        minimumIdleMinutesBeforeInitiative = minimumIdleMinutesBeforeInitiative.coerceIn(1, 30)
    )
}

data class ConversationalPresenceContext(
    val appInForeground: Boolean,
    /** Android foreground service is running with its persistent user-visible notification. */
    val foregroundServiceActive: Boolean = false,
    /** User explicitly opted in to continuing this session after leaving the app. */
    val backgroundContinuationAuthorized: Boolean = false,
    val userExplicitlyStartedSession: Boolean,
    val microphonePermissionGranted: Boolean,
    val activeIdentityAvailable: Boolean,
    val manuallyPaused: Boolean = false,
    val anotherAudioCallActive: Boolean = false,
    val userSpeaking: Boolean = false,
    val assistantSpeaking: Boolean = false,
    val ridingMode: Boolean = false,
    val quietHours: Boolean = false,
    val networkAvailable: Boolean = true,
    val idleMinutes: Int = 0,
    val minutesSinceLastInitiative: Int = Int.MAX_VALUE,
    val initiativesInLastHour: Int = 0
)

enum class PresenceBlockReason {
    DISABLED,
    APP_NOT_FOREGROUND,
    SESSION_NOT_STARTED_BY_USER,
    MICROPHONE_PERMISSION_MISSING,
    IDENTITY_NOT_SELECTED,
    MANUALLY_PAUSED,
    ANOTHER_AUDIO_CALL_ACTIVE,
    NETWORK_UNAVAILABLE,
    USER_IS_SPEAKING,
    ASSISTANT_IS_SPEAKING,
    PROACTIVE_INITIATIVE_DISABLED,
    RIDING_MODE,
    QUIET_HOURS,
    USER_NOT_IDLE_LONG_ENOUGH,
    COOLDOWN_ACTIVE,
    HOURLY_LIMIT_REACHED
}

sealed interface PresenceDecision {
    data object AllowListening : PresenceDecision
    data class BlockListening(val reason: PresenceBlockReason) : PresenceDecision
    data object AllowProactiveInitiative : PresenceDecision
    data class BlockProactiveInitiative(val reason: PresenceBlockReason) : PresenceDecision
}

object ConversationalPresencePolicy {
    /**
     * Listening requires an explicit user-started session. Background continuation
     * is permitted only while Android's user-visible foreground service is active
     * and the user explicitly opted in to that continuation.
     */
    fun listeningDecision(
        settings: ConversationalPresenceSettings,
        context: ConversationalPresenceContext
    ): PresenceDecision {
        val normalized = settings.normalized()
        val reason = when {
            !normalized.enabled -> PresenceBlockReason.DISABLED
            !context.appInForeground &&
                !(context.foregroundServiceActive && context.backgroundContinuationAuthorized) ->
                PresenceBlockReason.APP_NOT_FOREGROUND
            !context.userExplicitlyStartedSession -> PresenceBlockReason.SESSION_NOT_STARTED_BY_USER
            !context.microphonePermissionGranted -> PresenceBlockReason.MICROPHONE_PERMISSION_MISSING
            !context.activeIdentityAvailable -> PresenceBlockReason.IDENTITY_NOT_SELECTED
            context.manuallyPaused -> PresenceBlockReason.MANUALLY_PAUSED
            context.anotherAudioCallActive -> PresenceBlockReason.ANOTHER_AUDIO_CALL_ACTIVE
            !context.networkAvailable -> PresenceBlockReason.NETWORK_UNAVAILABLE
            else -> null
        }
        return reason?.let(PresenceDecision::BlockListening) ?: PresenceDecision.AllowListening
    }

    /**
     * Proactive speech is a stricter permission than listening. It is never
     * allowed during riding mode, quiet hours, either side's speech, or before
     * the user has been idle for the configured interval.
     */
    fun proactiveDecision(
        settings: ConversationalPresenceSettings,
        context: ConversationalPresenceContext
    ): PresenceDecision {
        val listening = listeningDecision(settings, context)
        if (listening !is PresenceDecision.AllowListening) {
            val reason = (listening as PresenceDecision.BlockListening).reason
            return PresenceDecision.BlockProactiveInitiative(reason)
        }
        val normalized = settings.normalized()
        val reason = when {
            !normalized.proactiveInitiativesEnabled -> PresenceBlockReason.PROACTIVE_INITIATIVE_DISABLED
            context.ridingMode && normalized.suppressInitiativesWhileRiding -> PresenceBlockReason.RIDING_MODE
            context.quietHours -> PresenceBlockReason.QUIET_HOURS
            context.userSpeaking -> PresenceBlockReason.USER_IS_SPEAKING
            context.assistantSpeaking -> PresenceBlockReason.ASSISTANT_IS_SPEAKING
            context.idleMinutes < normalized.minimumIdleMinutesBeforeInitiative -> PresenceBlockReason.USER_NOT_IDLE_LONG_ENOUGH
            context.minutesSinceLastInitiative < normalized.minimumMinutesBetweenInitiatives -> PresenceBlockReason.COOLDOWN_ACTIVE
            context.initiativesInLastHour >= normalized.maxInitiativesPerHour -> PresenceBlockReason.HOURLY_LIMIT_REACHED
            normalized.maxInitiativesPerHour == 0 -> PresenceBlockReason.HOURLY_LIMIT_REACHED
            else -> null
        }
        return reason?.let(PresenceDecision::BlockProactiveInitiative)
            ?: PresenceDecision.AllowProactiveInitiative
    }
}
