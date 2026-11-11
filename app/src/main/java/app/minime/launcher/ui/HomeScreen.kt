package app.minime.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.minime.launcher.LauncherViewModel
import app.minime.launcher.data.AppEntry
import app.minime.launcher.data.HomeAlignment
import app.minime.launcher.data.formatScreenTime
import app.minime.launcher.data.Settings
import app.minime.launcher.expandNotificationShade
import app.minime.launcher.openAlarms
import app.minime.launcher.openCalendar
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(vm: LauncherViewModel, settings: Settings) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val favorites by vm.favoriteApps.collectAsStateWithLifecycle()
    val screenTime by vm.screenTime.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<AppEntry?>(null) }
    val align = settings.alignment.horizontal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalSwipe(
                    onSwipeUp = vm::openDrawer,
                    onSwipeDown = { expandNotificationShade(context) },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalSwipe(onSwipeLeft = {}, onSwipeRight = vm::openInsights)
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.openSettings()
                })
            }
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 32.dp),
        horizontalAlignment = align,
    ) {
        StatusPills(
            Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(remember { MutableInteractionSource() }, indication = null) { expandNotificationShade(context) },
        )
        Spacer(Modifier.height(28.dp))
        val hasClock = settings.showClock
        if (hasClock || settings.showWeather) {
            // Clock on the left, weather to its right; the clock centres itself when it's alone.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasClock) {
                    ClockBlock(
                        centered = !settings.showWeather,
                        modifier = if (settings.showWeather) Modifier else Modifier.weight(1f),
                    )
                }
                if (settings.showWeather) {
                    Spacer(Modifier.weight(1f))
                    if (hasClock) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .padding(vertical = 6.dp)
                                .background(Color(0xFF2A2A2A)),
                        )
                        Spacer(Modifier.width(18.dp))
                    }
                    WeatherWidget(vm, settings.fahrenheit)
                }
            }
        }
        if (settings.showDate) {
            DateLine(Modifier.padding(top = 24.dp))
        }
        if (settings.showShortcuts) {
            ShortcutBar(vm, Modifier.padding(top = 28.dp))
        }

        Spacer(Modifier.height(28.dp))

        if (favorites.isEmpty()) {
            Text(
                "Swipe up for your apps.\nLong-press one to pin it here.\nLong-press empty space for settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = settings.alignment.textAlign,
            )
        }

        favorites.forEachIndexed { index, app ->
            AppItem(
                app = app,
                style = MaterialTheme.typography.headlineSmall,
                onClick = { vm.launch(app) },
                trailing = if (app.isWorkProfile) null else formatScreenTime(screenTime[app.component.packageName]),
                trailingAtEnd = settings.alignment == HomeAlignment.START,
                modifier = if (settings.alignment == HomeAlignment.START) Modifier.fillMaxWidth() else Modifier,
                menuActions = {
                    listOfNotNull(
                        MenuAction("Move up") { vm.moveFavorite(app, -1) }.takeIf { index > 0 },
                        MenuAction("Move down") { vm.moveFavorite(app, 1) }.takeIf { index < favorites.lastIndex },
                        MenuAction("Remove from home") { vm.toggleFavorite(app) },
                        MenuAction("Rename") { renaming = app },
                        MenuAction("App info") { vm.openAppInfo(app) },
                    )
                },
            )
        }

        Spacer(Modifier.weight(1f))

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

/** Large thin clock with tabular figures: white hours, softer minutes, a muted colon. Tap for alarms. */
@Composable
private fun ClockBlock(centered: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    // Tick on each minute boundary, only while the launcher is visible.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = LocalDateTime.now()
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }

    val is24h = DateFormat.is24HourFormat(context)
    val hourFormat = remember(is24h) { DateTimeFormatter.ofPattern(if (is24h) "HH" else "h") }
    val minuteFormat = remember { DateTimeFormatter.ofPattern("mm") }
    val noRipple = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = modifier,
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White)) { append(now.format(hourFormat)) }
                withStyle(SpanStyle(color = Color(0xFF5E5E5E))) { append(":") }
                withStyle(SpanStyle(color = Color(0xFFCFCFCF))) { append(now.format(minuteFormat)) }
            },
            style = TextStyle(
                fontSize = if (centered) 92.sp else 84.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-3).sp,
                fontFeatureSettings = "tnum",
                lineHeight = if (centered) 92.sp else 84.sp,
            ),
            modifier = Modifier.clickable(noRipple, indication = null) { openAlarms(context) },
        )
    }
}
