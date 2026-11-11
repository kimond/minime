package app.minime.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PillBorder = Color(0xFF2E2E2E)
private val PillContent = Color(0xFFCFCFCF)

private enum class Connection { WIFI, CELLULAR, NONE }

private data class BatteryState(val percent: Int, val charging: Boolean)

/** Two small outlined pills: network, and battery percentage with a drawn battery. */
@Composable
fun StatusPills(modifier: Modifier = Modifier) {
    val connection = rememberConnection()
    val battery = rememberBattery()

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill {
            Icon(
                imageVector = when (connection) {
                    Connection.WIFI -> Icons.Outlined.Wifi
                    Connection.CELLULAR -> Icons.Outlined.SignalCellularAlt
                    Connection.NONE -> Icons.Outlined.WifiOff
                },
                contentDescription = connection.name.lowercase(),
                tint = PillContent,
                modifier = Modifier.size(12.dp),
            )
        }
        Pill {
            if (battery.charging) {
                Icon(Icons.Outlined.Bolt, contentDescription = "charging", tint = PillContent, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(2.dp))
            }
            Text("${battery.percent}%", color = PillContent, fontSize = 10.sp)
            Spacer(Modifier.width(5.dp))
            BatteryGlyph(battery.percent)
        }
    }
}

@Composable
private fun Pill(content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(1.dp, PillBorder, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        content = content,
    )
}

/** Outline battery with a fill proportional to [percent]. */
@Composable
private fun BatteryGlyph(percent: Int) {
    Canvas(Modifier.size(width = 17.dp, height = 9.dp)) {
        val nub = 1.5.dp.toPx()
        val stroke = 1.dp.toPx()
        val bodyWidth = size.width - nub - stroke / 2
        drawRoundRect(
            color = PillContent,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(bodyWidth - stroke / 2, size.height - stroke),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(stroke),
        )
        val inset = 2.dp.toPx()
        val fillMax = bodyWidth - inset * 2 + stroke / 2
        drawRoundRect(
            color = PillContent,
            topLeft = Offset(inset, inset),
            size = Size(fillMax * percent.coerceIn(0, 100) / 100f, size.height - inset * 2),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        drawRoundRect(
            color = PillContent,
            topLeft = Offset(size.width - nub, size.height * 0.3f),
            size = Size(nub, size.height * 0.4f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}

@Composable
private fun rememberConnection(): Connection {
    val context = LocalContext.current
    var connection by remember { mutableStateOf(Connection.NONE) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun from(caps: NetworkCapabilities?) = when {
            caps == null -> Connection.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Connection.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Connection.CELLULAR
            else -> Connection.NONE
        }
        connection = from(cm.getNetworkCapabilities(cm.activeNetwork))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                connection = from(caps)
            }

            override fun onLost(network: Network) {
                connection = from(cm.getNetworkCapabilities(cm.activeNetwork))
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return connection
}

@Composable
private fun rememberBattery(): BatteryState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(BatteryState(100, false)) }
    DisposableEffect(context) {
        fun read(intent: Intent?): BatteryState? {
            intent ?: return null
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            return BatteryState(level * 100 / scale, charging)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                read(intent)?.let { state = it }
            }
        }
        // ACTION_BATTERY_CHANGED is sticky, so registering returns the current state immediately.
        read(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))?.let { state = it }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}
