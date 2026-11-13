package ai.juanita.app.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceSessionCommandMatcherTest {
    @Test
    fun recognizesDirectStopCommandsForTheActiveIdentity() {
        assertEquals(
            VoiceSessionCommandMatcher.Command.STOP_SESSION,
            VoiceSessionCommandMatcher.match("Juanita, fermati", "Juanita")
        )
        assertEquals(
            VoiceSessionCommandMatcher.Command.STOP_SESSION,
            VoiceSessionCommandMatcher.match("Ehi Juanita, termina la conversazione per favore", "Juanita")
        )
        assertEquals(
            VoiceSessionCommandMatcher.Command.STOP_SESSION,
            VoiceSessionCommandMatcher.match("Oye José, stop conversation", "José")
        )
    }

    @Test
    fun recognizesExplicitApplicationCloseCommands() {
        assertEquals(
            VoiceSessionCommandMatcher.Command.CLOSE_APPLICATION,
            VoiceSessionCommandMatcher.match("Juanita, chiudi l'applicazione", "Juanita")
        )
        assertEquals(
            VoiceSessionCommandMatcher.Command.CLOSE_APPLICATION,
            VoiceSessionCommandMatcher.match("Ehi Juanita, chiudi app", "Juanita")
        )
    }

    @Test
    fun ignoresIncidentalMentionsAndCommandsAddressedToAnotherIdentity() {
        assertNull(VoiceSessionCommandMatcher.match("Per favore fermati Juanita", "Juanita"))
        assertNull(VoiceSessionCommandMatcher.match("Juanita, fermati", "José"))
        assertNull(VoiceSessionCommandMatcher.match("Ieri Juanita mi ha detto fermati", "Juanita"))
        assertNull(VoiceSessionCommandMatcher.match("Juanita, continua pure", "Juanita"))
    }
}
