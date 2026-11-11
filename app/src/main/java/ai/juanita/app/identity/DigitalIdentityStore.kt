package ai.juanita.app.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted storage for a user-managed collection of Juanita and José profiles.
 * The collection has no product-imposed profile-count limit. Only one non-archived
 * profile can be selected for a session; every profile uses the same shared memory.
 */
class DigitalIdentityStore(context: Context) {
    private val appContext = context.applicationContext
    private val identitiesDirectory = File(appContext.filesDir, DIRECTORY_NAME)

    @Synchronized
    fun create(identity: DigitalIdentity): DigitalIdentity {
        val normalized = normalize(identity)
        val file = fileFor(normalized.id)
        require(!file.exists()) { "Esiste già un'identità con questo ID." }
        writeEncrypted(file, identityToJson(normalized))
        return normalized
    }

    @Synchronized
    fun update(identity: DigitalIdentity): DigitalIdentity {
        val normalized = normalize(identity.copy(updatedAtMillis = System.currentTimeMillis()))
        val file = fileFor(normalized.id)
        require(file.exists()) { "L'identità da modificare non esiste." }
        writeEncrypted(file, identityToJson(normalized))
        return normalized
    }

    @Synchronized
    fun get(identityId: String): DigitalIdentity? {
        val file = fileFor(identityId)
        if (!file.exists()) return null
        return identityFromJson(readEncrypted(file)).also {
            check(it.id == identityId) { "L'ID interno non corrisponde al file dell'identità." }
        }
    }

    @Synchronized
    fun list(): List<DigitalIdentity> {
        if (!identitiesDirectory.exists()) return emptyList()
        val listedFiles = identitiesDirectory.listFiles()
            ?: throw IllegalStateException("Impossibile elencare l'archivio delle identità.")
        val files = listedFiles
            .filter { it.isFile && it.name.endsWith(FILE_SUFFIX) }
            .sortedBy { it.name }
        // Fail closed if a stored identity cannot be decrypted; never silently discard it.
        return files.map { file ->
            val identity = identityFromJson(readEncrypted(file))
            check(file.name == fileNameFor(identity.id)) { "Il file non corrisponde all'ID dell'identità." }
            identity
        }.sortedBy { it.createdAtMillis }
    }

    /** Create an independent copy with a new stable ID; shared memory is never copied. */
    @Synchronized
    fun duplicate(identityId: String, newDisplayName: String): DigitalIdentity {
        val source = get(identityId) ?: throw IllegalArgumentException("Il profilo da duplicare non esiste.")
        require(newDisplayName.isNotBlank()) { "Il nuovo nome del profilo è obbligatorio." }
        val now = System.currentTimeMillis()
        return create(source.copy(
            id = UUID.randomUUID().toString(),
            displayName = newDisplayName.trim().take(80),
            archived = false,
            createdAtMillis = now,
            updatedAtMillis = now
        ))
    }

    /** Archive/restore a profile without touching shared memory or PersonalProfileStore. */
    @Synchronized
    fun setArchived(identityId: String, archived: Boolean): DigitalIdentity {
        val current = get(identityId) ?: throw IllegalArgumentException("Il profilo selezionato non esiste.")
        return update(current.copy(archived = archived))
    }

    @Synchronized
    fun delete(identityId: String): Boolean {
        val file = fileFor(identityId)
        if (!file.exists()) return false
        if (!file.delete()) throw IllegalStateException("Impossibile eliminare l'identità selezionata.")
        return true
    }

    private fun normalize(identity: DigitalIdentity): DigitalIdentity {
        UUID.fromString(identity.id)
        require(identity.displayName.isNotBlank()) { "Il nome dell'identità è obbligatorio." }
        require(identity.origin.countryName.isNotBlank()) { "Il paese di origine è obbligatorio." }
        require(identity.preferredLanguageTag.isNotBlank()) { "La lingua preferita è obbligatoria." }
        require(DigitalIdentityVoiceCatalog.forPresentation(identity.presentation).contains(identity.voiceName)) { "La voce selezionata non è compatibile con il tipo di personaggio." }
        require(identity.memoryNamespace == SHARED_IDENTITY_MEMORY_NAMESPACE || identity.memoryNamespace == identity.id) { "Spazio di memoria non riconosciuto." }
        require(identity.schemaVersion == SCHEMA_VERSION) { "Versione del profilo non supportata." }
        val traits = identity.personality
        return identity.copy(
            displayName = identity.displayName.trim().take(80),
            memoryNamespace = SHARED_IDENTITY_MEMORY_NAMESPACE,
            preferredLanguageTag = identity.preferredLanguageTag.trim(),
            voiceName = identity.voiceName,
            languages = identity.languages.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            biography = identity.biography.trim(),
            personality = traits.copy(
                irony = traits.irony.coerceIn(0f, 1f),
                sweetness = traits.sweetness.coerceIn(0f, 1f),
                sensuality = traits.sensuality.coerceIn(0f, 1f),
                friendliness = traits.friendliness.coerceIn(0f, 1f),
                warmth = traits.warmth.coerceIn(0f, 1f),
                humour = traits.humour.coerceIn(0f, 1f),
                curiosity = traits.curiosity.coerceIn(0f, 1f),
                expressiveness = traits.expressiveness.coerceIn(0f, 1f),
                formality = traits.formality.coerceIn(0f, 1f),
                empathy = traits.empathy.coerceIn(0f, 1f),
                seriousness = traits.seriousness.coerceIn(0f, 1f),
                directness = traits.directness.coerceIn(0f, 1f),
                sociability = traits.sociability.coerceIn(0f, 1f),
                spontaneity = traits.spontaneity.coerceIn(0f, 1f),
                patience = traits.patience.coerceIn(0f, 1f),
                optimism = traits.optimism.coerceIn(0f, 1f),
                assertiveness = traits.assertiveness.coerceIn(0f, 1f),
                playfulness = traits.playfulness.coerceIn(0f, 1f),
                discretion = traits.discretion.coerceIn(0f, 1f)
            )
        )
    }

