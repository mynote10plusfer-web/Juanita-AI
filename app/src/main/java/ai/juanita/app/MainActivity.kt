package ai.juanita.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.juanita.app.data.AssistantPreferences
import java.util.Locale

private data class VoiceLanguage(val label: String, val tag: String) {
    val locale: Locale get() = Locale.forLanguageTag(tag)
}

private val voiceLanguages = listOf(
    VoiceLanguage("Español (España)", "es-ES"),
    VoiceLanguage("Italiano", "it-IT"),
    VoiceLanguage("Čeština", "cs-CZ"),
    VoiceLanguage("English", "en-US"),
    VoiceLanguage("Español (Colombia)", "es-CO")
)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private lateinit var preferences: AssistantPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AssistantPreferences(applicationContext)

        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
        }

        setContent {
            JuanitaApp(
                initialLanguageTag = preferences.languageTag,
                onLanguageSelected = { preferences.languageTag = it },
                initialDialectId = preferences.dialectId,
                onDialectSelected = { dialectId, languageTag ->
                    preferences.dialectId = dialectId
                    preferences.languageTag = languageTag
                },
                onSpeak = { text, language ->
                    val engine = tts
                    when {
                        !ttsReady || engine == null ->
                            "Il motore vocale Android non è ancora pronto."
                        engine.isLanguageAvailable(language.locale) < TextToSpeech.LANG_AVAILABLE ->
                            "La voce " + language.label + " non è installata o non è supportata."
                        else -> {
                            val languageResult = engine.setLanguage(language.locale)
                            if (languageResult < TextToSpeech.LANG_AVAILABLE) {
                                "Impossibile attivare la lingua " + language.label + "."
                            } else {
                                val speakResult = engine.speak(
                                    text,
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "juanita-intro"
                                )
                                if (speakResult == TextToSpeech.ERROR) {
                                    "Il motore vocale non è riuscito ad avviare la riproduzione."
                                } else {
                                    "Voce avviata con " + language.label + "."
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        super.onDestroy()
    }
}

@Composable
private fun JuanitaApp(
    initialLanguageTag: String,
    onLanguageSelected: (String) -> Unit,
    initialDialectId: String,
    onDialectSelected: (String, String) -> Unit,
    onSpeak: (String, VoiceLanguage) -> String
) {
    val background = Color(0xFF090A0C)
    val panel = Color(0xFF17191D)
    val gold = Color(0xFFE4BD69)
    val initialLanguage = voiceLanguages.firstOrNull { it.tag == initialLanguageTag }
        ?: voiceLanguages.first()
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    val dialects = listOf(
        DialectProfile(
            id = "sicilian-palermo",
            label = "Siciliano · area palermitana",
            languageTag = "it-IT",
            sample = "Chi bedda jurnata! Nni facemu 'na passiata?",
            note = "Variante siciliana da sviluppare e verificare con parlanti madrelingua."
        ),
        DialectProfile(
            id = "canarian",
            label = "Español canario",
            languageTag = "es-ES",
            sample = "¡Qué día más bueno! ¿Nos damos una vueltita por la isla?",
            note = "Spagnolo delle Canarie: lessico e ritmo regionali da affinare."
        ),
        DialectProfile(
            id = "paisa",
            label = "Colombiano paisa",
            languageTag = "es-CO",
            sample = "¡Qué más pues! ¿Cómo vamos? Nos damos una vueltica, ¿o qué?",
            note = "Variante paisa: espressioni colloquiali da calibrare con parlanti di Antioquia."
        )
    )
    var selectedDialect by remember {
        mutableStateOf(dialects.firstOrNull { it.id == initialDialectId } ?: dialects[0])
    }
    var dialectMenuExpanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("Qui apparirà il testo riconosciuto.") }
    var status by remember { mutableStateOf("Impostazioni salvate sul dispositivo") }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                transcript = spoken
                status = "Voce riconosciuta"
            } else {
                status = "Nessun testo riconosciuto"
            }
        } else {
            status = "Riconoscimento vocale annullato o non disponibile"
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "JUANITA",
                    color = gold,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Text(
                    text = "PERSONAL VOICE ASSISTANT",
                    color = Color(0xFF9A9DA4),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = panel),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("LINGUA DELLA VOCE", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selectedLanguage.label,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { menuExpanded = true }) {
                                Text("Cambia", color = gold)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                voiceLanguages.forEach { language ->
                                    DropdownMenuItem(
                                        text = { Text(language.label) },
                                        onClick = {
                                            selectedLanguage = language
                                            onLanguageSelected(language.tag)
                                            menuExpanded = false
                                            status = "Lingua " + language.label + " salvata"
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            "La scelta resta memorizzata anche dopo la chiusura dell'app.",
                            color = Color(0xFF9A9DA4),
                            fontSize = 12.sp
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = panel),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("VARIANTE REGIONALE", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selectedDialect.label,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { dialectMenuExpanded = true }) {
                                Text("Scegli", color = gold)
                            }
                            DropdownMenu(
                                expanded = dialectMenuExpanded,
                                onDismissRequest = { dialectMenuExpanded = false }
                            ) {
                                dialects.forEach { dialect ->
                                    DropdownMenuItem(
                                        text = { Text(dialect.label) },
                                        onClick = {
                                            selectedDialect = dialect
                                            val matchingLanguage = voiceLanguages.firstOrNull {
                                                it.tag == dialect.languageTag
                                            } ?: selectedLanguage
                                            selectedLanguage = matchingLanguage
                                            onLanguageSelected(matchingLanguage.tag)
                                            onDialectSelected(dialect.id, dialect.languageTag)
                                            dialectMenuExpanded = false
                                            status = "Profilo " + dialect.label + " selezionato"
                                        }
                                    )
                                }
                            }
                        }
                        Text(selectedDialect.note, color = Color(0xFF9A9DA4), fontSize = 12.sp)
                        Text(
                            "Frase di esempio: " + selectedDialect.sample,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            "Anteprima con voce di sistema: non è ancora una voce dialettale autentica.",
                            color = Color(0xFFE4BD69),
                            fontSize = 11.sp
                        )
                        Button(
                            onClick = {
                                status = onSpeak(selectedDialect.sample, selectedLanguage)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF30343B),
                                contentColor = gold
                            )
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ascolta esempio")
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = panel),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("PROVA MICROFONO", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(transcript, color = Color.White, fontSize = 16.sp)
                        Button(
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage.tag)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguage.tag)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Parla con Juanita")
                                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                }
                                try {
                                    speechLauncher.launch(intent)
                                    status = "Ascolto tramite il servizio vocale Android…"
                                } catch (_: Exception) {
                                    status = "Nessun servizio di riconoscimento vocale disponibile"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Parla")
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = panel),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("VOCE DI JUANITA", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Ciao, sono Juanita. Sto preparando la mia voce e le mie capacità.",
                            color = Color.White
                        )
                        Button(
                            onClick = {
                                status = onSpeak(
                                    "Ciao, sono Juanita. Piacere di conoscerti. Come posso aiutarti?",
                                    selectedLanguage
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ascolta la voce")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text("STATO", color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(status, color = Color(0xFFB9BBC1), fontSize = 13.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Versione 0.3.0 • Profili regionali sperimentali",
                    color = Color(0xFF686C74),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
