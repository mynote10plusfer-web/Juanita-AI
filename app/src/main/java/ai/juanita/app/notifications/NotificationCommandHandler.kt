package ai.juanita.app.notifications
import android.content.Context
class NotificationCommandHandler(context: Context) {
    private val appContext = context.applicationContext
    fun execute(name: String, args: Map<String, String>): String {
        if (!NotificationAccess.isEnabled(appContext)) return "L'accesso speciale alle notifiche non è attivo. Apri Impostazioni > Accesso alle notifiche e abilita Juanita."
        if (!NotificationInbox.connected.value) return "L'accesso è abilitato ma il servizio Android non è ancora connesso. Attendi qualche secondo e riprova."
        return when (name) {
            "listNotificationApps" -> listApps()
            "readRecentNotifications" -> readSelectedApp(args)
            else -> "Comando notifiche non riconosciuto."
        }
    }
    private fun listApps(): String {
        val grouped = NotificationInbox.entries.value.groupBy { it.packageName }.map { (pkg, entries) ->
            "${entries.first().appName} [app_id=$pkg] · ${entries.size} notifiche"
        }.sorted()
        return if (grouped.isEmpty()) "Non ci sono notifiche attive con contenuto leggibile. Alcune app nascondono le anteprime."
        else "APP_INVENTORY (gli app_id sono identificatori tecnici, non pronunciarli):\n" + grouped.joinToString("\n")
    }
    private fun readSelectedApp(args: Map<String, String>): String {
        val consent = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CLOUD_READ_CONSENT, false)
        if (!consent) return "La lettura dei contenuti verso Gemini non è autorizzata. Chiedi all'utente di attivare il consenso separato nelle impostazioni di Juanita."
        val pkg = args["package_name"].orEmpty().trim()
        if (pkg.isBlank()) return "Manca l'app da leggere. Prima mostra l'elenco e chiedi quale app desidera."
        val limit = args["limit"]?.toIntOrNull()?.coerceIn(1, 5) ?: 3
        val selected = NotificationInbox.entries.value.filter { it.packageName == pkg }.sortedByDescending { it.postedAt }.take(limit)
        if (selected.isEmpty()) return "Non trovo notifiche leggibili per l'app selezionata. Non cercare in altre app."
        val safe = selected.filterNot { OTP_PATTERN.containsMatchIn(it.title + " " + it.text) }
        if (safe.isEmpty()) return "Le notifiche disponibili sembrano contenere codici di sicurezza o verifica; non le leggo."
        val body = safe.joinToString("\n") { e -> "${e.appName} — ${e.title.take(180)}: ${e.text.take(1200).ifBlank { "(anteprima del messaggio non disponibile)" }}" }
        return PRIVATE_CONTENT_MARKER + body
    }
    companion object {
        const val PRIVATE_CONTENT_MARKER = "__JUANITA_PRIVATE_NOTIFICATION_CONTENT__"
        private const val PREFS = "juanita_notification_settings"
        private const val KEY_CLOUD_READ_CONSENT = "cloud_read_consent"
        private val OTP_PATTERN = Regex("(?i)\\b(otp|one[- ]time password|verification code|security code|codice di verifica|codice di sicurezza|codice otp)\\b")
    }
}
