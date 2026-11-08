package ai.juanita.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.IBinder
import android.os.Bundle
import android.view.WindowManager
import android.speech.RecognizerIntent
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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
import ai.juanita.app.ai.GeminiLiveAssistant
import ai.juanita.app.ai.PersonaProfile
import ai.juanita.app.avatar.AvatarProfile
import ai.juanita.app.avatar.AvatarProfileStore
import ai.juanita.app.avatar.AvatarStudioSection
import ai.juanita.app.avatar.AvatarCallScreen
import ai.juanita.app.data.AssistantPreferences
import ai.juanita.app.alarm.AlarmSection
import ai.juanita.app.memory.EncryptedMemoryStore
import ai.juanita.app.memory.MemoryItem
import ai.juanita.app.memory.ImprovementReport
import java.util.Locale
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
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

private data class LiveVoice(
    val id: String,
    val label: String,
    val quality: String,
    val gender: String
)

private val liveVoices = listOf(
    // Voci femminili — catalogo Gemini Live
    LiveVoice("Kore", "Kore", "Ferma ed equilibrata", "FEMMINILE"),
    LiveVoice("Aoede", "Aoede", "Fresca e ariosa", "FEMMINILE"),
    LiveVoice("Sulafat", "Sulafat", "Calda e avvolgente", "FEMMINILE"),
    LiveVoice("Achernar", "Achernar", "Morbida e delicata", "FEMMINILE"),
    LiveVoice("Vindemiatrix", "Vindemiatrix", "Dolce e gentile", "FEMMINILE"),
    LiveVoice("Zephyr", "Zephyr", "Vivace e luminosa", "FEMMINILE"),
    LiveVoice("Pulcherrima", "Pulcherrima", "Decisa e presente", "FEMMINILE"),
    LiveVoice("Autonoe", "Autonoe", "Brillante", "FEMMINILE"),
    LiveVoice("Callirrhoe", "Callirrhoe", "Rilassata e naturale", "FEMMINILE"),
    LiveVoice("Despina", "Despina", "Morbida e fluida", "FEMMINILE"),
    LiveVoice("Erinome", "Erinome", "Chiara", "FEMMINILE"),
    LiveVoice("Gacrux", "Gacrux", "Matura", "FEMMINILE"),
    LiveVoice("Laomedeia", "Laomedeia", "Vivace ed energica", "FEMMINILE"),
    LiveVoice("Leda", "Leda", "Giovane e fresca", "FEMMINILE"),

    // Voci maschili — catalogo Gemini Live
    LiveVoice("Achird", "Achird", "Amichevole", "MASCHILE"),
    LiveVoice("Algenib", "Algenib", "Profonda e roca", "MASCHILE"),
    LiveVoice("Algieba", "Algieba", "Morbida e fluida", "MASCHILE"),
    LiveVoice("Alnilam", "Alnilam", "Ferma e decisa", "MASCHILE"),
    LiveVoice("Charon", "Charon", "Informativa e chiara", "MASCHILE"),
    LiveVoice("Enceladus", "Enceladus", "Bassa e ariosa", "MASCHILE"),
    LiveVoice("Fenrir", "Fenrir", "Energica", "MASCHILE"),
    LiveVoice("Iapetus", "Iapetus", "Chiara", "MASCHILE"),
    LiveVoice("Orus", "Orus", "Ferma e autorevole", "MASCHILE"),
    LiveVoice("Puck", "Puck", "Vivace", "MASCHILE"),
    LiveVoice("Rasalgethi", "Rasalgethi", "Informativa", "MASCHILE"),
    LiveVoice("Sadachbia", "Sadachbia", "Vivace e dinamica", "MASCHILE"),
    LiveVoice("Sadaltager", "Sadaltager", "Esperta e sicura", "MASCHILE"),
    LiveVoice("Schedar", "Schedar", "Neutra ed equilibrata", "MASCHILE"),
    LiveVoice("Umbriel", "Umbriel", "Rilassata e colloquiale", "MASCHILE"),
    LiveVoice("Zubenelgenubi", "Zubenelgenubi", "Disinvolta e naturale", "MASCHILE")
)

