package ai.juanita.app.presence

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Offline KWS microphone owner, started only by JuanitaVoiceService after explicit opt-in.
 *
 * The service serializes this detector against Gemini Live. onDetected is invoked
 * only after AudioRecord and OnlineStream have been released. Model files and the
 * identity-specific keyword file are provisioned in app-private storage.
 * No audio is sent to a network service.
 */
class SherpaOfflineWakeWordDetector(
    private val modelDirectory: File,
    private val vadModelFile: File?,
    private val phrase: String,
    private val onDetected: (String) -> Unit,
    private val onFailure: (Throwable) -> Unit
) : Closeable {
    private val running = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    // Linearizes explicit stop against a final detection callback.
    private val stopRequested = AtomicBoolean(false)
    private val detectionDispatchLock = Any()
    private val recorderLock = Any()

    @Volatile
    private var recorder: AudioRecord? = null

    @Volatile
    private var worker: Thread? = null

    private val keywordSpotter: KeywordSpotter

    init {
        require(WakePhraseValidator.validate(phrase) == null) { "Invalid wake phrase" }
        require(modelDirectory.isDirectory) { "Wake-word model directory is missing" }
        require(vadModelFile == null || vadModelFile.isFile) { "Silero VAD model file is missing" }

        val encoder = modelDirectory.resolve(ENCODER_FILE)
        val decoder = modelDirectory.resolve(DECODER_FILE)
        val joiner = modelDirectory.resolve(JOINER_FILE)
        val tokens = modelDirectory.resolve(TOKENS_FILE)
        val bpeModel = modelDirectory.resolve(BPE_FILE)
        val keywords = modelDirectory.resolve(KEYWORDS_FILE)
        val keywordManifest = modelDirectory.resolve(KEYWORD_MANIFEST_FILE)
        val required = listOf(encoder, decoder, joiner, tokens, bpeModel, keywords, keywordManifest)
        require(required.all(File::isFile)) { "Wake-word model or generated keyword files are incomplete" }
        require(keywordManifest.readText().trim() == phrase.trim()) {
            "Generated keyword file does not match the selected wake phrase"
        }

        val modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = encoder.absolutePath,
                decoder = decoder.absolutePath,
                joiner = joiner.absolutePath
            ),
            tokens = tokens.absolutePath,
            numThreads = MODEL_THREADS,
            provider = "cpu",
            modelType = "zipformer2",
            modelingUnit = "bpe",
            bpeVocab = bpeModel.absolutePath
        )

        keywordSpotter = KeywordSpotter(
            config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
                modelConfig = modelConfig,
                maxActivePaths = 4,
                keywordsFile = keywords.absolutePath,
                keywordsScore = KEYWORD_SCORE,
                keywordsThreshold = KEYWORD_THRESHOLD,
                numTrailingBlanks = 2
            )
        )
    }

    /** Starts one capture session. Repeated starts are ignored. */
    @Synchronized
    fun start() {
        check(!closed.get()) { "Detector is closed" }
        if (!running.compareAndSet(false, true)) return
        synchronized(detectionDispatchLock) { stopRequested.set(false) }
        worker = thread(name = "JuanitaOfflineKws", isDaemon = true) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            runCapture()
        }
    }

    /**
     * Requests capture stop and waits for the worker to release the microphone.
     * Call from a service IO dispatcher, never the main thread.
     */
    @Synchronized
    fun stop() {
        synchronized(detectionDispatchLock) { stopRequested.set(true) }
        running.set(false)
        synchronized(recorderLock) {
            runCatching { recorder?.stop() }
        }
        val activeWorker = worker
        if (activeWorker != null && activeWorker !== Thread.currentThread()) {
            activeWorker.join(STOP_TIMEOUT_MS)
            check(!activeWorker.isAlive) { "Wake-word audio worker did not stop in time" }
        }
        worker = null
    }

    private fun runCapture() {
        var stream: OnlineStream? = null
        var vad: Vad? = null
        var audioRecord: AudioRecord? = null
        var detectedPhrase: String? = null
        var failure: Throwable? = null

        try {
            val minBufferBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBufferBytes > 0) { "AudioRecord returned invalid buffer size: $minBufferBytes" }

            val bufferBytes = maxOf(minBufferBytes * 2, SAMPLE_BUFFER_SIZE * 2)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes
            )
            check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                "Unable to initialize microphone for offline wake-word detection"
            }

            // BPE text must be encoded with this model's SentencePiece vocabulary before
            // it reaches Sherpa. The provisioner creates keywords.txt with the canonical
            // text2token/SentencePiece implementation; never pass raw user text here.
            val activeStream = keywordSpotter.createStream()
            stream = activeStream
            val activeVad = vadModelFile?.let { modelFile ->
                Vad(
                    config = VadModelConfig(
                        sileroVadModelConfig = SileroVadModelConfig(
                            model = modelFile.absolutePath,
                            threshold = SileroVadSpeechGate.DEFAULT_THRESHOLD,
                            windowSize = SileroVadSpeechGate.VAD_WINDOW_SAMPLES
                        ),
                        sampleRate = SAMPLE_RATE,
                        numThreads = MODEL_THREADS,
                        provider = "cpu"
                    )
                ).also { vad = it }
            }
            val speechGate = activeVad?.let { detector ->
                SileroVadSpeechGate { samples -> detector.compute(samples) }
            }
            val activeRecorder = audioRecord
            synchronized(recorderLock) {
                if (!running.get()) return
                recorder = activeRecorder
                activeRecorder.startRecording()
            }

            val pcm = ShortArray(SAMPLE_BUFFER_SIZE)
            val frameAccumulator = PcmFrameAccumulator(SAMPLE_BUFFER_SIZE)
            var consecutiveEmptyReads = 0
            var lastPowerLogAtMs = SystemClock.elapsedRealtime()
            var lastThreadCpuNs = Debug.threadCpuTimeNanos()
            var decodeCpuWallNs = 0L
            var decodedFrames = 0L
            var audioBlocks = 0L
            var lastVadWindowCount = 0L
            var lastSpeechWindowCount = 0L

            while (running.get()) {
                val count = activeRecorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                if (count == AudioRecord.ERROR_DEAD_OBJECT) {
                    error("Microphone audio device was lost")
                }
                if (count < 0) {
                    error("AudioRecord.read failed with code $count")
                }
                if (count == 0) {
                    consecutiveEmptyReads++
                    if (consecutiveEmptyReads >= MAX_EMPTY_READS) {
                        error("AudioRecord repeatedly returned empty reads")
                    }
                    // READ_BLOCKING should normally return samples. Bound transient zero
                    // reads with a short backoff instead of spinning at full CPU speed.
                    Thread.sleep((consecutiveEmptyReads * EMPTY_READ_BACKOFF_MS).coerceAtMost(MAX_EMPTY_READ_BACKOFF_MS))
                    continue
                }
                consecutiveEmptyReads = 0

                // Accumulate short reads into fixed 100 ms frames. This avoids copyOf()
                // allocations for every partial AudioRecord.read() result.
                if (frameAccumulator.append(pcm, count)) {
                    val feedKeywordSpotter: (FloatArray) -> Unit = { audioFrame ->
                        activeStream.acceptWaveform(audioFrame, SAMPLE_RATE)
                        audioBlocks++
                    }
                    if (speechGate != null) {
                        speechGate.process(
                            frameAccumulator.floatFrame,
                            SystemClock.elapsedRealtime(),
                            feedKeywordSpotter
                        )
                    } else {
                        // Integrity/download failure of the optional VAD must never
                        // disable wake detection: retain the proven continuous Sherpa path.
                        feedKeywordSpotter(frameAccumulator.floatFrame)
                    }

                    while (keywordSpotter.isReady(activeStream)) {
                        val decodeStartedNs = SystemClock.elapsedRealtimeNanos()
                        keywordSpotter.decode(activeStream)
                        decodeCpuWallNs += SystemClock.elapsedRealtimeNanos() - decodeStartedNs
                        decodedFrames++
                        val result = keywordSpotter.getResult(activeStream).keyword
                        if (result.isNotBlank()) {
                            detectedPhrase = result.replace('_', ' ')
                            running.set(false)
                            break
                        }
                    }
                }

                // Low-frequency diagnostics quantify the actual KWS worker load on-device.
                // This is deliberately not a polling worker and does not keep the CPU awake.
                val nowMs = SystemClock.elapsedRealtime()
                if (nowMs - lastPowerLogAtMs >= POWER_LOG_INTERVAL_MS) {
                    val nowCpuNs = Debug.threadCpuTimeNanos()
                    val wallNs = (nowMs - lastPowerLogAtMs) * 1_000_000L
                    val cpuNs = (nowCpuNs - lastThreadCpuNs).coerceAtLeast(0L)
                    val cpuPercent = if (wallNs > 0L) cpuNs * 100.0 / wallNs else 0.0
                    val decodePercent = if (wallNs > 0L) decodeCpuWallNs * 100.0 / wallNs else 0.0
                    val vadWindowCount = speechGate?.vadWindowsProcessed ?: 0L
                    val speechWindowCount = speechGate?.speechWindowsDetected ?: 0L
                    val vadWindowsDelta = (vadWindowCount - lastVadWindowCount).coerceAtLeast(0L)
                    val speechWindowsDelta = (speechWindowCount - lastSpeechWindowCount).coerceAtLeast(0L)
                    val speechRatio = if (vadWindowsDelta > 0L) {
                        speechWindowsDelta * 100.0 / vadWindowsDelta
                    } else {
                        0.0
                    }
                    Log.i(
                        TAG,
                        "KWS power sample: workerCpuPercent=%.1f decodeWallPercent=%.1f blocks=%d decodedFrames=%d vadWindows=%d speechWindows=%d speechRatio=%.1f%%".format(
                            java.util.Locale.US, cpuPercent, decodePercent, audioBlocks, decodedFrames,
                            vadWindowsDelta, speechWindowsDelta, speechRatio
                        )
                    )
                    lastVadWindowCount = vadWindowCount
                    lastSpeechWindowCount = speechWindowCount
                    lastPowerLogAtMs = nowMs
                    lastThreadCpuNs = nowCpuNs
                    decodeCpuWallNs = 0L
                    decodedFrames = 0L
                    audioBlocks = 0L
                }
            }
        } catch (error: Throwable) {
            if (running.get()) failure = error
        } finally {
            running.set(false)
            runCatching { vad?.release() }
            runCatching { stream?.release() }
            synchronized(recorderLock) {
                val ownedRecorder = audioRecord
                if (ownedRecorder != null) {
                    runCatching {
                        if (ownedRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            ownedRecorder.stop()
                        }
                    }
                    runCatching { ownedRecorder.release() }
                }
                if (recorder === ownedRecorder) recorder = null
            }
            if (failure != null) {
                Log.e(TAG, "Offline wake-word capture failed", failure)
                onFailure(failure!!)
            } else if (detectedPhrase != null) {
                // Linearize the final callback against stop/disable. If stop acquired
                // this gate first, the hit is discarded; otherwise detection is ordered
                // before the subsequent stop request.
                synchronized(detectionDispatchLock) {
                    if (!stopRequested.get()) onDetected(detectedPhrase!!)
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        if (closed.get()) return
        // Do not mark closed until the worker has definitely released its stream and
        // recorder. If stop times out, a later close() can retry safely.
        stop()
        if (closed.compareAndSet(false, true)) {
            keywordSpotter.release()
        }
    }

    companion object {
        private const val TAG = "JuanitaOfflineKws"
        private const val SAMPLE_RATE = 16_000
        private const val FEATURE_DIM = 80
        private const val SAMPLE_BUFFER_SIZE = 1_600 // 100 ms at 16 kHz
        private const val PCM_SCALE = 32_768.0f
        private const val MODEL_THREADS = 1
        private const val KEYWORD_SCORE = 1.0f
        private const val KEYWORD_THRESHOLD = 0.20f
        private const val STOP_TIMEOUT_MS = 3_000L
        private const val MAX_EMPTY_READS = 10
        private const val EMPTY_READ_BACKOFF_MS = 10L
        private const val MAX_EMPTY_READ_BACKOFF_MS = 100L
        private const val POWER_LOG_INTERVAL_MS = 60_000L

        const val ENCODER_FILE = "encoder-epoch-20-avg-2-chunk-16-left-64.onnx"
        const val DECODER_FILE = "decoder-epoch-20-avg-2-chunk-16-left-64.onnx"
        const val JOINER_FILE = "joiner-epoch-20-avg-2-chunk-16-left-64.onnx"
        const val TOKENS_FILE = "tokens.txt"
        const val BPE_FILE = "bpe.model"
        const val KEYWORDS_FILE = "keywords.txt"
        const val KEYWORD_MANIFEST_FILE = "keywords.phrase"
    }
}
