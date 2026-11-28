package ai.juanita.app.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundLiveSafetyPolicyTest {
    @Test fun stopsAuthorizedBackgroundSessionAtIdleBoundary() {
        assertTrue(BackgroundLiveSafetyPolicy.shouldStop(false, true, false, BackgroundLiveSafetyPolicy.IDLE_TIMEOUT_MS))
    }
    @Test fun doesNotStopForegroundSession() {
        assertFalse(BackgroundLiveSafetyPolicy.shouldStop(true, true, false, BackgroundLiveSafetyPolicy.IDLE_TIMEOUT_MS * 3))
    }
    @Test fun requiresConsentAndExcludesAlarmSessions() {
        assertFalse(BackgroundLiveSafetyPolicy.shouldStop(false, false, false, BackgroundLiveSafetyPolicy.IDLE_TIMEOUT_MS * 2))
        assertFalse(BackgroundLiveSafetyPolicy.shouldStop(false, true, true, BackgroundLiveSafetyPolicy.IDLE_TIMEOUT_MS * 2))
    }
    @Test fun calculatesRemainingIdleWindow() {
        assertEquals(300_000L, BackgroundLiveSafetyPolicy.remainingIdleMillis(10_000L, 610_000L))
        assertEquals(0L, BackgroundLiveSafetyPolicy.remainingIdleMillis(10_000L, 1_210_000L))
    }
}
