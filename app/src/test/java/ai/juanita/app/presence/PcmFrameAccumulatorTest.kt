package ai.juanita.app.presence

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmFrameAccumulatorTest {
    @Test
    fun partialReadsAreAccumulatedWithoutProducingIncompleteFrames() {
        val accumulator = PcmFrameAccumulator(frameSamples = 4)

        assertFalse(accumulator.append(shortArrayOf(0, 8192), 2))
        assertTrue(accumulator.append(shortArrayOf(16384, 32767), 2))

        assertArrayEquals(floatArrayOf(0f, 0.25f, 0.5f, 32767 / 32768f), accumulator.floatFrame, 0.00001f)
    }

    @Test
    fun trailingSamplesAreRetainedAfterCompletingAFrame() {
        val accumulator = PcmFrameAccumulator(frameSamples = 4)

        assertTrue(accumulator.append(shortArrayOf(1000, 2000, 3000, 4000, 5000, 6000), 6))
        assertArrayEquals(
            floatArrayOf(1000 / 32768f, 2000 / 32768f, 3000 / 32768f, 4000 / 32768f),
            accumulator.floatFrame,
            0.00001f
        )

        assertFalse(accumulator.append(shortArrayOf(7000), 1))
        assertTrue(accumulator.append(shortArrayOf(8000, 9000, 10000), 3))
        assertArrayEquals(
            floatArrayOf(5000 / 32768f, 6000 / 32768f, 7000 / 32768f, 8000 / 32768f),
            accumulator.floatFrame,
            0.00001f
        )
    }
}
