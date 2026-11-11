package app.minime.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.minime.launcher.LauncherViewModel
import app.minime.launcher.data.BehaviorReport
import app.minime.launcher.data.DayStat
import app.minime.launcher.data.formatScreenTime
import app.minime.launcher.openUsageAccessSettings
import kotlinx.coroutines.delay
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Bright = Color(0xFFEDEDED)
private val Soft = Color(0xFFBDBDBD)
private val Muted = Color(0xFF6B6B6B)
private val Track = Color(0xFF1E1E1E)
private val Ghost = Color(0xFF2E2E2E)

/** Swipe-right screen: on-device analysis of the last 7 days of app usage. Swipe left or back to close. */
@Composable
fun InsightsScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val report by vm.insights.collectAsStateWithLifecycle()

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.refreshInsights()
                delay(60_000L)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectHorizontalSwipe(onSwipeLeft = vm::goHome, onSwipeRight = {}) }
            .systemBarsPadding(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
    ) {
        item { Label("Insights") }

        val r = report
        when {
            !vm.usageAccess -> item {
                Column(Modifier.padding(top = 24.dp)) {
                    Text("Insights learn from your app usage.", color = Bright, fontSize = 18.sp)
                    Text(
                        "Allow usage access to start. Everything is analysed on this phone and never leaves it.",
                        color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "ALLOW USAGE ACCESS  ›",
                        color = Soft, fontSize = 10.sp, letterSpacing = 2.sp,
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .clickable { openUsageAccessSettings(context) }
                            .padding(vertical = 8.dp),
                    )
                }
            }
            r == null -> item { Text("Reading your week…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 24.dp)) }
            else -> {
                item { Headline(r) }
                item { Section("Your day") { HourChart(r) } }
                item { Section("This week") { WeekChart(r.week) } }
                if (r.topApps.isNotEmpty()) {
                    item {
                        Section("Most used today") {
                            val max = r.topApps.first().ms
                            r.topApps.forEach { app -> AppBar(app.label, app.ms, max) }
                        }
                    }
                }
                if (r.insights.isNotEmpty()) {
                    item {
                        Section("Patterns") {
                            r.insights.forEach { InsightRow(it) }
                        }
                    }
                }
                item {
                    Text(
                        "Based on the last 7 days. Analysed on this phone only.",
                        color = Color(0xFF4A4A4A), fontSize = 10.sp,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Headline(r: BehaviorReport) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(
            formatScreenTime(r.today.totalMs) ?: "0m",
            style = TextStyle(color = Bright, fontSize = 56.sp, fontWeight = FontWeight.Thin, letterSpacing = (-1).sp),
        )
        val typical = r.typicalSoFarMs
        val comparison = if (typical != null && typical >= 5 * 60_000L) {
            val pct = ((r.today.totalMs - typical) * 100.0 / typical).roundToInt()
            when {
                abs(pct) < 8 -> "ON YOUR USUAL PACE"
                pct < 0 -> "${-pct}% BELOW TYPICAL"
                else -> "$pct% ABOVE TYPICAL"
            }
        } else null
        Text(
            listOfNotNull("TODAY", comparison).joinToString("  ·  "),
            color = Muted, fontSize = 9.sp, letterSpacing = 2.sp,
        )
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Stat(r.today.unlocks.toString(), "UNLOCKS")
            Stat(r.today.sessions.toString(), "APP OPENS")
            val avgWeek = r.week.dropLast(1).filter { it.totalMs > 0 }.map { it.totalMs }.average()
            if (!avgWeek.isNaN()) Stat(formatScreenTime(avgWeek.toLong()) ?: "0m", "DAILY AVG")
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, color = Soft, fontSize = 18.sp, fontWeight = FontWeight.Light)
        Text(label, color = Muted, fontSize = 8.sp, letterSpacing = 1.5.sp)
    }
}

/** 24 thin bars: today in light grey over a darker "typical day" ghost bar. */
@Composable
private fun HourChart(r: BehaviorReport) {
    val max = (r.hourlyToday + r.hourlyTypical).maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        val slot = size.width / 24f
        val bar = slot * 0.55f
        val radius = CornerRadius(bar / 2, bar / 2)
        for (h in 0 until 24) {
            val x = h * slot + (slot - bar) / 2
            val typical = size.height * r.hourlyTypical[h] / max
            val today = size.height * r.hourlyToday[h] / max
            drawRoundRect(Track, Offset(x, 0f), Size(bar, size.height), radius)
            if (typical > 0f) drawRoundRect(Ghost, Offset(x, size.height - typical), Size(bar, typical), radius)
            if (today > 0f) drawRoundRect(Soft, Offset(x, size.height - today), Size(bar, today), radius)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        listOf("12A", "6A", "12P", "6P").forEach {
            Text(it, color = Muted, fontSize = 8.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
        Legend(Soft, "TODAY")
        Spacer(Modifier.width(16.dp))
        Legend(Ghost, "TYPICAL")
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Box(Modifier.size(6.dp).background(color, CircleShape))
    Spacer(Modifier.width(6.dp))
    Text(label, color = Muted, fontSize = 8.sp, letterSpacing = 1.5.sp)
}

/** Seven columns, today highlighted, with the day's total above its bar. */
@Composable
private fun WeekChart(week: List<DayStat>) {
    val max = week.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEachIndexed { i, day ->
            val isToday = i == week.lastIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxHeight().weight(1f),
            ) {
                Text(
                    formatScreenTime(day.totalMs)?.substringBefore(' ') ?: "–",
                    color = if (isToday) Bright else Muted, fontSize = 8.sp,
                )
                Spacer(Modifier.height(6.dp))
                // The bar gets the space left between the two labels, so every label lines up.
                Box(Modifier.weight(1f).width(10.dp), contentAlignment = Alignment.BottomCenter) {
                    val fraction = (day.totalMs.toFloat() / max).coerceIn(0.04f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .background(if (isToday) Soft else Ghost, RoundedCornerShape(5.dp)),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    day.date.dayOfWeek.getDisplayName(DateTextStyle.NARROW, locale),
                    color = if (isToday) Bright else Muted, fontSize = 9.sp,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun AppBar(label: String, ms: Long, max: Long) {
    Column(Modifier.padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Soft, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(formatScreenTime(ms) ?: "", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Track, RoundedCornerShape(1.dp))) {
            Box(
                Modifier
                    .fillMaxWidth((ms.toFloat() / max).coerceIn(0.02f, 1f))
                    .height(2.dp)
                    .background(Soft, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
private fun InsightRow(text: String) {
    Row(Modifier.padding(vertical = 8.dp)) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(4.dp)
                .background(Soft, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, color = Soft, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 36.dp)) {
        Label(title)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), color = Muted, fontSize = 9.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.Medium)
}
