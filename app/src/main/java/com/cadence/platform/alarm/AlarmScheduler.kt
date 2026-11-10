package com.cadence.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cadence.domain.engine.EngineDiagnostics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules exact alarms that wake Cadence through [AlarmReceiver].
 *
 * Alarms are reference counted per key: several macros with the same time share one alarm, and
 * it's cancelled when the last one unregisters. Alarms survive process death (not reboot); after
 * a cold start the handlers acquire them again.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val diagnostics: EngineDiagnostics,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val refCounts = HashMap<String, Int>()

    /** Registers one user of [key] and schedules it for [at]. */
    @Synchronized
    fun acquire(key: String, at: Instant) {
        refCounts[key] = (refCounts[key] ?: 0) + 1
        schedule(key, at)
    }

    /** Moves [key] to [at] if anyone still uses it. */
    @Synchronized
    fun reschedule(key: String, at: Instant) {
        if ((refCounts[key] ?: 0) > 0) schedule(key, at)
    }

    /** Releases one user of [key] and cancels the alarm when none remain. */
    @Synchronized
    fun release(key: String) {
        val remaining = (refCounts[key] ?: 0) - 1
        if (remaining > 0) {
            refCounts[key] = remaining
        } else {
            refCounts.remove(key)
            alarmManager.cancel(pendingIntent(key, scheduledAt = 0))
        }
    }

    private fun schedule(key: String, at: Instant) {
        val operation = pendingIntent(key, at.toEpochMilli())
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
        } catch (e: SecurityException) {
            // Exact alarm access was revoked after registration: fall back to an inexact alarm.
            diagnostics.onError(null, "Exact alarm not allowed for $key; scheduling inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
        }
    }

    private fun pendingIntent(key: String, scheduledAt: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            // The data URI makes each key a distinct PendingIntent.
            .setData(Uri.parse("cadence://alarm/" + Uri.encode(key)))
            .putExtra(AlarmReceiver.EXTRA_KEY, key)
            .putExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, scheduledAt)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
