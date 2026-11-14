package ai.juanita.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.ComponentName
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.IBinder
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material3.AlertDialog
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import ai.juanita.app.identity.DigitalIdentitySection
import ai.juanita.app.identity.DigitalIdentity
import ai.juanita.app.identity.DigitalIdentityStore
import ai.juanita.app.identity.toSystemInstruction
import java.util.Locale
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var keepScreenOnForCall = false
    private var screenOffReceiverRegistered = false

    // Keep every Juanita UI window (including the full-screen Compose avatar Dialog)
    // behind Samsung/Android keyguard as soon as the physical display turns off.
    // The foreground voice service is independent and continues only under its
    // existing explicit background-consent policy.
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                moveTaskToBack(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (keepScreenOnForCall) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        // Never let an active call keep the display awake after Juanita leaves the foreground.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onStop() {
        // Background continuation is a per-session opt-in. The service decides whether
        // to keep Live running or perform a confirmed microphone shutdown.
        runCatching {
            startService(Intent(this, JuanitaVoiceService::class.java)
                .setAction(JuanitaVoiceService.ACTION_ACTIVITY_BACKGROUND))
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (screenOffReceiverRegistered) {
            runCatching { unregisterReceiver(screenOffReceiver) }
            screenOffReceiverRegistered = false
        }
        super.onDestroy()
    }

    fun setCallScreenKeepAwake(enabled: Boolean) {
        keepScreenOnForCall = enabled
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenOffReceiverRegistered = true
        preferences = AssistantPreferences(applicationContext)
        val memoryStore = EncryptedMemoryStore.getInstance(applicationContext)
        val avatarStore = AvatarProfileStore(applicationContext)
        val digitalIdentityStore = DigitalIdentityStore(applicationContext)
        val initialDigitalIdentities = runCatching { digitalIdentityStore.list() }.getOrDefault(emptyList())
        val initialMemoriesResult = runCatching { memoryStore.listMemories() }
        val initialReportsResult = runCatching { memoryStore.listImprovementReports() }
        val memoryLoadErrors = listOfNotNull(
            initialMemoriesResult.exceptionOrNull()?.let { "Memorie personali: ${it.localizedMessage ?: it.javaClass.simpleName}" },
            initialReportsResult.exceptionOrNull()?.let { "Rapporti: ${it.localizedMessage ?: it.javaClass.simpleName}" }
        ).joinToString(" · ")
        val initialMemoryLoadError = memoryLoadErrors.takeIf { it.isNotBlank() }
        val initialActiveDigitalIdentityId = preferences.activeDigitalIdentityId
            ?.takeIf { selectedId -> initialDigitalIdentities.any { it.id == selectedId && !it.archived } }
            .also { validId ->
                if (preferences.activeDigitalIdentityId != validId) preferences.activeDigitalIdentityId = validId
            }

        val savedPersona = PersonaProfile(
            name = preferences.assistantName,
            gender = preferences.assistantGender,
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
                initialOnboardingCompleted = preferences.hasCompletedOnboarding && initialDigitalIdentities.any { !it.archived },
                initialDialectId = preferences.dialectId,
                initialName = preferences.assistantName,
                initialAssistantGender = preferences.assistantGender,
                initialLiveVoiceName = preferences.liveVoiceName,
                initialWakePhrase = preferences.wakePhrase,
                initialPersona = savedPersona,
                initialDigitalIdentities = initialDigitalIdentities,
                initialActiveDigitalIdentityId = initialActiveDigitalIdentityId,
                onActiveDigitalIdentityChanged = { preferences.activeDigitalIdentityId = it },
                onCompleteInitialSetup = { preferences.hasCompletedOnboarding = true },
                initialArchiveEnabled = memoryStore.isConversationArchiveEnabled,
                initialAutoMemoryEnabled = memoryStore.isAutomaticMemoryEnabled,
                initialMemories = initialMemoriesResult.getOrDefault(emptyList()),
                initialMemoryLoadError = initialMemoryLoadError,
                // The archive is loaded asynchronously only when its tab is opened; do not scan/decrypt it during Activity composition.
                initialArchivedConversations = emptyList(),
                initialImprovementReports = initialReportsResult.getOrDefault(emptyList()),
                onSaveImprovementReport = { title, type, area, observed, evidence, expected, acceptance ->
                    memoryStore.addImprovementReport(title, type, area, observed, evidence, expected, acceptance)
                },
                onRefreshImprovementReports = { runCatching { memoryStore.listImprovementReports() }.getOrDefault(emptyList()) },
                onDeleteImprovementReports = { memoryStore.deleteImprovementReports(it) },
                initialAvatarProfiles = avatarStore.listProfiles(),
                initialActiveAvatarProfileId = avatarStore.activeProfile()?.id,
                initialRetentionDays = memoryStore.archiveRetentionDays,
                onArchiveEnabledChanged = { memoryStore.setConversationArchiveEnabled(it) },
                onAutoMemoryEnabledChanged = { memoryStore.setAutomaticMemoryEnabled(it) },
                onRefreshMemories = { runCatching { memoryStore.listMemories() }.getOrDefault(emptyList()) },
                onSaveMemory = { memoryStore.addMemory(it) },
                onDeleteMemory = { memoryStore.deleteMemory(it) },
                onDeleteMemories = { memoryStore.deleteMemories(it) },
                onUpdateMemory = { id, text -> memoryStore.updateMemory(id, text) },
                 onConfirmMemory = { id -> memoryStore.confirmMemory(id) },
                onClearMemories = { memoryStore.clearPersonalMemories() },
                onDeleteConversation = { memoryStore.deleteConversation(it) },
                onDeleteArchiveRange = { start, end -> memoryStore.deleteArchiveRange(start, end) },
                onListConversationTurns = { memoryStore.listConversationTurns(it) },
                onDeleteConversationRange = { id, start, end -> memoryStore.deleteConversationRange(id, start, end) },
                onRefreshArchive = { runCatching { memoryStore.listArchivedConversations() }.getOrDefault(emptyList()) },
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
                onIdentityChanged = { preferences.assistantGender = it },
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
    initialDialectId: String,
    initialName: String,
    initialAssistantGender: String,
    initialLiveVoiceName: String,
    initialWakePhrase: String,
    initialPersona: PersonaProfile,
    initialDigitalIdentities: List<DigitalIdentity>,
    initialActiveDigitalIdentityId: String?,
    onActiveDigitalIdentityChanged: (String?) -> Unit,
    onCompleteInitialSetup: (String) -> Unit,
    initialArchiveEnabled: Boolean,
    initialAutoMemoryEnabled: Boolean,
    initialMemories: List<MemoryItem>,
    initialMemoryLoadError: String?,
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
    onConfirmMemory: (String) -> Boolean,
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
    onIdentityChanged: (String) -> Unit,
    onLiveVoiceChanged: (String) -> Unit,
    onWakePhraseChanged: (String) -> Unit,
    onPersonaChanged: (PersonaProfile) -> Unit,
) {
    val background = Color(0xFF090A0C)
    val panel = Color(0xFF17191D)
    val gold = Color(0xFFE4BD69)
    val initialLanguage = voiceLanguages.firstOrNull { it.tag == initialLanguageTag } ?: voiceLanguages.first()
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    var digitalIdentities by remember { mutableStateOf(initialDigitalIdentities) }
    var identityRefreshSignal by remember { mutableStateOf(0) }
    var activeDigitalIdentityId by remember { mutableStateOf(initialActiveDigitalIdentityId) }
    val activeDigitalIdentity = digitalIdentities.firstOrNull { it.id == activeDigitalIdentityId }
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
    var status by remember { mutableStateOf("Impostazioni salvate sul dispositivo") }
    var assistantName by remember { mutableStateOf(initialName) }
    var assistantGender by remember { mutableStateOf(if (initialAssistantGender == "MASCHILE") "MASCHILE" else "FEMMINILE") }
    var assistantGenderMenuExpanded by remember { mutableStateOf(false) }
    var wakePhrase by remember { mutableStateOf(initialWakePhrase) }
    var persona by remember { mutableStateOf(initialPersona.copy(name = initialName, gender = assistantGender)) }

    val applyAssistantIdentity: (String) -> Unit = { requestedGender ->
        val nextGender = if (requestedGender == "MASCHILE") "MASCHILE" else "FEMMINILE"
        if (nextGender != assistantGender) {
            val oldDefaultWakePhrase = if (assistantGender == "MASCHILE") "Hola José" else "Hola Juanita"
            val nextName = if (nextGender == "MASCHILE") "José" else "Juanita"
            val nextDefaultWakePhrase = "Hola $nextName"
            if (wakePhrase.trim().equals(oldDefaultWakePhrase, ignoreCase = true)) {
                wakePhrase = nextDefaultWakePhrase
                onWakePhraseChanged(nextDefaultWakePhrase)
            }
            assistantGender = nextGender
            onIdentityChanged(nextGender)
            assistantName = nextName
            onNameChanged(nextName)
            persona = persona.copy(name = nextName, gender = nextGender)
            onPersonaChanged(persona)
            if (selectedLiveVoice.gender != nextGender) {
                val nextVoice = liveVoices.first { it.gender == nextGender }
                selectedLiveVoice = nextVoice
                onLiveVoiceChanged(nextVoice.id)
            }
            status = "Identità $nextName salvata. Memorie e sveglie non sono state modificate."
        }
    }
    var archiveEnabled by remember { mutableStateOf(initialArchiveEnabled) }
    var memoryDraft by remember { mutableStateOf("") }
    var memories by remember { mutableStateOf(initialMemories) }
    var memoryLoadError by remember { mutableStateOf(initialMemoryLoadError) }
    var improvementReports by remember { mutableStateOf(initialImprovementReports) }
    val context = LocalContext.current
    val profileStore = remember(context) { DigitalIdentityStore(context.applicationContext) }
    val profileScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedSection by remember { mutableStateOf("HOME") }
    val presencePreferences = remember(context) {
        context.getSharedPreferences("juanita_presence_preferences", android.content.Context.MODE_PRIVATE)
    }
    var backgroundContinuationAuthorized by remember {
        mutableStateOf(presencePreferences.getBoolean("background_continuation_authorized", false))
    }
    var deviceDateTime by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                deviceDateTime = ZonedDateTime.now()
                delay(1_000L)
            }
        }
    }
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
    val voiceSessionLabelVisible = listOf("in ascolto", "connessione a gemini live", "anteprima", "rinnovando", "chiusura", "non confermata")
        .any { liveState.contains(it, ignoreCase = true) }
    var liveSessionIdentityName by remember { mutableStateOf(activeDigitalIdentity?.displayName ?: assistantName) }
    val visionState by JuanitaVoiceService.visionState.collectAsState()
    val memoryState by JuanitaVoiceService.memoryState.collectAsState()
    var liveStartError by remember { mutableStateOf<String?>(null) }
    var callScreenVisible by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var exitInProgress by remember { mutableStateOf(false) }
    val hostActivity = context as? Activity
    DisposableEffect(hostActivity, callScreenVisible) {
        val window = hostActivity?.window
        (hostActivity as? MainActivity)?.setCallScreenKeepAwake(callScreenVisible)
        onDispose { (hostActivity as? MainActivity)?.setCallScreenKeepAwake(false) }
    }
    var showFirstMeeting by remember { mutableStateOf(!initialOnboardingCompleted) }
    var showPermissionOnboarding by remember {
        mutableStateOf(!context.getSharedPreferences("juanita_permission_state", android.content.Context.MODE_PRIVATE)
            .getBoolean("onboarding_completed", false))
    }
    var locationContext by remember { mutableStateOf("") }
    var locationStatus by remember { mutableStateOf("Posizione non ancora letta") }
    val uiScope = rememberCoroutineScope()

    fun performSafeExit() {
        if (exitInProgress) return
        exitInProgress = true
        uiScope.launch {
            val stopped = JuanitaVoiceService.stopAndAwait(context)
            exitInProgress = false
            if (stopped) {
                showExitConfirmation = false
                callScreenVisible = false
                hostActivity?.finishAndRemoveTask()
            } else {
                showExitConfirmation = false
                liveStartError = "Non posso confermare la chiusura completa del microfono e della sessione. Juanita resta aperta: riprova Termina e non avviare un'altra conversazione."
            }
        }
    }

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
                        append(activeDigitalIdentity?.toSystemInstruction() ?: persona.toSystemInstruction(selectedLanguage.label, selectedDialect.label))
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
                    liveSessionIdentityName = activeDigitalIdentity?.displayName ?: assistantName
                    JuanitaVoiceService.start(
                        context,
                        instruction,
                        activeDigitalIdentity?.voiceName ?: selectedLiveVoice.id,
                        identityId = activeDigitalIdentity?.id,
                        backgroundContinuationAuthorized = backgroundContinuationAuthorized
                    )
                } catch (error: Exception) {
                    liveStartError = "Avvio non riuscito: ${error.localizedMessage ?: "controlla i permessi"}"
                }
            }
        } else liveStartError = "Permesso microfono negato"
    }

    val persistSelectedVoice: (LiveVoice) -> Unit = { voice ->
        val selectedIdentity = activeDigitalIdentity
        if (selectedIdentity == null) {
            selectedLiveVoice = voice
            onLiveVoiceChanged(voice.id)
            liveStartError = "Voce ${voice.label} salvata. Sarà usata alla prossima connessione."
        } else {
            profileScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        profileStore.update(selectedIdentity.copy(voiceName = voice.id))
                    }
                }.onSuccess { updated ->
                    digitalIdentities = digitalIdentities.map { if (it.id == updated.id) updated else it }
                    identityRefreshSignal += 1
                    liveStartError = "Voce ${voice.label} salvata nel profilo ${updated.displayName}. Sarà usata dalla prossima conversazione."
                }.onFailure { error ->
                    liveStartError = "Non sono riuscita a salvare la voce: ${error.localizedMessage ?: "errore"}"
                }
            }
        }
        liveVoiceMenuExpanded = false
    }

    var pendingVoicePreview by remember { mutableStateOf<DigitalIdentity?>(null) }

    fun startIdentityVoicePreview(identity: DigitalIdentity) {
        val busyStates = listOf("in ascolto", "connessione a gemini live", "anteprima", "rinnovando", "chiusura", "non confermata")
        if (busyStates.any { liveState.contains(it, ignoreCase = true) }) {
            liveStartError = "Prima termina la conversazione o attendi la chiusura completa; poi potrai provare questa voce."
            return
        }
        val sample = when {
            identity.preferredLanguageTag.startsWith("es", ignoreCase = true) ->
                "Hola, soy ${identity.displayName}. Esta es una breve prueba de mi voz. ¿Qué tal? Me alegra saludarte."
            identity.preferredLanguageTag.startsWith("it", ignoreCase = true) ->
                "Ciao, sono ${identity.displayName}. Questa è una breve prova della mia voce. È un piacere conoscerti."
            else -> "Hello, I'm ${identity.displayName}. This is a short sample of my voice. It's lovely to meet you."
        }
        val instruction = identity.toSystemInstruction() +
            "\nANTEPRIMA VOCALE: pronuncia soltanto il breve testo iniziale fornito. Usa la voce Gemini selezionata e le indicazioni linguistiche di questo profilo. Non avviare una conversazione, non chiedere informazioni e poi resta in silenzio."
        liveSessionIdentityName = identity.displayName
        JuanitaVoiceService.start(
            context = context,
            instruction = instruction,
            voiceName = identity.voiceName,
            initialPrompt = sample,
            previewDurationMillis = 12_000L
        )
        liveStartError = "Anteprima di ${identity.displayName} avviata con la voce ${identity.voiceName}; si chiuderà automaticamente."
    }

    val voicePreviewPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val identity = pendingVoicePreview
        pendingVoicePreview = null
        if (granted && identity != null) {
            startIdentityVoicePreview(identity)
        } else if (!granted) {
            liveStartError = "Per ascoltare la voce Gemini è necessario autorizzare il microfono."
        }
    }
    val previewIdentityVoice: (DigitalIdentity) -> Unit = { identity ->
        val busyStates = listOf("in ascolto", "connessione a gemini live", "anteprima", "rinnovando", "chiusura", "non confermata")
        if (busyStates.any { liveState.contains(it, ignoreCase = true) }) {
            liveStartError = "Prima termina la conversazione o attendi la chiusura completa; poi potrai provare questa voce."
        } else {
            pendingVoicePreview = identity
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingVoicePreview = null
                startIdentityVoicePreview(identity)
            } else {
                voicePreviewPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
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
                if (showFirstMeeting) {
                    Text("CONFIGURA IL TUO PERSONAGGIO", color = gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Prima di iniziare, crea Juanita o José: scegli la voce, ascoltane un campione, scrivi la biografia e definisci il carattere. Potrai modificare tutto in seguito.", color = Color(0xFFB9BBC1), fontSize = 13.sp)
                    DigitalIdentitySection(
                        panel = panel,
                        gold = gold,
                        activeIdentityId = activeDigitalIdentityId,
                        refreshSignal = identityRefreshSignal,
                        firstRunMode = true,
                        onFirstIdentityCreated = { id ->
                            activeDigitalIdentityId = id
                            onActiveDigitalIdentityChanged(id)
                            onCompleteInitialSetup(id)
                            showFirstMeeting = false
                        },
                        onActiveIdentityChanged = { id ->
                            activeDigitalIdentityId = id
                            onActiveDigitalIdentityChanged(id)
                        },
                        onIdentitiesChanged = { digitalIdentities = it },
                        onPreviewVoice = previewIdentityVoice
                    )
                } else {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Identità dell'assistente", color = Color.White)
                            Text(
                                if (assistantGender == "MASCHILE") "José · maschile" else "Juanita · femminile",
                                color = Color(0xFF9A9DA4), fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = { assistantGenderMenuExpanded = true }) {
                            Text("Cambia", color = gold)
                        }
                        DropdownMenu(
                            expanded = assistantGenderMenuExpanded,
                            onDismissRequest = { assistantGenderMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Juanita · femminile") },
                                onClick = { applyAssistantIdentity("FEMMINILE"); assistantGenderMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("José · maschile") },
                                onClick = { applyAssistantIdentity("MASCHILE"); assistantGenderMenuExpanded = false }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = assistantName,
                        onValueChange = {
                            assistantName = it
                            persona = persona.copy(name = it)
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

                DigitalIdentitySection(
                    panel = panel,
                    gold = gold,
                    activeIdentityId = activeDigitalIdentityId,
                    refreshSignal = identityRefreshSignal,
                    onActiveIdentityChanged = { id ->
                        val previousId = activeDigitalIdentityId
                        val voiceMayBeRunning = listOf(
                            "in ascolto", "connessione", "rinnovando", "anteprima",
                            "chiusura sessione", "chiusura audio non confermata"
                        ).any { liveState.contains(it, ignoreCase = true) }
                        if (id != previousId && voiceMayBeRunning) {
                            // A Live session keeps the persona/system prompt it was created with.
                            // Stop it immediately when the selected identity changes; the next
                            // explicit Parla action will start a fresh session with the new profile.
                            JuanitaVoiceService.stop(context)
                            liveStartError = "Personaggio cambiato: chiusura della conversazione precedente in corso. Premi Parla per usare il nuovo profilo."
                        }
                        activeDigitalIdentityId = id
                        onActiveDigitalIdentityChanged(id)
                    },
                    onIdentitiesChanged = { digitalIdentities = it },
                    onPreviewVoice = previewIdentityVoice
                )

                SettingsCard(panel, gold, if (activeDigitalIdentity == null) "CARATTERE E PERSONALITÀ · STANDARD" else "CARATTERE DI ${activeDigitalIdentity?.displayName ?: "profilo"}") {
                    if (activeDigitalIdentity == null) {
                        Text("Questi cursori configurano l'assistente standard. Ogni personaggio salvato ha percentuali indipendenti nel proprio editor.", color = Color(0xFF9A9DA4), fontSize = 12.sp)
                        TraitSlider("Ironia", persona.irony) { persona = persona.copy(irony = it); onPersonaChanged(persona) }
                        TraitSlider("Dolcezza", persona.sweetness) { persona = persona.copy(sweetness = it); onPersonaChanged(persona) }
                        TraitSlider("Sensualità", persona.sensuality) { persona = persona.copy(sensuality = it); onPersonaChanged(persona) }
                        TraitSlider("Serietà", persona.seriousness) { persona = persona.copy(seriousness = it); onPersonaChanged(persona) }
                        TraitSlider("Simpatia", persona.friendliness) { persona = persona.copy(friendliness = it); onPersonaChanged(persona) }
                        TraitSlider("Intelligenza e curiosità", persona.curiosity) { persona = persona.copy(curiosity = it); onPersonaChanged(persona) }
                        TraitSlider("Espressività", persona.expressiveness) { persona = persona.copy(expressiveness = it); onPersonaChanged(persona) }
                        TraitSlider("Formalità", persona.formality) { persona = persona.copy(formality = it); onPersonaChanged(persona) }
                        TraitSlider("Empatia", persona.empathy) { persona = persona.copy(empathy = it); onPersonaChanged(persona) }
                    } else {
                        Text("Le percentuali di questo personaggio si modificano aprendo il suo profilo e premendo Modifica. Il salvataggio è indipendente per ogni Juanita e José.", color = Color(0xFFB9BBC1), fontSize = 12.sp)
                    }
                }

                SettingsCard(panel, gold, if (activeDigitalIdentity == null) "LINGUA DELLA VOCE · STANDARD" else "LINGUA DI ${activeDigitalIdentity?.displayName ?: "profilo"}") {
                    if (activeDigitalIdentity == null) {
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
                    } else {
                        Text("Lingua principale: ${activeDigitalIdentity?.preferredLanguageTag.orEmpty()}. Altre lingue: ${activeDigitalIdentity?.languages?.joinToString(", ")?.ifBlank { "non specificate" } ?: "non specificate"}. Per modificarle, usa Modifica nel profilo.", color = Color(0xFFB9BBC1), fontSize = 12.sp)
                    }
                }

                if (activeDigitalIdentity == null) {
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
                    

                }

                SettingsCard(panel, gold, if (activeDigitalIdentity == null) "VOCE DI $assistantName · GEMINI LIVE" else "VOCE DI ${activeDigitalIdentity?.displayName ?: "profilo"}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(activeDigitalIdentity?.voiceName ?: selectedLiveVoice.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(liveVoices.firstOrNull { it.id == (activeDigitalIdentity?.voiceName ?: selectedLiveVoice.id) }?.quality ?: "Voce Gemini Live", color = Color(0xFF9A9DA4), fontSize = 12.sp)
                        }
                        TextButton(onClick = { liveVoiceMenuExpanded = true }) { Text("Cambia voce", color = gold) }
                        DropdownMenu(expanded = liveVoiceMenuExpanded, onDismissRequest = { liveVoiceMenuExpanded = false }) {
                            Text(
                                if (activeDigitalIdentity == null) "VOCI FEMMINILI" else "VOCI DISPONIBILI PER ${activeDigitalIdentity.displayName}",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = gold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            liveVoices.filter { voice -> if (activeDigitalIdentity == null) voice.gender == "FEMMINILE" else voice.id in ai.juanita.app.identity.DigitalIdentityVoiceCatalog.forPresentation(activeDigitalIdentity.presentation) }.forEach { voice ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(voice.label)
                                            Text(voice.quality, fontSize = 11.sp, color = Color(0xFF777B83))
                                        }
                                    },
                                    onClick = { persistSelectedVoice(voice) }
                                )
                            }
                            if (activeDigitalIdentity == null) {
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
                                        onClick = { persistSelectedVoice(voice) }
                                    )
                                }
                            }
                        }
                    }
                    if (activeDigitalIdentity != null) Text("Questa voce appartiene a ${activeDigitalIdentity.displayName}. Puoi cambiarla qui oppure nell’editor del personaggio; il salvataggio aggiorna soltanto questo profilo.", color = Color(0xFF9A9DA4), fontSize = 11.sp)
                    Text(liveStartError ?: liveState, color = Color(0xFF9A9DA4), fontSize = 11.sp)
                    Text("La scelta viene salvata sul telefono. Per applicarla alle conversazioni, termina e riavvia la sessione vocale.", color = Color(0xFF9A9DA4), fontSize = 11.sp)
                }

                }

                if (selectedSection == "HOME") {
                SettingsCard(panel, gold, "CONVERSAZIONE VOCALE LIVE") {
                    Text("Motore Gemini Live · ${GeminiLiveAssistant.MODEL_NAME}", color = Color(0xFF9A9DA4), fontSize = 12.sp)
                    Text(
                        if (voiceSessionLabelVisible) "PERSONAGGIO DELLA SESSIONE: $liveSessionIdentityName"
                        else "PROSSIMO PERSONAGGIO: ${activeDigitalIdentity?.displayName ?: assistantName}",
                        color = gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(liveStartError ?: liveState, color = Color.White, fontSize = 14.sp)
                    SettingsCard(panel, gold, "CONTINUAZIONE IN BACKGROUND") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Consenti a Juanita di continuare con lo schermo spento", color = Color.White, fontSize = 13.sp)
                                Text(
                                    "Disattivato per impostazione predefinita. Se lo abiliti, la sessione avviata da te può continuare tramite il servizio Android e la notifica permanente. Senza consenso, quando lasci l'app il microfono viene chiuso.",
                                    color = Color(0xFF9A9DA4), fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = backgroundContinuationAuthorized,
                                onCheckedChange = { enabled ->
                                    backgroundContinuationAuthorized = enabled
                                    presencePreferences.edit()
                                        .putBoolean("background_continuation_authorized", enabled)
                                        .apply()
                                    context.startService(
                                        Intent(context, JuanitaVoiceService::class.java)
                                            .setAction(JuanitaVoiceService.ACTION_UPDATE_BACKGROUND_CONSENT)
                                            .putExtra(JuanitaVoiceService.EXTRA_BACKGROUND_CONTINUATION_AUTHORIZED, enabled)
                                    )
                                }
                            )
                        }
                    }
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
                    Button(
                        onClick = { showExitConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
                    ) { Text("Esci e chiudi completamente") }
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
                if (memoryLoadError != null) {
                    SettingsCard(panel, gold, "ARCHIVIO CIFRATO NON DISPONIBILE") {
                        Text(
                            "Non sono riuscita a leggere alcuni dati cifrati. Non ho cancellato né sostituito i file. Non disinstallare l'app e non cancellare i dati: serve prima una procedura di recupero.",
                            color = Color(0xFFFFCC80), fontSize = 12.sp
                        )
                        Text(memoryLoadError.orEmpty(), color = Color(0xFF9A9DA4), fontSize = 11.sp)
                    }
                }
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
                    onConfirmMemory = onConfirmMemory,
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
                        AlarmSection(panel = panel, gold = gold, assistantName = assistantName)
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

                if (!showFirstMeeting && callScreenVisible) {
                    val activeAvatarName = initialAvatarProfiles
                        .firstOrNull { it.id == initialActiveAvatarProfileId }
                        ?.displayName ?: assistantName
                    AvatarCallScreen(
                        avatarName = if (voiceSessionLabelVisible) liveSessionIdentityName else activeDigitalIdentity?.displayName ?: activeAvatarName,
                        portraitPath = "${context.filesDir.absolutePath}/juanita_portrait.jpg",
                        connectionStatus = liveState,
                        voiceConnected = liveState.contains("in ascolto", ignoreCase = true),
                        onVideoFrame = { frame -> cameraServiceBinder?.sendVideoFrame(frame) },
                        onDismiss = {
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
              }
              if (!showFirstMeeting && showPermissionOnboarding) {
                  PermissionOnboarding {
                      context.getSharedPreferences("juanita_permission_state", android.content.Context.MODE_PRIVATE)
                          .edit().putBoolean("onboarding_completed", true).apply()
                      showPermissionOnboarding = false
                  }
              }
              if (!showFirstMeeting && showExitConfirmation) {
                  AlertDialog(
                      onDismissRequest = { if (!exitInProgress) showExitConfirmation = false },
                      title = { Text(if (exitInProgress) "Chiusura in corso…" else "Uscire da Juanita AI?") },
                      text = {
                          Text(
                              if (exitInProgress) "Sto attendendo la conferma del servizio vocale. Non chiuderò la schermata finché il microfono non risulterà disattivato."
                              else "La conversazione vocale verrà interrotta e il servizio dovrà confermare la chiusura. Le sveglie programmate e i dati salvati resteranno intatti."
                          )
                      },
                      confirmButton = {
                          TextButton(onClick = { performSafeExit() }, enabled = !exitInProgress) {
                              Text(if (exitInProgress) "Attendi…" else "Esci e chiudi", color = Color(0xFFFF8A80))
                          }
                      },
                      dismissButton = {
                          TextButton(onClick = { showExitConfirmation = false }) { Text("Annulla", color = gold) }
                      },
                      containerColor = panel,
                      titleContentColor = Color.White,
                      textContentColor = Color(0xFFB9BBC1)
                  )
              }
              if (!showFirstMeeting) NavigationBar(containerColor = panel, contentColor = gold) {
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
