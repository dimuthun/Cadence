package com.cadence.platform.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.cadence.domain.action.RingerMode
import com.cadence.domain.device.DeviceStateReader
import com.cadence.domain.device.DeviceSwitch
import com.cadence.domain.device.NetworkType
import com.cadence.platform.variable.NetworkReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [DeviceStateReader] backed by the sticky battery broadcast, `ConnectivityManager` and [DeviceSwitchReader]. */
@Singleton
class AndroidDeviceStateReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceStateReader {

    private val switches = DeviceSwitchReader(context)
    private val network = NetworkReader(context)

    override suspend fun batteryLevelPercent(): Int? = batteryIntent()?.let(::batteryPercent)

    /** "Charging" means plugged into any power source, which also covers "full while plugged in". */
    override suspend fun isCharging(): Boolean =
        (batteryIntent()?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    override suspend fun isWifiConnected(): Boolean = isWifiConnectedNow(context)

    override suspend fun switchState(switch: DeviceSwitch): Boolean? = switches.read(switch)

    override suspend fun ringerMode(): RingerMode = when (context.getSystemService(AudioManager::class.java).ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
        AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
        else -> RingerMode.NORMAL
    }

    override suspend fun networkType(): NetworkType = network.currentType()

    private fun batteryIntent(): Intent? = ContextCompat.registerReceiver(
        context,
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )

    companion object {
        /** Level 0..100 from an `ACTION_BATTERY_CHANGED` intent, or `null` if missing. */
        fun batteryPercent(intent: Intent): Int? {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return null
            return (level * 100 / scale).coerceIn(0, 100)
        }

        /** `true` when the default network uses Wi-Fi. */
        fun isWifiConnectedNow(context: Context): Boolean {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
    }
}
