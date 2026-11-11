package app.minime.launcher.shield

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** Preference keys shared by the settings store and the listener service. */
object ShieldKeys {
    const val PREFS = "minime"
    const val ENABLED = "shield_enabled"
    const val MUTED = "muted_packages"
}

fun hasNotificationAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

data class ShieldedNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val time: Long,
)

/** Notifications the shield dismissed, newest first, kept on-device only. */
object ShieldLog {
    private const val FILE = "minime_shield"
    private const val KEY = "log"
    private const val MAX = 100

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(context: Context): List<ShieldedNotification> = runCatching {
        val array = JSONArray(prefs(context).getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ShieldedNotification(
                key = o.getString("key"),
                packageName = o.getString("pkg"),
                appLabel = o.getString("app"),
                title = o.optString("title"),
                text = o.optString("text"),
                time = o.getLong("time"),
            )
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun add(context: Context, sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)

        val entry = ShieldedNotification(sbn.key, sbn.packageName, label, title, text, sbn.postTime)
        // An updated notification (same key) replaces its earlier entry.
        val list = (listOf(entry) + read(context).filterNot { it.key == sbn.key }).take(MAX)
        write(context, list)
    }

    fun clear(context: Context) = prefs(context).edit { remove(KEY) }

    private fun write(context: Context, list: List<ShieldedNotification>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("key", it.key).put("pkg", it.packageName).put("app", it.appLabel)
                    .put("title", it.title).put("text", it.text).put("time", it.time)
            )
        }
        prefs(context).edit { putString(KEY, array.toString()) }
    }
}
