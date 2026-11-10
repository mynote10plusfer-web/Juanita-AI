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
