package ai.juanita.app.alarm

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmScheduleCalculatorTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun weeklyAlarmUsesNextMatchingDay() {
        val now = ZonedDateTime.of(2026, 11, 8, 12, 0, 0, 0, madrid) // Sunday
        val schedule = AlarmSchedule(hour = 8, minute = 15, repeatDays = setOf(DayOfWeek.MONDAY))
        val next = AlarmScheduleCalculator.nextOccurrence(schedule, now)
        assertEquals(ZonedDateTime.of(2026, 11, 9, 8, 15, 0, 0, madrid), next)
    }

    @Test
    fun weeklyAlarmDoesNotFireAgainWhenTodaysTimeHasPassed() {
        val now = ZonedDateTime.of(2026, 11, 8, 9, 0, 0, 0, madrid) // Sunday
        val schedule = AlarmSchedule(hour = 8, minute = 0, repeatDays = setOf(DayOfWeek.SUNDAY))
        val next = AlarmScheduleCalculator.nextOccurrence(schedule, now)
        assertEquals(ZonedDateTime.of(2026, 11, 15, 8, 0, 0, 0, madrid), next)
    }

    @Test
    fun oneShotAlarmReturnsOnlyAFutureInstant() {
        val now = ZonedDateTime.of(2026, 11, 8, 12, 0, 0, 0, madrid)
        val future = now.plusHours(2)
        val schedule = AlarmSchedule(
            hour = 14,
            minute = 0,
            oneShotAtEpochMillis = future.toInstant().toEpochMilli()
        )
        assertEquals(future, AlarmScheduleCalculator.nextOccurrence(schedule, now))
        val expired = schedule.copy(oneShotAtEpochMillis = now.minusMinutes(1).toInstant().toEpochMilli())
        assertNull(AlarmScheduleCalculator.nextOccurrence(expired, now))
    }

    @Test
    fun disabledAlarmHasNoOccurrence() {
        val now = ZonedDateTime.of(2026, 11, 8, 12, 0, 0, 0, madrid)
        val schedule = AlarmSchedule(8, 0, repeatDays = setOf(DayOfWeek.MONDAY), enabled = false)
        assertNull(AlarmScheduleCalculator.nextOccurrence(schedule, now))
    }

    @Test
    fun springDstGapMovesToFirstValidLocalTime() {
        val beforeGap = ZonedDateTime.of(2026, 3, 28, 23, 0, 0, 0, madrid)
        val schedule = AlarmSchedule(2, 30, repeatDays = setOf(DayOfWeek.SUNDAY))
        val next = AlarmScheduleCalculator.nextOccurrence(schedule, beforeGap)
        assertEquals(ZonedDateTime.of(2026, 3, 29, 3, 30, 0, 0, madrid), next)
    }

    @Test
    fun oneShotKeepsTheAbsoluteInstantAcrossZones() {
        val nowMadrid = ZonedDateTime.of(2026, 11, 8, 12, 0, 0, 0, madrid)
        val instant = Instant.parse("2026-11-08T12:30:00Z")
        val schedule = AlarmSchedule(13, 30, oneShotAtEpochMillis = instant.toEpochMilli())
        val next = AlarmScheduleCalculator.nextOccurrence(schedule, nowMadrid, ZoneId.of("UTC"))
        assertTrue(next!!.toInstant() == instant)
    }
}
