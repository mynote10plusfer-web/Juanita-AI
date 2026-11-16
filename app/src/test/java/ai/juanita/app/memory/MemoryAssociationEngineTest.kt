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
            routedTopic = "general"
        )

        assertEquals(listOf(anchor.id), result.map { it.memory.id })
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
