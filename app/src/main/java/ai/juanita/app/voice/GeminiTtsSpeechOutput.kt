package ai.juanita.app.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.SpeechMetadata
import com.google.firebase.ai.type.Voice
import ai.juanita.app.identity.RegionalTtsTargetCatalog
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

/**
 * Replaces Gemini Live's rendered response with the selected Gemini TTS voice.
 * Live PCM is retained only as a same-turn fallback if TTS generation fails.
 */
@OptIn(PublicPreviewAPI::class)
class GeminiTtsSpeechOutput(
    private val voiceName: String,
    private val targetId: String?,
    private val languageCode: String,
    private val onDiagnosticEvent: (event: String, detail: String) -> Unit = { _, _ -> }
) {
    @Volatile
    private var activeTrack: AudioTrack? = null

    suspend fun speak(text: String, liveFallbackPcm: ByteArray): Boolean {
        require(text.isNotBlank()) { "Il testo da pronunciare è vuoto." }
        return try {
            withTimeout(MAX_TTS_TIMEOUT_MS) {
                val wav = withContext(Dispatchers.IO) {
                    val target = speechTarget(targetId, languageCode)
                    val config = generationConfig {
                        responseModalities = listOf(ResponseModality.AUDIO)
                        speechConfig = SpeechConfig(
                            voice = Voice(voiceName),
                            languageCode = target.languageCode
                        )
                    }
                    val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                        .generativeModel(
                            modelName = TTS_MODEL_NAME,
                            generationConfig = config
                        )
                    val prompt = content {
                        text(
                            text = text.take(MAX_TTS_TEXT_CHARS),
                            speechMetadata = SpeechMetadata(style = target.style)
                        )
                    }
                    model.generateContent(prompt).candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<InlineDataPart>()
                        ?.firstOrNull()?.inlineData
                        ?: error("Gemini TTS non ha restituito audio.")
                }
                onDiagnosticEvent("tts.audio.generated", "wavBytes=${wav.size}")
                coroutineContext.ensureActive()
                playWav(wav)
            }
            true
        } catch (timeout: TimeoutCancellationException) {
            if (liveFallbackPcm.isEmpty()) throw timeout
            playPcm(liveFallbackPcm)
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (ttsFailure: Exception) {
            if (liveFallbackPcm.isEmpty()) throw ttsFailure
            // The original Live voice is used only as a fail-safe for this same response.
            playPcm(liveFallbackPcm)
            false
        }
    }

    suspend fun playFallback(liveFallbackPcm: ByteArray) {
        if (liveFallbackPcm.isNotEmpty()) playPcm(liveFallbackPcm)
    }

    /** Interrupts current TTS/fallback playback immediately; caller also cancels speak(). */
    fun stop() {
        val track = activeTrack ?: return
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
        }
        runCatching { track.flush() }
    }

    private suspend fun playWav(wav: ByteArray) {
        val pcm = extractPcm16Mono(wav)
        onDiagnosticEvent("tts.audio.pcm", "pcmBytes=${pcm.size}; sampleRateHz=$OUTPUT_SAMPLE_RATE")
        playPcm(pcm)
    }

    private suspend fun playPcm(pcm: ByteArray) = withContext(Dispatchers.IO) {
        require(pcm.isNotEmpty()) { "Audio PCM vuoto." }
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(OUTPUT_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(
            OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4_096)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(pcm.size, minBuffer))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        activeTrack = track
        val routedDevice = {
            val device = runCatching { track.routedDevice }.getOrNull()
            device?.let { "${it.productName}/type=${it.type}" } ?: "unknown"
        }
        onDiagnosticEvent(
            "tts.audio.track.created",
            "state=${track.state}; bufferBytes=${maxOf(pcm.size, minBuffer)}; routedDevice=${routedDevice()}"
        )
        try {
            var offset = 0
            while (offset < pcm.size) {
                val written = track.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                check(written > 0) { "Impossibile caricare l'audio sul dispositivo." }
                offset += written
            }
            onDiagnosticEvent("tts.audio.track.loaded", "writtenBytes=$offset; expectedBytes=${pcm.size}")
            track.play()
            onDiagnosticEvent(
                "tts.audio.play.started",
                "playState=${track.playState}; routedDevice=${routedDevice()}"
            )
            val durationMillis = (pcm.size * 1_000L / (OUTPUT_SAMPLE_RATE.toLong() * 2L)).coerceAtLeast(250L)
            delay(durationMillis + 120L)
            onDiagnosticEvent(
                "tts.audio.play.completed",
                "playbackHeadFrames=${track.playbackHeadPosition}; expectedFrames=${pcm.size / 2}; " +
                    "playState=${track.playState}; routedDevice=${routedDevice()}"
            )
        } finally {
            if (activeTrack === track) activeTrack = null
            runCatching { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() }
            track.release()
        }
    }

    private fun speechTarget(id: String?, requestedLanguage: String): SpeechTarget {
        val regional = RegionalTtsTargetCatalog.byId(id)
        return if (regional != null) {
            SpeechTarget(regional.languageCode, regional.delivery)
        } else {
            SpeechTarget(
                requestedLanguage.ifBlank { "es-ES" },
                "Speak naturally and conversationally in the requested language. Use clear articulation, a calm pace and natural pauses. Preserve the selected voice's own vocal character and gender presentation."
            )
        }
    }

    private data class SpeechTarget(val languageCode: String, val style: String)

    private fun extractPcm16Mono(wav: ByteArray): ByteArray {
        require(wav.size > WAV_HEADER_MIN_SIZE) { "File WAV vuoto o incompleto." }
        require(
            String(wav, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(wav, 8, 4, Charsets.US_ASCII) == "WAVE"
        ) { "Formato audio WAV non riconosciuto." }
        var offset = 12
        while (offset + 8 <= wav.size) {
            val chunkId = String(wav, offset, 4, Charsets.US_ASCII)
            val chunkSize = (wav[offset + 4].toInt() and 0xff) or
                ((wav[offset + 5].toInt() and 0xff) shl 8) or
                ((wav[offset + 6].toInt() and 0xff) shl 16) or
                ((wav[offset + 7].toInt() and 0xff) shl 24)
            val dataStart = offset + 8
            if (chunkId == "data") {
                require(chunkSize > 0 && dataStart + chunkSize <= wav.size) {
                    "Dati audio WAV incompleti."
                }
                return wav.copyOfRange(dataStart, dataStart + chunkSize)
            }
            offset = dataStart + chunkSize + (chunkSize and 1)
        }
        error("Il WAV non contiene una traccia audio PCM.")
    }

    private companion object {
        const val TTS_MODEL_NAME = "gemini-3.8-flash-tts"
        const val OUTPUT_SAMPLE_RATE = 24_000
        const val MAX_TTS_TIMEOUT_MS = 45_000L
        const val MAX_TTS_TEXT_CHARS = 5_000
        const val WAV_HEADER_MIN_SIZE = 44
    }
}