private val voiceLanguages = listOf(
    VoiceLanguage("Español (España)", "es-ES"),
    VoiceLanguage("Italiano", "it-IT"),
    VoiceLanguage("Čeština", "cs-CZ"),
    VoiceLanguage("English", "en-US"),
    VoiceLanguage("Español (Colombia)", "es-CO")
)

class MainActivity : ComponentActivity() {
    private lateinit var preferences: AssistantPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AssistantPreferences(applicationContext)
        val memoryStore = EncryptedMemoryStore(applicationContext)
        val avatarStore = AvatarProfileStore(applicationContext)

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
                initialOnboardingCompleted = preferences.hasCompletedOnboarding,
                onCompleteOnboarding = { entries ->
                    entries.filter { it.isNotBlank() }.forEach { entry ->
                        memoryStore.addMemory(entry, category = "profile", source = "onboarding", confidence = 1f, confirmedByUser = true)
                    }
                    preferences.hasCompletedOnboarding = true
                },
                initialDialectId = preferences.dialectId,
                initialName = preferences.assistantName,
                initialLiveVoiceName = preferences.liveVoiceName,
                initialWakePhrase = preferences.wakePhrase,
                initialPersona = savedPersona,
                initialArchiveEnabled = memoryStore.isConversationArchiveEnabled,
                initialAutoMemoryEnabled = memoryStore.isAutomaticMemoryEnabled,
                initialMemories = memoryStore.listMemories(),
                initialArchivedConversations = memoryStore.listArchivedConversations(),
                initialImprovementReports = memoryStore.listImprovementReports(),
                onSaveImprovementReport = { title, type, area, observed, evidence, expected, acceptance ->
                    memoryStore.addImprovementReport(title, type, area, observed, evidence, expected, acceptance)
                },
                onRefreshImprovementReports = { memoryStore.listImprovementReports() },
                onDeleteImprovementReports = { memoryStore.deleteImprovementReports(it) },
                initialAvatarProfiles = avatarStore.listProfiles(),
                initialActiveAvatarProfileId = avatarStore.activeProfile()?.id,
                initialRetentionDays = memoryStore.archiveRetentionDays,
                onArchiveEnabledChanged = { memoryStore.setConversationArchiveEnabled(it) },
                onAutoMemoryEnabledChanged = { memoryStore.setAutomaticMemoryEnabled(it) },
                onRefreshMemories = { memoryStore.listMemories() },
                onSaveMemory = { memoryStore.addMemory(it) },
                onDeleteMemory = { memoryStore.deleteMemory(it) },
                onDeleteMemories = { memoryStore.deleteMemories(it) },
                onUpdateMemory = { id, text -> memoryStore.updateMemory(id, text) },
                onClearMemories = { memoryStore.clearPersonalMemories() },
                onDeleteConversation = { memoryStore.deleteConversation(it) },
                onDeleteArchiveRange = { start, end -> memoryStore.deleteArchiveRange(start, end) },
                onListConversationTurns = { memoryStore.listConversationTurns(it) },
                onDeleteConversationRange = { id, start, end -> memoryStore.deleteConversationRange(id, start, end) },
                onRefreshArchive = { memoryStore.listArchivedConversations() },
                onClearArchive = { memoryStore.clearConversationArchive() },
                onRetentionChanged = { memoryStore.archiveRetentionDays = it },
                onSaveAvatarProfile = { avatarStore.save(it) },
                onSetActiveAvatarProfile = { avatarStore.setActive(it) },
                onDeleteAvatarProfile = { avatarStore.delete(it) },
                onLanguageSelected = { preferences.languageTag = it },
                onDialectSelected = { dialectId, languageTag ->
                    preferences.dialectId = dialectId
                    preferences.languageTag = languageTag
                },
                onNameChanged = { preferences.assistantName = it },
                onLiveVoiceChanged = { preferences.liveVoiceName = it },
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
            )
        }
    }

}

