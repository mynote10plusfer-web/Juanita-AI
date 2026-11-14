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
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
import ai.juanita.app.presence.VoiceSessionCommandMatcher
import ai.juanita.app.presence.ConversationalPresenceContext
import ai.juanita.app.presence.ConversationalPresencePolicy
import ai.juanita.app.presence.ConversationalPresenceSettings
import ai.juanita.app.presence.PresenceDecision
import java.time.LocalDate
import java.time.Period
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    // One dedicated worker serializes blocking SDK recorder teardown. A timed-out native
    // call is interrupted and the service remains fail-closed; no second recorder is opened.
    private val audioTeardownExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "JuanitaAudioTeardown").apply { isDaemon = true }
    }
    private val audioRouteController by lazy { VoiceAudioRouteController(applicationContext) }
    private lateinit var memoryStore: EncryptedMemoryStore
    private lateinit var automaticMemoryEngine: AutomaticMemoryEngine
    private lateinit var personalProfileStore: PersonalProfileStore
    private var startRequested = false
    /** Main-thread-confined lifecycle gate: one session can own audio resources at a time. */
    private var lifecycleState = VoiceLifecycleState.IDLE
    private var activeIdentityId: String? = null
    /** Non-null only when this Live session was launched by a specific alarm. */
    private var activeAlarmId: String? = null
    /** Consent is captured when the user explicitly starts this Live session. */
    private var backgroundContinuationAuthorized = false
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
    private val thermalStatusListener = PowerManager.OnThermalStatusChangedListener { status ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            status >= PowerManager.THERMAL_STATUS_SEVERE
        ) {
            serviceScope.launch {
                if (lifecycleState == VoiceLifecycleState.ACTIVE) {
                    Log.w("JuanitaVoiceService", "Severe device thermal status ($status); stopping Live audio to protect the phone")
                    requestStop(
                        preserveContinuitySnapshot = true,
                        finalMessage = "Sessione terminata per proteggere il telefono dal surriscaldamento"
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
        memoryStore = EncryptedMemoryStore.getInstance(applicationContext)
        automaticMemoryEngine = AutomaticMemoryEngine(memoryStore)
        personalProfileStore = PersonalProfileStore(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_BACKGROUND_CONSENT -> {
                backgroundContinuationAuthorized =
                    intent.getBooleanExtra(EXTRA_BACKGROUND_CONTINUATION_AUTHORIZED, false) && activeAlarmId == null
                // The consent switch may be changed while no voice session exists.
                // Do not leave an idle started Service behind just to persist a UI setting;
                // the preference is owned by MainActivity and is passed on the next start.
                if (!startRequested || lifecycleState == VoiceLifecycleState.IDLE) {
                    stopSelf(startId)
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
                if (!startRequested || lifecycleState == VoiceLifecycleState.IDLE) {
                    stopSelf(startId)
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
                                lifecycleState != VoiceLifecycleState.IDLE &&
                                lifecycleState != VoiceLifecycleState.STOPPING,
                            backgroundContinuationAuthorized = backgroundContinuationAuthorized,
                            userExplicitlyStartedSession = true,
                            microphonePermissionGranted =
                                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                                    android.content.pm.PackageManager.PERMISSION_GRANTED,
                            // A non-alarm session can be a selected character or the
                            // standard assistant; its validated instruction is already bound.
                            activeIdentityAvailable = lifecycleState != VoiceLifecycleState.IDLE
                        )
                    )
                    when (decision) {
                        PresenceDecision.AllowListening ->
                            updateNotification("Conversazione in background · consenso attivo")
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
            ACTION_STOP -> {
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
                } else if (!startRequested && stopJob?.isActive != true) {
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
                    acquireCpuWakeLock()
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
                            automaticMemoryEngine = AutomaticMemoryEngine(memoryStore)

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
                                memoryStore.buildAssistantContext(personaId = selectedIdentity?.id)
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
                            val audioFocusGranted = audioRouteController.begin {
                                requestStop(
                                    preserveContinuitySnapshot = true,
                                    finalMessage = "Sessione vocale interrotta: priorità audio passata a un'altra app."
                                )
                            }
                            check(audioFocusGranted) {
                                "Audio occupato da un'altra applicazione: microfono non avviato."
                            }
                            assistant.start(
                                systemInstruction = sessionInstruction,
                                voiceName = voice,
                                onTranscript = { role, text ->
                                    if (previewDurationMillis == 0L && role.equals("USER", ignoreCase = true)) {
                                        val activeName = selectedIdentity?.displayName?.takeIf { it.isNotBlank() } ?: "Juanita"
                                        when (VoiceSessionCommandMatcher.match(text, activeName)) {
                                            VoiceSessionCommandMatcher.Command.STOP_SESSION -> {
                                                pendingStartIntent = null
                                                pendingStartId = 0
                                                serviceScope.launch {
                                                    requestStop(
                                                        preserveContinuitySnapshot = true,
                                                        finalMessage = "Comando vocale ricevuto · conversazione terminata"
                                                    )
                                                }
                                            }
                                            VoiceSessionCommandMatcher.Command.CLOSE_APPLICATION -> {
                                                pendingStartIntent = null
                                                pendingStartId = 0
                                                closeApplicationAfterStop = true
                                                serviceScope.launch {
                                                    requestStop(
                                                        preserveContinuitySnapshot = true,
                                                        finalMessage = "Comando vocale ricevuto · applicazione chiusa"
                                                    )
                                                }
                                            }
                                            null -> Unit
                                        }
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
                                    memoryStore.buildRelevantRecallContext(query, personaId = selectedIdentity?.id)
                                },
                                onContinuityContext = { recentTranscript ->
                                    withContext(Dispatchers.IO) {
                                        memoryStore.buildContinuityContext(conversationId, recentTranscript, selectedIdentity?.id)
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
            assistant.stopAudioCapture()
        }

    private suspend fun stopAssistantWithDeadline(timeoutMillis: Long): Boolean =
        runAudioTeardownWithDeadline("Gemini Live session close", timeoutMillis) {
            runBlocking {
                assistant.stop()
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
        sessionJob?.cancel()
        voicePreviewJob?.cancel()
        if (stopJob?.isActive == true) {
            if (!preserveContinuitySnapshot) {
                serviceScope.launch(Dispatchers.IO) {
                    runCatching { memoryStore.clearContinuitySnapshots() }
                }
            }
            return
        }
        lifecycleState = VoiceLifecycleState.STOPPING
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
                    _state.value = "Microfono disattivato · salvataggio dei ricordi…"
                    updateNotification("Microfono disattivato · salvataggio in corso")
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
                if (!preserveContinuitySnapshot) {
                    runCatching {
                        withContext(Dispatchers.IO) { memoryStore.clearContinuitySnapshots() }
                    }.onFailure { Log.w("JuanitaVoiceService", "Continuity cleanup failed", it) }
                }
                activeConversationId = null
                runCatching {
                    withTimeoutOrNull(5_000L) { drainMemoryWorker() }
                }.onFailure { Log.w("JuanitaVoiceService", "Memory worker drain failed after audio shutdown", it) }
                val flushedCount = runCatching {
                    withTimeoutOrNull(5_000L) {
                        withContext(Dispatchers.IO) { automaticMemoryEngine.flush() }
                    }
                }.onFailure { Log.w("JuanitaVoiceService", "Final automatic-memory flush failed", it) }.getOrNull()
                if (flushedCount != null && flushedCount > 0) {
                    _memoryState.value = "Memoria aggiornata: $flushedCount ricordi salvati o confermati."
                } else if (flushedCount == -1) {
                    _memoryState.value = "Memoria in attesa: rete o quota non disponibili; riproverò al prossimo collegamento."
                } else if (flushedCount != null) {
                    _memoryState.value = "Controllo completato: nessun nuovo ricordo durevole da aggiungere."
                } else {
                    _memoryState.value = "Memoria in attesa: riprenderà al prossimo collegamento."
                }
                startRequested = false
                lifecycleState = VoiceLifecycleState.IDLE
                activeIdentityId = null
                activeAlarmId = null
                if (assistantStopped) releaseCpuWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
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
                    stopSelf()
                }
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
        // Removing the app from Recents is an explicit app-close gesture. Stop the
        // foreground voice session through the same awaited teardown path; do not touch
        // AlarmManager schedules or the encrypted shared memory store.
        if (lifecycleState != VoiceLifecycleState.IDLE || startRequested || stopJob?.isActive == true) {
            pendingStartIntent = null
            pendingStartId = 0
            requestStop(preserveContinuitySnapshot = true, finalMessage = "App chiusa · sessione vocale terminata")
        } else {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Android reports device-wide thermal pressure. Stop only the active voice session at
     * SEVERE or above; lower levels are advisory and must not interrupt ordinary use.
     */
    private fun startThermalMonitoring() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || thermalListenerRegistered) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        runCatching {
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
        val openIntent = Intent(this, MainActivity::class.java)
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
        // Normal destruction is reached only after requestStop() has awaited SDK
        // microphone and Live-session shutdown. Never launch detached cleanup here:
        // Android could destroy this Service while that unowned coroutine is still running.
        if (lifecycleState != VoiceLifecycleState.IDLE && lifecycleState != VoiceLifecycleState.ERROR) {
            Log.e("JuanitaVoiceService", "Service destroyed before orderly voice shutdown completed: $lifecycleState")
        }
        sessionJob?.cancel()
        voicePreviewJob?.cancel()
        activeAlarmId = null
        activeIdentityId = null
        pendingStartIntent = null
        pendingStartId = 0
        stopThermalMonitoring()
        releaseCpuWakeLock()
        transcriptChannel?.close()
        transcriptWorker?.cancel()
        memoryWorkChannel?.close()
        memoryWorker?.cancel()
        runCatching { audioRouteController.end() }
        audioTeardownExecutor.shutdownNow()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "ai.juanita.app.action.START_VOICE"
        const val ACTION_STOP = "ai.juanita.app.action.STOP_VOICE"
        const val ACTION_STOP_ALARM = "ai.juanita.app.action.STOP_ALARM_VOICE"
        const val ACTION_ACTIVITY_BACKGROUND = "ai.juanita.app.action.ACTIVITY_BACKGROUND"
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

        private val _state = MutableStateFlow("Non connessa")
        val state: StateFlow<String> = _state.asStateFlow()

        // True only after the service has confirmed microphone and Live-session teardown.
        private val _shutdownConfirmed = MutableStateFlow(true)

        /**
         * Requests an orderly stop and waits for the service's explicit teardown
         * acknowledgement. The caller must not destroy its Activity before this returns true.
         */
        suspend fun stopAndAwait(context: Context, timeoutMillis: Long = 20_000L): Boolean {
            require(timeoutMillis > 0L)
            _shutdownConfirmed.value = false
            return try {
                context.applicationContext.startService(
                    Intent(context, JuanitaVoiceService::class.java).setAction(ACTION_STOP)
                )
                withTimeoutOrNull(timeoutMillis) { _shutdownConfirmed.first { it } } == true
            } catch (error: Exception) {
                Log.e("JuanitaVoiceService", "Unable to request confirmed voice shutdown", error)
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
