package ai.juanita.app.alarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds factual device-local morning context. Missing access is never reported as an empty day. */
object MorningBriefingContext {
    fun build(context: Context, languageTag: String = Locale.getDefault().toLanguageTag()): String {
        val now = ZonedDateTime.now()
        val locale = Locale.forLanguageTag(languageTag).takeIf { it.language.isNotBlank() } ?: Locale.getDefault()
        val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale))
        val time = now.format(DateTimeFormatter.ofPattern("HH:mm", locale))
        val zone = now.zone.id
        val nextAlarm = runCatching { context.getSystemService(AlarmManager::class.java).nextAlarmClock }.getOrNull()
        val systemAlarmText = nextAlarm?.let {
            val alarmTime = java.time.Instant.ofEpochMilli(it.triggerTime).atZone(ZoneId.systemDefault())
            "Next Android alarm reported by the system: " +
                alarmTime.format(DateTimeFormatter.ofPattern("EEEE d MMMM 'at' HH:mm", locale)) + "."
        } ?: "Android reports no upcoming external alarm."
        val calendarText = readCalendar(context, now, locale)
        val weatherText = readWeather(context, locale)
        return """
            MORNING BRIEFING CONTEXT — device-local data:
            LOCAL DATE: $date. LOCAL TIME: $time. TIME ZONE: $zone.
            $systemAlarmText
            CALENDAR: $calendarText
            WEATHER: $weatherText

            REQUIRED WAKE-UP LANGUAGE: $languageTag. Use this language consistently.
            The Gemini Live voice is selected from the user's active identity profile.
            Open with a very warm, reassuring and uplifting good morning greeting; avoid robotic phrasing.
            Say the weekday, date and local time, then briefly summarize weather and calendar events.
            If a fact is unavailable, say so; never invent forecasts or appointments.
            Distinguish the Android alarm from Juanita's alarm. Do not read emoji aloud.
            End with an affectionate sentence and leave room for the user's reply without asking a string of questions.
        """.trimIndent()
    }

    private fun readCalendar(context: Context, now: ZonedDateTime, locale: Locale): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return "Calendar unavailable: permission not granted. Do not assume there are no events."
        }
        val start = now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()
        val end = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
        return runCatching {
            val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.EVENT_LOCATION)
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            android.content.ContentUris.appendId(builder, start)
            android.content.ContentUris.appendId(builder, end)
            val events = mutableListOf<String>()
            context.contentResolver.query(builder.build(), projection, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cursor ->
                val titleIndex = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val locationIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                while (cursor.moveToNext() && events.size < 12) {
                    val title = cursor.getString(titleIndex)?.takeIf { it.isNotBlank() } ?: "Calendar event"
                    val begin = java.time.Instant.ofEpochMilli(cursor.getLong(beginIndex)).atZone(now.zone)
                        .format(DateTimeFormatter.ofPattern("HH:mm", locale))
                    val place = if (locationIndex >= 0) cursor.getString(locationIndex)?.takeIf { it.isNotBlank() } else null
                    events += if (place == null) "$begin — $title" else "$begin — $title, $place"
                }
            }
            if (events.isEmpty()) "No events found today in the authorized calendar."
            else events.joinToString("; ")
        }.getOrElse { "Calendar temporarily unreadable (${it.javaClass.simpleName}); do not assume it is empty." }
    }

    private data class ModelWeather(
        val model: String,
        val temperature: Double,
        val feelsLike: Double,
        val wind: Double,
        val precipitation: Double,
        val minimum: Double,
        val maximum: Double,
        val rainChance: Double
    )

    private fun readWeather(context: Context, locale: Locale): String {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return "Weather unavailable: location permission is missing."
        val location = runCatching {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return "Weather unavailable: the phone has no recent location fix."

        val models = listOf(
            "ecmwf_ifs025" to "ECMWF",
            "icon_seamless" to "DWD ICON",
            "ncep_gfs_seamless" to "NOAA GFS"
        )
        val results = models.mapNotNull { (modelId, label) ->
            runCatching {
                // Open-Meteo coordinates are rounded to four decimals to avoid unnecessary precision.
                val lat = String.format(Locale.US, "%.4f", location.latitude)
                val lon = String.format(Locale.US, "%.4f", location.longitude)
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&models=$modelId" +
                        "&current=temperature_2m,apparent_temperature,precipitation,wind_speed_10m" +
                        "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1"
                )
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "JuanitaAI-Android")
                }
                try {
                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val current = json.getJSONObject("current")
                    val daily = json.getJSONObject("daily")
                    ModelWeather(
                        model = label,
                        temperature = current.optDouble("temperature_2m", Double.NaN),
                        feelsLike = current.optDouble("apparent_temperature", Double.NaN),
                        wind = current.optDouble("wind_speed_10m", Double.NaN),
                        precipitation = current.optDouble("precipitation", Double.NaN),
                        minimum = daily.getJSONArray("temperature_2m_min").optDouble(0, Double.NaN),
                        maximum = daily.getJSONArray("temperature_2m_max").optDouble(0, Double.NaN),
                        rainChance = daily.getJSONArray("precipitation_probability_max").optDouble(0, Double.NaN)
                    )
                } finally { connection.disconnect() }
            }.getOrNull()
        }
        if (results.isEmpty()) return "Weather unavailable: none of the weather models responded."

        fun average(selector: (ModelWeather) -> Double): Double {
            val values = results.map(selector).filter { it.isFinite() }
            return if (values.isEmpty()) Double.NaN else values.average()
        }
        val temperatures = results.map { it.temperature }.filter { it.isFinite() }
        val spread = if (temperatures.size > 1) temperatures.maxOrNull()!! - temperatures.minOrNull()!! else 0.0
        val sources = results.joinToString(", ") { it.model }
        val confidenceNote = if (results.size >= 2 && spread <= 3.0) {
            "The models broadly agree on the current temperature."
        } else if (results.size >= 2) {
            "The models differ by about ${String.format(locale, "%.1f", spread)} degrees: report the range and do not present the average as certain."
        } else {
            "Only one model is available; the result has not been cross-checked."
        }
        return "Open-Meteo, comparison of ${results.size} models ($sources): current average temperature ${String.format(locale, "%.1f", average { it.temperature })}°C; " +
            "average feels-like temperature ${String.format(locale, "%.1f", average { it.feelsLike })}°C; average wind ${String.format(locale, "%.0f", average { it.wind })} km/h; " +
            "average low ${String.format(locale, "%.1f", average { it.minimum })}°C, average high ${String.format(locale, "%.1f", average { it.maximum })}°C; " +
            "average precipitation probability ${String.format(locale, "%.0f", average { it.rainChance })}%. $confidenceNote"
    }

}
