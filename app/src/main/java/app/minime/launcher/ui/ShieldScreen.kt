package app.minime.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.minime.launcher.LauncherViewModel
import app.minime.launcher.data.Search
import app.minime.launcher.data.Settings
import app.minime.launcher.openNotificationAccessSettings
import app.minime.launcher.shield.ShieldedNotification
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val HELD_PREVIEW = 20

@Composable
fun ShieldScreen(vm: LauncherViewModel, settings: Settings) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val apps by vm.drawerApps.collectAsStateWithLifecycle()
    val held by vm.shieldLog.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showAllHeld by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    // Re-check access when coming back from the system settings page.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refreshShieldAccess() }
    }

    // One row per package: notifications are per app package, not per launcher entry.
    val mutableApps = remember(apps, query) { Search.filter(apps.distinctBy { it.component.packageName }, query) }
    val mutedCount = settings.mutedPackages.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
    ) {
        item {
            Text("Notification shield", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Silences the apps you choose. Their notifications are dismissed and kept below.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { toggleShield(vm, settings, context) }
                    .padding(vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Shield", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (mutedCount == 0) "No apps muted yet" else "$mutedCount app${if (mutedCount == 1) "" else "s"} muted",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.shieldEnabled && vm.notificationAccess, onCheckedChange = { toggleShield(vm, settings, context) })
            }
        }

        if (!vm.notificationAccess) {
            item {
                Column(
                    Modifier
                        .padding(vertical = 8.dp)
                        .widgetCard()
                        .clickable { openNotificationAccessSettings(context) }
                        .padding(16.dp),
                ) {
                    Text("Notification access needed", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Tap, then turn on “minime notification shield”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        // --- held back ---
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp)) {
                SectionLabel("Held back" + if (held.isNotEmpty()) " · ${held.size}" else "", Modifier.weight(1f))
                if (held.isNotEmpty()) TextButton(onClick = vm::clearShieldLog) { Text("Clear") }
            }
        }
        if (held.isEmpty()) {
            item { Dim("Nothing held back.") }
        }
        val visibleHeld = if (showAllHeld) held else held.take(HELD_PREVIEW)
        items(visibleHeld, key = { "held-" + it.key + it.time }) { item ->
            HeldRow(item, onClick = { vm.openShielded(item) })
        }
        if (!showAllHeld && held.size > HELD_PREVIEW) {
            item {
                TextButton(onClick = { showAllHeld = true }) { Text("Show all ${held.size}") }
            }
        }

        // --- muted apps ---
        item {
            SectionLabel("Muted apps", Modifier.padding(top = 24.dp, bottom = 8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        items(mutableApps, key = { "mute-" + it.component.packageName }) { app ->
            val pkg = app.component.packageName
            val muted = pkg in settings.mutedPackages
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.setMuted(pkg, !muted) }
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (muted) Color.White else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = muted, onCheckedChange = { vm.setMuted(pkg, it) })
            }
        }
    }
}

private fun toggleShield(vm: LauncherViewModel, settings: Settings, context: android.content.Context) {
    if (!vm.notificationAccess) {
        // Turning it on without access: remember the intent, then send them to grant it.
        vm.setShieldEnabled(true)
        openNotificationAccessSettings(context)
    } else {
        vm.setShieldEnabled(!settings.shieldEnabled)
    }
}

@Composable
private fun HeldRow(item: ShieldedNotification, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            "${item.appLabel.uppercase()} · ${formatTime(item.time)}",
            color = Color(0xFF6B6B6B),
            fontSize = 9.sp,
            letterSpacing = 1.5.sp,
        )
        if (item.title.isNotBlank()) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.text.isNotBlank()) {
            Text(
                item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun formatTime(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return if (time.toLocalDate() == LocalDate.now()) time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    else time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun Dim(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
