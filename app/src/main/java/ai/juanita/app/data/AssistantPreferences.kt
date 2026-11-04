package ai.juanita.app.data

import android.content.Context

/**
 * Small persistence boundary for user-controlled assistant settings.
 *
 * Keep this API stable when the storage implementation is later migrated
 * to DataStore. No credentials or conversation transcripts belong here.
 */
class AssistantPreferences(context: Context) {
    private val storage = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE
    )

    var languageTag: String
        get() = storage.getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG)
            ?: DEFAULT_LANGUAGE_TAG
        set(value) {
            storage.edit().putString(KEY_LANGUAGE_TAG, value).apply()
        }

    var wakePhrase: String
        get() = storage.getString(KEY_WAKE_PHRASE, DEFAULT_WAKE_PHRASE)
            ?: DEFAULT_WAKE_PHRASE
        set(value) {
            storage.edit().putString(KEY_WAKE_PHRASE, value.trim()).apply()
        }

    companion object {
        private const val FILE_NAME = "juanita_preferences"
        private const val KEY_LANGUAGE_TAG = "language_tag"
        private const val KEY_WAKE_PHRASE = "wake_phrase"

        const val DEFAULT_LANGUAGE_TAG = "es-ES"
        const val DEFAULT_WAKE_PHRASE = "Hola Juanita"
    }
}
