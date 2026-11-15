package ai.juanita.app.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant

/** Privacy-safe voice diagnostics: technical events only, never transcript or audio content. */
class VoiceDiagnosticLog(context: Context) {
    private val file = File(context.applicationContext.filesDir, "diagnostics/voice-diagnostic.txt")
    private val lock = Any()

    fun event(name: String, detail: String = "") {
        val safeName = name.replace(Regex("[^A-Za-z0-9_. -]"), "_").take(90)
        val safeDetail = detail
            .replace(Regex("(AIza[0-9A-Za-z_-]{20,})"), "[REDACTED_KEY]")
            .replace(Regex("https?://\\S+"), "[REDACTED_URL]")
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .take(240)
        synchronized(lock) {
            runCatching {
                file.parentFile?.mkdirs()
                if (file.exists() && file.length() > MAX_BYTES) {
                    val previous = File(file.parentFile, "voice-diagnostic.previous.txt")
                    previous.delete()
                    file.renameTo(previous)
                }
                file.appendText("${Instant.now()} | ${Thread.currentThread().name} | $safeName | $safeDetail\n")
            }.onFailure { Log.w(TAG, "Unable to append voice diagnostic event", it) }
        }
    }

    fun exportText(): String = synchronized(lock) {
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
        }
    }

    companion object {
        private const val TAG = "JuanitaVoiceDiag"
        private const val MAX_BYTES = 1024L * 1024L
    }
}
