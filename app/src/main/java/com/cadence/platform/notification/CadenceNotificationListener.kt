package com.cadence.platform.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.cadence.platform.events.ExternalEventKind
import com.cadence.platform.events.ExternalTriggerBus
import com.cadence.platform.trigger.NotificationPostedTriggerHandler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Forwards posted notifications to notification triggers. The system binds it once the user
 * grants notification access. Notification content stays in memory and is never stored.
 */
@AndroidEntryPoint
class CadenceNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var bus: ExternalTriggerBus

    /** Last title+text per notification key, so updates of an unchanged notification don't re-fire. */
    private val lastContent = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > MAX_TRACKED
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification
        if (sbn.isOngoing || notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val content = "$title\u0000$text"
        if (lastContent.put(sbn.key, content) == content) return

        bus.post(
            ExternalEventKind.NOTIFICATION,
            mapOf(
                NotificationPostedTriggerHandler.DATA_PACKAGE to sbn.packageName,
                NotificationPostedTriggerHandler.DATA_TITLE to title,
                NotificationPostedTriggerHandler.DATA_TEXT to text,
            ),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        lastContent.remove(sbn.key)
    }

    private companion object {
        const val MAX_TRACKED = 200
    }
}
