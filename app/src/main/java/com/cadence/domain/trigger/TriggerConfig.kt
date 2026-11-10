package com.cadence.domain.trigger

import com.cadence.domain.model.Weekday
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Persisted configuration of a trigger: the event that starts a macro.
 *
 * Configs are plain serializable data. The Android code that listens for the event lives in a
 * matching [com.cadence.domain.plugin.TriggerHandler].
 *
 * Every [SerialName] is part of the storage and export format. Never change an existing one;
 * add a new subtype instead.
 */
@Serializable
sealed interface TriggerConfig {

    /**
     * Run started by the user (editor "test run" now; widgets and quick-settings tiles in Phase 2).
     * Handled by the engine itself rather than by a registered handler.
     */
    @Serializable
    @SerialName("trigger.manual")
    data object Manual : TriggerConfig

    /**
     * Fires at [hour]:[minute] local time.
     *
     * @property days days on which to fire; empty means every day.
     */
    @Serializable
    @SerialName("trigger.time_of_day")
    data class TimeOfDay(
        val hour: Int,
        val minute: Int,
        val days: Set<Weekday> = emptySet(),
    ) : TriggerConfig {
        init {
            require(hour in 0..23) { "hour must be 0..23, was $hour" }
            require(minute in 0..59) { "minute must be 0..59, was $minute" }
        }
    }

    /** Fires when the battery level crosses [thresholdPercent] in [direction]. */
    @Serializable
    @SerialName("trigger.battery_level")
    data class BatteryLevel(
        val thresholdPercent: Int,
        val direction: Direction,
    ) : TriggerConfig {
        init {
            require(thresholdPercent in 1..99) { "thresholdPercent must be 1..99, was $thresholdPercent" }
        }

        @Serializable
        enum class Direction { FALLS_BELOW, RISES_ABOVE }
    }

    /** Fires when a charger is connected or disconnected. */
    @Serializable
    @SerialName("trigger.power_connection")
    data class PowerConnection(val event: ConnectionEvent) : TriggerConfig

    /**
     * Fires when the device joins or leaves any Wi-Fi network.
     *
     * Specific networks (SSIDs) are deliberately unsupported: Android only reveals the SSID to
     * background apps holding background location, which Cadence doesn't request.
     */
    @Serializable
    @SerialName("trigger.wifi_connection")
    data class WifiConnection(val event: ConnectionEvent) : TriggerConfig

    /**
     * Fires when a Bluetooth device connects or disconnects.
     *
     * @property deviceAddress restrict to one device by MAC address; `null` matches any device.
     * @property deviceName display name captured when the device was picked (shown in the UI only).
     */
    @Serializable
    @SerialName("trigger.bluetooth_connection")
    data class BluetoothConnection(
        val event: ConnectionEvent,
        val deviceAddress: String? = null,
        val deviceName: String? = null,
    ) : TriggerConfig

    /** Fires when the app [packageName] comes to the foreground or leaves it. */
    @Serializable
    @SerialName("trigger.app_lifecycle")
    data class AppLifecycle(
        val packageName: String,
        val event: Event,
    ) : TriggerConfig {
        @Serializable
        enum class Event { LAUNCHED, CLOSED }
    }

    /** Fires once after the device finishes booting. */
    @Serializable
    @SerialName("trigger.boot_completed")
    data object BootCompleted : TriggerConfig

    /** Fires when the screen turns on or off, or when the user unlocks the phone. */
    @Serializable
    @SerialName("trigger.screen_state")
    data class ScreenState(val event: Event) : TriggerConfig {
        @Serializable
        enum class Event { ON, OFF, UNLOCKED }
    }

    /**
     * Fires when [packageName] posts a notification.
     *
     * @property textContains only match notifications whose title or text contains this value
     * (case-insensitive); `null` matches all.
     */
    @Serializable
    @SerialName("trigger.notification_posted")
    data class NotificationPosted(
        val packageName: String,
        val textContains: String? = null,
    ) : TriggerConfig

    /**
     * Fires once at [startAtEpochMillis], or, with [repeatIntervalMinutes], at that moment and
     * then every interval after it.
     *
     * Intervals are fixed lengths of elapsed time, so an "every 24 hours" trigger shifts by an hour
     * when daylight saving time changes. Use [TimeOfDay] for "every day at 08:00".
     */
    @Serializable
    @SerialName("trigger.date_time")
    data class DateTime(
        val startAtEpochMillis: Long,
        val repeatIntervalMinutes: Int? = null,
    ) : TriggerConfig {
        init {
            require(repeatIntervalMinutes == null || repeatIntervalMinutes in 1..MAX_INTERVAL_MINUTES) {
                "repeatIntervalMinutes must be 1..$MAX_INTERVAL_MINUTES, was $repeatIntervalMinutes"
            }
        }

        companion object {
            const val MAX_INTERVAL_MINUTES = 366 * 24 * 60
        }
    }

    /**
     * Fires when the phone arrives in or leaves a circular area (a geofence).
     *
     * @property label optional place name shown in the UI, e.g. "Home".
     */
    @Serializable
    @SerialName("trigger.location_area")
    data class LocationArea(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Int,
        val event: Event,
        val label: String? = null,
    ) : TriggerConfig {
        init {
            require(latitude in -90.0..90.0) { "latitude must be -90..90, was $latitude" }
            require(longitude in -180.0..180.0) { "longitude must be -180..180, was $longitude" }
            require(radiusMeters in MIN_RADIUS_METERS..MAX_RADIUS_METERS) { "radiusMeters out of range: $radiusMeters" }
        }

        @Serializable
        enum class Event { ENTER, EXIT }

        companion object {
            /** Geofences smaller than about 100 m are unreliable. */
            const val MIN_RADIUS_METERS = 100
            const val MAX_RADIUS_METERS = 50_000
        }
    }

    /**
     * Fires each time the phone has moved at least [distanceMeters] (in a straight line) from
     * where it was when the trigger last fired. The first location after registering is the
     * starting point and never fires.
     */
    @Serializable
    @SerialName("trigger.location_moved")
    data class LocationMoved(val distanceMeters: Int) : TriggerConfig {
        init {
            require(distanceMeters in MIN_DISTANCE_METERS..MAX_DISTANCE_METERS) { "distanceMeters out of range: $distanceMeters" }
        }

        companion object {
            const val MIN_DISTANCE_METERS = 100
            const val MAX_DISTANCE_METERS = 100_000
        }
    }
}

/** Shared connect/disconnect event for connection-style triggers. */
@Serializable
enum class ConnectionEvent { CONNECTED, DISCONNECTED }
