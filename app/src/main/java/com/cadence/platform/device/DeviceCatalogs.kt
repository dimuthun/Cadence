package com.cadence.platform.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.cadence.core.coroutines.IoDispatcher
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.platform.permission.AndroidPermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** An app the user can pick in the editor. */
data class InstalledApp(val packageName: String, val label: String)

/** A paired Bluetooth device the user can pick in the editor. */
data class PairedDevice(val address: String, val name: String)

/**
 * Lists launchable apps. Visibility comes from the manifest's launcher `<queries>`, so no
 * `QUERY_ALL_PACKAGES` is needed.
 */
@Singleton
class InstalledAppsProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun launchableApps(): List<InstalledApp> = withContext(ioDispatcher) {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }
        activities
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

/** Lists paired Bluetooth devices. Returns an empty list without the Nearby devices permission. */
@Singleton
class PairedDevicesProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val permissionChecker: AndroidPermissionChecker,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    @SuppressLint("MissingPermission") // Checked via permissionChecker; SecurityException handled.
    suspend fun pairedDevices(): List<PairedDevice> = withContext(ioDispatcher) {
        if (!permissionChecker.isGranted(PermissionRequirement.BLUETOOTH_CONNECT)) return@withContext emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return@withContext emptyList()
        try {
            adapter.bondedDevices.orEmpty()
                .map { PairedDevice(it.address, it.name ?: it.address) }
                .sortedBy { it.name.lowercase() }
        } catch (e: SecurityException) {
            emptyList()
        }
    }
}
