package ai.juanita.app.route

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import android.os.SystemClock
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class RoutePlace(
    val name: String,
    val admin1: String,
    val admin2: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
) {
    val label: String
        get() = listOf(name, admin2, admin1, country)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
}

internal sealed class RoutePlaceResolution {
    data class Selected(val place: RoutePlace) : RoutePlaceResolution()
    data class Ambiguous(val places: List<RoutePlace>) : RoutePlaceResolution()
    data object NotFound : RoutePlaceResolution()
    data class Unavailable(val reason: String) : RoutePlaceResolution()
}

internal fun resolveRoutePlace(
    query: String,
    candidates: List<RoutePlace>
): RoutePlaceResolution {
    if (query.isBlank() || candidates.isEmpty()) return RoutePlaceResolution.NotFound
    val normalizedQuery = normalizePlaceText(query)
    val exact = candidates.filter { normalizePlaceText(it.name) == normalizedQuery }
    if (exact.size == 1) return RoutePlaceResolution.Selected(exact.first())
    if (exact.size > 1) return RoutePlaceResolution.Ambiguous(exact.take(5))

    val nameMatches = candidates.filter { candidate ->
        val name = normalizePlaceText(candidate.name)
        name.isNotBlank() && (normalizedQuery.contains(name) || name.contains(normalizedQuery))
    }
    if (nameMatches.size == 1) return RoutePlaceResolution.Selected(nameMatches.first())

    val regionalMatches = nameMatches.filter { candidate ->
        listOf(candidate.admin2, candidate.admin1)
            .map(::normalizePlaceText)
            .filter { it.length >= 3 }
            .any { normalizedQuery.contains(it) }
    }
    if (regionalMatches.size == 1) return RoutePlaceResolution.Selected(regionalMatches.first())
    if (regionalMatches.size > 1) return RoutePlaceResolution.Ambiguous(regionalMatches.take(5))

    val countryMatches = nameMatches.filter { candidate ->
        val country = normalizePlaceText(candidate.country)
        country.length >= 3 && normalizedQuery.contains(country)
    }
    if (countryMatches.size == 1) return RoutePlaceResolution.Selected(countryMatches.first())
    if (candidates.size == 1) return RoutePlaceResolution.Selected(candidates.first())
    return RoutePlaceResolution.Ambiguous((countryMatches.ifEmpty { nameMatches.ifEmpty { candidates } }).take(5))
}

private fun normalizePlaceText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

internal fun isRouteLocationAcceptable(ageMillis: Long, accuracyMeters: Float): Boolean =
    ageMillis in 0L..120_000L && accuracyMeters.isFinite() && accuracyMeters >= 0f && accuracyMeters <= 5_000f

internal object RouteText {
    fun formatDistance(meters: Double, locale: Locale = Locale.getDefault()): String {
        if (!meters.isFinite() || meters < 0.0) return "distance unavailable"
        return if (meters < 1000.0) {
            String.format(locale, "%d m", meters.roundToInt())
        } else {
            String.format(locale, "%.1f km", meters / 1000.0)
        }
    }

    fun formatDuration(seconds: Double): String {
        if (!seconds.isFinite() || seconds < 0.0) return "time unavailable"
        val totalMinutes = ceil(seconds / 60.0).toInt().coerceAtLeast(1)
        return if (totalMinutes < 60) {
            "$totalMinutes min"
        } else {
            "${totalMinutes / 60} h ${totalMinutes % 60} min"
        }
    }
}

/**
 * On-demand, low-volume base routing only. The public OSRM demo is best-effort,
 * rate-limited and non-commercial; this adapter must be replaceable before broad distribution.
 * No route history or coordinates are persisted.
 */
object RoadRouteContext {
    private const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
    private const val OSRM_URL = "https://router.project-osrm.org/route/v1/driving"
    private const val USER_AGENT = "JuanitaAI/0.11.15 (https://github.com/mynote10plusfer-web/Juanita-AI)"
    private const val ROUTE_CACHE_TTL_MS = 5L * 60L * 1000L
    private const val GEOCODE_CACHE_TTL_MS = 24L * 60L * 60L * 1000L

    private data class GeocodeCacheEntry(val storedAt: Long, val value: RoutePlaceResolution)
    private data class RouteCacheEntry(val storedAt: Long, val value: String)
    private data class PlaceResult(val place: RoutePlace?, val error: String?)

