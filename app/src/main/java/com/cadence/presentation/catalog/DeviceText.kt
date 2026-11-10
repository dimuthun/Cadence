package com.cadence.presentation.catalog

import com.cadence.domain.action.RingerMode
import com.cadence.domain.constraint.DeviceConstraint
import com.cadence.domain.device.DevicePosition
import com.cadence.domain.device.DeviceSwitch
import com.cadence.domain.device.Located
import com.cadence.domain.device.NetworkType
import com.cadence.domain.trigger.DeviceTrigger
import com.cadence.domain.trigger.TriggerConfig
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** Labels and summaries for [DeviceTrigger] and [DeviceConstraint] configs. */
object DeviceText {

    fun switchLabel(switch: DeviceSwitch): String = when (switch) {
        DeviceSwitch.AIRPLANE_MODE -> "Airplane mode"
        DeviceSwitch.BATTERY_SAVER -> "Battery saver"
        DeviceSwitch.DARK_THEME -> "Dark theme"
        DeviceSwitch.DO_NOT_DISTURB -> "Do Not Disturb"
        DeviceSwitch.AUTO_ROTATE -> "Auto-rotate"
        DeviceSwitch.WIFI -> "Wi-Fi"
        DeviceSwitch.BLUETOOTH -> "Bluetooth"
        DeviceSwitch.NFC -> "NFC"
        DeviceSwitch.LOCATION -> "Location services"
        DeviceSwitch.VPN -> "VPN"
        DeviceSwitch.HEADPHONES -> "Headphones"
        DeviceSwitch.MUSIC_PLAYING -> "Music playing"
        DeviceSwitch.DOCKED -> "Dock"
        DeviceSwitch.SCREEN_ON -> "Screen on"
        DeviceSwitch.DEVICE_LOCKED -> "Phone locked"
    }

    /** "On"/"Off" choice labels that read well for [switch]. */
    fun stateLabel(switch: DeviceSwitch, on: Boolean): String = when (switch) {
        DeviceSwitch.HEADPHONES, DeviceSwitch.VPN -> if (on) "Connected" else "Disconnected"
        DeviceSwitch.MUSIC_PLAYING -> if (on) "Playing" else "Not playing"
        DeviceSwitch.DOCKED -> if (on) "Docked" else "Undocked"
        DeviceSwitch.DEVICE_LOCKED -> if (on) "Locked" else "Unlocked"
        else -> if (on) "On" else "Off"
    }

    fun ringerLabel(mode: RingerMode): String = when (mode) {
        RingerMode.NORMAL -> "Sound"
        RingerMode.VIBRATE -> "Vibrate"
        RingerMode.SILENT -> "Silent"
    }

    fun networkLabel(type: NetworkType): String = when (type) {
        NetworkType.WIFI -> "Wi-Fi"
        NetworkType.CELLULAR -> "Mobile data"
        NetworkType.ETHERNET -> "Ethernet"
        NetworkType.OTHER -> "Other"
        NetworkType.NONE -> "No network"
    }

    fun positionLabel(position: DevicePosition): String = when (position) {
        DevicePosition.FACE_UP -> "Face up"
        DevicePosition.FACE_DOWN -> "Face down"
        DevicePosition.PORTRAIT -> "Upright"
        DevicePosition.LANDSCAPE -> "Sideways"
    }

