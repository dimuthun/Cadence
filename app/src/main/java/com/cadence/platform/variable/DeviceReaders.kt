package com.cadence.platform.variable

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.cadence.domain.device.NetworkType
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.platform.device.AndroidDeviceStateReader
import com.cadence.platform.device.HEADPHONE_OUTPUT_TYPES
import com.cadence.platform.permission.AndroidPermissionChecker
import java.net.Inet4Address
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

// Readers behind AndroidSystemVariableSource, one per namespace. Each returns null for keys it
// doesn't know and "" for values the device can't provide right now.

internal fun fmt(pattern: String, value: Number): String = String.format(Locale.ROOT, pattern, value)

/** `battery.*` from the sticky battery broadcast, BatteryManager and PowerManager. */
internal class BatteryReader(private val context: Context) {

    fun read(key: String): String? {
        if (key == "battery.saver") return context.getSystemService(PowerManager::class.java).isPowerSaveMode.toString()
        if (key == "battery.current_ma") {
            val microAmps = context.getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            return if (microAmps == Int.MIN_VALUE || microAmps == 0) "" else (microAmps / 1_000).toString()
        }
        val intent = ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        ) ?: return if (key in KEYS) "" else null
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return when (key) {
            "battery.level" -> AndroidDeviceStateReader.batteryPercent(intent)?.toString().orEmpty()
            "battery.charging" -> (plugged != 0).toString()
            "battery.plug" -> when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                0 -> "none"
                else -> "other"
            }
            "battery.temperature" -> intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE }?.let { fmt("%.1f", it / 10.0) }.orEmpty()
            "battery.voltage" -> intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                .takeIf { it > 0 }?.let { fmt("%.2f", it / 1_000.0) }.orEmpty()
            "battery.health" -> when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
                else -> "unknown"
            }
            else -> null
        }
    }

    private companion object {
        val KEYS = setOf("battery.level", "battery.charging", "battery.plug", "battery.temperature", "battery.voltage", "battery.health")
    }
}

/** `network.*` and `wifi.signal_*` from ConnectivityManager, WifiManager, TelephonyManager and Settings. */
internal class NetworkReader(private val context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun read(key: String): String? {
        val active = connectivity.activeNetwork
        val caps = active?.let(connectivity::getNetworkCapabilities)
        return when (key) {
            "network.type" -> when {
                caps == null -> "none"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                else -> "other"
            }
            "network.ip" -> currentIp()
            "network.carrier" -> context.getSystemService(TelephonyManager::class.java)?.networkOperatorName.orEmpty()
            "network.roaming" -> when {
                caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "false"
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                    (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)).toString()
                else -> (context.getSystemService(TelephonyManager::class.java)?.isNetworkRoaming ?: false).toString()
            }
            "network.vpn" -> (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false).toString()
            "network.airplane_mode" ->
                (Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1).toString()
            // Not a public setting on every Android version; best effort without READ_PHONE_STATE.
            "network.mobile_data" -> Settings.Global.getInt(context.contentResolver, "mobile_data", -1)
                .takeIf { it >= 0 }?.let { (it == 1).toString() }.orEmpty()
            "wifi.signal_dbm" -> wifiRssi(caps)?.toString().orEmpty()
            "wifi.signal_level" -> wifiRssi(caps)?.let(::signalBars)?.toString().orEmpty()
            else -> null
        }
    }

    /** The kind of the default network. A VPN reports the network it runs over. */
    fun currentType(): NetworkType {
        val caps = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities) ?: return NetworkType.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }
    }

    /** Local address of the default network, IPv4 preferred, or `""` when offline. */
    fun currentIp(): String = connectivity.activeNetwork?.let(connectivity::getLinkProperties)?.linkAddresses
        ?.map { it.address }
        ?.filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
        ?.sortedBy { if (it is Inet4Address) 0 else 1 }
        ?.firstOrNull()?.hostAddress.orEmpty()

    /** RSSI of the connected Wi-Fi network; signal strength never needs location access. */
    private fun wifiRssi(caps: NetworkCapabilities?): Int? {
        if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return caps.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
        }
        @Suppress("DEPRECATION")
        return context.getSystemService(WifiManager::class.java)?.connectionInfo?.rssi
    }

    private fun signalBars(rssi: Int): Int {
        val wifi = context.getSystemService(WifiManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifi != null) {
            (wifi.calculateSignalLevel(rssi) * 4 / wifi.maxSignalLevel.coerceAtLeast(1)).coerceIn(0, 4)
        } else {
            @Suppress("DEPRECATION")
            WifiManager.calculateSignalLevel(rssi, 5)
        }
    }
}

