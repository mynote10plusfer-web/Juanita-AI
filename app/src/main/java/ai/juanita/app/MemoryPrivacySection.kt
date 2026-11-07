package ai.juanita.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.juanita.app.memory.EncryptedMemoryStore
import ai.juanita.app.memory.MemoryItem
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@Composable
internal fun MemoryPrivacySection(
    modifier: Modifier = Modifier,
    panel: Color,
    gold: Color,
    initialArchiveEnabled: Boolean,
    initialAutoMemoryEnabled: Boolean,
    memoryStatus: String,
    initialMemories: List<MemoryItem>,
    initialArchivedConversations: List<EncryptedMemoryStore.ArchivedConversation>,
    initialRetentionDays: Int,
    onArchiveEnabledChanged: (Boolean) -> Unit,
    onAutoMemoryEnabledChanged: (Boolean) -> Unit,
    onSaveMemory: (String) -> MemoryItem,
    onDeleteMemory: (String) -> Unit,
    onDeleteMemories: (Set<String>) -> Unit,
    onUpdateMemory: (String, String) -> Boolean,
    onClearMemories: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDeleteArchiveRange: (Long, Long) -> Unit,
    onRefreshArchive: () -> List<EncryptedMemoryStore.ArchivedConversation>,
    onClearArchive: () -> Unit,
    onRetentionChanged: (Int) -> Unit,
    onRefreshMemories: () -> List<MemoryItem>
) {
    var archiveEnabled by remember { mutableStateOf(initialArchiveEnabled) }
    var autoMemoryEnabled by remember { mutableStateOf(initialAutoMemoryEnabled) }
    var memories by remember { mutableStateOf(initialMemories) }
    var conversations by remember { mutableStateOf(initialArchivedConversations) }
    var retentionDays by remember { mutableStateOf(initialRetentionDays) }
    var memoryDraft by remember { mutableStateOf("") }
    var retentionMenu by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var editingMemory by remember { mutableStateOf<MemoryItem?>(null) }
    var editText by remember { mutableStateOf("") }
    var archiveRangeStart by remember { mutableStateOf("") }
    var archiveRangeEnd by remember { mutableStateOf("") }
    var selectedMemoryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(memoryStatus) {
        memories = onRefreshMemories()
    }
    val rangeStartDate = runCatching { LocalDate.parse(archiveRangeStart.trim()) }.getOrNull()
    val rangeEndDate = runCatching { LocalDate.parse(archiveRangeEnd.trim()) }.getOrNull()
    val validArchiveRange = rangeStartDate != null && rangeEndDate != null && !rangeEndDate.isBefore(rangeStartDate)
    val retentionLabel = when (retentionDays) {
        7 -> "7 giorni"
        30 -> "30 giorni"
        90 -> "90 giorni"
        else -> "Mai (cancellazione manuale)"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = panel),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("MEMORIA E PRIVACY", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("I dati sono conservati in forma cifrata sul telefono. Puoi modificarli o cancellarli quando vuoi.",
                color = Color(0xFF9A9DA4), fontSize = 12.sp)
            Text("MEMORIA AUTOMATICA", color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = autoMemoryEnabled, onCheckedChange = { enabled ->
                    autoMemoryEnabled = enabled
                    onAutoMemoryEnabledChanged(enabled)
                })
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ricorda automaticamente le informazioni importanti", color = Color.White, fontSize = 13.sp)
                    Text(if (autoMemoryEnabled) "Attiva: analizza brevi blocchi di testo delle conversazioni per estrarre ricordi utili." else "In pausa: nessun nuovo ricordo automatico verrà creato.",
                        color = Color(0xFF9A9DA4), fontSize = 11.sp)
                }
            }
            Text(if (autoMemoryEnabled) memoryStatus else "Memoria automatica in pausa.",
                color = if (autoMemoryEnabled) gold else Color(0xFF9A9DA4), fontSize = 11.sp)
            Text("Per selezionare i ricordi, brevi estratti testuali vengono inviati a Gemini. Nel livello gratuito Google può usare i contenuti inviati per migliorare i propri prodotti, secondo i suoi termini. Non viene salvato l’audio e non serve attivare l’archivio completo. Puoi disattivare questa funzione in qualsiasi momento.",
                color = Color(0xFF9A9DA4), fontSize = 11.sp)
            Text("RICORDI PERSONALI", color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${memories.size}/100 ricordi · ${memories.sumOf { it.text.length }}/32.000 caratteri", color = Color(0xFF9A9DA4), fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { memories = onRefreshMemories() }) { Text("Aggiorna", color = gold, fontSize = 12.sp) }
            }
            OutlinedTextField(
                value = memoryDraft, onValueChange = { memoryDraft = it },
                label = { Text("Nuovo ricordo") },
                placeholder = { Text("Scrivi un'informazione da ricordare…") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (memoryDraft.isNotBlank()) {
                        memories = listOf(onSaveMemory(memoryDraft.trim())) + memories
                        memoryDraft = ""
                    }
                },
                enabled = memoryDraft.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
            ) { Text("Salva ricordo") }

            if (memories.isEmpty()) {
                Text("Non hai ancora salvato ricordi personali.", color = Color(0xFF9A9DA4), fontSize = 12.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selectedMemoryIds = if (selectedMemoryIds.size == memories.size) emptySet() else memories.map { it.id }.toSet() }) {
                        Text(if (selectedMemoryIds.size == memories.size) "Deseleziona tutti" else "Seleziona tutti", color = gold, fontSize = 12.sp)
                    }
                    TextButton(onClick = { confirmAction = "selected_memories" }, enabled = selectedMemoryIds.isNotEmpty()) {
                        Text("Elimina selezionati (${selectedMemoryIds.size})", color = Color(0xFFE6A0A0), fontSize = 12.sp)
                    }
                }
                memories.forEach { item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(checked = item.id in selectedMemoryIds, onCheckedChange = { checked -> selectedMemoryIds = if (checked) selectedMemoryIds + item.id else selectedMemoryIds - item.id })
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.text, color = Color.White, fontSize = 13.sp)
                            Text("${item.category} · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.createdAtMillis))}", color = Color(0xFF9A9DA4), fontSize = 10.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { editingMemory = item; editText = item.text }) { Text("Modifica", color = gold, fontSize = 12.sp) }
                                TextButton(onClick = { confirmAction = "memory:${item.id}" }) { Text("Elimina", color = gold, fontSize = 12.sp) }
                            }
                        }
                    }
                }
                TextButton(onClick = { confirmAction = "clear_memories" }) {
                    Text("ELIMINA TUTTI I RICORDI PERSONALI", color = Color(0xFFE6A0A0), fontSize = 11.sp)
                }
            }

            Text("ARCHIVIO CONVERSAZIONI", color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { conversations = onRefreshArchive() }) { Text("Aggiorna elenco conversazioni", color = gold, fontSize = 12.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = archiveEnabled, onCheckedChange = {
                    archiveEnabled = it
                    onArchiveEnabledChanged(it)
                })
                Column(modifier = Modifier.weight(1f)) {
                    Text("Consenti archiviazione", color = Color.White, fontSize = 13.sp)
                    Text(if (archiveEnabled) "Consenso attivo" else "Disattivato per impostazione predefinita",
                        color = Color(0xFF9A9DA4), fontSize = 11.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Conservazione", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { retentionMenu = true }) { Text(retentionLabel, color = gold) }
                DropdownMenu(expanded = retentionMenu, onDismissRequest = { retentionMenu = false }) {
                    listOf(0 to "Mai (manuale)", 7 to "7 giorni", 30 to "30 giorni", 90 to "90 giorni").forEach { option ->
                        DropdownMenuItem(text = { Text(option.second) }, onClick = {
                            retentionDays = option.first
                            onRetentionChanged(option.first)
                            retentionMenu = false
                        })
                    }
                }
            }
            Text("CANCELLA CONVERSAZIONI PER PERIODO", color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Inserisci le date nel formato AAAA-MM-GG. Verranno eliminati solo i messaggi dell'intervallo scelto; i ricordi personali resteranno intatti.", color = Color(0xFF9A9DA4), fontSize = 11.sp)
            OutlinedTextField(value = archiveRangeStart, onValueChange = { archiveRangeStart = it }, label = { Text("Dal (AAAA-MM-GG)") }, placeholder = { Text("2026-11-01") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = archiveRangeEnd, onValueChange = { archiveRangeEnd = it }, label = { Text("Al (AAAA-MM-GG)") }, placeholder = { Text("2026-11-07") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { confirmAction = "archive_range" }, enabled = validArchiveRange, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)) { Text("ELIMINA CONVERSAZIONI NEL PERIODO") }
            if (conversations.isEmpty()) {
                Text("Nessuna conversazione presente nell'archivio.", color = Color(0xFF9A9DA4), fontSize = 12.sp)
            } else {
                conversations.forEach { conversation ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(conversation.preview.ifBlank { "Conversazione senza anteprima" },
                            color = Color.White, fontSize = 13.sp)
                        Text("${conversation.turnCount} messaggi · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(conversation.startedAtMillis))}",
                            color = Color(0xFF9A9DA4), fontSize = 11.sp)
                        TextButton(onClick = { confirmAction = "conversation:${conversation.sessionId}" }) {
                            Text("Elimina conversazione", color = gold, fontSize = 12.sp)
                        }
                    }
                }
                TextButton(onClick = { confirmAction = "clear_archive" }) {
                    Text("SVUOTA TUTTO L'ARCHIVIO", color = Color(0xFFE6A0A0), fontSize = 11.sp)
                }
            }
            Text("Le trascrizioni complete vengono archiviate solo con consenso esplicito. La memoria automatica conserva solo ricordi selezionati, con limiti di spazio: massimo 100 ricordi e 32.000 caratteri complessivi. Puoi cancellarli singolarmente o tutti insieme.",
                color = gold, fontSize = 11.sp)
        }
    }

    confirmAction?.let { action ->
        val isMemory = action.startsWith("memory:")
        val isConversation = action.startsWith("conversation:")
        val isArchiveRange = action == "archive_range"
        val title = when (action) {
            "archive_range" -> "Eliminare le conversazioni nel periodo?"
            "clear_memories" -> "Eliminare tutti i ricordi?"
            "clear_archive" -> "Svuotare l'archivio?"
            "selected_memories" -> "Eliminare i ricordi selezionati?"
            else -> if (isMemory) "Eliminare questo ricordo?" else "Eliminare questa conversazione?"
        }
        val message = when (action) {
            "clear_memories" -> "Saranno cancellati solo i ricordi personali. L'archivio conversazioni resterà intatto."
            "clear_archive" -> "Saranno cancellate solo le conversazioni archiviate. I ricordi personali resteranno intatti."
            "selected_memories" -> "Saranno eliminati ${selectedMemoryIds.size} ricordi selezionati. Le conversazioni archiviate resteranno intatte."
            "archive_range" -> "Saranno eliminati tutti i messaggi archiviati dal ${rangeStartDate} al ${rangeEndDate}, estremi inclusi. I ricordi personali non verranno modificati. L'operazione è definitiva."
            else -> "Questa operazione è definitiva e non può essere annullata."
        }
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(title) }, text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        action == "archive_range" -> {
                            val start = rangeStartDate
                            val end = rangeEndDate
                            if (start != null && end != null && !end.isBefore(start)) {
                                val zone = ZoneId.systemDefault()
                                val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
                                val endMillis = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                                onDeleteArchiveRange(startMillis, endMillis)
                                conversations = onRefreshArchive()
                            }
                        }
                        action == "clear_memories" -> { onClearMemories(); memories = emptyList() }
                        action == "clear_archive" -> { onClearArchive(); conversations = emptyList() }
                        action == "selected_memories" -> { onDeleteMemories(selectedMemoryIds); memories = memories.filterNot { it.id in selectedMemoryIds }; selectedMemoryIds = emptySet() }
                        isMemory -> {
                            val id = action.removePrefix("memory:")
                            onDeleteMemory(id)
                            memories = memories.filterNot { it.id == id }
                        }
                        isConversation -> {
                            val id = action.removePrefix("conversation:")
                            onDeleteConversation(id)
                            conversations = conversations.filterNot { it.sessionId == id }
                        }
                    }
                    confirmAction = null
                }) { Text("Elimina", color = Color(0xFFB94A48)) }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("Annulla") } }
        )
    }

    editingMemory?.let { item ->
        AlertDialog(
            onDismissRequest = { editingMemory = null },
            title = { Text("Modifica ricordo") },
            text = {
                OutlinedTextField(value = editText, onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Testo del ricordo") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editText.isNotBlank() && onUpdateMemory(item.id, editText)) {
                        memories = memories.map { if (it.id == item.id) it.copy(text = editText.trim()) else it }
                    }
                    editingMemory = null
                }) { Text("Salva", color = gold) }
            },
            dismissButton = { TextButton(onClick = { editingMemory = null }) { Text("Annulla") } }
        )
    }
}
