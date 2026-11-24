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
    fun automaticRecallUsesLiveTurnsWhenArchiveHasNotPersistedYet() {
        val note = GeminiLiveAssistant.buildAutomaticRecallContextNote(
            cue = "Te l'ho appena detto",
            retrievedContext = "",
            recentLiveTurns = listOf(
                "USER: Avevamo deciso di continuare sulla PR cumulativa.",
                "JUANITA: Sì, mantenendo memoria e dati protetti."
            )
        )

        assertTrue(note.contains("RECENT LIVE TRANSCRIPT"))
        assertTrue(note.contains("Avevamo deciso di continuare sulla PR cumulativa."))
        assertTrue(note.contains("mantenendo memoria e dati protetti."))
        assertFalse(note.contains("No matching saved recent context"))
    }

    @Test
    fun automaticRecallCombinesLiveTurnsAndArchivedEvidenceWithinBounds() {
        val oldTurns = (1..10).map { "USER: turn-$it" }
        val note = GeminiLiveAssistant.buildAutomaticRecallContextNote(
            cue = "Cosa avevamo deciso?",
            retrievedContext = "ARCHIVE: decisione confermata",
            recentLiveTurns = oldTurns
        )

        assertTrue(note.contains("turn-10"))
        assertTrue(note.contains("turn-3"))
        assertFalse(note.contains("turn-2"))
        assertTrue(note.contains("decisione confermata"))
        assertTrue(note.length < 10000)
    }

    @Test
    fun doesNotTriggerForOrdinaryConversation() {
        assertFalse(GeminiLiveAssistant.shouldAutoRecallRecentContext("Che tempo farà domani?"))
        assertFalse(GeminiLiveAssistant.shouldAutoRecallRecentContext("Raccontami una barzelletta"))
        assertFalse(GeminiLiveAssistant.shouldAutoRecallRecentContext(""))
    }
}
