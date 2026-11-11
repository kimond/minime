package app.minime.launcher.shield

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Dismisses notifications from muted apps while the shield is on, and logs them so they
 * can be reviewed later. Ongoing notifications (media, calls, navigation) are left alone.
 */
class NotificationShieldService : NotificationListenerService() {
    private var prefs: SharedPreferences? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ShieldKeys.ENABLED || key == ShieldKeys.MUTED) sweep()
    }

    override fun onListenerConnected() {
        prefs = getSharedPreferences(ShieldKeys.PREFS, Context.MODE_PRIVATE).also {
            it.registerOnSharedPreferenceChangeListener(prefListener)
        }
        sweep()
    }

    override fun onListenerDisconnected() {
        prefs?.unregisterOnSharedPreferenceChangeListener(prefListener)
        prefs = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (shouldBlock(sbn)) block(sbn)
    }

    /** Clears notifications already in the shade when the shield is turned on or an app is muted. */
    private fun sweep() {
        runCatching { activeNotifications?.filter(::shouldBlock)?.forEach(::block) }
    }

    private fun block(sbn: StatusBarNotification) {
        cancelNotification(sbn.key)
        val isSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (!isSummary) ShieldLog.add(this, sbn)
    }

    private fun shouldBlock(sbn: StatusBarNotification): Boolean {
        val p = prefs ?: return false
        if (!p.getBoolean(ShieldKeys.ENABLED, false)) return false
        if (sbn.isOngoing || sbn.packageName == packageName) return false
        return sbn.packageName in p.getStringSet(ShieldKeys.MUTED, emptySet()).orEmpty()
    }
}
