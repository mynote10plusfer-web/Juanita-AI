package ai.juanita.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ai.juanita.app.data.AssistantPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Plays one local alarm tone for all currently ringing alarm occurrences. */
class JuanitaAlarmRingingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeAlarmIds = linkedSetOf<String>()
    private var player: MediaPlayer? = null
    private var volumeJob: Job? = null
    private var autoStopJob: Job? = null
    private var autoStopAlarmId: String? = null
    private var ringCountAlarmId: String? = null
    private var remainingRingCycles: Int? = null
    private var activeAlarmId: String = JuanitaAlarmStore.LEGACY_ID
    private var volumeNotice: String = "Controllo volume sveglia…"
    private var testMode: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val alarmId = intent.getStringExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID)
                if (alarmId.isNullOrBlank()) {
                    autoStopJob?.cancel()
                    autoStopJob = null
                    autoStopAlarmId = null
                    ringCountAlarmId = null
                    remainingRingCycles = null
                    activeAlarmIds.clear()
                } else {
                    activeAlarmIds.remove(alarmId)
                    if (autoStopAlarmId == alarmId) {
                        autoStopJob?.cancel()
                        autoStopJob = null
                        autoStopAlarmId = null
                    }
                    if (ringCountAlarmId == alarmId) {
                        ringCountAlarmId = null
                        remainingRingCycles = null
                    }
                    if (activeAlarmIds.isEmpty()) {
                        autoStopJob?.cancel()
                        autoStopJob = null
                    }
                }
                persistActiveAlarmIds()
                if (activeAlarmIds.isNotEmpty()) {
                    activeAlarmId = activeAlarmIds.first()
                    testMode = activeAlarmId == JuanitaAlarmStore.LEGACY_ID &&
                        activeAlarmIds.size == 1 && testMode
                    getSystemService(NotificationManager::class.java)
                        .notify(JuanitaAlarmScheduler.NOTIFICATION_ID, buildNotification())
                } else {
                    stopRinging()
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
            ACTION_STOP_AFTER -> {
                val alarmId = intent.getStringExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID)
                    ?: return START_NOT_STICKY
                if (alarmId !in activeAlarmIds) return START_NOT_STICKY
                val seconds = intent.getIntExtra(EXTRA_STOP_AFTER_SECONDS, 0)
                val rings = intent.getIntExtra(EXTRA_STOP_AFTER_RINGS, 0)
                autoStopJob?.cancel()
                autoStopJob = null
                autoStopAlarmId = alarmId.takeIf { seconds > 0 }
                ringCountAlarmId = alarmId.takeIf { rings > 0 }
                remainingRingCycles = rings.takeIf { it > 0 }
                if (seconds > 0) {
                    autoStopJob = scope.launch {
                        delay(seconds * 1_000L)
                        stop(this@JuanitaAlarmRingingService, alarmId)
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val alarmId = intent.getStringExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID)
                    ?: JuanitaAlarmStore.LEGACY_ID
                activeAlarmIds.add(alarmId)
                persistActiveAlarmIds()
                activeAlarmId = alarmId
                testMode = intent.getBooleanExtra(EXTRA_TEST_MODE, false)
                if (activeAlarmIds.size == 1) volumeNotice = AlarmVolumeController.ensureAudible(this)
                startForeground(JuanitaAlarmScheduler.NOTIFICATION_ID, buildNotification())
                if (player == null) startAlarmTone()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAlarmTone() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: run {
                AlarmVolumeController.restore(this)
                volumeNotice = "Nessun tono di allarme disponibile."
                getSystemService(NotificationManager::class.java)
                    .notify(JuanitaAlarmScheduler.NOTIFICATION_ID, buildNotification())
                return
            }
        runCatching {
            val media = MediaPlayer()
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            media.setDataSource(this, uri)
            media.isLooping = false
            media.setVolume(0.05f, 0.05f)
            media.setOnCompletionListener { completed ->
                if (player !== completed || activeAlarmIds.isEmpty()) return@setOnCompletionListener
                val targetId = ringCountAlarmId
                val remaining = remainingRingCycles
                if (targetId != null && remaining != null && targetId in activeAlarmIds) {
                    if (remaining <= 1) {
                        ringCountAlarmId = null
                        remainingRingCycles = null
                        completed.seekTo(0)
                        completed.start()
                        stop(this@JuanitaAlarmRingingService, targetId)
                        return@setOnCompletionListener
                    }
                    remainingRingCycles = remaining - 1
                }
                runCatching {
                    completed.seekTo(0)
                    completed.start()
                }.onFailure {
                    stopRinging()
                    stopSelf()
                }
            }
            media.setOnPreparedListener {
                if (player !== it || activeAlarmIds.isEmpty()) {
                    runCatching { it.release() }
                    return@setOnPreparedListener
                }
                it.start()
                volumeJob?.cancel()
                volumeJob = scope.launch {
                    for (step in 1..24) {
                        if (!isActive) break
                        val volume = 0.05f + (0.95f * step / 24f)
                        runCatching { it.setVolume(volume, volume) }
                        delay(500L)
                    }
                }
            }
            media.setOnErrorListener { _, _, _ ->
                stopRinging()
                stopSelf()
                true
            }
            player = media
            media.prepareAsync()
        }.onFailure {
            stopRinging()
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        createChannel()
        val fullScreen = PendingIntent.getActivity(
            this, 5205, Intent(this, AlarmRingingActivity::class.java)
                .putExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID, activeAlarmId)
                .putExtra(EXTRA_TEST_MODE, testMode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getBroadcast(
            this, actionRequestCode(4),
            Intent(this, JuanitaAlarmReceiver::class.java)
                .setAction(JuanitaAlarmScheduler.ACTION_DISMISS)
                .putExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID, activeAlarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = PendingIntent.getBroadcast(
            this, actionRequestCode(5),
            Intent(this, JuanitaAlarmReceiver::class.java)
                .setAction(JuanitaAlarmScheduler.ACTION_SNOOZE)
                .putExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID, activeAlarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (testMode) "Prova della sveglia" else
            JuanitaAlarmStore(this).find(activeAlarmId)?.label?.takeIf { it.isNotBlank() }
                ?: "È ora di svegliarsi."
        val contentText = if (activeAlarmIds.size > 1) {
            "${activeAlarmIds.size} sveglie attive · $volumeNotice"
        } else "$title · $volumeNotice"
        val assistantName = AssistantPreferences(this).assistantName
            .ifBlank { AssistantPreferences.DEFAULT_ASSISTANT_NAME }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (testMode) "Prova · $assistantName" else "Sveglia · $assistantName")
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
        if (!testMode) builder.addAction(android.R.drawable.ic_lock_idle_alarm, "Posticipa 10 min", snooze)
        return builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, if (testMode) "Chiudi prova" else "Spegni", dismiss)
            .build()
    }

    private fun persistActiveAlarmIds() {
        getSharedPreferences(ACTIVE_ALARMS_PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(ACTIVE_ALARMS_KEY, activeAlarmIds.toSet()).apply()
    }

    private fun actionRequestCode(suffix: Int): Int =
        5200 + ((activeAlarmId.hashCode() and 0x0fffffff) * 3) + suffix

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Sveglia Juanita", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Suono della sveglia locale di Juanita"
                        setSound(null, null)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                )
            }
        }
    }

    private fun stopRinging() {
        volumeJob?.cancel()
        volumeJob = null
        autoStopJob?.cancel()
        autoStopJob = null
        autoStopAlarmId = null
        ringCountAlarmId = null
        remainingRingCycles = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        activeAlarmIds.clear()
        persistActiveAlarmIds()
        AlarmVolumeController.restore(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(JuanitaAlarmScheduler.NOTIFICATION_ID)
    }

    override fun onDestroy() {
        stopRinging()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "ai.juanita.app.alarm.RING"
        const val ACTION_STOP = "ai.juanita.app.alarm.STOP_RING"
        const val ACTION_STOP_AFTER = "ai.juanita.app.alarm.STOP_AFTER"
        const val EXTRA_STOP_AFTER_SECONDS = "stop_after_seconds"
        const val EXTRA_STOP_AFTER_RINGS = "stop_after_rings"
        const val EXTRA_TEST_MODE = "alarm_test_mode"
        private const val CHANNEL_ID = "juanita_alarm_ringing"
        private const val ACTIVE_ALARMS_PREFS = "juanita_alarm_runtime"
        private const val ACTIVE_ALARMS_KEY = "currently_ringing_ids"
        fun activeAlarmIds(context: Context): Set<String> =
            context.getSharedPreferences(ACTIVE_ALARMS_PREFS, Context.MODE_PRIVATE)
                .getStringSet(ACTIVE_ALARMS_KEY, emptySet()).orEmpty().toSet()

        fun start(context: Context, alarmId: String = JuanitaAlarmStore.LEGACY_ID, testMode: Boolean = false) {
            val intent = Intent(context, JuanitaAlarmRingingService::class.java)
                .setAction(ACTION_START)
                .putExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_TEST_MODE, testMode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
        fun startTest(context: Context) = start(context, JuanitaAlarmStore.LEGACY_ID, testMode = true)
        fun stopAfter(context: Context, alarmId: String, seconds: Int = 0, rings: Int = 0) {
            val intent = Intent(context, JuanitaAlarmRingingService::class.java)
                .setAction(ACTION_STOP_AFTER)
                .putExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_STOP_AFTER_SECONDS, seconds)
                .putExtra(EXTRA_STOP_AFTER_RINGS, rings)
            runCatching { context.startService(intent) }
        }
        fun stop(context: Context, alarmId: String? = null) {
            val intent = Intent(context, JuanitaAlarmRingingService::class.java).setAction(ACTION_STOP)
            if (alarmId != null) intent.putExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID, alarmId)
            runCatching { context.startService(intent) }
        }
    }
}
