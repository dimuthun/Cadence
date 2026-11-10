package com.cadence.platform.action

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.cadence.platform.notification.CadenceNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens activities on behalf of actions while respecting Android 10+'s background activity
 * start limits.
 *
 * Opens the activity directly when Cadence is in the foreground or holds "Display over other
 * apps". Otherwise it posts a tap-to-open prompt notification, since Android would silently
 * block a direct start.
 */
@Singleton
class ActivityLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notifications: CadenceNotifications,
) {
    sealed interface Outcome {
        data object Started : Outcome
        data object PromptPosted : Outcome
        data class Failed(val reason: String) : Outcome
    }

    suspend fun launch(intent: Intent, promptTitle: String, promptText: String): Outcome {
        val target = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (target.resolveActivity(context.packageManager) == null) {
            return Outcome.Failed("No app on this device can open it")
        }
        if (canStartDirectly()) {
            return try {
                withContext(Dispatchers.Main) { context.startActivity(target) }
                Outcome.Started
            } catch (e: ActivityNotFoundException) {
                Outcome.Failed("No app on this device can open it")
            }
        }
        return if (notifications.postActionPrompt(promptTitle, promptText, target)) {
            Outcome.PromptPosted
        } else {
            Outcome.Failed("Android blocks this in the background and Cadence notifications are turned off")
        }
    }

    private suspend fun canStartDirectly(): Boolean {
        val inForeground = withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        return inForeground || Settings.canDrawOverlays(context)
    }
}
