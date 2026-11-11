package app.minime.launcher

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.minime.launcher.data.AppEntry
import app.minime.launcher.data.AppRepository
import app.minime.launcher.data.BehaviorAnalyzer
import app.minime.launcher.data.BehaviorReport
import app.minime.launcher.data.Prefs
import app.minime.launcher.data.Search
import app.minime.launcher.data.MAX_SHORTCUTS
import app.minime.launcher.data.Settings
import app.minime.launcher.data.Shortcut
import app.minime.launcher.data.Shortcuts
import app.minime.launcher.data.UsageRepository
import app.minime.launcher.data.Weather
import app.minime.launcher.data.WeatherRepository
import app.minime.launcher.shield.ShieldLog
import app.minime.launcher.shield.ShieldedNotification
import app.minime.launcher.shield.hasNotificationAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.Collator
import java.util.Collections
import java.util.UUID

enum class Screen { HOME, DRAWER, SETTINGS, SHIELD, INSIGHTS }

const val MAX_FAVORITES = 9
private const val WEATHER_MAX_AGE_MS = 30L * 60 * 1000

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AppRepository(application)
    private val prefs = Prefs(application)
    val settings: StateFlow<Settings> = prefs.settings

    private val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    /** Every launchable app with renames applied, alphabetical. */
    private val allApps: StateFlow<List<AppEntry>> =
        combine(repo.appsFlow(), settings.map { it.renames }.distinctUntilChanged()) { apps, renames ->
            apps.map { app -> renames[app.key]?.let { app.copy(label = it) } ?: app }
                .sortedWith(compareBy(collator) { it.label })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val drawerApps: StateFlow<List<AppEntry>> =
        combine(allApps, settings) { apps, s -> apps.filterNot { it.key in s.hidden } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenApps: StateFlow<List<AppEntry>> =
        combine(allApps, settings) { apps, s -> apps.filter { it.key in s.hidden } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteApps: StateFlow<List<AppEntry>> =
        combine(allApps, settings) { apps, s ->
            val byKey = apps.associateBy { it.key }
            s.favorites.mapNotNull { byKey[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Each shortcut paired with the app it opens (chosen, or auto-matched by package), if any. */
    val shortcuts: StateFlow<List<Pair<Shortcut, AppEntry?>>> =
        combine(allApps, settings) { apps, s ->
            val byKey = apps.associateBy { it.key }
            val byPackage = apps.filterNot { it.isWorkProfile }.associateBy { it.component.packageName }
            s.shortcuts.map { sc ->
                val app = if (sc.appKey != null) byKey[sc.appKey]
                else Shortcuts.packageHints[sc.icon]?.firstNotNullOfOrNull { byPackage[it] }
                sc to app
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var screen by mutableStateOf(Screen.HOME)
        private set
    var query by mutableStateOf("")
        private set

    // --- navigation -------------------------------------------------------

    fun openDrawer() {
        query = ""
        screen = Screen.DRAWER
    }

    fun openSettings() {
        screen = Screen.SETTINGS
    }

    fun openShield() {
        refreshShieldAccess()
        screen = Screen.SHIELD
    }

    fun goHome() {
        screen = Screen.HOME
        query = ""
    }

    fun onBack() {
        if (screen != Screen.HOME) goHome()
    }

    /** Called when the launcher goes to the background (e.g. an app was opened). */
    fun onStopped() {
        if (screen == Screen.DRAWER) goHome()
    }

    // --- search & launch --------------------------------------------------

    fun onQueryChange(newQuery: String) {
        query = newQuery
        if (settings.value.autoLaunch && newQuery.isNotBlank()) {
            val results = Search.filter(drawerApps.value, newQuery)
            if (results.size == 1) launch(results.first())
        }
    }

    fun launch(app: AppEntry) {
        if (!repo.launch(app)) getApplication<Application>().toast("Can't open ${app.label}")
    }

    fun openAppInfo(app: AppEntry) {
        repo.openAppInfo(app)
    }

    fun uninstall(app: AppEntry) {
        repo.uninstall(app)
    }

    // --- weather ----------------------------------------------------------

    private val weatherRepo = WeatherRepository(application)
    private val _weather = MutableStateFlow(weatherRepo.cached())
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    var locationPermission by mutableStateOf(weatherRepo.hasPermission())
        private set
    var weatherFailed by mutableStateOf(false)
        private set
    private var weatherJob: Job? = null

    /** Fetches weather if the cached copy is stale (30 min), in the wrong unit, or [force] is set. */
    fun refreshWeather(force: Boolean = false) {
        locationPermission = weatherRepo.hasPermission()
        val s = settings.value
        if (!locationPermission || !s.showWeather || weatherJob?.isActive == true) return
        val current = _weather.value
        val fresh = current != null && current.fahrenheit == s.fahrenheit &&
            System.currentTimeMillis() - current.fetchedAt < WEATHER_MAX_AGE_MS
        if (fresh && !force) return
        weatherJob = viewModelScope.launch {
            val result = weatherRepo.fetch(s.fahrenheit)
            if (result != null) _weather.value = result
            weatherFailed = result == null
        }
    }

    // --- shortcuts --------------------------------------------------------

    /** Shortcut open in the editor dialog, or null. */
    var editingShortcut by mutableStateOf<Shortcut?>(null)
        private set

    fun editShortcut(shortcut: Shortcut) {
        editingShortcut = shortcut
    }

    fun newShortcut() {
        if (settings.value.shortcuts.size >= MAX_SHORTCUTS) {
            getApplication<Application>().toast("Up to $MAX_SHORTCUTS shortcuts")
            return
        }
        editingShortcut = Shortcut(id = UUID.randomUUID().toString(), label = "", icon = "notes")
    }

    fun closeShortcutEditor() {
        editingShortcut = null
    }

    fun saveShortcut(shortcut: Shortcut) = prefs.update { s ->
        val list = s.shortcuts
        s.copy(
            shortcuts = if (list.any { it.id == shortcut.id }) list.map { if (it.id == shortcut.id) shortcut else it }
            else list + shortcut
        )
    }

    fun removeShortcut(id: String) = prefs.update { s -> s.copy(shortcuts = s.shortcuts.filterNot { it.id == id }) }

    fun moveShortcut(id: String, delta: Int) = prefs.update { s ->
        val list = s.shortcuts.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) s
        else {
            Collections.swap(list, from, to)
            s.copy(shortcuts = list)
        }
    }

    // --- screen time ------------------------------------------------------

    private val usageRepo = UsageRepository(application)
    private val _screenTime = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Today's foreground milliseconds per package name. */
    val screenTime: StateFlow<Map<String, Long>> = _screenTime.asStateFlow()

    var usageAccess by mutableStateOf(usageRepo.hasAccess())
        private set
    private var usageJob: Job? = null

    fun refreshScreenTime() {
        usageAccess = usageRepo.hasAccess()
        if (!usageAccess || !settings.value.showScreenTime) {
            _screenTime.value = emptyMap()
            return
        }
        if (usageJob?.isActive == true) return
        usageJob = viewModelScope.launch {
            _screenTime.value = withContext(Dispatchers.IO) {
                runCatching { usageRepo.todayByPackage() }.getOrDefault(emptyMap())
            }
        }
    }

    // --- behaviour insights (swipe right) ---------------------------------

    private val analyzer = BehaviorAnalyzer(usageRepo, ::labelOf)
    private val _insights = MutableStateFlow<BehaviorReport?>(null)
    val insights: StateFlow<BehaviorReport?> = _insights.asStateFlow()
    private var insightsJob: Job? = null

    /** Swipe right on home. Insights are opt-in, so point to Settings when they're off. */
    fun openInsights() {
        if (!settings.value.insightsEnabled) {
            getApplication<Application>().toast("Turn on Insights in Settings to use swipe right")
            return
        }
        screen = Screen.INSIGHTS
        refreshInsights()
    }

    fun refreshInsights() {
        usageAccess = usageRepo.hasAccess()
        if (!usageAccess || insightsJob?.isActive == true) return
        insightsJob = viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { analyzer.analyze() }.getOrNull() }
                ?.let { _insights.value = it }
        }
    }

    private fun labelOf(packageName: String): String {
        allApps.value.firstOrNull { it.component.packageName == packageName && !it.isWorkProfile }?.let { return it.label }
        val pm = getApplication<Application>().packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
            .getOrDefault(packageName)
    }

    // --- notification shield ----------------------------------------------

    private val shieldPrefs = ShieldLog.prefs(application)
    private val _shieldLog = MutableStateFlow(ShieldLog.read(application))
    /** Notifications the shield dismissed, newest first. */
    val shieldLog: StateFlow<List<ShieldedNotification>> = _shieldLog.asStateFlow()

    // Held as a field: SharedPreferences only keeps weak references to listeners.
    private val shieldLogListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _shieldLog.value = ShieldLog.read(getApplication())
    }

    init {
        shieldPrefs.registerOnSharedPreferenceChangeListener(shieldLogListener)
    }

    var notificationAccess by mutableStateOf(hasNotificationAccess(application))
        private set

    fun refreshShieldAccess() {
        notificationAccess = hasNotificationAccess(getApplication())
    }

    fun setShieldEnabled(enabled: Boolean) = prefs.update { it.copy(shieldEnabled = enabled) }

    fun setMuted(packageName: String, muted: Boolean) = prefs.update { s ->
        s.copy(mutedPackages = if (muted) s.mutedPackages + packageName else s.mutedPackages - packageName)
    }

    fun clearShieldLog() = ShieldLog.clear(getApplication())

    /** Opens the app a held-back notification came from. */
    fun openShielded(item: ShieldedNotification) {
        val app = allApps.value.firstOrNull { it.component.packageName == item.packageName && !it.isWorkProfile }
            ?: allApps.value.firstOrNull { it.component.packageName == item.packageName }
        if (app != null) launch(app) else getApplication<Application>().toast("Can't open ${item.appLabel}")
    }

    override fun onCleared() {
        shieldPrefs.unregisterOnSharedPreferenceChangeListener(shieldLogListener)
    }

    // --- customisation ----------------------------------------------------

    fun toggleFavorite(app: AppEntry) {
        val s = settings.value
        if (app.key in s.favorites) {
            prefs.update { it.copy(favorites = it.favorites - app.key) }
        } else if (favoriteApps.value.size >= MAX_FAVORITES) {
            getApplication<Application>().toast("Home screen is full ($MAX_FAVORITES apps)")
        } else {
            // Drop stale entries for uninstalled apps while we're at it.
            val live = favoriteApps.value.map { it.key }
            prefs.update { it.copy(favorites = live + app.key) }
        }
    }

    fun moveFavorite(app: AppEntry, delta: Int) = prefs.update { s ->
        val list = s.favorites.toMutableList()
        val from = list.indexOf(app.key)
        val to = from + delta
        if (from < 0 || to !in list.indices) s
        else {
            Collections.swap(list, from, to)
            s.copy(favorites = list)
        }
    }

    fun setHidden(app: AppEntry, hidden: Boolean) = prefs.update { s ->
        if (hidden) s.copy(hidden = s.hidden + app.key, favorites = s.favorites - app.key)
        else s.copy(hidden = s.hidden - app.key)
    }

    fun rename(app: AppEntry, newLabel: String) = prefs.update { s ->
        val label = newLabel.trim()
        if (label.isEmpty() || label == app.originalLabel) s.copy(renames = s.renames - app.key)
        else s.copy(renames = s.renames + (app.key to label))
    }

    fun updateSettings(transform: (Settings) -> Settings) = prefs.update(transform)
}
