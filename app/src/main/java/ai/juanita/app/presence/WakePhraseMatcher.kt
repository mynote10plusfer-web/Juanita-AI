package ai.juanita.app.presence

import java.text.Normalizer
import java.util.Locale

/**
 * Detects a direct, spoken call to the currently selected identity.
 *
 * Deliberately accepts the name only at the beginning of the utterance,
 * optionally preceded by a short vocative. It does not trigger merely because
 * the identity is mentioned while the user is talking to someone else.
 */
object WakePhraseMatcher {
    private val vocatives = setOf(
        "ehi", "hey", "oye", "hola", "ciao", "ascolta", "senti", "escucha"
    )

    fun matches(transcript: String, activeIdentityName: String): Boolean {
        val name = normalize(activeIdentityName)
        val words = tokenize(transcript)
        if (name.isBlank() || words.isEmpty()) return false

        val nameWords = name.split(' ').filter(String::isNotBlank)
        if (nameWords.isEmpty()) return false

        fun matchesAt(start: Int): Boolean =
            words.size >= start + nameWords.size &&
                words.subList(start, start + nameWords.size) == nameWords

        return matchesAt(0) || (words.first() in vocatives && matchesAt(1))
    }

    private fun tokenize(value: String): List<String> =
        normalize(value).split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank)

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .trim()
}
