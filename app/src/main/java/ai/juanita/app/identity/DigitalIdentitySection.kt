package ai.juanita.app.identity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Phase A UI: local identity management only; no voice-session switching yet. */
@Composable
fun DigitalIdentitySection(panel: Color, gold: Color) {
    val context = LocalContext.current
    val store = remember(context) { DigitalIdentityStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var identities by remember { mutableStateOf<List<DigitalIdentity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<DigitalIdentity?>(null) }

    var name by remember { mutableStateOf("Juanita") }
    var presentation by remember { mutableStateOf(IdentityPresentation.FEMININE) }
    var countryName by remember { mutableStateOf("Colombia") }
    var countryCode by remember { mutableStateOf("CO") }
    var region by remember { mutableStateOf("Antioquia") }
    var locality by remember { mutableStateOf("Medellín") }
    var community by remember { mutableStateOf("Paisa") }
    var languageTag by remember { mutableStateOf("es-CO") }
    var otherLanguages by remember { mutableStateOf("it-IT, es-ES") }
    var biography by remember { mutableStateOf("") }
    var warmth by remember { mutableStateOf(0.7f) }
    var humour by remember { mutableStateOf(0.6f) }
    var curiosity by remember { mutableStateOf(0.8f) }
    var seriousness by remember { mutableStateOf(0.5f) }

    fun resetForm() {
        editingId = null
        name = "Juanita"
        presentation = IdentityPresentation.FEMININE
        countryName = "Colombia"
        countryCode = "CO"
        region = "Antioquia"
        locality = "Medellín"
        community = "Paisa"
        languageTag = "es-CO"
        otherLanguages = "it-IT, es-ES"
        biography = ""
        warmth = 0.7f
        humour = 0.6f
        curiosity = 0.8f
        seriousness = 0.5f
    }

    fun loadForEdit(identity: DigitalIdentity) {
        editingId = identity.id
        name = identity.displayName
        presentation = identity.presentation
        countryName = identity.origin.countryName
        countryCode = identity.origin.countryCode
        region = identity.origin.region
        locality = identity.origin.locality
        community = identity.origin.community
        languageTag = identity.preferredLanguageTag
        otherLanguages = identity.languages.joinToString(", ")
        biography = identity.biography
        warmth = identity.personality.warmth
        humour = identity.personality.humour
        curiosity = identity.personality.curiosity
        seriousness = identity.personality.seriousness
        message = "Stai modificando ${identity.displayName}."
    }

    fun refresh() {
        scope.launch {
            loading = true
            runCatching { withContext(Dispatchers.IO) { store.list() } }
                .onSuccess { identities = it; message = null }
                .onFailure { message = "Archivio non leggibile: ${it.localizedMessage ?: "errore di decifratura"}. I dati originali sono stati preservati." }
            loading = false
        }
    }

    LaunchedEffect(store) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("IDENTITÀ DIGITALI", color = gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("Crea personaggi indipendenti. Ognuno avrà una propria biografia, personalità, origine culturale e memoria separata.", color = Color(0xFFB9BBC1), fontSize = 12.sp)
        Text("FASE A · ARCHIVIO LOCALE CIFRATO", color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("In questa fase gestisci le schede sul telefono. La ricerca culturale online e il collegamento alla conversazione vocale arriveranno dopo i test dell'archivio.", color = Color(0xFF9A9DA4), fontSize = 11.sp)

        if (loading) Text("Caricamento identità…", color = Color.White, fontSize = 13.sp)
        if (identities.isNotEmpty()) {
            Text("PERSONAGGI SALVATI · ${identities.size}", color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            identities.forEach { identity ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF202329))) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(identity.displayName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("${if (identity.presentation == IdentityPresentation.FEMININE) "Femminile" else "Maschile"} · ${identity.origin.countryName}${if (identity.origin.region.isNotBlank()) " · ${identity.origin.region}" else ""}${if (identity.origin.community.isNotBlank()) " · ${identity.origin.community}" else ""}", color = Color(0xFFB9BBC1), fontSize = 12.sp)
                        if (identity.biography.isNotBlank()) Text(identity.biography, color = Color(0xFF9A9DA4), fontSize = 12.sp, maxLines = 3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { loadForEdit(identity) }) { Text("Modifica", color = gold) }
                            TextButton(onClick = { deleteCandidate = identity }) { Text("Elimina", color = Color(0xFFFF8A80)) }
                        }
                    }
                }
            }
        } else if (!loading) {
            Text("Non hai ancora creato identità digitali.", color = Color(0xFF9A9DA4), fontSize = 12.sp)
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = panel)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (editingId == null) "CREA UNA NUOVA IDENTITÀ" else "MODIFICA IDENTITÀ", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome del personaggio") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Presentazione", color = Color.White, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { presentation = IdentityPresentation.FEMININE }, colors = ButtonDefaults.buttonColors(containerColor = if (presentation == IdentityPresentation.FEMININE) gold else Color(0xFF30343B), contentColor = if (presentation == IdentityPresentation.FEMININE) Color.Black else Color.White), modifier = Modifier.weight(1f)) { Text("Juanita · femminile") }
                    Button(onClick = { presentation = IdentityPresentation.MASCULINE }, colors = ButtonDefaults.buttonColors(containerColor = if (presentation == IdentityPresentation.MASCULINE) gold else Color(0xFF30343B), contentColor = if (presentation == IdentityPresentation.MASCULINE) Color.Black else Color.White), modifier = Modifier.weight(1f)) { Text("José · maschile") }
                }
                OutlinedTextField(value = countryName, onValueChange = { countryName = it }, label = { Text("Paese") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = countryCode, onValueChange = { countryCode = it.uppercase(Locale.ROOT).take(2) }, label = { Text("Codice paese · es. CO, ES, IT") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = region, onValueChange = { region = it }, label = { Text("Regione / provincia") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = locality, onValueChange = { locality = it }, label = { Text("Città / località") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = community, onValueChange = { community = it }, label = { Text("Identità culturale / area locale") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = languageTag, onValueChange = { languageTag = it }, label = { Text("Lingua preferita · es. es-CO") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = otherLanguages, onValueChange = { otherLanguages = it }, label = { Text("Altre lingue · codici separati da virgola") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = biography, onValueChange = { biography = it }, label = { Text("Biografia iniziale (facoltativa)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("PERSONALITÀ INIZIALE", color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                TraitControl("Calore", warmth, { warmth = it })
                TraitControl("Umorismo", humour, { humour = it })
                TraitControl("Curiosità", curiosity, { curiosity = it })
                TraitControl("Serietà", seriousness, { seriousness = it })
                Button(
                    onClick = {
                        if (name.isBlank() || countryName.isBlank() || languageTag.isBlank()) {
                            message = "Nome, paese e lingua preferita sono obbligatori."
                        } else {
                            val existing = editingId?.let { id -> identities.firstOrNull { it.id == id } }
                            val identity = DigitalIdentity(
                                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                                displayName = name.trim(),
                                presentation = presentation,
                                origin = CulturalOrigin(countryCode.trim(), countryName.trim(), region.trim(), locality.trim(), community.trim()),
                                preferredLanguageTag = languageTag.trim(),
                                languages = otherLanguages.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                                personality = IdentityPersonality(warmth = warmth, humour = humour, curiosity = curiosity, seriousness = seriousness),
                                biography = biography.trim(),
                                culturalDossier = existing?.culturalDossier,
                                memoryNamespace = existing?.memoryNamespace ?: (existing?.id ?: ""),
                                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                                updatedAtMillis = System.currentTimeMillis()
                            ).let { candidate -> if (existing == null) candidate.copy(memoryNamespace = candidate.id) else candidate }
                            scope.launch {
                                saving = true
                                runCatching { withContext(Dispatchers.IO) { if (existing == null) store.create(identity) else store.update(identity) } }
                                    .onSuccess { saved ->
                                        identities = withContext(Dispatchers.IO) { store.list() }
                                        message = "${saved.displayName} è stata salvata in modo indipendente."
                                        resetForm()
                                    }
                                    .onFailure { message = "Salvataggio non riuscito: ${it.localizedMessage ?: "errore"}" }
                                saving = false
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
                ) { Text(if (saving) "Salvataggio…" else if (editingId == null) "Crea identità" else "Salva modifiche") }
                if (editingId != null) TextButton(onClick = { resetForm(); message = "Modifiche annullate." }) { Text("Annulla modifica", color = gold) }
            }
        }
        message?.let { Text(it, color = if (it.contains("non riuscito") || it.contains("obbligatori") || it.contains("non leggibile")) Color(0xFFFFAB91) else Color(0xFFB9BBC1), fontSize = 12.sp) }
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Eliminare ${candidate.displayName}?") },
            text = { Text("Verrà eliminata soltanto questa scheda di identità. Le altre identità e il profilo personale dell'utente non verranno toccati. Le memorie collegate non sono ancora integrate in questa fase.") },
            confirmButton = { TextButton(onClick = {
                val target = candidate
                deleteCandidate = null
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { store.delete(target.id) } }
                        .onSuccess { identities = withContext(Dispatchers.IO) { store.list() }; if (editingId == target.id) resetForm(); message = "Identità ${target.displayName} eliminata." }
                        .onFailure { message = "Eliminazione non riuscita: ${it.localizedMessage ?: "errore"}" }
                }
            }) { Text("Elimina", color = Color(0xFFFF8A80)) } },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun TraitControl(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp)
            Text("${(value * 100).toInt()}%", color = Color(0xFFE4BD69), fontSize = 11.sp)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..1f)
    }
}
