package com.cadence.domain.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SunTimesTest {

    private fun assertClose(expected: String, actual: Instant, toleranceMinutes: Long = 4) {
        val diff = Duration.between(Instant.parse(expected), actual).abs()
        assertTrue("Expected about $expected but was $actual", diff <= Duration.ofMinutes(toleranceMinutes))
    }

    @Test
    fun `london midsummer`() {
        val day = SunTimes.on(LocalDate.of(2026, 6, 21), 51.5074, -0.1278) as SunDay.Normal

        // 04:43 and 21:21 British Summer Time.
        assertClose("2026-06-21T03:43:00Z", day.sunrise)
        assertClose("2026-06-21T20:21:00Z", day.sunset)
    }

    @Test
    fun `sydney midsummer is in the southern hemisphere's summer`() {
        val day = SunTimes.on(LocalDate.of(2026, 12, 21), -33.8688, 151.2093) as SunDay.Normal

        // 05:41 and 20:05 Australian Eastern Daylight Time (UTC+11).
        assertClose("2026-12-20T18:41:00Z", day.sunrise, toleranceMinutes = 5)
        assertClose("2026-12-21T09:05:00Z", day.sunset, toleranceMinutes = 5)
    }

    @Test
    fun `arctic polar night and midnight sun`() {
        assertEquals(SunDay.AlwaysDown, SunTimes.on(LocalDate.of(2026, 12, 21), 69.65, 18.96))
        assertEquals(SunDay.AlwaysUp, SunTimes.on(LocalDate.of(2026, 6, 21), 69.65, 18.96))
    }

    @Test
    fun `daylight is between sunrise and sunset`() {
        val date = LocalDate.of(2026, 6, 21)

        assertTrue(SunTimes.isDaylight(Instant.parse("2026-06-21T12:00:00Z"), date, 51.5074, -0.1278))
        assertFalse(SunTimes.isDaylight(Instant.parse("2026-06-21T23:30:00Z"), date, 51.5074, -0.1278))
        assertFalse(SunTimes.isDaylight(Instant.parse("2026-06-21T02:00:00Z"), date, 51.5074, -0.1278))
    }
}
