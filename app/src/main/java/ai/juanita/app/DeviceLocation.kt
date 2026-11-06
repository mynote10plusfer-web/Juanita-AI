package ai.juanita.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.android.gms.tasks.Task

/**
 * Reads one fresh location while the app is in use. No background updates,
 * location history, or persistent coordinate storage are created.
 */
suspend fun readDeviceLocationContext(context: Context): String? = withContext(Dispatchers.IO) {
    val fineGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!fineGranted && !coarseGranted) return@withContext null

    val client = LocationServices.getFusedLocationProviderClient(context)
    val tokenSource = CancellationTokenSource()
    val priority = if (fineGranted) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

    val current = try {
        withTimeoutOrNull(15_000L) {
            client.getCurrentLocation(priority, tokenSource.token).awaitResult()
        }
    } catch (_: Exception) {
        null
    } finally {
        tokenSource.cancel()
    }

    val location = current ?: runCatching { client.lastLocation.awaitResult() }.getOrNull()
        ?: return@withContext null

    val ageMs = (System.currentTimeMillis() - location.time).coerceAtLeast(0L)
    if (ageMs > 30 * 60 * 1000L) return@withContext null

    val place = runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale.getDefault())
            .getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()
            ?.let { address ->
                listOfNotNull(address.locality, address.subAdminArea, address.adminArea, address.countryName)
                    .distinct()
                    .joinToString(", ")
            }
    }.getOrNull().orEmpty().ifBlank { "Località non identificata" }

    val latitude = String.format(Locale.US, "%.5f", location.latitude)
    val longitude = String.format(Locale.US, "%.5f", location.longitude)
    val accuracy = if (location.hasAccuracy()) location.accuracy.toInt() else -1
    val ageMinutes = ageMs / 60_000L
    """
        CONTESTO DI POSIZIONE DEL DISPOSITIVO:
        Posizione rilevata con il permesso Android concesso dall'utente.
        Località: $place.
        Coordinate: latitudine $latitude, longitudine $longitude.
        Precisione stimata: ${if (accuracy >= 0) "$accuracy metri" else "non disponibile"}.
        Età della lettura: ${ageMinutes} minuti.
        Usa questa posizione come riferimento quando l'utente chiede informazioni locali, distanze, luoghi, servizi, viaggi o condizioni del territorio. Non dichiarare che è una posizione in tempo reale se la lettura non è recente. Non comunicare le coordinate esatte a voce a meno che l'utente le chieda. Non inventare dati aggiornati (come meteo o traffico) senza una fonte aggiornata.
        La posizione viene fornita solo per la sessione corrente; non esiste tracciamento continuo in background.
    """.trimIndent()
}

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) continuation.resume(value)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            if (continuation.isActive) continuation.cancel()
        }
    }
