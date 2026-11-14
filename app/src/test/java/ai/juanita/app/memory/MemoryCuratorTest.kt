package ai.juanita.app.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryCuratorTest {
    @Test
    fun onlyExplicitHighConfidenceFactsCanSkipReview() {
        val ranked = MemoryCurator().rank(listOf(
            MemoryCandidate("explicit", "preference", "automatic", .98f, 2, .9f, .9f, 1_800_000_000_000L, false, true),
            MemoryCandidate("suggested", "general", "automatic", .98f, 3, .9f, .9f, 1_800_000_000_000L, false, false)
        ), nowMillis = 1_800_000_000_000L).associateBy { it.candidate.text }
        assertFalse(ranked.getValue("explicit").requiresUserConfirmation)
        assertTrue(ranked.getValue("suggested").requiresUserConfirmation)
    }

    @Test
    fun sensitiveFactsAlwaysRequireReview() {
        val ranked = MemoryCurator().rank(listOf(
            MemoryCandidate("sensitive", "general", "automatic", 1f, 5, 1f, 1f, 1_800_000_000_000L, true, true)
        ), nowMillis = 1_800_000_000_000L)
        assertTrue(ranked.single().requiresUserConfirmation)
    }
}
