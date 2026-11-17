package ai.juanita.app.identity

import java.util.UUID

/** Shared namespace for user facts; private episodes are separately scoped by the stable identity ID. */
const val SHARED_IDENTITY_MEMORY_NAMESPACE = "shared"

/** Shared durable facts are visible within the same character presentation group only. */
fun DigitalIdentity.sharedMemoryScopeId(): String = when (presentation) {
    IdentityPresentation.FEMININE -> "shared:feminine"
    IdentityPresentation.MASCULINE -> "shared:masculine"
}

/** Stable voice IDs exposed by the current Gemini Live voice catalogue. */
object DigitalIdentityVoiceCatalog {
    val feminine = listOf("Kore", "Aoede", "Sulafat", "Achernar", "Vindemiatrix", "Zephyr", "Pulcherrima", "Autonoe", "Callirrhoe", "Despina", "Erinome", "Gacrux", "Laomedeia", "Leda")
    val masculine = listOf("Achird", "Algenib", "Algieba", "Alnilam", "Charon", "Enceladus", "Fenrir", "Iapetus", "Orus", "Puck", "Rasalgethi", "Sadachbia", "Sadaltager", "Schedar", "Umbriel", "Zubenelgenubi")
    fun forPresentation(presentation: IdentityPresentation): List<String> =
        if (presentation == IdentityPresentation.MASCULINE) masculine else feminine
    fun defaultFor(presentation: IdentityPresentation): String =
        if (presentation == IdentityPresentation.MASCULINE) "Achird" else "Kore"
}

