package ai.juanita.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentMemoryRecallCueTest {
    @Test
    fun detectsItalianRecentRecallReferences() {
        assertTrue(GeminiLiveAssistant.shouldAutoRecallRecentContext("Te l'ho appena detto"))
        assertTrue(GeminiLiveAssistant.shouldAutoRecallRecentContext("Cosa avevamo deciso pochi minuti fa?"))
        assertTrue(GeminiLiveAssistant.shouldAutoRecallRecentContext("Ti ricordi quello che ti ho detto?"))
    }

    @Test
    fun detectsEnglishAndSpanishRecentRecallReferences() {
        assertTrue(GeminiLiveAssistant.shouldAutoRecallRecentContext("I just told you a few minutes ago"))
        assertTrue(GeminiLiveAssistant.shouldAutoRecallRecentContext("¿Qué te dije hace un momento?"))
        assertTrue(GeminiLiveAssistant.shouldAutoRecallRecentContext("What did we decide earlier?"))
    }

    @Test
    fun doesNotTriggerForOrdinaryConversation() {
        assertFalse(GeminiLiveAssistant.shouldAutoRecallRecentContext("Che tempo farà domani?"))
        assertFalse(GeminiLiveAssistant.shouldAutoRecallRecentContext("Raccontami una barzelletta"))
        assertFalse(GeminiLiveAssistant.shouldAutoRecallRecentContext(""))
    }
}
