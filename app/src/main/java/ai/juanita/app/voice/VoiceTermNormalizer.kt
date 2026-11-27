package ai.juanita.app.voice

/**
 * A correction explicitly confirmed by the user.
 *
 * The alias is the exact phrase previously recognized incorrectly; the canonical
 * value is the spelling the user clarified. Corrections are deliberately narrow:
 * they must not rewrite unrelated words or infer a spelling from sound alone.
 */
data class ConfirmedVoiceTermCorrection(
    val alias: String,
    val canonical: String
) {
    fun toStorageText(): String = alias + STORAGE_SEPARATOR + canonical

    companion object {
        private const val STORAGE_SEPARATOR = "\u001F"

        fun fromStorageText(value: String): ConfirmedVoiceTermCorrection? {
            val parts = value.split(STORAGE_SEPARATOR)
            if (parts.size != 2 || parts.any { it.isBlank() }) return null
            return ConfirmedVoiceTermCorrection(parts[0], parts[1])
        }
    }
}

/**
 * Small, deterministic text normalizer for user-confirmed voice corrections.
 *
 * This class does not attempt general speech recognition. It runs after the speech
 * provider has produced text and only applies corrections that the user explicitly
 * taught the app. Raw audio and original transcript remain outside this class.
 */
object VoiceTermNormalizer {

    private val doubleLetterCorrections = listOf(
        Regex("""(?iu)\b(?:sono|è|e)\s+(?:proprio\s+)?due\s+([a-z]+)\b"""),
        Regex("""(?iu)\b(?:doppia|doppio)\s+([a-z]+)\b"""),
        Regex("""(?iu)\b(?:there are|it's|it is)\s+two\s+([a-z]+)s?\b"""),
        Regex("""(?iu)\bdouble\s+([a-z]+)\b"""),
        Regex("""(?iu)\bson\s+dos\s+([a-z]+)\b"""),
        Regex("""(?iu)\b(?:doble|dos veces la letra)\s+([a-z]+)\b""")
    )

    private val spokenLetters = mapOf(
        "ti" to 't', "tee" to 't', "te" to 't',
        "esse" to 's', "ese" to 's',
        "emme" to 'm', "eme" to 'm',
        "enne" to 'n', "ene" to 'n',
        "elle" to 'l', "ele" to 'l',
        "acca" to 'h', "ache" to 'h',
        "ics" to 'x'
    )

    /**
     * Learns only a clearly stated doubled-letter correction against the immediately
     * preceding user phrase. Example: "Suzuki GSX 8T" + "No, sono due T".
     */
    fun extractExplicitDoubleLetterCorrection(
        previousUserUtterance: String,
        correctionUtterance: String
    ): ConfirmedVoiceTermCorrection? {
        if (previousUserUtterance.isBlank() || correctionUtterance.isBlank()) return null
        val correction = correctionUtterance.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val letter = doubleLetterCorrections.firstNotNullOfOrNull { pattern ->
            pattern.find(correction)?.groupValues?.getOrNull(1)?.let(::resolveLetter)
        } ?: return null

        val previous = previousUserUtterance.trim().trimEnd('.', ',', '!', '?', ';', ':')
        val lastTokenMatch = Regex("""([\p{L}\p{N}]+)$""").find(previous) ?: return null
        val token = lastTokenMatch.groupValues[1]
        if (token.length < 2 || !token.last().equals(letter, ignoreCase = true)) return null
        if (token[token.lastIndex - 1].equals(letter, ignoreCase = true)) return null

        val canonicalToken = token.dropLast(1) + letter.uppercaseChar() + letter.uppercaseChar()
        val canonical = previous.dropLast(token.length) + canonicalToken
        return ConfirmedVoiceTermCorrection(previous, canonical)
    }

    /** Apply only explicit, previously confirmed aliases; longest aliases win. */
    fun normalize(
        text: String,
        corrections: List<ConfirmedVoiceTermCorrection>
    ): String {
        if (text.isBlank() || corrections.isEmpty()) return text
        return corrections
            .filter { it.alias.isNotBlank() && it.canonical.isNotBlank() }
            .distinctBy { it.alias.lowercase() }
            .sortedByDescending { it.alias.length }
            .fold(text) { current, correction ->
                current.replace(
                    Regex(Regex.escape(correction.alias), RegexOption.IGNORE_CASE),
                    correction.canonical
                )
            }
    }

    private fun resolveLetter(raw: String): Char? {
        val normalized = raw.lowercase().trim()
        return if (normalized.length == 1 && normalized[0].isLetter()) normalized[0]
        else spokenLetters[normalized]
    }


}
