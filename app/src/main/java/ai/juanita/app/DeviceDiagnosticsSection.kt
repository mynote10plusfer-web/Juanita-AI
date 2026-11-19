package ai.juanita.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private data class DeviceDiagnosticSnapshot(
    val capturedAt: String,
    val battery: String,
    val batteryTemperature: String,
    val charging: String,
    val appMemory: String,
    val availableMemory: String,
    val cpuAverage: String,
    val thermal: String,
    val activeRecorders: String,
    val liveSessionDuration: String
)

@Composable
internal fun DeviceDiagnosticsSection(
    panel: Color,
    gold: Color,
    liveState: String,
    wakeWordEnabled: Boolean,
    wakeWordStatus: String,
    serviceBound: Boolean,
    serviceLifecycle: String,
    wakeLockHeld: Boolean,
    liveSessionDurationMillis: () -> Long
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<DeviceDiagnosticSnapshot?>(null) }

    fun captureSnapshot(): DeviceDiagnosticSnapshot {
        val battery = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
        val batteryPercent = if (level >= 0) "${(level * 100f / scale).toInt()}%" else "Non disponibile"
        val tempTenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val temp = if (tempTenths >= 0) String.format(Locale.ROOT, "%.1f °C", tempTenths / 10f) else "Non disponibile"
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = when {
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL -> "In carica / piena"
            plugged != 0 -> "Collegata"
            status == BatteryManager.BATTERY_STATUS_DISCHARGING -> "A batteria"
            else -> "Sconosciuto"
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val processMemory = Debug.MemoryInfo()
        Debug.getMemoryInfo(processMemory)
        val appPssMb = processMemory.totalPss / 1024f
        val availableMb = memoryInfo.availMem / (1024f * 1024f)

        val elapsedMs = (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()).coerceAtLeast(1L)
        val cpuMs = Process.getElapsedCpuTime().coerceAtLeast(0L)
        val cpuAverage = (cpuMs * 100.0 / elapsedMs).coerceAtLeast(0.0)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normale"
                PowerManager.THERMAL_STATUS_LIGHT -> "Leggera"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderata"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severa"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critica"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergenza"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Arresto termico"
                else -> "Sconosciuta"
            }
        } else "Non esposta da questa versione Android"

        val recorderCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                    .activeRecordingConfigurations.size
            }.getOrNull()?.toString() ?: "Non disponibile"
        } else "Non supportato"

        return DeviceDiagnosticSnapshot(
            capturedAt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date()),
            battery = batteryPercent,
            batteryTemperature = temp,
            charging = charging,
            appMemory = String.format(Locale.getDefault(), "%.1f MB PSS", appPssMb),
            availableMemory = String.format(Locale.getDefault(), "%.0f MB", availableMb),
            cpuAverage = String.format(Locale.getDefault(), "%.1f%% medio dall'avvio del processo", cpuAverage),
            thermal = thermal,
            activeRecorders = recorderCount,
            liveSessionDuration = formatSessionDuration(liveSessionDurationMillis())
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = panel)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("STATO DISPOSITIVO ED ENERGIA", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                "Rilevazione manuale: non esegue polling continuo e non apre un secondo microfono. I dati CPU e RAM sono stime del processo/dispositivo, non misure di laboratorio.",
                color = Color(0xFF9A9DA4), fontSize = 11.sp
            )
            Button(
                onClick = { snapshot = captureSnapshot() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
            ) { Text("Acquisisci rilevazione") }

            Text("Servizio vocale: ${if (serviceBound) serviceLifecycle else "non collegato all'interfaccia"}", color = Color.White, fontSize = 13.sp)
            Text("Gemini Live: $liveState", color = Color(0xFFB9BBC1), fontSize = 12.sp)
            Text("Wake word: ${if (wakeWordEnabled) "attiva" else "disattivata"} · $wakeWordStatus", color = Color(0xFFB9BBC1), fontSize = 12.sp)
            Text("WakeLock CPU: ${if (wakeLockHeld) "acquisito" else "rilasciato / non attivo"}", color = Color(0xFFB9BBC1), fontSize = 12.sp)

            snapshot?.let { data ->
                Text("Rilevazione: ${data.capturedAt}", color = gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                DiagnosticValue("Batteria", data.battery)
                DiagnosticValue("Temperatura batteria", data.batteryTemperature)
                DiagnosticValue("Alimentazione", data.charging)
                DiagnosticValue("Memoria app", data.appMemory)
                DiagnosticValue("RAM disponibile dispositivo", data.availableMemory)
                DiagnosticValue("CPU processo", data.cpuAverage)
                DiagnosticValue("Stato termico Android", data.thermal)
                DiagnosticValue("Durata sessione Gemini Live", data.liveSessionDuration),
                DiagnosticValue("Registratori audio attivi", data.activeRecorders)
            }
        }
    }
}

private fun formatSessionDuration(durationMillis: Long): String {
    if (durationMillis <= 0L) return "Nessuna sessione Live attiva"
    val totalSeconds = durationMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}

@Composable
private fun DiagnosticValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF9A9DA4), fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
