package com.cadence.platform.alarm

import com.cadence.domain.model.Weekday
import com.cadence.domain.trigger.TriggerConfig
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Locale

/** Pure scheduling math for [TriggerConfig.TimeOfDay]. */
object TimeOfDaySchedule {

    /**
     * The first time strictly after [after] that matches [config], in [after]'s time zone.
     * If the time falls in a daylight-saving gap, it moves forward by the gap length
     * (`ZonedDateTime.of` semantics).
     */
    fun nextOccurrence(config: TriggerConfig.TimeOfDay, after: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(config.hour, config.minute)
        var date = after.toLocalDate()
        repeat(DAYS_TO_SEARCH) {
            val candidate = ZonedDateTime.of(date, time, after.zone)
            val dayMatches = config.days.isEmpty() || Weekday.from(date.dayOfWeek) in config.days
            if (dayMatches && candidate.isAfter(after)) return candidate
            date = date.plusDays(1)
        }
        error("No occurrence within $DAYS_TO_SEARCH days for $config")
    }

    /** Stable alarm key. Macros with identical time triggers share one alarm. */
    fun alarmKey(config: TriggerConfig.TimeOfDay): String {
        val days = config.days.sortedBy { it.ordinal }.joinToString(",") { it.name.take(3) }
        return String.format(Locale.ROOT, "tod:%02d:%02d:%s", config.hour, config.minute, days)
    }

    /** Today plus a full week covers every day-of-week combination. */
    private const val DAYS_TO_SEARCH = 8
}
