package com.cadence.platform.alarm

import com.cadence.domain.trigger.DeviceTrigger
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSchedulesTest {

    private val colombo = ZoneId.of("Asia/Colombo")

    private fun at(text: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), colombo)

    @Test
    fun `day 31 skips months without it`() {
        val config = DeviceTrigger.DayOfMonth(days = setOf(31))

        assertEquals(at("2026-05-31T09:00"), DayOfMonthSchedule.nextOccurrence(config, at("2026-04-15T10:00")))
    }

    @Test
    fun `last day follows the month length`() {
        val config = DeviceTrigger.DayOfMonth(lastDay = true, hour = 18, minute = 30)

        assertEquals(at("2026-02-28T18:30"), DayOfMonthSchedule.nextOccurrence(config, at("2026-02-10T08:00")))
        assertEquals(at("2028-02-29T18:30"), DayOfMonthSchedule.nextOccurrence(config, at("2028-02-01T08:00")))
    }

    @Test
    fun `the next occurrence is strictly after`() {
        val config = DeviceTrigger.DayOfMonth(days = setOf(1, 15))

        assertEquals(at("2026-11-15T09:00"), DayOfMonthSchedule.nextOccurrence(config, at("2026-11-01T09:00")))
        assertEquals(at("2026-12-01T09:00"), DayOfMonthSchedule.nextOccurrence(config, at("2026-11-20T09:00")))
    }

    @Test
    fun `alarm keys differ by schedule`() {
        assertNotEquals(
            DayOfMonthSchedule.alarmKey(DeviceTrigger.DayOfMonth(days = setOf(1))),
            DayOfMonthSchedule.alarmKey(DeviceTrigger.DayOfMonth(days = setOf(1), lastDay = true)),
        )
    }

    private val london = DeviceTrigger.Sun(DeviceTrigger.Sun.Event.SUNSET, 51.5074, -0.1278)

    private fun assertAbout(expected: String, actual: Instant?) {
        val diff = Duration.between(Instant.parse(expected), actual).abs()
        assertTrue("Expected about $expected but was $actual", diff <= Duration.ofMinutes(4))
    }

    @Test
    fun `sunset today when it's still ahead, otherwise tomorrow`() {
        assertAbout("2026-06-21T20:21:00Z", SunSchedule.nextOccurrence(london, Instant.parse("2026-06-21T12:00:00Z"), ZoneOffset.UTC))
        assertAbout("2026-06-22T20:21:00Z", SunSchedule.nextOccurrence(london, Instant.parse("2026-06-21T21:00:00Z"), ZoneOffset.UTC))
    }

    @Test
    fun `offsets shift the event`() {
        val earlier = london.copy(offsetMinutes = -180)

        assertAbout("2026-06-22T17:21:00Z", SunSchedule.nextOccurrence(earlier, Instant.parse("2026-06-21T18:00:00Z"), ZoneOffset.UTC))
    }

    @Test
    fun `polar night skips ahead to the next sunrise`() {
        val tromso = DeviceTrigger.Sun(DeviceTrigger.Sun.Event.SUNRISE, 69.65, 18.96)
        val next = SunSchedule.nextOccurrence(tromso, Instant.parse("2026-12-21T12:00:00Z"), ZoneOffset.UTC)!!

        assertTrue("Sunrise returns in mid January, was $next", next.isAfter(Instant.parse("2027-01-10T00:00:00Z")))
        assertTrue(next.isBefore(Instant.parse("2027-01-25T00:00:00Z")))
    }
}