@Composable
private fun JuanitaApp(
    initialLanguageTag: String,
    initialOnboardingCompleted: Boolean,
    onCompleteOnboarding: (List<String>) -> Unit,
    initialDialectId: String,
    initialName: String,
    initialLiveVoiceName: String,
    initialWakePhrase: String,
    initialPersona: PersonaProfile,
    initialArchiveEnabled: Boolean,
    initialAutoMemoryEnabled: Boolean,
    initialMemories: List<MemoryItem>,
    initialArchivedConversations: List<EncryptedMemoryStore.ArchivedConversation>,
    initialImprovementReports: List<ImprovementReport>,
    onSaveImprovementReport: (String, String, String, String, String, String, String) -> ImprovementReport,
    onRefreshImprovementReports: () -> List<ImprovementReport>,
    onDeleteImprovementReports: (Set<String>) -> Unit,
    initialAvatarProfiles: List<AvatarProfile>,
    initialActiveAvatarProfileId: String?,
    initialRetentionDays: Int,
    onArchiveEnabledChanged: (Boolean) -> Unit,
    onAutoMemoryEnabledChanged: (Boolean) -> Unit,
    onRefreshMemories: () -> List<MemoryItem>,
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
    onSaveAvatarProfile: (AvatarProfile) -> AvatarProfile,
    onSetActiveAvatarProfile: (String) -> Boolean,
    onDeleteAvatarProfile: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onDialectSelected: (String, String) -> Unit,
    onNameChanged: (String) -> Unit,
    onLiveVoiceChanged: (String) -> Unit,
    onWakePhraseChanged: (String) -> Unit,
    onPersonaChanged: (PersonaProfile) -> Unit,
) {
    val background = Color(0xFF090A0C)
    val panel = Color(0xFF17191D)
    val gold = Color(0xFFE4BD69)
    val initialLanguage = voiceLanguages.firstOrNull { it.tag == initialLanguageTag } ?: voiceLanguages.first()
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    var selectedLiveVoice by remember {
        mutableStateOf(liveVoices.firstOrNull { it.id == initialLiveVoiceName } ?: liveVoices.first())
    }
    var liveVoiceMenuExpanded by remember { mutableStateOf(false) }
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
    var improvementReports by remember { mutableStateOf(initialImprovementReports) }
    val context = LocalContext.current
    var selectedSection by remember { mutableStateOf("HOME") }
    var deviceDateTime by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { deviceDateTime = ZonedDateTime.now(); delay(1_000L) } }
    var cameraServiceBinder by remember { mutableStateOf<JuanitaVoiceService.LocalBinder?>(null) }
    val cameraServiceConnection = remember(context) {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                cameraServiceBinder = service as? JuanitaVoiceService.LocalBinder
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                cameraServiceBinder = null
            }
        }
    }
    DisposableEffect(context, cameraServiceConnection) {
        val bound = context.bindService(
            Intent(context, JuanitaVoiceService::class.java),
            cameraServiceConnection,
            android.content.Context.BIND_AUTO_CREATE
        )
        onDispose {
            if (bound) runCatching { context.unbindService(cameraServiceConnection) }
        }
    }
    val liveState by JuanitaVoiceService.state.collectAsState()
    val visionState by JuanitaVoiceService.visionState.collectAsState()
    val memoryState by JuanitaVoiceService.memoryState.collectAsState()
    var liveStartError by remember { mutableStateOf<String?>(null) }
    var callScreenVisible by remember { mutableStateOf(false) }
    val hostActivity = context as? Activity
    DisposableEffect(hostActivity, callScreenVisible) {
        val window = hostActivity?.window
        if (callScreenVisible) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    var showFirstMeeting by remember { mutableStateOf(!initialOnboardingCompleted) }
    var showPermissionOnboarding by remember {
        mutableStateOf(!context.getSharedPreferences("juanita_permission_state", android.content.Context.MODE_PRIVATE)
            .getBoolean("onboarding_completed", false))
    }
    var locationContext by remember { mutableStateOf("") }
    var locationStatus by remember { mutableStateOf("Posizione non ancora letta") }
    val uiScope = rememberCoroutineScope()
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allowed = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (allowed) {
            locationStatus = "Posizione autorizzata · acquisizione in corso"
            uiScope.launch {
                val result = runCatching { readDeviceLocationContext(context) }.getOrNull()
                locationContext = result.orEmpty()
                locationStatus = if (result.isNullOrBlank()) "Permesso attivo · posizione non ancora disponibile" else "Posizione disponibile per Juanita"
            }
        } else {
            locationStatus = "Posizione non autorizzata · Juanita continuerà a funzionare senza GPS"
        }
    }
    LaunchedEffect(Unit) {
        val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            locationStatus = "Posizione autorizzata · acquisizione in corso"
            val result = runCatching { readDeviceLocationContext(context) }.getOrNull()
            locationContext = result.orEmpty()
            locationStatus = if (result.isNullOrBlank()) "Permesso attivo · posizione non ancora disponibile" else "Posizione disponibile per Juanita"
        } else {
            locationStatus = "Posizione facoltativa non autorizzata · puoi abilitarla in Permessi e accessi"
        }
    }
    val livePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            liveStartError = null
            uiScope.launch {
                val hasLocationPermission =
                    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasLocationPermission) {
                    locationStatus = "Aggiornamento della posizione per la nuova conversazione…"
                    locationContext = runCatching { readDeviceLocationContext(context) }.getOrNull().orEmpty()
                    locationStatus = if (locationContext.isBlank()) "GPS autorizzato ma posizione non disponibile" else "Posizione aggiornata per la conversazione"
                }
                try {
                    val instruction = buildString {
                        append(persona.toSystemInstruction(selectedLanguage.label, selectedDialect.label))
                        val sessionNow = ZonedDateTime.now()
                        append("\n\nOROLOGIO LOCALE DEL DISPOSITIVO — riferimento temporale attuale: ${sessionNow.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}; fuso orario: ${sessionNow.zone.id}. Usa questo riferimento per distinguere passato, presente e futuro. Non inventare orari. Se l’utente chiede l’ora o la data attuale, usa lo strumento getCurrentDateTime per aggiornare il valore prima di rispondere.")
                        if (locationContext.isNotBlank()) {
                            append("\n\n")
                            append(locationContext)
                        } else {
                            append("\n\n")
                            append("La posizione GPS non è disponibile in questa sessione. Non fingere di conoscere la posizione attuale dell'utente; chiedi la località solo se serve.")
                        }
                    }
                    JuanitaVoiceService.start(context, instruction, selectedLiveVoice.id)
                } catch (error: Exception) {
                    liveStartError = "Avvio non riuscito: ${error.localizedMessage ?: "controlla i permessi"}"
                }
            }
        } else liveStartError = "Permesso microfono negato"
    }

    val voicePreviewPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val instruction = persona.toSystemInstruction(selectedLanguage.label, selectedDialect.label) +
                "\nQuesta è soltanto una breve prova della voce. Pronuncia il campione in modo naturale e poi resta in attesa."
            JuanitaVoiceService.start(context, instruction, selectedLiveVoice.id,
                "Questa è una breve anteprima della mia voce. Ciao, sono Juanita. Sono qui, pronta ad accompagnarti con calma e con il sorriso.",
                previewDurationMillis = 15_000L)
            liveStartError = "Anteprima della voce ${selectedLiveVoice.label} avviata. Si chiuderà automaticamente."
        } else liveStartError = "Per ascoltare la voce Gemini è necessario autorizzare il microfono."
    }
    val startVoicePreview = {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val instruction = persona.toSystemInstruction(selectedLanguage.label, selectedDialect.label) +
                "\nQuesta è soltanto una breve prova della voce. Pronuncia il campione in modo naturale e poi resta in attesa."
            JuanitaVoiceService.start(context, instruction, selectedLiveVoice.id,
                "Questa è una breve anteprima della mia voce. Ciao, sono Juanita. Sono qui, pronta ad accompagnarti con calma e con il sorriso.",
                previewDurationMillis = 15_000L)
            liveStartError = "Anteprima della voce ${selectedLiveVoice.label} avviata. Si chiuderà automaticamente."
        } else voicePreviewPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
            Column(modifier = Modifier.fillMaxSize()) {
              val mainScrollState = rememberScrollState()
              Column(
                modifier = Modifier.weight(1f).verticalScroll(mainScrollState).padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedSection == "HOME") {
                    JuanitaAvatarCard()
                }
                Text(text = assistantName.uppercase(), color = gold, fontSize = 25.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("PERSONAL VOICE ASSISTANT", color = Color(0xFF9A9DA4), fontSize = 11.sp, letterSpacing = 2.sp)
                Text(deviceDateTime.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy · HH:mm:ss", Locale.getDefault())) + " · " + deviceDateTime.zone.id, color = gold, fontSize = 12.sp)
                Text("Usa il menu in basso per passare da una sezione all'altra.", color = Color(0xFF9A9DA4), fontSize = 11.sp)

                if (selectedSection == "AVATAR") {
                AvatarStudioSection(
                    panel = panel,
                    gold = gold,
                    initialProfiles = initialAvatarProfiles,
                    initialActiveProfileId = initialActiveAvatarProfileId,
                    onSaveProfile = onSaveAvatarProfile,
                    onSetActiveProfile = onSetActiveAvatarProfile,
                    onDeleteProfile = onDeleteAvatarProfile
                )

                }

                if (selectedSection == "SETTINGS") {
                SettingsCard(panel, gold, "PERMESSI E ACCESSI") {
                    fun permissionState(permission: String): String =
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) "Autorizzato" else "Non autorizzato"
                    Text("Microfono: ${permissionState(Manifest.permission.RECORD_AUDIO)}", color = Color.White, fontSize = 12.sp)
                    Text("Posizione: ${if (permissionState(Manifest.permission.ACCESS_FINE_LOCATION) == "Autorizzato" || permissionState(Manifest.permission.ACCESS_COARSE_LOCATION) == "Autorizzato") "Autorizzata" else "Facoltativa · non autorizzata"}", color = Color.White, fontSize = 12.sp)
                    Text("Calendario: ${permissionState(Manifest.permission.READ_CALENDAR)}", color = Color.White, fontSize = 12.sp)
                    Text("Fotocamera: ${permissionState(Manifest.permission.CAMERA)}", color = Color.White, fontSize = 12.sp)
                    Text("Notifiche: ${if (android.os.Build.VERSION.SDK_INT < 33 || permissionState(Manifest.permission.POST_NOTIFICATIONS) == "Autorizzato") "Autorizzate" else "Non autorizzate"}", color = Color.White, fontSize = 12.sp)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Text("Orari esatti: ${if (context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()) "Autorizzati" else "Da autorizzare"}", color = Color.White, fontSize = 12.sp)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        Text("Sveglia a schermo intero: ${if (context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()) "Autorizzata" else "Da autorizzare"}", color = Color.White, fontSize = 12.sp)
                    }
                    Button(onClick = { showPermissionOnboarding = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
                    ) { Text("Rivedi guida permessi") }
                    Button(onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        runCatching { context.startActivity(intent) }
                    }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
                    ) { Text("Apri impostazioni permessi") }
                    Text("Puoi revocare o modificare gli accessi in qualsiasi momento. Le funzioni facoltative non bloccano il resto dell'app.",
                        color = Color(0xFF9A9DA4), fontSize = 11.sp)
                }
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
                    Text(
                        "La voce configurata di Juanita viene usata nelle conversazioni Live. L'anteprima vocale autonoma non è ancora disponibile; nessuna voce TTS di sistema verrà riprodotta.",
                        color = Color(0xFF9A9DA4),
                        fontSize = 12.sp
                    )
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

                SettingsCard(panel, gold, "VOCE DI JUANITA · GEMINI LIVE") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedLiveVoice.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(selectedLiveVoice.quality, color = Color(0xFF9A9DA4), fontSize = 12.sp)
                        }
                        TextButton(onClick = { liveVoiceMenuExpanded = true }) { Text("Cambia voce", color = gold) }
                        DropdownMenu(expanded = liveVoiceMenuExpanded, onDismissRequest = { liveVoiceMenuExpanded = false }) {
                            Text(
                                "VOCI FEMMINILI",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = gold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            liveVoices.filter { it.gender == "FEMMINILE" }.forEach { voice ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(voice.label)
                                            Text(voice.quality, fontSize = 11.sp, color = Color(0xFF777B83))
                                        }
                                    },
                                    onClick = {
                                        selectedLiveVoice = voice
                                        onLiveVoiceChanged(voice.id)
                                        liveVoiceMenuExpanded = false
                                        liveStartError = "Voce ${voice.label} salvata. Sarà usata alla prossima connessione."
                                    }
                                )
                            }
                            Text(
                                "VOCI MASCHILI",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = gold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            liveVoices.filter { it.gender == "MASCHILE" }.forEach { voice ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(voice.label)
                                            Text(voice.quality, fontSize = 11.sp, color = Color(0xFF777B83))
                                        }
                                    },
                                    onClick = {
                                        selectedLiveVoice = voice
                                        onLiveVoiceChanged(voice.id)
                                        liveVoiceMenuExpanded = false
                                        liveStartError = "Voce ${voice.label} salvata. Sarà usata alla prossima connessione."
                                    }
                                )
                            }
                        }
                    }
                    Button(onClick = { startVoicePreview() }, modifier = Modifier.fillMaxWidth(),
                        enabled = !liveState.contains("in ascolto", ignoreCase = true) &&
                            !liveState.contains("connessione", ignoreCase = true),
                        colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
                    ) { Text("Ascolta un campione della voce") }
                    Text("Riproduce un breve campione con la voce Gemini selezionata, senza TTS Android. La prova usa il microfono per avviare la sessione e si chiude automaticamente.", color = Color(0xFF9A9DA4), fontSize = 11.sp)
                    Text(liveStartError ?: liveState, color = Color(0xFF9A9DA4), fontSize = 11.sp)
                    Text("La scelta viene salvata sul telefono. Per applicarla alle conversazioni, termina e riavvia la sessione vocale.", color = Color(0xFF9A9DA4), fontSize = 11.sp)
                }

                }

                if (selectedSection == "HOME") {
                SettingsCard(panel, gold, "CONVERSAZIONE VOCALE LIVE") {
                    Text("Motore Gemini Live · ${GeminiLiveAssistant.MODEL_NAME}", color = Color(0xFF9A9DA4), fontSize = 12.sp)
                    Text(liveStartError ?: liveState, color = Color.White, fontSize = 14.sp)
                    Text(locationStatus, color = Color(0xFF9A9DA4), fontSize = 11.sp)
                    Text("La posizione viene letta automaticamente con il tuo permesso quando apri Juanita e aggiornata all’avvio della conversazione. Nessun tracciamento in background.", color = Color(0xFF777D87), fontSize = 11.sp)
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
                            liveStartError = null
                            JuanitaVoiceService.stop(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
                    ) { Text("Termina conversazione") }

                    Button(
                        onClick = { callScreenVisible = true },
                        enabled = liveState.contains("in ascolto", ignoreCase = true),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
                    ) { Text("Apri videochiamata") }
                    Text(
                        "La modalità chiamata mostra Juanita a schermo intero e la tua fotocamera in un riquadro. La videocamera resta spenta finché non la attivi.",
                        color = Color(0xFF9A9DA4), fontSize = 11.sp
                    )
                    Text("La conversazione continua con schermo spento tramite un servizio Android visibile nella notifica. Il microfono si attiva solo dopo il tuo consenso e quando premi Avvia. Audio in streaming verso Gemini; non viene archiviato nell'app.",
                        color = Color(0xFF9A9DA4), fontSize = 11.sp)
                }

                }

                if (selectedSection == "AVATAR" && !callScreenVisible) {
                    CameraVisionSection(
                        panel = panel,
                        gold = gold,
                        voiceConnected = liveState.contains("in ascolto", ignoreCase = true),
                        visionStatus = visionState,
                        onVideoFrame = { frame -> cameraServiceBinder?.sendVideoFrame(frame) }
                    )
                }

                if (selectedSection == "MEMORY") {
                MemoryPrivacySection(
                    panel = panel,
                    gold = gold,
                    initialArchiveEnabled = archiveEnabled,
                    initialAutoMemoryEnabled = initialAutoMemoryEnabled,
                    memoryStatus = memoryState,
                    onAutoMemoryEnabledChanged = onAutoMemoryEnabledChanged,
                    onRefreshMemories = onRefreshMemories,
                    initialMemories = memories,
                    initialArchivedConversations = initialArchivedConversations,
                    initialRetentionDays = initialRetentionDays,
                    onArchiveEnabledChanged = { enabled ->
                        archiveEnabled = enabled
                        onArchiveEnabledChanged(enabled)
                        status = if (enabled) "Consenso archivio conversazioni attivato" else "Archivio conversazioni in pausa"
                    },
                    onSaveMemory = { text -> onSaveMemory(text) },
                    onDeleteMemory = onDeleteMemory,
                    onDeleteMemories = onDeleteMemories,
                    onUpdateMemory = onUpdateMemory,
                    onClearMemories = onClearMemories,
                    onDeleteConversation = onDeleteConversation,
                    onDeleteArchiveRange = onDeleteArchiveRange,
                    onListConversationTurns = onListConversationTurns,
                    onDeleteConversationRange = onDeleteConversationRange,
                    onRefreshArchive = onRefreshArchive,
                    onClearArchive = onClearArchive,
                    onRetentionChanged = onRetentionChanged
                )

                }

                if (selectedSection == "ALARM") {
                    SettingsCard(panel, gold, "SVEGLIA") {
                        AlarmSection(panel = panel, gold = gold)
                    }
                }

                if (selectedSection == "MORE") {
                if (BuildConfig.DEBUG) AppCheckDebugPanel()

                ImprovementLabSection(
                    panel = panel,
                    gold = gold,
                    initialReports = improvementReports,
                    onRefresh = { onRefreshImprovementReports().also { improvementReports = it } },
                    onSave = { title, type, area, observed, evidence, expected, acceptance ->
                        onSaveImprovementReport(title, type, area, observed, evidence, expected, acceptance).also {
                            improvementReports = listOf(it) + improvementReports
                        }
                    },
                    onDelete = { ids ->
                        onDeleteImprovementReports(ids)
                        improvementReports = improvementReports.filterNot { it.id in ids }
                    }
                )

                SettingsCard(panel, gold, "STATO DEL PROGETTO") {
                    Text(status, color = Color(0xFFB9BBC1), fontSize = 13.sp)
                    Text("Gemini Live è integrato per le prove vocali. La memoria automatica è attiva quando abilitata nelle impostazioni; la wake word e le voci regionali native sono ancora in sviluppo.",
                        color = Color(0xFF9A9DA4), fontSize = 12.sp)
                }
                Text("Juanita AI • Versione ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    color = Color(0xFF686C74), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                if (callScreenVisible) {
                    val activeAvatarName = initialAvatarProfiles
                        .firstOrNull { it.id == initialActiveAvatarProfileId }
                        ?.displayName ?: assistantName
                    AvatarCallScreen(
                        avatarName = activeAvatarName,
                        portraitPath = "${context.filesDir.absolutePath}/juanita_portrait.jpg",
                        connectionStatus = liveState,
                        voiceConnected = liveState.contains("in ascolto", ignoreCase = true),
                        onVideoFrame = { frame -> cameraServiceBinder?.sendVideoFrame(frame) },
                        onDismiss = {
                            // Leaving the call UI also ends audio: never leave a hidden hot mic.
                            JuanitaVoiceService.stop(context)
                            callScreenVisible = false
                        },
                        onEndCall = {
                            JuanitaVoiceService.stop(context)
                            callScreenVisible = false
                        }
                    )
                }
              }
              if (showFirstMeeting) {
                  FirstMeetingOnboarding(
                      onComplete = { entries ->
                          onCompleteOnboarding(entries)
                          memories = onRefreshMemories()
                          showFirstMeeting = false
                      },
                      onSkip = {
                          onCompleteOnboarding(emptyList())
                          showFirstMeeting = false
                      }
                  )
              }
              if (showPermissionOnboarding) {
                  PermissionOnboarding {
                      context.getSharedPreferences("juanita_permission_state", android.content.Context.MODE_PRIVATE)
                          .edit().putBoolean("onboarding_completed", true).apply()
                      showPermissionOnboarding = false
                  }
              }
              NavigationBar(containerColor = panel, contentColor = gold) {
                  listOf("HOME" to "Home", "AVATAR" to "Avatar", "ALARM" to "Sveglia", "SETTINGS" to "Settaggi", "MEMORY" to "Memoria", "MORE" to "Altro").forEach { (key, label) ->
                      NavigationBarItem(
                          selected = selectedSection == key,
                          onClick = { selectedSection = key },
                          icon = { Text(when (key) { "HOME" -> "🏠"; "AVATAR" -> "👩🏻"; "SETTINGS" -> "⚙️"; "MEMORY" -> "🧠"; "ALARM" -> "⏰"; else -> "⋯" }, fontSize = 16.sp) },
                          label = {
                              Text(
                                  label,
                                  fontSize = 9.sp,
                                  maxLines = 1,
                                  softWrap = false
                              )
                          }
                      )
                  }
              }
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
