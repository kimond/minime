package app.minime.launcher.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val Monochrome = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF9E9E9E),
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF111111),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF262626),
    outline = Color(0xFF6E6E6E),
    outlineVariant = Color(0xFF333333),
)

@Composable
fun MinimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Monochrome) {
        // We draw straight on the window (no Surface), so set the default text color ourselves;
        // otherwise Text falls back to black, which is invisible on our black background.
        CompositionLocalProvider(LocalContentColor provides Monochrome.onBackground, content = content)
    }
}
