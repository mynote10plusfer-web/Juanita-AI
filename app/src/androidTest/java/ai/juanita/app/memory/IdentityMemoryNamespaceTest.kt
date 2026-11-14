package ai.juanita.app.memory

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun automaticCloudMemoryIsOffByDefaultAndRequiresExplicitOptIn() {
        val context = isolatedContext()
        context.getSharedPreferences("juanita_encrypted_memory", Context.MODE_PRIVATE).edit().clear().commit()
        val store = EncryptedMemoryStore(context)
        try {
            assertFalse("SAFE mode must not send transcript excerpts to Gemini by default", store.isAutomaticMemoryEnabled)
            store.setAutomaticMemoryEnabled(true)
            assertTrue("Automatic extraction is enabled only after explicit opt-in", store.isAutomaticMemoryEnabled)
            store.setAutomaticMemoryEnabled(false)
            assertFalse(store.isAutomaticMemoryEnabled)
        } finally {
            store.clearAll()
        }
    }

    @Test
    fun safeModeDisablesLegacyAutomaticMemoryPreferenceOnce() {
        val context = isolatedContext()
        val preferences = context.getSharedPreferences("juanita_encrypted_memory", Context.MODE_PRIVATE)
        preferences.edit().clear().putBoolean("automatic_memory_enabled", true).commit()
        try {
            val store = EncryptedMemoryStore(context)
            assertFalse("SAFE mode must revoke a legacy enabled/default state on first upgrade", store.isAutomaticMemoryEnabled)
            store.setAutomaticMemoryEnabled(true)
            assertTrue("A new explicit user opt-in must persist", store.isAutomaticMemoryEnabled)
            assertTrue(EncryptedMemoryStore(context).isAutomaticMemoryEnabled)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun encryptedAutomaticMemoryQueueSurvivesStoreRecreationAndRemainsBounded() {
        val context = isolatedContext()
        val preferences = context.getSharedPreferences("juanita_encrypted_memory", Context.MODE_PRIVATE)
        // Each instrumentation test gets an isolated preference namespace and must not
        // inherit the explicit-consent toggle left by another test in the same process.
        preferences.edit().clear().commit()
        val store = EncryptedMemoryStore(context)
        val turns = (1..8).map { "Turno automatico cifrato $it ${UUID.randomUUID()}" }
        try {
            assertFalse(store.isAutomaticMemoryEnabled)
            store.setAutomaticMemoryEnabled(true)
            val clearVersion = store.memoryClearVersion
            turns.forEachIndexed { index, turn ->
                assertEquals(index + 1, store.enqueuePendingAutomaticMemoryTurn(turn, clearVersion))
            }
            assertEquals(-2, store.enqueuePendingAutomaticMemoryTurn("Overflow", clearVersion))

            val encryptedPayload = context.getSharedPreferences(
                "juanita_encrypted_memory", Context.MODE_PRIVATE
            ).getString("encrypted_records", null).orEmpty()
            assertTrue("Pending transcript excerpts must be persisted in the encrypted store", encryptedPayload.isNotBlank())
            assertFalse("Plain transcript excerpts must never appear in preferences", encryptedPayload.contains(turns.first()))

            val recreatedStore = EncryptedMemoryStore(isolatedContext())
            assertEquals("Pending turns must survive store recreation", turns,
                recreatedStore.readPendingAutomaticMemoryTurns(clearVersion))

            recreatedStore.setAutomaticMemoryEnabled(false)
            assertTrue("Disabling automatic memory must invalidate queued transcript excerpts",
                recreatedStore.readPendingAutomaticMemoryTurns().isEmpty())
            recreatedStore.setAutomaticMemoryEnabled(true)
            assertTrue("Re-enabling memory must not resurrect excerpts from an earlier consent period",
                recreatedStore.readPendingAutomaticMemoryTurns(clearVersion).isEmpty())
        } finally {
            store.clearAll()
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun sharedKnowledgeIsVisibleToEveryCharacterWhileConversationHistoryIsIdentityScoped() {
        val context = isolatedContext()
        val juanitaMemory = EncryptedMemoryStore(context)
        val joseMemory = EncryptedMemoryStore(context)
        val tokenFromJuanita = "Conoscenza condivisa Juanita ${UUID.randomUUID()}"
        val tokenFromJose = "Conoscenza condivisa Jose ${UUID.randomUUID()}"
        val juanitaSession = "juanita-session-${UUID.randomUUID()}"
        val joseSession = "jose-session-${UUID.randomUUID()}"
        val juanitaTurn = "Conversazione iniziata con Juanita"
        val joseTurn = "Conversazione iniziata con José"

        try {
            juanitaMemory.addMemory(tokenFromJuanita)
            juanitaMemory.appendConversationTurn(juanitaSession, "USER", juanitaTurn, personaId = "juanita-1")

            joseMemory.addMemory(tokenFromJose)
            joseMemory.appendConversationTurn(joseSession, "USER", joseTurn, personaId = "jose-1")

            assertTrue(joseMemory.listMemories().any { it.text == tokenFromJuanita })
            assertTrue(juanitaMemory.listMemories().any { it.text == tokenFromJose })
            assertTrue(juanitaMemory.listConversationTurns(juanitaSession).any { it.text == juanitaTurn })
            assertTrue(joseMemory.listConversationTurns(joseSession).any { it.text == joseTurn })
            assertTrue(juanitaMemory.searchArchivedTurns(joseTurn, personaId = "juanita-1").none { it.sessionId == joseSession })
            assertTrue(joseMemory.searchArchivedTurns(juanitaTurn, personaId = "jose-1").none { it.sessionId == juanitaSession })
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
    fun editingAMemorySupersedesTheOldFactAndPreservesCorrectionHistory() {
        val context = isolatedContext()
        val store = EncryptedMemoryStore(context)
        val original = "Preferenza iniziale correzione test"
        val corrected = "Preferenza corretta confermata dall'utente"
        try {
            val saved = store.addMemory(original, source = "automatic", confidence = 0.84f, confirmedByUser = false)
            assertTrue(store.updateMemory(saved.id, corrected))

            val updated = store.listMemories().single { it.text == corrected }
            assertTrue("A correction must receive a new stable record ID", updated.id != saved.id)
            assertEquals("user_corrected", updated.source)
            assertEquals(1.0f, updated.confidence, 0.0f)
            assertTrue(updated.confirmedByUser)
            assertEquals("Only the corrected fact may remain in ordinary recall", 1, store.listMemories().size)

            val history = store.listAllMemories()
            val superseded = history.single { it.id == saved.id }
            assertEquals(original, superseded.text)
            assertEquals(updated.id, superseded.supersededBy)

            val restored = EncryptedMemoryStore(isolatedContext())
            val reloaded = restored.listMemories().single { it.text == corrected }
            assertEquals(updated.id, reloaded.id)
            assertEquals("user_corrected", reloaded.source)
            assertEquals(1.0f, reloaded.confidence, 0.0f)
            assertTrue("Explicit user correction must survive encrypted persistence", reloaded.confirmedByUser)
            val restoredHistory = restored.listAllMemories().single { it.id == saved.id }
            assertEquals(original, restoredHistory.text)
            assertEquals(reloaded.id, restoredHistory.supersededBy)
        } finally {
            store.clearAll()
        }
    }

    @Test
    fun retentionAndRangeDeletionPreserveUnreadableSessionArchiveBytes() {
        val context = isolatedContext()
        val store = EncryptedMemoryStore(context)
        val sessionId = "corrupt-${UUID.randomUUID()}"
        val journalDirectory = File(File(context.filesDir, "conversation_archive"), "$sessionId.journal")
        val corruptedBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)

        try {
            store.appendConversationTurn(sessionId, "USER", "Archivio da proteggere")
            assertTrue("Expected an encrypted per-turn session journal", journalDirectory.isDirectory)
            val frame = journalDirectory.listFiles()?.singleOrNull { it.isFile && it.name.endsWith(".enc") }
                ?: throw AssertionError("Expected one encrypted transcript frame")

            // Simulate a truncated/corrupted ciphertext or a transient Keystore read failure.
            frame.writeBytes(corruptedBytes)
            store.archiveRetentionDays = 7
            assertTrue("Retention must not delete an unreadable journal", journalDirectory.exists())
            assertArrayEquals("Retention must preserve unreadable bytes", corruptedBytes, frame.readBytes())

            store.deleteArchiveRange(0L, System.currentTimeMillis() + 1L)
            assertTrue("Range deletion must not delete an unreadable journal", journalDirectory.exists())
            assertArrayEquals("Range deletion must preserve unreadable bytes", corruptedBytes, frame.readBytes())
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

            val juanitaContext = store.buildAssistantContext(personaId = "juanita-1")
            val joseContext = store.buildAssistantContext(personaId = "jose-1")
            assertTrue(juanitaContext.contains(shared))
            assertTrue(joseContext.contains(shared))
            assertTrue(juanitaContext.contains(juanitaOnly))
            assertTrue(!juanitaContext.contains(joseOnly))
            assertTrue(joseContext.contains(joseOnly))
            assertTrue(!joseContext.contains(juanitaOnly))

            val juanitaRecall = store.buildRelevantRecallContext("Fatto comune", personaId = "juanita-1")
            val joseRecall = store.buildRelevantRecallContext("Fatto comune", personaId = "jose-1")
            assertTrue(juanitaRecall.contains(shared))
            assertTrue(joseRecall.contains(shared))
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

    @Test
    fun automaticallySuggestedMemoriesRequireApprovalBeforeTheyEnterAssistantContext() {
        val context = isolatedContext()
        val store = EncryptedMemoryStore(context)
        try {
            store.setAutomaticMemoryEnabled(true)
            val pending = store.addAutomaticMemory(
                "Preferisce risposte concise e naturali",
                "communication",
                0.96f,
                confirmedByUser = false
            ) ?: throw AssertionError("Expected pending memory")
            assertFalse(pending.confirmedByUser)
            assertFalse(store.buildAssistantContext().contains(pending.text))
            assertTrue(store.confirmMemory(pending.id))
            val restored = EncryptedMemoryStore(isolatedContext()).listMemories().single { it.id == pending.id }
            assertTrue(restored.confirmedByUser)
            assertTrue(store.buildAssistantContext().contains(pending.text))
        } finally {
            store.clearAll()
        }
    }

}
