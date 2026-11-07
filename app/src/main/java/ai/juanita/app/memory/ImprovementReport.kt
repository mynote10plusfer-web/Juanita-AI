package ai.juanita.app.memory

data class ImprovementReport(
    val id: String,
    val title: String,
    val type: String,
    val area: String,
    val observed: String,
    val evidence: String,
    val expectedOutcome: String,
    val acceptanceTest: String,
    val createdAtMillis: Long,
    val timeZoneId: String,
    val status: String
)
