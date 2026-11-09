package ai.juanita.app

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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import ai.juanita.app.ai.GeminiLiveAssistant
import ai.juanita.app.alarm.JuanitaAlarmRingingService
import ai.juanita.app.memory.EncryptedMemoryStore
import ai.juanita.app.memory.AutomaticMemoryEngine
import ai.juanita.app.profile.PersonalProfile
import ai.juanita.app.profile.PersonalProfileStore
import java.time.LocalDate
import java.time.Period
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean

/** Owns Gemini Live independently from the Activity; starts only after user consent. */
class JuanitaVoiceService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val assistant = GeminiLiveAssistant()
    private lateinit var memoryStore: EncryptedMemoryStore
    private lateinit var automaticMemoryEngine: AutomaticMemoryEngine
    private lateinit var personalProfileStore: PersonalProfileStore
    private var startRequested = false
    /** Non-null only when this Live session was launched by a specific alarm. */
    private var activeAlarmId: String? = null
    private var sessionJob: Job? = null
    private var stopJob: Job? = null
    private var voicePreviewJob: Job? = null
    private var transcriptChannel: Channel<Pair<String, String>>? = null
    private var transcriptWorker: Job? = null
    @Volatile private var activeConversationId: String? = null
    private var memoryWorkChannel: Channel<Unit>? = null
    private var memoryWorker: Job? = null
    private val videoFrameInFlight = AtomicBoolean(false)
    @Volatile private var cpuWakeLock: PowerManager.WakeLock? = null

    inner class LocalBinder : Binder() {
        fun sendVideoFrame(bitmap: Bitmap) {
            if (!videoFrameInFlight.compareAndSet(false, true)) return
            serviceScope.launch {
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
        memoryStore = EncryptedMemoryStore(applicationContext)
        automaticMemoryEngine = AutomaticMemoryEngine(memoryStore)
        personalProfileStore = PersonalProfileStore(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
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
                if (instruction.isBlank()) {
                    _state.value = "Impossibile avviare: istruzioni mancanti"
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification("Connessione a Gemini Live…"),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
                )
                if (stopJob?.isActive == true) {
                    _state.value = "Attendi la chiusura della conversazione precedente"
                    return START_NOT_STICKY
                }
                if (!startRequested) {
                    activeAlarmId = alarmId
                    acquireCpuWakeLock()
                    startRequested = true
                    sessionJob = serviceScope.launch {
                        _state.value = "Connessione a Gemini Live…"
                        try {
                            val conversationId = UUID.randomUUID().toString()
                            activeConversationId = conversationId
                            // Carry the most recent interrupted conversation forward before
                            // constructing the next session. This snapshot is separate from the
                            // optional transcript archive and survives an unexpected disconnect.
                            val recoveredContinuityContext = withContext(Dispatchers.IO) {
                                runCatching {
                                    memoryStore.carryForwardLatestContinuitySnapshot(conversationId)
                                }.getOrDefault("")
                            }
                            // Restore relevant local memories and recent turns before opening
                            // the new Live session. Nothing is uploaded to a separate memory store.
                            val localContext = withContext(Dispatchers.IO) {
                                memoryStore.buildAssistantContext()
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
                                        memoryStore.appendConversationTurn(conversationId, role, combinedText)
                                    } catch (archiveFailure: Exception) {
                                        _memoryState.value = "Archivio conversazione non riuscito: ${archiveFailure.localizedMessage ?: archiveFailure.javaClass.simpleName}"
                                    }
                                    try {
                                        // Session-only continuity persists even when full transcript archiving is paused.
                                        memoryStore.appendContinuityTurn(conversationId, role, combinedText)
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
                            assistant.start(
                                systemInstruction = sessionInstruction,
                                voiceName = voice,
                                onTranscript = { role, text ->
                                    if (initialPrompt.isNotBlank() && alarmId != null && role.equals("ASSISTANT", ignoreCase = true)) {
                                        // Keep the local alarm sounding until Gemini's first spoken response transcript.
                                        JuanitaAlarmRingingService.stop(this@JuanitaVoiceService, alarmId)
                                    }
                                    if (previewDurationMillis == 0L) {
                                        val sent = transcriptQueue.trySend(role to text)
                                        if (sent.isFailure && role.equals("USER", ignoreCase = true)) {
                                            _memoryState.value = "Coda trascrizioni piena: alcuni testi non sono stati acquisiti."
                                        }
                                    }
                                },
                                onArchiveSearch = { query ->
                                    memoryStore.buildRelevantRecallContext(
                                        query,
                                        excludeSessionId = conversationId
                                    )
                                },
                                onContinuityContext = { recentTranscript ->
                                    withContext(Dispatchers.IO) {
                                        memoryStore.buildContinuityContext(conversationId, recentTranscript)
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
                            _state.value = "Connessione non riuscita: ${error.localizedMessage ?: "controlla rete e App Check"}"
                            updateNotification("Connessione non riuscita · apri Juanita per riprovare")
                            runCatching { assistant.stop() }
                            withTimeoutOrNull(3_000L) { drainTranscriptQueue() }
                            startRequested = false
                            releaseCpuWakeLock()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf(startId)
                        }
                    }
                }
            }
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
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
        _state.value = "Microfono disattivato · chiusura sessione…"
        stopJob = serviceScope.launch {
            try {
                // AudioRecord.release() can block on some Android audio stacks. Never run
                // SDK audio teardown on the service/UI main thread.
                withContext(Dispatchers.IO) { assistant.stopAudioCapture() }
                releaseCpuWakeLock()
                withTimeoutOrNull(5_000L) { assistant.stop() }
                _state.value = "Microfono disattivato · salvataggio dei ricordi…"
                updateNotification("Microfono disattivato · salvataggio in corso")
            } finally {
                // Audio is stopped before draining callbacks or waiting on network work.
                withTimeoutOrNull(3_000L) { drainTranscriptQueue() }
                if (!preserveContinuitySnapshot) {
                    withContext(Dispatchers.IO) {
                        runCatching { memoryStore.clearContinuitySnapshots() }
                    }
                }
                activeConversationId = null
                withTimeoutOrNull(5_000L) { drainMemoryWorker() }
                val flushedCount = withTimeoutOrNull(5_000L) {
                    withContext(Dispatchers.IO) { automaticMemoryEngine.flush() }
                }
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
                activeAlarmId = null
                releaseCpuWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                _state.value = finalMessage ?: "Conversazione terminata"
                stopSelf()
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
        // Swiping Juanita away from Recents is an explicit end-of-session action.
        requestStop()
        super.onTaskRemoved(rootIntent)
    }

    /** Keep the CPU awake for the lifetime of an explicitly started Live audio session.
     * This is required on devices that suspend background CPU work when the display turns off.
     */
    @Suppress("WakelockTimeout")
    private fun acquireCpuWakeLock() {
        if (cpuWakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        cpuWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:JuanitaLiveAudio").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseCpuWakeLock() {
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
        sessionJob?.cancel()
        voicePreviewJob?.cancel()
        activeAlarmId = null
        releaseCpuWakeLock()
        transcriptChannel?.close()
        transcriptWorker?.cancel()
        memoryWorkChannel?.close()
        memoryWorker?.cancel()
        // Keep potentially blocking SDK audio teardown off Android's main thread.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { assistant.stopAudioCapture() }
            runCatching { assistant.stop() }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "ai.juanita.app.action.START_VOICE"
        const val ACTION_STOP = "ai.juanita.app.action.STOP_VOICE"
        const val ACTION_STOP_ALARM = "ai.juanita.app.action.STOP_ALARM_VOICE"
        const val EXTRA_INSTRUCTION = "extra_system_instruction"
        const val EXTRA_VOICE = "extra_voice_name"
        const val EXTRA_INITIAL_PROMPT = "extra_initial_prompt"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_PREVIEW_DURATION_MS = "extra_preview_duration_ms"
        private const val CHANNEL_ID = "juanita_voice_session"
        private const val NOTIFICATION_ID = 4101

        private val _visionState = MutableStateFlow("La visione è inattiva")
        val visionState: StateFlow<String> = _visionState.asStateFlow()

        private val _state = MutableStateFlow("Non connessa")
        val state: StateFlow<String> = _state.asStateFlow()

        private val _memoryState = MutableStateFlow("Memoria automatica pronta.")
        val memoryState: StateFlow<String> = _memoryState.asStateFlow()

        fun start(context: Context, instruction: String, voiceName: String, initialPrompt: String = "",
                  alarmId: String? = null, previewDurationMillis: Long = 0L) {
            val intent = Intent(context, JuanitaVoiceService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_INSTRUCTION, instruction)
                putExtra(EXTRA_VOICE, voiceName)
                putExtra(EXTRA_INITIAL_PROMPT, initialPrompt)
                if (alarmId != null) putExtra(EXTRA_ALARM_ID, alarmId)
                if (previewDurationMillis > 0L) putExtra(EXTRA_PREVIEW_DURATION_MS, previewDurationMillis)
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
