package ai.juanita.app.diagnostics

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceDiagnosticLogTest {
    @Test
    fun queuedEventsAreExportedAndSensitiveValuesAreRedacted() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "voice-diagnostic-test-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(target) {
            override fun getFilesDir(): File = directory
            override fun getApplicationContext(): Context = this
        }
        val logger = VoiceDiagnosticLog(isolated)
        val apiKey = "AIza123456789012345678901234567890"
        val url = "https://private.example/path?token=secret"
        try {
            logger.event("test_event", "key=$apiKey url=$url")
            val exported = logger.exportText()

            assertTrue("The queued diagnostic event must be flushed before export", exported.contains("test_event"))
            assertTrue("API keys must be redacted before persistence", exported.contains("[REDACTED_KEY]"))
            assertTrue("URLs must be redacted before persistence", exported.contains("[REDACTED_URL]"))
            assertFalse("The original API key must never appear in the export", exported.contains(apiKey))
            assertFalse("The original URL must never appear in the export", exported.contains(url))
            assertTrue("The log must identify the originating callback thread", exported.contains(Thread.currentThread().name))
        } finally {
            directory.deleteRecursively()
        }
    }
}
