package ai.juanita.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportBuilderTest {
    @Test
    fun completeReportContainsBuildDeviceAndRuntimeSections() {
        val report = sampleReport()
        assertTrue(report.contains("Application ID: ai.juanita.app"))
        assertTrue(report.contains("Versione: 0.11.18 (build 57)"))
        assertTrue(report.contains("Modello: samsung SM-N975F"))
        assertTrue(report.contains("Android: 12 (API 31)"))
        assertTrue(report.contains("[STATO ED ENERGIA"))
        assertTrue(report.contains("[EVENTI VOCALI REGISTRATI]"))
        assertTrue(report.contains("wake.detected"))
    }

    @Test
    fun reportStatesPrivacyAndDoesNotAddUnprovidedPersonalFields() {
        val report = sampleReport()
        assertTrue(report.contains("Nessun caricamento di rete"))
        assertTrue(report.contains("Non include intenzionalmente conversazioni"))
        assertFalse(report.contains("GPS:"))
        assertFalse(report.contains("Persona attiva:"))
    }

    private fun sampleReport() = DiagnosticReportBuilder.compose(
        generatedAt = "2026-11-24 19:00", appId = "ai.juanita.app",
        appVersion = "0.11.18", appBuild = "57", device = "samsung SM-N975F",
        androidVersion = "12", androidApi = "31", battery = "82%",
        batteryTemperature = "31.0 °C", charging = "A batteria", memory = "PSS 120 MB",
        cpuAverage = "2.1% dall'avvio", thermal = "Normale", activeRecorders = "1",
        liveState = "ACTIVE", wakeWord = "Attiva", service = "ACTIVE", wakeLock = "Acquisito",
        liveDuration = "00:12", voiceLog = "2026-11-24 | service | wake.detected"
    )
}
