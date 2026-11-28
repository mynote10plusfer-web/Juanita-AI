package ai.juanita.app.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ai.juanita.app.identity.DigitalIdentity
import ai.juanita.app.identity.RegionalTtsVoiceCatalog
import ai.juanita.app.identity.RegionalTtsTargetCatalog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.SpeechMetadata
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private val regionalTestVoices = RegionalTtsVoiceCatalog.all
/** Isolated TTS audition lab. It never starts or stops the Gemini Live microphone session. */
@OptIn(PublicPreviewAPI::class)
@Composable
fun RegionalVoiceLabSection(
    panel: Color,
    gold: Color,
    profiles: List<DigitalIdentity>,
    activeProfileId: String?,
    onSaveVoice: suspend (profileId: String, voiceName: String, targetId: String) -> DigitalIdentity
) {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(RegionalTtsTargetCatalog.paisa) }
    var voice by remember { mutableStateOf("Leda") }
    var profileId by remember(activeProfileId) { mutableStateOf(activeProfileId ?: profiles.firstOrNull { !it.archived }?.id) }
    var targetMenu by remember { mutableStateOf(false) }
    var voiceMenu by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Laboratorio TTS cloud in pausa: la conversazione usa la voce nativa Gemini Live.") }
    val selectedProfile = profiles.firstOrNull { it.id == profileId && !it.archived }
    LaunchedEffect(selectedProfile?.id, selectedProfile?.regionalTtsVoiceName, selectedProfile?.regionalTtsTargetId) {
        selectedProfile?.let { saved ->
            voice = saved.regionalTtsVoiceName?.takeIf { it in regionalTestVoices } ?: "Leda"
            target = RegionalTtsTargetCatalog.byId(saved.regionalTtsTargetId) ?: RegionalTtsTargetCatalog.paisa
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = panel)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("LABORATORIO VOCE REGIONALE · TEST A/B", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                "Questa prova usa Gemini TTS separatamente dalla conversazione Live. Ascoltiamo lo stesso campione cambiando regione o voce.",
                color = Color(0xFFB9BBC1),
                fontSize = 12.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Variante", color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = { targetMenu = true }, enabled = !busy) { Text(target.label, color = gold) }
                DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                    RegionalTtsTargetCatalog.all.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { target = option; targetMenu = false })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voce Gemini TTS · in pausa", color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = { voiceMenu = true }, enabled = !busy) { Text(voice, color = gold) }
                DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                    regionalTestVoices.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { voice = option; voiceMenu = false })
                    }
                }
            }
            Text("Campione: ${target.sample}", color = Color.White, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Salva nel personaggio", color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = { profileMenu = true }, enabled = !busy && profiles.any { !it.archived }) {
                    Text(selectedProfile?.displayName ?: "Seleziona profilo", color = gold)
                }
                DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                    profiles.filter { !it.archived }.forEach { profile ->
                        DropdownMenuItem(text = { Text(profile.displayName) }, onClick = {
                            profileId = profile.id
                            profileMenu = false
                        })
                    }
                }
            }
            selectedProfile?.let { saved ->
                Text("Voce TTS salvata (non attiva): ${saved.regionalTtsVoiceName ?: "non configurata"} · ${saved.regionalTtsTargetId ?: "nessuna variante"}",
                    color = Color(0xFF9A9DA4), fontSize = 11.sp)
            } ?: Text("Crea o seleziona prima un personaggio per salvare una voce personale.", color = Color(0xFFFFCC80), fontSize = 11.sp)
            Text(
                "Le generazioni TTS cloud sono temporaneamente sospese per evitare il limite giornaliero gratuito. Le conversazioni usano Gemini Live; la variante regionale salvata viene applicata alle istruzioni vocali.",
                color = Color(0xFFFFCC80),
                fontSize = 11.sp
            )
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        status = "Genero l'audio con Gemini 3.8 Flash TTS…"
                        try {
                            val wav = withTimeout(45_000L) {
                                withContext(Dispatchers.IO) {
                                    val config = generationConfig {
                                        responseModalities = listOf(ResponseModality.AUDIO)
                                        speechConfig = SpeechConfig(voice = Voice(voice), languageCode = target.languageCode)
                                    }
                                    val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                                        .generativeModel(modelName = "gemini-3.8-flash-tts", generationConfig = config)
                                    val prompt = content {
                                        text(text = target.sample, speechMetadata = SpeechMetadata(style = target.delivery))
                                    }
                                    val response = model.generateContent(prompt)
                                    response.candidates.firstOrNull()?.content?.parts
                                        ?.filterIsInstance<InlineDataPart>()?.firstOrNull()?.inlineData
                                        ?: error("Gemini non ha restituito un file audio.")
                                }
                            }
                            status = "Audio ricevuto. Riproduzione in corso…"
                            playWav(wav)
                            status = "Prova completata: $voice · ${target.label}. Cambia voce o variante per confrontare."
                        } catch (error: Exception) {
                            status = "Test non riuscito: ${error.message ?: error::class.java.simpleName}. Gemini Live non è stato modificato."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("TEST TTS CLOUD IN PAUSA")
            }
            Button(
                onClick = {
                    val destination = selectedProfile
                    if (destination == null) {
                        status = "Seleziona un personaggio esistente prima di salvare."
                    } else {
                        scope.launch {
                            busy = true
                            status = "Salvo la voce nel profilo ${destination.displayName}…"
                            try {
                                val updated = onSaveVoice(destination.id, voice, target.id)
                                status = "Salvato: $voice · ${target.label} nel personaggio ${updated.displayName}."
                            } catch (error: Exception) {
                                status = "Salvataggio non riuscito: ${error.message ?: error::class.java.simpleName}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                enabled = !busy && selectedProfile != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "ATTENDI…" else "SALVA NEL PERSONAGGIO") }
            Text(status, color = if (status.startsWith("Test non riuscito") || status.startsWith("Salvataggio non riuscito")) Color(0xFFFFAB91) else Color(0xFF9A9DA4), fontSize = 11.sp)
            Text(
                "Motore sperimentale in anteprima. La richiesta usa il livello gratuito Gemini Developer API quando disponibile; non abilita Blaze né configura servizi a pagamento. Se Google rifiuta la richiesta sul progetto Spark, l'errore apparirà qui.",
                color = Color(0xFF777D87),
                fontSize = 10.sp
            )
        }
    }
}

private suspend fun playWav(wav: ByteArray) = withContext(Dispatchers.IO) {
    val pcm = extractPcm16Mono(wav)
    val format = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(24_000)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    val minBuffer = AudioTrack.getMinBufferSize(24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        .coerceAtLeast(4_096)
    val track = AudioTrack.Builder()
        .setAudioAttributes(attributes)
        .setAudioFormat(format)
        .setBufferSizeInBytes(maxOf(pcm.size, minBuffer))
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    try {
        var offset = 0
        while (offset < pcm.size) {
            val written = track.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
            check(written > 0) { "Impossibile caricare l'audio sul dispositivo." }
            offset += written
        }
        track.play()
        val durationMillis = (pcm.size * 1_000L / (24_000L * 2L)).coerceAtLeast(250L)
        delay(durationMillis + 150L)
    } finally {
        runCatching { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() }
        track.release()
    }
}

private fun extractPcm16Mono(wav: ByteArray): ByteArray {
    require(wav.size > 44) { "File WAV vuoto o incompleto." }
    require(String(wav, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(wav, 8, 4, Charsets.US_ASCII) == "WAVE") { "Formato audio WAV non riconosciuto." }
    var offset = 12
    while (offset + 8 <= wav.size) {
        val chunkId = String(wav, offset, 4, Charsets.US_ASCII)
        val chunkSize = (wav[offset + 4].toInt() and 0xff) or
            ((wav[offset + 5].toInt() and 0xff) shl 8) or
            ((wav[offset + 6].toInt() and 0xff) shl 16) or
            ((wav[offset + 7].toInt() and 0xff) shl 24)
        val dataStart = offset + 8
        if (chunkId == "data") {
            require(chunkSize > 0 && dataStart + chunkSize <= wav.size) { "Dati audio WAV incompleti." }
            return wav.copyOfRange(dataStart, dataStart + chunkSize)
        }
        offset = dataStart + chunkSize + (chunkSize and 1)
    }
    error("Il WAV non contiene una traccia audio PCM.")
}
