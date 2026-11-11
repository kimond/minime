package app.minime.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.minime.launcher.BuildConfig
import app.minime.launcher.LauncherViewModel
import app.minime.launcher.data.MAX_SHORTCUTS
import app.minime.launcher.data.Settings
import app.minime.launcher.openDefaultLauncherSettings
import app.minime.launcher.openUsageAccessSettings

@Composable
fun SettingsScreen(vm: LauncherViewModel, settings: Settings) {
    val context = LocalContext.current
    val hidden by vm.hiddenApps.collectAsStateWithLifecycle()
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        item { ValueRow("Set as default launcher", "›") { openDefaultLauncherSettings(context) } }

        item {
            ValueRow("Notification shield", if (settings.shieldEnabled) "On · ${settings.mutedPackages.size} muted" else "Off") {
                vm.openShield()
            }
        }

        item { SectionTitle("Home") }
        item { ToggleRow("Show clock", settings.showClock) { v -> vm.updateSettings { it.copy(showClock = v) } } }
        item { ToggleRow("Show date", settings.showDate) { v -> vm.updateSettings { it.copy(showDate = v) } } }
        item { ToggleRow("Show weather", settings.showWeather) { v -> vm.updateSettings { it.copy(showWeather = v) } } }
        item {
            ValueRow("Temperature unit", if (settings.fahrenheit) "°F" else "°C") {
                vm.updateSettings { it.copy(fahrenheit = !it.fahrenheit) }
            }
        }
        item { ToggleRow("Show wallpaper", settings.showWallpaper) { v -> vm.updateSettings { it.copy(showWallpaper = v) } } }
        item {
            ValueRow("Alignment", settings.alignment.label) {
                vm.updateSettings { it.copy(alignment = it.alignment.next()) }
            }
        }

        item { SectionTitle("Shortcuts") }
        item { ToggleRow("Show shortcuts", settings.showShortcuts) { v -> vm.updateSettings { it.copy(showShortcuts = v) } } }
        items(shortcuts, key = { "sc-" + it.first.id }) { (shortcut, app) ->
            ValueRow(shortcut.label, app?.label ?: "No app") { vm.editShortcut(shortcut) }
        }
        if (shortcuts.size < MAX_SHORTCUTS) {
            item { ValueRow("Add shortcut", "+") { vm.newShortcut() } }
        }

        item { SectionTitle("Screen time") }
        item {
            ToggleRow("Show time spent per app", settings.showScreenTime) { v ->
                vm.updateSettings { it.copy(showScreenTime = v) }
            }
        }
        item {
            ToggleRow("Behaviour insights (swipe right)", settings.insightsEnabled) { v ->
                vm.updateSettings { it.copy(insightsEnabled = v) }
            }
        }
        if ((settings.showScreenTime || settings.insightsEnabled) && !vm.usageAccess) {
            item { ValueRow("Allow usage access", "›") { openUsageAccessSettings(context) } }
        }

        item { SectionTitle("App list") }
        item {
            ToggleRow("Open keyboard automatically", settings.autoKeyboard) { v ->
                vm.updateSettings { it.copy(autoKeyboard = v) }
            }
        }
        item {
            ToggleRow("Open app when it's the only match", settings.autoLaunch) { v ->
                vm.updateSettings { it.copy(autoLaunch = v) }
            }
        }

        item { SectionTitle("Hidden apps") }
        if (hidden.isEmpty()) {
            item {
                Text(
                    "None. Long-press an app in the list to hide it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        items(hidden, key = { it.key }) { app ->
            ValueRow(app.label, "Unhide") { vm.setHidden(app, false) }
        }

        item {
            Text(
                "minime ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
