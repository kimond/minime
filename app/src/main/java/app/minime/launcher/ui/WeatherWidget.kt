package app.minime.launcher.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.minime.launcher.LauncherViewModel
import app.minime.launcher.openWeather
import app.minime.launcher.toast
import kotlinx.coroutines.delay

private val Muted = Color(0xFF6B6B6B)
private val Label = Color(0xFFBDBDBD)

/**
 * Compact, stacked current weather meant to sit beside the clock:
 * temperature, condition, place, today's high/low. Tap for a weather app.
 */
@Composable
fun WeatherWidget(vm: LauncherViewModel, fahrenheit: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val weather by vm.weather.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val noRipple = remember { MutableInteractionSource() }

    // Check on show (and when the unit changes); the ViewModel only fetches if the data is stale.
    LaunchedEffect(lifecycleOwner, fahrenheit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshWeather()
                delay(5 * 60_000L)
            }
        }
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.refreshWeather(force = true)
        if (!granted) context.toast("Location denied. You can allow it in App info.")
    }

    val w = weather
    Column(
        modifier = modifier
            .widthIn(max = 130.dp)
            .clickable(noRipple, indication = null) {
                when {
                    !vm.locationPermission -> requestPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    w == null -> vm.refreshWeather(force = true)
                    else -> openWeather(context)
                }
            }
            .padding(vertical = 4.dp),
    ) {
        when {
            !vm.locationPermission -> Hint("Tap for\nlocal weather")
            w == null -> Hint(if (vm.weatherFailed) "Weather\nunavailable" else "Loading…")
            else -> {
                Text("${w.temperature}°", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light, lineHeight = 30.sp)
                Text(w.condition, color = Label, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                w.place?.let {
                    Text(
                        it.uppercase(),
                        color = Muted,
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Text("↑${w.high}°  ↓${w.low}°", color = Muted, fontSize = 10.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Muted, fontSize = 11.sp, lineHeight = 14.sp)
}
