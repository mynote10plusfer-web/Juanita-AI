package ai.juanita.app.route

import android.media.AudioManager
import ai.juanita.app.audio.VoiceAudioRouteController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAudioFocusPolicyTest {
    @Test
    fun transientDuckDoesNotStopLiveSession() {
        assertFalse(
            VoiceAudioRouteController.shouldStopVoiceSessionForFocusChange(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
            )
        )
    }

    @Test
    fun fullAndTransientFocusLossStopLiveSession() {
        assertTrue(
            VoiceAudioRouteController.shouldStopVoiceSessionForFocusChange(
                AudioManager.AUDIOFOCUS_LOSS
            )
        )
        assertTrue(
            VoiceAudioRouteController.shouldStopVoiceSessionForFocusChange(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
            )
        )
    }
}