    private val cacheLock = Any()
    private val geocodeCache = LinkedHashMap<String, GeocodeCacheEntry>(24, 0.75f, true)
    private val routeCache = LinkedHashMap<String, RouteCacheEntry>(12, 0.75f, true)
    private val rateLock = Any()
    private var lastOsrmRequestAt = 0L

    fun getRouteEstimate(context: Context, origin: String, destination: String): String {
        val destinationText = destination.trim().take(160)
        if (destinationText.isBlank()) {
            return "A route destination is required. Ask the user where they want to go."
        }
        val originText = origin.trim().take(160)
        val originResult = resolveRequestedPlace(context, originText, "origin")
        if (originResult.place == null) return originResult.error ?: "Route origin is unavailable."
        val destinationResult = resolveRequestedPlace(context, destinationText, "destination")
        if (destinationResult.place == null) return destinationResult.error ?: "Route destination is unavailable."

        val start = originResult.place
        val end = destinationResult.place
        val key = listOf(
            String.format(Locale.US, "%.4f,%.4f", start.longitude, start.latitude),
            String.format(Locale.US, "%.4f,%.4f", end.longitude, end.latitude)
        ).joinToString(";")
        val usesCurrentLocation = isCurrentLocationQuery(originText) || isCurrentLocationQuery(destinationText)
        val now = SystemClock.elapsedRealtime()
        if (!usesCurrentLocation) {
            synchronized(cacheLock) {
                routeCache[key]?.takeIf { now - it.storedAt < ROUTE_CACHE_TTL_MS }?.let { return it.value }
            }
        }

        return try {
            waitForOsrmRateLimit()
            val url = "$OSRM_URL/${key}?overview=false&steps=true&alternatives=false"
            val json = readJson(url)
            if (json.optString("code") != "Ok") {
                return "No road route was returned for ${start.label} to ${end.label}. No travel-time estimate was produced."
            }
            val route = json.optJSONArray("routes")?.optJSONObject(0)
                ?: return "The routing service returned no usable route. No travel-time estimate was produced."
            val distance = route.optDouble("distance", Double.NaN)
            val duration = route.optDouble("duration", Double.NaN)
            if (!distance.isFinite() || !duration.isFinite() || distance < 0.0 || duration < 0.0) {
                return "The routing service returned invalid distance or duration. No estimate was produced."
            }
            val roads = collectRoadNames(route)
            val checkedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val roadText = if (roads.isEmpty()) "" else " Main roads: ${roads.joinToString(", ")}."
            val result = "Base road route from ${start.label} to ${end.label}: ${RouteText.formatDistance(distance)}; estimated standard-car driving time ${RouteText.formatDuration(duration)}. This is not adjusted for live traffic, is not motorcycle-specific, and is not turn-by-turn navigation. Traffic was not checked. Checked at $checkedAt. Source: OSRM public demo and OpenStreetMap contributors (ODbL); place lookup: Open-Meteo Geocoding.$roadText"
            if (!usesCurrentLocation) {
                synchronized(cacheLock) {
                    routeCache[key] = RouteCacheEntry(SystemClock.elapsedRealtime(), result)
                    while (routeCache.size > 12) routeCache.remove(routeCache.keys.first())
                }
            }
            result
        } catch (_: Exception) {
            "Routing service is temporarily unavailable. No route or traffic-aware ETA was produced."
        }
    }

    private fun resolveRequestedPlace(context: Context, query: String, role: String): PlaceResult {
        if (isCurrentLocationQuery(query)) {
            val location = currentLocation(context)
                ?: return PlaceResult(
                    null,
                    "The phone has no sufficiently fresh, precise authorized location for the route $role. Ask the user for a starting/ending locality or enable location permission."
                )
            return PlaceResult(
                RoutePlace("Current phone location", "", "", "", location.latitude, location.longitude),
                null
            )
        }
        return when (val resolution = geocode(query)) {
            is RoutePlaceResolution.Selected -> PlaceResult(resolution.place, null)
            is RoutePlaceResolution.Ambiguous -> {
                val options = resolution.places.joinToString("; ") { it.label }
                PlaceResult(null, "The $role locality '$query' is ambiguous. Ask the user to choose or specify the region/country: $options.")
            }
            RoutePlaceResolution.NotFound -> PlaceResult(null, "Could not resolve the $role locality '$query'. Ask the user for a nearby town or region.")
            is RoutePlaceResolution.Unavailable -> PlaceResult(null, "Place lookup is temporarily unavailable for the $role. Ask the user to try again or provide a more specific locality.")
        }
    }

