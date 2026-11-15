package ai.juanita.app.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceSessionCommandMatcherTest {
    @Test
    fun recognizesDirectStopCommandsForTheActiveIdentity() {
        assertEquals(VoiceSessionCommandMatcher.Command.STOP_SESSION,
            VoiceSessionCommandMatcher.match("Juanita, fermati", "Juanita"))
        assertEquals(VoiceSessionCommandMatcher.Command.STOP_SESSION,
            VoiceSessionCommandMatcher.match("Ehi Juanita, termina la conversazione per favore", "Juanita"))
        assertEquals(VoiceSessionCommandMatcher.Command.STOP_SESSION,
            VoiceSessionCommandMatcher.match("Oye José, stop conversation", "José"))
        assertEquals(VoiceSessionCommandMatcher.Command.STOP_SESSION,
            VoiceSessionCommandMatcher.match("puoi fermarti per favore", "Juanita"))
    }

    @Test
    fun recognizesNaturalApplicationCloseRequests() {
        val expected = VoiceSessionCommandMatcher.Command.CLOSE_APPLICATION
        assertEquals(expected, VoiceSessionCommandMatcher.match("Juanita, chiudi l'applicazione", "Juanita"))
        assertEquals(expected, VoiceSessionCommandMatcher.match("Ehi Juanita, chiudi app", "Juanita"))
        assertEquals(expected, VoiceSessionCommandMatcher.match("Juanita, puoi chiudere l'applicazione?", "Juanita"))
        assertEquals(expected, VoiceSessionCommandMatcher.match("Juanita, per favore chiudi questa app", "Juanita"))
        assertEquals(expected, VoiceSessionCommandMatcher.match("vorrei che chiudessi l'app", "Juanita"))
        assertEquals(expected, VoiceSessionCommandMatcher.match("chiudi applicazione per favore", "José"))
    }

    @Test
    fun recognizesCloseCommandAcrossIncrementalLiveTranscriptionChunks() {
        val buffer = VoiceSessionCommandMatcher.TranscriptAccumulator()
        var transcript = buffer.append("Juanita")
        transcript = buffer.append("Juanita, puedes")
        transcript = buffer.append("cerrar")
        transcript = buffer.append("la aplicación")
        assertEquals(
            VoiceSessionCommandMatcher.Command.CLOSE_APPLICATION,
            VoiceSessionCommandMatcher.match(transcript, "Juanita Paisa")
        )

        buffer.clear()
        assertEquals("", buffer.append(""))
    }

    @Test
    fun recognizesShortNameForMultiWordIdentityAndNameOptionalCommands() {
        val expected = VoiceSessionCommandMatcher.Command.CLOSE_APPLICATION
        assertEquals(expected, VoiceSessionCommandMatcher.match("Juanita, chiudi l'app", "Juanita Paisa"))
        assertEquals(expected, VoiceSessionCommandMatcher.match("chiudi l'app", "Juanita Paisa"))
        assertEquals(VoiceSessionCommandMatcher.Command.STOP_SESSION,
            VoiceSessionCommandMatcher.match("fermati", "Juanita Paisa"))
    }

    @Test
    fun ignoresIncidentalMentionsAndNegatedOrUnrelatedCommands() {
        assertNull(VoiceSessionCommandMatcher.match("Per favore fermati Juanita", "Juanita"))
        assertNull(VoiceSessionCommandMatcher.match("Juanita, fermati", "José"))
        assertNull(VoiceSessionCommandMatcher.match("Ieri Juanita mi ha detto fermati", "Juanita"))
        assertNull(VoiceSessionCommandMatcher.match("Juanita, continua pure", "Juanita"))
        assertNull(VoiceSessionCommandMatcher.match("non chiudere l'applicazione", "Juanita"))
        assertNull(VoiceSessionCommandMatcher.match("puoi chiudere la finestra?", "Juanita"))
    }
}
