package ai.juanita.app.alarm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure alarm recurrence definition. Persistence and Android scheduling are kept
 * outside this type so recurrence can be tested without touching AlarmManager.
 *
 * Exactly one mode is intended:
 * - oneShotAtEpochMillis for a one-time alarm; or
 * - repeatDays + hour/minute for a weekly alarm.
 */
data class AlarmSchedule(
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val oneShotAtEpochMillis: Long? = null,
    val enabled: Boolean = true
) {
    init {
        require(hour in 0..23) { "hour must be between 0 and 23" }
        require(minute in 0..59) { "minute must be between 0 and 59" }
        require(repeatDays.all { it.value in 1..7 }) { "invalid repeat day" }
        require(oneShotAtEpochMillis == null || repeatDays.isEmpty()) {
            "one-time and weekly recurrence cannot be combined"
        }
    }
}

/**
 * Computes the next wall-clock occurrence in the supplied device time zone.
 *
 * LocalDateTime.atZone applies the platform's documented zone rules for DST:
 * nonexistent local times move forward through the gap; ambiguous times use
 * the earlier valid offset. Callers persist the schedule, not this result,
 * and recompute after reboot/time-zone changes.
 */
object AlarmScheduleCalculator {
    fun nextOccurrence(
        schedule: AlarmSchedule,
        now: ZonedDateTime,
        zoneId: ZoneId = now.zone
    ): ZonedDateTime? {
        if (!schedule.enabled) return null

        schedule.oneShotAtEpochMillis?.let { epochMillis ->
            val trigger = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
            return trigger.takeIf { it.isAfter(now.withZoneSameInstant(zoneId)) }
        }

        if (schedule.repeatDays.isEmpty()) return null
        val localNow = now.withZoneSameInstant(zoneId)
        val alarmTime = LocalTime.of(schedule.hour, schedule.minute)

        for (daysAhead in 0L..7L) {
            val date = localNow.toLocalDate().plusDays(daysAhead)
            if (date.dayOfWeek !in schedule.repeatDays) continue
            val candidate = date.atTime(alarmTime).atZone(zoneId)
            if (candidate.isAfter(localNow)) return candidate
        }
        return null
    }
}
