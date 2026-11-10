package com.cadence.domain.constraint

import com.cadence.domain.action.RingerMode
import com.cadence.domain.device.DeviceSwitch
import com.cadence.domain.device.NetworkType
import com.cadence.testutil.FakeDeviceStateReader
import com.cadence.testutil.testContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceConstraintEvaluatorsTest {

    private val context = testContext()

    private fun clockAt(instant: String): Clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)

    @Test
    fun `switch state matches on and off, and unknown never matches`() = runTest {
        val state = FakeDeviceStateReader(switches = mutableMapOf(DeviceSwitch.HEADPHONES to true))
        val evaluator = SwitchStateEvaluator(state)

        assertTrue(evaluator.evaluate(DeviceConstraint.SwitchState(DeviceSwitch.HEADPHONES), context))
        assertFalse(evaluator.evaluate(DeviceConstraint.SwitchState(DeviceSwitch.HEADPHONES, on = false), context))
        assertFalse(evaluator.evaluate(DeviceConstraint.SwitchState(DeviceSwitch.NFC, on = false), context))
    }

    @Test
    fun `ringer and network match any chosen value`() = runTest {
        val state = FakeDeviceStateReader(ringer = RingerMode.VIBRATE, network = NetworkType.CELLULAR)

        assertTrue(RingerModeIsEvaluator(state).evaluate(DeviceConstraint.RingerModeIs(setOf(RingerMode.VIBRATE, RingerMode.SILENT)), context))
        assertFalse(RingerModeIsEvaluator(state).evaluate(DeviceConstraint.RingerModeIs(setOf(RingerMode.NORMAL)), context))
        assertTrue(NetworkTypeIsEvaluator(state).evaluate(DeviceConstraint.NetworkTypeIs(setOf(NetworkType.CELLULAR)), context))
        assertFalse(NetworkTypeIsEvaluator(state).evaluate(DeviceConstraint.NetworkTypeIs(setOf(NetworkType.WIFI)), context))
    }

    @Test
    fun `days of month include the last day when chosen`() = runTest {
        val lastOfFebruary = DaysOfMonthEvaluator(clockAt("2026-02-28T10:00:00Z"))

        assertTrue(lastOfFebruary.evaluate(DeviceConstraint.DaysOfMonth(lastDay = true), context))
        assertTrue(lastOfFebruary.evaluate(DeviceConstraint.DaysOfMonth(days = setOf(28)), context))
        assertFalse(lastOfFebruary.evaluate(DeviceConstraint.DaysOfMonth(days = setOf(1, 15)), context))
    }

    @Test
    fun `months match the current month`() = runTest {
        val evaluator = MonthsEvaluator(clockAt("2026-07-04T10:00:00Z"))

        assertTrue(evaluator.evaluate(DeviceConstraint.Months(setOf(6, 7, 8)), context))
        assertFalse(evaluator.evaluate(DeviceConstraint.Months(setOf(12, 1, 2)), context))
    }

    @Test
    fun `daylight and dark at a saved place`() = runTest {
        val london = DeviceConstraint.Daylight(latitude = 51.5074, longitude = -0.1278)
        val noon = DaylightEvaluator(clockAt("2026-06-21T12:00:00Z"))
        val night = DaylightEvaluator(clockAt("2026-06-21T23:30:00Z"))

        assertTrue(noon.evaluate(london, context))
        assertFalse(noon.evaluate(london.copy(daylight = false), context))
        assertTrue(night.evaluate(london.copy(daylight = false), context))
    }
}
