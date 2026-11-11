package app.minime.launcher

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.os.Build
import app.minime.launcher.shield.NotificationShieldService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast

fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

fun openAlarms(context: Context) = context.startSafely(Intent(AlarmClock.ACTION_SHOW_ALARMS))

fun openCalendar(context: Context): Boolean =
    context.startSafely(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)) ||
        context.startSafely(
            Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                .appendPath(System.currentTimeMillis().toString()).build())
        )

/** Opens a weather app if one is installed, otherwise a web search for the weather. */
fun openWeather(context: Context): Boolean {
    val pm = context.packageManager
    val app = WEATHER_APPS.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
    return (app != null && context.startSafely(app)) || webSearch(context, "weather")
}

private val WEATHER_APPS = listOf(
    "com.google.android.apps.weather", // Pixel Weather
    "com.samsung.android.weather",
    "com.sec.android.daemonapp",       // older Samsung weather
    "com.miui.weather2",
    "com.oneplus.weather",
    "com.coloros.weather2",
)

/** Opens the system page where the user grants minime notification access. */
fun openNotificationAccessSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, NotificationShieldService::class.java).flattenToString(),
            )
        if (context.startSafely(detail)) return true
    }
    return context.startSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

/** Opens the system "Usage access" page (minime's entry directly where supported). */
fun openUsageAccessSettings(context: Context): Boolean =
    context.startSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))) ||
        context.startSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

fun openDefaultLauncherSettings(context: Context) =
    context.startSafely(Intent(Settings.ACTION_HOME_SETTINGS)) ||
        context.startSafely(Intent(Settings.ACTION_SETTINGS))

fun webSearch(context: Context, query: String) = context.startSafely(
    Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
)

/** No public API for this; every minimal launcher uses the same reflective call. */
@SuppressLint("WrongConstant", "PrivateApi")
fun expandNotificationShade(context: Context) {
    runCatching {
        val service = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager")
            .getMethod("expandNotificationsPanel")
            .invoke(service)
    }
}
