package ai.juanita.app.alarm

import android.Manifest
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.AlarmClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
fun AlarmSection(panel: Color, gold: Color) {
    val context = LocalContext.current
    val store = remember { JuanitaAlarmStore(context) }
    var alarms by remember {
        JuanitaAlarmScheduler.scheduleAll(context)
        mutableStateOf(store.all().sortedBy { it.schedule.hour * 60 + it.schedule.minute })
    }
    var status by remember { mutableStateOf("Sveglie indipendenti salvate sul dispositivo · nessun servizio a pagamento.") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf(7) }
    var minute by remember { mutableStateOf(0) }
    var days by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var oneTimeMode by remember { mutableStateOf(false) }
    var oneTimeDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var deleteTarget by remember { mutableStateOf<JuanitaAlarm?>(null) }
    var morningVoiceEnabled by remember {
        mutableStateOf(context.getSharedPreferences("juanita_alarm", Context.MODE_PRIVATE).getBoolean("morning_voice_enabled", true))
    }

    fun refresh() {
        alarms = store.all().sortedBy { it.schedule.hour * 60 + it.schedule.minute }
    }
    fun openEditor(alarm: JuanitaAlarm?) {
        editingId = alarm?.id ?: UUID.randomUUID().toString()
        label = alarm?.label.orEmpty()
        hour = alarm?.schedule?.hour ?: 7
        minute = alarm?.schedule?.minute ?: 0
        days = alarm?.schedule?.repeatDays ?: setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        )
        oneTimeMode = alarm?.schedule?.oneShotAtEpochMillis != null
        oneTimeDate = alarm?.schedule?.oneShotAtEpochMillis?.let {
            java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        } ?: LocalDate.now().plusDays(1)
    }
    fun saveEditor() {
        val dateEpoch = if (oneTimeMode) {
            oneTimeDate.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else null
        if (!oneTimeMode && days.isEmpty()) {
            status = "Seleziona almeno un giorno oppure scegli una sveglia una tantum."
            return
        }
        if (dateEpoch != null && dateEpoch <= System.currentTimeMillis()) {
            status = "La data e l'ora devono essere future."
            return
        }
        val record = JuanitaAlarm(
            id = editingId ?: UUID.randomUUID().toString(),
            label = label.trim().take(80),
            schedule = AlarmSchedule(
                hour = hour,
                minute = minute,
                repeatDays = if (oneTimeMode) emptySet() else days,
                oneShotAtEpochMillis = dateEpoch,
                enabled = true
            )
        )
        if (!store.upsert(record)) {
            status = "Non sono riuscita a salvare la sveglia nella memoria locale."
            return
        }
        val scheduled = JuanitaAlarmScheduler.schedule(context, record)
        refresh()
        editingId = null
        status = if (scheduled) "Sveglia salvata e programmata."
        else {
            store.setEnabled(record.id, false)
            JuanitaAlarmScheduler.cancel(context, record.id)
            refresh()
            "Sveglia salvata ma non programmata: controlla Accesso a sveglie e promemoria nelle impostazioni Android."
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        morningVoiceEnabled = granted
        context.getSharedPreferences("juanita_alarm", Context.MODE_PRIVATE).edit()
            .putBoolean("morning_voice_enabled", granted).apply()
        status = if (granted) "Buongiorno vocale abilitato con la voce Gemini Live configurata."
        else "Microfono negato: resta attiva la sveglia sonora."
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Notifiche autorizzate." else "Notifiche negate: controlla le impostazioni Android."
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("BUONGIORNO VOCALE DI JUANITA", color = gold, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Parlami al risveglio", color = Color.White, fontSize = 14.sp)
                Text("Usa la voce Gemini Live selezionata. Nessun TTS Android.", color = Color(0xFF9A9DA4), fontSize = 11.sp)
            }
            Switch(checked = morningVoiceEnabled, onCheckedChange = { enabled ->
                if (enabled) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        morningVoiceEnabled = true
                        context.getSharedPreferences("juanita_alarm", Context.MODE_PRIVATE).edit().putBoolean("morning_voice_enabled", true).apply()
                    } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    morningVoiceEnabled = false
                    context.getSharedPreferences("juanita_alarm", Context.MODE_PRIVATE).edit().putBoolean("morning_voice_enabled", false).apply()
                    status = "Buongiorno vocale disattivato. La sveglia sonora continua a funzionare."
                }
            })
        }
        Text("Il microfono è necessario per parlare con Juanita. Calendario e posizione sono facoltativi e vengono richiesti separatamente quando servono.",
            color = Color(0xFF9A9DA4), fontSize = 11.sp)

        Text("LE TUE SVEGLIE", color = gold, fontSize = 13.sp)
        Text("Ogni sveglia è indipendente: puoi impostare lunedì alle 07:00 e martedì alle 07:30, oppure una ripetizione settimanale alla stessa ora.",
            color = Color(0xFFB9BBC1), fontSize = 12.sp)
        if (alarms.isEmpty()) {
            Text("Non hai ancora impostato sveglie.", color = Color(0xFF9A9DA4), fontSize = 13.sp)
        }
        alarms.forEach { alarm ->
            val schedule = alarm.schedule
            Column(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF202329), RoundedCornerShape(14.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(String.format(Locale.getDefault(), "%02d:%02d", schedule.hour, schedule.minute),
                            color = Color.White, fontSize = 30.sp)
                        Text(
                            when {
                                schedule.oneShotAtEpochMillis != null -> "Una volta · " + java.time.Instant.ofEpochMilli(schedule.oneShotAtEpochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE dd/MM", Locale.getDefault()))
                                schedule.repeatDays.size == 7 -> "Ogni giorno"
                                else -> schedule.repeatDays.sortedBy { it.value }.joinToString(" · ") { dayLabel(it) }.ifBlank { "Nessuna ripetizione" }
                            },
                            color = Color(0xFFB9BBC1), fontSize = 12.sp
                        )
                        if (alarm.label.isNotBlank()) Text(alarm.label, color = gold, fontSize = 12.sp)
                    }
                    Switch(
                        checked = schedule.enabled,
                        onCheckedChange = { enabled ->
                            val updated = store.setEnabled(alarm.id, enabled)
                            if (updated != null) {
                                if (enabled) {
                                    val ok = JuanitaAlarmScheduler.schedule(context, updated)
                                    if (!ok) {
                                        store.setEnabled(alarm.id, false)
                                        status = "Non sono riuscita a programmare questa sveglia. Controlla i permessi Android."
                                    } else status = "Sveglia attivata."
                                } else {
                                    JuanitaAlarmScheduler.cancel(context, alarm.id)
                                    status = "Sveglia disattivata. Le altre restano invariate."
                                }
                                refresh()
                            }
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { openEditor(alarm) }) { Text("Modifica", color = gold) }
                    TextButton(onClick = { deleteTarget = alarm }) { Text("Elimina", color = Color(0xFFE49B91)) }
                }
            }
        }
        Button(
            onClick = { openEditor(null) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
        ) { Text("+ Aggiungi sveglia") }
        Button(
            onClick = {
                val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                    .onSuccess { status = "Aperta l'app Orologio del telefono. Le sveglie Samsung restano gestite da quell'app." }
                    .onFailure { status = "Non è disponibile un'app Orologio compatibile." }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
        ) { Text("Apri anche le sveglie del telefono") }
        Text("Android non permette a Juanita di leggere direttamente l'elenco privato delle sveglie Samsung. Può però aprire l'app Orologio; le sveglie create qui restano gestite da Juanita.",
            color = Color(0xFF9A9DA4), fontSize = 11.sp)

        Text("PROVA AUDIO", color = gold, fontSize = 13.sp)
        Button(
            onClick = {
                JuanitaAlarmRingingService.startTest(context)
                status = "Prova sveglia avviata: controlla suono, volume e schermata di blocco. Nessun buongiorno vocale verrà avviato."
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
        ) { Text("Prova sveglia completa") }
        Button(
            onClick = {
                val volumeMessage = AlarmVolumeController.ensureAudible(context)
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val ringtone = uri?.let { RingtoneManager.getRingtone(context, it) }
                if (ringtone == null) {
                    AlarmVolumeController.restore(context)
                    status = "Non è disponibile un tono di allarme sul dispositivo."
                } else {
                    runCatching {
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        ringtone.play()
                        status = "Prova audio per 3 secondi. $volumeMessage"
                        Handler(Looper.getMainLooper()).postDelayed({
                            runCatching { ringtone.stop() }
                            AlarmVolumeController.restore(context)
                        }, 3_000L)
                    }.onFailure {
                        AlarmVolumeController.restore(context)
                        status = "Prova audio non riuscita: ${it.localizedMessage ?: "errore"}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
        ) { Text("Prova suono e volume · 3 secondi") }

        Text("ACCESSI ANDROID", color = gold, fontSize = 13.sp)
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else status = "Permesso notifiche già attivo."
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
        ) { Text("Verifica / autorizza notifiche") }

        if (Build.VERSION.SDK_INT >= 34 && !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            Text("Per mostrare la sveglia sopra la schermata di blocco, autorizza gli avvisi a schermo intero.",
                color = Color(0xFFE4BD69), fontSize = 12.sp)
            Button(onClick = {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }.onFailure { status = "Apri le impostazioni Android degli avvisi a schermo intero." }
            }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
            ) { Text("Consenti sveglia a schermo intero") }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val exactAllowed = context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
            if (!exactAllowed) {
                Text("Senza questo accesso Android può ritardare le sveglie.", color = Color(0xFFE4BD69), fontSize = 12.sp)
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }.onFailure { status = "Apri Impostazioni → Accesso speciale → Sveglie e promemoria." }
                }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
                ) { Text("Consenti orari esatti") }
            } else Text("Orari esatti autorizzati.", color = Color(0xFF9A9DA4), fontSize = 12.sp)
        }
        Text(status, color = Color.White, fontSize = 13.sp)
        Text("Le sveglie sono locali e funzionano senza rete. Il buongiorno vocale richiede connessione Gemini Live. Posticipare una sveglia non modifica le altre.",
            color = Color(0xFF9A9DA4), fontSize = 11.sp)
    }

    if (editingId != null) {
        AlertDialog(
            onDismissRequest = { editingId = null },
            title = { Text(if (alarms.any { it.id == editingId }) "Modifica sveglia" else "Nuova sveglia") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = label, onValueChange = { label = it.take(80) },
                        label = { Text("Etichetta (facoltativa)") }, singleLine = true)
                    Text(String.format(Locale.getDefault(), "%02d:%02d", hour, minute), color = Color.White, fontSize = 32.sp)
                    Button(onClick = {
                        TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Scegli orario") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Una sola volta", modifier = Modifier.weight(1f))
                        Switch(checked = oneTimeMode, onCheckedChange = { oneTimeMode = it })
                    }
                    if (oneTimeMode) {
                        Button(onClick = {
                            DatePickerDialog(context, { _, y, m, d -> oneTimeDate = LocalDate.of(y, m + 1, d) },
                                oneTimeDate.year, oneTimeDate.monthValue - 1, oneTimeDate.dayOfMonth).show()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Data: ${oneTimeDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
                        }
                    } else {
                        Text("Ripeti nei giorni selezionati", color = Color(0xFFB9BBC1), fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                            DayOfWeek.entries.forEach { day ->
                                val selected = day in days
                                Box(
                                    modifier = Modifier.weight(1f).height(40.dp)
                                        .background(if (selected) gold else Color(0xFF30343B), RoundedCornerShape(8.dp))
                                        .clickable { days = if (selected) days - day else days + day },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(dayLabel(day), color = if (selected) Color.Black else Color.White, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { saveEditor() }) { Text("Salva", color = gold) } },
            dismissButton = { TextButton(onClick = { editingId = null }) { Text("Annulla") } }
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminare la sveglia?") },
            text = { Text("Verrà eliminata solo ${target.label.ifBlank { String.format(Locale.getDefault(), "%02d:%02d", target.schedule.hour, target.schedule.minute) }}. Le altre sveglie non verranno modificate.") },
            confirmButton = {
                TextButton(onClick = {
                    if (store.delete(target.id)) {
                        JuanitaAlarmScheduler.cancel(context, target.id)
                        status = "Sveglia eliminata."
                    } else {
                        status = "Non sono riuscita a eliminare la sveglia dalla memoria locale."
                    }
                    refresh()
                    deleteTarget = null
                }) { Text("Elimina", color = Color(0xFFE49B91)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Annulla") } }
        )
    }
}

private fun dayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Lun"
    DayOfWeek.TUESDAY -> "Mar"
    DayOfWeek.WEDNESDAY -> "Mer"
    DayOfWeek.THURSDAY -> "Gio"
    DayOfWeek.FRIDAY -> "Ven"
    DayOfWeek.SATURDAY -> "Sab"
    DayOfWeek.SUNDAY -> "Dom"
}
