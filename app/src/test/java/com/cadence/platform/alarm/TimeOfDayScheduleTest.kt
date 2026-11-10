package com.cadence.platform.alarm

import com.cadence.domain.model.Weekday
import com.cadence.domain.trigger.TriggerConfig
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TimeOfDayScheduleTest {

    private val colombo = ZoneId.of("Asia/Colombo")

    /** 2026-11-10 is a Tuesday. */
    private fun at(local: String, zone: ZoneId = colombo): ZonedDateTime = LocalDateTime.parse(local).atZone(zone)

    @Test
    fun `later today when the time hasn't passed`() {
        val next = TimeOfDaySchedule.nextOccurrence(TriggerConfig.TimeOfDay(18, 30), at("2026-11-10T09:00"))

        assertEquals(at("2026-11-10T18:30"), next)
    }

    @Test
    fun `tomorrow when the time has passed or is now`() {
        val config = TriggerConfig.TimeOfDay(7, 0)

        assertEquals(at("2026-11-11T07:00"), TimeOfDaySchedule.nextOccurrence(config, at("2026-11-10T07:30")))
        assertEquals(at("2026-11-11T07:00"), TimeOfDaySchedule.nextOccurrence(config, at("2026-11-10T07:00")))
    }

    @Test
    fun `skips to the next allowed weekday`() {
        val weekends = TriggerConfig.TimeOfDay(9, 0, setOf(Weekday.SATURDAY, Weekday.SUNDAY))

        assertEquals(at("2026-11-14T09:00"), TimeOfDaySchedule.nextOccurrence(weekends, at("2026-11-10T08:00")))
    }

    @Test
    fun `same weekday next week when today's time has passed`() {
        val tuesdays = TriggerConfig.TimeOfDay(8, 0, setOf(Weekday.TUESDAY))

        assertEquals(at("2026-11-17T08:00"), TimeOfDaySchedule.nextOccurrence(tuesdays, at("2026-11-10T08:00")))
    }

    @Test
    fun `time inside a daylight saving gap moves forward`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 2026-03-29 02:00 doesn't exist in Berlin (clocks jump to 03:00).
        val next = TimeOfDaySchedule.nextOccurrence(TriggerConfig.TimeOfDay(2, 30), at("2026-03-29T00:00", berlin))

        assertEquals(at("2026-03-29T03:30", berlin), next)
    }

    @Test
    fun `alarm keys are stable and distinguish configs`() {
        val a = TriggerConfig.TimeOfDay(7, 5, setOf(Weekday.FRIDAY, Weekday.MONDAY))
        val b = TriggerConfig.TimeOfDay(7, 5, setOf(Weekday.MONDAY, Weekday.FRIDAY))

        assertEquals("tod:07:05:MON,FRI", TimeOfDaySchedule.alarmKey(a))
        assertEquals(TimeOfDaySchedule.alarmKey(a), TimeOfDaySchedule.alarmKey(b))
        assertNotEquals(TimeOfDaySchedule.alarmKey(a), TimeOfDaySchedule.alarmKey(TriggerConfig.TimeOfDay(7, 5)))
    }
}
