package app.minime.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.edit
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class Weather(
    val temperature: Int,
    val high: Int,
    val low: Int,
    val code: Int,
    val isDay: Boolean,
    val place: String?,
    val fahrenheit: Boolean,
    val fetchedAt: Long,
) {
    val condition: String get() = describe(code, isDay)
}

/** Current conditions from Open-Meteo (free, no API key) for the phone's approximate location. */
class WeatherRepository(private val context: Context) {
    private val cache = context.getSharedPreferences("minime_weather", Context.MODE_PRIVATE)
    private val locationManager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun cached(): Weather? {
        if (!cache.contains("fetched_at")) return null
        return Weather(
            temperature = cache.getInt("temp", 0),
            high = cache.getInt("high", 0),
            low = cache.getInt("low", 0),
            code = cache.getInt("code", 0),
            isDay = cache.getBoolean("is_day", true),
            place = cache.getString("place", null),
            fahrenheit = cache.getBoolean("fahrenheit", false),
            fetchedAt = cache.getLong("fetched_at", 0),
        )
    }

    /** Returns fresh weather, or null if location or network isn't available. */
    suspend fun fetch(fahrenheit: Boolean): Weather? {
        if (!hasPermission()) return null
        val location = currentLocation() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val unit = if (fahrenheit) "fahrenheit" else "celsius"
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=%.3f&longitude=%.3f".format(Locale.US, location.latitude, location.longitude) +
                        "&current=temperature_2m,weather_code,is_day" +
                        "&daily=temperature_2m_max,temperature_2m_min" +
                        "&timezone=auto&forecast_days=1&temperature_unit=$unit"
                )
                val json = (url.openConnection() as HttpURLConnection).run {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    try {
                        if (responseCode != 200) error("HTTP $responseCode")
                        inputStream.bufferedReader().use { it.readText() }
                    } finally {
                        disconnect()
                    }
                }.let(::JSONObject)

                val current = json.getJSONObject("current")
                val daily = json.getJSONObject("daily")
                Weather(
                    temperature = current.getDouble("temperature_2m").roundToInt(),
                    high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                    low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                    code = current.getInt("weather_code"),
                    isDay = current.optInt("is_day", 1) == 1,
                    place = placeName(location),
                    fahrenheit = fahrenheit,
                    fetchedAt = System.currentTimeMillis(),
                ).also(::save)
            }.getOrNull()
        }
    }

    private fun save(w: Weather) = cache.edit {
        putInt("temp", w.temperature)
        putInt("high", w.high)
        putInt("low", w.low)
        putInt("code", w.code)
        putBoolean("is_day", w.isDay)
        putString("place", w.place)
        putBoolean("fahrenheit", w.fahrenheit)
        putLong("fetched_at", w.fetchedAt)
    }

    @SuppressLint("MissingPermission") // checked in fetch()
    private suspend fun currentLocation(): Location? {
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) return lastKnown()
        val provider = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.firstOrNull { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return lastKnown()

        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    locationManager, provider, signal, context.mainExecutor,
                ) { location -> if (cont.isActive) cont.resume(location) }
            }
        }
        return fresh ?: lastKnown()
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? =
        locationManager.getProviders(true)
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    @Suppress("DEPRECATION") // the async variant is API 33+; we're already on a background thread
    private fun placeName(location: Location): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        Geocoder(context, Locale.getDefault())
            .getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()
            ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull()
}

/** WMO weather interpretation codes, as used by Open-Meteo. */
private fun describe(code: Int, isDay: Boolean): String = when (code) {
    0 -> if (isDay) "Sunny" else "Clear"
    1 -> if (isDay) "Mostly sunny" else "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61 -> "Light rain"
    63 -> "Rain"
    65 -> "Heavy rain"
    66, 67 -> "Freezing rain"
    71 -> "Light snow"
    73 -> "Snow"
    75 -> "Heavy snow"
    77 -> "Snow grains"
    80, 81 -> "Showers"
    82 -> "Heavy showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm, hail"
    else -> "—"
}
