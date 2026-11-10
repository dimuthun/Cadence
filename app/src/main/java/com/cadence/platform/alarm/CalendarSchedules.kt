package com.cadence.platform.alarm

import com.cadence.domain.time.SunDay
import com.cadence.domain.time.SunTimes
import com.cadence.domain.trigger.DeviceTrigger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Pure scheduling math for [DeviceTrigger.DayOfMonth]. */
object DayOfMonthSchedule {

    /** `true` when [date] is one of the chosen days (or the last day of its month, if chosen). */
    fun matches(days: Set<Int>, lastDay: Boolean, date: LocalDate): Boolean =
        date.dayOfMonth in days || (lastDay && date.dayOfMonth == date.lengthOfMonth())

    /** The first matching time strictly after [after], in [after]'s time zone. */
    fun nextOccurrence(config: DeviceTrigger.DayOfMonth, after: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(config.hour, config.minute)
        var date = after.toLocalDate()
        repeat(DAYS_TO_SEARCH) {
            if (matches(config.days, config.lastDay, date)) {
                val candidate = ZonedDateTime.of(date, time, after.zone)
                if (candidate.isAfter(after)) return candidate
            }
            date = date.plusDays(1)
        }
        error("No occurrence within $DAYS_TO_SEARCH days for $config")
    }

    fun alarmKey(config: DeviceTrigger.DayOfMonth): String = String.format(
        Locale.ROOT,
        "dom:%02d:%02d:%s:%s",
        config.hour,
        config.minute,
        config.days.sorted().joinToString(","),
        if (config.lastDay) "last" else "",
    )

    /** Covers the longest gap: day 31 only, from just after Dec 31 back round to it (excluding leap quirks). */
    private const val DAYS_TO_SEARCH = 400
}

/** Pure scheduling math for [DeviceTrigger.Sun]. */
object SunSchedule {

    /**
     * The first sunrise or sunset (plus offset) strictly after [after], or `null` if the sun
     * neither rises nor sets in the next year (only possible very near the poles).
     */
    fun nextOccurrence(config: DeviceTrigger.Sun, after: Instant, zone: ZoneId): Instant? {
        // Start a day early: with a large offset, yesterday's event can still be ahead.
        var date = after.atZone(zone).toLocalDate().minusDays(1)
        repeat(DAYS_TO_SEARCH) {
            val day = SunTimes.on(date, config.latitude, config.longitude)
            if (day is SunDay.Normal) {
                val event = if (config.event == DeviceTrigger.Sun.Event.SUNRISE) day.sunrise else day.sunset
                val candidate = event.plusSeconds(config.offsetMinutes * 60L)
                if (candidate.isAfter(after)) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }

    fun alarmKey(config: DeviceTrigger.Sun): String = String.format(
        Locale.ROOT,
        "sun:%s:%.4f:%.4f:%d",
        config.event.name,
        config.latitude,
        config.longitude,
        config.offsetMinutes,
    )

    private const val DAYS_TO_SEARCH = 370
}
