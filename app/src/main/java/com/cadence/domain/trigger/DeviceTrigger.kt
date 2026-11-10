package com.cadence.domain.trigger

import com.cadence.domain.action.RingerMode
import com.cadence.domain.device.DevicePosition
import com.cadence.domain.device.DeviceSwitch
import com.cadence.domain.device.Located
import com.cadence.domain.device.NetworkType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Device sensor, setting and system triggers. They're grouped under one sealed interface so the
 * editor and summaries can handle them in one place; to the engine they're ordinary
 * [TriggerConfig]s. None of them needs a permission beyond what Cadence already asks for.
 *
 * Every [SerialName] is part of the storage format. Never change an existing one.
 */
@Serializable
sealed interface DeviceTrigger : TriggerConfig {

    /** Fires when [switch] turns on ([turnedOn]) or off. Only [DeviceSwitch.triggerable] switches are allowed. */
    @Serializable
    @SerialName("trigger.device_switch")
    data class SwitchChanged(val switch: DeviceSwitch, val turnedOn: Boolean = true) : DeviceTrigger {
        init {
            require(switch.triggerable) { "$switch can't be used as a trigger" }
        }
    }

    /** Fires when the ringer changes to [mode]. */
    @Serializable
    @SerialName("trigger.ringer_mode")
    data class RingerModeChanged(val mode: RingerMode) : DeviceTrigger

    /** Fires when the phone is shaken. */
    @Serializable
    @SerialName("trigger.shake")
    data class Shake(val sensitivity: Sensitivity = Sensitivity.MEDIUM) : DeviceTrigger {
        @Serializable
        enum class Sensitivity { LOW, MEDIUM, HIGH }
    }

    /** Fires when the phone settles into [position] (e.g. is put face down) for about a second. */
    @Serializable
    @SerialName("trigger.device_position")
    data class PositionChanged(val position: DevicePosition) : DeviceTrigger

    /** Fires when something comes [near] the proximity sensor (top of the screen), or moves away. */
    @Serializable
    @SerialName("trigger.proximity")
    data class Proximity(val near: Boolean = true) : DeviceTrigger

    /** Fires when ambient light crosses [thresholdLux] in [direction]. */
    @Serializable
    @SerialName("trigger.light_level")
    data class LightLevel(
        val thresholdLux: Int,
        val direction: TriggerConfig.BatteryLevel.Direction,
    ) : DeviceTrigger {
        init {
            require(thresholdLux in 1..MAX_LUX) { "thresholdLux must be 1..$MAX_LUX, was $thresholdLux" }
        }

        companion object {
            const val MAX_LUX = 100_000
        }
    }

    /** Fires when battery temperature crosses [thresholdCelsius] in [direction]. */
    @Serializable
    @SerialName("trigger.battery_temperature")
    data class BatteryTemperature(
        val thresholdCelsius: Int,
        val direction: TriggerConfig.BatteryLevel.Direction,
    ) : DeviceTrigger {
        init {
            require(thresholdCelsius in 0..80) { "thresholdCelsius must be 0..80, was $thresholdCelsius" }
        }
    }

    /**
     * Fires when an app is installed, removed or updated.
     *
     * @property packageName only this app; `null` matches any app.
     */
    @Serializable
    @SerialName("trigger.app_package")
    data class AppPackageChanged(val event: Event, val packageName: String? = null) : DeviceTrigger {
        @Serializable
        enum class Event { INSTALLED, REMOVED, UPDATED }
    }

    /**
     * Fires when the network internet traffic uses changes (for example Wi-Fi to mobile data).
     *
     * @property to only changes to this type; `null` matches any change.
     */
    @Serializable
    @SerialName("trigger.network_type")
    data class NetworkTypeChanged(val to: NetworkType? = null) : DeviceTrigger

    /** Fires when the phone's local IP address changes. */
    @Serializable
    @SerialName("trigger.ip_address")
    data object IpAddressChanged : DeviceTrigger

    /**
     * Fires when a media app starts a new track. Needs notification access, which is how Android
     * lets apps see other apps' media sessions.
     *
     * @property packageName only this player; `null` matches any.
     */
    @Serializable
    @SerialName("trigger.music_track")
    data class MusicTrackChanged(val packageName: String? = null) : DeviceTrigger

    /**
     * Fires at sunrise or sunset at a saved place, shifted by [offsetMinutes] (negative is
     * before). The place is stored in the config, so no location access is needed.
     */
    @Serializable
    @SerialName("trigger.sun")
    data class Sun(
        val event: Event,
        override val latitude: Double = 0.0,
        override val longitude: Double = 0.0,
        val offsetMinutes: Int = 0,
        val label: String? = null,
    ) : DeviceTrigger, Located {
        init {
            Located.requireValid(latitude, longitude)
            require(offsetMinutes in -MAX_OFFSET_MINUTES..MAX_OFFSET_MINUTES) { "offsetMinutes out of range: $offsetMinutes" }
        }

        override fun at(latitude: Double, longitude: Double) = copy(latitude = latitude, longitude = longitude)

        @Serializable
        enum class Event { SUNRISE, SUNSET }

        companion object {
            const val MAX_OFFSET_MINUTES = 180
        }
    }

    /**
     * Fires at [hour]:[minute] on chosen days of the month.
     *
     * @property days days 1..31; months without that day are skipped.
     * @property lastDay also fire on the last day of every month.
     */
    @Serializable
    @SerialName("trigger.day_of_month")
    data class DayOfMonth(
        val days: Set<Int> = emptySet(),
        val lastDay: Boolean = false,
        val hour: Int = 9,
        val minute: Int = 0,
    ) : DeviceTrigger {
        init {
            require(days.all { it in 1..31 }) { "days must be 1..31: $days" }
            require(days.isNotEmpty() || lastDay) { "Choose at least one day" }
            require(hour in 0..23 && minute in 0..59) { "Invalid time $hour:$minute" }
        }
    }
}
