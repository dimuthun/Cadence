package com.cadence.domain.variable

import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.permission.PermissionRequirement
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** A user variable that persists across runs and macros. Values are text; numbers are parsed when needed. */
data class GlobalVariable(val name: String, val value: String, val updatedAt: Instant)

/** Where a variable written by an action lives. */
@Serializable
enum class VariableScope {
    /** Only for the current run of the macro; gone when it finishes. */
    LOCAL,

    /** Saved, and visible to every macro. */
    GLOBAL,
}

/** Storage of global variables. Implemented with Room. */
interface GlobalVariableRepository {
    fun observeAll(): Flow<List<GlobalVariable>>
    suspend fun get(name: String): String?
    suspend fun set(name: String, value: String)
    suspend fun delete(name: String)
}

/** Read-only values that come from the device (battery, location, time…). */
interface SystemVariableSource {
    /**
     * Current value of [key] (e.g. `battery.level`), `""` if it's known but unavailable right now
     * (e.g. no location permission), or `null` if [key] isn't a system variable.
     */
    suspend fun value(key: String, context: ExecutionContext): String?
}

/** Naming rules shared by the editor, the actions, and the resolver. */
object VariableNames {
    private val USER_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]{0,39}$")
    private val ANY_NAME = Regex("^[A-Za-z_][A-Za-z0-9_.]{0,79}$")

    /** User variables: letters, digits and underscores, no dots (dots are reserved for built-ins). */
    fun isValidUserName(name: String): Boolean = USER_NAME.matches(name)

    /** Any readable name, including built-ins like `battery.level`. */
    fun isValidReference(name: String): Boolean = ANY_NAME.matches(name)
}

/**
 * The built-in variables, for the variable picker and for permission checks. Values come from
 * [SystemVariableSource] (device), the trigger, or earlier actions in the same run.
 */
object SystemVariables {

    data class Definition(val key: String, val description: String, val group: String)

    const val GROUP_RUN = "This run"

