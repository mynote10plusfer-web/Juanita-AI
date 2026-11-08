package ai.juanita.app.alarm

import android.content.Context
import android.media.AudioManager
import android.app.NotificationManager

/**
 * Temporarily raises only STREAM_ALARM when it is below the configured safe
 * threshold. The original level is stored locally and restored on dismissal,
 * snooze, service shutdown, or next app launch if the service was killed.
 */
object AlarmVolumeController {
    private const val PREFS = "juanita_alarm"
    private const val KEY_ADJUSTED = "alarm_volume_adjusted"
    private const val KEY_ORIGINAL = "alarm_volume_original"
    private const val TARGET_PERCENT = 70

    fun ensureAudible(context: Context): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return runCatching {
            val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            if (maximum <= 0) return "Volume sveglia non regolabile su questo dispositivo."
            val target = ((maximum * TARGET_PERCENT) / 100.0).toInt().coerceIn(1, maximum)
            if (current >= target) return "Volume sveglia già adeguato."
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_ADJUSTED, false)) {
                prefs.edit().putInt(KEY_ORIGINAL, current).putBoolean(KEY_ADJUSTED, true).commit()
            }
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            val actual = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            if (actual >= target) "Volume sveglia aumentato temporaneamente al livello consigliato."
            else "Android o il dispositivo non hanno consentito di aumentare il volume della sveglia."
        }.getOrElse {
            "Non ho potuto regolare il volume della sveglia: verifica il volume allarme e Non disturbare."
        }
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ADJUSTED, false)) return
        val original = prefs.getInt(KEY_ORIGINAL, -1)
        runCatching {
            if (original >= 0) {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    original.coerceIn(0, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)),
                    0
                )
            }
        }
        prefs.edit().remove(KEY_ORIGINAL).putBoolean(KEY_ADJUSTED, false).commit()
    }

    fun restoreIfNoActiveAlarm(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ADJUSTED, false)) return
        val active = runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.any { it.id == JuanitaAlarmScheduler.NOTIFICATION_ID }
        }.getOrDefault(false)
        if (!active) restore(context)
    }
}
