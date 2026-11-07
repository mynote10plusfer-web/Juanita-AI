package ai.juanita.app.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Volatile, on-device inbox populated only after the user enables Android
 * notification access. Notification text is intentionally not persisted.
 */
data class NotificationEntry(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long
)

object NotificationInbox {
    private val mutableEntries = MutableStateFlow<List<NotificationEntry>>(emptyList())
    val entries: StateFlow<List<NotificationEntry>> = mutableEntries.asStateFlow()

    internal fun add(entry: NotificationEntry) {
        mutableEntries.value = (listOf(entry) + mutableEntries.value.filterNot { it.key == entry.key })
            .sortedByDescending { it.postedAt }
            .take(30)
    }

    internal fun remove(key: String) {
        mutableEntries.value = mutableEntries.value.filterNot { it.key == key }
    }

    fun clear() {
        mutableEntries.value = emptyList()
    }
}

class JuanitaNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            )?.toString()?.trim().orEmpty()

        // Some messaging apps expose individual messages in the MessagingStyle bundle.
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val latestMessage = messages?.lastOrNull()?.let { parcelable ->
            runCatching {
                val bundle = parcelable as? android.os.Bundle
                bundle?.getCharSequence("text")?.toString()
            }.getOrNull()
        }.orEmpty()

        val body = latestMessage.ifBlank { text }
        if (title.isBlank() && body.isBlank()) return

        val appName = runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)

        NotificationInbox.add(
            NotificationEntry(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                title = title.ifBlank { appName },
                text = body,
                postedAt = sbn.postTime
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationInbox.remove(sbn.key)
    }
}
