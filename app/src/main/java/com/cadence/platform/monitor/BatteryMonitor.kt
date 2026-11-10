package com.cadence.platform.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.platform.common.broadcastFlow
import com.cadence.platform.device.AndroidDeviceStateReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn

/**
 * Battery level and plug state from one shared `ACTION_BATTERY_CHANGED` receiver. The receiver
 * is registered while at least one trigger collects [snapshots].
 */
@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    /** @property temperatureCelsius battery temperature in whole °C, or `null` if not reported. */
    data class Snapshot(val levelPercent: Int, val plugged: Boolean, val temperatureCelsius: Int? = null)

    /** Current state first (replayed to each new subscriber), then every change. */
    val snapshots: SharedFlow<Snapshot> = context
        .broadcastFlow(IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        .mapNotNull { intent ->
            val level = AndroidDeviceStateReader.batteryPercent(intent) ?: return@mapNotNull null
            Snapshot(
                level,
                plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
                temperatureCelsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                    .takeIf { it != Int.MIN_VALUE }?.div(10),
            )
        }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)
}
