package ai.juanita.app.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryTopicClassifierTest {
    @Test
    fun routesVehicleQuestionsToVehicleTopic() {
        assertEquals("vehicles_transport", MemoryTopicClassifier.classify("Come va la mia Yamaha Tricity 300?"))
        assertEquals("vehicles_transport", MemoryTopicClassifier.classify("Devo controllare i freni dello scooter"))
    }

    @Test
    fun routesProjectQuestionsToTechnologyTopic() {
        assertEquals("projects_technology", MemoryTopicClassifier.classify("Come procede la build APK di Juanita AI?"))
        assertEquals("projects_technology", MemoryTopicClassifier.classify("Problema Android con Gradle"))
    }

    @Test
    fun routesOtherSubjectsToTheirOwnTopics() {
        assertEquals("home_property", MemoryTopicClassifier.classify("Devo sistemare la pergola di casa"))
        assertEquals("travel_places", MemoryTopicClassifier.classify("Vorrei fare un viaggio a Tenerife"))
        assertEquals("relationships_family", MemoryTopicClassifier.classify("Come rispondo a questa ragazza?"))
        assertEquals("interests_hobbies", MemoryTopicClassifier.classify("Consigli per la fotografia con Canon"))
    }

    @Test
    fun supportsAccentsAndUnknownFallback() {
        assertEquals("travel_places", MemoryTopicClassifier.classify("Vacaciones en España y Tenerife"))
        assertEquals(MemoryTopicClassifier.GENERAL, MemoryTopicClassifier.classify("Una domanda generica"))
        assertEquals(MemoryTopicClassifier.GENERAL, MemoryTopicClassifier.classify(""))
    }
}