    private fun geocode(query: String): RoutePlaceResolution {
        val normalized = normalizePlaceText(query)
        if (normalized.isBlank()) return RoutePlaceResolution.NotFound
        val now = SystemClock.elapsedRealtime()
        synchronized(cacheLock) {
            geocodeCache[normalized]?.takeIf { now - it.storedAt < GEOCODE_CACHE_TTL_MS }?.let { return it.value }
        }
        val encoded = URLEncoder.encode(query, "UTF-8")
        val language = URLEncoder.encode(Locale.getDefault().language.ifBlank { "en" }, "UTF-8")
        val json = try {
            readJson("$GEOCODING_URL?name=$encoded&count=5&language=$language&format=json")
        } catch (failure: Exception) {
            return RoutePlaceResolution.Unavailable(failure.javaClass.simpleName)
        }
        val array = json.optJSONArray("results")
        val places = mutableListOf<RoutePlace>()
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val latitude = item.optDouble("latitude", Double.NaN)
                val longitude = item.optDouble("longitude", Double.NaN)
                val name = item.optString("name").trim()
                if (name.isBlank() || !latitude.isFinite() || !longitude.isFinite()) continue
                places += RoutePlace(
                    name = name,
                    admin1 = item.optString("admin1").trim(),
                    admin2 = item.optString("admin2").trim(),
                    country = item.optString("country").trim(),
                    latitude = latitude,
                    longitude = longitude
                )
            }
        }
        val resolution = resolveRoutePlace(query, places)
        if (resolution !is RoutePlaceResolution.Unavailable) {
            synchronized(cacheLock) {
                geocodeCache[normalized] = GeocodeCacheEntry(SystemClock.elapsedRealtime(), resolution)
                while (geocodeCache.size > 24) geocodeCache.remove(geocodeCache.keys.first())
            }
        }
        return resolution
    }

    @SuppressLint("MissingPermission")
    private fun currentLocation(context: Context): Location? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val cancellation = CancellationTokenSource()
        return try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(60_000L)
                .setDurationMillis(10_000L)
                .build()
            val location = Tasks.await(
                LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(request, cancellation.token),
                12,
                TimeUnit.SECONDS
            ) ?: return null
            val age = (System.currentTimeMillis() - location.time).coerceAtLeast(0L)
            val accuracy = if (location.hasAccuracy()) location.accuracy else Float.NaN
            if (location.latitude.isFinite() && location.longitude.isFinite() && isRouteLocationAcceptable(age, accuracy)) {
                location
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    private fun isCurrentLocationQuery(query: String): Boolean {
        val normalized = normalizePlaceText(query)
        return normalized.isBlank() || normalized in setOf(
            "current location", "my location", "phone location",
            "la mia posizione", "posizione attuale", "la posizione attuale",
            "mi ubicacion", "mi ubicacion actual", "ubicacion actual",
            "mi ubicacao", "localizacao atual"
        )
    }

    private fun collectRoadNames(route: JSONObject): List<String> {
        val result = mutableListOf<String>()
        val legs = route.optJSONArray("legs") ?: return result
        for (legIndex in 0 until legs.length()) {
            val steps = legs.optJSONObject(legIndex)?.optJSONArray("steps") ?: continue
            for (stepIndex in 0 until steps.length()) {
                val name = steps.optJSONObject(stepIndex)?.optString("name")?.trim().orEmpty()
                if (name.isNotBlank() && name !in result) result += name
                if (result.size >= 4) return result
            }
        }
        return result
    }

    private fun waitForOsrmRateLimit() {
        synchronized(rateLock) {
            val now = SystemClock.elapsedRealtime()
            val remaining = 1000L - (now - lastOsrmRequestAt)
            if (lastOsrmRequestAt > 0L && remaining > 0L) Thread.sleep(remaining)
            lastOsrmRequestAt = SystemClock.elapsedRealtime()
        }
    }

    private fun readJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("HTTP $status")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
