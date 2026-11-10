package com.cadence.domain.permission

/**
 * A runtime or special permission a trigger, action, or constraint needs.
 *
 * Plugins declare these instead of checking Android APIs directly. The engine skips anything
 * whose requirements aren't met, and the Permissions center maps each value to its
 * explanation screen and grant intent.
 */
enum class PermissionRequirement {
    /** `POST_NOTIFICATIONS` on Android 13+. */
    POST_NOTIFICATIONS,

    /** `SCHEDULE_EXACT_ALARM` / `canScheduleExactAlarms()` on Android 12+. */
    EXACT_ALARM,

    /** Usage access (`PACKAGE_USAGE_STATS`) for app launched/closed triggers. */
    USAGE_ACCESS,

    /** Notification listener access for notification triggers. */
    NOTIFICATION_LISTENER,

    /** "Modify system settings" (`WRITE_SETTINGS`) for brightness. */
    WRITE_SETTINGS,

    /** Do Not Disturb policy access for silent ringer mode. */
    DO_NOT_DISTURB_ACCESS,

    /** `BLUETOOTH_CONNECT` on Android 12+. */
    BLUETOOTH_CONNECT,

    /** `ACCESS_FINE_LOCATION`, for location triggers only. */
    FINE_LOCATION,

    /** `ACCESS_BACKGROUND_LOCATION` ("Allow all the time") on Android 10+, for location triggers only. */
    BACKGROUND_LOCATION,
}

/** Reports whether a [PermissionRequirement] is currently granted. */
fun interface PermissionStatusProvider {
    /** Returns `true` when [requirement] is granted right now. */
    fun isGranted(requirement: PermissionRequirement): Boolean
}
