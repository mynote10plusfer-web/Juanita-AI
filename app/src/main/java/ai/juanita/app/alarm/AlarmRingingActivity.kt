package ai.juanita.app.alarm

import android.Manifest
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ai.juanita.app.JuanitaVoiceService
import ai.juanita.app.data.AssistantPreferences
import ai.juanita.app.identity.DigitalIdentityStore
import ai.juanita.app.identity.toSystemInstruction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lock-screen alarm controls and optional Gemini Live morning greeting. */
class AlarmRingingActivity : ComponentActivity() {
    private lateinit var root: LinearLayout
    private lateinit var alarmId: String
    private var voiceStarted = false
    private var testMode = false
    private var snoozeButton: Button? = null
    private var retryButton: Button? = null
    private var statusText: TextView? = null
    private var closeReceiverRegistered = false
    private val alarmClosedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getStringExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID) == alarmId) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alarmId = intent.getStringExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID)
            ?: JuanitaAlarmStore.LEGACY_ID
        testMode = intent.getBooleanExtra(JuanitaAlarmRingingService.EXTRA_TEST_MODE, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 48, 32, 48)
            setBackgroundColor(Color.rgb(10, 11, 13))
        }
        root.addView(TextView(this).apply {
            text = AssistantPreferences(this@AlarmRingingActivity).assistantName.uppercase()
            textSize = 15f
            setTextColor(Color.rgb(228, 189, 105))
            gravity = Gravity.CENTER
        })
        val alarm = JuanitaAlarmStore(this).find(alarmId)
        root.addView(TextView(this).apply {
            text = if (testMode) "Prova della sveglia" else alarm?.label?.takeIf { it.isNotBlank() } ?: "È ora di svegliarsi"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 32)
        })
        statusText = TextView(this).apply {
            text = "La sveglia è attiva."
            textSize = 14f
            setTextColor(Color.rgb(228, 189, 105))
            gravity = Gravity.CENTER
        }
        root.addView(statusText)
        snoozeButton = makeButton("POSTICIPA · 10 MINUTI") {
            sendAlarmAction(JuanitaAlarmScheduler.ACTION_SNOOZE)
        }
        root.addView(snoozeButton)
        root.addView(makeButton(if (testMode) "CHIUDI PROVA" else "SPEGNI LA SVEGLIA") {
            sendAlarmAction(JuanitaAlarmScheduler.ACTION_DISMISS)
        })
        root.addView(TextView(this).apply {
            text = "Puoi posticipare questa sveglia di dieci minuti oppure spegnerla. Le altre sveglie restano programmate."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        })
        setContentView(root)

        if (testMode) {
            snoozeButton?.visibility = android.view.View.GONE
            statusText?.text = "Prova audio e schermata: nessun buongiorno vocale verrà avviato."
        } else {
            observeVoiceStatus()
        }
        val alarmPrefs = getSharedPreferences("juanita_alarm", MODE_PRIVATE)
        val voiceEnabled = alarmPrefs.getBoolean("morning_voice_enabled", true)
        val microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!testMode && voiceEnabled && microphoneGranted) {
            statusText?.text = "Buongiorno in preparazione…"
            // The local alarm tone remains active during this delay and while Live connects.
            lifecycleScope.launch {
                delay(5_000L)
                if (!isFinishing && !isDestroyed) startMorningVoice()
            }
        } else if (!testMode && voiceEnabled) {
            statusText?.text = "Per il buongiorno vocale serve il permesso microfono. La sveglia sonora continua."
            addRetryButton("Apri ${AssistantPreferences(this).assistantName}") {
                startActivity(Intent(this, ai.juanita.app.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
        }
    }

    private fun observeVoiceStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                JuanitaVoiceService.state.collect { state ->
                    statusText?.text = when {
                        state.contains("in ascolto", ignoreCase = true) -> "Buongiorno, sono qui. Ti ascolto."
                        state.contains("non riuscita", ignoreCase = true) ||
                            state.contains("impossibile avviare", ignoreCase = true) -> {
                            voiceStarted = false
                            addRetryButton("Avvia buongiorno") { startMorningVoice() }
                            "La voce non è riuscita ad avviarsi: ${state.take(180)}. La sveglia sonora resta attiva."
                        }
                        state.contains("Connessione a Gemini", ignoreCase = true) -> "Mi sto collegando per parlarti…"
                        else -> statusText?.text ?: state
                    }
                }
            }
        }
    }

    private fun startMorningVoice() {
        if (voiceStarted) return
        val microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!microphoneGranted) {
            statusText?.text = "Microfono non autorizzato: la sveglia sonora resta attiva."
            addRetryButton("Apri ${AssistantPreferences(this).assistantName}") {
                startActivity(Intent(this, ai.juanita.app.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
            return
        }
        voiceStarted = true
        statusText?.text = "Mi collego alla voce Gemini Live…"
        lifecycleScope.launch {
            val preferences = AssistantPreferences(this@AlarmRingingActivity)
            val selectedIdentity = withContext(Dispatchers.IO) {
                preferences.activeDigitalIdentityId?.let { identityId ->
                    runCatching { DigitalIdentityStore(applicationContext).get(identityId) }.getOrNull()
                        ?.takeUnless { it.archived }
                }
            }
            val languageTag = selectedIdentity?.preferredLanguageTag ?: preferences.languageTag
            val selectedVoice = selectedIdentity?.voiceName ?: preferences.liveVoiceName
            val assistantName = selectedIdentity?.displayName
                ?: preferences.assistantName.ifBlank { AssistantPreferences.DEFAULT_ASSISTANT_NAME }
            val briefing = withContext(Dispatchers.IO) {
                MorningBriefingContext.build(this@AlarmRingingActivity, languageTag)
            }
            val identityGuidance = selectedIdentity?.toSystemInstruction() ?: """
                ASSISTANT IDENTITY: You are $assistantName, a personal voice assistant.
                Use the configured response language tag: $languageTag. Keep grammatical self-reference
                consistent with the configured assistant identity.
            """.trimIndent()
            val instruction = """
                $identityGuidance
                This is the user's explicitly enabled morning alarm greeting.
                Use the selected Gemini Live voice only; never use Android TTS or a fallback voice.
                Speak in the configured response language ($languageTag) throughout the greeting.
                Keep the tone warm, natural, positive and reassuring.
                Briefly say good morning and the local day/date/time, then read only the morning
                briefing sections explicitly marked as requested in the supplied context. Include
                horoscope and news only when their verified content is present. If a selected source
                is unavailable, say so briefly; never invent weather, headlines or appointments.
                After the short briefing, remain available for conversation.
            """.trimIndent()
            runCatching {
                JuanitaVoiceService.start(
                    this@AlarmRingingActivity,
                    instruction,
                    selectedVoice,
                    briefing,
                    alarmId,
                    identityId = selectedIdentity?.id
                )
            }.onFailure { error ->
                voiceStarted = false
                statusText?.text = "Non riesco ad avviare la voce: ${error.localizedMessage ?: error.javaClass.simpleName}. Il suono continua."
                addRetryButton("Riprova buongiorno") { startMorningVoice() }
            }
        }
    }

    private fun addRetryButton(label: String, action: () -> Unit) {
        val existing = retryButton
        if (existing != null) {
            existing.text = label
            existing.setOnClickListener { action() }
            return
        }
        retryButton = makeButton(label, action)
        root.addView(retryButton)
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        setTextColor(Color.BLACK)
        setBackgroundColor(Color.rgb(228, 189, 105))
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 }
    }

    override fun onStart() {
        super.onStart()
        // A stale full-screen PendingIntent must never wake the display after dismissal.
        if (alarmId !in JuanitaAlarmRingingService.activeAlarmIds(this)) {
            clearAlarmWindowOverrides()
            finish()
            return
        }
        if (!closeReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                alarmClosedReceiver,
                IntentFilter(ACTION_ALARM_CLOSED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            closeReceiverRegistered = true
        }
        applyAlarmWindowOverrides()
    }

    override fun onResume() {
        super.onResume()
        if (alarmId in JuanitaAlarmRingingService.activeAlarmIds(this)) {
            applyAlarmWindowOverrides()
        } else {
            clearAlarmWindowOverrides()
        }
    }

    override fun onPause() {
        // Release lock-screen / turn-screen-on overrides as soon as Android takes
        // this activity out of the foreground, so they cannot cover keyguard unlock.
        clearAlarmWindowOverrides()
        super.onPause()
    }

    private fun applyAlarmWindowOverrides() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        clearAlarmWindowOverrides()
        if (closeReceiverRegistered) {
            runCatching { unregisterReceiver(alarmClosedReceiver) }
            closeReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        clearAlarmWindowOverrides()
        super.onDestroy()
    }

    private fun clearAlarmWindowOverrides() {
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
    }

    private fun sendAlarmAction(action: String) {
        val effectiveAction = if (testMode) JuanitaAlarmScheduler.ACTION_DISMISS else action
        sendBroadcast(
            Intent(this, JuanitaAlarmReceiver::class.java)
                .setAction(effectiveAction)
                .putExtra(JuanitaAlarmScheduler.EXTRA_ALARM_ID, alarmId)
        )
        finish()
    }

    companion object {
        const val ACTION_ALARM_CLOSED = "ai.juanita.app.alarm.ALARM_CLOSED"
    }
}
