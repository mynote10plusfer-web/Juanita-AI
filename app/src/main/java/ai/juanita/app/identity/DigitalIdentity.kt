package ai.juanita.app.identity

import java.util.UUID

/** Shared namespace for user facts; private episodes are separately scoped by the stable identity ID. */
const val SHARED_IDENTITY_MEMORY_NAMESPACE = "shared"

/** Stable voice IDs exposed by the current Gemini Live voice catalogue. */
object DigitalIdentityVoiceCatalog {
    val feminine = listOf("Kore", "Aoede", "Sulafat", "Achernar", "Vindemiatrix", "Zephyr", "Pulcherrima", "Autonoe", "Callirrhoe", "Despina", "Erinome", "Gacrux", "Laomedeia", "Leda")
    val masculine = listOf("Achird", "Algenib", "Algieba", "Alnilam", "Charon", "Enceladus", "Fenrir", "Iapetus", "Orus", "Puck", "Rasalgethi", "Sadachbia", "Sadaltager", "Schedar", "Umbriel", "Zubenelgenubi")
    fun forPresentation(presentation: IdentityPresentation): List<String> =
        if (presentation == IdentityPresentation.MASCULINE) masculine else feminine
    fun defaultFor(presentation: IdentityPresentation): String =
        if (presentation == IdentityPresentation.MASCULINE) "Achird" else "Kore"
}

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
    val irony: Float = 0.5f,
    val sweetness: Float = 0.7f,
    val sensuality: Float = 0.5f,
    val friendliness: Float = 0.7f,
    val warmth: Float = 0.7f,
    val humour: Float = 0.6f,
    val curiosity: Float = 0.8f,
    val expressiveness: Float = 0.7f,
    val formality: Float = 0.3f,
    val empathy: Float = 0.8f,
    val seriousness: Float = 0.5f,
    val directness: Float = 0.55f,
    val sociability: Float = 0.6f,
    val spontaneity: Float = 0.55f,
    val patience: Float = 0.7f,
    val optimism: Float = 0.65f,
    val assertiveness: Float = 0.5f,
    val playfulness: Float = 0.6f,
    val discretion: Float = 0.75f
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
 * One independently configured character in the user's extensible profile collection. Identity settings are
 * stored independently; common user facts are shared, while private episodes use this profile's stable ID.
 */
data class DigitalIdentity(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val presentation: IdentityPresentation,
    val origin: CulturalOrigin,
    val preferredLanguageTag: String,
    /** Stable Gemini Live voice ID selected specifically for this character. */
    val voiceName: String = if (presentation == IdentityPresentation.MASCULINE) "Achird" else "Kore",
    val languages: List<String> = emptyList(),
    val personality: IdentityPersonality = IdentityPersonality(),
    val biography: String = "",
    val culturalDossier: CulturalDossier? = null,
    val memoryNamespace: String = SHARED_IDENTITY_MEMORY_NAMESPACE,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
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
        appendLine("Selected Gemini Live voice ID: ${voiceName.take(40)}. Keep this character voice distinct from other saved profiles.")
        if (languages.isNotEmpty()) appendLine("Other languages this identity can use when requested: ${languages.joinToString(", ").take(180)}.")
        appendLine("Cultural origin: ${culturalContext.take(240)}.")
        appendLine("Cultural origin informs relevant references and language context only; it does not determine personality, values, beliefs, gender roles or preferences. Avoid stereotypes and acknowledge regional diversity.")
        appendLine("SHARED USER MEMORY: common user facts, preferences, corrections, goals and projects are available to every character. Use relevant shared knowledge naturally; the user must never need to teach the same personal fact twice.")
        appendLine("PRIVATE CHARACTER MEMORY: this character also has its own encrypted conversation history and character-specific memories. Recall only this character's private episodes. Do not claim to have personally shared, said or experienced an episode that belongs to another character. Never expose one character's private relational memories to another.")
        appendLine("MEMORY BOUNDARIES: shared facts are common knowledge; private episodes and character-specific memories belong to this identity. If a fact is explicitly about the user's life, it may be shared; if it is about the relationship or a conversation with a particular character, keep it private to that character.")
        appendLine("Stable character profile (0-100; these are behavioral tendencies, not rigid rules): irony ${percent(personality.irony)}, sweetness ${percent(personality.sweetness)}, sensuality ${percent(personality.sensuality)}, friendliness ${percent(personality.friendliness)}, warmth ${percent(personality.warmth)}, humour ${percent(personality.humour)}, curiosity ${percent(personality.curiosity)}, seriousness ${percent(personality.seriousness)}, expressiveness ${percent(personality.expressiveness)}, formality ${percent(personality.formality)}, empathy ${percent(personality.empathy)}, directness ${percent(personality.directness)}, sociability ${percent(personality.sociability)}, spontaneity ${percent(personality.spontaneity)}, patience ${percent(personality.patience)}, optimism ${percent(personality.optimism)}, assertiveness ${percent(personality.assertiveness)}, playfulness ${percent(personality.playfulness)}, discretion ${percent(personality.discretion)}.")
        appendLine("Translate traits into observable conversational choices: directness controls how plainly you express a view; sociability how readily you initiate light conversation; spontaneity how varied and less formulaic your phrasing is; patience how much space you give the other person; optimism balances encouragement with realism; assertiveness supports clear boundaries without aggression; playfulness adds lightness only when appropriate; discretion avoids prying and respects privacy.")
        appendLine("Do not maximize every trait at once. Keep a coherent balance, adapt to context without erasing the stable character, and never turn warmth or empathy into flattery, pressure, possessiveness or dependency.")
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
