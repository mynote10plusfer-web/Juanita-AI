package ai.juanita.app

import android.app.ActivityManager
import android.app.Notification
import android.graphics.Bitmap
import android.os.Binder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.media.AudioManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import ai.juanita.app.ai.GeminiLiveAssistant
import ai.juanita.app.audio.VoiceAudioRouteController
import ai.juanita.app.alarm.JuanitaAlarmRingingService
import ai.juanita.app.memory.EncryptedMemoryStore
import ai.juanita.app.memory.AutomaticMemoryEngine
import ai.juanita.app.identity.DigitalIdentityStore
import ai.juanita.app.profile.PersonalProfile
import ai.juanita.app.profile.PersonalProfileStore
import ai.juanita.app.presence.WakeWordModelInstaller
import ai.juanita.app.presence.SherpaOfflineWakeWordDetector
import ai.juanita.app.presence.BackgroundLiveSafetyPolicy
import ai.juanita.app.presence.VoiceSessionCommandMatcher
import ai.juanita.app.presence.ConversationalPresenceContext
import ai.juanita.app.presence.ConversationalPresencePolicy
import ai.juanita.app.presence.ConversationalPresenceSettings
import ai.juanita.app.presence.PresenceDecision
import ai.juanita.app.diagnostics.VoiceDiagnosticLog
import ai.juanita.app.vision.JuanitaImageSearch
import ai.juanita.app.vision.VisualSearchResult
import java.time.LocalDate
import java.time.Period
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean

private enum class VoiceLifecycleState { IDLE, STARTING, ACTIVE, STOPPING, ERROR }

