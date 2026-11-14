package ai.juanita.app.memory

import java.text.Normalizer
import java.util.Locale

/**
 * Small deterministic subject router for the shared memory index.
 *
 * It is intentionally local, cheap and explainable: no network request, embedding model,
 * extra microphone or character-specific state is involved. Unknown/ambiguous subjects
 * remain searchable through the broad lexical fallback in EncryptedMemoryStore.
 */
object MemoryTopicClassifier {
    const val GENERAL = "general"

    private val keywords: Map<String, Set<String>> = linkedMapOf(
        "vehicles_transport" to setOf(
            "moto", "motocicletta", "scooter", "yamaha", "tricity", "xmax", "mp3", "kymco",
            "casco", "pneumatico", "gomme", "tagliando", "motore", "sella", "freno", "freni",
            "consumo", "benzina", "veicolo", "auto", "macchina", "motorbike", "motorcycle",
            "scooter", "bike", "tyre", "tire", "engine", "brake", "maintenance"
        ),
        "projects_technology" to setOf(
            "juanita", "jose", "josé", "app", "applicazione", "android", "apk", "github",
            "gradle", "programmazione", "codice", "software", "road", "display", "progetto",
            "memoria", "build", "release", "repository", "telefono", "microfono", "avatar",
            "technology", "project", "code", "software", "computer", "batteria", "battery",
            "cellulare", "samsung", "ram", "cpu", "wakelock", "wifi", "gps", "processore"
        ),
        "home_property" to setOf(
            "casa", "abitazione", "appartamento", "terrazza", "jacuzzi", "pergola", "proprieta",
            "proprietà", "affitto", "inquilino", "ristrutturazione", "impianto", "tetto",
            "house", "home", "property", "rent", "apartment"
        ),
        "health_wellbeing" to setOf(
            "salute", "benessere", "allenamento", "palestra", "cardio", "airbike", "pesi",
            "sonno", "stress", "alimentazione", "nutrizione", "fitness",
            "health", "wellbeing", "workout", "exercise", "sleep"
        ),
        "work_finances" to setOf(
            "lavoro", "azienda", "impresa", "business", "stipendio", "fattura", "tasse",
            "investimento", "finanza", "soldi", "budget", "prezzo", "costo", "work", "finance"
        ),
        "relationships_family" to setOf(
            "relazione", "donna", "ragazza", "fidanzata", "moglie", "figlia", "figlie",
            "famiglia", "appuntamento", "messaggio", "sedurre", "amore", "partner",
            "relationship", "dating", "girlfriend", "wife", "family", "daughter"
        ),
        "travel_places" to setOf(
            "viaggio", "vacanza", "tenerife", "canarie", "canaria", "palermo", "praga",
            "brno", "sevilla", "spagna", "italia", "thailandia", "brasile", "egitto",
            "aeroporto", "hotel", "spiaggia", "travel", "trip", "holiday", "vacation"
        ),
        "interests_hobbies" to setOf(
            "fotografia", "canon", "immersione", "subacquea", "tartarughe", "cucina",
            "ricetta", "film", "cinema", "musica", "profumo", "fotocamera", "hobby",
            "photography", "diving", "cooking", "movie", "music"
        ),
        "preferences" to setOf(
            "preferisco", "preferenza", "piace", "nonpiace", "stile", "lingua", "italiano",
            "spagnolo", "ceco", "inglese", "portoghese", "voce", "tono", "prefer", "language"
        ),
        "personal_profile" to setOf(
            "riccardo", "ric", "compleanno", "nascita", "palermo", "prague", "praga",
            "nazionalita", "nazionalità", "lingue", "residenza", "profilo", "anni", "birthday"
        )
    )

    fun classify(text: String): String {
        val normalized = normalize(text)
        if (normalized.isBlank()) return GENERAL
        val tokens = normalized.split(' ').filter { it.isNotBlank() }.toSet()
        var bestTopic = GENERAL
        var bestScore = 0
        for ((topic, words) in keywords) {
            val score: Int = words.fold(0) { total, keyword ->
                val points = if (keyword.contains(' ')) {
                    if (normalized.contains(keyword)) 2 else 0
                } else if (keyword in tokens) {
                    2
                } else if (keyword.length >= 5 && tokens.any { it.length >= 5 && (it.startsWith(keyword) || keyword.startsWith(it)) }) {
                    1
                } else 0
                total + points
            }
            if (score > bestScore) {
                bestScore = score
                bestTopic = topic
            }
        }
        return bestTopic
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
