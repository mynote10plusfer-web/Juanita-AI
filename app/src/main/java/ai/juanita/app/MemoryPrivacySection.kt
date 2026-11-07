package ai.juanita.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.juanita.app.memory.EncryptedMemoryStore
import ai.juanita.app.memory.MemoryItem
import java.text.DateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
internal fun MemoryPrivacySection(
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
    onListConversationTurns: (String) -> List<EncryptedMemoryStore.ArchivedTurn>,
    onDeleteConversationRange: (String, Long, Long) -> Unit,
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
        modifier = Modifier.fillMaxWidth(),
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

            ConversationArchiveCalendar(
                panel = panel, gold = gold, conversations = conversations,
                archiveEnabled = archiveEnabled, retentionLabel = retentionLabel,
                onArchiveEnabledChanged = { archiveEnabled = it; onArchiveEnabledChanged(it) },
                onRetentionChanged = { retentionDays = it; onRetentionChanged(it) },
                onRefresh = { conversations = onRefreshArchive() },
                onDeleteConversation = { confirmAction = "conversation:" + it },
                onDeleteDay = { day ->
                    val zone = ZoneId.systemDefault()
                    onDeleteArchiveRange(day.atStartOfDay(zone).toInstant().toEpochMilli(),
                        day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                    conversations = onRefreshArchive()
                },
                onListTurns = onListConversationTurns,
                onDeleteTimeRange = { id, from, until ->
                    onDeleteConversationRange(id, from, until); conversations = onRefreshArchive()
                },
                onClearArchive = { confirmAction = "clear_archive" }
            )
        }
    }


