package com.cadence.platform.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.domain.trigger.DeviceTrigger
import com.cadence.platform.common.broadcastFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn

/**
 * App install, removal and update events from one shared receiver.
 *
 * Android 11+ only reports apps visible to Cadence, which declares launcher apps in its
 * `<queries>`; apps without a home-screen icon may not be reported. Cadence deliberately
 * doesn't request `QUERY_ALL_PACKAGES`.
 */
@Singleton
class AppPackageMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    data class Change(val event: DeviceTrigger.AppPackageChanged.Event, val packageName: String, val label: String?)

    private val packageManager = context.packageManager

    val changes: SharedFlow<Change> = context
        .broadcastFlow(
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
        )
        .mapNotNull { intent ->
            val packageName = intent.data?.schemeSpecificPart ?: return@mapNotNull null
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            val event = when (intent.action) {
                // An update sends ADDED and REMOVED with EXTRA_REPLACING, then REPLACED.
                Intent.ACTION_PACKAGE_ADDED -> if (replacing) null else DeviceTrigger.AppPackageChanged.Event.INSTALLED
                Intent.ACTION_PACKAGE_REMOVED -> if (replacing) null else DeviceTrigger.AppPackageChanged.Event.REMOVED
                Intent.ACTION_PACKAGE_REPLACED -> DeviceTrigger.AppPackageChanged.Event.UPDATED
                else -> null
            } ?: return@mapNotNull null
            Change(event, packageName, label(packageName))
        }
        .shareIn(scope, SharingStarted.WhileSubscribed())

    private fun label(packageName: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null // Already removed.
    }
}
