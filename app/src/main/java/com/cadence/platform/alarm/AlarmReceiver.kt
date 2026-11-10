package com.cadence.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cadence.platform.common.launchAsync
import com.cadence.platform.events.ExternalEventKind
import com.cadence.platform.receiver.ReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Receives Cadence's exact alarms, posts them to the trigger bus, and makes sure the engine is
 * running. An exact-alarm callback is one of the cases where Android allows starting a foreground
 * service from the background.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val entryPoint = EntryPointAccessors.fromApplication(context, ReceiverEntryPoint::class.java)
        entryPoint.externalTriggerBus().post(
            ExternalEventKind.ALARM,
            mapOf(DATA_KEY to key, DATA_SCHEDULED_AT to intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L).toString()),
        )
        launchAsync(entryPoint.applicationScope()) {
            entryPoint.engineServiceController().startIfNeeded()
        }
    }

    companion object {
        const val EXTRA_KEY = "com.cadence.extra.ALARM_KEY"
        const val EXTRA_SCHEDULED_AT = "com.cadence.extra.SCHEDULED_AT"

        /** Keys in the posted event's data. */
        const val DATA_KEY = "alarmKey"
        const val DATA_SCHEDULED_AT = "scheduledAt"
    }
}
