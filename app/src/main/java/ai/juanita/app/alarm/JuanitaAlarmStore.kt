package ai.juanita.app.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.util.UUID

/**
 * A user-visible alarm is an independent record. A record can be a one-off
 * date/time or a weekly recurrence at one time on selected days.
 */
data class JuanitaAlarm(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val schedule: AlarmSchedule
)

/** Durable local alarm store with one-time migration from the 0.11.0 single alarm. */
class JuanitaAlarmStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<JuanitaAlarm> {
        val raw = prefs.getString(KEY_ALARMS, null)
        if (raw != null) return decode(raw)
        val legacy = loadLegacy()
        if (legacy != null) {
            val migrated = listOf(JuanitaAlarm(id = LEGACY_ID, label = "Sveglia", schedule = legacy))
            saveAll(migrated)
            return migrated
        }
        return emptyList()
    }

    @Synchronized
    fun saveAll(alarms: List<JuanitaAlarm>): Boolean {
        require(alarms.map { it.id }.distinct().size == alarms.size) { "Gli ID delle sveglie devono essere univoci." }
        val json = JSONArray()
        alarms.forEach { alarm ->
            val schedule = alarm.schedule
            json.put(JSONObject()
                .put("id", alarm.id)
                .put("label", alarm.label.take(80))
                .put("hour", schedule.hour)
                .put("minute", schedule.minute)
                .put("days", JSONArray(schedule.repeatDays.sortedBy { it.value }.map { it.value }))
                .put("oneShotAt", schedule.oneShotAtEpochMillis ?: 0L)
                .put("enabled", schedule.enabled))
        }
        return prefs.edit().putString(KEY_ALARMS, json.toString()).commit()
    }

    fun upsert(alarm: JuanitaAlarm): Boolean {
        val next = all().filterNot { it.id == alarm.id } + alarm
        return saveAll(next)
    }

    fun find(id: String): JuanitaAlarm? = all().firstOrNull { it.id == id }

    fun delete(id: String): Boolean = saveAll(all().filterNot { it.id == id })

    fun setEnabled(id: String, enabled: Boolean): JuanitaAlarm? {
        val current = find(id) ?: return null
        val updated = current.copy(schedule = current.schedule.copy(enabled = enabled))
        return updated.takeIf { upsert(it) }
    }

    fun consumeOneShot(id: String) {
        val current = find(id) ?: return
        if (current.schedule.oneShotAtEpochMillis != null) {
            upsert(current.copy(schedule = current.schedule.copy(enabled = false)))
        }
    }

    fun setSnooze(id: String, atMillis: Long) {
        prefs.edit().putLong(SNOOZE_PREFIX + id, atMillis).commit()
    }

    fun snoozeAt(id: String): Long = prefs.getLong(SNOOZE_PREFIX + id, 0L)

    fun clearSnooze(id: String) {
        prefs.edit().remove(SNOOZE_PREFIX + id).commit()
    }

    private fun decode(raw: String): List<JuanitaAlarm> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val daysJson = item.optJSONArray("days") ?: JSONArray()
                val days = buildSet {
                    for (dayIndex in 0 until daysJson.length()) {
                        DayOfWeek.entries.firstOrNull { it.value == daysJson.optInt(dayIndex) }?.let(::add)
                    }
                }
                val oneShot = item.optLong("oneShotAt", 0L).takeIf { it > 0L }
                val schedule = AlarmSchedule(
                    hour = item.getInt("hour"),
                    minute = item.getInt("minute"),
                    repeatDays = days,
                    oneShotAtEpochMillis = oneShot,
                    enabled = item.optBoolean("enabled", false)
                )
                add(JuanitaAlarm(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    label = item.optString("label"),
                    schedule = schedule
                ))
            }
        }
    }.getOrElse { emptyList() }

    private fun loadLegacy(): AlarmSchedule? {
        if (!prefs.getBoolean("configured", false)) return null
        val days = prefs.getString("days", "").orEmpty().split(",")
            .mapNotNull { it.toIntOrNull() }
            .mapNotNull { value -> DayOfWeek.entries.firstOrNull { it.value == value } }
            .toSet()
        return runCatching {
            AlarmSchedule(
                hour = prefs.getInt("hour", 7),
                minute = prefs.getInt("minute", 0),
                repeatDays = days,
                oneShotAtEpochMillis = prefs.getLong("one_shot_at_epoch_millis", 0L).takeIf { it > 0L },
                enabled = prefs.getBoolean("enabled", false)
            )
        }.getOrNull()
    }

    companion object {
        private const val PREFS = "juanita_alarm"
        private const val KEY_ALARMS = "alarms_v2"
        private const val SNOOZE_PREFIX = "snooze_at_"
        const val LEGACY_ID = "legacy-single-alarm"
    }
}
