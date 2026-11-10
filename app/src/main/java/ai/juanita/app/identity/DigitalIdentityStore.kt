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
 * Dedicated encrypted storage for independent digital identities.
 * It is intentionally separate from the human user's PersonalProfileStore.
 * Each identity has its own file and UUID-bound memory namespace.
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
        require(identity.memoryNamespace == identity.id) { "Lo spazio di memoria deve appartenere all'ID dell'identità." }
        require(identity.schemaVersion == SCHEMA_VERSION) { "Versione del profilo non supportata." }
        val traits = identity.personality
        return identity.copy(
            displayName = identity.displayName.trim(),
            preferredLanguageTag = identity.preferredLanguageTag.trim(),
            languages = identity.languages.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            biography = identity.biography.trim(),
            personality = traits.copy(
                warmth = traits.warmth.coerceIn(0f, 1f),
                humour = traits.humour.coerceIn(0f, 1f),
                curiosity = traits.curiosity.coerceIn(0f, 1f),
                expressiveness = traits.expressiveness.coerceIn(0f, 1f),
                formality = traits.formality.coerceIn(0f, 1f),
                empathy = traits.empathy.coerceIn(0f, 1f),
                seriousness = traits.seriousness.coerceIn(0f, 1f)
            )
        )
    }

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
        put("languages", JSONArray(identity.languages))
        put("personality", JSONObject().apply {
            put("warmth", identity.personality.warmth.toDouble())
            put("humour", identity.personality.humour.toDouble())
            put("curiosity", identity.personality.curiosity.toDouble())
            put("expressiveness", identity.personality.expressiveness.toDouble())
            put("formality", identity.personality.formality.toDouble())
            put("empathy", identity.personality.empathy.toDouble())
            put("seriousness", identity.personality.seriousness.toDouble())
        })
        put("biography", identity.biography)
        put("culturalDossier", identity.culturalDossier?.let(::dossierToJson) ?: JSONObject.NULL)
        put("memoryNamespace", identity.memoryNamespace)
        put("createdAtMillis", identity.createdAtMillis)
        put("updatedAtMillis", identity.updatedAtMillis)
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
            languages = languages,
            personality = IdentityPersonality(
                warmth = personality.optDouble("warmth", 0.7).toFloat(),
                humour = personality.optDouble("humour", 0.6).toFloat(),
                curiosity = personality.optDouble("curiosity", 0.8).toFloat(),
                expressiveness = personality.optDouble("expressiveness", 0.7).toFloat(),
                formality = personality.optDouble("formality", 0.3).toFloat(),
                empathy = personality.optDouble("empathy", 0.8).toFloat(),
                seriousness = personality.optDouble("seriousness", 0.5).toFloat()
            ),
            biography = json.optString("biography"),
            culturalDossier = dossierJson?.let(::dossierFromJson),
            memoryNamespace = json.optString("memoryNamespace", json.getString("id")),
            createdAtMillis = json.optLong("createdAtMillis", 0L),
            updatedAtMillis = json.optLong("updatedAtMillis", 0L),
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
