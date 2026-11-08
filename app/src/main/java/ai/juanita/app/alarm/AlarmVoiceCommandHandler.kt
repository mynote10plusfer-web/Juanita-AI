package ai.juanita.app.alarm

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Executes only structured alarm actions explicitly requested through Gemini
 * Live function calls. The model must clarify missing details and obtain
 * confirmation before destructive operations.
 */
class AlarmVoiceCommandHandler(private val context: Context) {
    private val store = JuanitaAlarmStore(context.applicationContext)

    init {
        JuanitaAlarmScheduler.cancelLegacyPending(context.applicationContext)
    }

    fun execute(name: String, args: Map<String, String>): String = when (name) {
        "createAlarm" -> create(args)
        "listAlarms" -> list()
        "openDeviceAlarms" -> openDeviceAlarms()
        "updateAlarm" -> update(args)
        "setAlarmEnabled" -> setEnabled(args)
        "deleteAlarm" -> delete(args)
        else -> "Comando sveglia non riconosciuto."
    }

    private fun parseDay(raw: String): DayOfWeek? = when (raw.trim().uppercase(Locale.ROOT)) {
        "MONDAY", "LUNEDI", "LUNEDÌ", "LUN" -> DayOfWeek.MONDAY
        "TUESDAY", "MARTEDI", "MARTEDÌ", "MAR" -> DayOfWeek.TUESDAY
        "WEDNESDAY", "MERCOLEDI", "MERCOLEDÌ", "MER" -> DayOfWeek.WEDNESDAY
        "THURSDAY", "GIOVEDI", "GIOVEDÌ", "GIO" -> DayOfWeek.THURSDAY
        "FRIDAY", "VENERDI", "VENERDÌ", "VEN" -> DayOfWeek.FRIDAY
        "SATURDAY", "SABATO", "SAB" -> DayOfWeek.SATURDAY
        "SUNDAY", "DOMENICA", "DOM" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun create(args: Map<String, String>): String {
        val timeRaw = args["time"].orEmpty().trim()
        val time = runCatching { LocalTime.parse(timeRaw, DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)) }.getOrNull()
            ?: return "Orario non valido. Specifica l'ora nel formato 24 ore, per esempio 07:30."
        val dateRaw = args["date"].orEmpty().trim()
        val daysRaw = args["repeat_days"].orEmpty().trim()
        if (dateRaw.isNotBlank() && daysRaw.isNotBlank()) {
            return "Scegli una data singola oppure i giorni di ripetizione, non entrambi."
        }
        val date = if (dateRaw.isNotBlank()) {
            runCatching { LocalDate.parse(dateRaw) }.getOrNull()
                ?: return "Data non valida. Usa il formato anno-mese-giorno."
        } else null
        val days = if (daysRaw.isNotBlank()) {
            val parsed = daysRaw.split(",").mapNotNull { raw ->
                parseDay(raw)
            }.toSet()
            if (parsed.isEmpty() || parsed.size != daysRaw.split(",").size) {
                return "Giorni non riconosciuti. Specifica i giorni della settimana in modo chiaro."
            }
            parsed
        } else emptySet()
        if (date == null && days.isEmpty()) {
            return "Per creare la sveglia mi serve una data precisa oppure sapere in quali giorni deve ripetersi."
        }
        val oneShot = date?.atTime(time)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        if (oneShot != null && oneShot <= System.currentTimeMillis()) {
            return "La data e l'ora indicate sono già passate. Chiedi all'utente un momento futuro."
        }
        val alarm = JuanitaAlarm(
            id = UUID.randomUUID().toString(),
            label = args["label"].orEmpty().trim().take(80),
            schedule = AlarmSchedule(
                hour = time.hour,
                minute = time.minute,
                repeatDays = days,
                oneShotAtEpochMillis = oneShot,
                enabled = true
            )
        )
        if (!store.upsert(alarm)) return "Non sono riuscita a salvare la sveglia nella memoria locale."
        if (!JuanitaAlarmScheduler.schedule(context, alarm)) {
            store.delete(alarm.id)
            return "La sveglia non è stata programmata da Android. Controlla l'accesso a Sveglie e promemoria."
        }
        return "Sveglia salvata e programmata. ID: ${alarm.id}. ${describe(alarm)}" + timingNote()
    }

