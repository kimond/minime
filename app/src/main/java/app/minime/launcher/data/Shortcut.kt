package app.minime.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A home-screen shortcut button. [appKey] is the chosen app ([AppEntry.key]);
 * null means "pick automatically" from [Shortcuts.packageHints] for its icon.
 */
data class Shortcut(
    val id: String,
    val label: String,
    val icon: String,
    val appKey: String? = null,
)

const val MAX_SHORTCUTS = 6

object Shortcuts {
    val defaults = listOf(
        Shortcut("notes", "Notes", "notes"),
        Shortcut("math", "Math", "math"),
        Shortcut("cal", "Cal", "calendar"),
        Shortcut("pomo", "Pomo", "timer"),
        Shortcut("body", "Body", "heart"),
    )

    /** Apps tried, in order, for a shortcut with no app chosen yet. */
    val packageHints: Map<String, List<String>> = mapOf(
        "notes" to listOf(
            "com.google.android.keep", "md.obsidian", "notion.id", "com.samsung.android.app.notes",
            "com.microsoft.office.onenote", "com.evernote",
        ),
        "math" to listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator", "com.miui.calculator"),
        "calendar" to listOf("com.google.android.calendar", "com.samsung.android.calendar", "com.microsoft.office.outlook"),
        "timer" to listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock"),
        "alarm" to listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock"),
        "heart" to listOf(
            "com.google.android.apps.fitness", "com.google.android.apps.healthdata",
            "com.samsung.android.app.health", "com.fitbit.FitbitMobile",
        ),
        "fitness" to listOf("com.google.android.apps.fitness", "com.strava", "com.fitbit.FitbitMobile"),
        "phone" to listOf("com.google.android.dialer", "com.samsung.android.dialer"),
        "message" to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging"),
        "camera" to listOf("com.google.android.GoogleCamera", "com.sec.android.app.camera"),
        "photos" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
        "map" to listOf("com.google.android.apps.maps"),
        "music" to listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
        "mail" to listOf("com.google.android.gm", "com.microsoft.office.outlook"),
        "web" to listOf("com.android.chrome", "org.mozilla.firefox", "com.brave.browser"),
        "settings" to listOf("com.android.settings"),
        "translate" to listOf("com.google.android.apps.translate"),
        "podcast" to listOf("com.google.android.apps.podcasts", "au.com.shiftyjelly.pocketcasts"),
        "wallet" to listOf("com.google.android.apps.walletnfcrel"),
    )

    fun toJson(list: List<Shortcut>): String = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject().put("id", s.id).put("label", s.label).put("icon", s.icon).put("app", s.appKey ?: JSONObject.NULL))
        }
    }.toString()

    fun fromJson(json: String): List<Shortcut>? = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Shortcut(
                id = o.getString("id"),
                label = o.getString("label"),
                icon = o.getString("icon"),
                appKey = if (o.isNull("app")) null else o.getString("app"),
            )
        }
    }.getOrNull()
}
