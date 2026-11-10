package com.cadence.platform.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.platform.common.broadcastFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

/**
 * Screen on/off and unlock events from one shared receiver (these broadcasts can't be declared
 * in the manifest).
 */
@Singleton
class ScreenStateMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    /** Screen on/off and unlock events as they happen. */
    val events: SharedFlow<TriggerConfig.ScreenState.Event> = context
        .broadcastFlow(
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        .mapNotNull { intent ->
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> TriggerConfig.ScreenState.Event.ON
                Intent.ACTION_SCREEN_OFF -> TriggerConfig.ScreenState.Event.OFF
                Intent.ACTION_USER_PRESENT -> TriggerConfig.ScreenState.Event.UNLOCKED
                else -> null
            }
        }
        .shareIn(scope, SharingStarted.WhileSubscribed())

    /** `true` while the screen is on. Starts with the current state; may repeat values. */
    val interactive: Flow<Boolean> = events
        .map { it != TriggerConfig.ScreenState.Event.OFF }
        .onStart { emit(powerManager.isInteractive) }
}