    private fun openDeviceAlarms(): String {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null) {
            runCatching {
                context.startActivity(intent)
                "Ho aperto l'app Orologio del telefono. Android non consente alle app di terze parti di leggere direttamente l'elenco privato delle sveglie Samsung."
            }.getOrElse { "Non sono riuscita ad aprire l'app Orologio: ${it.localizedMessage ?: "errore"}" }
        } else {
            "Non è disponibile un'app Orologio compatibile sul dispositivo."
        }
    }

    private fun list(): String {
        val alarms = store.all().sortedWith(compareBy<JuanitaAlarm> { it.schedule.hour }.thenBy { it.schedule.minute })
        if (alarms.isEmpty()) return "Non ci sono sveglie salvate."
        return alarms.joinToString("\n") { alarm ->
            "ID ${alarm.id} — ${if (alarm.schedule.enabled) "attiva" else "disattivata"} — ${describe(alarm)}"
        }.take(3000)
    }

    private fun update(args: Map<String, String>): String {
        val id = args["alarm_id"].orEmpty().trim()
        if (id.isBlank()) return "ID sveglia mancante. Prima elenca le sveglie."
        val current = store.find(id) ?: return "Non trovo una sveglia con questo ID."
        val old = current.schedule
        val timeRaw = args["time"]?.trim().orEmpty()
        val parsedTime = if (timeRaw.isBlank()) null else
            runCatching { LocalTime.parse(timeRaw, DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)) }.getOrNull()
                ?: return "Orario non valido. Usa HH:mm."
        val dateRaw = args["date"]?.trim().orEmpty()
        val daysRaw = args["repeat_days"]?.trim().orEmpty()
        if (dateRaw.isNotBlank() && daysRaw.isNotBlank()) return "Scegli una data singola oppure i giorni di ripetizione."
        val date = if (dateRaw.isNotBlank()) runCatching { LocalDate.parse(dateRaw) }.getOrNull()
            ?: return "Data non valida. Usa YYYY-MM-DD." else null
        val days = if (daysRaw.isNotBlank()) {
            val parsed = daysRaw.split(",").mapNotNull { raw ->
                runCatching { DayOfWeek.valueOf(raw.trim().uppercase(Locale.ROOT)) }.getOrNull()
            }.toSet()
            if (parsed.isEmpty() || parsed.size != daysRaw.split(",").size) return "Giorni di ripetizione non validi."
            parsed
        } else null
        val hour = parsedTime?.hour ?: old.hour
        val minute = parsedTime?.minute ?: old.minute
        val oneShot = when {
            date != null -> date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            days != null -> null
            old.oneShotAtEpochMillis != null && parsedTime != null -> {
                val existingDate = java.time.Instant.ofEpochMilli(old.oneShotAtEpochMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                existingDate.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            else -> old.oneShotAtEpochMillis
        }
        val repeatDays = days ?: if (date != null) emptySet() else old.repeatDays
        if (oneShot == null && repeatDays.isEmpty()) return "La sveglia deve avere una data singola o almeno un giorno di ripetizione."
        if (oneShot != null && oneShot <= System.currentTimeMillis()) return "La nuova data e ora devono essere future."
        val updated = current.copy(
            label = args["label"]?.trim()?.take(80) ?: current.label,
            schedule = AlarmSchedule(hour, minute, repeatDays, oneShot, old.enabled)
        )
        if (!store.upsert(updated)) return "Non sono riuscita a salvare la modifica."
        if (updated.schedule.enabled && !JuanitaAlarmScheduler.schedule(context, updated)) {
            store.upsert(current)
            JuanitaAlarmScheduler.schedule(context, current)
            return "Android non ha accettato la nuova programmazione. La sveglia precedente è stata ripristinata."
        }
        return "Sveglia aggiornata. ${describe(updated)}" + timingNote()
    }

    private fun setEnabled(args: Map<String, String>): String {
        val id = args["alarm_id"].orEmpty().trim()
        if (id.isBlank()) return "ID sveglia mancante. Prima elenca le sveglie."
        val enabled = args["enabled"]?.trim()?.lowercase(Locale.ROOT)
            ?.let { when (it) { "true", "si", "sì", "1" -> true; "false", "no", "0" -> false; else -> null } }
            ?: return "Stato non valido: specifica true o false."
        val previous = store.find(id) ?: return "Non trovo una sveglia con questo ID."
        val updated = store.setEnabled(id, enabled) ?: return "Non sono riuscita a salvare la modifica."
        if (enabled) {
            if (!JuanitaAlarmScheduler.schedule(context, updated)) {
                store.setEnabled(id, previous.schedule.enabled)
                return "Android non ha programmato la sveglia. Controlla i permessi per gli orari esatti."
            }
        } else JuanitaAlarmScheduler.cancel(context, id)
        return "Sveglia ${if (enabled) "attivata" else "disattivata"}. ${describe(updated)}" + if (enabled) timingNote() else ""
    }

    private fun delete(args: Map<String, String>): String {
        val id = args["alarm_id"].orEmpty().trim()
        if (id.isBlank()) return "ID sveglia mancante. Prima elenca le sveglie."
        val alarm = store.find(id) ?: return "Non trovo una sveglia con questo ID."
        if (!store.delete(id)) return "Non sono riuscita a eliminare la sveglia."
        JuanitaAlarmScheduler.cancel(context, id)
        return "Sveglia eliminata: ${describe(alarm)}. Le altre sveglie non sono state modificate."
    }

    private fun timingNote(): String =
        if (JuanitaAlarmScheduler.hasExactAlarmAccess(context)) ""
        else " Attenzione: l'accesso agli orari esatti non è attivo; Android potrebbe ritardare la sveglia."

    private fun describe(alarm: JuanitaAlarm): String {
        val schedule = alarm.schedule
        val time = String.format(Locale.ROOT, "%02d:%02d", schedule.hour, schedule.minute)
        val whenText = if (schedule.oneShotAtEpochMillis != null) {
            java.time.Instant.ofEpochMilli(schedule.oneShotAtEpochMillis).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN))
        } else {
            schedule.repeatDays.sortedBy { it.value }.joinToString(", ") { it.getDisplayName(java.time.format.TextStyle.FULL, Locale.ITALIAN) }
        }
        val label = alarm.label.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
        return "$time · $whenText$label"
    }
}
