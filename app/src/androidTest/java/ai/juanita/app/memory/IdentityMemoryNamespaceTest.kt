package ai.juanita.app.memory

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentityMemoryNamespaceTest {
    @Test
    fun productionFactoryReturnsOneProcessWideEncryptedStoreInstance() {
        // Use the real target application context: the process singleton must never
        // retain a test-only ContextWrapper or redirect production storage into cacheDir.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val first = EncryptedMemoryStore.getInstance(appContext)
        val second = EncryptedMemoryStore.getInstance(appContext)
        assertSame("Activity and service must share the same synchronized store", first, second)
    }

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

    @Test
    fun retentionAndRangeDeletionPreserveUnreadableSessionArchiveBytes() {
        val context = isolatedContext()
        val store = EncryptedMemoryStore(context)
        val sessionId = "corrupt-${UUID.randomUUID()}"
        val archiveFile = File(File(context.filesDir, "conversation_archive"), "$sessionId.enc")
        val corruptedBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)

        try {
            store.appendConversationTurn(sessionId, "USER", "Archivio da proteggere")
            assertTrue("Expected an encrypted session archive", archiveFile.exists())

            // Simulate a truncated/corrupted ciphertext or a transient Keystore read failure.
            archiveFile.writeBytes(corruptedBytes)
            store.archiveRetentionDays = 7
            assertTrue("Retention must not delete an unreadable archive", archiveFile.exists())
            assertArrayEquals("Retention must preserve unreadable bytes", corruptedBytes, archiveFile.readBytes())

            store.deleteArchiveRange(0L, System.currentTimeMillis() + 1L)
            assertTrue("Range deletion must not delete an unreadable archive", archiveFile.exists())
            assertArrayEquals("Range deletion must preserve unreadable bytes", corruptedBytes, archiveFile.readBytes())
        } finally {
            store.archiveRetentionDays = 0
            store.clearAll()
        }
    }

    @Test
    fun sharedFactsAreVisibleToBothCharactersButPrivateMemoriesStayIsolated() {
        val context = isolatedContext()
        val store = EncryptedMemoryStore(context)
        val shared = "Fatto comune ${UUID.randomUUID()}"
        val juanitaOnly = "Ricordo privato Juanita ${UUID.randomUUID()}"
        val joseOnly = "Ricordo privato Jose ${UUID.randomUUID()}"
        try {
            store.addMemory(shared)
            store.addMemory(juanitaOnly, personaId = "juanita-1")
            store.addMemory(joseOnly, personaId = "jose-1")
            val juanitaMemories = store.listMemoriesForPersona("juanita-1").map { it.text }
            val joseMemories = store.listMemoriesForPersona("jose-1").map { it.text }
            assertTrue(shared in juanitaMemories)
            assertTrue(shared in joseMemories)
            assertTrue(juanitaOnly in juanitaMemories)
            assertTrue(juanitaOnly !in joseMemories)
            assertTrue(joseOnly in joseMemories)
            assertTrue(joseOnly !in juanitaMemories)
        } finally {
            store.clearAll()
        }
    }

    @Test
    fun conversationRecallIsScopedToTheActiveCharacter() {
        val context = isolatedContext()
        val store = EncryptedMemoryStore(context)
        val juanitaSession = "juanita-${UUID.randomUUID()}"
        val joseSession = "jose-${UUID.randomUUID()}"
        val juanitaPhrase = "Juanita episodio esclusivo ${UUID.randomUUID()}"
        val josePhrase = "Jose episodio esclusivo ${UUID.randomUUID()}"
        try {
            store.appendConversationTurn(juanitaSession, "USER", juanitaPhrase, personaId = "juanita-1")
            store.appendConversationTurn(joseSession, "USER", josePhrase, personaId = "jose-1")
            val juanitaHits = store.searchArchivedTurns(juanitaPhrase, personaId = "juanita-1")
            val joseHits = store.searchArchivedTurns(josePhrase, personaId = "jose-1")
            val juanitaRecall = store.buildRelevantRecallContext(juanitaPhrase, personaId = "juanita-1")
            val joseRecall = store.buildRelevantRecallContext(josePhrase, personaId = "jose-1")
            assertTrue(juanitaHits.any { it.sessionId == juanitaSession })
            assertTrue(juanitaHits.none { it.sessionId == joseSession })
            assertTrue(joseHits.any { it.sessionId == joseSession })
            assertTrue(joseHits.none { it.sessionId == juanitaSession })
            assertTrue(juanitaRecall.contains(juanitaPhrase))
            assertTrue(!juanitaRecall.contains(josePhrase))
            assertTrue(joseRecall.contains(josePhrase))
            assertTrue(!joseRecall.contains(juanitaPhrase))
        } finally {
            store.clearAll()
        }
    }

    @Test
    fun interruptedContinuityIsRecoveredOnlyByTheSameCharacter() {
        val context = isolatedContext()
        val store = EncryptedMemoryStore(context)
        val juanitaSession = "juanita-continuity-${UUID.randomUUID()}"
        val joseSession = "jose-continuity-${UUID.randomUUID()}"
        val juanitaText = "Filo privato Juanita ${UUID.randomUUID()}"
        val joseText = "Filo privato Jose ${UUID.randomUUID()}"
        try {
            store.appendContinuityTurn(juanitaSession, "USER", juanitaText, personaId = "juanita-1")
            store.appendContinuityTurn(joseSession, "USER", joseText, personaId = "jose-1")
            assertTrue(store.buildContinuityContext(juanitaSession, personaId = "juanita-1").contains(juanitaText))
            assertTrue(!store.buildContinuityContext(juanitaSession, personaId = "jose-1").contains(juanitaText))
            assertTrue(store.buildContinuityContext(joseSession, personaId = "jose-1").contains(joseText))
            assertTrue(!store.buildContinuityContext(joseSession, personaId = "juanita-1").contains(joseText))
            assertTrue(store.carryForwardLatestContinuitySnapshot("juanita-next", "juanita-1").contains(juanitaText))
            assertTrue(!store.buildContinuityContext("juanita-next", personaId = "jose-1").contains(juanitaText))
        } finally {
            store.clearAll()
        }
    }

    @Test
    fun legacyUntaggedConversationTurnsAreNotInjectedIntoCharacterPrivateRecall() {
        val context = isolatedContext()
        val store = EncryptedMemoryStore(context)
        val legacySession = "legacy-${UUID.randomUUID()}"
        val privateSession = "private-${UUID.randomUUID()}"
        val legacyPhrase = "Episodio legacy non attribuito ${UUID.randomUUID()}"
        val privatePhrase = "Episodio privato Juanita ${UUID.randomUUID()}"
        try {
            // Null personaId represents a pre-migration transcript whose character
            // ownership cannot be proven. It must not leak into any private persona.
            store.appendConversationTurn(legacySession, "USER", legacyPhrase)
            store.appendConversationTurn(privateSession, "USER", privatePhrase, personaId = "juanita-1")

            val privateHits = store.searchArchivedTurns(legacyPhrase, personaId = "juanita-1")
            val privateContext = store.buildAssistantContext(personaId = "juanita-1")
            val recalledPrivate = store.buildRelevantRecallContext(legacyPhrase, personaId = "juanita-1")

            assertTrue(privateHits.none { it.sessionId == legacySession })
            assertTrue(!privateContext.contains(legacyPhrase))
            assertTrue(!recalledPrivate.contains(legacyPhrase))
            assertTrue(privateContext.contains(privatePhrase))
        } finally {
            store.clearAll()
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
