package com.cadence.platform.device

import android.app.KeyguardManager
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.cadence.domain.device.DeviceSwitch

/** Audio outputs that count as headphones: wired, USB and Bluetooth audio. */
internal val HEADPHONE_OUTPUT_TYPES: Set<Int> = buildSet {
    add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
    add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
    add(AudioDeviceInfo.TYPE_USB_HEADSET)
    add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
    add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
}

/**
 * Reads the current value of each [DeviceSwitch]. None of these need a runtime permission;
 * `null` means the phone lacks the hardware (e.g. no NFC chip).
 */
class DeviceSwitchReader(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    fun read(switch: DeviceSwitch): Boolean? {
        val resolver = context.contentResolver
        return when (switch) {
            DeviceSwitch.AIRPLANE_MODE -> Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            DeviceSwitch.BATTERY_SAVER -> power.isPowerSaveMode
            // The system configuration, so Cadence's own theme setting doesn't affect it.
            DeviceSwitch.DARK_THEME ->
                Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            DeviceSwitch.DO_NOT_DISTURB -> when (context.getSystemService(NotificationManager::class.java).currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> false
                NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> null
                else -> true
            }
            DeviceSwitch.AUTO_ROTATE -> Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            DeviceSwitch.WIFI -> context.getSystemService(WifiManager::class.java)?.isWifiEnabled
            DeviceSwitch.BLUETOOTH -> context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
            DeviceSwitch.NFC -> NfcAdapter.getDefaultAdapter(context)?.isEnabled
            DeviceSwitch.LOCATION -> LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))
            DeviceSwitch.VPN -> {
                val connectivity = context.getSystemService(ConnectivityManager::class.java)
                connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false
            }
            DeviceSwitch.HEADPHONES -> audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_OUTPUT_TYPES }
            DeviceSwitch.MUSIC_PLAYING -> audio.isMusicActive
            DeviceSwitch.DOCKED -> {
                val dock = ContextCompat.registerReceiver(
                    context,
                    null,
                    IntentFilter(Intent.ACTION_DOCK_EVENT),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                (dock?.getIntExtra(Intent.EXTRA_DOCK_STATE, Intent.EXTRA_DOCK_STATE_UNDOCKED) ?: Intent.EXTRA_DOCK_STATE_UNDOCKED) !=
                    Intent.EXTRA_DOCK_STATE_UNDOCKED
            }
            DeviceSwitch.SCREEN_ON -> power.isInteractive
            DeviceSwitch.DEVICE_LOCKED -> context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        }
    }
}
