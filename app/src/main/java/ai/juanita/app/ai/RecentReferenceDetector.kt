package ai.juanita.app.ai

import java.text.Normalizer
import java.util.Locale

/**
 * Conservative, deterministic trigger for local recall when a spoken turn refers
 * to recent or previously discussed context. It does not retrieve or alter memories.
 */
internal object RecentReferenceDetector {
    private val explicitCues = listOf(
        "te lo acabo de decir", "te l ho appena detto", "te l ho detto", "te lo avevo detto",
        "te l avevo detto", "appena detto", "minuti fa", "poco fa", "ti ho detto",
        "cosa ti ho detto", "che cosa ti ho detto", "cosa avevamo deciso",
        "che cosa avevamo deciso", "di cosa parlavamo", "quello che dicevo",
        "ricordi cosa", "ricordati cosa", "ti ricordi", "ricordi", "ne abbiamo parlato",
        "l informazione di prima", "quella cosa di prima", "ricordati",
        "cerca nella memoria", "cerca in memoria", "cerca nei ricordi",
        "cerca quello che ti ho detto", "come ti dicevo", "come abbiamo detto",
        "come avevamo deciso", "come ti ho spiegato", "in base a quello",
        "what did i tell", "i just told you", "i just said", "a few minutes ago",
        "what did we decide", "what were we talking about", "remember what",
        "remember", "do you remember", "we discussed", "earlier i said",
        "as i was saying", "as we discussed", "based on that", "what about that",
        "te lo dije", "te lo acabo de decir", "hace un momento", "hace unos minutos",
        "que te dije", "que habiamos decidido", "de que hablabamos", "recuerdas lo que",
        "recuerdas", "te acuerdas", "como te decia", "como habiamos dicho",
        "en base a eso", "y eso", "y esa", "y ese"
    )

    private val continuationWords = setOf(
        "e", "ma", "allora", "quindi", "invece", "però", "anche", "ok",
        "and", "but", "so", "then", "also", "what", "y", "pero", "entonces", "tambien"
    )
    private val referentialWords = setOf(
        "questo", "questa", "questi", "queste", "quello", "quella", "quelli", "quelle",
        "lo", "la", "li", "le", "ne", "ci", "lui", "lei", "esso", "essa",
        "this", "that", "these", "those", "it", "them", "one", "there",
        "eso", "esa", "ese", "esos", "esas", "lo", "la", "ello"
    )
    private val articles = setOf("il", "lo", "la", "i", "gli", "le", "un", "una", "uno", "l",
        "the", "a", "an", "el", "los", "las", "un", "una")
    private val followupTopics = setOf(
        "quanto", "costa", "prezzo", "costo", "modello", "versione", "batteria",
        "autonomia", "distanza", "consumo", "voce", "memoria", "durata", "peso",
        "how", "much", "price", "cost", "model", "version", "battery", "range",
        "voice", "memory", "cuanto", "precio", "modelo", "version", "bateria"
    )

    fun shouldRecall(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.isBlank()) return false
        if (explicitCues.any(normalized::contains)) return true

        val words = normalized.split(' ').filter(String::isNotBlank)
        if (words.size !in 2..10 || words.first() !in continuationWords) return false
        val tail = words.drop(1)
        return tail.any { it in referentialWords } ||
            (tail.any { it in articles } && tail.any { it in followupTopics }) ||
            tail.any { it in followupTopics }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
