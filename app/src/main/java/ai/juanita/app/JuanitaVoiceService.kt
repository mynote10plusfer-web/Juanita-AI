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

/** Owns Gemini Live independently from the Activity; starts only after user consent. */
class JuanitaVoiceService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val assistant = GeminiLiveAssistant()
    private lateinit var memoryStore: EncryptedMemoryStore
    private lateinit var automaticMemoryEngine: AutomaticMemoryEngine
    private var startRequested = false
    private var sessionJob: Job? = null
    private var stopJob: Job? = null

    inner class LocalBinder : Binder() {
        fun sendVideoFrame(bitmap: Bitmap) {
            serviceScope.launch {
                runCatching { assistant.sendVideoFrame(bitmap) }
                    .onFailure { _visionState.value = "Invio immagine non riuscito: ${it.localizedMessage ?: "errore"}" }
            }
        }
    }

    private val localBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        memoryStore = EncryptedMemoryStore(applicationContext)
        automaticMemoryEngine = AutomaticMemoryEngine(memoryStore)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                requestStop(startId)
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
                            val sessionInstruction = if (localContext.isBlank()) instruction else {
                                "$instruction\n\n$localContext"
                            }
                            assistant.start(sessionInstruction, voice) { role, text ->
                                serviceScope.launch(Dispatchers.IO) {
                                    // Transcript archiving is opt-in; curated memory runs independently.
                                    memoryStore.appendConversationTurn(conversationId, role, text)
                                    automaticMemoryEngine.acceptTurn(role, text)
                                }
                            }
                            _state.value = "Juanita è in ascolto. Puoi spegnere lo schermo e continuare a parlare."
                            updateNotification("Conversazione vocale attiva")
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            _state.value = "Connessione non riuscita: ${error.localizedMessage ?: "controlla rete e App Check"}"
                            updateNotification("Connessione non riuscita · apri Juanita per riprovare")
                            runCatching { assistant.stop() }
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
    private fun requestStop(startId: Int?) {
        sessionJob?.cancel()
        if (stopJob?.isActive == true) return
        stopJob = serviceScope.launch {
            _state.value = "Chiusura della conversazione…"
            try {
                withTimeoutOrNull(5_000L) { assistant.stop() }
            } finally {
                // The SDK audio capture has already been stopped before this network work.
                withTimeoutOrNull(5_000L) {
                    withContext(Dispatchers.IO) {
                        runCatching { automaticMemoryEngine.flush() }
                    }
                }
                startRequested = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                _state.value = "Conversazione terminata"
                if (startId != null) stopSelf(startId) else stopSelf()
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping Juanita away from Recents is an explicit end-of-session action.
        requestStop(null)
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
