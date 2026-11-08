package ai.juanita.app.alarm

import android.Manifest
import android.app.TimePickerDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import java.time.DayOfWeek

@Composable
fun AlarmSection(panel: Color, gold: Color) {
    val context = LocalContext.current
    val initial = remember {
        AlarmPreferences(context).load()
            ?: AlarmSchedule(7, 0, setOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
            ), enabled = false)
    }
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    var days by remember { mutableStateOf(initial.repeatDays) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var status by remember { mutableStateOf("Sveglia locale · nessun servizio a pagamento") }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "Notifiche autorizzate. Puoi salvare la sveglia."
        else "Permesso notifiche negato: Android potrebbe non mostrare l'avviso."
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SVEGLIA DI JUANITA", color = gold, fontSize = 13.sp)
        Text("Imposta un orario e i giorni. La programmazione avviene sul telefono, senza API o abbonamenti.",
            color = Color(0xFFB9BBC1), fontSize = 13.sp)
        Text(String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute),
            color = Color.White, fontSize = 42.sp)
        Button(
            onClick = {
                TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
        ) { Text("Scegli orario") }

        Text("Ripeti", color = gold, fontSize = 13.sp)
        val dayLabels = listOf(
            DayOfWeek.MONDAY to "Lun", DayOfWeek.TUESDAY to "Mar",
            DayOfWeek.WEDNESDAY to "Mer", DayOfWeek.THURSDAY to "Gio",
            DayOfWeek.FRIDAY to "Ven", DayOfWeek.SATURDAY to "Sab",
            DayOfWeek.SUNDAY to "Dom"
        )
        dayLabels.chunked(4).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                group.forEach { (day, label) ->
                    val selected = day in days
                    Button(
                        onClick = { days = if (selected) days - day else days + day },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) gold else Color(0xFF30343B),
                            contentColor = if (selected) Color.Black else Color.White
                        )
                    ) { Text(label, fontSize = 11.sp) }
                }
                repeat(4 - group.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    status = "Permesso notifiche già attivo."
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
        ) { Text("Verifica / autorizza notifiche") }

        if (Build.VERSION.SDK_INT >= 34 &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) {
            Text("Per mostrare la sveglia a schermo intero anche con il telefono bloccato, consenti l'avviso a schermo intero.",
                color = Color(0xFFE4BD69), fontSize = 12.sp)
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure { status = "Controlla l'autorizzazione Sveglia a schermo intero nelle impostazioni Android." }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
            ) { Text("Consenti sveglia a schermo intero") }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val exactAllowed = context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
            if (!exactAllowed) {
                Text("Per rispettare l'orario esatto, Android richiede un'autorizzazione speciale.",
                    color = Color(0xFFE4BD69), fontSize = 12.sp)
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure { status = "Apri manualmente Impostazioni Android → Accesso speciale → Sveglie e promemoria." }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
                ) { Text("Consenti orari esatti") }
            } else {
                Text("Orari esatti autorizzati.", color = Color(0xFF9A9DA4), fontSize = 12.sp)
            }
        }

        Button(
            onClick = {
                if (days.isEmpty()) {
                    status = "Seleziona almeno un giorno per attivare la sveglia."
                } else if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    status = "Concedi prima il permesso notifiche, poi premi di nuovo Salva e attiva."
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    val schedule = AlarmSchedule(hour, minute, days, enabled = true)
                    val ok = JuanitaAlarmScheduler.schedule(context, schedule)
                    enabled = ok
                    status = if (ok) "Sveglia salvata e programmata sul dispositivo."
                    else "Non è stato possibile programmare la sveglia. Controlla i permessi Android."
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
        ) { Text("Salva e attiva") }

        Button(
            onClick = {
                JuanitaAlarmScheduler.cancel(context)
                enabled = false
                status = "Sveglia disattivata."
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
        ) { Text("Disattiva sveglia") }

        Text(if (enabled) "STATO: ATTIVA" else "STATO: NON ATTIVA", color = if (enabled) gold else Color(0xFF9A9DA4), fontSize = 12.sp)
        Text(status, color = Color.White, fontSize = 13.sp)
        Text("Questa prima versione genera un avviso sonoro Android. La voce Live personalizzata di Juanita, la dissolvenza graduale, il tasto snooze e la schermata di risveglio saranno collegati solo dopo aver verificato in modo affidabile la riproduzione della voce già configurata: non verrà usata una voce TTS sostitutiva.",
            color = Color(0xFF9A9DA4), fontSize = 11.sp)
    }
}
