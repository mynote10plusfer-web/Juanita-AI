package ai.juanita.app.memory

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentityMemoryNamespaceTest {
    @Test
    fun everyCharacterConfigurationReadsAndWritesTheSameEncryptedMemory() {
        val context = isolatedContext()
        val juanitaMemory = EncryptedMemoryStore(context)
        val joseMemory = EncryptedMemoryStore(context)
        val tokenFromJuanita = "Conoscenza condivisa Juanita ${UUID.randomUUID()}"
        val tokenFromJose = "Conoscenza condivisa Jose ${UUID.randomUUID()}"
        val sessionId = "shared-session-${UUID.randomUUID()}"

        try {
            juanitaMemory.addMemory(tokenFromJuanita)
            juanitaMemory.appendConversationTurn(sessionId, "USER", "Conversazione iniziata con Juanita")

            assertTrue(joseMemory.listMemories().any { it.text == tokenFromJuanita })
            assertTrue(joseMemory.listConversationTurns(sessionId).any { it.text == "Conversazione iniziata con Juanita" })

            joseMemory.addMemory(tokenFromJose)
            joseMemory.appendConversationTurn(sessionId, "ASSISTANT", "Risposta di José")

            assertTrue(juanitaMemory.listMemories().any { it.text == tokenFromJose })
            assertTrue(juanitaMemory.listConversationTurns(sessionId).any { it.text == "Risposta di José" })
            assertEquals(2, juanitaMemory.listMemories().count { it.text.startsWith("Conoscenza condivisa") })
        } finally {
            juanitaMemory.clearAll()
        }
    }

    @Test
    fun sharedMemorySurvivesStoreRecreationAndRemainsIndependentFromCharacterProfiles() {
        val context = isolatedContext()
        val token = "Persistenza memoria comune ${UUID.randomUUID()}"
        try {
            EncryptedMemoryStore(context).addMemory(token)
            assertTrue(EncryptedMemoryStore(isolatedContext()).listMemories().any { it.text == token })
        } finally {
            EncryptedMemoryStore(context).clearAll()
        }
    }

    @Test
    fun topicRoutedRecallIsSharedAcrossCharactersAndKeepsBroadFallback() {
        val context = isolatedContext()
        val juanitaMemory = EncryptedMemoryStore(context)
        val joseMemory = EncryptedMemoryStore(context)
        val vehicleFact = "La mia Yamaha Tricity 300 è il mio scooter principale."
        val projectFact = "Juanita AI è il progetto Android che sto sviluppando."
        try {
            juanitaMemory.addMemory(vehicleFact)
            juanitaMemory.addMemory(projectFact)

            val vehicleRecall = joseMemory.buildRelevantRecallContext("Come va la mia Tricity?")
            assertTrue(vehicleRecall.contains(vehicleFact))
            assertTrue(vehicleRecall.contains("topic=vehicles_transport"))

            val projectRecall = joseMemory.buildRelevantRecallContext("Problema con il progetto Android Juanita")
            assertTrue(projectRecall.contains(projectFact))
            assertTrue(projectRecall.contains("topic=projects_technology"))
        } finally {
            juanitaMemory.clearAll()
        }
    }

    private fun isolatedContext(): Context {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "shared-memory-test").apply {
            mkdirs()
        }
        return object : ContextWrapper(target) {
            override fun getFilesDir(): File = directory
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                target.getSharedPreferences("test_shared_memory_${name}", mode)
        }
    }
}
