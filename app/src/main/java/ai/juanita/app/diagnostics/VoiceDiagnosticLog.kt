package ai.juanita.app.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Privacy-safe voice diagnostics. Event callbacks only sanitize and enqueue;
 * all filesystem work is serialized on a bounded background worker.
 */
class VoiceDiagnosticLog(context: Context) {
    private val file = File(context.applicationContext.filesDir, "diagnostics/voice-diagnostic.txt")
    private val lock = Any()
    private val droppedEvents = AtomicLong(0L)
    private val writer = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
        ThreadFactory { runnable ->
            Thread(runnable, "JuanitaVoiceDiagWriter").apply { isDaemon = true }
        }
    )

    fun event(name: String, detail: String = "") {
        val safeName = name.replace(UNSAFE_NAME, "_").take(90)
        val safeDetail = detail
            .replace(API_KEY, "[REDACTED_KEY]")
            .replace(URL, "[REDACTED_URL]")
            .replace(WHITESPACE, " ")
            .take(240)
        val event = DiagnosticEvent(Instant.now().toString(), Thread.currentThread().name, safeName, safeDetail)
        try {
            writer.execute { appendEvent(event) }
        } catch (_: RejectedExecutionException) {
            // Diagnostics are best-effort; never block a Live callback when the
            // bounded queue is saturated. The next persisted event reports the loss.
            droppedEvents.incrementAndGet()
        }
    }

    private fun appendEvent(event: DiagnosticEvent) {
        synchronized(lock) {
            var dropped = droppedEvents.getAndSet(0L)
            runCatching {
                file.parentFile?.mkdirs()
                if (file.exists() && file.length() > MAX_BYTES) {
                    val previous = File(file.parentFile, "voice-diagnostic.previous.txt")
                    if (previous.exists() && !previous.delete()) {
                        throw IllegalStateException("Unable to rotate previous voice diagnostic log")
                    }
                    if (!file.renameTo(previous)) {
                        throw IllegalStateException("Unable to rotate voice diagnostic log")
                    }
                }
                file.appendText(buildString {
                    if (dropped > 0L) appendLine("${event.timestamp} | diagnostics | queue_overflow | dropped=$dropped")
                    appendLine("${event.timestamp} | ${event.threadName} | ${event.name} | ${event.detail}")
                })
            }.onFailure {
                if (dropped > 0L) droppedEvents.addAndGet(dropped)
                Log.w(TAG, "Unable to append voice diagnostic event", it)
            }
        }
    }

    /** Wait for already-enqueued events before exporting; called from the UI/export path only. */
    private fun flush(timeoutMillis: Long = EXPORT_FLUSH_TIMEOUT_MS) {
        val barrier = CountDownLatch(1)
        try {
            // put() may wait for queue capacity, but this method is never called by
            // an audio callback; event() itself remains non-blocking.
            try {
                writer.execute { barrier.countDown() }
            } catch (_: RejectedExecutionException) {
                // The queue is full: enqueue the barrier after existing events drain.
                writer.queue.put(Runnable { barrier.countDown() })
            }
            barrier.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    fun exportText(): String {
        flush()
        return synchronized(lock) {
            buildString {
                appendLine("Juanita AI - Voice diagnostic log")
                appendLine("Privacy: no transcript text, prompts, audio, API keys or profile values are recorded.")
                appendLine("Current log:")
                if (file.exists()) append(file.readText())
                val previous = File(file.parentFile, "voice-diagnostic.previous.txt")
                if (previous.exists()) {
                    appendLine()
                    appendLine("Previous rotated log:")
                    append(previous.readText())
                }
                val pendingDrops = droppedEvents.get()
                if (pendingDrops > 0L) {
                    appendLine()
                    appendLine("Diagnostic events not persisted because the queue was full: $pendingDrops")
                }
            }
        }
    }

    private data class DiagnosticEvent(
        val timestamp: String,
        val threadName: String,
        val name: String,
        val detail: String
    )

    companion object {
        private const val TAG = "JuanitaVoiceDiag"
        private const val MAX_BYTES = 1024L * 1024L
        private const val QUEUE_CAPACITY = 256
        private const val EXPORT_FLUSH_TIMEOUT_MS = 3000L
        private val UNSAFE_NAME = Regex("[^A-Za-z0-9_. -]")
        private val API_KEY = Regex("(AIza[0-9A-Za-z_-]{20,})")
        private val URL = Regex("https?://\\S+")
        private val WHITESPACE = Regex("[\\r\\n\\t]+")
    }
}