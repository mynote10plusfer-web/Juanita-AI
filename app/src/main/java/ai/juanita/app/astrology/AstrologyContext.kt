package ai.juanita.app.astrology

import android.content.Context
import ai.juanita.app.profile.PersonalProfile
import ai.juanita.app.profile.PersonalProfileStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** On-demand astrology data. Daily requests disclose only a zodiac sign; natal requests require explicit consent. */
object AstrologyContext {
    private const val DAILY_BASE = "https://freehoroscopeapi.com/api/v1/get-horoscope/daily?sign="
    private const val NATAL_BASE = "https://api.cosmyday.com/natal"
    private const val LOCATION_BASE = "https://api.cosmyday.com/search-location?q="

    fun getReading(context: Context, readingType: String, privacyConsent: String): String {
        val profile = PersonalProfileStore(context).load()
        val dateText = profile.value(PersonalProfile.BIRTH_DATE)
            ?: return "Nel profilo cifrato non trovo la data di nascita. Chiedi all'utente di comunicarla esplicitamente e salvarla."
        val date = runCatching { LocalDate.parse(dateText) }.getOrElse { return "La data di nascita salvata non è valida nel formato YYYY-MM-DD." }
        return when (readingType.lowercase(Locale.ROOT)) {
            "daily" -> fetchDaily(sunSign(date))
            "natal" -> {
                if (privacyConsent != "confirmed") return "Tema natale non richiesto: consenso privacy mancante."
                val timeText = profile.value(PersonalProfile.BIRTH_TIME)
                    ?: return "Per calcolare Ascendente e case mi serve l'ora locale esatta di nascita (HH:mm). Chiedila e salvala nel profilo."
                val place = profile.value(PersonalProfile.BIRTH_PLACE)
                    ?: return "Per calcolare Ascendente e case mi serve la città/località di nascita con il Paese. Chiedila e salvala nel profilo."
                val time = runCatching { LocalTime.parse(timeText) }.getOrElse { return "L'ora di nascita salvata non è valida; usa HH:mm." }
                fetchNatal(date, time, place)
            }
            else -> "Tipo di lettura astrologica non supportato."
        }
    }

    private fun fetchDaily(sign: String): String {
        val data = requestJson(DAILY_BASE + sign).optJSONObject("data") ?: return "Il provider non ha restituito dati astrologici."
        val horoscope = data.optString("horoscope").takeIf { it.isNotBlank() } ?: return "Il provider non ha restituito il testo dell'oroscopo."
        return "Oroscopo giornaliero (${data.optString("date")}), segno solare $sign: $horoscope. Fonte: FreeHoroscopeAPI (freehoroscopeapi.com)."
    }

    private fun fetchNatal(date: LocalDate, time: LocalTime, place: String): String {
        val location = requestJson(LOCATION_BASE + URLEncoder.encode(place, Charsets.UTF_8.name()))
        val lat = firstNumber(location, "lat", "latitude"); val lon = firstNumber(location, "lon", "lng", "longitude")
        if (!lat.isFinite() || !lon.isFinite()) return "La località di nascita non è stata risolta. Chiedi città e Paese più precisi."
        val payload = JSONObject().apply {
            put("year", date.year); put("month", date.monthValue); put("day", date.dayOfMonth)
            put("hour", time.hour); put("minute", time.minute); put("lat", lat); put("lon", lon)
        }
        val response = requestJson(NATAL_BASE, "POST", payload.toString())
        return "Tema natale per $date alle ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}, località $place. Dati astrologici strutturati (posizioni, Luna, Ascendente, case e aspetti quando restituiti): ${response.toString(2).take(6000)}. Fonte: CosmyDay (cosmyday.com), attribuzione richiesta. L'ora è locale; l'interpretazione astrologica non è una previsione scientifica."
    }

    private fun firstNumber(json: JSONObject, vararg keys: String): Double {
        for (key in keys) {
            val value = json.opt(key)
            val number = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
            if (number != null && number.isFinite()) return number
        }
        return Double.NaN
    }

    private fun requestJson(url: String, method: String = "GET", body: String? = null): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 12000; c.readTimeout = 15000
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "Juanita-AI astrology data client")
            if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json; charset=utf-8") }
            if (body != null) c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Provider HTTP $code")
            return JSONObject(text)
        } finally { c.disconnect() }
    }

    internal fun sunSign(date: LocalDate): String {
        val m=date.monthValue; val d=date.dayOfMonth
        return when {
            (m==3&&d>=21)||(m==4&&d<=19)->"aries"
            (m==4&&d>=20)||(m==5&&d<=20)->"taurus"
            (m==5&&d>=21)||(m==6&&d<=20)->"gemini"
            (m==6&&d>=21)||(m==7&&d<=22)->"cancer"
            (m==7&&d>=23)||(m==8&&d<=22)->"leo"
            (m==8&&d>=23)||(m==9&&d<=22)->"virgo"
            (m==9&&d>=23)||(m==10&&d<=22)->"libra"
            (m==10&&d>=23)||(m==11&&d<=21)->"scorpio"
            (m==11&&d>=22)||(m==12&&d<=21)->"sagittarius"
            (m==12&&d>=22)||(m==1&&d<=19)->"capricorn"
            (m==1&&d>=20)||(m==2&&d<=18)->"aquarius"
            else->"pisces"
        }
    }
}
