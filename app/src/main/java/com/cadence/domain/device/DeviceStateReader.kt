package com.cadence.domain.device

import com.cadence.domain.action.RingerMode

/**
 * Read-only snapshot queries about device state, used by constraint evaluators.
 *
 * Keeping this behind an interface lets the built-in evaluators stay pure Kotlin and unit
 * testable. The platform layer implements it with `BatteryManager`, `ConnectivityManager`,
 * `AudioManager` and friends.
 */
interface DeviceStateReader {

    /** Battery level 0..100, or `null` if unavailable. */
    suspend fun batteryLevelPercent(): Int?

    /** `true` when the device is charging (or full while plugged in). */
    suspend fun isCharging(): Boolean

    /** `true` when the device is connected to any Wi-Fi network. */
    suspend fun isWifiConnected(): Boolean

    /** Whether [switch] is on right now, or `null` if this device can't tell (e.g. no NFC chip). */
    suspend fun switchState(switch: DeviceSwitch): Boolean?

    /** The current ringer mode. */
    suspend fun ringerMode(): RingerMode

    /** The network internet traffic currently uses. */
    suspend fun networkType(): NetworkType
}
