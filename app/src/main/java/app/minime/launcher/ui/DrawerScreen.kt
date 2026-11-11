package app.minime.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.minime.launcher.LauncherViewModel
import app.minime.launcher.data.AppEntry
import app.minime.launcher.data.Search
import app.minime.launcher.data.Settings
import app.minime.launcher.data.formatScreenTime
import app.minime.launcher.openUsageAccessSettings
import app.minime.launcher.webSearch
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter

@Composable
fun DrawerScreen(vm: LauncherViewModel, settings: Settings) {
    val context = LocalContext.current
    val apps by vm.drawerApps.collectAsStateWithLifecycle()
    val query = vm.query
    val results = remember(apps, query) { Search.filter(apps, query) }
    val screenTime by vm.screenTime.collectAsStateWithLifecycle()
    val totalToday = remember(screenTime) { formatScreenTime(screenTime.values.sum()) }
    var renaming by remember { mutableStateOf<AppEntry?>(null) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val closeThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val pullToClose = remember(closeThreshold) { PullToClose(closeThreshold, vm::goHome) }

    LaunchedEffect(Unit) {
        if (settings.autoKeyboard) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
    // Scrolling the list means you're browsing, not typing.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { keyboard?.hide() }
    }
    LaunchedEffect(query) { listState.scrollToItem(0) }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 32.dp),
    ) {
        SearchField(
            value = query,
            onValueChange = vm::onQueryChange,
            onGo = {
                val first = results.firstOrNull()
                if (first != null) vm.launch(first) else if (query.isNotBlank()) webSearch(context, query)
            },
            focusRequester = focusRequester,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )

        // Today's total, or a one-tap prompt to enable screen time.
        if (settings.showScreenTime && query.isBlank()) {
            val label = when {
                !vm.usageAccess -> "SHOW SCREEN TIME  ·  TAP TO ALLOW"
                totalToday != null -> "TODAY  ·  $totalToday"
                else -> null
            }
            if (label != null) {
                Text(
                    label,
                    color = Color(0xFF6B6B6B),
                    fontSize = 9.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier
                        .padding(start = 4.dp, top = 10.dp)
                        .then(
                            if (!vm.usageAccess) Modifier.clickable { openUsageAccessSettings(context) } else Modifier
                        ),
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(pullToClose),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(results, key = { it.key }) { app ->
                val pinned = app.key in settings.favorites
                AppItem(
                    app = app,
                    style = MaterialTheme.typography.titleLarge,
                    onClick = { vm.launch(app) },
                    trailing = if (app.isWorkProfile) null else formatScreenTime(screenTime[app.component.packageName]),
                    menuActions = {
                        listOf(
                            MenuAction(if (pinned) "Remove from home" else "Add to home") { vm.toggleFavorite(app) },
                            MenuAction("Rename") { renaming = app },
                            MenuAction("Hide") { vm.setHidden(app, true) },
                            MenuAction("App info") { vm.openAppInfo(app) },
                            MenuAction("Uninstall") { vm.uninstall(app) },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                )
            }
            if (results.isEmpty() && query.isNotBlank()) {
                item {
                    Text(
                        "Search the web for “$query”",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { webSearch(context, query) }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }

    renaming?.let { app ->
        RenameDialog(
            app = app,
            onDismiss = { renaming = null },
            onConfirm = {
                vm.rename(app, it)
                renaming = null
            },
        )
    }
}

/** Rounded pill search field with a search glyph and a clear button, matching the status pills. */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onGo: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(if (focused) Color(0xFF4A4A4A) else Color(0xFF2A2A2A), label = "searchBorder")
    val textStyle = TextStyle(color = Color.White, fontSize = 17.sp, letterSpacing = 0.3.sp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(Color.White),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0xFF0B0B0B), RoundedCornerShape(50))
                    .border(1.dp, border, RoundedCornerShape(50))
                    .padding(start = 18.dp, end = 8.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = Color(0xFF7A7A7A), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text("Search apps", style = textStyle.copy(color = Color(0xFF5A5A5A)))
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(remember { MutableInteractionSource() }, indication = null) { onValueChange("") },
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = Color(0xFF7A7A7A), modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
    )
}

/** Pulling down past the top of the list closes the drawer. */
private class PullToClose(
    private val threshold: Float,
    private val onClose: () -> Unit,
) : NestedScrollConnection {
    private var pulled = 0f

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0f) {
            pulled += available.y
            if (pulled > threshold) {
                pulled = 0f
                onClose()
            }
        } else if (consumed.y != 0f) {
            pulled = 0f
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        pulled = 0f
        return Velocity.Zero
    }
}
