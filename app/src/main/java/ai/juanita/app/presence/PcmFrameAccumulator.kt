package ai.juanita.app.presence

/**
 * Converts arbitrary positive PCM16 read sizes into fixed-size normalized float frames.
 * Reuses both arrays and retains any trailing samples for the next read.
 */
internal class PcmFrameAccumulator(frameSamples: Int) {
    private val pcmFrame = ShortArray(frameSamples)
    val floatFrame: FloatArray = FloatArray(frameSamples)
    private var filledSamples = 0

    init {
        require(frameSamples > 0)
    }

    /** Returns true when one complete frame is ready in [floatFrame]. */
    fun append(input: ShortArray, count: Int): Boolean {
        require(count in 0..input.size)
        var inputOffset = 0
        var frameReady = false

        while (inputOffset < count) {
            val copied = minOf(count - inputOffset, pcmFrame.size - filledSamples)
            System.arraycopy(input, inputOffset, pcmFrame, filledSamples, copied)
            inputOffset += copied
            filledSamples += copied

            if (filledSamples == pcmFrame.size) {
                for (index in pcmFrame.indices) {
                    floatFrame[index] = pcmFrame[index] / 32_768.0f
                }
                filledSamples = 0
                frameReady = true
            }
        }
        return frameReady
    }
}
