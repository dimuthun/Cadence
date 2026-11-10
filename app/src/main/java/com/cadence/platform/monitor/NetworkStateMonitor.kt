package com.cadence.platform.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.domain.device.NetworkType
import com.cadence.platform.variable.NetworkReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn

/**
 * The default network's type and local IP address, from one shared default-network callback.
 * No permission beyond `ACCESS_NETWORK_STATE` is needed.
 */
@OptIn(FlowPreview::class)
@Singleton
class NetworkStateMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    data class State(val type: NetworkType, val ip: String)

    private val reader = NetworkReader(context)

    /**
     * Current state first, then each settled change. Switching networks briefly reports "no
     * network" in between, so changes are debounced.
     */
    val state: SharedFlow<State> = callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        fun send() {
            trySend(State(reader.currentType(), reader.currentIp()))
        }
        send()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = send()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = send()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = send()
            override fun onLost(network: Network) = send()
        }
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }
        .debounce(SETTLE_MILLIS)
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    private companion object {
        const val SETTLE_MILLIS = 2_000L
    }
}