/** `bluetooth.*` and `nfc.*`. Connected device names need the Nearby devices permission. */
internal class RadioReader(
    private val context: Context,
    private val permissionChecker: AndroidPermissionChecker,
) {
    suspend fun read(key: String): String? {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        return when (key) {
            "bluetooth.enabled" -> (adapter?.isEnabled ?: false).toString()
            "bluetooth.connected_devices" -> connectedDeviceNames()
            "nfc.enabled" -> (NfcAdapter.getDefaultAdapter(context)?.isEnabled ?: false).toString()
            else -> null
        }
    }

    /** Devices connected over the headset and audio profiles (headphones, car kits, speakers). */
    @SuppressLint("MissingPermission") // Checked via permissionChecker; SecurityException handled.
    private suspend fun connectedDeviceNames(): String {
        if (!permissionChecker.isGranted(PermissionRequirement.BLUETOOTH_CONNECT)) return ""
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return ""
        if (!adapter.isEnabled) return ""
        val names = sortedSetOf<String>()
        for (profile in listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)) {
            val devices = withTimeoutOrNull(2.seconds) {
                suspendCancellableCoroutine<List<BluetoothDevice>> { continuation ->
                    val listener = object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile) {
                            val connected = try {
                                proxy.connectedDevices
                            } catch (e: SecurityException) {
                                emptyList()
                            }
                            adapter.closeProfileProxy(profileId, proxy)
                            if (continuation.isActive) continuation.resume(connected)
                        }

                        override fun onServiceDisconnected(profileId: Int) = Unit
                    }
                    if (!adapter.getProfileProxy(context, listener, profile) && continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }
            }.orEmpty()
            devices.forEach { device ->
                names += try {
                    device.name ?: device.address
                } catch (e: SecurityException) {
                    device.address
                }
            }
        }
        return names.joinToString(", ")
    }
}

/** `display.*` and `audio.*`. */
internal class DisplaySoundReader(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)

    fun read(key: String): String? {
        val resolver = context.contentResolver
        val configuration = context.resources.configuration
        return when (key) {
            "display.brightness" -> Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
                .takeIf { it >= 0 }?.let { (it * 100 / 255).coerceIn(0, 100).toString() }.orEmpty()
            "display.brightness_auto" -> (
                Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                ).toString()
            "display.auto_rotate" -> (Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1).toString()
            "display.orientation" ->
                if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
            "display.dark_mode" ->
                (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES).toString()
            "audio.dnd" -> when (context.getSystemService(NotificationManager::class.java).currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
                NotificationManager.INTERRUPTION_FILTER_NONE -> "silence"
                else -> ""
            }
            "audio.volume_ring" -> volume(AudioManager.STREAM_RING)
            "audio.volume_notification" -> volume(AudioManager.STREAM_NOTIFICATION)
            "audio.volume_media" -> volume(AudioManager.STREAM_MUSIC)
            "audio.volume_alarm" -> volume(AudioManager.STREAM_ALARM)
            "audio.volume_call" -> volume(AudioManager.STREAM_VOICE_CALL)
            "audio.headphones" -> audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_OUTPUT_TYPES }.toString()
            "audio.music_playing" -> audio.isMusicActive.toString()
            else -> null
        }
    }

    private fun volume(stream: Int): String {
        val max = audio.getStreamMaxVolume(stream)
        if (max <= 0) return ""
        return (audio.getStreamVolume(stream) * 100 / max).toString()
    }
}

/** `device.*`, `storage.*` and `memory.*`. */
internal class DeviceInfoReader(private val context: Context) {

    fun read(key: String): String? = when (key) {
        "device.screen_on" -> context.getSystemService(PowerManager::class.java).isInteractive.toString()
        "device.ringer_mode" -> when (context.getSystemService(AudioManager::class.java).ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "normal"
        }
        "device.model" -> Build.MODEL
        "device.manufacturer" -> Build.MANUFACTURER
        "device.android_version" -> Build.VERSION.RELEASE
        "device.api_level" -> Build.VERSION.SDK_INT.toString()
        "device.name" -> Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL
        "device.locked" -> context.getSystemService(KeyguardManager::class.java).isKeyguardLocked.toString()
        "device.uptime_minutes" -> (SystemClock.elapsedRealtime() / 60_000).toString()
        "device.language" -> Locale.getDefault().toLanguageTag()
        "storage.free_gb", "storage.total_gb" -> {
            val stats = StatFs(Environment.getDataDirectory().path)
            val bytes = if (key == "storage.free_gb") stats.availableBytes else stats.totalBytes
            fmt("%.1f", bytes / 1_000_000_000.0)
        }
        "memory.free_mb", "memory.total_mb" -> {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            ((if (key == "memory.free_mb") info.availMem else info.totalMem) / 1_048_576).toString()
        }
        else -> null
    }
}

/** `time.*` in the device time zone. */
internal class TimeReader(private val clock: Clock) {

    fun read(key: String): String? {
        val now = ZonedDateTime.now(clock)
        return when (key) {
            "time.now" -> now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT))
            "time.date" -> now.toLocalDate().toString()
            "time.time" -> now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
            "time.time_12h" -> now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
            "time.hour" -> now.hour.toString()
            "time.minute" -> now.minute.toString()
            "time.weekday" -> now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            "time.day" -> now.dayOfMonth.toString()
            "time.month" -> now.monthValue.toString()
            "time.month_name" -> now.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            "time.year" -> now.year.toString()
            "time.week" -> now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString()
            "time.timezone" -> now.zone.id
            "time.epoch_seconds" -> now.toEpochSecond().toString()
            "time.epoch_millis" -> now.toInstant().toEpochMilli().toString()
            else -> null
        }
    }
}
