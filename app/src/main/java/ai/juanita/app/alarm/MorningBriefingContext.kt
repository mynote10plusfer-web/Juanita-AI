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
    fun build(context: Context): String {
        val now = ZonedDateTime.now()
        val locale = Locale.forLanguageTag("it-IT")
        val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale))
        val time = now.format(DateTimeFormatter.ofPattern("HH:mm", locale))
        val zone = now.zone.id
        val nextAlarm = runCatching { context.getSystemService(AlarmManager::class.java).nextAlarmClock }.getOrNull()
        val systemAlarmText = nextAlarm?.let {
            val alarmTime = java.time.Instant.ofEpochMilli(it.triggerTime).atZone(ZoneId.systemDefault())
            "Prossima sveglia Android comunicata dal sistema: " +
                alarmTime.format(DateTimeFormatter.ofPattern("EEEE d MMMM 'alle' HH:mm", locale)) + "."
        } ?: "Android non comunica una prossima sveglia esterna a Juanita."
        val calendarText = readCalendar(context, now)
        val weatherText = readWeather(context)
        return """
            CONTESTO PER IL RISVEGLIO — dati locali raccolti sul dispositivo:
            Oggi è $date. Ora locale: $time. Fuso orario: $zone.
            $systemAlarmText
            CALENDARIO: $calendarText
            METEO: $weatherText

            Parla in italiano naturale, con la voce Gemini Live già selezionata dall'utente.
            Apri con un buongiorno molto dolce, rassicurante e luminoso, senza frasi robotiche.
            Pronuncia il giorno, la data e l'ora; poi riassumi brevemente meteo e appuntamenti.
            Se un dato non è disponibile, dichiaralo: non inventare previsioni o impegni.
            Distingui la sveglia Android da quella di Juanita. Non leggere emoji ad alta voce.
            Concludi con una frase affettuosa e lascia spazio alla risposta, senza interrogare.
        """.trimIndent()
    }

    private fun readCalendar(context: Context, now: ZonedDateTime): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return "Calendario non accessibile: permesso non concesso. Non dedurre che non ci siano appuntamenti."
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
                    val title = cursor.getString(titleIndex)?.takeIf { it.isNotBlank() } ?: "Appuntamento"
                    val begin = java.time.Instant.ofEpochMilli(cursor.getLong(beginIndex)).atZone(now.zone)
                        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN))
                    val place = if (locationIndex >= 0) cursor.getString(locationIndex)?.takeIf { it.isNotBlank() } else null
                    events += if (place == null) "$begin — $title" else "$begin — $title, $place"
                }
            }
            if (events.isEmpty()) "Nessun evento trovato oggi nel calendario autorizzato."
            else events.joinToString("; ")
        }.getOrElse { "Calendario temporaneamente non leggibile (${it.javaClass.simpleName}); non assumere che sia vuoto." }
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

    private fun readWeather(context: Context): String {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return "Meteo non disponibile: manca il permesso di posizione."
        val location = runCatching {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return "Meteo non disponibile: il telefono non ha una posizione recente."

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
        if (results.isEmpty()) return "Meteo non disponibile: nessuno dei modelli meteorologici ha risposto."

        fun average(selector: (ModelWeather) -> Double): Double {
            val values = results.map(selector).filter { it.isFinite() }
            return if (values.isEmpty()) Double.NaN else values.average()
        }
        val temperatures = results.map { it.temperature }.filter { it.isFinite() }
        val spread = if (temperatures.size > 1) temperatures.maxOrNull()!! - temperatures.minOrNull()!! else 0.0
        val sources = results.joinToString(", ") { it.model }
        val confidenceNote = if (results.size >= 2 && spread <= 3.0) {
            "I modelli sono abbastanza concordi sulla temperatura attuale."
        } else if (results.size >= 2) {
            "I modelli divergono di circa ${String.format(Locale.ITALIAN, "%.1f", spread)} gradi: segnala l'intervallo, senza presentare la media come certezza."
        } else {
            "È disponibile un solo modello: il dato non è stato confrontato."
        }
        return "Open-Meteo, confronto di ${results.size} modelli ($sources): temperatura media attuale ${String.format(Locale.ITALIAN, "%.1f", average { it.temperature })}°C; " +
            "percepita media ${String.format(Locale.ITALIAN, "%.1f", average { it.feelsLike })}°C; vento medio ${String.format(Locale.ITALIAN, "%.0f", average { it.wind })} km/h; " +
            "minima media ${String.format(Locale.ITALIAN, "%.1f", average { it.minimum })}°C, massima media ${String.format(Locale.ITALIAN, "%.1f", average { it.maximum })}°C; " +
            "probabilità media di pioggia ${String.format(Locale.ITALIAN, "%.0f", average { it.rainChance })}%. $confidenceNote"
    }

}
