package ai.juanita.app.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalIdentityMemoryScopeTest {
    private fun identity(
        name: String,
        presentation: IdentityPresentation,
        language: String
    ) = DigitalIdentity(
        displayName = name,
        presentation = presentation,
        origin = CulturalOrigin("ES", "España", "Canarias", "Tenerife", "Canario"),
        preferredLanguageTag = language
    )

    @Test
    fun feminineCharactersShareKnowledgeScopeButKeepSeparateIds() {
        val canary = identity("Juanita Canaria", IdentityPresentation.FEMININE, "es-ES")
        val colombian = identity("Juanita Colombiana", IdentityPresentation.FEMININE, "es-CO")

        assertEquals(canary.effectiveMemoryScopeId, colombian.effectiveMemoryScopeId)
        assertNotEquals(canary.id, colombian.id)
    }

    @Test
    fun masculineCharactersShareTheirOwnScopeSeparateFromFeminineCharacters() {
        val joseOne = identity("José", IdentityPresentation.MASCULINE, "es-ES")
        val joseTwo = identity("José Latino", IdentityPresentation.MASCULINE, "es-CO")
        val juanita = identity("Juanita", IdentityPresentation.FEMININE, "es-ES")

        assertEquals(joseOne.effectiveMemoryScopeId, joseTwo.effectiveMemoryScopeId)
        assertNotEquals(joseOne.effectiveMemoryScopeId, juanita.effectiveMemoryScopeId)
    }

    @Test
    fun systemInstructionLocksTheSelectedLanguageAndRegionalDossier() {
        val colombian = identity("Juanita Colombiana", IdentityPresentation.FEMININE, "es-CO")
            .copy(origin = CulturalOrigin("CO", "Colombia", "Antioquia", "Medellín", "Paisa"))
        val canary = identity("Juanita Canaria", IdentityPresentation.FEMININE, "es-ES")

        val colombianPrompt = colombian.toSystemInstruction()
        val canaryPrompt = canary.toSystemInstruction()

        assertTrue(colombianPrompt.contains("Spanish (Colombia)"))
        assertTrue(colombianPrompt.contains("CONTEMPORARY PAISA / MEDELLÍN"))
        assertTrue(canaryPrompt.contains("Spanish (Spain)"))
        assertTrue(canaryPrompt.contains("CANARY ISLANDS"))
    }

    @Test
    fun standardCzechIdentityReceivesContemporaryCzechLanguageLock() {
        val czech = identity("Juanita", IdentityPresentation.FEMININE, "cs-CZ")
            .copy(origin = CulturalOrigin("CZ", "Czech Republic", "Prague", "Prague", ""))
        val prompt = czech.toSystemInstruction()
        assertTrue(prompt.contains("Czech"))
        assertTrue(prompt.contains("STANDARD CZECH"))
        assertTrue(prompt.contains("correct Czech grammar, diacritics"))
    }

    @Test
    fun moravianIdentityUsesSubtleRegionalGuidanceWithoutSlovakMixing() {
        val moravian = identity("Juanita", IdentityPresentation.FEMININE, "cs-CZ")
            .copy(origin = CulturalOrigin("CZ", "Czech Republic", "Moravia", "Brno", "Moravian"))
        val prompt = moravian.toSystemInstruction()
        assertTrue(prompt.contains("MORAVIAN CZECH"))
        assertTrue(prompt.contains("distinct local varieties"))
        assertTrue(prompt.contains("Do not drift into Slovak"))
    }
}
