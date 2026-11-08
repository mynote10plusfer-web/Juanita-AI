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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Plays the device's selected alarm tone locally; no network or speech synthesis. */
class JuanitaAlarmRingingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var volumeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForeground(JuanitaAlarmScheduler.NOTIFICATION_ID, buildNotification())
                if (player == null) startAlarmTone()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAlarmTone() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        runCatching {
            val media = MediaPlayer()
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            media.setDataSource(this, uri)
            media.isLooping = true
            media.setVolume(0.05f, 0.05f)
            media.setOnPreparedListener {
                it.start()
                volumeJob?.cancel()
                volumeJob = scope.launch {
                    // Smooth 12-second fade without changing the user's system volume.
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
                true
            }
            media.prepareAsync()
            player = media
        }.onFailure {
            stopRinging()
        }
    }

    private fun buildNotification(): Notification {
        createChannel()
        val fullScreen = PendingIntent.getActivity(
            this, 5205, Intent(this, AlarmRingingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getBroadcast(
            this, 5204,
            Intent(this, JuanitaAlarmReceiver::class.java).setAction(JuanitaAlarmScheduler.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = PendingIntent.getBroadcast(
            this, 5206,
            Intent(this, JuanitaAlarmReceiver::class.java).setAction(JuanitaAlarmScheduler.ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Sveglia · Juanita")
            .setContentText("È ora di svegliarsi.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Posticipa 10 min", snooze)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Spegni", dismiss)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Sveglia Juanita", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Suono della sveglia locale di Juanita"
                        setSound(null, null) // MediaPlayer owns alarm audio and gradual volume.
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                )
            }
        }
    }

    private fun stopRinging() {
        volumeJob?.cancel()
        volumeJob = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
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
        private const val CHANNEL_ID = "juanita_alarm_ringing"
        fun start(context: Context) {
            val intent = Intent(context, JuanitaAlarmRingingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, JuanitaAlarmRingingService::class.java))
        }
    }
}