    fun monthLabel(month: Int): String = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    /** "1st", "2nd", "23rd", "11th". */
    fun ordinal(day: Int): String {
        val suffix = when {
            day % 100 in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }

    /** "the 1st, 15th and last day". */
    fun monthDays(days: Set<Int>, lastDay: Boolean): String {
        val parts = days.sorted().map(::ordinal) + if (lastDay) listOf("last day") else emptyList()
        val joined = if (parts.size <= 1) parts.joinToString() else parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        return "the $joined"
    }

    fun trigger(config: DeviceTrigger, appLabel: (String) -> String): String = when (config) {
        is DeviceTrigger.SwitchChanged -> switchTrigger(config.switch, config.turnedOn)
        is DeviceTrigger.RingerModeChanged -> "Ringer changes to ${ringerLabel(config.mode).lowercase(Locale.ROOT)}"
        is DeviceTrigger.Shake -> when (config.sensitivity) {
            DeviceTrigger.Shake.Sensitivity.LOW -> "Phone shaken hard"
            DeviceTrigger.Shake.Sensitivity.MEDIUM -> "Phone shaken"
            DeviceTrigger.Shake.Sensitivity.HIGH -> "Phone shaken gently"
        }
        is DeviceTrigger.PositionChanged -> when (config.position) {
            DevicePosition.FACE_UP -> "Phone laid face up"
            DevicePosition.FACE_DOWN -> "Phone turned face down"
            DevicePosition.PORTRAIT -> "Phone held upright"
            DevicePosition.LANDSCAPE -> "Phone turned sideways"
        }
        is DeviceTrigger.Proximity -> if (config.near) "Something covers the proximity sensor" else "Proximity sensor uncovered"
        is DeviceTrigger.LightLevel -> when (config.direction) {
            TriggerConfig.BatteryLevel.Direction.FALLS_BELOW -> "Light falls below ${config.thresholdLux} lux"
            TriggerConfig.BatteryLevel.Direction.RISES_ABOVE -> "Light rises above ${config.thresholdLux} lux"
        }
        is DeviceTrigger.BatteryTemperature -> when (config.direction) {
            TriggerConfig.BatteryLevel.Direction.FALLS_BELOW -> "Battery cools below ${config.thresholdCelsius} °C"
            TriggerConfig.BatteryLevel.Direction.RISES_ABOVE -> "Battery heats above ${config.thresholdCelsius} °C"
        }
        is DeviceTrigger.AppPackageChanged -> {
            val app = config.packageName?.let(appLabel) ?: "Any app"
            when (config.event) {
                DeviceTrigger.AppPackageChanged.Event.INSTALLED -> "$app installed"
                DeviceTrigger.AppPackageChanged.Event.REMOVED -> "$app removed"
                DeviceTrigger.AppPackageChanged.Event.UPDATED -> "$app updated"
            }
        }
        is DeviceTrigger.NetworkTypeChanged -> when (config.to) {
            null -> "Network type changes"
            NetworkType.NONE -> "Phone goes offline"
            else -> "Network changes to ${networkLabel(config.to).lowercase(Locale.ROOT).replace("wi-fi", "Wi-Fi")}"
        }
        DeviceTrigger.IpAddressChanged -> "IP address changes"
        is DeviceTrigger.MusicTrackChanged -> "New track playing" + (config.packageName?.let { " in ${appLabel(it)}" } ?: "")
        is DeviceTrigger.Sun -> {
            val event = if (config.event == DeviceTrigger.Sun.Event.SUNRISE) "sunrise" else "sunset"
            val whenText = when {
                config.offsetMinutes == 0 -> "At $event"
                config.offsetMinutes < 0 -> "${ConfigText.interval(-config.offsetMinutes)} before $event"
                else -> "${ConfigText.interval(config.offsetMinutes)} after $event"
            }
            "$whenText${place(config, config.label)}"
        }
        is DeviceTrigger.DayOfMonth ->
            "On ${monthDays(config.days, config.lastDay)} at ${ConfigText.time(config.hour, config.minute)}"
    }

    fun constraint(config: DeviceConstraint, appLabel: (String) -> String): String = when (config) {
        is DeviceConstraint.SwitchState -> switchConstraint(config.switch, config.on)
        is DeviceConstraint.RingerModeIs ->
            "While the ringer is " + config.modes.sortedBy { it.ordinal }.joinToString(" or ") { ringerLabel(it).lowercase(Locale.ROOT) }
        is DeviceConstraint.NetworkTypeIs ->
            "While on " + config.types.sortedBy { it.ordinal }.joinToString(" or ") { networkLabel(it) }
        is DeviceConstraint.PositionIs -> "While ${positionLabel(config.position).lowercase(Locale.ROOT)}"
        is DeviceConstraint.AppInForeground -> {
            val app = if (config.packageName.isBlank()) "an app" else appLabel(config.packageName)
            if (config.inForeground) "While $app is on screen" else "While $app isn't on screen"
        }
        is DeviceConstraint.Daylight ->
            (if (config.daylight) "Between sunrise and sunset" else "Between sunset and sunrise") + place(config, config.label)
        is DeviceConstraint.DaysOfMonth -> "On ${monthDays(config.days, config.lastDay)} of the month"
        is DeviceConstraint.Months -> "In " + config.months.sorted().joinToString(", ") { monthLabel(it) }
    }

    private fun switchTrigger(switch: DeviceSwitch, on: Boolean): String = when (switch) {
        DeviceSwitch.HEADPHONES -> if (on) "Headphones connected" else "Headphones disconnected"
        DeviceSwitch.VPN -> if (on) "VPN connects" else "VPN disconnects"
        DeviceSwitch.MUSIC_PLAYING -> if (on) "Music starts playing" else "Music stops playing"
        DeviceSwitch.DOCKED -> if (on) "Phone docked" else "Phone undocked"
        else -> "${switchLabel(switch)} turns ${if (on) "on" else "off"}"
    }

    private fun switchConstraint(switch: DeviceSwitch, on: Boolean): String = when (switch) {
        DeviceSwitch.HEADPHONES -> if (on) "While headphones are connected" else "While no headphones are connected"
        DeviceSwitch.VPN -> if (on) "While a VPN is connected" else "While no VPN is connected"
        DeviceSwitch.MUSIC_PLAYING -> if (on) "While music is playing" else "While no music is playing"
        DeviceSwitch.DOCKED -> if (on) "While docked" else "While not docked"
        DeviceSwitch.SCREEN_ON -> if (on) "While the screen is on" else "While the screen is off"
        DeviceSwitch.DEVICE_LOCKED -> if (on) "While the phone is locked" else "While the phone is unlocked"
        else -> {
            // Common nouns read better in lower case mid-sentence; names (Wi-Fi, NFC…) keep theirs.
            val name = when (switch) {
                DeviceSwitch.AIRPLANE_MODE, DeviceSwitch.BATTERY_SAVER, DeviceSwitch.DARK_THEME,
                DeviceSwitch.AUTO_ROTATE, DeviceSwitch.LOCATION,
                -> switchLabel(switch).lowercase(Locale.ROOT)
                else -> switchLabel(switch)
            }
            "While $name is ${if (on) "on" else "off"}"
        }
    }

    private fun place(config: Located, label: String?): String = when {
        !label.isNullOrBlank() -> " at $label"
        config.hasLocation -> String.format(Locale.ROOT, " at %.3f, %.3f", config.latitude, config.longitude)
        else -> ""
    }
}
