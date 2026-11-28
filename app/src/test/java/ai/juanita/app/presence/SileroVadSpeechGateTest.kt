package ai.juanita.app.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SileroVadSpeechGateTest {
    @Test
    fun silenceDoesNotReachKeywordSpotter() {
        val gate = SileroVadSpeechGate(speechProbability = { window -> if (window.any { it > 0.2f }) 0.9f else 0.0f })
        var forwarded = 0

        repeat(12) {
            gate.process(FloatArray(SileroVadSpeechGate.KWS_FRAME_SAMPLES), it * 100L) { forwarded++ }
        }

        assertEquals(0, forwarded)
        assertTrue(gate.vadWindowsProcessed > 0L)
        assertEquals(0L, gate.speechWindowsDetected)
    }

    @Test
    fun speechOnsetReplaysBoundedPreRollInChronologicalOrder() {
        val gate = SileroVadSpeechGate(speechProbability = { window -> if (window.any { it > 0.75f }) 0.9f else 0.0f })
        val received = mutableListOf<Float>()

        repeat(7) { index ->
            gate.process(FloatArray(SileroVadSpeechGate.KWS_FRAME_SAMPLES) { (index + 1) / 10f }, index * 100L) {
                received += it[0]
            }
        }
        gate.process(FloatArray(SileroVadSpeechGate.KWS_FRAME_SAMPLES) { 0.8f }, 700L) {
            received += it[0]
        }

        assertEquals(8, received.size)
        assertTrue(gate.speechWindowsDetected > 0L)
        assertEquals(listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f), received)
    }

    @Test
    fun preRollRemainsBoundedAndTrailingSilenceClosesGate() {
        val gate = SileroVadSpeechGate(speechProbability = { window -> if (window.any { it > 0.2f }) 0.9f else 0.0f })
        var forwarded = 0
        val quiet = FloatArray(SileroVadSpeechGate.KWS_FRAME_SAMPLES)
        val speech = FloatArray(SileroVadSpeechGate.KWS_FRAME_SAMPLES) { 0.9f }

        repeat(20) { index ->
            gate.process(if (index == 19) speech else quiet, index * 100L) { forwarded++ }
        }
        val afterOnset = forwarded
        assertEquals(SileroVadSpeechGate.PRE_ROLL_FRAMES, afterOnset)

        for (time in 2_000L..2_700L step 100L) gate.process(quiet, time) { forwarded++ }
        val afterTrailingWindow = forwarded
        gate.process(quiet, 2_800L) { forwarded++ }

        assertTrue(afterTrailingWindow > afterOnset)
        assertEquals(afterTrailingWindow, forwarded)
    }

    @Test
    fun resetClearsPreRollAndSpeechState() {
        val gate = SileroVadSpeechGate(speechProbability = { window -> if (window.any { it > 0.2f }) 1f else 0f })
        val speech = FloatArray(SileroVadSpeechGate.KWS_FRAME_SAMPLES) { 1f }
        var forwarded = 0
        gate.process(speech, 100L) { forwarded++ }
        assertTrue(forwarded > 0)

        gate.reset()
        forwarded = 0
        gate.process(FloatArray(SileroVadSpeechGate.KWS_FRAME_SAMPLES), 200L) { forwarded++ }

        assertFalse(forwarded > 0)
    }
}
