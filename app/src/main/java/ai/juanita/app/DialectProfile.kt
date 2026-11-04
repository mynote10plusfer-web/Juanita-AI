package ai.juanita.app

/**
 * A regional speech style profile. The locale is a fallback for the current
 * Android system TTS; it does not imply that the system voice reproduces the
 * regional accent. Regional language generation and dedicated voice models
 * will be implemented as separate providers.
 */
internal data class DialectProfile(
    val id: String,
    val label: String,
    val languageTag: String,
    val sample: String,
    val note: String
)
