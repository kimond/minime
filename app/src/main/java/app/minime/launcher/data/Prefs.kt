package app.minime.launcher.data

import android.content.Context
import androidx.core.content.edit
import app.minime.launcher.shield.ShieldKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class HomeAlignment(val label: String) {
    START("Left"), CENTER("Center"), END("Right");

    fun next(): HomeAlignment = entries[(ordinal + 1) % entries.size]
}

data class Settings(
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showWeather: Boolean = true,
    val showShortcuts: Boolean = true,
    val showScreenTime: Boolean = true,
    /** Optional swipe-right behaviour analysis; off until the user turns it on. */
    val insightsEnabled: Boolean = false,
    val shieldEnabled: Boolean = false,
    val mutedPackages: Set<String> = emptySet(),
    val shortcuts: List<Shortcut> = Shortcuts.defaults,
    val fahrenheit: Boolean = Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW"),
    val showWallpaper: Boolean = false,
    val alignment: HomeAlignment = HomeAlignment.START,
    val autoKeyboard: Boolean = true,
    val autoLaunch: Boolean = true,
)

/** Small SharedPreferences-backed store that exposes settings as a StateFlow. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences(ShieldKeys.PREFS, Context.MODE_PRIVATE)
    private val renameSp = context.getSharedPreferences("minime_renames", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read())
    val settings: StateFlow<Settings> = state.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val old = state.value
        val new = transform(old)
        if (new == old) return
        write(old, new)
        state.value = new
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            favorites = sp.getString(FAVORITES, "").orEmpty().split('\n').filter { it.isNotBlank() },
            hidden = sp.getStringSet(HIDDEN, emptySet()).orEmpty().toSet(),
            renames = renameSp.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap(),
            showClock = sp.getBoolean(SHOW_CLOCK, d.showClock),
            showDate = sp.getBoolean(SHOW_DATE, d.showDate),
            showWeather = sp.getBoolean(SHOW_WEATHER, d.showWeather),
            showShortcuts = sp.getBoolean(SHOW_SHORTCUTS, d.showShortcuts),
            showScreenTime = sp.getBoolean(SHOW_SCREEN_TIME, d.showScreenTime),
            insightsEnabled = sp.getBoolean(INSIGHTS, d.insightsEnabled),
            shieldEnabled = sp.getBoolean(ShieldKeys.ENABLED, d.shieldEnabled),
            mutedPackages = sp.getStringSet(ShieldKeys.MUTED, emptySet()).orEmpty().toSet(),
            shortcuts = sp.getString(SHORTCUTS, null)?.let(Shortcuts::fromJson) ?: d.shortcuts,
            fahrenheit = sp.getBoolean(FAHRENHEIT, d.fahrenheit),
            showWallpaper = sp.getBoolean(SHOW_WALLPAPER, d.showWallpaper),
            alignment = sp.getString(ALIGNMENT, null)
                ?.let { name -> HomeAlignment.entries.firstOrNull { it.name == name } } ?: d.alignment,
            autoKeyboard = sp.getBoolean(AUTO_KEYBOARD, d.autoKeyboard),
            autoLaunch = sp.getBoolean(AUTO_LAUNCH, d.autoLaunch),
        )
    }

    private fun write(old: Settings, s: Settings) {
        sp.edit {
            putString(FAVORITES, s.favorites.joinToString("\n"))
            putStringSet(HIDDEN, s.hidden)
            putBoolean(SHOW_CLOCK, s.showClock)
            putBoolean(SHOW_DATE, s.showDate)
            putBoolean(SHOW_WEATHER, s.showWeather)
            putBoolean(SHOW_SHORTCUTS, s.showShortcuts)
            putBoolean(SHOW_SCREEN_TIME, s.showScreenTime)
            putBoolean(INSIGHTS, s.insightsEnabled)
            putBoolean(ShieldKeys.ENABLED, s.shieldEnabled)
            putStringSet(ShieldKeys.MUTED, s.mutedPackages)
            putString(SHORTCUTS, Shortcuts.toJson(s.shortcuts))
            putBoolean(FAHRENHEIT, s.fahrenheit)
            putBoolean(SHOW_WALLPAPER, s.showWallpaper)
            putString(ALIGNMENT, s.alignment.name)
            putBoolean(AUTO_KEYBOARD, s.autoKeyboard)
            putBoolean(AUTO_LAUNCH, s.autoLaunch)
        }
        if (old.renames != s.renames) {
            renameSp.edit {
                clear()
                s.renames.forEach { (k, v) -> putString(k, v) }
            }
        }
    }

    private companion object {
        const val FAVORITES = "favorites"
        const val HIDDEN = "hidden"
        const val SHOW_CLOCK = "show_clock"
        // New key so the date line starts on, even for people who hid the old under-clock date.
        const val SHOW_DATE = "show_date_line"
        const val SHOW_WEATHER = "show_weather"
        const val SHOW_SHORTCUTS = "show_shortcuts"
        const val SHOW_SCREEN_TIME = "show_screen_time"
        const val INSIGHTS = "insights_enabled"
        const val SHORTCUTS = "shortcuts"
        const val FAHRENHEIT = "fahrenheit"
        const val SHOW_WALLPAPER = "show_wallpaper"
        const val ALIGNMENT = "alignment"
        const val AUTO_KEYBOARD = "auto_keyboard"
        const val AUTO_LAUNCH = "auto_launch"
    }
}
