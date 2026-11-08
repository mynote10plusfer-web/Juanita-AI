package ai.juanita.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.DayOfWeek
import java.time.ZonedDateTime

/** Stores only the user's alarm settings locally on this device. */
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

    fun saveSnooze(epochMillis: Long) { prefs.edit().putLong(KEY_SNOOZE, epochMillis).apply() }
    fun loadSnooze(): Long = prefs.getLong(KEY_SNOOZE, 0L)
    fun clearSnooze() { prefs.edit().remove(KEY_SNOOZE).apply() }

    companion object {
        private const val KEY_CONFIGURED = "configured"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
        private const val KEY_DAYS = "days"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SNOOZE = "snooze_at"
    }
}

object JuanitaAlarmScheduler {
    const val ACTION_FIRE = "ai.juanita.app.alarm.FIRE"
    const val ACTION_SNOOZE_FIRE = "ai.juanita.app.alarm.SNOOZE_FIRE"
    const val ACTION_SNOOZE = "ai.juanita.app.alarm.SNOOZE"
    const val ACTION_DISMISS = "ai.juanita.app.alarm.DISMISS"
    const val NOTIFICATION_ID = 5201
    private const val ALARM_REQUEST = 5201
    private const val SNOOZE_REQUEST = 5207

    fun schedule(context: Context, schedule: AlarmSchedule): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        AlarmPreferences(context).save(schedule)
        val pending = alarmPendingIntent(context)
        manager.cancel(pending)
        if (!schedule.enabled || schedule.repeatDays.isEmpty()) return true

        val next = AlarmScheduleCalculator.nextOccurrence(schedule, ZonedDateTime.now()) ?: return false
        return runCatching {
            val triggerAt = next.toInstant().toEpochMilli()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                val showIntent = PendingIntent.getActivity(
                    context, 5202, Intent(context, ai.juanita.app.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pending)
            } else {
                // Android may defer this fallback; the settings screen explains the limitation.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            true
        }.getOrDefault(false)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(alarmPendingIntent(context))
        manager.cancel(snoozePendingIntent(context))
        AlarmPreferences(context).disable()
        JuanitaAlarmRingingService.stop(context)
    }

    internal fun onAlarmFired(context: Context, isSnooze: Boolean = false) {
        if (isSnooze) AlarmPreferences(context).clearSnooze()
        if (!isSnooze) {
            val schedule = AlarmPreferences(context).load()
            if (schedule == null || !schedule.enabled) return
            // Keep the next weekly occurrence armed before ringing this one.
            schedule(context, schedule)
        }
        JuanitaAlarmRingingService.start(context)
    }

    internal fun snooze(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = snoozePendingIntent(context)
        manager.cancel(pending)
        val triggerAt = System.currentTimeMillis() + SNOOZE_MILLIS
        AlarmPreferences(context).saveSnooze(triggerAt)
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
        JuanitaAlarmRingingService.stop(context)
    }

    private fun alarmPendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, ALARM_REQUEST,
        Intent(context, JuanitaAlarmReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun snoozePendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, SNOOZE_REQUEST,
        Intent(context, JuanitaAlarmReceiver::class.java).setAction(ACTION_SNOOZE_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private const val SNOOZE_MILLIS = 10 * 60 * 1000L
}

class JuanitaAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            JuanitaAlarmScheduler.ACTION_FIRE -> JuanitaAlarmScheduler.onAlarmFired(context)
            JuanitaAlarmScheduler.ACTION_SNOOZE_FIRE -> JuanitaAlarmScheduler.onAlarmFired(context, isSnooze = true)
            JuanitaAlarmScheduler.ACTION_SNOOZE -> JuanitaAlarmScheduler.snooze(context)
            JuanitaAlarmScheduler.ACTION_DISMISS -> {
                val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                manager.cancel(
                    PendingIntent.getBroadcast(
                        context, 5207,
                        Intent(context, JuanitaAlarmReceiver::class.java).setAction(JuanitaAlarmScheduler.ACTION_SNOOZE_FIRE),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                AlarmPreferences(context).clearSnooze()
                JuanitaAlarmRingingService.stop(context)
            }
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
        val snoozeAt = AlarmPreferences(context).loadSnooze()
        if (snoozeAt > System.currentTimeMillis()) {
            val snoozeIntent = PendingIntent.getBroadcast(
                context, 5207,
                Intent(context, JuanitaAlarmReceiver::class.java).setAction(JuanitaAlarmScheduler.ACTION_SNOOZE_FIRE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            runCatching {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeAt, snoozeIntent)
                } else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeAt, snoozeIntent)
            }
        } else if (snoozeAt != 0L) AlarmPreferences(context).clearSnooze()
    }
}
