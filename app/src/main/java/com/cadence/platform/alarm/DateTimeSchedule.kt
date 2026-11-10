package com.cadence.platform.alarm

import com.cadence.domain.trigger.TriggerConfig
import java.time.Instant

/** Pure scheduling math for [TriggerConfig.DateTime]. */
object DateTimeSchedule {

    /**
     * The first firing strictly after [after], or `null` if a one-off time has already passed.
     * Repeats are anchored to the start time, so a late wake-up never shifts later firings.
     */
    fun nextOccurrence(config: TriggerConfig.DateTime, after: Instant): Instant? {
        val start = config.startAtEpochMillis
        val afterMillis = after.toEpochMilli()
        if (start > afterMillis) return Instant.ofEpochMilli(start)
        val intervalMinutes = config.repeatIntervalMinutes ?: return null
        val intervalMillis = intervalMinutes * 60_000L
        val periods = (afterMillis - start) / intervalMillis + 1
        return Instant.ofEpochMilli(start + periods * intervalMillis)
    }

    /** Stable alarm key. Macros with identical schedules share one alarm. */
    fun alarmKey(config: TriggerConfig.DateTime): String =
        "dt:${config.startAtEpochMillis}:${config.repeatIntervalMinutes ?: "once"}"
}
