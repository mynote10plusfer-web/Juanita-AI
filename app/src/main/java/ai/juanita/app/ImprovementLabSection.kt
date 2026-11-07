package ai.juanita.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.juanita.app.memory.ImprovementReport
import java.text.DateFormat
import java.util.Date

private data class CapabilityRow(
    val name: String,
    val state: String,
    val detail: String
)

private val capabilityRows = listOf(
    CapabilityRow("Conversazione vocale Gemini Live", "INTEGRATA · DA VERIFICARE", "Microfono e risposta vocale sono collegati. Restano da verificare stabilità, riavvio e sessioni prolungate sui dispositivi reali."),
    CapabilityRow("Memoria personale automatica", "INTEGRATA · DA VERIFICARE", "Estrazione asincrona di ricordi non sensibili con coda cifrata locale. Dipende da trascrizione, rete e quota Gemini."),
    CapabilityRow("Archivio conversazioni cifrato", "INTEGRATO · DA VERIFICARE", "Trascrizioni testuali locali per sessione, calendario, ricerca lessicale e cancellazioni selettive. Nessun audio grezzo archiviato."),
    CapabilityRow("Richiamo delle conversazioni passate", "INTEGRATO · DA VERIFICARE", "Ricerca locale per frasi e parole chiave; non è ancora una ricerca semantica multilingue basata su vettori."),
    CapabilityRow("Fotocamera durante la chiamata", "INTEGRATA · DA VERIFICARE", "CameraX invia fotogrammi a Gemini Live quando autorizzata. Orientamento e stabilità vanno verificati su Note10+ e S22 Ultra."),
    CapabilityRow("Ritratto animato e sincronizzazione labiale", "NON IMPLEMENTATA", "Il ritratto attuale ha solo un movimento leggero. Non esistono ancora animazione facciale reale e lip-sync."),
    CapabilityRow("Creazione automatica di nuovi ritratti", "NON IMPLEMENTATA", "Avatar Studio salva profili e descrizioni, ma non genera ancora immagini."),
    CapabilityRow("Riconoscimento della parola di attivazione", "NON IMPLEMENTATO", "La frase è configurabile ma non è collegata a un motore wake-word."),
    CapabilityRow("Voce e riconoscimento completamente offline", "PIANIFICATA", "Servono modelli locali compatibili con italiano, spagnolo, ceco e inglese e prove di prestazioni."),
    CapabilityRow("Filtro intelligente delle chiamate cellulari", "RICERCA / NON IMPLEMENTATO", "Android consente screening limitato dei metadati; la conversazione audio bidirezionale non è garantita dalle API di screening."),
    CapabilityRow("Modalità LIFE e notifiche", "PIANIFICATA", "Promemoria e integrazioni con notifiche, calendario e messaggi richiedono moduli e permessi separati."),
    CapabilityRow("Ricerca web e archivio di conoscenze", "NON IMPLEMENTATA NELL'APP", "La ricerca mirata e le note con fonti sono una fase futura; non va dichiarato che Juanita abbia studiato qualcosa senza una ricerca effettiva.")
)

private val reportTypes = listOf(
    "BUG", "INEFFICIENCY", "CAPABILITY_GAP", "KNOWLEDGE_GAP", "RESEARCH_REQUEST", "IMPROVEMENT_IDEA"
)

