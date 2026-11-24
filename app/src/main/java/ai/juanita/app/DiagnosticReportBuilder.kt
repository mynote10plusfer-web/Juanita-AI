package ai.juanita.app

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
import ai.juanita.app.diagnostics.VoiceDiagnosticLog
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a single, on-demand diagnostic export. No polling, network calls,
 * identifiers, location, transcripts, prompts, audio, or persona data.
 */
internal object DiagnosticReportBuilder {
    fun build(
        context: Context,
        liveState: String,
        wakeWordEnabled: Boolean,
        wakeWordStatus: String,
        serviceBound: Boolean,
        serviceLifecycle: String,
        wakeLockHeld: Boolean,
        liveSessionDurationMillis: Long
    ): String {
        val app = context.applicationContext
        val battery = runCatching {
            app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
        val batteryPercent = if (level >= 0) "${(level * 100f / scale).toInt()}%" else "Non disponibile"
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val batteryTemp = if (temp >= 0) String.format(Locale.ROOT, "%.1f °C", temp / 10f) else "Non disponibile"
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = when {
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL -> "In carica / piena"
            plugged != 0 -> "Collegata"
            status == BatteryManager.BATTERY_STATUS_DISCHARGING -> "A batteria"
            else -> "Sconosciuto"
        }

        val memory = runCatching {
            val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            val available = android.app.ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
            "App PSS: ${String.format(Locale.ROOT, "%.1f", info.totalPss / 1024f)} MB; RAM disponibile: ${String.format(Locale.ROOT, "%.0f", available.availMem / (1024f * 1024f))} MB"
        }.getOrDefault("Non disponibile")

        val elapsed = (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()).coerceAtLeast(1L)
        val cpu = (Process.getElapsedCpuTime().coerceAtLeast(0L) * 100.0 / elapsed).coerceAtLeast(0.0)
        val thermal = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
                when (power.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE -> "Normale"
                    PowerManager.THERMAL_STATUS_LIGHT -> "Leggera"
                    PowerManager.THERMAL_STATUS_MODERATE -> "Moderata"
                    PowerManager.THERMAL_STATUS_SEVERE -> "Severa"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "Critica"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergenza"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "Arresto termico"
                    else -> "Sconosciuta"
                }
            } else "Non esposto da Android"
        }.getOrDefault("Non disponibile")
        val recorders = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).activeRecordingConfigurations.size.toString()
            } else "Non supportato"
        }.getOrDefault("Non disponibile")

        val duration = formatDuration(liveSessionDurationMillis)
        val device = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.distinct().joinToString(" ").take(100)
        val voiceLog = VoiceDiagnosticLog(app).exportText()
        return compose(
            generatedAt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date()),
            appId = BuildConfig.APPLICATION_ID,
            appVersion = BuildConfig.VERSION_NAME,
            appBuild = BuildConfig.VERSION_CODE.toString(),
            device = device.ifBlank { "Non disponibile" },
            androidVersion = Build.VERSION.RELEASE ?: "Non disponibile",
            androidApi = Build.VERSION.SDK_INT.toString(),
            battery = batteryPercent,
            batteryTemperature = batteryTemp,
            charging = charging,
            memory = memory,
            cpuAverage = String.format(Locale.ROOT, "%.1f%% dall'avvio del processo (stima)", cpu),
            thermal = thermal,
            activeRecorders = recorders,
            liveState = liveState,
            wakeWord = "${if (wakeWordEnabled) "Attiva" else "Disattivata"} · $wakeWordStatus",
            service = if (serviceBound) serviceLifecycle else "Non collegato all'interfaccia",
            wakeLock = if (wakeLockHeld) "Acquisito" else "Non attivo / rilasciato",
            liveDuration = duration,
            voiceLog = voiceLog
        )
    }

    internal fun compose(
        generatedAt: String, appId: String, appVersion: String, appBuild: String,
        device: String, androidVersion: String, androidApi: String, battery: String,
        batteryTemperature: String, charging: String, memory: String, cpuAverage: String,
        thermal: String, activeRecorders: String, liveState: String, wakeWord: String,
        service: String, wakeLock: String, liveDuration: String, voiceLog: String
    ): String = buildString {
        appendLine("JUANITA AI — RAPPORTO DIAGNOSTICO COMPLETO")
        appendLine("Generato: $generatedAt")
        appendLine()
        appendLine("[APP]")
        appendLine("Application ID: $appId")
        appendLine("Versione: $appVersion (build $appBuild)")
        appendLine()
        appendLine("[DISPOSITIVO]")
        appendLine("Modello: $device")
        appendLine("Android: $androidVersion (API $androidApi)")
        appendLine()
        appendLine("[STATO ED ENERGIA — ISTANTANEA SU RICHIESTA]")
        appendLine("Batteria: $battery")
        appendLine("Temperatura batteria: $batteryTemperature")
        appendLine("Alimentazione: $charging")
        appendLine("Memoria: $memory")
        appendLine("CPU processo: $cpuAverage")
        appendLine("Stato termico Android: $thermal")
        appendLine("Registratori audio attivi sul dispositivo: $activeRecorders")
        appendLine()
        appendLine("[STATO VOCALE AL MOMENTO DELL'ESPORTAZIONE]")
        appendLine("Gemini Live: $liveState")
        appendLine("Wake word: $wakeWord")
        appendLine("Servizio: $service")
        appendLine("WakeLock CPU: $wakeLock")
        appendLine("Durata sessione Live: $liveDuration")
        appendLine()
        appendLine("[EVENTI VOCALI REGISTRATI]")
        appendLine(voiceLog)
        appendLine()
        appendLine("[PRIVACY E LIMITI]")
        appendLine("Rapporto locale generato solo su richiesta. Nessun caricamento di rete.")
        appendLine("Non include intenzionalmente conversazioni, prompt, audio, memoria personale, contatti, posizione o identificativi del dispositivo.")
        appendLine("CPU e RAM sono stime/istantanee Android, non misure dirette di consumo elettrico in mAh.")
    }

    private fun formatDuration(milliseconds: Long): String {
        if (milliseconds <= 0L) return "Nessuna sessione Live attiva"
        val seconds = milliseconds / 1_000L
        val hours = seconds / 3_600L
        val minutes = (seconds % 3_600L) / 60L
        val remainder = seconds % 60L
        return if (hours > 0) String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder)
        else String.format(Locale.ROOT, "%02d:%02d", minutes, remainder)
    }
}
