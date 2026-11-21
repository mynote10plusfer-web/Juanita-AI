package ai.juanita.app.presence

/**
 * Silero probability gate for the offline keyword spotter.
 *
 * Audio is still captured continuously while wake listening is enabled, but expensive
 * keyword decoding receives audio only after Silero detects speech. A bounded pre-roll
 * preserves the beginning of a phrase that caused the gate to open.
 *
 * All methods are called from the single microphone worker.
 */
internal class SileroVadSpeechGate(
    private val speechProbability: (FloatArray) -> Float,
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val trailingSilenceMillis: Long = DEFAULT_TRAILING_SILENCE_MS
) {
    private val preRoll = Array(PRE_ROLL_FRAMES) { FloatArray(KWS_FRAME_SAMPLES) }
    private val vadWindow = FloatArray(VAD_WINDOW_SAMPLES)
    private var preRollWriteIndex = 0
    private var preRollCount = 0
    private var vadWindowFill = 0
    private var lastSpeechAtMillis = Long.MIN_VALUE
    private var active = false

    var vadWindowsProcessed: Long = 0L
        private set
    var speechWindowsDetected: Long = 0L
        private set

    init {
        require(threshold in 0f..1f)
        require(trailingSilenceMillis >= 0L)
    }

    /**
     * Adds one normalized 100 ms frame. onKeywordAudio is called for the pre-roll
     * exactly once on speech onset, then for each live frame until the trailing window
     * expires. The input frame is never retained by reference.
     */
    fun process(
        frame: FloatArray,
        nowMillis: Long,
        onKeywordAudio: (FloatArray) -> Unit
    ) {
        require(frame.size == KWS_FRAME_SAMPLES) {
            "Expected $KWS_FRAME_SAMPLES samples per keyword frame"
        }

        frame.copyInto(preRoll[preRollWriteIndex])
        preRollWriteIndex = (preRollWriteIndex + 1) % PRE_ROLL_FRAMES
        preRollCount = (preRollCount + 1).coerceAtMost(PRE_ROLL_FRAMES)

        var speechDetected = false
        frame.forEach { sample ->
            vadWindow[vadWindowFill++] = sample
            if (vadWindowFill == VAD_WINDOW_SAMPLES) {
                vadWindowsProcessed++
                if (speechProbability(vadWindow) >= threshold) {
                    speechDetected = true
                    speechWindowsDetected++
                }
                vadWindowFill = 0
            }
        }

        if (speechDetected) lastSpeechAtMillis = nowMillis

        if (!active && speechDetected) {
            active = true
            val firstIndex = (preRollWriteIndex - preRollCount + PRE_ROLL_FRAMES) % PRE_ROLL_FRAMES
            repeat(preRollCount) { offset ->
                onKeywordAudio(preRoll[(firstIndex + offset) % PRE_ROLL_FRAMES])
            }
            return
        }

        if (active) {
            onKeywordAudio(frame)
            if (!speechDetected && lastSpeechAtMillis != Long.MIN_VALUE &&
                nowMillis - lastSpeechAtMillis >= trailingSilenceMillis
            ) {
                active = false
            }
        }
    }

    fun reset() {
        preRoll.forEach { it.fill(0f) }
        vadWindow.fill(0f)
        preRollWriteIndex = 0
        preRollCount = 0
        vadWindowFill = 0
        lastSpeechAtMillis = Long.MIN_VALUE
        active = false
        vadWindowsProcessed = 0L
        speechWindowsDetected = 0L
    }

    companion object {
        const val KWS_FRAME_SAMPLES = 1_600
        const val VAD_WINDOW_SAMPLES = 512
        const val PRE_ROLL_FRAMES = 8 // 800 ms
        const val DEFAULT_THRESHOLD = 0.5f
        const val DEFAULT_TRAILING_SILENCE_MS = 600L
    }
}
