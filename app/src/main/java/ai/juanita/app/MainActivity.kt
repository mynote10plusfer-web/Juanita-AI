package ai.juanita.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import ai.juanita.app.ai.GeminiLiveAssistant
import ai.juanita.app.ai.PersonaProfile
import ai.juanita.app.data.AssistantPreferences
import ai.juanita.app.memory.EncryptedMemoryStore
import ai.juanita.app.memory.MemoryItem
import java.util.Locale
import kotlinx.coroutines.launch

private data class VoiceLanguage(val label: String, val tag: String) {
    val locale: Locale get() = Locale.forLanguageTag(tag)
}

private data class RegionalDialect(
    val id: String,
    val label: String,
    val languageTag: String,
    val sample: String,
    val note: String
)

private val voiceLanguages = listOf(
    VoiceLanguage("Español (España)", "es-ES"),
    VoiceLanguage("Italiano", "it-IT"),
    VoiceLanguage("Čeština", "cs-CZ"),
    VoiceLanguage("English", "en-US"),
    VoiceLanguage("Español (Colombia)", "es-CO")
)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private lateinit var preferences: AssistantPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AssistantPreferences(applicationContext)
        val memoryStore = EncryptedMemoryStore(applicationContext)
        tts = TextToSpeech(this) { result -> ttsReady = result == TextToSpeech.SUCCESS }

        val savedPersona = PersonaProfile(
            name = preferences.assistantName,
            irony = preferences.irony,
            sweetness = preferences.sweetness,
            sensuality = preferences.sensuality,
            seriousness = preferences.seriousness,
            friendliness = preferences.friendliness,
            curiosity = preferences.curiosity,
            expressiveness = preferences.expressiveness,
            formality = preferences.formality,
            empathy = preferences.empathy
        )

        setContent {
            JuanitaApp(
                initialLanguageTag = preferences.languageTag,
                initialDialectId = preferences.dialectId,
                initialName = preferences.assistantName,
                initialWakePhrase = preferences.wakePhrase,
                initialPersona = savedPersona,
                initialArchiveEnabled = memoryStore.isConversationArchiveEnabled,
                initialMemories = memoryStore.listMemories(),
                onArchiveEnabledChanged = { memoryStore.setConversationArchiveEnabled(it) },
                onSaveMemory = { memoryStore.addMemory(it) },
                onDeleteMemory = { memoryStore.deleteMemory(it) },
                onLanguageSelected = { preferences.languageTag = it },
                onDialectSelected = { dialectId, languageTag ->
                    preferences.dialectId = dialectId
                    preferences.languageTag = languageTag
                },
                onNameChanged = { preferences.assistantName = it },
                onWakePhraseChanged = { preferences.wakePhrase = it },
                onPersonaChanged = { profile ->
                    preferences.irony = profile.irony
                    preferences.sweetness = profile.sweetness
                    preferences.sensuality = profile.sensuality
                    preferences.seriousness = profile.seriousness
                    preferences.friendliness = profile.friendliness
                    preferences.curiosity = profile.curiosity
                    preferences.expressiveness = profile.expressiveness
                    preferences.formality = profile.formality
                    preferences.empathy = profile.empathy
                },
                onSpeak = { text, language ->
                    val engine = tts
                    when {
                        !ttsReady || engine == null -> "Il motore vocale Android non è ancora pronto."
                        engine.isLanguageAvailable(language.locale) < TextToSpeech.LANG_AVAILABLE ->
                            "La voce ${language.label} non è installata o non è supportata."
                        else -> {
                            val languageResult = engine.setLanguage(language.locale)
                            if (languageResult < TextToSpeech.LANG_AVAILABLE) {
                                "Impossibile attivare la lingua ${language.label}."
                            } else {
                                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "juanita-preview")
                                if (result == TextToSpeech.ERROR) "Errore durante la riproduzione."
                                else "Voce di sistema avviata: ${language.label}. Il timbro regionale non è garantito."
                            }
                        }
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        super.onDestroy()
    }
}

@Composable
private fun JuanitaApp(
    initialLanguageTag: String,
    initialDialectId: String,
    initialName: String,
    initialWakePhrase: String,
    initialPersona: PersonaProfile,
    initialArchiveEnabled: Boolean,
    initialMemories: List<MemoryItem>,
    onArchiveEnabledChanged: (Boolean) -> Unit,
    onSaveMemory: (String) -> MemoryItem,
    onDeleteMemory: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onDialectSelected: (String, String) -> Unit,
    onNameChanged: (String) -> Unit,
    onWakePhraseChanged: (String) -> Unit,
    onPersonaChanged: (PersonaProfile) -> Unit,
    onSpeak: (String, VoiceLanguage) -> String
) {
    val background = Color(0xFF090A0C)
    val panel = Color(0xFF17191D)
    val gold = Color(0xFFE4BD69)
    val initialLanguage = voiceLanguages.firstOrNull { it.tag == initialLanguageTag } ?: voiceLanguages.first()
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    val dialects = listOf(
        RegionalDialect("sicilian-palermo", "Siciliano · area palermitana", "it-IT",
            "Chi bedda jurnata! Nni facemu 'na passiata?",
            "Profilo sperimentale: siciliano palermitano colloquiale; lessico e pronuncia da perfezionare con parlanti nativi."),
        RegionalDialect("canarian", "Español canario", "es-ES",
            "¡Qué día más bueno! ¿Nos damos una vueltita por la isla?",
            "Profilo sperimentale: spagnolo canario colloquiale, ritmo rilassato e lessico delle isole; timbro nativo non garantito."),
        RegionalDialect("paisa", "Colombiano paisa", "es-CO",
            "¡Qué más pues! ¿Cómo vamos? Nos damos una vueltica, ¿o qué?",
            "Profilo sperimentale: spagnolo paisa di Antioquia/Medellín, espressioni locali e tono cordiale; timbro nativo non garantito.")
    )
    var selectedDialect by remember {
        mutableStateOf(dialects.firstOrNull { it.id == initialDialectId } ?: dialects[1])
    }
    var dialectMenuExpanded by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("Qui apparirà il testo riconosciuto.") }
    var status by remember { mutableStateOf("Impostazioni salvate sul dispositivo") }
    var assistantName by remember { mutableStateOf(initialName) }
    var wakePhrase by remember { mutableStateOf(initialWakePhrase) }
    var persona by remember { mutableStateOf(initialPersona) }
    var archiveEnabled by remember { mutableStateOf(initialArchiveEnabled) }
    var memoryDraft by remember { mutableStateOf("") }
    var memories by remember { mutableStateOf(initialMemories) }
    var liveState by remember { mutableStateOf("Non connessa") }
    val liveAssistant = remember { GeminiLiveAssistant() }
    val liveScope = rememberCoroutineScope()
    val livePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            liveScope.launch {
                liveState = "Connessione a Gemini Live…"
                try {
                    liveAssistant.start(persona.toSystemInstruction(selectedLanguage.label, selectedDialect.label))
                    liveState = "Juanita è in ascolto. Puoi parlare normalmente."
                } catch (error: Exception) {
                    liveState = "Connessione non riuscita: ${error.localizedMessage ?: "controlla App Check e rete"}"
                }
            }
        } else liveState = "Permesso microfono negato"
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                transcript = spoken
                status = "Voce riconosciuta. Il dialogo AI non è ancora collegato."
            } else status = "Nessun testo riconosciuto"
        } else status = "Riconoscimento vocale annullato o non disponibile"
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = gold,
            onPrimary = Color.Black,
            secondary = gold,
            onSecondary = Color.Black,
            background = background,
            onBackground = Color.White,
            surface = panel,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF242830),
            onSurfaceVariant = Color(0xFFE1E3E8),
            outline = Color(0xFF8C929C)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = background) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = assistantName.uppercase(), color = gold, fontSize = 29.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text("PERSONAL VOICE ASSISTANT", color = Color(0xFF9A9DA4), fontSize = 11.sp, letterSpacing = 2.sp)
                JuanitaAvatarCard()

                SettingsCard(panel, gold, "IDENTITÀ E ATTIVAZIONE") {
                    OutlinedTextField(
                        value = assistantName,
                        onValueChange = {
                            assistantName = it
                            onNameChanged(it)
                        },
                        label = { Text("Nome dell'assistente") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = wakePhrase,
                        onValueChange = {
                            wakePhrase = it
                            onWakePhraseChanged(it)
                        },
                        label = { Text("Parola/frase di attivazione") },
                        supportingText = { Text("Esempio: Hola ${assistantName.ifBlank { "Juanita" }}. Il riconoscimento automatico non è ancora implementato.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Il nome e la frase vengono salvati sul telefono. La wake word sarà collegata al motore di ascolto in una fase successiva.",
                        color = Color(0xFF9A9DA4), fontSize = 12.sp)
                }

                SettingsCard(panel, gold, "CARATTERE E PERSONALITÀ") {
                    Text("Regola ogni tratto da 0 a 100%. Le preferenze vengono salvate automaticamente.",
                        color = Color(0xFF9A9DA4), fontSize = 12.sp)
                    TraitSlider("Ironia", persona.irony) { persona = persona.copy(irony = it); onPersonaChanged(persona) }
                    TraitSlider("Dolcezza", persona.sweetness) { persona = persona.copy(sweetness = it); onPersonaChanged(persona) }
                    TraitSlider("Sensualità", persona.sensuality) { persona = persona.copy(sensuality = it); onPersonaChanged(persona) }
                    TraitSlider("Serietà", persona.seriousness) { persona = persona.copy(seriousness = it); onPersonaChanged(persona) }
                    TraitSlider("Simpatia", persona.friendliness) { persona = persona.copy(friendliness = it); onPersonaChanged(persona) }
                    TraitSlider("Intelligenza e curiosità", persona.curiosity) { persona = persona.copy(curiosity = it); onPersonaChanged(persona) }
                    TraitSlider("Espressività", persona.expressiveness) { persona = persona.copy(expressiveness = it); onPersonaChanged(persona) }
                    TraitSlider("Formalità", persona.formality) { persona = persona.copy(formality = it); onPersonaChanged(persona) }
                    TraitSlider("Empatia", persona.empathy) { persona = persona.copy(empathy = it); onPersonaChanged(persona) }
                    Text("Questi parametri vengono inclusi nelle istruzioni inviate a Gemini Live e influenzano il carattere di Juanita.",
                        color = gold, fontSize = 11.sp)
                }

                SettingsCard(panel, gold, "LINGUA DELLA VOCE") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedLanguage.label, color = Color.White, modifier = Modifier.weight(1f))
                        TextButton(onClick = { languageMenuExpanded = true }) { Text("Cambia", color = gold) }
                        DropdownMenu(expanded = languageMenuExpanded, onDismissRequest = { languageMenuExpanded = false }) {
                            voiceLanguages.forEach { language ->
                                DropdownMenuItem(text = { Text(language.label) }, onClick = {
                                    selectedLanguage = language
                                    onLanguageSelected(language.tag)
                                    languageMenuExpanded = false
                                    status = "Lingua ${language.label} salvata"
                                })
                            }
                        }
                    }
                    Text("La lingua selezionata è separata dal carattere e dal dialetto.", color = Color(0xFF9A9DA4), fontSize = 12.sp)
                }

                SettingsCard(panel, gold, "ACCENTO E VARIANTE REGIONALE") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedDialect.label, color = Color.White, modifier = Modifier.weight(1f))
                        TextButton(onClick = { dialectMenuExpanded = true }) { Text("Scegli", color = gold) }
                        DropdownMenu(expanded = dialectMenuExpanded, onDismissRequest = { dialectMenuExpanded = false }) {
                            dialects.forEach { dialect ->
                                DropdownMenuItem(text = { Text(dialect.label) }, onClick = {
                                    selectedDialect = dialect
                                    val language = voiceLanguages.firstOrNull { it.tag == dialect.languageTag } ?: selectedLanguage
                                    selectedLanguage = language
                                    onLanguageSelected(language.tag)
                                    onDialectSelected(dialect.id, dialect.languageTag)
                                    dialectMenuExpanded = false
                                    status = "Profilo ${dialect.label} selezionato"
                                })
                            }
                        }
                    }
                    Text(selectedDialect.note, color = Color(0xFF9A9DA4), fontSize = 12.sp)
                    Text("Esempio: ${selectedDialect.sample}", color = Color.White, fontSize = 14.sp)
                    Button(onClick = { status = onSpeak(selectedDialect.sample, selectedLanguage) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                        Text("  Ascolta esempio")
                    }
                }

                SettingsCard(panel, gold, "PROVA MICROFONO") {
                    Text(transcript, color = Color.White, fontSize = 16.sp)
                    Button(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage.tag)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguage.tag)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parla con ${assistantName.ifBlank { "Juanita" }}")
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        }
                        try {
                            speechLauncher.launch(intent)
                            status = "Ascolto tramite il servizio vocale Android…"
                        } catch (_: Exception) {
                            status = "Nessun servizio di riconoscimento vocale disponibile"
                        }
                    }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Text("  Parla")
                    }
                }

                SettingsCard(panel, gold, "CONVERSAZIONE VOCALE LIVE") {
                    Text("Motore Gemini Live · ${GeminiLiveAssistant.MODEL_NAME}", color = Color(0xFF9A9DA4), fontSize = 12.sp)
                    Text(liveState, color = Color.White, fontSize = 14.sp)
                    Button(
                        onClick = { livePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Text("  Avvia conversazione")
                    }
                    Button(
                        onClick = {
                            liveScope.launch {
                                liveAssistant.stop()
                                liveState = "Conversazione terminata"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
                    ) { Text("Termina conversazione") }
                    Text("Il microfono si attiva solo dopo il tuo consenso e quando premi Avvia. Audio in streaming verso Gemini; non viene archiviato nell'app.",
                        color = Color(0xFF9A9DA4), fontSize = 11.sp)
                }

                SettingsCard(panel, gold, "MEMORIA DI JUANITA") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = archiveEnabled,
                            onCheckedChange = {
                                archiveEnabled = it
                                onArchiveEnabledChanged(it)
                                status = if (it) "Consenso archivio conversazioni attivato" else "Archivio conversazioni in pausa"
                            }
                        )
                        Text("Consenti archiviazione delle conversazioni", color = Color.White, fontSize = 13.sp)
                    }
                    Text(
                        "Disattivato per impostazione predefinita. I ricordi salvati manualmente restano cifrati sul telefono. Lo streaming audio Live non viene archiviato da questa app.",
                        color = Color(0xFF9A9DA4), fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = memoryDraft,
                        onValueChange = { memoryDraft = it },
                        label = { Text("Ricordo da conservare") },
                        placeholder = { Text("Scrivi un'informazione non sensibile…") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (memoryDraft.isNotBlank()) {
                                val saved = onSaveMemory(memoryDraft)
                                memories = listOf(saved) + memories
                                memoryDraft = ""
                                status = "Ricordo salvato in forma cifrata sul dispositivo"
                            }
                        },
                        enabled = memoryDraft.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
                    ) { Text("Salva ricordo") }
                    if (memories.isEmpty()) {
                        Text("Nessun ricordo salvato.", color = Color(0xFF9A9DA4), fontSize = 12.sp)
                    } else {
                        memories.take(20).forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.text, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    onDeleteMemory(item.id)
                                    memories = memories.filterNot { it.id == item.id }
                                    status = "Ricordo eliminato"
                                }) { Text("Elimina", color = gold) }
                            }
                        }
                    }
                    Text("La memoria automatica delle conversazioni non è ancora attiva. Puoi salvare e cancellare ricordi manualmente.",
                        color = gold, fontSize = 11.sp)
                }

                if (BuildConfig.DEBUG) AppCheckDebugPanel()

                SettingsCard(panel, gold, "STATO DEL PROGETTO") {
                    Text(status, color = Color(0xFFB9BBC1), fontSize = 13.sp)
                    Text("Gemini Live è integrato per le prove vocali. La wake word, la memoria automatica e le voci regionali native sono ancora in sviluppo.",
                        color = Color(0xFF9A9DA4), fontSize = 12.sp)
                }
                Text("Juanita AI • Versione ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    color = Color(0xFF686C74), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    panel: Color,
    gold: Color,
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = panel),
        shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun TraitSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
            Text("${(value * 100).toInt()}%", color = Color(0xFFE4BD69), fontSize = 12.sp)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..1f)
    }
}
