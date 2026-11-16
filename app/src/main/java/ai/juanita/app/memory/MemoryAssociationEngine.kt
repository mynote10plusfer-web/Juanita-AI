package ai.juanita.app.memory

/**
 * Builds a small, query-scoped association graph over already persisted memories.
 *
 * Direct matches are the graph's anchors. A second memory may be recalled through
 * an anchor only when it shares at least two meaningful concepts, or one distinctive
 * long concept. This is deliberately conservative: a shared broad topic alone must
 * never manufacture a personal connection.
 *
 * The graph is derived in memory for one retrieval request. It never mutates, merges,
 * confirms, or deletes source memories, so the encrypted records remain authoritative.
 */
internal object MemoryAssociationEngine {

    data class RankedMemory(
        val memory: MemoryItem,
        val score: Int,
        val direct: Boolean,
        val bridgeTerms: List<String>
    )

    fun rank(
        query: String,
        memories: List<MemoryItem>,
        routedTopic: String,
        limit: Int = 10
    ): List<RankedMemory> {
        if (query.isBlank() || memories.isEmpty() || limit <= 0) return emptyList()

        val queryTerms = meaningfulTerms(query)
        if (queryTerms.isEmpty()) return emptyList()

        val candidates = memories.map { memory ->
            val terms = meaningfulTerms(memory.text)
            val overlap = queryTerms.intersect(terms)
            val topicMatch = memory.topic == routedTopic
            val direct = overlap.isNotEmpty()
            val directScore = overlap.size * 5 +
                (if (topicMatch) 2 else 0) +
                (if (memory.confirmedByUser) 2 else 0) +
                (memory.confidence.coerceIn(0f, 1f) * 2).toInt()
            Candidate(memory, terms, direct, directScore)
        }

        // Only evidence-bearing direct matches can act as association anchors.
        val anchors = candidates.filter { it.direct }
        val ranked = candidates.mapNotNull { candidate ->
            if (candidate.direct) {
                RankedMemory(candidate.memory, candidate.directScore, true, emptyList())
            } else {
                val bestBridge = anchors
                    .map { anchor ->
                        val shared = candidate.terms.intersect(anchor.terms)
                        val distinctive = shared.filter { it.length >= DISTINCTIVE_TERM_LENGTH }
                        val qualifies = shared.size >= MIN_SHARED_TERMS || distinctive.isNotEmpty()
                        val bridgeScore = if (qualifies) {
                            shared.size * ASSOCIATION_WEIGHT +
                                (if (distinctive.isNotEmpty()) DISTINCTIVE_TERM_BONUS else 0)
                        } else 0
                        Triple(bridgeScore, shared.toList().sorted(), anchor)
                    }
                    .filter { it.first > 0 }
                    .maxByOrNull { it.first }

                bestBridge?.let { (bridgeScore, bridgeTerms, _) ->
                    val confirmationBoost = if (candidate.memory.confirmedByUser) 2 else 0
                    RankedMemory(
                        candidate.memory,
                        bridgeScore + confirmationBoost +
                            (candidate.memory.confidence.coerceIn(0f, 1f) * 2).toInt(),
                        false,
                        bridgeTerms
                    )
                }
            }
        }

        return ranked.sortedWith(
            compareByDescending<RankedMemory> { it.direct }
                .thenByDescending { it.score }
                .thenByDescending { it.memory.confidence }
                .thenByDescending { it.memory.createdAtMillis }
        ).take(limit)
    }

    private data class Candidate(
        val memory: MemoryItem,
        val terms: Set<String>,
        val direct: Boolean,
        val directScore: Int
    )

    private fun meaningfulTerms(value: String): Set<String> =
        normalize(value).split(' ')
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .toSet()

    private fun normalize(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private const val MIN_SHARED_TERMS = 2
    private const val DISTINCTIVE_TERM_LENGTH = 8
    private const val ASSOCIATION_WEIGHT = 2
    private const val DISTINCTIVE_TERM_BONUS = 2

    private val STOP_WORDS = setOf(
        "che", "chi", "con", "come", "cosa", "dal", "dalla", "dalle", "dello", "della",
        "delle", "degli", "dei", "del", "di", "una", "uno", "gli", "per", "tra", "fra",
        "non", "nel", "nella", "nelle", "nello", "nei", "sul", "sulla", "sulle", "sono",
        "era", "essere", "anche", "questa", "questo", "quello", "quella", "the", "and",
        "for", "with", "from", "that", "this", "what", "when", "where", "have", "has",
        "are", "was", "were", "you", "your", "about", "into", "como", "para", "por",
        "una", "uno", "los", "las", "del", "que", "con", "está", "esta", "esto"
    )
}
