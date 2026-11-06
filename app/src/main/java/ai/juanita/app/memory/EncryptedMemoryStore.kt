package ai.juanita.app.memory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, local-first memory. Transcript archiving is disabled by default. */
data class MemoryItem(
    val id: String,
    val text: String,
    val category: String,
    val source: String,
    val createdAtMillis: Long,
    val confidence: Float,
    val confirmedByUser: Boolean,
    val sensitive: Boolean
)

/**
 * Stores text-only records encrypted with AES-256-GCM using a non-exportable
 * Android Keystore key. Raw microphone audio is never stored.
 */
class EncryptedMemoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isConversationArchiveEnabled: Boolean
        get() = preferences.getBoolean(KEY_ARCHIVE_ENABLED, false)

    /** Disabling pauses future transcript storage but preserves existing records. */
    fun setConversationArchiveEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ARCHIVE_ENABLED, enabled).apply()
    }

    @Synchronized
    fun appendConversationTurn(sessionId: String, role: String, text: String, timestampMillis: Long = System.currentTimeMillis()) {
        if (!isConversationArchiveEnabled || text.isBlank()) return
        val safeRole = role.uppercase().takeIf { it == "USER" || it == "ASSISTANT" } ?: return
        val records = readRecords()
        records.put(JSONObject().apply {
            put("kind", "turn")
            put("id", UUID.randomUUID().toString())
            put("sessionId", sessionId)
            put("role", safeRole)
            put("text", text.take(MAX_TEXT_LENGTH))
            put("createdAt", timestampMillis)
        })
        while (records.length() > MAX_ARCHIVE_RECORDS) records.remove(0)
        writeRecords(records)
    }

    /** Explicitly saved semantic memory. Sensitive items require user confirmation. */
    @Synchronized
    fun addMemory(
        text: String,
        category: String = "general",
        source: String = "user_saved",
        confidence: Float = 1f,
        confirmedByUser: Boolean = true,
        sensitive: Boolean = false
    ): MemoryItem {
        require(text.isNotBlank()) { "Memory text cannot be blank" }
        require(!sensitive || confirmedByUser) { "Sensitive memories require explicit confirmation" }
        val item = MemoryItem(
            id = UUID.randomUUID().toString(),
            text = text.trim().take(MAX_TEXT_LENGTH),
            category = category.take(80),
            source = source.take(160),
            createdAtMillis = System.currentTimeMillis(),
            confidence = confidence.coerceIn(0f, 1f),
            confirmedByUser = confirmedByUser,
            sensitive = sensitive
        )
        val records = readRecords()
        records.put(memoryToJson(item))
        writeRecords(records)
        return item
    }

    @Synchronized
    fun listMemories(): List<MemoryItem> = buildList {
        val records = readRecords()
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            if (record.optString("kind") != "memory") continue
            runCatching { jsonToMemory(record) }.getOrNull()?.let(::add)
        }
    }.sortedByDescending { it.createdAtMillis }

    @Synchronized
    fun deleteMemory(id: String) {
        val records = readRecords()
        val kept = JSONArray()
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            if (record.optString("id") != id) kept.put(record)
        }
        writeRecords(kept)
    }

    /** Summary of one locally archived voice/text conversation. */
    data class ArchivedConversation(
        val sessionId: String,
        val turnCount: Int,
        val startedAtMillis: Long,
        val updatedAtMillis: Long,
        val preview: String
    )

    /** Retention: 0 means keep until manually deleted; otherwise 7, 30 or 90 days. */
    var archiveRetentionDays: Int
        get() = preferences.getInt(KEY_ARCHIVE_RETENTION_DAYS, 0).coerceIn(0, 90)
        set(value) {
            require(value in ALLOWED_RETENTION_DAYS) { "Unsupported archive retention period" }
            preferences.edit().putInt(KEY_ARCHIVE_RETENTION_DAYS, value).apply()
            purgeExpiredArchive()
        }

    @Synchronized
    fun listArchivedConversations(): List<ArchivedConversation> {
        purgeExpiredArchive()
        val grouped = linkedMapOf<String, MutableList<JSONObject>>()
        val records = readRecords()
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            if (record.optString("kind") != "turn") continue
            val sessionId = record.optString("sessionId")
            if (sessionId.isNotBlank()) grouped.getOrPut(sessionId) { mutableListOf() }.add(record)
        }
        return grouped.map { (sessionId, turns) ->
            val ordered = turns.sortedBy { it.optLong("createdAt") }
            ArchivedConversation(
                sessionId = sessionId,
                turnCount = ordered.size,
                startedAtMillis = ordered.first().optLong("createdAt"),
                updatedAtMillis = ordered.last().optLong("createdAt"),
                preview = ordered.firstOrNull { it.optString("role") == "USER" }
                    ?.optString("text").orEmpty().take(120)
            )
        }.sortedByDescending { it.updatedAtMillis }
    }

    @Synchronized
    fun listConversationTurns(sessionId: String): List<ArchivedTurn> {
        purgeExpiredArchive()
        val records = readRecords()
        return buildList {
            for (index in 0 until records.length()) {
                val record = records.optJSONObject(index) ?: continue
                if (record.optString("kind") == "turn" && record.optString("sessionId") == sessionId) {
                    add(ArchivedTurn(
                        id = record.optString("id"),
                        role = record.optString("role"),
                        text = record.optString("text"),
                        timestampMillis = record.optLong("createdAt")
                    ))
                }
            }
        }.sortedBy { it.timestampMillis }
    }

    data class ArchivedTurn(
        val id: String,
        val role: String,
        val text: String,
        val timestampMillis: Long
    )

    @Synchronized
    fun deleteConversation(sessionId: String) {
        filterRecords { record ->
            record.optString("kind") != "turn" || record.optString("sessionId") != sessionId
        }
    }

    /** Deletes archived turns in [startInclusive, endExclusive), preserving personal memories. */
    @Synchronized
    fun deleteArchiveRange(startInclusive: Long, endExclusive: Long) {
        require(endExclusive > startInclusive)
        filterRecords { record ->
            if (record.optString("kind") != "turn") true
            else {
                val timestamp = record.optLong("createdAt")
                timestamp < startInclusive || timestamp >= endExclusive
            }
        }
    }

    /** Clears conversation transcripts only; saved personal memories are preserved. */
    @Synchronized
    fun clearConversationArchive() {
        filterRecords { it.optString("kind") != "turn" }
    }

    /** Clears saved personal memories only; archived conversation turns are preserved. */
    @Synchronized
    fun clearPersonalMemories() {
        filterRecords { it.optString("kind") != "memory" }
    }

    @Synchronized
    fun updateMemory(id: String, newText: String): Boolean {
        require(newText.isNotBlank()) { "Memory text cannot be blank" }
        val records = readRecords()
        var updated = false
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            if (record.optString("kind") == "memory" && record.optString("id") == id) {
                record.put("text", newText.trim().take(MAX_TEXT_LENGTH))
                updated = true
                break
            }
        }
        if (updated) writeRecords(records)
        return updated
    }

    @Synchronized
    private fun purgeExpiredArchive() {
        val days = archiveRetentionDays
        if (days == 0) return
        val cutoff = System.currentTimeMillis() - days * MILLIS_PER_DAY
        filterRecords { record ->
            record.optString("kind") != "turn" || record.optLong("createdAt") >= cutoff
        }
    }

    private fun filterRecords(keep: (JSONObject) -> Boolean) {
        val records = readRecords()
        val kept = JSONArray()
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            if (keep(record)) kept.put(record)
        }
        writeRecords(kept)
    }

    /** Clears every archived turn and semantic memory record (legacy all-data operation). */
    @Synchronized
    fun clearAll() {
        preferences.edit().remove(KEY_ENCRYPTED_DATA).apply()
    }

    private fun readRecords(): JSONArray {
        val encoded = preferences.getString(KEY_ENCRYPTED_DATA, null) ?: return JSONArray()
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > GCM_IV_LENGTH) { "Encrypted memory data is invalid" }
        val iv = packed.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = packed.copyOfRange(GCM_IV_LENGTH, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return JSONArray(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
    }

    private fun writeRecords(records: JSONArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(records.toString().toByteArray(Charsets.UTF_8))
        preferences.edit().putString(
            KEY_ENCRYPTED_DATA,
            Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        ).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun memoryToJson(item: MemoryItem) = JSONObject().apply {
        put("kind", "memory")
        put("id", item.id)
        put("text", item.text)
        put("category", item.category)
        put("source", item.source)
        put("createdAt", item.createdAtMillis)
        put("confidence", item.confidence.toDouble())
        put("confirmed", item.confirmedByUser)
        put("sensitive", item.sensitive)
    }

    private fun jsonToMemory(json: JSONObject) = MemoryItem(
        id = json.getString("id"),
        text = json.getString("text"),
        category = json.optString("category", "general"),
        source = json.optString("source", "unknown"),
        createdAtMillis = json.getLong("createdAt"),
        confidence = json.optDouble("confidence", 1.0).toFloat().coerceIn(0f, 1f),
        confirmedByUser = json.optBoolean("confirmed", false),
        sensitive = json.optBoolean("sensitive", false)
    )

    companion object {
        private const val PREFS_NAME = "juanita_encrypted_memory"
        private const val KEY_ARCHIVE_ENABLED = "conversation_archive_enabled"
        private const val KEY_ARCHIVE_RETENTION_DAYS = "conversation_archive_retention_days"
        private const val MILLIS_PER_DAY = 86_400_000L
        private val ALLOWED_RETENTION_DAYS = setOf(0, 7, 30, 90)
        private const val KEY_ENCRYPTED_DATA = "encrypted_records"
        private const val KEY_ALIAS = "juanita.memory.aes.v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private const val MAX_TEXT_LENGTH = 4000
        private const val MAX_ARCHIVE_RECORDS = 1000
    }
}
