package com.cadence.platform.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.cadence.platform.common.launchAsync
import com.cadence.platform.events.ExternalEventKind
import com.cadence.platform.receiver.ReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Receives geofence transitions from Play services and posts one bus event per area. A geofence
 * transition is one of the cases where Android allows starting a foreground service from the
 * background, so the engine is started too.
 */
class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Geofence error: ${GeofenceStatusCodes.getStatusCodeString(event.errorCode)}")
            return
        }
        val transition = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> TRANSITION_ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> TRANSITION_EXIT
            else -> return
        }
        val entryPoint = EntryPointAccessors.fromApplication(context, ReceiverEntryPoint::class.java)
        event.triggeringGeofences.orEmpty().forEach { geofence ->
            entryPoint.externalTriggerBus().post(
                ExternalEventKind.GEOFENCE,
                mapOf(DATA_KEY to geofence.requestId, DATA_TRANSITION to transition),
            )
        }
        launchAsync(entryPoint.applicationScope()) { entryPoint.engineServiceController().startIfNeeded() }
    }

    companion object {
        private const val TAG = "CadenceGeofence"
        const val DATA_KEY = "geofenceKey"
        const val DATA_TRANSITION = "transition"
        const val TRANSITION_ENTER = "enter"
        const val TRANSITION_EXIT = "exit"
    }
}
