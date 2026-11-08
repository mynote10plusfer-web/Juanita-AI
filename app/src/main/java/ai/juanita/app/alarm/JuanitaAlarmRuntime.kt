package ai.juanita.app.alarm

import ai.juanita.app.JuanitaVoiceService
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZonedDateTime

/**
 * Schedules every independent alarm using a stable per-alarm PendingIntent.
 * Exact-alarm permission is respected; on Android versions where exact alarms
 * are unavailable, the app uses an inexact idle-allowed fallback and exposes
 * that limitation in the UI rather than claiming exact timing.
 */
object JuanitaAlarmScheduler {
    const val ACTION_FIRE = "ai.juanita.app.alarm.FIRE"
    const val ACTION_SNOOZE_FIRE = "ai.juanita.app.alarm.SNOOZE_FIRE"
    const val ACTION_SNOOZE = "ai.juanita.app.alarm.SNOOZE"
    const val ACTION_DISMISS = "ai.juanita.app.alarm.DISMISS"
    const val EXTRA_ALARM_ID = "alarm_id"
    const val NOTIFICATION_ID = 5201
    private const val REQUEST_BASE = 5200
    private const val SNOOZE_MILLIS = 10 * 60 * 1000L

    fun hasExactAlarmAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return manager.canScheduleExactAlarms()
    }

    fun schedule(context: Context, alarm: JuanitaAlarm): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = alarmPendingIntent(context, alarm.id, false)
        manager.cancel(pending)
        val schedule = alarm.schedule
        if (!schedule.enabled ||
            (schedule.repeatDays.isEmpty() && schedule.oneShotAtEpochMillis == null)
        ) return true

        val next = AlarmScheduleCalculator.nextOccurrence(schedule, ZonedDateTime.now())
        if (next == null) {
            if (schedule.oneShotAtEpochMillis != null) JuanitaAlarmStore(context).consumeOneShot(alarm.id)
            return false
        }
        val triggerAt = next.toInstant().toEpochMilli()
        return runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                val showIntent = PendingIntent.getActivity(
                    context, requestCode(alarm.id, 2),
                    Intent(context, ai.juanita.app.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            true
        }.getOrDefault(false)
    }

    fun cancelLegacyPending(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(PendingIntent.getBroadcast(
            context, 5201,
            Intent(context, JuanitaAlarmReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
        manager.cancel(PendingIntent.getBroadcast(
            context, 5207,
            Intent(context, JuanitaAlarmReceiver::class.java).setAction(ACTION_SNOOZE_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
    }

    fun scheduleAll(context: Context): Boolean {
        cancelLegacyPending(context)
        val alarms = JuanitaAlarmStore(context).all()
        var success = true
        alarms.forEach { alarm ->
            if (!schedule(context, alarm)) success = false
            val snoozeAt = JuanitaAlarmStore(context).snoozeAt(alarm.id)
            if (snoozeAt > System.currentTimeMillis()) {
                val pendingSnooze = alarmPendingIntent(context, alarm.id, true)
                runCatching {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeAt, pendingSnooze)
                    } else {
                        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeAt, pendingSnooze)
                    }
                }.onFailure { success = false }
            } else if (snoozeAt != 0L) {
                JuanitaAlarmStore(context).clearSnooze(alarm.id)
            }
        }
        return success
    }

    fun cancel(context: Context, alarmId: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(alarmPendingIntent(context, alarmId, false))
        manager.cancel(alarmPendingIntent(context, alarmId, true))
        JuanitaAlarmStore(context).clearSnooze(alarmId)
    }

    /** Compatibility helper: cancel all Juanita alarms without touching app data. */
    fun cancel(context: Context) {
        val store = JuanitaAlarmStore(context)
        store.all().forEach { cancel(context, it.id) }
        JuanitaAlarmRingingService.stop(context)
    }

    internal fun onAlarmFired(context: Context, alarmId: String, isSnooze: Boolean = false) {
        val store = JuanitaAlarmStore(context)
        val alarm = store.find(alarmId) ?: return
        if (isSnooze) {
            store.clearSnooze(alarmId)
        } else {
            if (!alarm.schedule.enabled) return
            if (alarm.schedule.oneShotAtEpochMillis != null) {
                store.consumeOneShot(alarmId)
            } else {
                // Re-arm this weekly alarm independently; other alarms are untouched.
                schedule(context, alarm)
            }
        }
        JuanitaAlarmRingingService.start(context, alarmId)
    }

    internal fun snooze(context: Context, alarmId: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + SNOOZE_MILLIS
        val pending = alarmPendingIntent(context, alarmId, true)
        manager.cancel(pending)
        JuanitaAlarmStore(context).setSnooze(alarmId, triggerAt)
        val voiceState = JuanitaVoiceService.state.value
        val liveSessionActive = voiceState.contains("in ascolto", ignoreCase = true) ||
            voiceState.contains("Connessione a Gemini Live", ignoreCase = true)
        if (liveSessionActive) runCatching { JuanitaVoiceService.stop(context) }
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
        JuanitaAlarmRingingService.stop(context)
    }

    internal fun dismiss(context: Context, alarmId: String) {
        // Dismiss only this ringing occurrence. The weekly alarm was already
        // re-armed when it fired and must remain scheduled for its next week.
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(alarmPendingIntent(context, alarmId, true))
        JuanitaAlarmStore(context).clearSnooze(alarmId)
        JuanitaAlarmRingingService.stop(context)
    }

    private fun alarmPendingIntent(context: Context, alarmId: String, snooze: Boolean): PendingIntent {
        val action = if (snooze) ACTION_SNOOZE_FIRE else ACTION_FIRE
        return PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, if (snooze) 1 else 0),
            Intent(context, JuanitaAlarmReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(id: String, suffix: Int): Int =
        (REQUEST_BASE + (id.hashCode() and 0x0fffffff) * 3 + suffix) and 0x7fffffff
}

class JuanitaAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val alarmId = intent?.getStringExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID)
            ?: JuanitaAlarmStore.LEGACY_ID
        when (intent?.action) {
            JuanitaAlarmScheduler.ACTION_FIRE ->
                JuanitaAlarmScheduler.onAlarmFired(context, alarmId)
            JuanitaAlarmScheduler.ACTION_SNOOZE_FIRE ->
                JuanitaAlarmScheduler.onAlarmFired(context, alarmId, isSnooze = true)
            JuanitaAlarmScheduler.ACTION_SNOOZE ->
                JuanitaAlarmScheduler.snooze(context, alarmId)
            JuanitaAlarmScheduler.ACTION_DISMISS ->
                JuanitaAlarmScheduler.dismiss(context, alarmId)
        }
    }
}

class JuanitaAlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )
        ) return

        val store = JuanitaAlarmStore(context)
        JuanitaAlarmScheduler.scheduleAll(context)
        store.all().forEach { alarm ->
            val snoozeAt = store.snoozeAt(alarm.id)
            if (snoozeAt > System.currentTimeMillis()) {
                val snoozeIntent = PendingIntent.getBroadcast(
                    context,
                    5200 + (alarm.id.hashCode() and 0x0fffffff) * 3 + 1,
                    Intent(context, JuanitaAlarmReceiver::class.java)
                        .setAction(JuanitaAlarmScheduler.ACTION_SNOOZE_FIRE)
                        .putExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID, alarm.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                runCatching {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeAt, snoozeIntent)
                    } else {
                        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeAt, snoozeIntent)
                    }
                }
            } else if (snoozeAt != 0L) {
                store.clearSnooze(alarm.id)
            }
        }
    }
}