    private fun canonicalName(presentation: IdentityPresentation): String =
        if (presentation == IdentityPresentation.MASCULINE) "José" else "Juanita"

    private fun fileFor(identityId: String): File {
        UUID.fromString(identityId)
        return File(identitiesDirectory, fileNameFor(identityId))
    }

    private fun fileNameFor(identityId: String): String = "$identityId$FILE_SUFFIX"

    private fun writeEncrypted(file: File, json: JSONObject) {
        if (!identitiesDirectory.exists() && !identitiesDirectory.mkdirs()) {
            throw IllegalStateException("Impossibile creare l'archivio delle identità.")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val packed = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(packed, Base64.NO_WRAP)
        val temporary = File(identitiesDirectory, "${file.name}.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(encoded.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (replaceFailure: Exception) {
                temporary.delete()
                throw IllegalStateException("Impossibile completare il salvataggio dell'identità.", replaceFailure)
            }
        }
    }

    private fun readEncrypted(file: File): JSONObject = try {
        val packed = Base64.decode(file.readText(Charsets.UTF_8), Base64.NO_WRAP)
        require(packed.size > IV_LENGTH + TAG_LENGTH_BYTES)
        val iv = packed.copyOfRange(0, IV_LENGTH)
        val ciphertext = packed.copyOfRange(IV_LENGTH, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
    } catch (failure: Exception) {
        throw IllegalStateException("Identità cifrata non leggibile; il file originale è stato preservato.", failure)
    }

    private fun identityToJson(identity: DigitalIdentity): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("id", identity.id)
        put("displayName", identity.displayName)
        put("presentation", identity.presentation.name)
        put("origin", JSONObject().apply {
            put("countryCode", identity.origin.countryCode)
            put("countryName", identity.origin.countryName)
            put("region", identity.origin.region)
            put("locality", identity.origin.locality)
            put("community", identity.origin.community)
        })
        put("preferredLanguageTag", identity.preferredLanguageTag)
        put("voiceName", identity.voiceName)
        put("languages", JSONArray(identity.languages))
        put("personality", JSONObject().apply {
            put("irony", identity.personality.irony.toDouble())
            put("sweetness", identity.personality.sweetness.toDouble())
            put("sensuality", identity.personality.sensuality.toDouble())
            put("friendliness", identity.personality.friendliness.toDouble())
            put("warmth", identity.personality.warmth.toDouble())
            put("humour", identity.personality.humour.toDouble())
            put("curiosity", identity.personality.curiosity.toDouble())
            put("expressiveness", identity.personality.expressiveness.toDouble())
            put("formality", identity.personality.formality.toDouble())
            put("empathy", identity.personality.empathy.toDouble())
            put("seriousness", identity.personality.seriousness.toDouble())
            put("directness", identity.personality.directness.toDouble())
            put("sociability", identity.personality.sociability.toDouble())
            put("spontaneity", identity.personality.spontaneity.toDouble())
            put("patience", identity.personality.patience.toDouble())
            put("optimism", identity.personality.optimism.toDouble())
            put("assertiveness", identity.personality.assertiveness.toDouble())
            put("playfulness", identity.personality.playfulness.toDouble())
            put("discretion", identity.personality.discretion.toDouble())
        })
        put("biography", identity.biography)
        put("culturalDossier", identity.culturalDossier?.let(::dossierToJson) ?: JSONObject.NULL)
        put("memoryNamespace", identity.memoryNamespace)
        put("createdAtMillis", identity.createdAtMillis)
        put("updatedAtMillis", identity.updatedAtMillis)
        put("archived", identity.archived)
    }

    private fun dossierToJson(dossier: CulturalDossier): JSONObject = JSONObject().apply {
        put("summary", dossier.summary)
        put("version", dossier.version)
        put("approvedAtMillis", dossier.approvedAtMillis ?: JSONObject.NULL)
        put("sources", JSONArray().apply {
            dossier.sources.forEach { source -> put(JSONObject().apply {
                put("title", source.title)
                put("url", source.url)
                put("publisher", source.publisher)
                put("retrievedAtMillis", source.retrievedAtMillis)
                put("topic", source.topic)
                put("confidence", source.confidence.toDouble())
                put("summary", source.summary)
            }) }
        })
    }