    val all: List<Definition> = listOf(
        Definition("macro.name", "Name of the running macro", GROUP_RUN),
        Definition("macro.id", "Internal id of the running macro", GROUP_RUN),
        Definition("macro.category", "Category of the running macro", GROUP_RUN),
        Definition("macro.last_run", "When this macro last ran before now (ISO 8601; empty if never)", GROUP_RUN),
        Definition("trigger.time", "When the trigger fired (ISO 8601)", GROUP_RUN),
        Definition("trigger.battery", "Battery level reported by a battery trigger", GROUP_RUN),
        Definition("trigger.package", "App package from an app or notification trigger", GROUP_RUN),
        Definition("trigger.deviceName", "Bluetooth device name from a Bluetooth trigger", GROUP_RUN),
        Definition("trigger.place", "Place name from a location area trigger", GROUP_RUN),
        Definition("trigger.movedMeters", "Distance from a distance-moved trigger", GROUP_RUN),
        Definition("trigger.app", "App name from an app installed or updated trigger", GROUP_RUN),
        Definition("trigger.networkType", "wifi, cellular, ethernet, other or none, from a network trigger", GROUP_RUN),
        Definition("trigger.ip", "New IP address from a network trigger", GROUP_RUN),
        Definition("trigger.lux", "Light level from a light trigger", GROUP_RUN),
        Definition("trigger.temperature", "Battery °C from a battery temperature trigger", GROUP_RUN),
        Definition("http.status", "Status code of the last HTTP request (e.g. 200)", GROUP_RUN),
        Definition("http.body", "Response body of the last HTTP request", GROUP_RUN),
        Definition("http.content_type", "Content type of the last HTTP response", GROUP_RUN),
        Definition("battery.level", "Battery level, 0–100", "Battery"),
        Definition("battery.charging", "true while plugged in", "Battery"),
        Definition("battery.plug", "ac, usb, wireless or none", "Battery"),
        Definition("battery.temperature", "Battery temperature in °C", "Battery"),
        Definition("battery.health", "good, overheat, cold, dead, over_voltage, failure or unknown", "Battery"),
        Definition("battery.voltage", "Battery voltage in volts, e.g. 4.12", "Battery"),
        Definition("battery.current_ma", "Charge (+) or drain (−) current in mA, where the phone reports it", "Battery"),
        Definition("battery.saver", "true while battery saver is on", "Battery"),
        Definition("network.type", "wifi, cellular, ethernet, vpn or none", "Network"),
        Definition("network.ip", "This phone's local IP address", "Network"),
        Definition("network.carrier", "Mobile network name, e.g. Dialog", "Network"),
        Definition("network.roaming", "true while roaming on mobile data", "Network"),
        Definition("network.vpn", "true while a VPN is active", "Network"),
        Definition("network.airplane_mode", "true while airplane mode is on", "Network"),
        Definition("network.mobile_data", "true while mobile data is switched on (if the phone reports it)", "Network"),
        Definition("wifi.signal_dbm", "Wi-Fi signal strength in dBm, e.g. -55", "Network"),
        Definition("wifi.signal_level", "Wi-Fi signal bars, 0–4", "Network"),
        Definition("bluetooth.enabled", "true while Bluetooth is on", "Bluetooth & NFC"),
        Definition("bluetooth.connected_devices", "Names of connected audio devices, comma-separated (needs Nearby devices)", "Bluetooth & NFC"),
        Definition("nfc.enabled", "true while NFC is on", "Bluetooth & NFC"),
        Definition("display.brightness", "Screen brightness, 0–100", "Display"),
        Definition("display.brightness_auto", "true while adaptive brightness is on", "Display"),
        Definition("display.auto_rotate", "true while auto-rotate is on", "Display"),
        Definition("display.orientation", "portrait or landscape", "Display"),
        Definition("display.dark_mode", "true while dark theme is on", "Display"),
        Definition("audio.dnd", "Do Not Disturb: off, priority, alarms or silence", "Sound"),
        Definition("audio.volume_ring", "Ring volume, 0–100", "Sound"),
        Definition("audio.volume_notification", "Notification volume, 0–100", "Sound"),
        Definition("audio.volume_media", "Media volume, 0–100", "Sound"),
        Definition("audio.volume_alarm", "Alarm volume, 0–100", "Sound"),
        Definition("audio.volume_call", "Call volume, 0–100", "Sound"),
        Definition("audio.headphones", "true while wired, USB or Bluetooth audio is connected", "Sound"),
        Definition("audio.music_playing", "true while any app is playing audio", "Sound"),
        Definition("music.title", "Title of the playing track (needs notification access)", "Sound"),
        Definition("music.artist", "Artist of the playing track (needs notification access)", "Sound"),
        Definition("music.album", "Album of the playing track (needs notification access)", "Sound"),
        Definition("music.app", "Package of the app playing it (needs notification access)", "Sound"),
        Definition("location.latitude", "Last known latitude", "Location"),
        Definition("location.longitude", "Last known longitude", "Location"),
        Definition("location.altitude", "Altitude in meters (if known)", "Location"),
        Definition("location.speed", "Speed in m/s (if moving)", "Location"),
        Definition("location.speed_kmh", "Speed in km/h (if moving)", "Location"),
        Definition("location.bearing", "Direction of travel in degrees (if moving)", "Location"),
        Definition("location.accuracy", "Accuracy radius in meters", "Location"),
        Definition("location.time", "When the location was measured (ISO 8601)", "Location"),
        Definition("location.age_seconds", "How old the location is, in seconds", "Location"),
        Definition("location.map_url", "Link to the location on a map", "Location"),
        Definition("time.now", "Date and time, e.g. 2026-11-11 14:05:09", "Date & time"),
        Definition("time.date", "Date, e.g. 2026-11-11", "Date & time"),
        Definition("time.time", "Time, e.g. 14:05", "Date & time"),
        Definition("time.hour", "Hour, 0–23", "Date & time"),
        Definition("time.minute", "Minute, 0–59", "Date & time"),
        Definition("time.weekday", "Day of the week, e.g. Wednesday", "Date & time"),
        Definition("time.epoch_seconds", "Seconds since 1970-01-01 UTC", "Date & time"),
        Definition("time.epoch_millis", "Milliseconds since 1970-01-01 UTC", "Date & time"),
        Definition("time.time_12h", "12-hour time, e.g. 2:05 PM", "Date & time"),
        Definition("time.day", "Day of the month, 1–31", "Date & time"),
        Definition("time.month", "Month number, 1–12", "Date & time"),
        Definition("time.month_name", "Month name, e.g. November", "Date & time"),
        Definition("time.year", "Year, e.g. 2026", "Date & time"),
        Definition("time.week", "ISO week of the year, 1–53", "Date & time"),
        Definition("time.timezone", "Time zone, e.g. Asia/Colombo", "Date & time"),
        Definition("wifi.connected", "true while on Wi-Fi", "Device"),
        Definition("device.screen_on", "true while the screen is on", "Device"),
        Definition("device.ringer_mode", "normal, vibrate or silent", "Device"),
        Definition("device.model", "Phone model", "Device"),
        Definition("device.manufacturer", "Phone manufacturer", "Device"),
        Definition("device.android_version", "Android version, e.g. 14", "Device"),
        Definition("device.api_level", "Android API level, e.g. 34", "Device"),
        Definition("device.name", "Device name set in Settings", "Device"),
        Definition("device.locked", "true while the lock screen is showing", "Device"),
        Definition("device.uptime_minutes", "Minutes since the phone last started", "Device"),
        Definition("device.language", "Language tag, e.g. en-GB", "Device"),
        Definition("storage.free_gb", "Free internal storage in GB", "Device"),
        Definition("storage.total_gb", "Total internal storage in GB", "Device"),
        Definition("memory.free_mb", "Available memory (RAM) in MB", "Device"),
        Definition("memory.total_mb", "Total memory (RAM) in MB", "Device"),
    )

    private val LOCATION_PERMISSIONS = setOf(PermissionRequirement.FINE_LOCATION, PermissionRequirement.BACKGROUND_LOCATION)

    /**
     * Permissions needed to read [key]: location values need location access in the background,
     * and track details need notification access.
     */
    fun requirementsFor(key: String): Set<PermissionRequirement> = when {
        key.startsWith("location.") -> LOCATION_PERMISSIONS
        key.startsWith("music.") -> setOf(PermissionRequirement.NOTIFICATION_LISTENER)
        else -> emptySet()
    }
}
