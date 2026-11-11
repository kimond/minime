package app.minime.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

/** Today's foreground time per app, from the system's usage events (needs Usage access). */
class UsageRepository(private val context: Context) {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Milliseconds each package spent in the foreground since local midnight. */
    fun todayByPackage(): Map<String, Long> {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val totals = HashMap<String, Long>()
        read(start, System.currentTimeMillis()).sessions.forEach { s ->
            totals[s.packageName] = (totals[s.packageName] ?: 0L) + s.length
        }
        return totals
    }

    /**
     * Foreground sessions and unlocks between [from] and [to].
     *
     * Uses a single-foreground model: only one app is in front at a time, so a session ends
     * when its app pauses or stops, when another app comes forward, or when the screen turns
     * off / locks / the phone shuts down. This stays correct even when Android drops a pause
     * event (which otherwise leaves an app "open" for the rest of the day).
     * Sessions of the same app separated by a hop between its own screens are merged.
     */
    fun read(from: Long, to: Long): UsageLog {
        if (!hasAccess()) return UsageLog(emptyList(), emptyList())
        val events = usageStats.queryEvents(from, to) ?: return UsageLog(emptyList(), emptyList())

        val sessions = ArrayList<Session>()
        val unlocks = ArrayList<Long>()
        val screenOns = ArrayList<Long>()
        var current: String? = null
        val visible = HashSet<String>() // the current app's screens that are showing right now
        var since = 0L
        var screenOn = true
        val event = UsageEvents.Event()

        fun close(at: Long) {
            val pkg = current ?: return
            if (at > since) {
                val last = sessions.lastOrNull()
                if (last != null && last.packageName == pkg && since - last.end <= MERGE_GAP_MS) {
                    sessions[sessions.lastIndex] = last.copy(end = at)
                } else {
                    sessions += Session(pkg, since, at)
                }
            }
            current = null
            visible.clear()
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val t = event.timeStamp
            when (event.eventType) {
                ACTIVITY_RESUMED -> {
                    val pkg = event.packageName ?: continue
                    if (pkg != current) {
                        close(t)
                        current = pkg
                        since = t
                    }
                    visible += event.className.orEmpty()
                }
                // Only the screen that's actually showing can end the session. Apps report the
                // previous screen as paused/stopped *after* the next one opens; ignore those.
                ACTIVITY_PAUSED, ACTIVITY_STOPPED -> if (event.packageName == current) {
                    if (visible.remove(event.className.orEmpty()) && visible.isEmpty()) close(t)
                }
                SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN, DEVICE_SHUTDOWN -> {
                    close(t)
                    screenOn = false
                }
                SCREEN_INTERACTIVE -> {
                    screenOns += t
                    screenOn = true
                }
                KEYGUARD_HIDDEN -> unlocks += t
            }
        }
        // Whatever is in front now is still being used, if the screen is on.
        if (screenOn) close(to) else current = null

        sessions.removeAll { it.packageName in IGNORED || it.packageName == context.packageName }
        // Phones without a lock screen never report unlocks; fall back to screen-on.
        return UsageLog(sessions, unlocks.ifEmpty { screenOns })
    }

    private companion object {
        // UsageEvents.Event.ACTIVITY_RESUMED / ACTIVITY_PAUSED (same values as the pre-Q MOVE_TO_* constants).
        const val ACTIVITY_RESUMED = 1
        const val ACTIVITY_PAUSED = 2
        const val SCREEN_INTERACTIVE = 15     // UsageEvents.Event.* (API 28+)
        const val SCREEN_NON_INTERACTIVE = 16
        const val KEYGUARD_SHOWN = 17
        const val KEYGUARD_HIDDEN = 18
        const val ACTIVITY_STOPPED = 23
        const val DEVICE_SHUTDOWN = 26
        const val MERGE_GAP_MS = 3_000L

        /** System surfaces that aren't "apps you use": lock screen, shade, permission dialogs. */
        val IGNORED = setOf(
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.google.android.apps.nexuslauncher",
            "android",
        )
    }
}

data class Session(val packageName: String, val start: Long, val end: Long) {
    val length: Long get() = end - start

    /** How much of this session falls inside [from, to). */
    fun overlap(from: Long, to: Long): Long = (minOf(end, to) - maxOf(start, from)).coerceAtLeast(0L)
}

data class UsageLog(val sessions: List<Session>, val unlocks: List<Long>)

/** "1h 12m", "24m", or null under a minute. */
fun formatScreenTime(millis: Long?): String? {
    val minutes = (millis ?: 0L) / 60_000L
    if (minutes < 1) return null
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "${m}m"
        m == 0L -> "${h}h"
        else -> "${h}h ${m}m"
    }
}
