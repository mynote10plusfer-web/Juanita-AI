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
fun DigitalIdentitySection(
    panel: Color,
    gold: Color,
    activeIdentityId: String?,
    onActiveIdentityChanged: (String?) -> Unit,
    onIdentitiesChanged: (List<DigitalIdentity>) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { DigitalIdentityStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var identities by remember { mutableStateOf<List<DigitalIdentity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<DigitalIdentity?>(null) }
    var dossierTarget by remember { mutableStateOf<DigitalIdentity?>(null) }

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
    var directness by remember { mutableStateOf(0.55f) }
    var sociability by remember { mutableStateOf(0.6f) }
    var spontaneity by remember { mutableStateOf(0.55f) }
    var patience by remember { mutableStateOf(0.7f) }
    var optimism by remember { mutableStateOf(0.65f) }
    var assertiveness by remember { mutableStateOf(0.5f) }
    var playfulness by remember { mutableStateOf(0.6f) }
    var discretion by remember { mutableStateOf(0.75f) }

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
        directness = 0.55f
        sociability = 0.6f
        spontaneity = 0.55f
        patience = 0.7f
        optimism = 0.65f
        assertiveness = 0.5f
        playfulness = 0.6f
        discretion = 0.75f
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
        directness = identity.personality.directness
        sociability = identity.personality.sociability
        spontaneity = identity.personality.spontaneity
        patience = identity.personality.patience
        optimism = identity.personality.optimism
        assertiveness = identity.personality.assertiveness
        playfulness = identity.personality.playfulness
        discretion = identity.personality.discretion
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
        Text("Puoi configurare Juanita e José, ma soltanto uno sarà attivo alla volta. La memoria delle conoscenze e delle esperienze è comune; personalità, voce e origine regionale restano distinte.", color = Color(0xFFB9BBC1), fontSize = 12.sp)
        Text("IDENTITÀ DIGITALI · SELEZIONE E ARCHIVIO CIFRATO", color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("La scelta vale dalla prossima conversazione: per cambiare identità termina prima la sessione in corso. Ogni identità ha un solo profilo modificabile: cambia paese, regione o località per cambiarne la caratterizzazione, senza creare copie. Il dossier culturale approvato resta disponibile offline.", color = Color(0xFF9A9DA4), fontSize = 11.sp)

        if (loading) Text("Caricamento identità…", color = Color.White, fontSize = 13.sp)
        if (identities.isNotEmpty()) {
            Text("CONFIGURAZIONI DISPONIBILI · ${identities.size}/2", color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            identities.forEach { identity ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF202329))) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(identity.displayName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("${if (identity.presentation == IdentityPresentation.FEMININE) "Femminile" else "Maschile"} · ${identity.origin.countryName}${if (identity.origin.region.isNotBlank()) " · ${identity.origin.region}" else ""}${if (identity.origin.community.isNotBlank()) " · ${identity.origin.community}" else ""}", color = Color(0xFFB9BBC1), fontSize = 12.sp)
                        if (identity.biography.isNotBlank()) Text(identity.biography, color = Color(0xFF9A9DA4), fontSize = 12.sp, maxLines = 3)
                        identity.culturalDossier?.takeIf { it.approvedAtMillis != null }?.let { dossier ->
                            Text("DOSSIER CULTURALE APPROVATO · v${dossier.version} · ${dossier.sources.size} fonti", color = Color(0xFF9BC7A5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            if (activeIdentityId == identity.id) "SELEZIONATA · sarà usata nella prossima sessione" else "Non selezionata",
                            color = if (activeIdentityId == identity.id) gold else Color(0xFF9A9DA4),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { dossierTarget = identity }) { Text(if (identity.culturalDossier?.approvedAtMillis != null) "Esamina / modifica dossier culturale" else "Esamina dossier culturali") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (activeIdentityId != identity.id) {
                                TextButton(onClick = { onActiveIdentityChanged(identity.id); message = "${identity.displayName} sarà usata nella prossima conversazione." }) {
                                    Text("Usa questa identità", color = gold)
                                }
                            } else {
                                TextButton(onClick = { onActiveIdentityChanged(null); message = "Tornato al profilo assistente standard." }) {
                                    Text("Disattiva identità digitale", color = gold)
                                }
                            }
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
                Text(if (editingId == null) "CONFIGURA UN’IDENTITÀ" else "MODIFICA CONFIGURAZIONE", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = name, onValueChange = {}, readOnly = true, label = { Text("Nome identità") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Presentazione", color = Color.White, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { presentation = IdentityPresentation.FEMININE; name = "Juanita" }, enabled = editingId == null || identities.firstOrNull { it.id == editingId }?.presentation == IdentityPresentation.FEMININE, colors = ButtonDefaults.buttonColors(containerColor = if (presentation == IdentityPresentation.FEMININE) gold else Color(0xFF30343B), contentColor = if (presentation == IdentityPresentation.FEMININE) Color.Black else Color.White), modifier = Modifier.weight(1f)) { Text("Juanita · femminile") }
                    Button(onClick = { presentation = IdentityPresentation.MASCULINE; name = "José" }, enabled = editingId == null || identities.firstOrNull { it.id == editingId }?.presentation == IdentityPresentation.MASCULINE, colors = ButtonDefaults.buttonColors(containerColor = if (presentation == IdentityPresentation.MASCULINE) gold else Color(0xFF30343B), contentColor = if (presentation == IdentityPresentation.MASCULINE) Color.Black else Color.White), modifier = Modifier.weight(1f)) { Text("José · maschile") }
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
                Text("MODO DI RELAZIONARSI", color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                TraitControl("Franchezza", directness, { directness = it })
                TraitControl("Socievolezza", sociability, { sociability = it })
                TraitControl("Spontaneità", spontaneity, { spontaneity = it })
                TraitControl("Pazienza", patience, { patience = it })
                TraitControl("Ottimismo realistico", optimism, { optimism = it })
                TraitControl("Assertività", assertiveness, { assertiveness = it })
                TraitControl("Giocosità", playfulness, { playfulness = it })
                TraitControl("Riservatezza", discretion, { discretion = it })
                Button(
                    onClick = {
                        if (name.isBlank() || countryName.isBlank() || languageTag.isBlank()) {
                            message = "Nome, paese e lingua preferita sono obbligatori."
                        } else {
                            val existing = editingId?.let { id -> identities.firstOrNull { it.id == id } }
                            val identityId = existing?.id ?: java.util.UUID.randomUUID().toString()
                            val identity = DigitalIdentity(
                                id = identityId,
                                displayName = name.trim(),
                                presentation = presentation,
                                origin = CulturalOrigin(countryCode.trim(), countryName.trim(), region.trim(), locality.trim(), community.trim()),
                                preferredLanguageTag = languageTag.trim(),
                                languages = otherLanguages.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                                personality = IdentityPersonality(
                                    warmth = warmth, humour = humour, curiosity = curiosity, seriousness = seriousness,
                                    directness = directness, sociability = sociability, spontaneity = spontaneity,
                                    patience = patience, optimism = optimism, assertiveness = assertiveness,
                                    playfulness = playfulness, discretion = discretion
                                ),
                                biography = biography.trim(),
                                culturalDossier = existing?.culturalDossier,
                                memoryNamespace = SHARED_IDENTITY_MEMORY_NAMESPACE,
                                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                                updatedAtMillis = System.currentTimeMillis()
                            )
                            scope.launch {
                                saving = true
                                runCatching { withContext(Dispatchers.IO) { if (existing == null) store.create(identity) else store.update(identity) } }
                                    .onSuccess { saved ->
                                        identities = withContext(Dispatchers.IO) { store.list() }
                                        onIdentitiesChanged(identities)
                                        message = "Configurazione ${saved.displayName} salvata. La memoria comune non è stata modificata."
                                        resetForm()
                                    }
                                    .onFailure { message = "Salvataggio non riuscito: ${it.localizedMessage ?: "errore"}" }
                                saving = false
                            }
                        }
                    },
                    enabled = !saving && (editingId != null || (identities.size < 2 && identities.none { it.presentation == presentation })),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
                ) { Text(if (saving) "Salvataggio…" else if (editingId == null) "Crea identità" else "Salva modifiche") }
                if (editingId != null) TextButton(onClick = { resetForm(); message = "Modifiche annullate." }) { Text("Annulla modifica", color = gold) }
            }
        }
        message?.let { Text(it, color = if (it.contains("non riuscito") || it.contains("obbligatori") || it.contains("non leggibile")) Color(0xFFFFAB91) else Color(0xFFB9BBC1), fontSize = 12.sp) }
    }

    dossierTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { dossierTarget = null },
            title = { Text("Ricerca culturale · ${target.displayName}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Scegli un dossier documentato da esaminare. Il contenuto è incluso nell'app e disponibile offline. Nulla viene applicato finché non premi Approva.",
                        fontSize = 12.sp
                    )
                    CulturalDossierCatalog.dossiers.forEach { proposal ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF202329))) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(proposal.sources.firstOrNull()?.publisher ?: "Fonti culturali", color = gold, fontWeight = FontWeight.Bold)
                                Text(proposal.summary, color = Color.White, fontSize = 11.sp)
                                Text("FONTI VERIFICABILI", color = gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                proposal.sources.forEach { source ->
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(source.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Text(source.publisher, color = Color(0xFFB9BBC1), fontSize = 10.sp)
                                        Text(source.url, color = Color(0xFF8AB4F8), fontSize = 9.sp)
                                        Text(source.summary, color = Color(0xFFB9BBC1), fontSize = 10.sp)
                                    }
                                }
                                Button(
                                    onClick = {
                                        val approved = proposal.copy(
                                            version = (target.culturalDossier?.version ?: 0) + 1,
                                            approvedAtMillis = System.currentTimeMillis(),
                                            sources = proposal.sources
                                        )
                                        scope.launch {
                                            saving = true
                                            runCatching {
                                                withContext(Dispatchers.IO) { store.update(target.copy(culturalDossier = approved)) }
                                            }.onSuccess { saved ->
                                                identities = withContext(Dispatchers.IO) { store.list() }
                                                onIdentitiesChanged(identities)
                                                message = "Dossier culturale approvato per ${saved.displayName}. È salvato in locale e verrà usato nelle prossime conversazioni."
                                                dossierTarget = null
                                            }.onFailure {
                                                message = "Applicazione del dossier non riuscita: ${it.localizedMessage ?: "errore"}"
                                            }
                                            saving = false
                                        }
                                    },
                                    enabled = !saving,
                                    colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Approva e applica questo dossier") }
                            }
                        }
                    }
                    if (target.culturalDossier != null) {
                        TextButton(onClick = {
                            scope.launch {
                                saving = true
                                runCatching { withContext(Dispatchers.IO) { store.update(target.copy(culturalDossier = null)) } }
                                    .onSuccess {
                                        identities = withContext(Dispatchers.IO) { store.list() }
                                        onIdentitiesChanged(identities)
                                        message = "Dossier culturale revocato. L'identità userà solo il suo profilo individuale."
                                        dossierTarget = null
                                    }.onFailure { message = "Revoca non riuscita: ${it.localizedMessage ?: "errore"}" }
                                saving = false
                            }
                        }) { Text("Revoca dossier da questa identità", color = Color(0xFFFF8A80)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dossierTarget = null }) { Text("Chiudi") } }
        )
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Eliminare ${candidate.displayName}?") },
            text = { Text("Verrà eliminata soltanto questa configurazione. L'altra identità, la memoria comune e il profilo personale dell'utente resteranno intatti.") },
            confirmButton = { TextButton(onClick = {
                val target = candidate
                deleteCandidate = null
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { store.delete(target.id) } }
                        .onSuccess {
                            if (activeIdentityId == target.id) onActiveIdentityChanged(null)
                            identities = withContext(Dispatchers.IO) { store.list() }
                            onIdentitiesChanged(identities)
                            if (editingId == target.id) resetForm()
                            message = "Configurazione ${target.displayName} eliminata. La memoria comune è stata conservata."
                        }
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
