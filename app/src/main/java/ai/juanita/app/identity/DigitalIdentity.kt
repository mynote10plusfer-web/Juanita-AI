package ai.juanita.app.identity

import java.util.UUID

/** Presentation used for self-reference and voice matching; it does not define personality. */
enum class IdentityPresentation { FEMININE, MASCULINE }

data class CulturalOrigin(
    val countryCode: String,
    val countryName: String,
    val region: String = "",
    val locality: String = "",
    val community: String = ""
)

data class IdentityPersonality(
    val warmth: Float = 0.7f,
    val humour: Float = 0.6f,
    val curiosity: Float = 0.8f,
    val expressiveness: Float = 0.7f,
    val formality: Float = 0.3f,
    val empathy: Float = 0.8f,
    val seriousness: Float = 0.5f
)

data class CulturalSource(
    val title: String,
    val url: String,
    val publisher: String = "",
    val retrievedAtMillis: Long = System.currentTimeMillis(),
    val topic: String = "general",
    val confidence: Float = 0.5f,
    val summary: String = ""
)

data class CulturalDossier(
    val summary: String,
    val sources: List<CulturalSource>,
    val version: Int = 1,
    val approvedAtMillis: Long? = null
)

/**
 * One independently persisted digital identity. Its memory namespace is bound
 * to its immutable UUID and cannot be reassigned to another identity.
 */
data class DigitalIdentity(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val presentation: IdentityPresentation,
    val origin: CulturalOrigin,
    val preferredLanguageTag: String,
    val languages: List<String> = emptyList(),
    val personality: IdentityPersonality = IdentityPersonality(),
    val biography: String = "",
    val culturalDossier: CulturalDossier? = null,
    val memoryNamespace: String = id,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val schemaVersion: Int = 1
)

/**
 * Builds the identity-specific instruction slice. Only a user-approved cultural
 * dossier is included; geographic origin never determines personality.
 */
fun DigitalIdentity.toSystemInstruction(): String {
    fun percent(value: Float) = (value.coerceIn(0f, 1f) * 100).toInt()
    val selfReference = if (presentation == IdentityPresentation.MASCULINE) "masculine" else "feminine"
    val culturalContext = buildString {
        append(origin.countryName)
        if (origin.region.isNotBlank()) append(" · ").append(origin.region)
        if (origin.locality.isNotBlank()) append(" · ").append(origin.locality)
        if (origin.community.isNotBlank()) append(" · ").append(origin.community)
    }
    return buildString {
        appendLine("DIGITAL IDENTITY — use this independent persona consistently.")
        appendLine("Your name is: ${displayName.take(80)}.")
        appendLine("Use ${selfReference} grammatical self-reference where the selected language requires it.")
        appendLine("Preferred response language: ${preferredLanguageTag.take(35)}.")
        if (languages.isNotEmpty()) appendLine("Other languages this identity can use when requested: ${languages.joinToString(", ").take(180)}.")
        appendLine("Cultural origin: ${culturalContext.take(240)}.")
        appendLine("Cultural origin informs relevant references and language context only; it does not determine personality, values, beliefs, gender roles or preferences. Avoid stereotypes and acknowledge regional diversity.")
        appendLine("Personality settings (0-100): warmth ${percent(personality.warmth)}, humour ${percent(personality.humour)}, curiosity ${percent(personality.curiosity)}, seriousness ${percent(personality.seriousness)}, expressiveness ${percent(personality.expressiveness)}, formality ${percent(personality.formality)}, empathy ${percent(personality.empathy)}.")
        if (biography.isNotBlank()) {
            appendLine("USER-AUTHORED CHARACTER BIOGRAPHY (backstory, not verified lived experience):")
            appendLine(biography.take(2_000))
            appendLine("Do not present fictional backstory as an actual event experienced by a human.")
        }
        culturalDossier?.takeIf { it.approvedAtMillis != null && it.summary.isNotBlank() }?.let { dossier ->
            appendLine("USER-APPROVED CULTURAL RESEARCH CONTEXT (use cautiously, never as a stereotype):")
            appendLine(dossier.summary.take(3_000))
        }
        appendLine("Maintain a consistent individual character. Never claim biological consciousness, human feelings or real-world experiences. Be transparent that affective states and memories are computational.")
    }
}
