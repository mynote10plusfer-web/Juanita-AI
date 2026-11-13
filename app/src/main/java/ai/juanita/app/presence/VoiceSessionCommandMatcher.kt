package ai.juanita.app.presence

import java.text.Normalizer
import java.util.Locale

/**
 * Recognizes only explicit commands addressed to the currently active character.
 * It consumes the existing Gemini Live user transcript; it never opens another
 * microphone or speech-recognition service.
 */
object VoiceSessionCommandMatcher {
    enum class Command { STOP_SESSION, CLOSE_APPLICATION }

    private val vocatives = setOf("ehi", "hey", "oye", "hola", "ciao", "ascolta", "senti", "escucha")
    private val politeSuffixes = setOf("per favore", "per piacere", "por favor", "please")

    fun match(transcript: String, activeIdentityName: String): Command? {
        val words = tokenize(transcript)
        val nameWords = tokenize(activeIdentityName)
        if (words.isEmpty() || nameWords.isEmpty()) return null

        var offset = if (words.first() in vocatives) 1 else 0
        if (words.size < offset + nameWords.size) return null
        if (words.subList(offset, offset + nameWords.size) != nameWords) return null

        var command = words.drop(offset + nameWords.size).joinToString(" ")
        if (command.isBlank()) return null
        politeSuffixes.firstOrNull { command.endsWith(" $it") }?.let {
            command = command.removeSuffix(" $it").trim()
        }

        return when (command) {
            "chiudi l applicazione", "chiudi applicazione", "chiudi l app",
            "chiudi app", "esci e chiudi", "chiudi e esci",
            "close application", "close the application", "close app", "exit and close" ->
                Command.CLOSE_APPLICATION

            "fermati", "stop", "basta", "termina conversazione",
            "termina la conversazione", "termina sessione", "termina la sessione",
            "interrompi", "interrompi conversazione", "interrompi la conversazione",
            "stop conversation", "end conversation", "stop session" ->
                Command.STOP_SESSION

            else -> null
        }
    }

    private fun tokenize(value: String): List<String> =
        normalize(value).split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank)

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .trim()
}