@Composable
private fun ConversationArchiveCalendar(
    panel: Color, gold: Color,
    conversations: List<EncryptedMemoryStore.ArchivedConversation>,
    archiveEnabled: Boolean, retentionLabel: String,
    onArchiveEnabledChanged: (Boolean) -> Unit, onRetentionChanged: (Int) -> Unit,
    onRefresh: () -> Unit, onDeleteConversation: (String) -> Unit,
    onDeleteDay: (LocalDate) -> Unit,
    onListTurns: (String) -> List<EncryptedMemoryStore.ArchivedTurn>,
    onDeleteTimeRange: (String, Long, Long) -> Unit, onClearArchive: () -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var opened by remember { mutableStateOf<EncryptedMemoryStore.ArchivedConversation?>(null) }
    var turns by remember { mutableStateOf(emptyList<EncryptedMemoryStore.ArchivedTurn>()) }
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var confirmDay by remember { mutableStateOf(false) }
    var retentionExpanded by remember { mutableStateOf(false) }
    var selectingDays by remember { mutableStateOf(false) }
    var selectedDays by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var confirmSelectedDays by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val fmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    val byDay = remember(conversations) {
        conversations.flatMap { session ->
            val first = java.time.Instant.ofEpochMilli(session.startedAtMillis).atZone(zone).toLocalDate()
            val last = java.time.Instant.ofEpochMilli(session.updatedAtMillis).atZone(zone).toLocalDate()
            generateSequence(first) { current -> if (current < last) current.plusDays(1) else null }
                .map { it to session }.toList()
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.distinctBy { session -> session.sessionId } }
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("ARCHIVIO CONVERSAZIONI", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Calendario dello storico: scegli un giorno per vedere le conversazioni, poi aprine una per leggere i messaggi o cancellare una fascia oraria.", color = Color(0xFF9A9DA4), fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = archiveEnabled, onCheckedChange = onArchiveEnabledChanged)
                Text(if (archiveEnabled) "Archiviazione attiva" else "Archiviazione in pausa", color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp)
                TextButton(onClick = onRefresh) { Text("Aggiorna", color = gold) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Conservazione: " + retentionLabel, color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp)
                Box {
                    TextButton(onClick = { retentionExpanded = true }) { Text("Modifica", color = gold) }
                    DropdownMenu(expanded = retentionExpanded, onDismissRequest = { retentionExpanded = false }) {
                        listOf(0 to "Mai (manuale)", 7 to "7 giorni", 30 to "30 giorni", 90 to "90 giorni").forEach { option ->
                            DropdownMenuItem(text = { Text(option.second) }, onClick = { onRetentionChanged(option.first); retentionExpanded = false })
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹", color = gold, fontSize = 22.sp) }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)), color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                TextButton(onClick = { month = month.plusMonths(1) }) { Text("›", color = gold, fontSize = 22.sp) }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("L","M","M","G","V","S","D").forEach { Text(it, color = Color(0xFF9A9DA4), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp) }
            }
            val blanks = month.atDay(1).dayOfWeek.value - 1
            val cells: List<LocalDate?> = List(blanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { date ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (date == null) Spacer(modifier = Modifier.padding(15.dp))
                            else {
                                val count = byDay[date]?.size ?: 0
                                Button(onClick = { if (selectingDays) selectedDays = if (date in selectedDays) selectedDays - date else selectedDays + date else day = date }, modifier = Modifier.fillMaxWidth(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (date in selectedDays) Color(0xFFB98D42) else if (date == day) gold else Color(0xFF292D34),
                                        contentColor = if (date == day || date in selectedDays) Color.Black else Color.White
                                    ), shape = RoundedCornerShape(8.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(date.dayOfMonth.toString(), fontSize = 11.sp)
                                        Text(if (count > 0) count.toString() else "·", fontSize = 8.sp)
                                    }
                                }
                            }
                        }
                    }
                    repeat(7-week.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selectingDays = !selectingDays; selectedDays = emptySet() }) {
                    Text(if (selectingDays) "Annulla selezione giorni" else "Seleziona più giorni", color = gold, fontSize = 11.sp)
                }
                if (selectedDays.isNotEmpty()) TextButton(onClick = { confirmSelectedDays = true }) {
                    Text("Elimina ${selectedDays.size} giorni", color = Color(0xFFE6A0A0), fontSize = 11.sp)
                }
            }
            Text("Conversazioni del " + day.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ITALIAN)), color = gold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            val sessions = byDay[day].orEmpty().sortedByDescending { it.startedAtMillis }
            if (sessions.isEmpty()) Text("Nessuna conversazione archiviata in questo giorno.", color = Color(0xFF9A9DA4), fontSize = 12.sp)
            sessions.forEach { session ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF252930)), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(session.preview.ifBlank { "Conversazione senza anteprima" }, color = Color.White, fontSize = 12.sp, maxLines = 2)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.startedAtMillis)) +
                            " · " + session.turnCount + " messaggi", color = Color(0xFF9A9DA4), fontSize = 10.sp)
                        Row {
                            TextButton(onClick = {
                                opened = session; turns = onListTurns(session.sessionId)
                                fromText = java.time.Instant.ofEpochMilli(session.startedAtMillis).atZone(zone).toLocalDateTime().format(fmt)
                                toText = java.time.Instant.ofEpochMilli(session.updatedAtMillis).atZone(zone).toLocalDateTime().format(fmt)
                            }) { Text("Apri", color = gold) }
                            TextButton(onClick = { onDeleteConversation(session.sessionId) }) { Text("Elimina", color = Color(0xFFE6A0A0)) }
                        }
                    }
                }
            }
            if (sessions.isNotEmpty()) Button(onClick = { confirmDay = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A292B))) { Text("Elimina l'intera giornata") }
            if (conversations.isNotEmpty()) TextButton(onClick = onClearArchive) { Text("SVUOTA TUTTO L'ARCHIVIO", color = Color(0xFFE6A0A0)) }
        }
    }
    if (confirmSelectedDays) AlertDialog(onDismissRequest = { confirmSelectedDays = false },
        title = { Text("Eliminare i giorni selezionati?") },
        text = { Text("Saranno cancellate tutte le trascrizioni di ${selectedDays.size} giornate selezionate. I giorni non selezionati e i ricordi personali resteranno intatti.") },
        confirmButton = { TextButton(onClick = { selectedDays.sorted().forEach(onDeleteDay); selectedDays = emptySet(); selectingDays = false; confirmSelectedDays = false }) { Text("Elimina", color = Color(0xFFB94A48)) } },
        dismissButton = { TextButton(onClick = { confirmSelectedDays = false }) { Text("Annulla") } })
    if (confirmDay) AlertDialog(onDismissRequest = { confirmDay = false },
        title = { Text("Eliminare l'intera giornata?") },
        text = { Text("Saranno cancellate le trascrizioni del giorno " + day + ". I ricordi personali resteranno intatti.") },
        confirmButton = { TextButton(onClick = { onDeleteDay(day); confirmDay = false }) { Text("Elimina", color = Color(0xFFB94A48)) } },
        dismissButton = { TextButton(onClick = { confirmDay = false }) { Text("Annulla") } })
    opened?.let { session ->
        val from = runCatching { LocalDateTime.parse(fromText, fmt).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
        val until = runCatching { LocalDateTime.parse(toText, fmt).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
        AlertDialog(onDismissRequest = { opened = null },
            title = { Text("Conversazione · " + session.turnCount + " messaggi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Leggi i messaggi e indica l'intervallo da cancellare. Formato: AAAA-MM-GG HH:MM.", color = Color(0xFF9A9DA4), fontSize = 10.sp)
                    OutlinedTextField(value = fromText, onValueChange = { fromText = it }, label = { Text("Dal") }, singleLine = true)
                    OutlinedTextField(value = toText, onValueChange = { toText = it }, label = { Text("Al") }, singleLine = true)
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).verticalScroll(rememberScrollState())) {
                        turns.forEach { turn ->
                            Text(java.time.Instant.ofEpochMilli(turn.timestampMillis).atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) +
                                " · " + turn.role + "\n" + turn.text, color = Color.White, fontSize = 11.sp)
                            Divider(color = Color(0xFF3B4049))
                        }
                    }
                }
            },
            confirmButton = { TextButton(enabled = from != null && until != null && until > from, onClick = {
                if (from != null && until != null && until > from) {
                    onDeleteTimeRange(session.sessionId, from, until + 60_000L)
                    turns = onListTurns(session.sessionId)
                    if (turns.isEmpty()) opened = null
                }
            }) { Text("Elimina intervallo", color = Color(0xFFB94A48)) } },
            dismissButton = { TextButton(onClick = { opened = null }) { Text("Chiudi", color = gold) } })
    }
}

    confirmAction?.let { action ->
        val isMemory = action.startsWith("memory:")
        val isConversation = action.startsWith("conversation:")
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
