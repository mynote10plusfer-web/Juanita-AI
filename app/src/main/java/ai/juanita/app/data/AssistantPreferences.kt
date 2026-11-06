package ai.juanita.app.data

import android.content.Context

/**
 * Device-local user settings for the assistant identity and speech preferences.
 * No credentials, raw audio, or conversation transcripts are stored here.
 */
class AssistantPreferences(context: Context) {
    private val storage = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var languageTag: String
        get() = storage.getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG) ?: DEFAULT_LANGUAGE_TAG
        set(value) { storage.edit().putString(KEY_LANGUAGE_TAG, value).apply() }

    var wakePhrase: String
        get() = storage.getString(KEY_WAKE_PHRASE, DEFAULT_WAKE_PHRASE) ?: DEFAULT_WAKE_PHRASE
        set(value) { storage.edit().putString(KEY_WAKE_PHRASE, value.trim()).apply() }

    var dialectId: String
        get() = storage.getString(KEY_DIALECT_ID, DEFAULT_DIALECT_ID) ?: DEFAULT_DIALECT_ID
        set(value) { storage.edit().putString(KEY_DIALECT_ID, value).apply() }

    var liveVoiceName: String
        get() = storage.getString(KEY_LIVE_VOICE, DEFAULT_LIVE_VOICE) ?: DEFAULT_LIVE_VOICE
        set(value) { storage.edit().putString(KEY_LIVE_VOICE, value).apply() }

    var assistantName: String
        get() = storage.getString(KEY_ASSISTANT_NAME, DEFAULT_ASSISTANT_NAME) ?: DEFAULT_ASSISTANT_NAME
        set(value) { storage.edit().putString(KEY_ASSISTANT_NAME, value.trim()).apply() }

    var irony: Float get() = getTrait(KEY_IRONY, 0.75f); set(value) = setTrait(KEY_IRONY, value)
    var sweetness: Float get() = getTrait(KEY_SWEETNESS, 0.90f); set(value) = setTrait(KEY_SWEETNESS, value)
    var sensuality: Float get() = getTrait(KEY_SENSUALITY, 0.65f); set(value) = setTrait(KEY_SENSUALITY, value)
    var seriousness: Float get() = getTrait(KEY_SERIOUSNESS, 0.40f); set(value) = setTrait(KEY_SERIOUSNESS, value)
    var friendliness: Float get() = getTrait(KEY_FRIENDLINESS, 0.85f); set(value) = setTrait(KEY_FRIENDLINESS, value)
    var curiosity: Float get() = getTrait(KEY_CURIOSITY, 1.0f); set(value) = setTrait(KEY_CURIOSITY, value)
    var expressiveness: Float get() = getTrait(KEY_EXPRESSIVENESS, 0.80f); set(value) = setTrait(KEY_EXPRESSIVENESS, value)
    var formality: Float get() = getTrait(KEY_FORMALITY, 0.15f); set(value) = setTrait(KEY_FORMALITY, value)
    var empathy: Float get() = getTrait(KEY_EMPATHY, 0.95f); set(value) = setTrait(KEY_EMPATHY, value)

    private fun getTrait(key: String, default: Float): Float = storage.getFloat(key, default)
    private fun setTrait(key: String, value: Float) { storage.edit().putFloat(key, value.coerceIn(0f, 1f)).apply() }

    companion object {
        private const val FILE_NAME = "juanita_preferences"
        private const val KEY_LANGUAGE_TAG = "language_tag"
        private const val KEY_WAKE_PHRASE = "wake_phrase"
        private const val KEY_DIALECT_ID = "dialect_id"
        private const val KEY_ASSISTANT_NAME = "assistant_name"
        private const val KEY_LIVE_VOICE = "live_voice_name"
        private const val KEY_IRONY = "trait_irony"
        private const val KEY_SWEETNESS = "trait_sweetness"
        private const val KEY_SENSUALITY = "trait_sensuality"
        private const val KEY_SERIOUSNESS = "trait_seriousness"
        private const val KEY_FRIENDLINESS = "trait_friendliness"
        private const val KEY_CURIOSITY = "trait_curiosity"
        private const val KEY_EXPRESSIVENESS = "trait_expressiveness"
        private const val KEY_FORMALITY = "trait_formality"
        private const val KEY_EMPATHY = "trait_empathy"

        const val DEFAULT_LANGUAGE_TAG = "es-ES"
        const val DEFAULT_WAKE_PHRASE = "Hola Juanita"
        const val DEFAULT_DIALECT_ID = "canarian"
        const val DEFAULT_ASSISTANT_NAME = "Juanita"
        const val DEFAULT_LIVE_VOICE = "Kore"
    }
}