/** Owns Gemini Live independently from the Activity; starts only after user consent. */
class JuanitaVoiceService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val assistant = GeminiLiveAssistant()
    private val voiceDiagnostics by lazy { VoiceDiagnosticLog(applicationContext) }
    // One dedicated worker serializes blocking SDK recorder teardown. A timed-out native
    // call is interrupted and the service remains fail-closed; no second recorder is opened.
    private val audioTeardownExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "JuanitaAudioTeardown").apply { isDaemon = true }
    }
    // File cleanup is deliberately isolated from the voice-service lifecycle. A slow
    // encrypted-store monitor or SharedPreferences commit must never keep the mic service alive.
    private val continuityCleanupExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "JuanitaContinuityCleanup").apply { isDaemon = true }
    }
    private val audioRouteController by lazy { VoiceAudioRouteController(applicationContext) }
    private lateinit var memoryStore: EncryptedMemoryStore
    private lateinit var automaticMemoryEngine: AutomaticMemoryEngine
    private lateinit var personalProfileStore: PersonalProfileStore
    private var startRequested = false
    /** Explicit opt-in controls the offline listener; it is never enabled on boot. */
    private var wakeWordRequested = false
    private var wakeDetector: SherpaOfflineWakeWordDetector? = null
    private var wakeSetupJob: Job? = null
    /** Last user-selected Live configuration, reused only after an explicit wake hit. */
    private var wakeStartIntent: Intent? = null
    /** Main-thread-confined lifecycle gate: one session can own audio resources at a time. */
    private var lifecycleState = VoiceLifecycleState.IDLE
    private var activeIdentityId: String? = null
    /** Non-null only when this Live session was launched by a specific alarm. */
    private var activeAlarmId: String? = null
    /** Consent is captured when the user explicitly starts this Live session. */
    private var backgroundContinuationAuthorized = false
    private var appInForeground = true
    private var backgroundIdleTimeoutJob: Job? = null
    private var lastBackgroundUserActivityAtElapsedRealtime = 0L
    private var screenPowerReceiverRegistered = false
    private val screenPowerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (!appInForeground && backgroundContinuationAuthorized && activeAlarmId == null &&
                        lifecycleState == VoiceLifecycleState.ACTIVE
                    ) {
                        voiceDiagnostics.event("power.screen_off", "background Live session remains user-authorized")
                        acquireCpuWakeLock()
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (!appInForeground) {
                        voiceDiagnostics.event("power.screen_on", "releasing background partial WakeLock")
                        releaseCpuWakeLock()
                    }
                }
            }
        }
    }
    private var sessionJob: Job? = null
    private var stopJob: Job? = null
    private var wakeLockRenewalJob: Job? = null
    /** Latest explicit start requested while the previous session is shutting down. */
    private var pendingStartIntent: Intent? = null
    private var pendingStartId: Int = 0
    /** Set only by an explicit spoken close-application command; acted on after confirmed audio shutdown. */
    private var closeApplicationAfterStop = false
    private var voicePreviewJob: Job? = null
    private var transcriptChannel: Channel<Pair<String, String>>? = null
    /** Stop Live if bounded transcript intake saturates; never continue silently after losing an event. */
    private val transcriptOverflowHandled = AtomicBoolean(false)
    private var transcriptWorker: Job? = null
    @Volatile private var activeConversationId: String? = null
    private var memoryWorkChannel: Channel<Unit>? = null
    private var memoryWorker: Job? = null
    private val videoFrameInFlight = AtomicBoolean(false)
    @Volatile private var cpuWakeLock: PowerManager.WakeLock? = null
    private var thermalListenerRegistered = false
    private var audioRecordingDiagnosticsJob: Job? = null
    private var lastAudioRecordingDiagnosticSignature: String? = null
    private var lastAudioRecordingDiagnosticAtMs: Long = 0L
    private val thermalStatusListener = PowerManager.OnThermalStatusChangedListener { status ->
        val level = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "NONE"
            PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
            PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
            PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
            PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
            else -> "UNKNOWN"
        }
        voiceDiagnostics.event("thermal.status", "level=$level; value=$status")
        // SEVERE means Android is applying significant throttling, not that shutdown
        // is imminent. Keep the conversation alive and reserve automatic stop for
        // CRITICAL or higher, when the platform has exhausted normal power reductions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            status >= PowerManager.THERMAL_STATUS_CRITICAL
        ) {
            serviceScope.launch {
                if (lifecycleState == VoiceLifecycleState.ACTIVE) {
                    Log.w("JuanitaVoiceService", "Critical device thermal status ($level/$status); stopping Live audio")
                    requestStop(
                        preserveContinuitySnapshot = true,
                        finalMessage = "Sessione terminata per proteggere il telefono: temperatura interna critica"
                    )
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun sendVideoFrame(bitmap: Bitmap) {
            if (!videoFrameInFlight.compareAndSet(false, true)) return
            // JPEG compression and the Live image upload are CPU/network work;
            // never execute them on the service's Main.immediate scope.
            serviceScope.launch(Dispatchers.IO) {
                try {
                    runCatching { assistant.sendVideoFrame(bitmap) }
                        .onFailure { _visionState.value = "Invio immagine non riuscito: ${it.localizedMessage ?: "errore"}" }
                } finally {
                    videoFrameInFlight.set(false)
                }
            }
        }
    }

    private val localBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        voiceDiagnostics.event("service.created")
        memoryStore = EncryptedMemoryStore.getInstance(applicationContext)
        automaticMemoryEngine = AutomaticMemoryEngine(memoryStore)
        personalProfileStore = PersonalProfileStore(applicationContext)
        createNotificationChannel()
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                screenPowerReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                },
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            screenPowerReceiverRegistered = true
        }.onFailure { Log.w("JuanitaVoiceService", "Unable to register screen power receiver", it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        voiceDiagnostics.event("service.command", "action=${intent?.action ?: "null"}; lifecycle=$lifecycleState")
        when (intent?.action) {
            ACTION_ENABLE_WAKE -> {
                val instruction = intent.getStringExtra(EXTRA_INSTRUCTION).orEmpty()
                val voice = intent.getStringExtra(EXTRA_VOICE).orEmpty().ifBlank { GeminiLiveAssistant.DEFAULT_VOICE }
                val identityId = intent.getStringExtra(EXTRA_IDENTITY_ID)?.takeIf { it.isNotBlank() }
                if (instruction.isBlank()) {
                    _wakeWordStatus.value = "Attivazione non riuscita: istruzioni mancanti"
                    if (lifecycleState == VoiceLifecycleState.IDLE) stopSelf(startId)
                    return START_NOT_STICKY
                }
                val configuredPhrase = ai.juanita.app.data.AssistantPreferences(applicationContext).wakePhrase.trim()
                if (!configuredPhrase.equals(WakeWordModelInstaller.DEFAULT_PHRASE, ignoreCase = true)) {
                    _wakeWordStatus.value = "Per questa prima prova usa la frase Hola Juanita"
                    if (lifecycleState == VoiceLifecycleState.IDLE) stopSelf(startId)
                    return START_NOT_STICKY
                }
                wakeStartIntent = Intent(this, JuanitaVoiceService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_INSTRUCTION, instruction)
                    putExtra(EXTRA_VOICE, voice)
                    putExtra(EXTRA_IDENTITY_ID, identityId)
                    putExtra(EXTRA_BACKGROUND_CONTINUATION_AUTHORIZED, false)
                }
                wakeWordRequested = true
                _wakeWordEnabled.value = true
                _wakeWordStatus.value = "Preparazione ascolto offline…"
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification("Preparazione ascolto offline · Hola Juanita"),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    } else 0
                )
                if (lifecycleState == VoiceLifecycleState.IDLE && stopJob?.isActive != true) {
                    startWakeDetector()
                } else {
                    updateNotification("Juanita Live attiva · ascolto offline al termine della conversazione")
                }
                return START_NOT_STICKY
            }
            ACTION_DISABLE_WAKE -> {
                wakeWordRequested = false
                wakeStartIntent = null
                _wakeWordEnabled.value = false
                _wakeWordStatus.value = "Ascolto automatico disattivato"
                wakeSetupJob?.cancel()
                if (lifecycleState == VoiceLifecycleState.IDLE && stopJob?.isActive != true) {
                    serviceScope.launch {
                        stopWakeDetector()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                } else {
                    updateNotification("Conversazione Gemini Live attiva · wake word disattivata")
                }
                return START_NOT_STICKY
            }
            ACTION_UPDATE_BACKGROUND_CONSENT -> {
                backgroundContinuationAuthorized =
                    intent.getBooleanExtra(EXTRA_BACKGROUND_CONTINUATION_AUTHORIZED, false) && activeAlarmId == null
                // The consent switch may be changed while no voice session exists.
                // Do not leave an idle started Service behind just to persist a UI setting;
                // the preference is owned by MainActivity and is passed on the next start.
                if (!startRequested || lifecycleState == VoiceLifecycleState.IDLE) {
                    if (wakeWordRequested) {
                        updateNotification("Ascolto offline attivo · pronuncia Hola Juanita")
                    } else {
                        stopSelf(startId)
                    }
                    return START_NOT_STICKY
                }
                if (!backgroundContinuationAuthorized) {
                    updateNotification("Continuazione in background non autorizzata")
                } else {
                    updateNotification("Conversazione · continuazione in background autorizzata")
                }
                return START_NOT_STICKY
            }
            ACTION_ACTIVITY_BACKGROUND -> {
                appInForeground = false
                logPowerSnapshot("activity_background")
                if (!startRequested || lifecycleState == VoiceLifecycleState.IDLE) {
                    if (wakeWordRequested) {
                        updateNotification("Ascolto offline attivo · pronuncia Hola Juanita")
                    } else {
                        stopSelf(startId)
                    }
                } else if (activeAlarmId == null) {
                    // Apply the same fail-closed presence policy used by the C3 tests
                    // at the actual audio owner. Background continuation is allowed only
                    // while this foreground service owns the active user-started session
                    // and the user has separately authorized continuation.
                    val decision = ConversationalPresencePolicy.listeningDecision(
                        settings = ConversationalPresenceSettings(enabled = true),
                        context = ConversationalPresenceContext(
                            appInForeground = false,
                            foregroundServiceActive = startRequested &&
                                lifecycleState == VoiceLifecycleState.ACTIVE,
                            backgroundContinuationAuthorized = backgroundContinuationAuthorized,
                            userExplicitlyStartedSession = true,
                            microphonePermissionGranted =
                                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                                    android.content.pm.PackageManager.PERMISSION_GRANTED,
                            // A non-alarm session can be a selected character or the
                            // standard assistant; its validated instruction is already bound.
                            activeIdentityAvailable = lifecycleState == VoiceLifecycleState.ACTIVE
                        )
                    )
                    when (decision) {
                        PresenceDecision.AllowListening -> {
                            // A manual partial WakeLock is only needed once the screen is off.
                            // Foreground interaction already keeps the device awake.
                            if (!(getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive) {
                                acquireCpuWakeLock()
                            }
                            lastBackgroundUserActivityAtElapsedRealtime = SystemClock.elapsedRealtime()
                            updateNotification("Conversazione in background · pausa dopo 15 min senza voce")
                            armBackgroundIdleTimeout()
                        }
                        is PresenceDecision.BlockListening -> {
                            _state.value = "App in background · microfono in chiusura (" + decision.reason.name + ")"
                            requestStop(preserveContinuitySnapshot = true)
                        }
                        else -> {
                            _state.value = "App in background · presenza non autorizzata"
                            requestStop(preserveContinuitySnapshot = true)
                        }
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_ACTIVITY_FOREGROUND -> {
                appInForeground = true
                backgroundIdleTimeoutJob?.cancel()
                backgroundIdleTimeoutJob = null
                voiceDiagnostics.event("background.idle_guard.cancelled", "reason=activity_foreground")
                releaseCpuWakeLock()
                logPowerSnapshot("activity_foreground")
                if (!startRequested || lifecycleState == VoiceLifecycleState.IDLE) {
                    if (wakeWordRequested) {
                        updateNotification("Ascolto offline attivo · pronuncia Hola Juanita")
                    } else {
                        stopSelf(startId)
                    }
                } else updateNotification("Conversazione vocale attiva · app in primo piano")
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                // A direct Stop disables both Live and the persistent offline listener.
                wakeWordRequested = false
                wakeStartIntent = null
                _wakeWordEnabled.value = false
                _wakeWordStatus.value = "Ascolto automatico disattivato"
                wakeSetupJob?.cancel()
                // An explicit stop/exit always wins over a start queued during teardown.
                pendingStartIntent = null
                pendingStartId = 0
                requestStop()
                return START_NOT_STICKY
            }
            ACTION_STOP_ALARM -> {
                val requestedAlarmId = intent.getStringExtra(EXTRA_ALARM_ID)
                if (!requestedAlarmId.isNullOrBlank() && activeAlarmId == requestedAlarmId) {
                    requestStop()
                } else if (!startRequested && stopJob?.isActive != true && !wakeWordRequested) {
                    // A stop request must not leave behind a newly-created, idle Service.
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val instruction = intent.getStringExtra(EXTRA_INSTRUCTION).orEmpty()
                val voice = intent.getStringExtra(EXTRA_VOICE).orEmpty().ifBlank { GeminiLiveAssistant.DEFAULT_VOICE }
                val initialPrompt = intent.getStringExtra(EXTRA_INITIAL_PROMPT).orEmpty()
                val alarmId = intent.getStringExtra(EXTRA_ALARM_ID)
                val previewDurationMillis = intent.getLongExtra(EXTRA_PREVIEW_DURATION_MS, 0L).coerceIn(0L, 30_000L)
                val identityId = intent.getStringExtra(EXTRA_IDENTITY_ID)?.takeIf { it.isNotBlank() }
                voiceDiagnostics.event("session.start.request", "voice=$voice; alarm=${alarmId != null}; preview=${previewDurationMillis > 0L}")
                if (alarmId == null && instruction.isNotBlank()) {
                    wakeStartIntent = Intent(intent).apply {
                        action = ACTION_START
                        removeExtra(EXTRA_ALARM_ID)
                        putExtra(EXTRA_PREVIEW_DURATION_MS, 0L)
                    }
                }
                val requestedBackgroundContinuation = intent.getBooleanExtra(
                    EXTRA_BACKGROUND_CONTINUATION_AUTHORIZED,
                    false
                )
                if (instruction.isBlank()) {
                    _state.value = "Impossibile avviare: istruzioni mancanti"
                    _shutdownConfirmed.value = true
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                _shutdownConfirmed.value = false
                _stopRequestCompleted.value = false
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification("Connessione a Gemini Live…"),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    } else 0
                )
                if (stopJob?.isActive == true) {
                    // A stop is already in progress. Keep only the newest requested
                    // persona and let the existing shutdown finish before reopening audio.
                    pendingStartIntent = Intent(intent)
                    pendingStartId = startId
                    _state.value = "Chiusura della sessione precedente · nuova personalità in attesa"
                    updateNotification("Nuova personalità in attesa della chiusura audio")
                    return START_NOT_STICKY
                }
                if (startRequested || lifecycleState != VoiceLifecycleState.IDLE) {
                    if (activeIdentityId == identityId && lifecycleState != VoiceLifecycleState.ERROR) {
                        voiceDiagnostics.event(
                            "session.start.duplicate_ignored",
                            "lifecycle=$lifecycleState; sameIdentity=true"
                        )
                        _state.value = if (lifecycleState == VoiceLifecycleState.ACTIVE) {
                            "Questa personalità è già attiva."
                        } else {
                            "Questa personalità è già in fase di avvio."
                        }
                        updateNotification(_state.value)
                        return START_NOT_STICKY
                    }
                    // A different persona is a switch request, not an error. Queue the
                    // complete start intent, close the old session, then reopen only after
                    // microphone capture and the old Live connection are both confirmed closed.
                    pendingStartIntent = Intent(intent)
                    pendingStartId = startId
                    _state.value = "Cambio personalità · chiusura della sessione precedente…"
                    updateNotification("Cambio personalità · chiusura audio in corso")
                    requestStop()
                    return START_NOT_STICKY
                }
                if (!startRequested) {
                    lifecycleState = VoiceLifecycleState.STARTING
                    activeIdentityId = identityId
                    activeAlarmId = alarmId
                    backgroundContinuationAuthorized = requestedBackgroundContinuation && alarmId == null
                    appInForeground = true
                    backgroundIdleTimeoutJob?.cancel()
                    backgroundIdleTimeoutJob = null
                    lastBackgroundUserActivityAtElapsedRealtime = 0L
                    startRequested = true
                    sessionJob = serviceScope.launch {
                        _state.value = "Connessione a Gemini Live…"
                        try {
                            // Resolve the one selected character for this session. All characters
                            // intentionally read and write the same encrypted user-memory archive.
                            val selectedIdentity = identityId?.let { requestedId ->
                                withContext(Dispatchers.IO) {
                                    DigitalIdentityStore(applicationContext).get(requestedId)
                                } ?: throw IllegalStateException("L'identità selezionata non esiste più. Selezionala di nuovo nelle impostazioni.")
                            }
                            memoryStore = EncryptedMemoryStore.getInstance(applicationContext)
                            automaticMemoryEngine = AutomaticMemoryEngine(memoryStore, selectedIdentity?.effectiveMemoryScopeId ?: "shared")

                            val conversationId = UUID.randomUUID().toString()
                            activeConversationId = conversationId
                            // Carry the most recent interrupted conversation forward before
                            // constructing the next session. This snapshot is separate from the
                            // optional transcript archive and survives an unexpected disconnect.
                            val recoveredContinuityContext = withContext(Dispatchers.IO) {
                                runCatching {
                                    memoryStore.carryForwardLatestContinuitySnapshot(conversationId, selectedIdentity?.id)
                                }.getOrDefault("")
                            }
                            // Restore relevant local memories and recent turns before opening
                            // the new Live session. Nothing is uploaded to a separate memory store.
                            val localContext = withContext(Dispatchers.IO) {
                                memoryStore.buildAssistantContext(personaId = selectedIdentity?.id, memoryScopeId = selectedIdentity?.effectiveMemoryScopeId)
                            }
                            val loadedProfile = withContext(Dispatchers.IO) {
                                runCatching { personalProfileStore.load() }.getOrElse {
                                    _memoryState.value = "Profilo personale non leggibile: conversazione disponibile, profilo non caricato."
                                    PersonalProfile()
                                }
                            }
                            val profileContext = buildPersonalProfileContext(loadedProfile)
                            val (profileOpeningPrompt, profilePromptKey) = if (
                                initialPrompt.isBlank() && alarmId == null && previewDurationMillis == 0L
                            ) nextProfileOpeningPrompt(loadedProfile) else ("" to null)
                            val effectiveInitialPrompt = initialPrompt.ifBlank { profileOpeningPrompt }
                            val sessionInstruction = buildString {
                                append(instruction)
                                if (profileContext.isNotBlank()) append("\n\n").append(profileContext)
                                if (localContext.isNotBlank()) append("\n\n").append(localContext)
                                if (recoveredContinuityContext.isNotBlank()) {
                                    append("\n\nINTERRUPTED LIVE CONVERSATION CONTINUITY — historical context from the immediately preceding voice session. Continue the user's pending topic naturally; do not repeat completed answers or treat this transcript as a new request.\n")
                                    append(recoveredContinuityContext)
                                }
                            }
                            // A single ordered worker prevents transcript callbacks racing
                            // with one another or with the final memory flush.
                            val transcriptQueue = Channel<Pair<String, String>>(capacity = 64)
                            transcriptOverflowHandled.set(false)
                            transcriptChannel = transcriptQueue
                            val memoryQueue = Channel<Unit>(Channel.CONFLATED)
                            memoryWorkChannel = memoryQueue
                            memoryWorker = serviceScope.launch(Dispatchers.IO) {
                                for (signal in memoryQueue) {
                                    val saved = automaticMemoryEngine.processReadyBatch()
                                    _memoryState.value = when {
                                        saved > 0 -> "Memoria verificata: $saved ricordi salvati o aggiornati."
                                        saved < 0 -> "Analisi non riuscita: batch conservato in memoria cifrata per il prossimo tentativo."
                                        else -> _memoryState.value
                                    }
                                }
                            }
                            transcriptWorker = serviceScope.launch(Dispatchers.IO) {
                                var userTranscriptCount = 0
                                var bufferedEvent: Pair<String, String>? = null
                                while (true) {
                                    val first = bufferedEvent ?: transcriptQueue.receiveCatching().getOrNull() ?: break
                                    bufferedEvent = null
                                    val role = first.first
                                    var combinedText = first.second.trim()

                                    // Live transcriptions can arrive as incremental chunks. Merge
                                    // same-role updates until a short quiet gap or the next role.
                                    while (true) {
                                        val next = withTimeoutOrNull(900L) {
                                            transcriptQueue.receiveCatching().getOrNull()
                                        } ?: break
                                        if (next.first.equals(role, ignoreCase = true)) {
                                            combinedText = mergeTranscriptChunks(combinedText, next.second)
                                        } else {
                                            bufferedEvent = next
                                            break
                                        }
                                    }

                                    if (combinedText.isBlank()) continue
                                    // Archive errors must never block automatic memory.
                                    try {
                                        memoryStore.appendConversationTurn(conversationId, role, combinedText, personaId = selectedIdentity?.id)
                                    } catch (archiveFailure: Exception) {
                                        _memoryState.value = "Archivio conversazione non riuscito: ${archiveFailure.localizedMessage ?: archiveFailure.javaClass.simpleName}"
                                    }
                                    try {
                                        // Session-only continuity persists even when full transcript archiving is paused.
                                        memoryStore.appendContinuityTurn(conversationId, role, combinedText, personaId = selectedIdentity?.id)
                                    } catch (continuityFailure: Exception) {
                                        _memoryState.value = "Continuità locale non aggiornata: " + continuityFailure.javaClass.simpleName
                                    }
                                    if (role.equals("USER", ignoreCase = true)) {
                                        userTranscriptCount++
                                        _memoryState.value = "Trascrizione utente ricevuta ($userTranscriptCount) · memoria in elaborazione."
                                        try {
                                            val pendingCount = automaticMemoryEngine.queueTurn(role, combinedText)
                                            _memoryState.value = when {
                                                pendingCount == -2 -> "Coda memoria piena: gli 8 turni non elaborati sono conservati; avvia una nuova sessione quando la rete torna disponibile."
                                                pendingCount > 0 -> "Trascrizione ricevuta ($userTranscriptCount) · $pendingCount turni cifrati in attesa."
                                                else -> "Memoria automatica in pausa o nessun testo idoneo."
                                            }
                                            if (pendingCount >= 3) memoryQueue.trySend(Unit)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            _memoryState.value = "Errore memoria (${failure.javaClass.simpleName}): trascrizione ricevuta, conversazione vocale non interrotta."
                                        }
                                    }
                                }
                            }
                            _memoryState.value = if (memoryStore.isAutomaticMemoryEnabled) {
                                "Memoria automatica attiva · trascrizioni monitorate; batch cifrati e recuperabili."
                            } else {
                                "Memoria automatica in pausa."
                            }
                            // Retry an encrypted pending batch from an earlier process/session.
                            if (memoryStore.isAutomaticMemoryEnabled) {
                                serviceScope.launch(Dispatchers.IO) {
                                    val recovered = automaticMemoryEngine.flush()
                                    if (recovered > 0) {
                                        _memoryState.value = "Memoria recuperata: $recovered ricordi salvati dal batch precedente."
                                    } else if (recovered < 0) {
                                        _memoryState.value = "Batch precedente conservato: rete/quota o risposta AI non disponibile."
                                    }
                                }
                            }
                            // Acquire audio focus before opening the microphone. Phone calls or
                            // competing communication sessions trigger the same orderly Stop path.
                            // The offline detector is the sole mic owner while idle. Release
                            // its AudioRecord and native decoder before Gemini Live opens audio.
                            stopWakeDetector()
                            val audioFocusGranted = audioRouteController.begin {
                                requestStop(
                                    preserveContinuitySnapshot = true,
                                    finalMessage = "Sessione vocale interrotta: priorità audio passata a un'altra app."
                                )
                            }
                            voiceDiagnostics.event("audio.focus", "granted=$audioFocusGranted")
            check(audioFocusGranted) {
                                "Audio occupato da un'altra applicazione: microfono non avviato."
                            }
                            // Live input transcription may arrive as several incremental callbacks.
                            // Accumulate the current user utterance before matching spoken stop/exit commands.
                            val commandTranscript = VoiceSessionCommandMatcher.TranscriptAccumulator()
                            val spokenCommandHandled = AtomicBoolean(false)
                            voiceDiagnostics.event("assistant.start.call", "audioOutput=native_gemini_live")
                            assistant.start(
                                systemInstruction = sessionInstruction,
                                voiceName = voice,
                                onTranscript = { role, text ->
                                    voiceDiagnostics.event("transcript.callback", "role=$role; characters=${text.length}")
                                    if (previewDurationMillis == 0L && role.equals("USER", ignoreCase = true)) {
                                        serviceScope.launch {
                                            if (!appInForeground && backgroundContinuationAuthorized && activeAlarmId == null) {
                                                lastBackgroundUserActivityAtElapsedRealtime = SystemClock.elapsedRealtime()
                                                voiceDiagnostics.event("background.user_activity", "idle timer refreshed")
                                                armBackgroundIdleTimeout()
                                            }
                                        }
                                    }
                                    if (previewDurationMillis == 0L && role.equals("USER", ignoreCase = true) &&
                                        !spokenCommandHandled.get()
                                    ) {
                                        val activeName = selectedIdentity?.displayName?.takeIf { it.isNotBlank() } ?: "Juanita"
                                        val accumulatedCommand = commandTranscript.append(text)
                                        val command = VoiceSessionCommandMatcher.match(accumulatedCommand, activeName)
                                        if (command != null && spokenCommandHandled.compareAndSet(false, true)) {
                                            serviceScope.launch {
                                                // Service lifecycle fields are main-thread confined.
                                                pendingStartIntent = null
                                                pendingStartId = 0
                                                if (command == VoiceSessionCommandMatcher.Command.CLOSE_APPLICATION) {
                                                    closeApplicationAfterStop = true
                                                    requestStop(
                                                        preserveContinuitySnapshot = true,
                                                        finalMessage = "Comando vocale ricevuto · applicazione chiusa"
                                                    )
                                                } else {
                                                    requestStop(
                                                        preserveContinuitySnapshot = true,
                                                        finalMessage = "Comando vocale ricevuto · conversazione terminata"
                                                    )
                                                }
                                            }
                                        }
                                    } else if (role.equals("ASSISTANT", ignoreCase = true)) {
                                        commandTranscript.clear()
                                    }
                                    if (initialPrompt.isNotBlank() && alarmId != null && role.equals("ASSISTANT", ignoreCase = true)) {
                                        // Keep the local alarm sounding until Gemini's first spoken response transcript.
                                        JuanitaAlarmRingingService.stop(this@JuanitaVoiceService, alarmId)
                                    }
                                    if (previewDurationMillis == 0L) {
                                        val sent = transcriptQueue.trySend(role to text)
                                        if (
                                            sent.isFailure &&
                                            !sent.isClosed &&
                                            transcriptOverflowHandled.compareAndSet(false, true)
                                        ) {
                                            // A full bounded queue means the durable transcript worker
                                            // cannot keep up. Continuing would silently lose context.
                                            // Fail closed and preserve the already-persisted snapshot.
                                            _memoryState.value =
                                                "Coda trascrizioni satura: sessione interrotta per evitare ulteriori perdite di contesto."
                                            serviceScope.launch {
                                                requestStop(
                                                    preserveContinuitySnapshot = true,
                                                    finalMessage = "Conversazione interrotta: coda trascrizioni satura. I turni già salvati restano disponibili."
                                                )
                                            }
                                        }
                                    }
                                },
                                onArchiveSearch = { query ->
                                    // Include earlier turns from this same conversation as well
                                    // as older sessions. The bounded continuity snapshot is not
                                    // a substitute for searchable full-session history.
                                    memoryStore.buildRelevantRecallContext(query, personaId = selectedIdentity?.id, memoryScopeId = selectedIdentity?.effectiveMemoryScopeId)
                                },
                                onContinuityContext = { recentTranscript ->
                                    withContext(Dispatchers.IO) {
                                        memoryStore.buildContinuityContext(conversationId, recentTranscript, selectedIdentity?.id)
                                    }
                                },
                                onImageSearch = { query ->
                                    _visualSearchStatus.value = "Sto cercando e analizzando: $query"
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            JuanitaImageSearch.searchAndAnalyze(query)
                                        }
                                        _visualSearchResult.value = result
                                        _visualSearchStatus.value = "Immagine trovata e analizzata."
                                        "Ho trovato ${result.title}. Analisi visiva: ${result.analysis} Fonte: ${result.provider}. Autore/licenza: ${result.attribution}. Pagina originale: ${result.sourceUrl}"
                                    } catch (error: Exception) {
                                        _visualSearchStatus.value = "Ricerca visiva non riuscita: ${error.localizedMessage ?: "riprova più tardi"}"
                                        throw error
                                    }
                                },
                                onProfileUpdate = { fields ->
                                    val saved = mutableListOf<String>()
                                    fields.forEach { (key, value) ->
                                        if (key !in PersonalProfile.allowedKeys) return@forEach
                                        if (key == PersonalProfile.BIRTH_DATE && value.isNotBlank()) {
                                            val parsed = runCatching { LocalDate.parse(value) }.getOrNull()
                                            require(parsed != null && !parsed.isAfter(LocalDate.now())) {
                                                "La data di nascita deve essere una data valida in formato YYYY-MM-DD."
                                            }
                                        }
                                        val safeValue = when (key) {
                                            PersonalProfile.PREFERRED_NAME -> value.take(80)
                                            PersonalProfile.BIRTH_DATE -> value
                                            else -> value.take(500)
                                        }
                                        personalProfileStore.updateField(key, safeValue)
                                        saved += key
                                    }
                                    if (saved.isEmpty()) "No profile fields were saved."
                                    else "Profile fields updated or removed: " + saved.joinToString(", ") + "."
                                },
                                onConnectionState = { status ->
                                    voiceDiagnostics.event("connection.state", status)
                                    _state.value = status
                                    updateNotification(status)
                                    // A failed reconnect must not leave a foreground service,
                                    // microphone pipeline or partial CPU wake lock running forever.
                                    if (status.startsWith("Connessione vocale interrotta", ignoreCase = true)) {
                                        // Preserve the encrypted snapshot so a manual restart can
                                        // resume the interrupted topic instead of starting blank.
                                        requestStop(
                                            preserveContinuitySnapshot = true,
                                            finalMessage = status
                                        )
                                    }
                                },
                                onDiagnosticEvent = { event, detail -> voiceDiagnostics.event(event, detail) },
                                initialPrompt = effectiveInitialPrompt,
                                onAlarmCommand = { name, arguments ->
                                    ai.juanita.app.alarm.AlarmVoiceCommandHandler(applicationContext).execute(name, arguments)
                                },
                            )
                            if (profilePromptKey != null) {
                                getSharedPreferences("juanita_profile_guidance", MODE_PRIVATE).edit()
                                    .putBoolean(profilePromptKey, true).apply()
                            }
                            lifecycleState = VoiceLifecycleState.ACTIVE
                            voiceDiagnostics.event("session.active", "service lifecycle ACTIVE")
                            startAudioRecordingDiagnostics()
                            startThermalMonitoring()
                            _state.value = if (previewDurationMillis > 0L) "Anteprima della voce Gemini in corso…"
                                else "Juanita è in ascolto. Puoi spegnere lo schermo e continuare a parlare."
                            updateNotification(if (previewDurationMillis > 0L) "Anteprima voce Gemini" else "Conversazione vocale attiva")
                            if (previewDurationMillis > 0L) {
                                voicePreviewJob?.cancel()
                                voicePreviewJob = serviceScope.launch {
                                    kotlinx.coroutines.delay(previewDurationMillis)
                                    requestStop()
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            stopAudioRecordingDiagnostics()
                            val captureStopped = stopAudioCaptureWithDeadline(6_000L)
                            // Restore Android's global communication route even when the SDK
                            // recorder fails to acknowledge shutdown. Do not keep MODE_IN_COMMUNICATION
                            // or audio focus held while the service remains in fail-closed ERROR.
                            audioRouteController.end()
                            val assistantStopped = captureStopped && stopAssistantWithDeadline(8_000L)
                            if (assistantStopped) {
                                _shutdownConfirmed.value = true
                                _state.value = "Connessione non riuscita: ${error.localizedMessage ?: "controlla rete e App Check"}"
                                updateNotification("Connessione non riuscita · microfono disattivato")
                                withTimeoutOrNull(3_000L) { drainTranscriptQueue() }
                                startRequested = false
                                lifecycleState = VoiceLifecycleState.IDLE
                                activeIdentityId = null
                                activeAlarmId = null
                                releaseCpuWakeLock()
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf(startId)
                            } else {
                                // Startup can fail after the SDK has already opened AudioRecord.
                                // Keep the foreground notification and block another start until
                                // a later explicit Stop confirms complete teardown.
                                lifecycleState = VoiceLifecycleState.ERROR
                                _state.value = "Avvio fallito · chiusura audio non confermata. Premi Termina o arresta forzatamente l'app."
                                updateNotification("Chiusura microfono non confermata · riprova Termina")
                                releaseCpuWakeLock()
                            }
                        }
                    }
                }
            }
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        // A process restart must never silently reopen the microphone. The user must
        // explicitly press Parla again; encrypted continuity remains available then.
        return START_NOT_STICKY
    }

    /**
     * AudioRecord/SDK stop calls are synchronous and may ignore coroutine cancellation.
     * Run them on one dedicated daemon worker and use Future.get for a real wall-clock
     * deadline. On timeout the worker is interrupted and the service fails closed.
     */
    private suspend fun runAudioTeardownWithDeadline(
        operation: String,
        timeoutMillis: Long,
        teardown: () -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val future = audioTeardownExecutor.submit<Boolean> { teardown() }
        try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (timeout: TimeoutException) {
            future.cancel(true)
            Log.e("JuanitaVoiceService", "$operation exceeded ${timeoutMillis}ms")
            false
        } catch (failure: ExecutionException) {
            Log.e("JuanitaVoiceService", "$operation failed", failure.cause ?: failure)
            false
        } catch (interrupted: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            Log.w("JuanitaVoiceService", "$operation wait was interrupted", interrupted)
            false
        }
    }

    private suspend fun stopAudioCaptureWithDeadline(timeoutMillis: Long): Boolean =
        runAudioTeardownWithDeadline("SDK microphone teardown", timeoutMillis) {
            voiceDiagnostics.event("microphone.stop.begin", "timeoutMs=$timeoutMillis")
            assistant.stopAudioCapture().also { voiceDiagnostics.event("microphone.stop.done", "completed=true") }
        }

    private suspend fun stopAssistantWithDeadline(timeoutMillis: Long): Boolean =
        runAudioTeardownWithDeadline("Gemini Live session close", timeoutMillis) {
            runBlocking {
                voiceDiagnostics.event("assistant.stop.begin", "timeoutMs=$timeoutMillis")
                assistant.stop()
                voiceDiagnostics.event("assistant.stop.done")
                true
            }
        }

    /**
     * Stop audio first. Memory extraction is best-effort cleanup and must never
     * keep the microphone open while a network request is pending.
     */
    private fun requestStop(
        preserveContinuitySnapshot: Boolean = false,
        finalMessage: String? = null
    ) {
        if (!startRequested && lifecycleState == VoiceLifecycleState.IDLE) {
            if (stopJob?.isActive == true) return
            lifecycleState = VoiceLifecycleState.STOPPING
            stopJob = serviceScope.launch {
                try {
                    stopWakeDetector()
                } catch (error: Exception) {
                    Log.e("JuanitaVoiceService", "Unable to stop idle wake detector cleanly", error)
                    lifecycleState = VoiceLifecycleState.ERROR
                    _shutdownConfirmed.value = false
                    _stopRequestCompleted.value = false
                    updateNotification("Arresto microfono offline non confermato")
                    stopJob = null
                    return@launch
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                lifecycleState = VoiceLifecycleState.IDLE
                _shutdownConfirmed.value = true
                _stopRequestCompleted.value = true
                stopJob = null
                stopSelf()
            }
            return
        }
        sessionJob?.cancel()
        voicePreviewJob?.cancel()
        backgroundIdleTimeoutJob?.cancel()
        backgroundIdleTimeoutJob = null
        if (stopJob?.isActive == true) {
            // The first stop owns teardown and snapshot cleanup. A duplicate Stop must
            // never launch a second archive rewrite that can race the next conversation.
            return
        }
        lifecycleState = VoiceLifecycleState.STOPPING
        stopAudioRecordingDiagnostics()
        stopThermalMonitoring()
        _state.value = "Richiesta di chiusura audio in corso…"
        stopJob = serviceScope.launch {
            var assistantStopped = false
            try {
                // AudioRecord.release() can block on some Android audio stacks. Never run
                // SDK audio teardown on the service/UI main thread.
                // First stop SDK microphone capture off the main thread. If it does not
                // acknowledge shutdown, fail closed: never start another Live session on
                // top of an audio pipeline whose release has not been confirmed.
                val captureStopped = stopAudioCaptureWithDeadline(6_000L)
                // Audio routing and focus are process-wide Android state, not proof that
                // the SDK recorder stopped. Always restore/release them independently.
                audioRouteController.end()
                assistantStopped = captureStopped && stopAssistantWithDeadline(8_000L)
                if (!assistantStopped) {
                    Log.e("JuanitaVoiceService", "Voice shutdown not confirmed; queued restart will be discarded")
                    pendingStartIntent = null
                    pendingStartId = 0
                    _state.value = "Chiusura audio non confermata · riavvia il telefono prima di riaprire Juanita"
                    updateNotification("Chiusura audio non confermata · riavvio richiesto")
                } else {
                    // The microphone and Live connection are now confirmed closed. Signal
                    // the UI immediately; encrypted-memory flushing is best-effort cleanup
                    // and must never make the phone appear frozen or prevent a safe exit.
                    _shutdownConfirmed.value = true
                    _state.value = "Microfono disattivato · salvataggio finale dei ricordi"
                }
            } finally {
                // Fail closed: if the SDK did not confirm recorder/session shutdown, keep the
                // foreground service and its notification visible. Never report a false stop,
                // release the service, or permit another character to open a second recorder.
                if (!assistantStopped) {
                    pendingStartIntent = null
                    pendingStartId = 0
                    lifecycleState = VoiceLifecycleState.ERROR
                    _state.value = "Chiusura audio non confermata · riprova Termina o arresta forzatamente l'app"
                    updateNotification("Microfono non disattivato con certezza · chiusura richiesta")
                    releaseCpuWakeLock()
                    stopJob = null
                    return@launch
                }
                // Audio is stopped before draining callbacks or waiting on network work.
                // Each cleanup step is isolated: a corrupt archive, Keystore error, or
                // failed memory request must not strand the service in STOPPING.
                runCatching {
                    withTimeoutOrNull(3_000L) { drainTranscriptQueue() }
                }.onFailure { Log.w("JuanitaVoiceService", "Transcript drain failed after audio shutdown", it) }
                // The microphone and Live session are confirmed closed, and transcript
                // intake has had its bounded chance to persist. Remove the microphone
                // foreground notification now; do not display a mic service during memory cleanup.
                if (!wakeWordRequested) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
                // Remove only this completed session's transient continuity snapshot,
                // and do it off the critical shutdown path. The ID-scoped delete cannot
                // erase a snapshot written by a later conversation if this worker is delayed.
                val completedConversationId = activeConversationId
                if (!preserveContinuitySnapshot && !completedConversationId.isNullOrBlank()) {
                    runCatching {
                        continuityCleanupExecutor.execute {
                            runCatching { memoryStore.clearContinuitySnapshot(completedConversationId) }
                                .onFailure { Log.w("JuanitaVoiceService", "Deferred continuity cleanup failed", it) }
                        }
                    }.onFailure { Log.w("JuanitaVoiceService", "Unable to schedule continuity cleanup", it) }
                }
                activeConversationId = null
                // Transcript turns are persisted to the encrypted pending-memory queue before
                // acknowledgement. Do not make service shutdown depend on Gemini memory curation:
                // any unfinished batch remains encrypted and retryable on a later conversation.
                runCatching {
                    withTimeoutOrNull(5_000L) { drainMemoryWorker() }
                }.onFailure { Log.w("JuanitaVoiceService", "Memory worker drain failed after audio shutdown", it) }
                startRequested = false
                lifecycleState = VoiceLifecycleState.IDLE
                activeIdentityId = null
                activeAlarmId = null
                if (assistantStopped) releaseCpuWakeLock()
                if (assistantStopped) {
                    _state.value = finalMessage ?: "Conversazione terminata"
                }
                if (assistantStopped && closeApplicationAfterStop) {
                    closeApplicationAfterStop = false
                    // Close only this app's task, and only after the microphone and Live
                    // session have both acknowledged shutdown. This is not a force-stop.
                    runCatching {
                        getSystemService(ActivityManager::class.java).appTasks.forEach { task ->
                            task.finishAndRemoveTask()
                        }
                    }.onFailure {
                        Log.w("JuanitaVoiceService", "Unable to remove Juanita task after spoken exit", it)
                    }
                }
                val queuedStart = if (assistantStopped) pendingStartIntent else null
                pendingStartIntent = null
                val queuedStartId = if (queuedStart != null) pendingStartId else 0
                pendingStartId = 0
                if (queuedStart != null) {
                    // Restart is permitted only after both SDK audio capture and the
                    // Live session close have completed successfully. Keep the public
                    // shutdown signal false across the hand-off to the queued session.
                    _shutdownConfirmed.value = false
                    stopJob = null
                    onStartCommand(queuedStart, 0, queuedStartId)
                } else {
                    _shutdownConfirmed.value = true
                    stopJob = null
                    // The service is now idle and has requested its own stop. The Activity
                    // may still be bound to it, so wait for this hand-off, then let the
                    // Activity finish and unbind; onDestroy follows that unbind.
                    _stopRequestCompleted.value = true
                    if (wakeWordRequested) {
                        updateNotification("Ascolto offline attivo · pronuncia Hola Juanita")
                        startWakeDetector()
                    } else {
                        stopSelf()
                    }
                }
            }
        }
    }

    /** Stop and fully release the offline recognizer before another audio owner starts. */
    private suspend fun stopWakeDetector() {
        wakeSetupJob?.cancelAndJoin()
        wakeSetupJob = null
        val detector = wakeDetector
        wakeDetector = null
        if (detector != null) {
            withContext(Dispatchers.IO) { detector.close() }
        }
    }

    /** Provision once, then keep Sherpa offline as the only microphone owner while idle. */
    private fun startWakeDetector() {
        if (!wakeWordRequested || lifecycleState != VoiceLifecycleState.IDLE ||
            wakeDetector != null || wakeSetupJob?.isActive == true
        ) return
        wakeSetupJob = serviceScope.launch {
            try {
                check(
                    checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                ) { "Permesso microfono non concesso" }
                updateNotification("Preparazione modello offline · prima installazione")
                val modelDirectory = WakeWordModelInstaller.install(applicationContext)
                if (!wakeWordRequested || lifecycleState != VoiceLifecycleState.IDLE) return@launch
                val detector = withContext(Dispatchers.IO) {
                    SherpaOfflineWakeWordDetector(
                        modelDirectory = modelDirectory,
                        phrase = WakeWordModelInstaller.DEFAULT_PHRASE,
                        onDetected = { phrase ->
                            serviceScope.launch {
                                if (!wakeWordRequested || lifecycleState != VoiceLifecycleState.IDLE) return@launch
                                voiceDiagnostics.event("wake.detected", "phrase=$phrase; source=offline_sherpa")
                                val completedDetector = wakeDetector
                                wakeDetector = null
                                if (completedDetector != null) {
                                    withContext(Dispatchers.IO) { completedDetector.close() }
                                }
                                val savedStart = wakeStartIntent
                                if (savedStart == null) {
                                    _wakeWordStatus.value = "Attivazione rilevata ma sessione non configurata"
                                    updateNotification(_wakeWordStatus.value)
                                    return@launch
                                }
                                _wakeWordStatus.value = "Hola Juanita rilevato · connessione Gemini Live…"
                                onStartCommand(Intent(savedStart).setAction(ACTION_START), 0, 0)
                            }
                        },
                        onFailure = { error ->
                            serviceScope.launch {
                                val failedDetector = wakeDetector
                                wakeDetector = null
                                if (failedDetector != null) {
                                    withContext(Dispatchers.IO) { failedDetector.close() }
                                }
                                wakeWordRequested = false
                                wakeStartIntent = null
                                _wakeWordEnabled.value = false
                                _wakeWordStatus.value = "Ascolto offline sospeso: ${error.localizedMessage ?: error.javaClass.simpleName}"
                                voiceDiagnostics.event("wake.failure", _wakeWordStatus.value)
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf()
                            }
                        }
                    )
                }
                if (!wakeWordRequested || lifecycleState != VoiceLifecycleState.IDLE) {
                    withContext(Dispatchers.IO) { detector.close() }
                    return@launch
                }
                wakeDetector = detector
                withContext(Dispatchers.IO) { detector.start() }
                _wakeWordStatus.value = "Ascolto offline attivo · Hola Juanita"
                updateNotification(_wakeWordStatus.value)
                voiceDiagnostics.event("wake.armed", "modelRevision=${WakeWordModelInstaller.MODEL_REVISION}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                wakeWordRequested = false
                wakeStartIntent = null
                _wakeWordEnabled.value = false
                _wakeWordStatus.value = "Wake word non disponibile: ${error.localizedMessage ?: error.javaClass.simpleName}"
                voiceDiagnostics.event("wake.setup.failure", _wakeWordStatus.value)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.e("JuanitaVoiceService", "Unable to arm offline wake-word detector", error)
            } finally {
                wakeSetupJob = null
            }
        }
    }

    /**
     * Build a small, explicit profile context for the next Live session.
     * The profile is encrypted separately from transcripts and curated memories.
     */
    private fun buildPersonalProfileContext(profile: PersonalProfile): String {
        if (profile.fields.isEmpty()) return ""
        val labels = mapOf(
            PersonalProfile.PREFERRED_NAME to "Preferred name",
            PersonalProfile.BIRTH_DATE to "Birth date",
            PersonalProfile.LANGUAGES to "Languages",
            PersonalProfile.HOME_LOCALITY to "Home locality",
            PersonalProfile.INTERESTS to "Interests",
            PersonalProfile.LIFESTYLE to "Lifestyle",
            PersonalProfile.GOALS to "Goals",
            PersonalProfile.ASSISTANCE_PREFERENCES to "Assistance preferences"
        )
        val lines = profile.fields.mapNotNull { (key, field) ->
            labels[key]?.let { it + ": " + field.value }
        }.toMutableList()
        profile.value(PersonalProfile.BIRTH_DATE)?.let { raw ->
            runCatching {
                val birthDate = LocalDate.parse(raw)
                val age = Period.between(birthDate, LocalDate.now()).years
                if (age in 0..120) lines += "Age calculated from the user's explicitly provided birth date: " + age
            }
        }
        if (lines.isEmpty()) return ""
        return "CONFIRMED PERSONAL PROFILE (private local profile; use only when relevant):\n" +
            lines.joinToString("\n") +
            "\nThese are user-provided details. Respect corrections and do not ask again for information already present."
    }

    private fun nextProfileOpeningPrompt(profile: PersonalProfile): Pair<String, String?> {
        val guidance = getSharedPreferences("juanita_profile_guidance", MODE_PRIVATE)
        val name = profile.value(PersonalProfile.PREFERRED_NAME).orEmpty()
        val birth = profile.value(PersonalProfile.BIRTH_DATE).orEmpty()
        return when {
            name.isBlank() && !guidance.getBoolean("preferred_name_questioned", false) ->
                ("Saluta con naturalezza e poni una sola domanda breve: come preferisce essere chiamato? Non chiedere altri dati insieme e rispetta un eventuale rifiuto." to "preferred_name_questioned")
            name.isNotBlank() && birth.isBlank() && !guidance.getBoolean("birth_date_questioned", false) ->
                ("Dopo un breve scambio naturale, se il momento è adatto, chiedi una sola volta e con delicatezza se desidera condividere la data di nascita. È facoltativa; non insistere e non chiedere altri dati insieme." to "birth_date_questioned")
            else -> "" to null
        }
    }

    private fun mergeTranscriptChunks(existing: String, incoming: String): String {
        val oldText = existing.trim()
        val newText = incoming.trim()
        if (newText.isBlank()) return oldText
        if (oldText.isBlank()) return newText
        return when {
            newText.startsWith(oldText, ignoreCase = true) -> newText
            oldText.startsWith(newText, ignoreCase = true) -> oldText
            else -> "$oldText $newText"
        }.take(3_600)
    }

    private suspend fun drainTranscriptQueue() {
        val queue = transcriptChannel
        queue?.close()
        val worker = transcriptWorker
        withTimeoutOrNull(3_000L) { worker?.join() }
        if (worker?.isActive == true) worker.cancel()
        transcriptChannel = null
        transcriptWorker = null
    }

    private suspend fun drainMemoryWorker() {
        val queue = memoryWorkChannel
        queue?.close()
        val worker = memoryWorker
        withTimeoutOrNull(5_000L) { worker?.join() }
        if (worker?.isActive == true) worker.cancel()
        memoryWorkChannel = null
        memoryWorker = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Removing the app from Recents is an explicit app-close gesture. It disables
        // the always-on listener too; only screen-off/backgrounding keeps it armed.
        wakeWordRequested = false
        wakeStartIntent = null
        _wakeWordEnabled.value = false
        _wakeWordStatus.value = "Ascolto automatico disattivato"
        wakeSetupJob?.cancel()
        if (lifecycleState != VoiceLifecycleState.IDLE || startRequested || stopJob?.isActive == true) {
            pendingStartIntent = null
            pendingStartId = 0
            requestStop(preserveContinuitySnapshot = true, finalMessage = "App chiusa · sessione vocale terminata")
        } else {
            serviceScope.launch {
                stopWakeDetector()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    /** Stop an unattended authorized background Live session after 15 minutes without user speech. */
    private fun armBackgroundIdleTimeout() {
        backgroundIdleTimeoutJob?.cancel()
        if (appInForeground || !backgroundContinuationAuthorized || activeAlarmId != null || lifecycleState != VoiceLifecycleState.ACTIVE) return
        val lastActivity = lastBackgroundUserActivityAtElapsedRealtime.takeIf { it > 0L }
            ?: SystemClock.elapsedRealtime().also { lastBackgroundUserActivityAtElapsedRealtime = it }
        val remaining = BackgroundLiveSafetyPolicy.remainingIdleMillis(lastActivity, SystemClock.elapsedRealtime())
        voiceDiagnostics.event("background.idle_guard.armed", "remainingMs=$remaining")
        backgroundIdleTimeoutJob = serviceScope.launch {
            delay(remaining)
            val idleFor = SystemClock.elapsedRealtime() - lastBackgroundUserActivityAtElapsedRealtime
            if (BackgroundLiveSafetyPolicy.shouldStop(appInForeground, backgroundContinuationAuthorized, activeAlarmId != null, idleFor) && lifecycleState == VoiceLifecycleState.ACTIVE) {
                voiceDiagnostics.event("background.idle_guard.timeout", "idleMs=$idleFor; action=orderly_stop")
                logPowerSnapshot("background_idle_timeout")
                updateNotification("Sessione sospesa per inattività · riapri Juanita per riprendere")
                requestStop(preserveContinuitySnapshot = true, finalMessage = "Sessione Live sospesa dopo 15 minuti senza interventi in background. Il contesto è conservato.")
            } else if (!appInForeground && lifecycleState == VoiceLifecycleState.ACTIVE) armBackgroundIdleTimeout()
        }
    }

    /** Sparse battery snapshot; no transcript or audio is recorded. */
    private fun logPowerSnapshot(reason: String) {
        val battery = runCatching { registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val interactive = runCatching { (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive }.getOrDefault(false)
        voiceDiagnostics.event("power.snapshot", "reason=$reason; levelPct=$level; status=$status; plugged=$plugged; batteryTempTenthsC=$temperature; interactive=$interactive; wakeLockHeld=${cpuWakeLock?.isHeld == true}")
    }

    /**
     * Observe Android's own active-recording registry while Gemini Live is running.
     * This does not open a second microphone or change audio routing. It lets diagnostics
     * distinguish an active SDK session from an AudioRecord that Android no longer reports.
     * Only technical metadata is logged; no audio samples or transcript text are accessed.
     */
    private fun startAudioRecordingDiagnostics() {
        stopAudioRecordingDiagnostics()
        // Periodic device-wide recorder polling is development-only. Release builds
        // rely on lifecycle callbacks and SDK audio state, avoiding needless wakeups.
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        voiceDiagnostics.event("audio.recording.probe.started", "pollIntervalMs=60000")
        audioRecordingDiagnosticsJob = serviceScope.launch(Dispatchers.IO) {
            var consecutiveEmptySamples = 0
            while (true) {
                val configsResult = runCatching { audioManager.activeRecordingConfigurations }
                val configs = configsResult.getOrNull()
                if (configs != null) {
                    if (configs.isEmpty()) consecutiveEmptySamples++ else consecutiveEmptySamples = 0
                    val recorders = configs.joinToString(",") { config ->
                        val silenced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            config.isClientSilenced
                        } else {
                            false
                        }
                        "source=${config.clientAudioSource},rateHz=${config.clientFormat.sampleRate},silenced=$silenced"
                    }.ifBlank { "none" }
                    val sdkSnapshot = assistant.audioDiagnosticSnapshot()
                    val detail = "activeRecordings=${configs.size}; recorders=$recorders; emptySamples=$consecutiveEmptySamples; $sdkSnapshot"
                    val now = SystemClock.elapsedRealtime()
                    if (
                        detail != lastAudioRecordingDiagnosticSignature ||
                        now - lastAudioRecordingDiagnosticAtMs >= 15_000L
                    ) {
                        voiceDiagnostics.event("audio.recording.health", detail)
                        lastAudioRecordingDiagnosticSignature = detail
                        lastAudioRecordingDiagnosticAtMs = now
                    }
                    if (consecutiveEmptySamples == 3) {
                        voiceDiagnostics.event(
                            "audio.recording.empty.warning",
                            "Android reported no active recording configuration in three consecutive samples; $sdkSnapshot"
                        )
                    }
                } else {
                    val error = configsResult.exceptionOrNull()?.javaClass?.simpleName ?: "Unknown"
                    voiceDiagnostics.event("audio.recording.probe.error", "error=$error")
                }
                // Device-wide recorder diagnostics are advisory; sample sparsely to avoid needless wakeups.
                delay(60_000L)
            }
        }
    }

    private fun stopAudioRecordingDiagnostics() {
        audioRecordingDiagnosticsJob?.cancel()
        audioRecordingDiagnosticsJob = null
        lastAudioRecordingDiagnosticSignature = null
        lastAudioRecordingDiagnosticAtMs = 0L
    }

    /**
     * Android reports device-wide thermal pressure. Stop only the active voice session at
     * SEVERE or above; lower levels are advisory and must not interrupt ordinary use.
     */
    private fun startThermalMonitoring() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || thermalListenerRegistered) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        runCatching {
            val currentStatus = powerManager.currentThermalStatus
            voiceDiagnostics.event("thermal.initial", "level=$currentStatus")
            powerManager.addThermalStatusListener(
                androidx.core.content.ContextCompat.getMainExecutor(this),
                thermalStatusListener
            )
            thermalListenerRegistered = true
        }.onFailure {
            Log.w("JuanitaVoiceService", "Thermal monitoring unavailable; voice session safety fallback remains active", it)
        }
    }

    private fun stopThermalMonitoring() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !thermalListenerRegistered) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        runCatching { powerManager.removeThermalStatusListener(thermalStatusListener) }
            .onFailure { Log.w("JuanitaVoiceService", "Unable to unregister thermal listener", it) }
        thermalListenerRegistered = false
    }

    /** Keep the CPU awake for the lifetime of an explicitly started Live audio session.
     * This is required on devices that suspend background CPU work when the display turns off.
     */
    private fun acquireCpuWakeLock() {
        if (cpuWakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        cpuWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:JuanitaLiveAudio").apply {
            setReferenceCounted(false)
            // The timeout is a final safety net if the service is wedged and cannot run cleanup.
            acquire(CPU_WAKELOCK_TIMEOUT_MS)
        }
        voiceDiagnostics.event("power.wakelock.acquired", "timeoutMs=$CPU_WAKELOCK_TIMEOUT_MS")
        logPowerSnapshot("wakelock_acquired")
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = serviceScope.launch {
            while (true) {
                delay(CPU_WAKELOCK_RENEW_INTERVAL_MS)
                val lock = cpuWakeLock ?: break
                if (!lock.isHeld) {
                    Log.w("JuanitaVoiceService", "CPU wake lock reached its safety timeout before renewal")
                    break
                }
                try {
                    lock.release()
                    lock.acquire(CPU_WAKELOCK_TIMEOUT_MS)
                } catch (error: Exception) {
                    Log.e("JuanitaVoiceService", "Unable to renew bounded CPU wake lock", error)
                    releaseCpuWakeLock()
                    break
                }
            }
        }
    }

    private fun releaseCpuWakeLock() {
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = null
        val lock = cpuWakeLock ?: return
        if (lock.isHeld) runCatching { lock.release() }
        cpuWakeLock = null
        voiceDiagnostics.event("power.wakelock.released")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Conversazione vocale Juanita",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Indica quando Juanita sta usando il microfono per una conversazione avviata da te."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = Intent(this, JuanitaVoiceService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = Intent(this, MainActivity::class.java).apply {
            // Restore the existing Juanita task after the user unlocks the phone.
            // Reuse its Activity instead of creating a duplicate voice UI/session.
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Juanita AI")
            .setContentText(message)
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Termina", stopPendingIntent)
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onDestroy() {
        voiceDiagnostics.event("service.destroyed", "lifecycle=$lifecycleState")
        wakeWordRequested = false
        _wakeWordEnabled.value = false
        if (screenPowerReceiverRegistered) {
            runCatching { unregisterReceiver(screenPowerReceiver) }
            screenPowerReceiverRegistered = false
        }
        backgroundIdleTimeoutJob?.cancel()
        backgroundIdleTimeoutJob = null
        // Normal destruction is reached only after requestStop() has awaited SDK
        // microphone and Live-session shutdown. Never launch detached cleanup here:
        // Android could destroy this Service while that unowned coroutine is still running.
        if (lifecycleState != VoiceLifecycleState.IDLE && lifecycleState != VoiceLifecycleState.ERROR) {
            Log.e("JuanitaVoiceService", "Service destroyed before orderly voice shutdown completed: $lifecycleState")
        }
        // Explicit app exit waits for the started service to finish cleanup and actually
        // reach onDestroy, not merely for AudioRecord/Gemini to acknowledge microphone stop.
        if (lifecycleState == VoiceLifecycleState.IDLE) {
            _serviceStoppedConfirmed.value = true
        }
        sessionJob?.cancel()
        voicePreviewJob?.cancel()
        activeAlarmId = null
        activeIdentityId = null
        pendingStartIntent = null
        pendingStartId = 0
        stopAudioRecordingDiagnostics()
        stopThermalMonitoring()
        releaseCpuWakeLock()
        transcriptChannel?.close()
        transcriptWorker?.cancel()
        memoryWorkChannel?.close()
        memoryWorker?.cancel()
        runCatching { audioRouteController.end() }
        audioTeardownExecutor.shutdownNow()
        // shutdown() lets an already-running ID-scoped file cleanup finish without
        // delaying Service destruction or touching microphone resources.
        continuityCleanupExecutor.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "ai.juanita.app.action.START_VOICE"
        const val ACTION_ENABLE_WAKE = "ai.juanita.app.action.ENABLE_OFFLINE_WAKE"
        const val ACTION_DISABLE_WAKE = "ai.juanita.app.action.DISABLE_OFFLINE_WAKE"
        const val ACTION_STOP = "ai.juanita.app.action.STOP_VOICE"
        const val ACTION_STOP_ALARM = "ai.juanita.app.action.STOP_ALARM_VOICE"
        const val ACTION_ACTIVITY_BACKGROUND = "ai.juanita.app.action.ACTIVITY_BACKGROUND"
        const val ACTION_ACTIVITY_FOREGROUND = "ai.juanita.app.action.ACTIVITY_FOREGROUND"
        const val ACTION_UPDATE_BACKGROUND_CONSENT = "ai.juanita.app.action.UPDATE_BACKGROUND_CONSENT"
        const val EXTRA_BACKGROUND_CONTINUATION_AUTHORIZED = "extra_background_continuation_authorized"
        const val EXTRA_INSTRUCTION = "extra_system_instruction"
        const val EXTRA_VOICE = "extra_voice_name"
        const val EXTRA_INITIAL_PROMPT = "extra_initial_prompt"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_PREVIEW_DURATION_MS = "extra_preview_duration_ms"
        const val EXTRA_IDENTITY_ID = "extra_digital_identity_id"
        private const val CHANNEL_ID = "juanita_voice_session"
        private const val NOTIFICATION_ID = 4101
        private const val CPU_WAKELOCK_TIMEOUT_MS = 15 * 60 * 1_000L
        private const val CPU_WAKELOCK_RENEW_INTERVAL_MS = 5 * 60 * 1_000L

        private val _visionState = MutableStateFlow("La visione è inattiva")
        val visionState: StateFlow<String> = _visionState.asStateFlow()

        private val _visualSearchResult = MutableStateFlow<VisualSearchResult?>(null)
        val visualSearchResult: StateFlow<VisualSearchResult?> = _visualSearchResult.asStateFlow()
        private val _visualSearchStatus = MutableStateFlow("")
        val visualSearchStatus: StateFlow<String> = _visualSearchStatus.asStateFlow()

        fun clearVisualSearchResult() {
            _visualSearchResult.value = null
            _visualSearchStatus.value = ""
        }

        private val _state = MutableStateFlow("Non connessa")
        val state: StateFlow<String> = _state.asStateFlow()
        private val _wakeWordEnabled = MutableStateFlow(false)
        val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()
        private val _wakeWordStatus = MutableStateFlow("Ascolto automatico disattivato")
        val wakeWordStatus: StateFlow<String> = _wakeWordStatus.asStateFlow()

        // True only after the service has confirmed microphone and Live-session teardown.
        private val _shutdownConfirmed = MutableStateFlow(true)
        // True when teardown is complete and stopSelf() has been requested. The bound
        // Activity must then finish/unbind; waiting for onDestroy here would deadlock.
        private val _stopRequestCompleted = MutableStateFlow(true)
        // Diagnostic signal set only by onDestroy after the Activity releases its binding.
        private val _serviceStoppedConfirmed = MutableStateFlow(true)

        /**
         * Waits for confirmed microphone/Live teardown and the service's stopSelf hand-off.
         * The Activity then finishes, its Compose binding is released, and Android destroys
         * the now-idle service. Never wait for onDestroy while this Activity is still bound.
         */
        suspend fun stopAndAwait(context: Context, timeoutMillis: Long = 35_000L): Boolean {
            require(timeoutMillis > 0L)
            _shutdownConfirmed.value = false
            _stopRequestCompleted.value = false
            _serviceStoppedConfirmed.value = false
            return try {
                context.applicationContext.startService(
                    Intent(context, JuanitaVoiceService::class.java).setAction(ACTION_STOP)
                )
                withTimeoutOrNull(timeoutMillis) {
                    _shutdownConfirmed.first { it }
                    _stopRequestCompleted.first { it }
                } == true
            } catch (error: Exception) {
                Log.e("JuanitaVoiceService", "Unable to confirm complete app voice-service shutdown", error)
                false
            }
        }

        private val _memoryState = MutableStateFlow("Memoria automatica pronta.")
        val memoryState: StateFlow<String> = _memoryState.asStateFlow()

        fun start(context: Context, instruction: String, voiceName: String, initialPrompt: String = "",
                  alarmId: String? = null, previewDurationMillis: Long = 0L, identityId: String? = null,
                  backgroundContinuationAuthorized: Boolean = false) {
            val intent = Intent(context, JuanitaVoiceService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_INSTRUCTION, instruction)
                putExtra(EXTRA_VOICE, voiceName)
                putExtra(EXTRA_INITIAL_PROMPT, initialPrompt)
                putExtra(EXTRA_BACKGROUND_CONTINUATION_AUTHORIZED, backgroundContinuationAuthorized)
                if (alarmId != null) putExtra(EXTRA_ALARM_ID, alarmId)
                if (previewDurationMillis > 0L) putExtra(EXTRA_PREVIEW_DURATION_MS, previewDurationMillis)
                if (identityId != null) putExtra(EXTRA_IDENTITY_ID, identityId)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun enableWakeWord(context: Context, instruction: String, voiceName: String, identityId: String?) {
            val intent = Intent(context, JuanitaVoiceService::class.java).apply {
                action = ACTION_ENABLE_WAKE
                putExtra(EXTRA_INSTRUCTION, instruction)
                putExtra(EXTRA_VOICE, voiceName)
                if (identityId != null) putExtra(EXTRA_IDENTITY_ID, identityId)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun disableWakeWord(context: Context) {
            val intent = Intent(context, JuanitaVoiceService::class.java).setAction(ACTION_DISABLE_WAKE)
            runCatching { context.startService(intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JuanitaVoiceService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        /** Stop only the Live session started by this alarm; never interrupt an unrelated call. */
        fun stopForAlarm(context: Context, alarmId: String) {
            if (alarmId.isBlank()) return
            val intent = Intent(context, JuanitaVoiceService::class.java)
                .setAction(ACTION_STOP_ALARM)
                .putExtra(EXTRA_ALARM_ID, alarmId)
            runCatching { context.startService(intent) }
        }
    }
}
