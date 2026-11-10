package com.cadence.platform.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.platform.device.AndroidDeviceStateReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn

/**
 * Whether any Wi-Fi network is connected, from one shared `NetworkCallback`. No location
 * permission is needed because SSIDs are never read.
 */
@Singleton
class WifiMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    /** Current state first (replayed to each new subscriber), then every change. */
    val connected: SharedFlow<Boolean> = callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val wifiNetworks = mutableSetOf<Network>()
        trySend(AndroidDeviceStateReader.isWifiConnectedNow(context))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val connected = synchronized(wifiNetworks) {
                    wifiNetworks += network
                    true
                }
                trySend(connected)
            }

            override fun onLost(network: Network) {
                val connected = synchronized(wifiNetworks) {
                    wifiNetworks -= network
                    wifiNetworks.isNotEmpty()
                }
                trySend(connected)
            }
        }
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            callback,
        )
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)
}
