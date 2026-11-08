package ai.juanita.app.alarm

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ai.juanita.app.JuanitaVoiceService
import ai.juanita.app.data.AssistantPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lock-screen alarm controls. */
class AlarmRingingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 48, 32, 48)
            setBackgroundColor(Color.rgb(10, 11, 13))
        }
        root.addView(TextView(this).apply {
            text = "JUANITA"
            textSize = 15f
            setTextColor(Color.rgb(228, 189, 105))
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "È ora di svegliarsi"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 40)
        })
        root.addView(makeButton("POSTICIPA · 10 MINUTI") {
            sendAlarmAction(JuanitaAlarmScheduler.ACTION_SNOOZE)
        })
        root.addView(makeButton("SPEGNI LA SVEGLIA") {
            sendAlarmAction(JuanitaAlarmScheduler.ACTION_DISMISS)
        })
        root.addView(TextView(this).apply {
            text = "Dopo aver spento la sveglia puoi aprire Juanita e avviare la conversazione con la sua voce Live."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 36, 0, 0)
        })
        setContentView(root)
        val alarmPrefs = getSharedPreferences("juanita_alarm", MODE_PRIVATE)
        val voiceEnabled = alarmPrefs.getBoolean("morning_voice_enabled", false)
        val microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (voiceEnabled && microphoneGranted) {
            root.addView(TextView(this).apply {
                text = "Juanita sta preparando il tuo buongiorno…"
                textSize = 14f
                setTextColor(Color.rgb(228, 189, 105))
                gravity = Gravity.CENTER
                setPadding(0, 18, 0, 0)
            })
            lifecycleScope.launch {
                val briefing = withContext(Dispatchers.IO) { MorningBriefingContext.build(this@AlarmRingingActivity) }
                val preferences = AssistantPreferences(this@AlarmRingingActivity)
                val instruction = """
                    Sei Juanita, assistente vocale personale dell'utente. Questa è la sveglia del mattino
                    autorizzata dall'utente. Usa esclusivamente la voce Gemini Live configurata.
                    Sii dolce, naturale, luminosa e rassicurante. Non usare TTS Android.
                    Non inventare dati. Dopo il riepilogo lascia che l'utente possa risponderti.
                """.trimIndent()
                JuanitaAlarmRingingService.stop(this@AlarmRingingActivity)
                runCatching {
                    JuanitaVoiceService.start(
                        this@AlarmRingingActivity,
                        instruction,
                        preferences.liveVoiceName,
                        briefing
                    )
                }.onFailure {
                    root.addView(TextView(this@AlarmRingingActivity).apply {
                        text = "Il buongiorno vocale non è partito. Puoi aprire Juanita e avviare Live."
                        setTextColor(Color.LTGRAY)
                        gravity = Gravity.CENTER
                    })
                }
            }
        } else if (voiceEnabled) {
            root.addView(TextView(this).apply {
                text = "Per il buongiorno vocale serve il permesso microfono. La sveglia sonora resta disponibile."
                textSize = 13f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 18, 0, 0)
            })
        }
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

    private fun sendAlarmAction(action: String) {
        sendBroadcast(Intent(this, JuanitaAlarmReceiver::class.java).setAction(action))
        finish()
    }
}
