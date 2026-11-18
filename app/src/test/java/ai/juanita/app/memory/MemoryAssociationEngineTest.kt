package ai.juanita.app.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAssociationEngineTest {

    @Test
    fun recallsASecondMemoryThroughTwoGroundedSharedConcepts() {
        val anchor = memory("Juanita AI project uses Firebase Spark free tier", topic = "projects_technology")
        val linked = memory("Firebase Spark free tier must remain within zero-cost limits", topic = "projects_technology")
        val unrelated = memory("The user enjoys scuba diving with turtles", topic = "travel_places")

        val result = MemoryAssociationEngine.rank(
            query = "How is the Juanita AI project progressing?",
            memories = listOf(anchor, linked, unrelated),
            routedTopic = "projects_technology"
        )

        assertEquals(listOf(anchor.id, linked.id), result.map { it.memory.id })
        assertTrue(result.first().direct)
        assertFalse(result.last().direct)
        assertTrue(result.last().bridgeTerms.containsAll(listOf("firebase", "spark", "free", "tier")))
    }

    @Test
    fun doesNotCreateAssociationFromOneGenericSharedWord() {
        val anchor = memory("The user is developing an application")
        val weak = memory("The user enjoys the sea")
        val result = MemoryAssociationEngine.rank(
            query = "Tell me about the application",
            memories = listOf(anchor, weak),
            routedTopic = "projects_technology"
        )

        assertEquals(listOf(anchor.id), result.map { it.memory.id })
    }

    @Test
    fun commonLongWordIsNotTreatedAsDistinctiveWhenRepeatedAcrossArchive() {
        val anchor = memory("Project history includes conversation", topic = "projects_technology")
        val candidate = memory("Conversation quality matters")
        val third = memory("Conversation memory design is evolving")

        val result = MemoryAssociationEngine.rank(
            query = "Tell me about project history",
            memories = listOf(anchor, candidate, third),
            routedTopic = "projects_technology"
        )

        assertEquals(listOf(anchor.id), result.map { it.memory.id })
    }

    @Test
    fun preservesTopicOnlyFallbackWithoutCallingItAnAssociation() {
        val sameTopic = memory("A previous Android build used a different Gradle plugin", topic = "projects_technology")
        val result = MemoryAssociationEngine.rank(
            query = "How is the Juanita project progressing?",
            memories = listOf(sameTopic),
            routedTopic = "projects_technology"
        )

        assertEquals(1, result.size)
        assertFalse(result.single().direct)
        assertTrue(result.single().topicFallback)
        assertTrue(result.single().bridgeTerms.isEmpty())
    }

    @Test
    fun recognizesHighConfidenceConceptsAcrossItalianSpanishAndEnglish() {
        val motorcycle = memory("La motocicletta Yamaha è il mezzo usato ogni giorno", topic = "vehicles_transport")
        val voice = memory("La voz paisa deve suonare naturale", topic = "projects_technology")
        val result = MemoryAssociationEngine.rank(
            query = "Tell me about my scooter and voice",
            memories = listOf(motorcycle, voice),
            routedTopic = "general"
        )

        assertEquals(setOf(motorcycle.id, voice.id), result.map { it.memory.id }.toSet())
        assertTrue(result.all { it.direct })
    }

    @Test
    fun recallsWakeActivationAcrossItalianSpanishAndEnglishParaphrases() {
        val stored = memory(
            "Offline wake phrase lets the voice assistant activate without touching the phone",
            topic = "projects_technology"
        )
        val result = MemoryAssociationEngine.rank(
            query = "Come posso llamar a mi asistente para attivare la voce sin conexion?",
            memories = listOf(stored),
            routedTopic = "projects_technology"
        )

        assertEquals(1, result.size)
        assertTrue(result.single().direct)
        assertTrue(result.single().score >= 20)
    }

    @Test
    fun recallsConversationContextWithoutReclassifyingPersonalMemoryAsAConfirmedFact() {
        val provisional = MemoryItem(
            id = "contextual",
            text = "Juanita can retrieve prior conversation episodes and connect their context",
            category = "project",
            source = "automatic",
            createdAtMillis = 3L,
            confidence = 0.72f,
            confirmedByUser = false,
            sensitive = false,
            topic = "projects_technology"
        )
        val result = MemoryAssociationEngine.rank(
            query = "recuperar el contexto de episodios anteriores",
            memories = listOf(provisional),
            routedTopic = "projects_technology"
        )

        assertEquals("contextual", result.single().memory.id)
        assertTrue(result.single().direct)
        assertFalse(result.single().memory.confirmedByUser)
    }

    @Test
    fun directEvidenceAlwaysRanksBeforeAssociativeEvidence() {
        val anchor = memory("Juanita project voice engine")
        val linked = memory("Juanita project battery optimization")
        val direct = memory("The voice engine needs an interrupt button")

        val result = MemoryAssociationEngine.rank(
            query = "voice engine",
            memories = listOf(linked, direct, anchor),
            routedTopic = "general"
        )

        assertTrue(result.first().direct)
        assertEquals(direct.id, result.first().memory.id)
        assertTrue(result.any { it.memory.id == linked.id && !it.direct })
    }

    @Test
    fun keepsUnconfirmedAutomaticMemoryAvailableAsEvidence() {
        val provisional = MemoryItem(
            id = "provisional",
            text = "Juanita AI project uses offline voice activation",
            category = "project",
            source = "automatic",
            createdAtMillis = 2L,
            confidence = 0.86f,
            confirmedByUser = false,
            sensitive = false,
            topic = "projects_technology"
        )
        val result = MemoryAssociationEngine.rank(
            query = "How is the Juanita AI project progressing?",
            memories = listOf(provisional),
            routedTopic = "projects_technology"
        )
        assertEquals("provisional", result.single().memory.id)
        assertFalse(result.single().memory.confirmedByUser)
    }

    private fun memory(
        text: String,
        topic: String = "general",
        confirmed: Boolean = true
    ) = MemoryItem(
        id = text.hashCode().toString(),
        text = text,
        category = "project",
        source = "user_confirmed",
        createdAtMillis = 1L,
        confidence = 1f,
        confirmedByUser = confirmed,
        sensitive = false,
        topic = topic
    )
}