    private fun identityFromJson(json: JSONObject): DigitalIdentity {
        require(json.optInt("schemaVersion", 0) == SCHEMA_VERSION) { "Versione identità non supportata." }
        val origin = json.getJSONObject("origin")
        val personality = json.optJSONObject("personality") ?: JSONObject()
        val languagesJson = json.optJSONArray("languages") ?: JSONArray()
        val languages = (0 until languagesJson.length()).mapNotNull { index ->
            languagesJson.optString(index).takeIf { it.isNotBlank() }
        }
        val dossierJson = json.optJSONObject("culturalDossier")
        return DigitalIdentity(
            id = json.getString("id"),
            displayName = json.getString("displayName"),
            presentation = IdentityPresentation.valueOf(json.getString("presentation")),
            origin = CulturalOrigin(
                countryCode = origin.optString("countryCode"),
                countryName = origin.getString("countryName"),
                region = origin.optString("region"),
                locality = origin.optString("locality"),
                community = origin.optString("community")
            ),
            preferredLanguageTag = json.getString("preferredLanguageTag"),
            voiceName = json.optString("voiceName").takeIf { it.isNotBlank() && DigitalIdentityVoiceCatalog.forPresentation(IdentityPresentation.valueOf(json.getString("presentation"))).contains(it) }
                ?: DigitalIdentityVoiceCatalog.defaultFor(IdentityPresentation.valueOf(json.getString("presentation"))),
            languages = languages,
            personality = IdentityPersonality(
                irony = personality.optDouble("irony", 0.5).toFloat(),
                sweetness = personality.optDouble("sweetness", 0.7).toFloat(),
                sensuality = personality.optDouble("sensuality", 0.5).toFloat(),
                friendliness = personality.optDouble("friendliness", 0.7).toFloat(),
                warmth = personality.optDouble("warmth", 0.7).toFloat(),
                humour = personality.optDouble("humour", 0.6).toFloat(),
                curiosity = personality.optDouble("curiosity", 0.8).toFloat(),
                expressiveness = personality.optDouble("expressiveness", 0.7).toFloat(),
                formality = personality.optDouble("formality", 0.3).toFloat(),
                empathy = personality.optDouble("empathy", 0.8).toFloat(),
                seriousness = personality.optDouble("seriousness", 0.5).toFloat(),
                directness = personality.optDouble("directness", 0.55).toFloat(),
                sociability = personality.optDouble("sociability", 0.6).toFloat(),
                spontaneity = personality.optDouble("spontaneity", 0.55).toFloat(),
                patience = personality.optDouble("patience", 0.7).toFloat(),
                optimism = personality.optDouble("optimism", 0.65).toFloat(),
                assertiveness = personality.optDouble("assertiveness", 0.5).toFloat(),
                playfulness = personality.optDouble("playfulness", 0.6).toFloat(),
                discretion = personality.optDouble("discretion", 0.75).toFloat()
            ),
            biography = json.optString("biography"),
            culturalDossier = dossierJson?.let(::dossierFromJson),
            // Older C2 profiles used an identity UUID here. Normalize them to the shared namespace.
            memoryNamespace = SHARED_IDENTITY_MEMORY_NAMESPACE,
            createdAtMillis = json.optLong("createdAtMillis", 0L),
            updatedAtMillis = json.optLong("updatedAtMillis", 0L),
            archived = json.optBoolean("archived", false),
            schemaVersion = SCHEMA_VERSION
        )
    }

    private fun dossierFromJson(json: JSONObject): CulturalDossier {
        val sourceArray = json.optJSONArray("sources") ?: JSONArray()
        val sources = (0 until sourceArray.length()).mapNotNull { index ->
            val item = sourceArray.optJSONObject(index) ?: return@mapNotNull null
            CulturalSource(
                title = item.optString("title"),
                url = item.optString("url"),
                publisher = item.optString("publisher"),
                retrievedAtMillis = item.optLong("retrievedAtMillis", 0L),
                topic = item.optString("topic", "general"),
                confidence = item.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f),
                summary = item.optString("summary")
            )
        }
        val approved = json.opt("approvedAtMillis")
        return CulturalDossier(
            summary = json.optString("summary"),
            sources = sources,
            version = json.optInt("version", 1),
            approvedAtMillis = if (approved == null || approved == JSONObject.NULL) null else (approved as Number).toLong()
        )
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val DIRECTORY_NAME = "juanita_digital_identities"
        private const val FILE_SUFFIX = ".identity.enc"
        private const val KEY_ALIAS = "juanita_digital_identity_aes_v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BYTES = 16
        private const val TAG_BITS = 128
        private const val SCHEMA_VERSION = 1
    }
}
