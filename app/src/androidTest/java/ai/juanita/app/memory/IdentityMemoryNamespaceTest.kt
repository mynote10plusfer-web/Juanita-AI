package ai.juanita.app.memory

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentityMemoryNamespaceTest {
    @Test
    fun identityNamespacesDoNotShareMemoriesOrConversationArchives() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val juanitaId = UUID.randomUUID().toString()
        val joseId = UUID.randomUUID().toString()
        val sessionId = "identity-session-${UUID.randomUUID()}"
        val juanita = EncryptedMemoryStore(context, juanitaId)
        val jose = EncryptedMemoryStore(context, joseId)

        try {
            juanita.addMemory("Ricordo esclusivo Juanita ${UUID.randomUUID()}")
            juanita.appendConversationTurn(sessionId, "USER", "Conversazione privata Juanita")

            assertEquals(1, juanita.listMemories().size)
            assertTrue(juanita.listMemories().single().text.startsWith("Ricordo esclusivo Juanita"))
            assertTrue(jose.listMemories().isEmpty())
            assertTrue(juanita.listConversationTurns(sessionId).isNotEmpty())
            assertTrue(jose.listConversationTurns(sessionId).isEmpty())

            jose.addMemory("Ricordo esclusivo José ${UUID.randomUUID()}")
            assertEquals(1, jose.listMemories().size)
            assertEquals(1, juanita.listMemories().size)
        } finally {
            juanita.clearAll()
            jose.clearAll()
        }
    }

    @Test
    fun identityNamespaceSurvivesStoreRecreationAndDoesNotChangeLegacyStore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val identityId = UUID.randomUUID().toString()
        val token = "Persistenza identità ${UUID.randomUUID()}"
        val identityStore = EncryptedMemoryStore(context, identityId)
        val legacyStore = EncryptedMemoryStore(context)

        try {
            identityStore.addMemory(token)
            assertTrue(EncryptedMemoryStore(context, identityId).listMemories().any { it.text == token })
            assertFalse(legacyStore.listMemories().any { it.text == token })
        } finally {
            EncryptedMemoryStore(context, identityId).clearAll()
        }
    }

    @Test
    fun malformedIdentityNamespaceIsRejectedInsteadOfFallingBackToLegacy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedMemoryStore(context, "not-a-uuid")
        }
    }
}
