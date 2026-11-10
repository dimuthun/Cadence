package com.cadence.platform.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cadence.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Notification channels and builders used across Cadence. */
@Singleton
class CadenceNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)
    private val nextId = AtomicInteger(FIRST_DYNAMIC_ID)

    /** Creates all channels. Safe to call repeatedly. */
    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_ENGINE_STATUS, NotificationManagerCompat.IMPORTANCE_MIN)
                    .setName(context.getString(R.string.channel_engine_status_name))
                    .setDescription(context.getString(R.string.channel_engine_status_description))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_MACRO, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.channel_macro_notifications_name))
                    .setDescription(context.getString(R.string.channel_macro_notifications_description))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_ACTION_REQUIRED, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.channel_action_required_name))
                    .setDescription(context.getString(R.string.channel_action_required_description))
                    .build(),
            ),
        )
    }

    /** Ongoing, minimum-priority notification for the engine's foreground service. */
    fun engineStatus(activeMacros: Int): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_ENGINE_STATUS)
            .setSmallIcon(R.drawable.ic_stat_cadence)
            .setContentTitle(context.getString(R.string.engine_status_title))
            .setContentText(context.resources.getQuantityString(R.plurals.engine_status_text, activeMacros, activeMacros))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()

    /** Replaces the engine status notification if notifications are allowed. */
    @SuppressLint("MissingPermission") // Checked via areNotificationsEnabled.
    fun updateEngineStatus(activeMacros: Int) {
        if (manager.areNotificationsEnabled()) manager.notify(ENGINE_STATUS_ID, engineStatus(activeMacros))
    }

    /** Posts a macro's own notification. Returns `false` if notifications are blocked. */
    @SuppressLint("MissingPermission") // Checked via areNotificationsEnabled.
    fun postMacroNotification(title: String, text: String): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        val notification = NotificationCompat.Builder(context, CHANNEL_MACRO)
            .setSmallIcon(R.drawable.ic_stat_cadence)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        manager.notify(nextId.getAndIncrement(), notification)
        return true
    }

    /**
     * Posts a high-priority prompt that opens [target] when tapped, for actions Android won't let
     * Cadence perform from the background. Returns `false` if notifications are blocked.
     */
    @SuppressLint("MissingPermission") // Checked via areNotificationsEnabled.
    fun postActionPrompt(title: String, text: String, target: Intent): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        val id = nextId.getAndIncrement()
        val tap = PendingIntent.getActivity(
            context,
            id,
            target,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ACTION_REQUIRED)
            .setSmallIcon(R.drawable.ic_stat_cadence)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager.notify(id, notification)
        return true
    }

    private fun openAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_ENGINE_STATUS = "engine_status"
        const val CHANNEL_MACRO = "macro_notifications"
        const val CHANNEL_ACTION_REQUIRED = "action_required"
        const val ENGINE_STATUS_ID = 1
        private const val FIRST_DYNAMIC_ID = 1_000
    }
}
