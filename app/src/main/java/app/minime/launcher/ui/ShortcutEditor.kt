package app.minime.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.minime.launcher.LauncherViewModel
import app.minime.launcher.data.AppEntry
import app.minime.launcher.data.Search
import app.minime.launcher.data.Shortcut

private const val MAX_LABEL = 8

/** Dialog to set a shortcut's label, app and icon. Hosted at the root so Home and Settings can open it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutEditor(vm: LauncherViewModel, shortcut: Shortcut, onDismiss: () -> Unit) {
    val apps by vm.drawerApps.collectAsStateWithLifecycle()
    val resolved by vm.shortcuts.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isNew = settings.shortcuts.none { it.id == shortcut.id }

    var label by remember(shortcut.id) { mutableStateOf(shortcut.label) }
    var icon by remember(shortcut.id) { mutableStateOf(shortcut.icon) }
    var appKey by remember(shortcut.id) { mutableStateOf(shortcut.appKey) }
    var picking by remember { mutableStateOf(false) }

    val chosenApp = appKey?.let { key -> apps.firstOrNull { it.key == key } }
    val autoApp = resolved.firstOrNull { it.first.id == shortcut.id }?.second.takeIf { appKey == null && icon == shortcut.icon }
    val appText = chosenApp?.label ?: autoApp?.let { "${it.label} (auto)" } ?: "Choose app"
    val colors = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New shortcut" else "Edit shortcut") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(MAX_LABEL) },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                Text("APP", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { picking = true }
                        .padding(vertical = 10.dp),
                ) {
                    Text(appText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Change", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }

                Spacer(Modifier.height(8.dp))
                Text("ICON", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShortcutIcons.forEach { (name, vector) ->
                        val selected = name == icon
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .then(
                                    if (selected) Modifier.background(Color.White, CircleShape)
                                    else Modifier.border(1.dp, Color(0xFF2E2E2E), CircleShape)
                                )
                                .clickable { icon = name },
                        ) {
                            Icon(vector, contentDescription = name, tint = if (selected) Color.Black else Color(0xFFBDBDBD),
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank(),
                onClick = {
                    vm.saveShortcut(shortcut.copy(label = label.trim(), icon = icon, appKey = appKey))
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    TextButton(onClick = {
                        vm.removeShortcut(shortcut.id)
                        onDismiss()
                    }) { Text("Remove") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (picking) {
        AppPickerDialog(
            apps = apps,
            onPick = { app ->
                appKey = app.key
                if (label.isBlank()) label = app.label.take(MAX_LABEL)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun AppPickerDialog(apps: List<AppEntry>, onPick: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(apps, query) { Search.filter(apps, query) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(results, key = { it.key }) { app ->
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
