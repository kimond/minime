package app.minime.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.minime.launcher.openCalendar
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Bright = Color(0xFFCFCFCF)
private val Soft = Color(0xFF8A8A8A)
private val Separator = Color(0xFF4A4A4A)

/** "SUNDAY · NOVEMBER 8 · 2026" in small spaced capitals, centred. Tap to open the calendar. */
@Composable
fun DateLine(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var today by remember { mutableStateOf(LocalDate.now()) }

    // Keep the date right across midnight while the home screen is visible.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                today = LocalDate.now()
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }

    val locale = Locale.getDefault()
    val weekday = remember(today) { today.format(DateTimeFormatter.ofPattern("EEEE", locale)) }
    val monthDay = remember(today) {
        today.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMd"), locale))
    }
    val year = today.year.toString()
    val noRipple = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .clickable(noRipple, indication = null) { openCalendar(context) }
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Bright)) { append(weekday.uppercase(locale)) }
                withStyle(SpanStyle(color = Separator)) { append("  ·  ") }
                withStyle(SpanStyle(color = Soft)) { append(monthDay.uppercase(locale)) }
                withStyle(SpanStyle(color = Separator)) { append("  ·  ") }
                withStyle(SpanStyle(color = Soft)) { append(year) }
            },
            fontSize = 11.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
