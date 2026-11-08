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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import ai.juanita.app.ai.GeminiLiveAssistant
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
    private var sessionJob: Job? = null
    private var stopJob: Job? = null
    private var transcriptChannel: Channel<Pair<String, String>>? = null
    private var transcriptWorker: Job? = null
    private var memoryWorkChannel: Channel<Unit>? = null
    private var memoryWorker: Job? = null
    private val videoFrameInFlight = AtomicBoolean(false)

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
            ACTION_START -> {
                val instruction = intent.getStringExtra(EXTRA_INSTRUCTION).orEmpty()
                val voice = intent.getStringExtra(EXTRA_VOICE).orEmpty().ifBlank { GeminiLiveAssistant.DEFAULT_VOICE }
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
                    startRequested = true
                    sessionJob = serviceScope.launch {
                        _state.value = "Connessione a Gemini Live…"
                        try {
                            val conversationId = UUID.randomUUID().toString()
                            // Restore relevant local memories and recent turns before opening
                            // the new Live session. Nothing is uploaded to a separate memory store.
                            val localContext = withContext(Dispatchers.IO) {
                                memoryStore.buildAssistantContext()
                            }
                            val profileContext = withContext(Dispatchers.IO) {
                                buildPersonalProfileContext(personalProfileStore.load())
                            }
                            val sessionInstruction = buildString {
                                append(instruction)
                                if (profileContext.isNotBlank()) append("\n\n").append(profileContext)
                                if (localContext.isNotBlank()) append("\n\n").append(localContext)
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
                                    val sent = transcriptQueue.trySend(role to text)
                                    if (sent.isFailure && role.equals("USER", ignoreCase = true)) {
                                        _memoryState.value = "Coda trascrizioni piena: alcuni testi non sono stati acquisiti."
                                    }
                                },
                                onArchiveSearch = { query ->
                                    memoryStore.buildRelevantRecallContext(
                                        query,
                                        excludeSessionId = conversationId
                                    )
                                },
                                onProfileUpdate = { fields ->
                                    val saved = mutableListOf<String>()
                                    fields.forEach { (key, value) ->
                                        if (key !in PersonalProfile.allowedKeys) return@forEach
                                        if (key == PersonalProfile.BIRTH_DATE) {
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
                                    else "Saved explicitly stated profile fields: " + saved.joinToString(", ") + "."
                                }
                            )
                            _state.value = "Juanita è in ascolto. Puoi spegnere lo schermo e continuare a parlare."
                            updateNotification("Conversazione vocale attiva")
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            _state.value = "Connessione non riuscita: ${error.localizedMessage ?: "controlla rete e App Check"}"
                            updateNotification("Connessione non riuscita · apri Juanita per riprovare")
                            runCatching { assistant.stop() }
                            withTimeoutOrNull(3_000L) { drainTranscriptQueue() }
                            startRequested = false
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
    private fun requestStop() {
        sessionJob?.cancel()
        // Synchronously stop SDK audio capture before launching any asynchronous cleanup.
        assistant.stopAudioCapture()
        _state.value = "Microfono disattivato · chiusura sessione…"
        if (stopJob?.isActive == true) return
        stopJob = serviceScope.launch {
            try {
                withTimeoutOrNull(5_000L) { assistant.stop() }
                _state.value = "Microfono disattivato · salvataggio dei ricordi…"
                updateNotification("Microfono disattivato · salvataggio in corso")
            } finally {
                // Audio is stopped before draining callbacks or waiting on network work.
                withTimeoutOrNull(3_000L) { drainTranscriptQueue() }
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
                stopForeground(STOP_FOREGROUND_REMOVE)
                _state.value = "Conversazione terminata"
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
        // Stop capture synchronously even if Android is tearing down the service.
        assistant.stopAudioCapture()
        transcriptChannel?.close()
        transcriptWorker?.cancel()
        memoryWorkChannel?.close()
        memoryWorker?.cancel()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { assistant.stop() }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "ai.juanita.app.action.START_VOICE"
        const val ACTION_STOP = "ai.juanita.app.action.STOP_VOICE"
        const val EXTRA_INSTRUCTION = "extra_system_instruction"
        const val EXTRA_VOICE = "extra_voice_name"
        private const val CHANNEL_ID = "juanita_voice_session"
        private const val NOTIFICATION_ID = 4101

        private val _visionState = MutableStateFlow("La visione è inattiva")
        val visionState: StateFlow<String> = _visionState.asStateFlow()

        private val _state = MutableStateFlow("Non connessa")
        val state: StateFlow<String> = _state.asStateFlow()

        private val _memoryState = MutableStateFlow("Memoria automatica pronta.")
        val memoryState: StateFlow<String> = _memoryState.asStateFlow()

        fun start(context: Context, instruction: String, voiceName: String) {
            val intent = Intent(context, JuanitaVoiceService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_INSTRUCTION, instruction)
                putExtra(EXTRA_VOICE, voiceName)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, JuanitaVoiceService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
