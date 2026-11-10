package com.cadence.platform.permission

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.permission.PermissionStatusProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Checks [PermissionRequirement]s against the live system state. Cheap enough to call often. */
@Singleton
class AndroidPermissionChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PermissionStatusProvider {

    override fun isGranted(requirement: PermissionRequirement): Boolean = when (requirement) {
        PermissionRequirement.POST_NOTIFICATIONS ->
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        PermissionRequirement.EXACT_ALARM ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        PermissionRequirement.USAGE_ACCESS -> hasUsageAccess()

        PermissionRequirement.NOTIFICATION_LISTENER ->
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        PermissionRequirement.WRITE_SETTINGS -> Settings.System.canWrite(context)

        PermissionRequirement.DO_NOT_DISTURB_ACCESS ->
            context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

        PermissionRequirement.BLUETOOTH_CONNECT ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || isRuntimeGranted(Manifest.permission.BLUETOOTH_CONNECT)

        PermissionRequirement.FINE_LOCATION -> isRuntimeGranted(Manifest.permission.ACCESS_FINE_LOCATION)

        // Before Android 10, foreground location access also covered the background.
        PermissionRequirement.BACKGROUND_LOCATION ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isRuntimeGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                isRuntimeGranted(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }

    /** Every requirement currently granted. Used to detect changes when the app resumes. */
    fun grantedRequirements(): Set<PermissionRequirement> =
        PermissionRequirement.entries.filterTo(mutableSetOf(), ::isGranted)

    private fun isRuntimeGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION") // Replacement needs a newer API than minSdk; behaviour is unchanged.
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            isRuntimeGranted(Manifest.permission.PACKAGE_USAGE_STATS)
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
