package ai.juanita.app.presence

/**
 * Battery guard for the optional Gemini Live conversation after an alarm briefing.
 * It is deliberately separate from the persistent offline wake-word listener.
 */
object AlarmLiveIdlePolicy {
    /** Give the user a brief chance to answer after Juanita finishes speaking. */
    const val IDLE_TIMEOUT_MS = 20_000L

    /** Never let an alarm-triggered Live session become an open-ended conversation. */
    const val MAX_SESSION_MS = 3 * 60 * 1_000L

    fun remainingTimeoutMillis(
        startedAtElapsedRealtime: Long,
        lastVoiceActivityElapsedRealtime: Long,
        nowElapsedRealtime: Long
    ): Long {
        val idleDeadline = lastVoiceActivityElapsedRealtime + IDLE_TIMEOUT_MS
        val absoluteDeadline = startedAtElapsedRealtime + MAX_SESSION_MS
        return (minOf(idleDeadline, absoluteDeadline) - nowElapsedRealtime).coerceAtLeast(0L)
    }
}
