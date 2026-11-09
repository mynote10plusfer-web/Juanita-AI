package ai.juanita.app.memory

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationArchiveFidelityTest {

    @Test
    fun preservesLongUnicodeTurnExactlyAcrossStoreInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionId = "fidelity-${UUID.randomUUID()}"
        val original = buildString {
            append("Riccardo — conversazione integrale. ")
            repeat(250) {
                append("Parole accentate: perché, città, più; español: mañana, corazón; čeština: příliš žluťoučký. ")
            }
        }
        assertTrue(original.length > 1_800)

        try {
            EncryptedMemoryStore(context).appendConversationTurn(
                sessionId = sessionId,
                role = "USER",
                text = original,
                timestampMillis = 1_800_000_000_000L
            )

            // A fresh store instance verifies that the assertion reads encrypted disk data,
            // rather than an in-memory copy retained by the writer.
            val restored = EncryptedMemoryStore(context).listConversationTurns(sessionId)
            assertEquals(1, restored.size)
            assertEquals(original.length, restored.single().text.length)
            assertEquals(original, restored.single().text)
        } finally {
            EncryptedMemoryStore(context).deleteConversation(sessionId)
        }
    }

    @Test
    fun preservesSeparateTurnsAndTheirOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionId = "order-${UUID.randomUUID()}"
        val first = "Prima battuta — " + "á".repeat(2_000)
        val second = "Seconda battuta — " + "β".repeat(2_100)

        try {
            val store = EncryptedMemoryStore(context)
            store.appendConversationTurn(sessionId, "USER", first, 1_800_000_000_000L)
            store.appendConversationTurn(sessionId, "ASSISTANT", second, 1_800_000_000_001L)

            val restored = EncryptedMemoryStore(context).listConversationTurns(sessionId)
            assertEquals(listOf(first, second), restored.map { it.text })
            assertEquals(listOf("USER", "ASSISTANT"), restored.map { it.role })
        } finally {
            EncryptedMemoryStore(context).deleteConversation(sessionId)
        }
    }
    @Test
    fun activeConversationContinuitySurvivesStoreRecreationAndIgnoresArchivePause() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionId = "continuity-" + UUID.randomUUID()
        val store = EncryptedMemoryStore(context)
        val archiveWasEnabled = store.isConversationArchiveEnabled
        try {
            store.setConversationArchiveEnabled(false)
            store.appendContinuityTurn(sessionId, "USER", "Stiamo progettando il promemoria locale cifrato.")
            store.appendContinuityTurn(sessionId, "ASSISTANT", "Prima completiamo il recupero della conversazione.")
            val restored = EncryptedMemoryStore(context).buildContinuityContext(sessionId)
            assertTrue(restored.contains("promemoria locale cifrato"))
            assertTrue(restored.contains("recupero della conversazione"))
            assertTrue(EncryptedMemoryStore(context).listConversationTurns(sessionId).isEmpty())
            EncryptedMemoryStore(context).clearContinuitySnapshot(sessionId)
            assertTrue(EncryptedMemoryStore(context).buildContinuityContext(sessionId).isBlank())
        } finally {
            store.clearContinuitySnapshot(sessionId)
            store.setConversationArchiveEnabled(archiveWasEnabled)
        }
    }

}
