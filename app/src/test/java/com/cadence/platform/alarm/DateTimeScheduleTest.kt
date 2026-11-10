package com.cadence.platform.alarm

import com.cadence.domain.trigger.TriggerConfig
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DateTimeScheduleTest {

    private val start = Instant.parse("2026-11-12T08:00:00Z")
    private fun at(iso: String) = Instant.parse(iso)

    @Test
    fun `one-off fires at its time and never after`() {
        val once = TriggerConfig.DateTime(start.toEpochMilli())

        assertEquals(start, DateTimeSchedule.nextOccurrence(once, at("2026-11-10T09:00:00Z")))
        assertNull(DateTimeSchedule.nextOccurrence(once, start))
        assertNull(DateTimeSchedule.nextOccurrence(once, at("2026-11-13T00:00:00Z")))
    }

    @Test
    fun `repeat waits for the start time first`() {
        val every30 = TriggerConfig.DateTime(start.toEpochMilli(), repeatIntervalMinutes = 30)

        assertEquals(start, DateTimeSchedule.nextOccurrence(every30, at("2026-11-11T23:59:00Z")))
    }

    @Test
    fun `repeat stays anchored to the start time`() {
        val every30 = TriggerConfig.DateTime(start.toEpochMilli(), repeatIntervalMinutes = 30)

        assertEquals(at("2026-11-12T08:30:00Z"), DateTimeSchedule.nextOccurrence(every30, at("2026-11-12T08:00:00Z")))
        assertEquals(at("2026-11-12T10:30:00Z"), DateTimeSchedule.nextOccurrence(every30, at("2026-11-12T10:17:42Z")))
        // Exactly on a boundary means that firing is due now, so the next one is strictly later.
        assertEquals(at("2026-11-12T11:00:00Z"), DateTimeSchedule.nextOccurrence(every30, at("2026-11-12T10:30:00Z")))
    }

    @Test
    fun `multi-day intervals`() {
        val everyTwoDays = TriggerConfig.DateTime(start.toEpochMilli(), repeatIntervalMinutes = 2 * 24 * 60)

        assertEquals(at("2026-11-16T08:00:00Z"), DateTimeSchedule.nextOccurrence(everyTwoDays, at("2026-11-14T09:00:00Z")))
    }

    @Test
    fun `alarm keys distinguish schedules`() {
        val once = TriggerConfig.DateTime(start.toEpochMilli())
        val repeating = TriggerConfig.DateTime(start.toEpochMilli(), repeatIntervalMinutes = 60)

        assertNotEquals(DateTimeSchedule.alarmKey(once), DateTimeSchedule.alarmKey(repeating))
        assertEquals(DateTimeSchedule.alarmKey(repeating), DateTimeSchedule.alarmKey(repeating.copy()))
    }

    @Test
    fun `invalid intervals are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { TriggerConfig.DateTime(0, repeatIntervalMinutes = 0) }
    }
}
