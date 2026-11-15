package ai.juanita.app.presence

import java.text.Normalizer
import java.util.Locale

/**
 * Recognizes explicit session commands in the existing Gemini Live user transcript.
 * It accepts the full display name or its first spoken name, and never opens
 * another microphone or speech-recognition service.
 */
object VoiceSessionCommandMatcher {
    enum class Command { STOP_SESSION, CLOSE_APPLICATION }

    private val vocatives = setOf("ehi", "hey", "oye", "hola", "ciao", "ascolta", "senti", "escucha")
    private val politeSuffixes = setOf("per favore", "per piacere", "per cortesia", "por favor", "please")

    private val closePatterns = listOf(
        Regex("""^(?:(?:per favore|per piacere|per cortesia) )?(?:puoi )?chiud(?:i|ere|essi) (?:questa )?(?:l )?(?:applicazione|app)$"""),
        Regex("""^vorrei che (?:tu )?chiudessi (?:l )?(?:applicazione|app)$"""),
        Regex("""^esci dall applicazione$"""),
        Regex("""^esci dalla app$"""),
        Regex("""^esci dall app$"""),
        Regex("""^close (?:the )?app(?:lication)?$"""),
        Regex("""^exit and close$""")
    )

    private val stopPatterns = listOf(
        Regex("""^(?:per favore )?(?:puoi )?fermarti$"""),
        Regex("""^(?:per favore )?(?:puoi )?terminare (?:la )?(?:conversazione|sessione)$"""),
        Regex("""^(?:per favore )?(?:puoi )?interrompere (?:la )?conversazione$"""),
        Regex("""^(?:stop|basta|stop conversation|end conversation|stop session)$""")
    )

    fun match(transcript: String, activeIdentityName: String): Command? {
        val words = tokenize(transcript)
        val nameWords = tokenize(activeIdentityName)
        if (words.isEmpty() || nameWords.isEmpty()) return null

        // The character name is optional. When spoken, accept either the complete
        // configured display name or its first name (e.g. "Juanita" for "Juanita Paisa").
        var offset = 0
        if (words.first() in vocatives) {
            offset = 1
            if (offset >= words.size) return null
            val supplied = words.drop(offset)
            val matchedNameLength = when {
                supplied.take(nameWords.size) == nameWords -> nameWords.size
                supplied.firstOrNull() == nameWords.first() -> 1
                else -> return null
            }
            offset += matchedNameLength
        } else if (words.take(nameWords.size) == nameWords) {
            offset = nameWords.size
        } else if (words.first() == nameWords.first()) {
            offset = 1
        }

        var command = words.drop(offset).joinToString(" ")
        if (command.isBlank()) return null
        politeSuffixes.firstOrNull { command.endsWith(" $it") }?.let {
            command = command.removeSuffix(" $it").trim()
        }

        return when {
            closePatterns.any { it.matches(command) } -> Command.CLOSE_APPLICATION
            stopPatterns.any { it.matches(command) } -> Command.STOP_SESSION
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
