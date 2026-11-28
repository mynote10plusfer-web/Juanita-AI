package ai.juanita.app.alarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.DayOfWeek
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JuanitaAlarmStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("juanita_alarm", 0)

    @Before
    fun clearBefore() {
        prefs.edit().clear().commit()
    }

    @After
    fun clearAfter() {
        prefs.edit().clear().commit()
    }

    @Test
    fun storesDifferentTimesForDifferentWeekdaysAsIndependentAlarms() {
        val store = JuanitaAlarmStore(context)
        val monday = JuanitaAlarm(
            id = "monday-0700",
            label = "Lavoro",
            schedule = AlarmSchedule(7, 0, setOf(DayOfWeek.MONDAY))
        )
        val tuesday = JuanitaAlarm(
            id = "tuesday-0730",
            label = "Appuntamento",
            schedule = AlarmSchedule(7, 30, setOf(DayOfWeek.TUESDAY))
        )

        assertTrue(store.upsert(monday))
        assertTrue(store.upsert(tuesday))

        val alarms = store.all().associateBy { it.id }
        assertEquals(2, alarms.size)
        assertEquals(7, alarms.getValue(monday.id).schedule.hour)
        assertEquals(0, alarms.getValue(monday.id).schedule.minute)
        assertEquals(setOf(DayOfWeek.MONDAY), alarms.getValue(monday.id).schedule.repeatDays)
        assertEquals(7, alarms.getValue(tuesday.id).schedule.hour)
        assertEquals(30, alarms.getValue(tuesday.id).schedule.minute)
        assertEquals(setOf(DayOfWeek.TUESDAY), alarms.getValue(tuesday.id).schedule.repeatDays)
    }

    @Test
    fun disablingOrDeletingOneAlarmDoesNotChangeAnother() {
        val store = JuanitaAlarmStore(context)
        val first = JuanitaAlarm(id = "first", schedule = AlarmSchedule(6, 45, setOf(DayOfWeek.MONDAY)))
        val second = JuanitaAlarm(id = "second", schedule = AlarmSchedule(8, 15, setOf(DayOfWeek.FRIDAY)))
        assertTrue(store.upsert(first))
        assertTrue(store.upsert(second))

        assertNotNull(store.setEnabled(first.id, false))
        assertTrue(store.delete(first.id))

        val remaining = store.all()
        assertEquals(1, remaining.size)
        assertEquals(second.id, remaining.single().id)
        assertTrue(remaining.single().schedule.enabled)
        assertEquals(8, remaining.single().schedule.hour)
        assertEquals(15, remaining.single().schedule.minute)
    }

    @Test
    fun snoozeStateIsIsolatedPerAlarm() {
        val store = JuanitaAlarmStore(context)
        store.setSnooze("first", 1_800_000_000_000L)
        store.setSnooze("second", 1_800_000_060_000L)

        store.clearSnooze("first")

        assertEquals(0L, store.snoozeAt("first"))
        assertEquals(1_800_000_060_000L, store.snoozeAt("second"))
    }

    @Test
    fun consumingOneShotDisablesOnlyThatAlarm() {
        val store = JuanitaAlarmStore(context)
        val first = JuanitaAlarm(
            id = "one-shot-a",
            schedule = AlarmSchedule(7, 0, emptySet(), System.currentTimeMillis() + 60_000L)
        )
        val second = JuanitaAlarm(
            id = "one-shot-b",
            schedule = AlarmSchedule(7, 30, emptySet(), System.currentTimeMillis() + 120_000L)
        )
        assertTrue(store.upsert(first))
        assertTrue(store.upsert(second))

        store.consumeOneShot(first.id)

        assertFalse(store.find(first.id)!!.schedule.enabled)
        assertTrue(store.find(second.id)!!.schedule.enabled)
    }

    @Test
    fun migratesLegacySnoozeAlongsideLegacyAlarm() {
        val legacySnooze = System.currentTimeMillis() + 300_000L
        prefs.edit()
            .putBoolean("configured", true)
            .putInt("hour", 7)
            .putInt("minute", 0)
            .putString("days", "1")
            .putBoolean("enabled", true)
            .putLong("snooze_at", legacySnooze)
            .commit()

        val store = JuanitaAlarmStore(context)
        store.all()

        assertEquals(legacySnooze, store.snoozeAt(JuanitaAlarmStore.LEGACY_ID))
        assertEquals(0L, prefs.getLong("snooze_at", 0L))
    }

    @Test
    fun migratesExistingSingleAlarmWithoutLosingItsSettings() {
        prefs.edit()
            .putBoolean("configured", true)
            .putInt("hour", 6)
            .putInt("minute", 20)
            .putString("days", "1,3,5")
            .putBoolean("enabled", true)
            .putLong("one_shot_at_epoch_millis", 0L)
            .commit()

        val migrated = JuanitaAlarmStore(context).all()
        assertEquals(1, migrated.size)
        assertEquals(JuanitaAlarmStore.LEGACY_ID, migrated.single().id)
        assertEquals(6, migrated.single().schedule.hour)
        assertEquals(20, migrated.single().schedule.minute)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), migrated.single().schedule.repeatDays)
        assertTrue(migrated.single().schedule.enabled)
        assertFalse(prefs.getString("alarms_v2", null).isNullOrBlank())
    }
}
