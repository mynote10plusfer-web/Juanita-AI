package ai.juanita.app.alarm

import android.Manifest
import android.util.Xml
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks

/** Builds factual device-local morning context. Missing access is never reported as an empty day. */
object MorningBriefingContext {
    fun build(
        context: Context,
        languageTag: String = Locale.getDefault().toLanguageTag(),
        options: MorningBriefingOptions = MorningBriefingOptions.load(context)
    ): String {
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
        val calendarText = if (options.calendar) readCalendar(context, now, locale) else "Not requested by the user."
        val weatherText = if (options.weather) readWeather(context, locale) else "Not requested by the user."
        val horoscopeText = if (options.horoscope) {
            runCatching { ai.juanita.app.astrology.AstrologyContext.getReading(context, "daily", "") }
                .getOrElse { "Horoscope unavailable (${it.javaClass.simpleName}); do not invent a reading." }
        } else "Not requested by the user."
        val newsText = if (options.news) readNews(locale) else "Not requested by the user."
        return """
            MORNING BRIEFING CONTEXT — verified data:
            LOCAL DATE: $date. LOCAL TIME: $time. TIME ZONE: $zone.
            $systemAlarmText
            REQUESTED SECTIONS: weather=${options.weather}; horoscope=${options.horoscope}; news=${options.news}; calendar=${options.calendar}.
            CALENDAR: $calendarText
            WEATHER: $weatherText
            HOROSCOPE: $horoscopeText
            NEWS: $newsText

            REQUIRED WAKE-UP LANGUAGE: $languageTag. Use this language consistently.
            The Gemini Live voice is selected from the user's active identity profile.
            Open with a warm, reassuring and uplifting good morning greeting; avoid robotic phrasing.
            Say the weekday, date and local time. Read only the sections explicitly requested above.
            Keep each selected section brief and practical. If a selected source is unavailable, say so briefly;
            never invent weather, horoscope text, headlines or appointments.
            Distinguish the Android alarm from Juanita's alarm. Do not read emoji aloud.
            End naturally and leave room for the user's reply without asking a string of questions.
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

    /**
     * Reads a few headlines from the public Google News RSS edition for the configured locale.
     * This is an observed, undocumented feed, not an official API; failure is non-fatal.
     * Headlines are spoken for the user's personal briefing and are not persisted.
     */
    private fun readNews(locale: Locale): String {
        val language = locale.language.takeIf { it.isNotBlank() } ?: "es"
        val country = locale.country.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().country.takeIf { it.isNotBlank() }
            ?: "ES"
        val hl = if (locale.country.isBlank()) "$language-$country" else locale.toLanguageTag()
        val edition = "$country:$language"
        val url = URL(
            "https://news.google.com/rss?hl=${URLEncoder.encode(hl, "UTF-8")}" +
                "&gl=${URLEncoder.encode(country, "UTF-8")}" +
                "&ceid=${URLEncoder.encode(edition, "UTF-8")}"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
            setRequestProperty("User-Agent", "JuanitaAI personal RSS reader")
        }
        return try {
            val parser = Xml.newPullParser()
            connection.inputStream.use { input ->
                parser.setInput(input, "UTF-8")
                val headlines = mutableListOf<String>()
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT && headlines.size < 4) {
                    if (event == XmlPullParser.START_TAG && parser.name == "item") {
                        var title = ""
                        var source = ""
                        while (true) {
                            val itemEvent = parser.next()
                            if (itemEvent == XmlPullParser.END_DOCUMENT ||
                                (itemEvent == XmlPullParser.END_TAG && parser.name == "item")
                            ) break
                            if (itemEvent == XmlPullParser.START_TAG) {
                                when (parser.name) {
                                    "title" -> title = parser.nextText().trim().take(180)
                                    "source" -> source = parser.nextText().trim().take(80)
                                }
                            }
                        }
                        if (title.isNotBlank()) {
                            headlines += if (source.isNotBlank()) "$title — $source" else title
                        }
                        event = parser.eventType
                    } else {
                        event = parser.next()
                    }
                }
                if (headlines.isEmpty()) {
                    "News unavailable: the RSS feed returned no headlines."
                } else {
                    "Latest headlines (Google News RSS, edition $edition): " +
                        headlines.joinToString("; ") +
                        ". Source: Google News RSS and the publishers named beside each headline. " +
                        "Feed is unofficial and may change."
                }
            }
        } catch (failure: Exception) {
            "News unavailable: RSS request failed (${failure.javaClass.simpleName})."
        } finally {
            connection.disconnect()
        }
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

    fun readWeather(
        context: Context,
        locale: Locale = Locale.getDefault(),
        locality: String = ""
    ): String {
        val requestedLocality = locality.trim().take(120)
        val coordinates: Pair<Double, Double>
        val placeLabel: String
        if (requestedLocality.isNotBlank()) {
            val encoded = java.net.URLEncoder.encode(requestedLocality, "UTF-8")
            val geocodeConnection = (URL("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json").openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "JuanitaAI-Android")
            }
            val resolved = try {
                val json = JSONObject(geocodeConnection.inputStream.bufferedReader().use { it.readText() })
                val result = json.optJSONArray("results")?.optJSONObject(0)
                    ?: return "Weather unavailable: could not resolve the locality '$requestedLocality'. Ask the user to specify a nearby city or region."
                val lat = result.optDouble("latitude", Double.NaN)
                val lon = result.optDouble("longitude", Double.NaN)
                if (!lat.isFinite() || !lon.isFinite()) return "Weather unavailable: geocoding returned no valid coordinates for '$requestedLocality'."
                Triple(lat, lon, listOf(result.optString("name"), result.optString("admin1"), result.optString("country")).filter { it.isNotBlank() }.distinct().joinToString(", "))
            } catch (failure: Exception) {
                return "Weather unavailable: locality lookup failed (${failure.javaClass.simpleName})."
            } finally {
                geocodeConnection.disconnect()
            }
            coordinates = resolved.first to resolved.second
            placeLabel = resolved.third
        } else {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) return "Weather unavailable: location permission is missing. Ask the user for a town or city; a locality can be checked without GPS."
            // Use the same fused location source as the main assistant. LocationManager's
            // provider cache can be empty even when the phone has a usable fused fix.
            // Balanced accuracy is sufficient for local weather and avoids forcing GPS.
            val location = runCatching {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val cancellation = CancellationTokenSource()
                try {
                    runCatching {
                        Tasks.await(
                            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token),
                            8,
                            TimeUnit.SECONDS
                        )
                    }.getOrNull() ?: runCatching {
                        Tasks.await(client.lastLocation, 2, TimeUnit.SECONDS)
                    }.getOrNull()
                } finally {
                    cancellation.cancel()
                }
            }.getOrNull() ?: return "Weather unavailable: the phone has no recent fused location fix. Ask the user for a town or city."
            val fixAgeMs = (System.currentTimeMillis() - location.time).coerceAtLeast(0L)
            if (fixAgeMs > 2 * 60 * 60 * 1000L) {
                return "Weather unavailable: the last phone location is stale (${fixAgeMs / 60_000L} minutes old). Ask the user for a locality."
            }
            coordinates = location.latitude to location.longitude
            placeLabel = "current phone location"
        }

        val models = listOf(
            "ecmwf_ifs025" to "ECMWF",
            "icon_seamless" to "DWD ICON",
            "ncep_gfs_seamless" to "NOAA GFS"
        )
        val results = models.mapNotNull { (modelId, label) ->
            runCatching {
                // Open-Meteo coordinates are rounded to four decimals to avoid unnecessary precision.
                val lat = String.format(Locale.US, "%.4f", coordinates.first)
                val lon = String.format(Locale.US, "%.4f", coordinates.second)
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
        val checkedAt = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        return "Weather for $placeLabel. Open-Meteo, comparison of ${results.size} models ($sources), checked at $checkedAt; current average temperature ${String.format(locale, "%.1f", average { it.temperature })}°C; " +
            "average feels-like temperature ${String.format(locale, "%.1f", average { it.feelsLike })}°C; average wind ${String.format(locale, "%.0f", average { it.wind })} km/h; " +
            "average low ${String.format(locale, "%.1f", average { it.minimum })}°C, average high ${String.format(locale, "%.1f", average { it.maximum })}°C; " +
            "average precipitation probability ${String.format(locale, "%.0f", average { it.rainChance })}%. $confidenceNote"
    }

}
