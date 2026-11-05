package ai.juanita.app.memory

/**
 * Deterministic ranking policy for memory candidates.
 *
 * This class does not infer memories from conversations. It scores candidates
 * supplied by a future extraction provider and blocks sensitive auto-promotion.
 */
data class MemoryCandidate(
    val text: String,
    val category: String,
    val source: String,
    val confidence: Float,
    val recurrence: Int,
    val usefulness: Float,
    val futureRelevance: Float,
    val lastSeenAtMillis: Long,
    val sensitive: Boolean,
    val explicitlyRequested: Boolean = false
)

data class RankedMemoryCandidate(
    val candidate: MemoryCandidate,
    val score: Float,
    val requiresUserConfirmation: Boolean
)

class MemoryCurator {
    fun rank(
        candidates: List<MemoryCandidate>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<RankedMemoryCandidate> = candidates
        .filter { it.text.isNotBlank() }
        .map { candidate ->
            val ageDays = ((nowMillis - candidate.lastSeenAtMillis).coerceAtLeast(0L) / MILLIS_PER_DAY).toFloat()
            val recency = (1f - ageDays / RECENCY_WINDOW_DAYS).coerceIn(0f, 1f)
            val recurrence = (candidate.recurrence.coerceAtLeast(0) / 5f).coerceIn(0f, 1f)
            val confidence = candidate.confidence.coerceIn(0f, 1f)
            val usefulness = candidate.usefulness.coerceIn(0f, 1f)
            val relevance = candidate.futureRelevance.coerceIn(0f, 1f)
            val score = (
                confidence * 0.25f +
                recurrence * 0.20f +
                recency * 0.10f +
                usefulness * 0.25f +
                relevance * 0.20f
            ).coerceIn(0f, 1f)
            RankedMemoryCandidate(
                candidate = candidate,
                score = score,
                requiresUserConfirmation = candidate.sensitive || !candidate.explicitlyRequested || confidence < MIN_AUTO_CONFIDENCE
            )
        }
        .sortedByDescending { it.score }

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val RECENCY_WINDOW_DAYS = 365f
        private const val MIN_AUTO_CONFIDENCE = 0.90f
    }
}
