package ai.juanita.app.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationalPresencePolicyTest {
    private val enabled = ConversationalPresenceSettings(
        enabled = true,
        proactiveInitiativesEnabled = true,
        maxInitiativesPerHour = 2,
        minimumMinutesBetweenInitiatives = 30,
        minimumIdleMinutesBeforeInitiative = 3
    )

    private val safeContext = ConversationalPresenceContext(
        appInForeground = true,
        userExplicitlyStartedSession = true,
        microphonePermissionGranted = true,
        activeIdentityAvailable = true,
        idleMinutes = 5,
        minutesSinceLastInitiative = 45,
        initiativesInLastHour = 0
    )

    @Test
    fun listeningFailsClosedByDefaultAndWhenAppLeavesForegroundWithoutBackgroundConsent() {
        assertEquals(
            PresenceDecision.BlockListening(PresenceBlockReason.DISABLED),
            ConversationalPresencePolicy.listeningDecision(
                ConversationalPresenceSettings(),
                safeContext
            )
        )
        assertEquals(
            PresenceDecision.BlockListening(PresenceBlockReason.APP_NOT_FOREGROUND),
            ConversationalPresencePolicy.listeningDecision(
                enabled,
                safeContext.copy(appInForeground = false)
            )
        )
    }

    @Test
    fun backgroundListeningRequiresBothForegroundServiceAndExplicitContinuationConsent() {
        assertEquals(
            PresenceDecision.BlockListening(PresenceBlockReason.APP_NOT_FOREGROUND),
            ConversationalPresencePolicy.listeningDecision(
                enabled,
                safeContext.copy(appInForeground = false, foregroundServiceActive = true)
            )
        )
        assertEquals(
            PresenceDecision.BlockListening(PresenceBlockReason.APP_NOT_FOREGROUND),
            ConversationalPresencePolicy.listeningDecision(
                enabled,
                safeContext.copy(appInForeground = false, backgroundContinuationAuthorized = true)
            )
        )
        assertEquals(
            PresenceDecision.AllowListening,
            ConversationalPresencePolicy.listeningDecision(
                enabled,
                safeContext.copy(
                    appInForeground = false,
                    foregroundServiceActive = true,
                    backgroundContinuationAuthorized = true
                )
            )
        )
    }

    @Test
    fun backgroundContinuationFailsClosedWhenMicrophonePermissionIsRevoked() {
        val backgroundContext = safeContext.copy(
            appInForeground = false,
            foregroundServiceActive = true,
            backgroundContinuationAuthorized = true,
            microphonePermissionGranted = false
        )
        assertEquals(
            PresenceDecision.BlockListening(PresenceBlockReason.MICROPHONE_PERMISSION_MISSING),
            ConversationalPresencePolicy.listeningDecision(enabled, backgroundContext)
        )
    }

    @Test
    fun listeningRequiresExplicitSessionAndMicrophonePermission() {
        assertEquals(
            PresenceDecision.BlockListening(PresenceBlockReason.SESSION_NOT_STARTED_BY_USER),
            ConversationalPresencePolicy.listeningDecision(
                enabled,
                safeContext.copy(userExplicitlyStartedSession = false)
            )
        )
        assertEquals(
            PresenceDecision.BlockListening(PresenceBlockReason.MICROPHONE_PERMISSION_MISSING),
            ConversationalPresencePolicy.listeningDecision(
                enabled,
                safeContext.copy(microphonePermissionGranted = false)
            )
        )
    }

    @Test
    fun proactiveInitiativeAllowedOnlyWhenAllGatesPass() {
        assertEquals(
            PresenceDecision.AllowProactiveInitiative,
            ConversationalPresencePolicy.proactiveDecision(enabled, safeContext)
        )
    }

    @Test
    fun proactiveInitiativeIsSuppressedWhileRidingOrDuringQuietHours() {
        assertEquals(
            PresenceDecision.BlockProactiveInitiative(PresenceBlockReason.RIDING_MODE),
            ConversationalPresencePolicy.proactiveDecision(
                enabled,
                safeContext.copy(ridingMode = true)
            )
        )
        assertEquals(
            PresenceDecision.BlockProactiveInitiative(PresenceBlockReason.QUIET_HOURS),
            ConversationalPresencePolicy.proactiveDecision(
                enabled,
                safeContext.copy(quietHours = true)
            )
        )
    }

    @Test
    fun proactiveInitiativeWaitsForTurnAndCooldown() {
        assertEquals(
            PresenceDecision.BlockProactiveInitiative(PresenceBlockReason.USER_IS_SPEAKING),
            ConversationalPresencePolicy.proactiveDecision(
                enabled,
                safeContext.copy(userSpeaking = true)
            )
        )
        assertEquals(
            PresenceDecision.BlockProactiveInitiative(PresenceBlockReason.COOLDOWN_ACTIVE),
            ConversationalPresencePolicy.proactiveDecision(
                enabled,
                safeContext.copy(minutesSinceLastInitiative = 10)
            )
        )
    }

    @Test
    fun proactiveInitiativeRespectsHourlyLimitAndCanBeDisabledIndependently() {
        assertEquals(
            PresenceDecision.BlockProactiveInitiative(PresenceBlockReason.HOURLY_LIMIT_REACHED),
            ConversationalPresencePolicy.proactiveDecision(
                enabled,
                safeContext.copy(initiativesInLastHour = 2)
            )
        )
        assertEquals(
            PresenceDecision.BlockProactiveInitiative(PresenceBlockReason.PROACTIVE_INITIATIVE_DISABLED),
            ConversationalPresencePolicy.proactiveDecision(
                enabled.copy(proactiveInitiativesEnabled = false),
                safeContext
            )
        )
    }

    @Test
    fun settingsAreClampedToSafeBounds() {
        val normalized = enabled.copy(
            maxInitiativesPerHour = 99,
            minimumMinutesBetweenInitiatives = 1,
            minimumIdleMinutesBeforeInitiative = 0
        ).normalized()
        assertEquals(4, normalized.maxInitiativesPerHour)
        assertEquals(10, normalized.minimumMinutesBetweenInitiatives)
        assertEquals(1, normalized.minimumIdleMinutesBeforeInitiative)
        assertTrue(normalized.suppressInitiativesWhileRiding)
    }
}
