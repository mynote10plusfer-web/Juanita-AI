package ai.juanita.app.profile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A field in the user's durable personal profile.
 *
 * Only user-confirmed values are written by the initial onboarding flow.
 * Inferred values must be confirmed before they are persisted.
 */
data class PersonalProfileField(
    val value: String,
    val source: String = SOURCE_USER_CONFIRMED,
    val capturedAtMillis: Long = System.currentTimeMillis(),
    val confidence: Float = 1f,
    val confirmedByUser: Boolean = true
) {
    companion object {
        const val SOURCE_USER_CONFIRMED = "USER_CONFIRMED"
        const val SOURCE_CONFIRMED_INFERENCE = "CONFIRMED_INFERENCE"
    }
}

/**
 * Permanent, editable user profile. This is deliberately separate from
 * episodic transcripts, curated conversational memories and app preferences.
 *
 * homeLocality and currentLocality are separate: a foreground GPS observation
 * must never silently become the user's home address or residence.
 */
data class PersonalProfile(
    val fields: Map<String, PersonalProfileField> = emptyMap(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun value(key: String): String? = fields[key]?.value

    companion object {
        const val PREFERRED_NAME = "preferred_name"
        const val HOME_LOCALITY = "home_locality"
        const val CURRENT_LOCALITY = "current_locality"
        const val LANGUAGES = "languages"
        const val AGE = "age"
        const val BIRTH_DATE = "birth_date"
        const val INTERESTS = "interests"
        const val LIFESTYLE = "lifestyle"
        const val GOALS = "goals"
        const val ASSISTANCE_PREFERENCES = "assistance_preferences"

        val allowedKeys = setOf(
            PREFERRED_NAME, HOME_LOCALITY, CURRENT_LOCALITY, LANGUAGES,
            AGE, BIRTH_DATE, INTERESTS, LIFESTYLE, GOALS, ASSISTANCE_PREFERENCES
        )
    }
}

/**
 * Dedicated AES-256-GCM encrypted store for the permanent profile.
 * It uses its own Keystore alias and file, so clearing conversational
 * memories or the transcript archive cannot erase the personal profile.
 */
class PersonalProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val profileFile = File(appContext.filesDir, FILE_NAME)

    @Synchronized
    fun load(): PersonalProfile {
        if (!profileFile.exists()) return PersonalProfile()
        return runCatching {
            val packed = Base64.decode(profileFile.readText(Charsets.UTF_8), Base64.NO_WRAP)
            require(packed.size > IV_LENGTH)
            val iv = packed.copyOfRange(0, IV_LENGTH)
            val ciphertext = packed.copyOfRange(IV_LENGTH, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            val json = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
            profileFromJson(json)
        }.getOrElse {
            // Fail closed: never replace a file that could not be decrypted.
            throw IllegalStateException("Il profilo cifrato non è leggibile; il file originale è stato preservato.", it)
        }
    }

    @Synchronized
    fun save(profile: PersonalProfile) {
        val safeFields = profile.fields
            .filterKeys { it in PersonalProfile.allowedKeys }
            .filterValues { it.value.isNotBlank() }
        val normalized = profile.copy(
            fields = safeFields,
            updatedAtMillis = System.currentTimeMillis()
        )
        val json = profileToJson(normalized).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(json)
        val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val temporary = File(appContext.filesDir, "$FILE_NAME.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(encoded.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                profileFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (atomicFailure: Exception) {
            // Some filesystems do not support ATOMIC_MOVE. Retry a replace move;
            // never delete the previous profile before the replacement is ready.
            try {
                Files.move(temporary.toPath(), profileFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (replaceFailure: Exception) {
                temporary.delete()
                throw IllegalStateException("Impossibile completare il salvataggio del profilo.", replaceFailure)
            }
        }
    }

    @Synchronized
    fun updateField(key: String, value: String, source: String = PersonalProfileField.SOURCE_USER_CONFIRMED,
                    confirmedByUser: Boolean = true, confidence: Float = 1f) {
        require(key in PersonalProfile.allowedKeys) { "Campo profilo non riconosciuto." }
        require(confirmedByUser) { "I dati inferiti devono essere confermati prima del salvataggio." }
        val current = load()
        val updated = current.fields.toMutableMap()
        if (value.isBlank()) {
            updated.remove(key)
        } else {
            updated[key] = PersonalProfileField(
                value = value.trim(),
                source = source,
                capturedAtMillis = System.currentTimeMillis(),
                confidence = confidence.coerceIn(0f, 1f),
                confirmedByUser = true
            )
        }
        save(current.copy(fields = updated))
    }

    @Synchronized
    fun clear() {
        if (profileFile.exists() && !profileFile.delete()) {
            throw IllegalStateException("Impossibile eliminare il profilo.")
        }
    }

    private fun profileToJson(profile: PersonalProfile): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("updatedAtMillis", profile.updatedAtMillis)
        val values = JSONObject()
        profile.fields.forEach { (key, field) ->
            values.put(key, JSONObject().apply {
                put("value", field.value)
                put("source", field.source)
                put("capturedAtMillis", field.capturedAtMillis)
                put("confidence", field.confidence.toDouble())
                put("confirmedByUser", field.confirmedByUser)
            })
        }
        put("fields", values)
    }

    private fun profileFromJson(json: JSONObject): PersonalProfile {
        val schemaVersion = json.optInt("schemaVersion", 0)
        require(schemaVersion == SCHEMA_VERSION) { "Versione del profilo non supportata: $schemaVersion" }
        val values = json.optJSONObject("fields") ?: JSONObject()
        val fields = LinkedHashMap<String, PersonalProfileField>()
        val iterator = values.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key !in PersonalProfile.allowedKeys) continue
            val item = values.optJSONObject(key) ?: continue
            val value = item.optString("value").trim()
            if (value.isBlank()) continue
            fields[key] = PersonalProfileField(
                value = value,
                source = item.optString("source", PersonalProfileField.SOURCE_USER_CONFIRMED),
                capturedAtMillis = item.optLong("capturedAtMillis", 0L),
                confidence = item.optDouble("confidence", 1.0).toFloat().coerceIn(0f, 1f),
                confirmedByUser = item.optBoolean("confirmedByUser", false)
            )
        }
        return PersonalProfile(fields, json.optLong("updatedAtMillis", 0L))
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val FILE_NAME = "juanita_personal_profile.enc"
        private const val KEY_ALIAS = "juanita_personal_profile_aes_v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
        private const val SCHEMA_VERSION = 1
    }
}
