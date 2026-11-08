package ai.juanita.app.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ai.juanita.app.MainActivity
import ai.juanita.app.R
import java.time.DayOfWeek
import java.time.ZonedDateTime

/**
 * Local-only weekly alarm. The only persisted data is the user's chosen time,
 * repeat days and enabled flag; no network service is involved.
 */
class AlarmPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("juanita_alarm", Context.MODE_PRIVATE)

    fun load(): AlarmSchedule? {
        if (!prefs.getBoolean(KEY_CONFIGURED, false)) return null
        val days = prefs.getString(KEY_DAYS, "").orEmpty()
            .split(",").mapNotNull { it.toIntOrNull() }
            .mapNotNull { value -> DayOfWeek.entries.firstOrNull { it.value == value } }
            .toSet()
        return runCatching {
            AlarmSchedule(
                hour = prefs.getInt(KEY_HOUR, 7),
                minute = prefs.getInt(KEY_MINUTE, 0),
                repeatDays = days,
                enabled = prefs.getBoolean(KEY_ENABLED, false)
            )
        }.getOrNull()
    }

    fun save(schedule: AlarmSchedule) {
        prefs.edit()
            .putBoolean(KEY_CONFIGURED, true)
            .putInt(KEY_HOUR, schedule.hour)
            .putInt(KEY_MINUTE, schedule.minute)
            .putString(KEY_DAYS, schedule.repeatDays.sortedBy { it.value }.joinToString(",") { it.value.toString() })
            .putBoolean(KEY_ENABLED, schedule.enabled)
            .apply()
    }

    fun disable() {
        load()?.let { save(it.copy(enabled = false)) }
    }

    companion object {
        private const val KEY_CONFIGURED = "configured"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
        private const val KEY_DAYS = "days"
        private const val KEY_ENABLED = "enabled"
    }
}

object JuanitaAlarmScheduler {
    const val ACTION_FIRE = "ai.juanita.app.alarm.FIRE"
    const val ACTION_DISMISS = "ai.juanita.app.alarm.DISMISS"
    const val NOTIFICATION_ID = 5201
    private const val CHANNEL_ID = "juanita_alarm"

    fun schedule(context: Context, schedule: AlarmSchedule): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = AlarmPreferences(context)
        prefs.save(schedule)
        val pending = alarmPendingIntent(context)
        manager.cancel(pending)
        if (!schedule.enabled || schedule.repeatDays.isEmpty()) return true

        val now = ZonedDateTime.now()
        val next = AlarmScheduleCalculator.nextOccurrence(schedule, now) ?: return false
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        return runCatching {
            if (exactAllowed) {
                val showIntent = PendingIntent.getActivity(
                    context, 5202, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(next.toInstant().toEpochMilli(), showIntent),
                    pending
                )
            } else {
                // Fallback remains functional but Android may defer it. The UI
                // explicitly tells the user that exact-alarm access is required.
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.toInstant().toEpochMilli(),
                    pending
                )
            }
            true
        }.getOrDefault(false)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(alarmPendingIntent(context))
        AlarmPreferences(context).disable()
    }

    internal fun onAlarmFired(context: Context) {
        val schedule = AlarmPreferences(context).load()
        if (schedule == null || !schedule.enabled) return
        // Schedule the next weekly occurrence before displaying the current one.
        schedule(context, schedule)
        showAlarmNotification(context)
    }

    private fun alarmPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 5201,
            Intent(context, JuanitaAlarmReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun showAlarmNotification(context: Context) {
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 5203, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getBroadcast(
            context, 5204,
            Intent(context, JuanitaAlarmReceiver::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Buongiorno")
            .setContentText("È ora di svegliarsi. Juanita ti aspetta.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Spegni", dismiss)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Sveglia Juanita", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avviso sonoro della sveglia locale di Juanita."
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

class JuanitaAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            JuanitaAlarmScheduler.ACTION_FIRE -> JuanitaAlarmScheduler.onAlarmFired(context)
            JuanitaAlarmScheduler.ACTION_DISMISS -> NotificationManagerCompat.from(context)
                .cancel(JuanitaAlarmScheduler.NOTIFICATION_ID)
        }
    }
}

class JuanitaAlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )
        ) return
        val schedule = AlarmPreferences(context).load() ?: return
        if (schedule.enabled) JuanitaAlarmScheduler.schedule(context, schedule)
    }
}
