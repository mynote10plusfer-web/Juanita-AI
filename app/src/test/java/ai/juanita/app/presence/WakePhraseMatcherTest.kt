package ai.juanita.app.presence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseMatcherTest {
    @Test
    fun recognizesActiveIdentityAtStartOfNaturalItalianAndSpanishUtterances() {
        assertTrue(WakePhraseMatcher.matches("Juanita, sai cosa stavo pensando?", "Juanita"))
        assertTrue(WakePhraseMatcher.matches("Ehi, Juanita! Ascolta un momento", "Juanita"))
        assertTrue(WakePhraseMatcher.matches("Oye José, una pregunta", "José"))
        assertTrue(WakePhraseMatcher.matches("José: ¿qué opinas?", "Jose"))
    }

    @Test
    fun doesNotActivateAnIdentityThatIsNotSelected() {
        assertFalse(WakePhraseMatcher.matches("Juanita, ascolta", "José"))
        assertFalse(WakePhraseMatcher.matches("José, escucha", "Juanita"))
    }

    @Test
    fun doesNotTriggerOnAnIncidentalMentionLaterInTheSentence() {
        assertFalse(WakePhraseMatcher.matches("Ieri ho parlato con Juanita", "Juanita"))
        assertFalse(WakePhraseMatcher.matches("Mi amigo se llama José", "José"))
    }

    @Test
    fun handlesEmptyAndMultiWordIdentityNames() {
        assertFalse(WakePhraseMatcher.matches("Ehi", ""))
        assertTrue(WakePhraseMatcher.matches("Hola María José, ¿estás ahí?", "María José"))
    }
}
