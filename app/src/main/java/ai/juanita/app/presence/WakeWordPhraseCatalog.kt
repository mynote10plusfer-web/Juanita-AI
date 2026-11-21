package ai.juanita.app.presence

import java.security.MessageDigest

/**
 * Canonical, CI-validated wake phrases supported by the pinned Spanish BPE model.
 * The keyword pieces are generated with SentencePiece in CI and checked byte-for-byte.
 */
object WakeWordPhraseCatalog {
    data class Definition(
        val phrase: String,
        val normalizedPhrase: String,
        val keywordLine: String,
        val keywordSha256: String
    )

    const val DEFAULT_PHRASE = "Hola Juanita"

    private val definitions = listOf(
        Definition(
            phrase = "Hola Juanita",
            normalizedPhrase = "hola juanita",
            keywordLine = "▁ho la ▁juan i ta :1.0 #0.20 @hola_juanita\n",
            keywordSha256 = "c659a6266b71b069bad7e04ec3b7d12c895c54b83a98317da581c39448a54e9d"
        ),
        Definition(
            phrase = "Hola José",
            normalizedPhrase = "hola josé",
            keywordLine = "▁ho la ▁ jo s é :1.0 #0.20 @hola_jose\n",
            keywordSha256 = "0e98af9cae972913f790ade9066049251c764e7303359c77c119966d6f48bb64"
        )
    )

    fun definition(phrase: String): Definition? =
        definitions.firstOrNull { it.phrase.equals(phrase.trim(), ignoreCase = true) }

    fun isSupported(phrase: String): Boolean = definition(phrase) != null

    fun keywordHashIsValid(definition: Definition): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        val actual = digest.digest(definition.keywordLine.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return actual == definition.keywordSha256
    }
}
