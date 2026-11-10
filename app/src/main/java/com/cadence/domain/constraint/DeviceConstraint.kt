package com.cadence.domain.constraint

import com.cadence.domain.action.RingerMode
import com.cadence.domain.device.DevicePosition
import com.cadence.domain.device.DeviceSwitch
import com.cadence.domain.device.Located
import com.cadence.domain.device.NetworkType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Device state and calendar constraints, grouped so the editor and summaries can handle them in
 * one place. Every [SerialName] is part of the storage format. Never change an existing one.
 */
@Serializable
sealed interface DeviceConstraint : ConstraintConfig {

    /** Holds while [switch] is on (or off, with [on] = `false`). Unknown states never hold. */
    @Serializable
    @SerialName("constraint.device_switch")
    data class SwitchState(val switch: DeviceSwitch, val on: Boolean = true) : DeviceConstraint

    /** Holds while the ringer is in one of [modes]. */
    @Serializable
    @SerialName("constraint.ringer_mode")
    data class RingerModeIs(val modes: Set<RingerMode>) : DeviceConstraint {
        init {
            require(modes.isNotEmpty()) { "modes must not be empty" }
        }
    }

    /** Holds while internet traffic goes over one of [types]. */
    @Serializable
    @SerialName("constraint.network_type")
    data class NetworkTypeIs(val types: Set<NetworkType>) : DeviceConstraint {
        init {
            require(types.isNotEmpty()) { "types must not be empty" }
        }
    }

    /** Holds while the phone is in [position], from a short accelerometer reading. */
    @Serializable
    @SerialName("constraint.device_position")
    data class PositionIs(val position: DevicePosition) : DeviceConstraint

    /** Holds while [packageName] is the app on screen (or isn't, with [inForeground] = `false`). Needs usage access. */
    @Serializable
    @SerialName("constraint.app_foreground")
    data class AppInForeground(val packageName: String, val inForeground: Boolean = true) : DeviceConstraint

    /** Holds between sunrise and sunset at a saved place ([daylight]), or between sunset and sunrise. */
    @Serializable
    @SerialName("constraint.daylight")
    data class Daylight(
        val daylight: Boolean = true,
        override val latitude: Double = 0.0,
        override val longitude: Double = 0.0,
        val label: String? = null,
    ) : DeviceConstraint, Located {
        init {
            Located.requireValid(latitude, longitude)
        }

        override fun at(latitude: Double, longitude: Double) = copy(latitude = latitude, longitude = longitude)
    }

    /** Holds on chosen days of the month, and optionally on the last day of every month. */
    @Serializable
    @SerialName("constraint.day_of_month")
    data class DaysOfMonth(val days: Set<Int> = emptySet(), val lastDay: Boolean = false) : DeviceConstraint {
        init {
            require(days.all { it in 1..31 }) { "days must be 1..31: $days" }
            require(days.isNotEmpty() || lastDay) { "Choose at least one day" }
        }
    }

    /** Holds in chosen months (1 = January). */
    @Serializable
    @SerialName("constraint.months")
    data class Months(val months: Set<Int>) : DeviceConstraint {
        init {
            require(months.isNotEmpty() && months.all { it in 1..12 }) { "months must be 1..12 and not empty: $months" }
        }
    }
}
