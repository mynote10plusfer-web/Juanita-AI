package ai.juanita.app.ai

/**
 * Provider-neutral boundary for the conversational engine.
 * The selected persona and regional style travel with each request.
 */
data class ConversationTurn(
    val role: Role,
    val text: String,
    val timestampEpochMillis: Long
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

data class ConversationRequest(
    val userText: String,
    val languageTag: String,
    val recentHistory: List<ConversationTurn> = emptyList(),
    val persona: PersonaProfile = PersonaProfile(),
    val dialectName: String = "Standard"
) {
    fun systemInstruction(languageName: String): String =
        persona.toSystemInstruction(languageName, dialectName)
}

data class ConversationResponse(
    val text: String,
    val languageTag: String,
    val providerName: String
)

interface ConversationProvider {
    suspend fun respond(request: ConversationRequest): Result<ConversationResponse>
}
