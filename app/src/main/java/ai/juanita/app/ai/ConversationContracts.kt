package ai.juanita.app.ai

/**
 * Provider-neutral boundary for the conversational engine.
 *
 * Implementations may be local or remote. The Android UI must never depend
 * directly on a vendor SDK or contain API credentials.
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
    val recentHistory: List<ConversationTurn> = emptyList()
)

data class ConversationResponse(
    val text: String,
    val languageTag: String,
    val providerName: String
)

interface ConversationProvider {
    suspend fun respond(request: ConversationRequest): Result<ConversationResponse>
}
