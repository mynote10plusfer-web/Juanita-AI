package ai.juanita.app.presence

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmLiveIdlePolicyTest {
    @Test
    fun allowsTwentySecondsAfterLastRecognizedVoiceActivity() {
        assertEquals(
            12_000L,
            AlarmLiveIdlePolicy.remainingTimeoutMillis(10_000L, 28_000L, 36_000L)
        )
    }

    @Test
    fun refreshesIdleWindowWhenAnotherTurnIsRecognized() {
        assertEquals(
            20_000L,
            AlarmLiveIdlePolicy.remainingTimeoutMillis(10_000L, 50_000L, 50_000L)
        )
    }

    @Test
    fun hardSessionCeilingWinsEvenWhenVoiceActivityContinues() {
        assertEquals(
            0L,
            AlarmLiveIdlePolicy.remainingTimeoutMillis(1_000L, 170_000L, 181_000L)
        )
    }

    @Test
    fun neverReturnsNegativeDelay() {
        assertEquals(
            0L,
            AlarmLiveIdlePolicy.remainingTimeoutMillis(0L, 0L, 60_000L)
        )
    }
}
