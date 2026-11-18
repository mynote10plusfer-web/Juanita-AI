package ai.juanita.app.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordPhraseCatalogTest {
    @Test
    fun supportsOnlyTheTwoIdentityBoundPhrases() {
        assertTrue(WakeWordPhraseCatalog.isSupported("Hola Juanita"))
        assertTrue(WakeWordPhraseCatalog.isSupported("hola juanita"))
        assertTrue(WakeWordPhraseCatalog.isSupported("Hola José"))
        assertTrue(WakeWordPhraseCatalog.isSupported("HOLA JOSÉ"))
        assertFalse(WakeWordPhraseCatalog.isSupported("Ciao Juanita"))
        assertFalse(WakeWordPhraseCatalog.isSupported("Hola Jose"))
        assertFalse(WakeWordPhraseCatalog.isSupported("Hola Riccardo"))
    }

    @Test
    fun bothKeywordLinesMatchTheirPinnedCanonicalHashes() {
        val juanita = WakeWordPhraseCatalog.definition("Hola Juanita")
        val jose = WakeWordPhraseCatalog.definition("Hola José")
        assertNotNull(juanita)
        assertNotNull(jose)
        assertTrue(WakeWordPhraseCatalog.keywordHashIsValid(juanita!!))
        assertTrue(WakeWordPhraseCatalog.keywordHashIsValid(jose!!))
    }

    @Test
    fun phraseCatalogPreservesCanonicalSpellingAndBpeKeywordTags() {
        assertEquals(
            "▁ho la ▁juan i ta :1.0 #0.20 @hola_juanita\n",
            WakeWordPhraseCatalog.definition("hola juanita")?.keywordLine
        )
        assertEquals(
            "▁ho la ▁ jo s é :1.0 #0.20 @hola_jose\n",
            WakeWordPhraseCatalog.definition("hola josé")?.keywordLine
        )
        assertNull(WakeWordPhraseCatalog.definition("Hola Jose"))
    }
}
