package ai.juanita.app.memory

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNotNull
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
    fun appendsTranscriptAsIndependentEncryptedFramesAcrossStoreInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionId = "journal-${UUID.randomUUID()}"
        val store = EncryptedMemoryStore(context)
        val expected = (0 until 160).map { index -> "Turno $index — contenuto unicode áβč ".repeat(8) }
        try {
            expected.forEachIndexed { index, value ->
                store.appendConversationTurn(sessionId, if (index % 2 == 0) "USER" else "ASSISTANT", value, 1_800_000_000_000L + index)
            }
            val journal = File(context.filesDir, "conversation_archive/$sessionId.journal")
            assertTrue("New turns must use an append-only encrypted journal", journal.isDirectory)
            assertEquals(expected.size, journal.listFiles { file -> file.isFile && file.name.endsWith(".enc") }?.size)
            val restored = EncryptedMemoryStore(context).listConversationTurns(sessionId)
            assertEquals(expected, restored.map { it.text })
            assertEquals(expected.size, restored.size)
        } finally {
            store.deleteConversation(sessionId)
        }
    }

    @Test
    fun compactsJournalWithoutLosingTurnsAndAllowsSubsequentAppend() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionId = "compact-${UUID.randomUUID()}"
        val store = EncryptedMemoryStore(context)
        try {
            repeat(6) { index ->
                store.appendConversationTurn(sessionId, "USER", "messaggio-$index", 1_800_000_000_000L + index)
            }
            store.deleteConversationRange(sessionId, 1_800_000_000_002L, 1_800_000_000_004L)
            val directory = File(context.filesDir, "conversation_archive")
            assertTrue(File(directory, "$sessionId.enc").isFile)
            assertTrue(!File(directory, "$sessionId.journal").exists())
            store.appendConversationTurn(sessionId, "ASSISTANT", "dopo-compattazione", 1_800_000_000_006L)
            val restored = EncryptedMemoryStore(context).listConversationTurns(sessionId)
            assertEquals(listOf("messaggio-0", "messaggio-1", "messaggio-4", "messaggio-5", "dopo-compattazione"), restored.map { it.text })
        } finally {
            store.deleteConversation(sessionId)
        }
    }

    @Test
    fun carriesForwardLatestInterruptedSnapshotToNewSession() {
    @Test
    fun migratesLegacyTranscriptRecordsBeforeTheOldThreeHundredTurnLimitCanDropThem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionId = "legacy-migration-" + UUID.randomUUID()
        val store = EncryptedMemoryStore(context)
        val legacyRecords = JSONArray()
        val expected = (0 until 301).map { index ->
            val id = "legacy-turn-" + index
            legacyRecords.put(JSONObject().apply {
                put("kind", "turn")
                put("id", id)
                put("sessionId", sessionId)
                put("role", if (index % 2 == 0) "USER" else "ASSISTANT")
                put("text", "Legacy transcript entry " + index)
                put("createdAt", 1_800_000_000_000L + index)
                put("personaId", "persona-legacy")
            })
            "Legacy transcript entry " + index
        }
        try {
            val writer = EncryptedMemoryStore::class.java.getDeclaredMethod(
                "writeRecords",
                JSONArray::class.java,
                Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }
            writer.invoke(store, legacyRecords, true)
            val restored = EncryptedMemoryStore(context).listConversationTurns(sessionId)
            assertEquals("Legacy turns must migrate before the old 300-turn ceiling is applied", 301, restored.size)
            assertEquals(expected, restored.map { it.text })
            assertTrue(restored.all { it.personaId == "persona-legacy" })
        } finally {
            store.deleteConversation(sessionId)
            store.clearAll()
        }
    }


        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val olderSession = "interrupted-old-" + UUID.randomUUID()
        val latestSession = "interrupted-latest-" + UUID.randomUUID()
        val nextSession = "interrupted-next-" + UUID.randomUUID()
        val store = EncryptedMemoryStore(context)
        try {
            store.appendContinuityTurn(olderSession, "USER", "Argomento precedente non più attuale.", 1_800_000_000_000L)
            store.appendContinuityTurn(latestSession, "USER", "Stavamo correggendo la riconnessione vocale.", 1_800_000_000_100L)
            store.appendContinuityTurn(latestSession, "ASSISTANT", "Dobbiamo conservare il contesto dopo una caduta.", 1_800_000_000_200L)

            val recovered = EncryptedMemoryStore(context).carryForwardLatestContinuitySnapshot(nextSession)

            assertTrue(recovered.contains("riconnessione vocale"))
            assertTrue(recovered.contains("conservare il contesto"))
            assertTrue(EncryptedMemoryStore(context).buildContinuityContext(latestSession).isBlank())
            assertTrue(EncryptedMemoryStore(context).buildContinuityContext(olderSession).isBlank())

            store.appendContinuityTurn(nextSession, "USER", "Riprendiamo da dove eravamo rimasti.")
            val continued = EncryptedMemoryStore(context).buildContinuityContext(nextSession)
            assertTrue(continued.contains("riconnessione vocale"))
            assertTrue(continued.contains("Riprendiamo da dove eravamo rimasti"))
        } finally {
            store.clearContinuitySnapshots()
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

    @Test
    fun recallReportsUnreadableArchiveInsteadOfTreatingItAsNoHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionId = "corrupt-" + UUID.randomUUID()
        val archiveDirectory = File(context.filesDir, "conversation_archive").apply { mkdirs() }
        val corruptArchive = File(archiveDirectory, "$sessionId.enc")

        try {
            // Simulate an unreadable encrypted archive without touching any user data.
            corruptArchive.writeBytes(byteArrayOf(1, 2, 3, 4))
            val failure = runCatching {
                EncryptedMemoryStore(context).buildRelevantRecallContext("ricordo conversazione")
            }.exceptionOrNull()

            assertNotNull("Recall must surface the unreadable archive instead of returning an empty result.", failure)
            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message.orEmpty().contains("retrieval is incomplete"))
        } finally {
            corruptArchive.delete()
        }
    }

}
