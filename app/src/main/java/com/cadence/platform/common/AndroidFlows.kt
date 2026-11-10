package com.cadence.platform.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Cold flow of broadcasts matching [filter]. The receiver is registered on collection and
 * unregistered when collection stops, so it can't leak. Sticky broadcasts (e.g. battery) deliver
 * their current value first.
 *
 * @param exported `true` only for protected system broadcasts sent from another system uid
 * (e.g. Bluetooth). Everything else uses `RECEIVER_NOT_EXPORTED`.
 */
fun Context.broadcastFlow(filter: IntentFilter, exported: Boolean = false): Flow<Intent> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(intent)
        }
    }
    val flags = if (exported) ContextCompat.RECEIVER_EXPORTED else ContextCompat.RECEIVER_NOT_EXPORTED
    ContextCompat.registerReceiver(this@broadcastFlow, receiver, filter, flags)
    awaitClose { unregisterReceiver(receiver) }
}

/**
 * Runs [block] off the main thread while keeping the broadcast alive (`goAsync`). The block
 * should finish within a few seconds.
 */
fun BroadcastReceiver.launchAsync(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
