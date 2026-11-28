package ai.juanita.app.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WakeWordRuntimePolicyTest {
    private val allowed = WakeWordRuntimeConditions(
        userOptedIn = true,
        microphonePermissionGranted = true,
        microphoneForegroundServiceActive = true
    )

    @Test
    fun detectorCannotArmWithoutExplicitOptInPermissionAndForegroundService() {
        assertEquals(
            WakeWordRuntimeState.DISABLED,
            WakeWordRuntimePolicy.transition(
                WakeWordRuntimeState.DISABLED,
                WakeWordRuntimeEvent.Enable,
                allowed.copy(userOptedIn = false)
            )
        )
        assertEquals(
            WakeWordRuntimeState.DISABLED,
            WakeWordRuntimePolicy.transition(
                WakeWordRuntimeState.DISABLED,
                WakeWordRuntimeEvent.Enable,
                allowed.copy(microphonePermissionGranted = false)
            )
        )
        assertEquals(
            WakeWordRuntimeState.DISABLED,
            WakeWordRuntimePolicy.transition(
                WakeWordRuntimeState.DISABLED,
                WakeWordRuntimeEvent.Enable,
                allowed.copy(microphoneForegroundServiceActive = false)
            )
        )
    }

    @Test
    fun liveCannotStartUntilDetectorReleaseIsConfirmed() {
        var state = WakeWordRuntimePolicy.transition(
            WakeWordRuntimeState.DISABLED, WakeWordRuntimeEvent.Enable, allowed
        )
        state = WakeWordRuntimePolicy.transition(state, WakeWordRuntimeEvent.DetectorReady, allowed)
        state = WakeWordRuntimePolicy.transition(state, WakeWordRuntimeEvent.WakeDetected, allowed)
        assertEquals(WakeWordRuntimeState.TRIGGERED, state)

        // A premature LiveStarted event is ignored; only DetectorReleased advances the state.
        assertEquals(
            state,
            WakeWordRuntimePolicy.transition(state, WakeWordRuntimeEvent.LiveStarted, allowed)
        )
        state = WakeWordRuntimePolicy.transition(state, WakeWordRuntimeEvent.DetectorReleased, allowed)
        assertEquals(WakeWordRuntimeState.STARTING_LIVE, state)
        state = WakeWordRuntimePolicy.transition(state, WakeWordRuntimeEvent.LiveStarted, allowed)
        assertEquals(WakeWordRuntimeState.LIVE_ACTIVE, state)
    }

    @Test
    fun detectorRestartsOnlyAfterLiveStoppedAndConditionsStillAllowIt() {
        var state = WakeWordRuntimeState.LIVE_ACTIVE
        state = WakeWordRuntimePolicy.transition(state, WakeWordRuntimeEvent.LiveStopped, allowed)
        assertEquals(WakeWordRuntimeState.STARTING_DETECTOR, state)

        state = WakeWordRuntimePolicy.transition(
            WakeWordRuntimeState.LIVE_ACTIVE,
            WakeWordRuntimeEvent.LiveStopped,
            allowed.copy(userOptedIn = false)
        )
        assertEquals(WakeWordRuntimeState.DISABLED, state)
    }

    @Test
    fun disablingDuringLiveRequiresAudioReleaseBeforeRearmingOrDisabling() {
        var state = WakeWordRuntimePolicy.transition(
            WakeWordRuntimeState.LIVE_ACTIVE, WakeWordRuntimeEvent.Disable, allowed
        )
        assertEquals(WakeWordRuntimeState.STOPPING, state)
        state = WakeWordRuntimePolicy.transition(
            state, WakeWordRuntimeEvent.AllAudioReleased, allowed.copy(userOptedIn = false)
        )
        assertEquals(WakeWordRuntimeState.DISABLED, state)
    }

    @Test
    fun failuresAreFailClosedUntilExplicitReset() {
        val failed = WakeWordRuntimePolicy.transition(
            WakeWordRuntimeState.ARMED, WakeWordRuntimeEvent.Failure, allowed
        )
        assertEquals(WakeWordRuntimeState.ERROR, failed)
        assertEquals(
            WakeWordRuntimeState.ERROR,
            WakeWordRuntimePolicy.transition(failed, WakeWordRuntimeEvent.Enable, allowed)
        )
        assertEquals(
            WakeWordRuntimeState.DISABLED,
            WakeWordRuntimePolicy.transition(failed, WakeWordRuntimeEvent.Reset, allowed)
        )
    }

    @Test
    fun phraseValidationRejectsEmptyLongControlAndExcessivePhrases() {
        assertNotNull(WakePhraseValidator.validate("   "))
        assertNotNull(WakePhraseValidator.validate("a".repeat(WakePhraseValidator.MAX_LENGTH + 1)))
        assertNotNull(WakePhraseValidator.validate("Juanita\nattivati"))
        assertNotNull(WakePhraseValidator.validate("una due tre quattro cinque sei sette"))
        assertNull(WakePhraseValidator.validate("Hola Juanita"))
        assertNull(WakePhraseValidator.validate("Ciao Juanita"))
    }
}


// Additional invariants for the serialized microphone hand-off.