@Composable
internal fun ImprovementLabSection(
    panel: Color,
    gold: Color,
    initialReports: List<ImprovementReport>,
    onRefresh: () -> List<ImprovementReport>,
    onSave: (String, String, String, String, String, String, String) -> ImprovementReport,
    onDelete: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var reports by remember { mutableStateOf(initialReports) }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("CAPABILITY_GAP") }
    var typeMenu by remember { mutableStateOf(false) }
    var area by remember { mutableStateOf("Generale") }
    var observed by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf("") }
    var expected by remember { mutableStateOf("") }
    var acceptance by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedReport by remember { mutableStateOf<ImprovementReport?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var exportPayload by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("I rapporti restano cifrati sul dispositivo finché non scegli di esportarli.") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) {
            message = "Esportazione annullata."
        } else {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(exportPayload) }
                    ?: error("Impossibile aprire il file scelto.")
            }
            message = if (result.isSuccess) "Rapporto esportato nel percorso scelto." else "Esportazione non riuscita: ${result.exceptionOrNull()?.localizedMessage ?: "errore"}"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("LABORATORIO · CAPACITÀ DI JUANITA", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Questa scheda distingue ciò che è integrato da ciò che è ancora da verificare o da sviluppare. Gli stati non equivalgono a test superati sul telefono.", color = Color(0xFF9A9DA4), fontSize = 12.sp)
                capabilityRows.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(item.state, color = gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(item.detail, color = Color(0xFF9A9DA4), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("NUOVO RAPPORTO DI MIGLIORAMENTO", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Puoi annotare un errore, una funzione mancante o un miglioramento. Il rapporto non include automaticamente conversazioni, ricordi o dati personali.", color = Color(0xFF9A9DA4), fontSize = 11.sp)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tipo: $type", color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = { typeMenu = true }) { Text("Cambia", color = gold) }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        reportTypes.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { type = option; typeMenu = false })
                        }
                    }
                }
                OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Area interessata") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = observed, onValueChange = { observed = it }, label = { Text("Cosa è successo / cosa vorresti ottenere") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(value = evidence, onValueChange = { evidence = it }, label = { Text("Prove o messaggio d'errore (facoltativo)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = expected, onValueChange = { expected = it }, label = { Text("Risultato desiderato") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = acceptance, onValueChange = { acceptance = it }, label = { Text("Come verifichiamo che funziona?") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Button(
                    onClick = {
                        val saved = onSave(title.trim(), type, area.trim(), observed.trim(), evidence.trim(), expected.trim(), acceptance.trim())
                        reports = listOf(saved) + reports
                        title = ""; observed = ""; evidence = ""; expected = ""; acceptance = ""
                        message = "Rapporto salvato in forma cifrata sul telefono."
                    },
                    enabled = title.isNotBlank() && observed.isNotBlank() && expected.isNotBlank() && acceptance.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
                ) { Text("Salva rapporto") }
                Text(message, color = Color(0xFF9A9DA4), fontSize = 11.sp)
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RAPPORTI SALVATI · ${reports.size}", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { reports = onRefresh() }) { Text("Aggiorna", color = gold) }
                }
                if (reports.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selectedIds = if (selectedIds.size == reports.size) emptySet() else reports.map { it.id }.toSet() }) { Text(if (selectedIds.size == reports.size) "Deseleziona" else "Seleziona tutti", color = gold) }
                        TextButton(onClick = {
                            exportPayload = reports.filter { it.id in selectedIds }.joinToString("\n\n---\n\n") { formatReport(it) }
                            exportLauncher.launch("Juanita-rapporti-${System.currentTimeMillis()}.txt")
                        }, enabled = selectedIds.isNotEmpty()) { Text("Esporta (${selectedIds.size})", color = gold) }
                        TextButton(onClick = { confirmDelete = true }, enabled = selectedIds.isNotEmpty()) { Text("Elimina", color = Color(0xFFE6A0A0)) }
                    }
                    reports.forEach { report ->
                        Row(verticalAlignment = Alignment.Top) {
                            Checkbox(checked = report.id in selectedIds, onCheckedChange = { checked -> selectedIds = if (checked) selectedIds + report.id else selectedIds - report.id })
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(report.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${report.type} · ${report.area} · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(report.createdAtMillis))}", color = Color(0xFF9A9DA4), fontSize = 10.sp)
                                TextButton(onClick = { selectedReport = report }) { Text("Apri rapporto", color = gold) }
                            }
                        }
                    }
                } else {
                    Text("Non ci sono ancora rapporti. La creazione automatica dei rapporti a partire dagli eventi Live sarà una fase successiva.", color = Color(0xFF9A9DA4), fontSize = 12.sp)
                }
            }
        }
    }

    selectedReport?.let { report ->
        AlertDialog(
            onDismissRequest = { selectedReport = null },
            title = { Text(report.title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ID: ${report.id}", color = gold, fontSize = 10.sp)
                    Text("Tipo: ${report.type} · Stato: ${report.status}", color = Color.White, fontSize = 11.sp)
                    Text("Data: ${DateFormat.getDateTimeInstance().format(Date(report.createdAtMillis))} · ${report.timeZoneId}", color = Color.White, fontSize = 11.sp)
                    Text("Area: ${report.area}", color = Color.White, fontSize = 12.sp)
                    Text("Osservazione: ${report.observed}", color = Color.White, fontSize = 12.sp)
                    if (report.evidence.isNotBlank()) Text("Evidenza: ${report.evidence}", color = Color.White, fontSize = 12.sp)
                    Text("Risultato atteso: ${report.expectedOutcome}", color = Color.White, fontSize = 12.sp)
                    Text("Test di accettazione: ${report.acceptanceTest}", color = Color.White, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    exportPayload = formatReport(report)
                    exportLauncher.launch("Juanita-${report.id.take(8)}.txt")
                }) { Text("Esporta .txt", color = gold) }
            },
            dismissButton = { TextButton(onClick = { selectedReport = null }) { Text("Chiudi", color = gold) } }
        )
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Eliminare i rapporti selezionati?") },
        text = { Text("Saranno eliminati ${selectedIds.size} rapporti locali. L'archivio delle conversazioni e i ricordi personali non verranno modificati.") },
        confirmButton = {
            TextButton(onClick = { onDelete(selectedIds); reports = reports.filterNot { it.id in selectedIds }; selectedIds = emptySet(); confirmDelete = false }) {
                Text("Elimina", color = Color(0xFFE6A0A0))
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla", color = gold) } }
    )
}

private fun formatReport(report: ImprovementReport): String = buildString {
    appendLine("JUANITA AI — RAPPORTO DI MIGLIORAMENTO")
    appendLine("ID: ${report.id}")
    appendLine("Data locale: ${DateFormat.getDateTimeInstance().format(Date(report.createdAtMillis))}")
    appendLine("Fuso orario: ${report.timeZoneId}")
    appendLine("Tipo: ${report.type}")
    appendLine("Stato: ${report.status}")
    appendLine("Area: ${report.area}")
    appendLine("Titolo: ${report.title}")
    appendLine()
    appendLine("OSSERVAZIONE")
    appendLine(report.observed)
    if (report.evidence.isNotBlank()) { appendLine(); appendLine("EVIDENZA"); appendLine(report.evidence) }
    appendLine()
    appendLine("RISULTATO ATTESO")
    appendLine(report.expectedOutcome)
    appendLine()
    appendLine("TEST DI ACCETTAZIONE")
    appendLine(report.acceptanceTest)
    appendLine()
    appendLine("Privacy: rapporto scritto manualmente; nessuna trascrizione, memoria personale, audio o credenziale inclusa automaticamente.")
}
