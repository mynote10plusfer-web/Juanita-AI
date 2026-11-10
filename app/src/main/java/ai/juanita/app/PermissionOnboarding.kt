package ai.juanita.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * First-run, contextual permission guide. Android still presents each system
 * permission separately; optional features can be skipped without blocking
 * the core assistant or locally scheduled alarms.
 */
@Composable
fun PermissionOnboarding(onComplete: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Scegli quali funzioni vuoi attivare. Puoi cambiare idea in seguito.") }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = if (it) "Microfono autorizzato." else "Microfono non autorizzato: puoi comunque usare le funzioni locali."
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        status = if (grants.values.any { it }) "Posizione autorizzata." else "Posizione facoltativa non autorizzata."
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = if (it) "Calendario autorizzato." else "Calendario non autorizzato: Juanita non dirà che è vuoto."
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = if (it) "Fotocamera autorizzata." else "Fotocamera non autorizzata: le funzioni vocali restano disponibili."
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = if (it) "Notifiche autorizzate." else "Notifiche non autorizzate: controlla le impostazioni se vuoi riceverle."
    }

    AlertDialog(
        onDismissRequest = onComplete,
        title = { Text("Prepariamo Juanita") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Ti spiego ogni accesso prima di chiederlo. Android non permette di concedere tutti i permessi con un solo pulsante.",
                    color = Color(0xFFB9BBC1), fontSize = 13.sp)
                PermissionAction(
                    title = "Microfono",
                    detail = "Necessario per parlare con Juanita e per il buongiorno vocale quando suona la sveglia.",
                    action = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                )
                PermissionAction(
                    title = "Posizione",
                    detail = "Facoltativa: serve per meteo locale e informazioni legate alla zona. Nessun tracciamento continuo in background.",
                    action = { locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                )
                PermissionAction(
                    title = "Calendario",
                    detail = "Facoltativo: permette di includere gli appuntamenti nel riepilogo del mattino.",
                    action = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) }
                )
                if (Build.VERSION.SDK_INT >= 33) {
                    PermissionAction(
                        title = "Avvisi di Juanita",
                        detail = "Per mostrare avvisi e promemoria. Il permesso è separato dalle sveglie precise.",
                        action = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }
                PermissionAction(
                    title = "Fotocamera",
                    detail = "Facoltativa: serve solo alle funzioni visive e videochiamata quando decidi di usarle.",
                    action = { cameraLauncher.launch(Manifest.permission.CAMERA) }
                )
                PermissionAction(
                    title = "Orari esatti delle sveglie",
                    detail = "Accesso speciale Android: senza, il sistema può ritardare una sveglia.",
                    action = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }.onFailure { status = "Apri Accesso speciale → Sveglie e promemoria nelle impostazioni." }
                        } else status = "Su questa versione Android non serve l'accesso speciale agli orari esatti."
                    }
                )
                if (Build.VERSION.SDK_INT >= 34) {
                    PermissionAction(
                        title = "Sveglia a schermo intero",
                        detail = "Permette alla schermata della sveglia di comparire sopra il blocco schermo, dove consentito da Android.",
                        action = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }.onFailure { status = "Apri le impostazioni degli avvisi a schermo intero." }
                        }
                    )
                }
                Text(status, color = Color(0xFFE4BD69), fontSize = 12.sp)
                Text("Non chiediamo accesso a SMS, contatti, registro chiamate o microfono sempre attivo.",
                    color = Color(0xFF9A9DA4), fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onComplete) { Text("Continua", color = Color(0xFFE4BD69)) }
        },
        dismissButton = {
            TextButton(onClick = onComplete) { Text("Non ora", color = Color(0xFFB9BBC1)) }
        }
    )
}

@Composable
private fun PermissionAction(title: String, detail: String, action: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color.White, fontSize = 14.sp)
        Text(detail, color = Color(0xFF9A9DA4), fontSize = 11.sp)
        Button(
            onClick = action,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = Color(0xFFE4BD69))
        ) { Text("Configura") }
    }
}
