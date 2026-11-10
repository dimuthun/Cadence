package com.cadence.platform.permission

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.platform.notification.CadenceNotificationListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prominent disclosure for location access (Google Play User Data policy): says what is
 * collected, why, and that it happens in the background, before the system prompt appears.
 */
private const val LOCATION_DISCLOSURE =
    "Cadence collects location data to run your location triggers (arriving at or leaving a place, and " +
        "distance moved), even when the app is closed or not in use. Location is only used on this " +
        "device to check your triggers; it isn't stored in the log or sent anywhere."

/**
 * Every permission or special access Cadence may ask for, with the text shown in the
 * Permissions center and explanation dialogs, and how to grant it.
 *
 * @property requirement the engine-level requirement, or `null` for optional access that
 * improves reliability but never blocks a macro.
 */
enum class SpecialAccess(
    val requirement: PermissionRequirement?,
    val title: String,
    val rationale: String,
) {
    NOTIFICATIONS(
        PermissionRequirement.POST_NOTIFICATIONS,
        "Notifications",
        "Lets macros show notifications, and lets Cadence post tap-to-open prompts when Android blocks an action in the background.",
    ),
    EXACT_ALARMS(
        PermissionRequirement.EXACT_ALARM,
        "Alarms & reminders",
        "Lets time-of-day triggers fire at exactly the time you set, even when the phone is idle.",
    ),
    USAGE_ACCESS(
        PermissionRequirement.USAGE_ACCESS,
        "Usage access",
        "Lets \"App opened or closed\" triggers see which app is in the foreground. Cadence only checks this while such a macro is enabled.",
    ),
    NOTIFICATION_ACCESS(
        PermissionRequirement.NOTIFICATION_LISTENER,
        "Notification access",
        "Lets \"Notification received\" triggers react to other apps' notifications. Notification content is only matched in memory and never saved.",
    ),
    MODIFY_SETTINGS(
        PermissionRequirement.WRITE_SETTINGS,
        "Modify system settings",
        "Lets the brightness action change screen brightness.",
    ),
    DO_NOT_DISTURB(
        PermissionRequirement.DO_NOT_DISTURB_ACCESS,
        "Do Not Disturb access",
        "Lets ringer actions switch to silent and change volume while Do Not Disturb is on.",
    ),
    NEARBY_DEVICES(
        PermissionRequirement.BLUETOOTH_CONNECT,
        "Nearby devices",
        "Lets Bluetooth triggers detect when a device connects, and lets you pick a paired device.",
    ),
    LOCATION(
        PermissionRequirement.FINE_LOCATION,
        "Location",
        LOCATION_DISCLOSURE,
    ),
    BACKGROUND_LOCATION(
        PermissionRequirement.BACKGROUND_LOCATION,
        "Location all the time",
        LOCATION_DISCLOSURE + " Choose \"Allow all the time\" on the next screen; with \"Only while using the app\" location triggers won't fire.",
    ),
    DISPLAY_OVER_APPS(
        null,
        "Display over other apps (optional)",
        "Android blocks apps from opening screens in the background. With this, \"Launch app\" and \"Open URL\" open directly instead of posting a tap-to-open notification.",
    ),
    UNRESTRICTED_BATTERY(
        null,
        "Unrestricted battery use (recommended)",
        "Stops Android from pausing Cadence in the background, which would delay or skip your triggers.",
    ),
    ;

    /** Runtime permission to request with the system dialog, or `null` if granted in Settings. */
    val runtimePermission: String?
        get() = when (this) {
            NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
            NEARBY_DEVICES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null
            LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
            // Android 11+ shows a Settings page for this request; it needs foreground location first.
            BACKGROUND_LOCATION ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.ACCESS_BACKGROUND_LOCATION else Manifest.permission.ACCESS_FINE_LOCATION
            else -> null
        }

    /**
     * Google Play requires an in-app disclosure right before every request for this access, not
     * just the first one.
     */
    val requiresProminentDisclosure: Boolean
        get() = this == LOCATION || this == BACKGROUND_LOCATION

    /** The Settings screen where the user grants this access. */
    fun settingsIntent(context: Context): Intent {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        return when (this) {
            NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
            } else {
                appDetails(packageUri)
            }
            USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            NOTIFICATION_ACCESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, CadenceNotificationListener::class.java).flattenToString(),
                )
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            MODIFY_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri)
            DO_NOT_DISTURB -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            NEARBY_DEVICES, LOCATION, BACKGROUND_LOCATION -> appDetails(packageUri)
            DISPLAY_OVER_APPS -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
            UNRESTRICTED_BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }

    companion object {
        /** App info screen, used as a fallback when a specific screen isn't available. */
        fun appDetails(context: Context): Intent =
            appDetails(Uri.fromParts("package", context.packageName, null))

        private fun appDetails(packageUri: Uri) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)

        /** The entry for an engine [requirement]. */
        fun of(requirement: PermissionRequirement): SpecialAccess = entries.first { it.requirement == requirement }
    }
}

/** Checks [SpecialAccess] status, including the optional entries the engine doesn't track. */
@Singleton
class SpecialAccessChecker @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val permissionChecker: AndroidPermissionChecker,
) {
    fun isGranted(access: SpecialAccess): Boolean = when (access) {
        SpecialAccess.DISPLAY_OVER_APPS -> Settings.canDrawOverlays(context)
        SpecialAccess.UNRESTRICTED_BATTERY ->
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        else -> permissionChecker.isGranted(requireNotNull(access.requirement))
    }
}
