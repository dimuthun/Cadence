package com.cadence.domain.constraint

import com.cadence.domain.model.Weekday
import com.cadence.testutil.FakeDeviceStateReader
import com.cadence.testutil.testContext
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInConstraintEvaluatorsTest {

    private val zone = ZoneId.of("Asia/Colombo")
    private val context = testContext()

    /** A clock fixed at [localDateTime] in [zone]. 2026-11-10 is a Tuesday. */
    private fun clockAt(localDateTime: String): Clock =
        Clock.fixed(LocalDateTime.parse(localDateTime).atZone(zone).toInstant(), zone)

    private fun minutes(hour: Int, minute: Int = 0) = hour * 60 + minute

    // region TimeRange

    @Test
    fun `time range matches inside, including start and excluding end`() = runTest {
        val range = ConstraintConfig.TimeRange(minutes(9), minutes(17))

        assertTrue(TimeRangeEvaluator(clockAt("2026-11-10T09:00")).evaluate(range, context))
        assertTrue(TimeRangeEvaluator(clockAt("2026-11-10T12:30")).evaluate(range, context))
        assertTrue(TimeRangeEvaluator(clockAt("2026-11-10T16:59")).evaluate(range, context))
        assertFalse(TimeRangeEvaluator(clockAt("2026-11-10T17:00")).evaluate(range, context))
        assertFalse(TimeRangeEvaluator(clockAt("2026-11-10T08:59")).evaluate(range, context))
    }

    @Test
    fun `time range wraps past midnight`() = runTest {
        val night = ConstraintConfig.TimeRange(minutes(22), minutes(6))

        assertTrue(TimeRangeEvaluator(clockAt("2026-11-10T23:15")).evaluate(night, context))
        assertTrue(TimeRangeEvaluator(clockAt("2026-11-10T00:00")).evaluate(night, context))
        assertTrue(TimeRangeEvaluator(clockAt("2026-11-10T05:59")).evaluate(night, context))
        assertFalse(TimeRangeEvaluator(clockAt("2026-11-10T06:00")).evaluate(night, context))
        assertFalse(TimeRangeEvaluator(clockAt("2026-11-10T12:00")).evaluate(night, context))
    }

    @Test
    fun `time range with equal bounds covers the whole day`() = runTest {
        val allDay = ConstraintConfig.TimeRange(minutes(8), minutes(8))

        assertTrue(TimeRangeEvaluator(clockAt("2026-11-10T03:00")).evaluate(allDay, context))
        assertTrue(TimeRangeEvaluator(clockAt("2026-11-10T20:00")).evaluate(allDay, context))
    }

    @Test
    fun `time range uses the clock's time zone`() = runTest {
        // 04:00 UTC is 09:30 in Colombo (UTC+05:30).
        val utcMorning = Clock.fixed(java.time.Instant.parse("2026-11-10T04:00:00Z"), zone)
        val range = ConstraintConfig.TimeRange(minutes(9), minutes(10))

        assertTrue(TimeRangeEvaluator(utcMorning).evaluate(range, context))
    }

    @Test
    fun `time range rejects out of range minutes`() {
        assertThrows(IllegalArgumentException::class.java) { ConstraintConfig.TimeRange(-1, 10) }
        assertThrows(IllegalArgumentException::class.java) { ConstraintConfig.TimeRange(0, 24 * 60) }
    }

    // endregion

    // region DaysOfWeek

    @Test
    fun `day of week matches the local date`() = runTest {
        val tuesday = clockAt("2026-11-10T10:00")

        assertTrue(DaysOfWeekEvaluator(tuesday).evaluate(ConstraintConfig.DaysOfWeek(setOf(Weekday.TUESDAY)), context))
        assertFalse(
            DaysOfWeekEvaluator(tuesday).evaluate(
                ConstraintConfig.DaysOfWeek(setOf(Weekday.SATURDAY, Weekday.SUNDAY)),
                context,
            ),
        )
    }

    @Test
    fun `weekday maps to and from java time`() {
        for (day in java.time.DayOfWeek.entries) {
            assertEquals(day, Weekday.from(day).dayOfWeek)
        }
    }

    // endregion

    // region Battery and charging

    @Test
    fun `battery range is inclusive at both ends`() = runTest {
        val state = FakeDeviceStateReader()
        val evaluator = BatteryLevelRangeEvaluator(state)
        val range = ConstraintConfig.BatteryLevelRange(minPercent = 20, maxPercent = 80)

        state.batteryLevel = 20
        assertTrue(evaluator.evaluate(range, context))
        state.batteryLevel = 80
        assertTrue(evaluator.evaluate(range, context))
        state.batteryLevel = 19
        assertFalse(evaluator.evaluate(range, context))
        state.batteryLevel = 81
        assertFalse(evaluator.evaluate(range, context))
    }

    @Test
    fun `unknown battery level never matches`() = runTest {
        val evaluator = BatteryLevelRangeEvaluator(FakeDeviceStateReader(batteryLevel = null))

        assertFalse(evaluator.evaluate(ConstraintConfig.BatteryLevelRange(0, 100), context))
    }

    @Test
    fun `battery range rejects inverted bounds`() {
        assertThrows(IllegalArgumentException::class.java) { ConstraintConfig.BatteryLevelRange(80, 20) }
    }

    @Test
    fun `charging state compares with the device`() = runTest {
        val state = FakeDeviceStateReader(charging = true)
        val evaluator = ChargingStateEvaluator(state)

        assertTrue(evaluator.evaluate(ConstraintConfig.ChargingState(charging = true), context))
        assertFalse(evaluator.evaluate(ConstraintConfig.ChargingState(charging = false), context))
    }

    // endregion

    // region Wi-Fi

    @Test
    fun `wifi constraint matches the connection state`() = runTest {
        val state = FakeDeviceStateReader(wifiConnected = true)
        val evaluator = WifiNetworkEvaluator(state)

        assertTrue(evaluator.evaluate(ConstraintConfig.WifiNetwork(connected = true), context))
        assertFalse(evaluator.evaluate(ConstraintConfig.WifiNetwork(connected = false), context))

        state.wifiConnected = false
        assertFalse(evaluator.evaluate(ConstraintConfig.WifiNetwork(connected = true), context))
        assertTrue(evaluator.evaluate(ConstraintConfig.WifiNetwork(connected = false), context))
    }

    @Test
    fun `wifi constraint needs no permissions`() {
        val evaluator = WifiNetworkEvaluator(FakeDeviceStateReader())

        assertTrue(evaluator.requiredPermissions(ConstraintConfig.WifiNetwork()).isEmpty())
    }

    // endregion
}
