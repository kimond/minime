package app.minime.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.minime.launcher.LauncherViewModel
import app.minime.launcher.Screen

@Composable
fun LauncherApp(vm: LauncherViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val screen = vm.screen

    // Screen time: refresh on every return to the launcher, then each minute while it's in front.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, settings.showScreenTime) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.refreshScreenTime()
                delay(60_000L)
            }
        }
    }

    // A launcher never "exits" on back; from sub-screens it returns home.
    BackHandler { vm.onBack() }

    val scrim by animateColorAsState(
        targetValue = when {
            !settings.showWallpaper -> Color.Black
            screen == Screen.HOME -> Color.Black.copy(alpha = 0.3f)
            else -> Color.Black.copy(alpha = 0.88f)
        },
        label = "scrim",
    )

    vm.editingShortcut?.let { ShortcutEditor(vm, it, onDismiss = vm::closeShortcutEditor) }

    Box(Modifier.fillMaxSize().background(scrim)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState == Screen.INSIGHTS) {
                    // Swipe right pulls Insights in from the left edge.
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 6 }) togetherWith fadeOut(tween(120))
                } else if (initialState == Screen.INSIGHTS) {
                    fadeIn(tween(180)) togetherWith (fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 6 })
                } else if (targetState == Screen.HOME) {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                } else {
                    (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 12 }) togetherWith
                        fadeOut(tween(120))
                }
            },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.HOME -> HomeScreen(vm, settings)
                Screen.DRAWER -> DrawerScreen(vm, settings)
                Screen.SETTINGS -> SettingsScreen(vm, settings)
                Screen.SHIELD -> ShieldScreen(vm, settings)
                Screen.INSIGHTS -> InsightsScreen(vm)
            }
        }
    }
}
