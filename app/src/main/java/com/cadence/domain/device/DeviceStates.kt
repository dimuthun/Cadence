package com.cadence.domain.device

import kotlinx.serialization.Serializable

/**
 * An on/off device state that triggers and constraints can watch. Enum names are part of the
 * storage format; never rename one.
 *
 * @property triggerable `false` for states Android can't report reliably as events (they can
 * still be used as constraints).
 */
@Serializable
enum class DeviceSwitch(val triggerable: Boolean = true) {
    AIRPLANE_MODE,
    BATTERY_SAVER,
    DARK_THEME,

    /** On means any Do Not Disturb mode (priority only, alarms only, or total silence). */
    DO_NOT_DISTURB,
    AUTO_ROTATE,

    /** The Wi-Fi radio, not whether a network is connected. */
    WIFI,
    BLUETOOTH,
    NFC,

    /** Location services in Settings. */
    LOCATION,
    VPN,

    /** Wired, USB or Bluetooth audio output. */
    HEADPHONES,
    MUSIC_PLAYING,

    /** In a desk or car dock (rare on modern phones). */
    DOCKED,

    /** Use the Screen on/off trigger for events. */
    SCREEN_ON(triggerable = false),

    /** The lock can engage seconds after the screen turns off, so there's no reliable event. */
    DEVICE_LOCKED(triggerable = false),
}

/** The kind of network the phone's internet traffic goes over. */
@Serializable
enum class NetworkType { WIFI, CELLULAR, ETHERNET, OTHER, NONE }

/** How the phone is lying or being held, from the accelerometer. */
@Serializable
enum class DevicePosition { FACE_UP, FACE_DOWN, PORTRAIT, LANDSCAPE }
