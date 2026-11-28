package ai.juanita.app.presence

/** Bounds unattended background Live sessions without limiting ordinary foreground calls. */
object BackgroundLiveSafetyPolicy {
    const val IDLE_TIMEOUT_MS = 15 * 60 * 1_000L
    fun remainingIdleMillis(lastUserActivityElapsedRealtime: Long, nowElapsedRealtime: Long): Long {
        val idleFor = (nowElapsedRealtime - lastUserActivityElapsedRealtime).coerceAtLeast(0L)
        return (IDLE_TIMEOUT_MS - idleFor).coerceAtLeast(0L)
    }
    fun shouldStop(appInForeground: Boolean, backgroundContinuationAuthorized: Boolean, alarmSession: Boolean, idleDurationMs: Long): Boolean =
        !appInForeground && backgroundContinuationAuthorized && !alarmSession && idleDurationMs >= IDLE_TIMEOUT_MS
}
