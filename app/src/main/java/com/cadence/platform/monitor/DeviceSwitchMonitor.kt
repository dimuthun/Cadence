package com.cadence.platform.monitor

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.location.LocationManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.domain.action.RingerMode
import com.cadence.domain.device.DeviceSwitch
import com.cadence.platform.common.broadcastFlow
import com.cadence.platform.device.DeviceSwitchReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

/**
 * The state of each [DeviceSwitch] as a shared flow: the current value first, then every
 * change. Each switch has one system listener (receiver, observer or callback) registered only
 * while some trigger collects it.
 */
@Singleton
class DeviceSwitchMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val screen: ScreenStateMonitor,
) {
    private val reader = DeviceSwitchReader(context)
    private val flows = ConcurrentHashMap<DeviceSwitch, SharedFlow<Boolean>>()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Current state of [switch], then each change. Never emits if the phone lacks the hardware. */
    fun states(switch: DeviceSwitch): SharedFlow<Boolean> = flows.getOrPut(switch) {
        changeSignals(switch)
            .onStart { emit(Unit) }
            .mapNotNull {
                // Playback state settles a moment after the callback.
                if (switch == DeviceSwitch.MUSIC_PLAYING) delay(MUSIC_SETTLE_MILLIS)
                reader.read(switch)
            }
            .distinctUntilChanged()
            .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)
    }

    /** The ringer mode from the sticky ringer broadcast: the current mode first, then each change. */
    val ringerModes: SharedFlow<RingerMode> = context
        .broadcastFlow(IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION))
        .map { intent ->
            when (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)) {
                AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
                AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
                else -> RingerMode.NORMAL
            }
        }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    /** A signal each time [switch] may have changed; the reader decides whether it did. */
    private fun changeSignals(switch: DeviceSwitch): Flow<Unit> = when (switch) {
        DeviceSwitch.AIRPLANE_MODE -> broadcasts(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        DeviceSwitch.BATTERY_SAVER -> broadcasts(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        DeviceSwitch.DARK_THEME -> configurationChanges()
        DeviceSwitch.DO_NOT_DISTURB -> broadcasts(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        DeviceSwitch.AUTO_ROTATE -> settingChanges(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION))
        DeviceSwitch.WIFI -> broadcasts(WifiManager.WIFI_STATE_CHANGED_ACTION)
        // Sent by the Bluetooth and NFC processes, which aren't the system uid.
        DeviceSwitch.BLUETOOTH -> broadcasts(BluetoothAdapter.ACTION_STATE_CHANGED, exported = true)
        DeviceSwitch.NFC -> broadcasts(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED, exported = true)
        DeviceSwitch.LOCATION -> broadcasts(LocationManager.MODE_CHANGED_ACTION, LocationManager.PROVIDERS_CHANGED_ACTION)
        DeviceSwitch.VPN -> networkChanges()
        DeviceSwitch.HEADPHONES -> audioDeviceChanges()
        DeviceSwitch.MUSIC_PLAYING -> playbackChanges()
        DeviceSwitch.DOCKED -> broadcasts(Intent.ACTION_DOCK_EVENT)
        DeviceSwitch.SCREEN_ON, DeviceSwitch.DEVICE_LOCKED -> screen.events.map { }
    }

    private fun broadcasts(vararg actions: String, exported: Boolean = false): Flow<Unit> =
        context.broadcastFlow(IntentFilter().apply { actions.forEach(::addAction) }, exported).map { }

    private fun configurationChanges(): Flow<Unit> = callbackFlow {
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                trySend(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        }
        context.registerComponentCallbacks(callbacks)
        awaitClose { context.unregisterComponentCallbacks(callbacks) }
    }

    private fun settingChanges(uri: Uri): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(uri, false, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    private fun networkChanges(): Flow<Unit> = callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(Unit)
            }

            override fun onLost(network: Network) {
                trySend(Unit)
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }

    private fun audioDeviceChanges(): Flow<Unit> = callbackFlow {
        val audio = context.getSystemService(AudioManager::class.java)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(Unit)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(Unit)
            }
        }
        audio.registerAudioDeviceCallback(callback, mainHandler)
        awaitClose { audio.unregisterAudioDeviceCallback(callback) }
    }

    private fun playbackChanges(): Flow<Unit> = callbackFlow {
        val audio = context.getSystemService(AudioManager::class.java)
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                trySend(Unit)
            }
        }
        audio.registerAudioPlaybackCallback(callback, mainHandler)
        awaitClose { audio.unregisterAudioPlaybackCallback(callback) }
    }

    private companion object {
        const val MUSIC_SETTLE_MILLIS = 300L
    }
}
