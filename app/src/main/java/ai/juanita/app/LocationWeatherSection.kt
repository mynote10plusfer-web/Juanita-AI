package ai.juanita.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

private data class WeatherSnapshot(
    val place: String,
    val description: String,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val wind: Double,
    val rain: Double,
    val today: String,
    val forecast: String,
    val updatedAt: String,
    val contextForAssistant: String
)

@Composable
fun LocationWeatherSection(
    panel: Color,
    gold: Color,
    onContextReady: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("La posizione non è stata ancora richiesta.") }
    var snapshot by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            status = "Ricerca della posizione GPS e aggiornamento meteo…"
            try {
                val result = withContext(Dispatchers.IO) { loadWeather(context) }
                snapshot = result
                onContextReady(result.contextForAssistant)
                status = "Posizione aggiornata. Dati meteo pronti per Juanita."
            } catch (error: Exception) {
                status = error.localizedMessage?.let { "Non riesco ad aggiornare: $it" }
                    ?: "Non riesco a ottenere la posizione o il meteo."
            } finally {
                loading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            refresh()
        } else {
            status = "Permesso posizione negato. Puoi continuare a usare Juanita senza GPS."
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = panel)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("POSIZIONE GPS E METEO", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                "Juanita userà la posizione del telefono per recuperare condizioni attuali e previsioni reali. La posizione non viene salvata nell'app e non viene tracciata in background.",
                color = Color(0xFF9A9DA4), fontSize = 12.sp
            )
            Button(
                onClick = {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) refresh()
                    else permissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Aggiornamento…" else if (snapshot == null) "Consenti GPS e aggiorna meteo" else "Aggiorna posizione e meteo")
            }
            Text(status, color = Color.White, fontSize = 13.sp)
            snapshot?.let { weather ->
                Text(weather.place, color = gold, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(weather.description, color = Color.White, fontSize = 14.sp)
                Text(
                    "${weather.temperature.toInt()}°C · percepiti ${weather.feelsLike.toInt()}°C · umidità ${weather.humidity}%\nVento ${weather.wind.toInt()} km/h · precipitazioni ${weather.rain} mm",
                    color = Color.White, fontSize = 13.sp
                )
                Text("Oggi: ${weather.today}", color = Color(0xFFB9BBC1), fontSize = 12.sp)
                Text("Domani: ${weather.forecast}", color = Color(0xFFB9BBC1), fontSize = 12.sp)
                Text("Aggiornato: ${weather.updatedAt}", color = Color(0xFF777D87), fontSize = 10.sp)
                Text(
                    "I dati vengono inseriti nel contesto di Juanita all'avvio della prossima conversazione vocale. Fonte meteo: Open-Meteo.",
                    color = Color(0xFF9A9DA4), fontSize = 11.sp
                )
            }
        }
    }
}

private suspend fun loadWeather(context: Context): WeatherSnapshot {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) throw IllegalStateException("Concedi il permesso di posizione nelle impostazioni Android.")

    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = if (fine) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
    val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        withTimeoutOrNull(18_000L) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                try {
                    manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        } ?: manager.getLastKnownLocation(provider)
    } else {
        @Suppress("DEPRECATION")
        manager.getLastKnownLocation(provider)
    } ?: throw IllegalStateException("GPS non disponibile. Attiva la posizione del telefono e riprova.")

    val place = runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()
            ?.let { address ->
                listOfNotNull(address.locality, address.subAdminArea, address.adminArea)
                    .distinct().joinToString(", ")
            }
    }.getOrNull().orEmpty().ifBlank { "Posizione attuale" }

    val lat = String.format(Locale.US, "%.5f", location.latitude)
    val lon = String.format(Locale.US, "%.5f", location.longitude)
    val endpoint = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max" +
        "&forecast_days=2&timezone=auto"
    val jsonText = withContext(Dispatchers.IO) {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("Servizio meteo non raggiungibile (HTTP ${connection.responseCode}).")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
    val root = JSONObject(jsonText)
    val current = root.getJSONObject("current")
    val daily = root.getJSONObject("daily")
    val code = current.optInt("weather_code", -1)
    val description = weatherDescription(code)
    val temp = current.optDouble("temperature_2m")
    val feels = current.optDouble("apparent_temperature", temp)
    val humidity = current.optInt("relative_humidity_2m")
    val wind = current.optDouble("wind_speed_10m")
    val rain = current.optDouble("precipitation")
    val today = "${daily.getJSONArray("temperature_2m_min").optDouble(0).toInt()}–${daily.getJSONArray("temperature_2m_max").optDouble(0).toInt()}°C, ${weatherDescription(daily.getJSONArray("weather_code").optInt(0))}, probabilità pioggia ${daily.getJSONArray("precipitation_probability_max").optInt(0)}%"
    val tomorrow = "${daily.getJSONArray("temperature_2m_min").optDouble(1).toInt()}–${daily.getJSONArray("temperature_2m_max").optDouble(1).toInt()}°C, ${weatherDescription(daily.getJSONArray("weather_code").optInt(1))}, probabilità pioggia ${daily.getJSONArray("precipitation_probability_max").optInt(1)}%"
    val time = current.optString("time")
    val updated = if (time.length >= 16) time.replace('T', ' ') else time
    val contextText = """
        CONTESTO METEO AGGIORNATO (dati reali Open-Meteo, non una supposizione):
        Posizione corrente dell'utente: $place.
        Ora locale dei dati: $updated.
        Condizioni attuali: $description, temperatura ${temp}°C, percepita ${feels}°C, umidità ${humidity}%, vento ${wind} km/h, precipitazioni ${rain} mm.
        Previsione oggi: $today.
        Previsione domani: $tomorrow.
        Se l'utente chiede il meteo, usa questi dati e specifica che l'aggiornamento è quello indicato. Non inventare dati più recenti; se sono diventati vecchi, chiedi di aggiornare la posizione e il meteo.
    """.trimIndent()

    return WeatherSnapshot(
        place, description, temp, feels, humidity, wind, rain, today, tomorrow, updated, contextText
    )
}

private fun weatherDescription(code: Int): String = when (code) {
    0 -> "sereno"
    1 -> "prevalentemente sereno"
    2 -> "parzialmente nuvoloso"
    3 -> "coperto"
    45, 48 -> "nebbia"
    51, 53, 55 -> "pioviggine"
    56, 57 -> "pioviggine gelata"
    61, 63, 65 -> "pioggia"
    66, 67 -> "pioggia gelata"
    71, 73, 75 -> "neve"
    77 -> "granelli di neve"
    80, 81, 82 -> "rovesci"
    85, 86 -> "rovesci di neve"
    95 -> "temporale"
    96, 99 -> "temporale con grandine"
    else -> "condizioni non specificate"
}