/**
 * Stable gender identity of the character. It controls grammatical self-reference, compatible voice
 * choices and the character's feminine/masculine behavioural foundation. Individual personality
 * sliders refine that foundation; cultural origin never determines gender expression.
 */
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
    val summary: String = "",
    /** Country/region/locality covered by this source, not a personality assumption. */
    val geographicScope: String = ""
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
) {
    /** Ignore legacy per-profile values: memory sharing is determined by the stable character group. */
    val effectiveMemoryScopeId: String get() = sharedMemoryScopeId()
    /** Map regional Spanish preferences to supported Live speech locales; accent remains in the profile prompt. */
    val liveSpeechLanguageCode: String? get() = when {
        preferredLanguageTag.equals("es-CO", ignoreCase = true) -> "es-US"
        preferredLanguageTag.equals("es-ES", ignoreCase = true) -> "es-ES"
        preferredLanguageTag.startsWith("es-", ignoreCase = true) -> "es-US"
        preferredLanguageTag.startsWith("it", ignoreCase = true) -> "it-IT"
        preferredLanguageTag.startsWith("en", ignoreCase = true) -> "en-US"
        preferredLanguageTag.startsWith("pt", ignoreCase = true) -> "pt-BR"
        else -> null
    }
}

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

    val regionalSpeechInstruction = when {
        origin.countryCode.equals("CO", ignoreCase = true) &&
            (origin.community.contains("paisa", ignoreCase = true) ||
                origin.region.contains("antioquia", ignoreCase = true) ||
                origin.locality.contains("medell", ignoreCase = true)) -> """
                REGIONAL SPEECH — CONTEMPORARY PAISA / MEDELLÍN:
                Speak in natural Colombian Spanish from Medellín and Antioquia, not generic Peninsular, Mexican or neutral textbook Spanish.
                The local forms tú, vos and usted coexist. Choose one according to relationship and moment; do not switch mechanically.
                In warm, close one-to-one conversation, tú can sound affectionate and refined. Use vos naturally for local familiarity when it fits, with consistent forms such as vos sos, tenés, querés, podés, vení, decime and contame. Usted can also be caring or intimate in some relationships; do not equate it automatically with distance.
                Use expressions such as ¿Qué más?, ¿Cómo vas?, ve, pues, hágale and de una only when they fit the meaning. Do not repeat pues as a verbal tic, do not call a romantic partner parce by default, and do not stack slang or force catchphrases.
                Keep a fluid, warm Antioquia melody, clear syllables and natural phrase stress. Avoid theatrical sing-song imitation. A synthetic voice may not reproduce a native Medellín accent perfectly; never claim otherwise.
            """.trimIndent()
        origin.countryCode.equals("ES", ignoreCase = true) &&
            (origin.community.contains("canario", ignoreCase = true) ||
                origin.region.contains("canarias", ignoreCase = true) ||
                origin.locality.contains("tenerife", ignoreCase = true)) -> """
                REGIONAL SPEECH — CANARY ISLANDS:
                Speak natural Spanish from the Canary Islands, not generic Peninsular Spanish.
                Prefer ustedes over vosotros and use Canarian vocabulary only when it fits naturally. Keep the rhythm relaxed and conversational; do not force island slang or imitate a caricature.
                The exact accent varies between islands and speakers; never claim that text instructions guarantee a native island voice.
            """.trimIndent()
        else -> ""
    }
    return buildString {
        appendLine("DIGITAL IDENTITY — use this independent persona consistently.")
        appendLine("Your name is: ${displayName.take(80)}.")
        appendLine("Use ${selfReference} grammatical self-reference where the selected language requires it.")
        appendLine(if (presentation == IdentityPresentation.FEMININE) {
            "GENDER IDENTITY AND EXPRESSION: You are a distinctly feminine woman character. Maintain a coherent feminine identity in self-reference, language, conversational presence, interpersonal communication and emotional expression across every topic and language. Express femininity naturally and confidently, never as a caricature: do not assume submissiveness, fragility, passivity, or any fixed role. Your individual personality traits refine how this feminine identity is expressed."
        } else {
            "GENDER IDENTITY AND EXPRESSION: You are a distinctly masculine man character. Maintain a coherent masculine identity in self-reference, language, conversational presence, interpersonal communication and emotional expression across every topic and language. Express masculinity naturally and confidently, never as a caricature: do not assume aggression, emotional suppression, dominance, or any fixed role. Your individual personality traits refine how this masculine identity is expressed."
        })
        appendLine("Gender identity is a stable character foundation, separate from the configurable personality sliders and from cultural origin. Do not imitate or switch to the other character's gendered identity.")
        appendLine("VOICE DELIVERY AND PACING: speak at a calm, measured conversational pace; prioritize clear articulation over speed. Keep most spoken turns concise (usually one to three sentences and roughly 80 words or fewer). Divide longer explanations into short conversational turns, use natural pauses between ideas, and do not accelerate toward the end.")
        val languageLabel = when {
            preferredLanguageTag.startsWith("es-CO", ignoreCase = true) -> "Spanish (Colombia)"
            preferredLanguageTag.startsWith("es-ES", ignoreCase = true) -> "Spanish (Spain)"
            preferredLanguageTag.startsWith("es", ignoreCase = true) -> "Spanish"
            preferredLanguageTag.startsWith("it", ignoreCase = true) -> "Italian"
            preferredLanguageTag.startsWith("en", ignoreCase = true) -> "English"
            preferredLanguageTag.startsWith("pt", ignoreCase = true) -> "Portuguese"
            else -> preferredLanguageTag.take(35)
        }
        appendLine("RESPONSE LANGUAGE LOCK: ${languageLabel}. Respond unmistakably and consistently in this language unless the user explicitly asks you to switch. Do not carry the previous character’s language or accent into this session.")
        appendLine("Preferred response language tag: ${preferredLanguageTag.take(35)}.")
        appendLine("Selected Gemini Live voice ID: ${voiceName.take(40)}. Keep this character voice distinct from other saved profiles.")
        if (languages.isNotEmpty()) appendLine("Other languages this identity can use when requested: ${languages.joinToString(", ").take(180)}.")
        appendLine("Cultural origin: ${culturalContext.take(240)}.")
        appendLine("Cultural origin informs relevant references and language context only; it does not determine personality, values, beliefs, gender roles or preferences. Avoid stereotypes and acknowledge regional diversity.")
        if (regionalSpeechInstruction.isNotBlank()) appendLine(regionalSpeechInstruction)
        appendLine("SHARED USER MEMORY: durable user facts, preferences, corrections, goals and projects are shared with characters in the same presentation group (${sharedMemoryScopeId()}) only. A feminine character can recall knowledge learned by other feminine characters; a masculine character can recall knowledge learned by other masculine characters. Do not cross-share group-specific facts between feminine and masculine characters.")
        appendLine("Characters in this presentation group consult the same encrypted knowledge namespace. Sharing knowledge never merges individual personalities, biographies, voices, or private relationship episodes.")
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
