package ai.juanita.app.notifications
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
object NotificationAccess {
    fun isEnabled(context: Context): Boolean {
        val raw = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        val expected = ComponentName(context, JuanitaNotificationListener::class.java)
        return raw.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.any { it == expected }
    }
}
