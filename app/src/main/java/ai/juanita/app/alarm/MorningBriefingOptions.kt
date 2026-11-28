package ai.juanita.app.alarm

import android.content.Context

/**
 * User-selected sections for the local morning briefing.
 * Defaults preserve the existing weather + calendar behavior.
 */
data class MorningBriefingOptions(
    val weather: Boolean = true,
    val horoscope: Boolean = false,
    val news: Boolean = false,
    val calendar: Boolean = true
) {
    fun save(context: Context) {
        context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WEATHER, weather)
            .putBoolean(KEY_HOROSCOPE, horoscope)
            .putBoolean(KEY_NEWS, news)
            .putBoolean(KEY_CALENDAR, calendar)
            .apply()
    }

    companion object {
        private const val PREFERENCES_FILE = "juanita_alarm"
        private const val KEY_WEATHER = "briefing_weather"
        private const val KEY_HOROSCOPE = "briefing_horoscope"
        private const val KEY_NEWS = "briefing_news"
        private const val KEY_CALENDAR = "briefing_calendar"

        fun load(context: Context): MorningBriefingOptions {
            val prefs = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
            return MorningBriefingOptions(
                weather = prefs.getBoolean(KEY_WEATHER, true),
                horoscope = prefs.getBoolean(KEY_HOROSCOPE, false),
                news = prefs.getBoolean(KEY_NEWS, false),
                calendar = prefs.getBoolean(KEY_CALENDAR, true)
            )
        }
    }
}
