package com.cadence.platform.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cadence.platform.common.launchAsync
import com.cadence.platform.events.ExternalEventKind
import dagger.hilt.android.EntryPointAccessors

/**
 * Handles system broadcasts that may be declared in the manifest: boot, app update, clock or
 * time zone changes, and exact-alarm access changes. Each one may start the engine service from
 * the background.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val entryPoint = EntryPointAccessors.fromApplication(context, ReceiverEntryPoint::class.java)
        val controller = entryPoint.engineServiceController()
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> entryPoint.externalTriggerBus().post(ExternalEventKind.BOOT)
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> controller.onScheduleEnvironmentChanged()
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        launchAsync(entryPoint.applicationScope()) { controller.startIfNeeded() }
    }
}
