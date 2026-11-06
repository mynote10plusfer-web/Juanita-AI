package ai.juanita.app

import android.app.Notification
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
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns Gemini Live independently from the Activity; starts only after user consent. */
class JuanitaVoiceService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val assistant = GeminiLiveAssistant()
    private lateinit var memoryStore: EncryptedMemoryStore
    private var startRequested = false

    override fun onCreate() {
        super.onCreate()
        memoryStore = EncryptedMemoryStore(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    _state.value = "Chiusura della conversazione…"
                    runCatching { assistant.stop() }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    _state.value = "Conversazione terminata"
                    stopSelf(startId)
                }
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
                if (!startRequested) {
                    startRequested = true
                    serviceScope.launch {
                        _state.value = "Connessione a Gemini Live…"
                        try {
                            val conversationId = UUID.randomUUID().toString()
                            assistant.start(instruction, voice) { role, text ->
                                // Storage is a no-op unless the user enabled archive consent.
                                serviceScope.launch(Dispatchers.IO) {
                                    memoryStore.appendConversationTurn(conversationId, role, text)
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
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
