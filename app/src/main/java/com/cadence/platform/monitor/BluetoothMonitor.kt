package com.cadence.platform.monitor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.IntentCompat
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.platform.common.broadcastFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn

/**
 * Bluetooth device connect/disconnect events (ACL level) from one shared receiver. On
 * Android 12+ these broadcasts are only delivered with `BLUETOOTH_CONNECT`.
 */
@Singleton
class BluetoothMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    data class ConnectionChange(val connected: Boolean, val address: String, val name: String?)

    /** Connection changes as they happen. */
    val changes: SharedFlow<ConnectionChange> = context
        .broadcastFlow(
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            },
            // Sent by the Bluetooth system process; these are protected broadcasts.
            exported = true,
        )
        .mapNotNull { intent ->
            val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                ?: return@mapNotNull null
            ConnectionChange(
                connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED,
                address = device.address,
                name = device.safeName(),
            )
        }
        .shareIn(scope, SharingStarted.WhileSubscribed())

    @SuppressLint("MissingPermission") // Guarded: SecurityException means the name is unavailable.
    private fun BluetoothDevice.safeName(): String? = try {
        name
    } catch (e: SecurityException) {
        null
    }
}
