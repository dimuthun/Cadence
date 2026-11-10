package com.cadence.domain.constraint

import com.cadence.domain.device.DeviceStateReader
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.model.Weekday
import com.cadence.domain.plugin.ConstraintEvaluator
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlin.reflect.KClass

/** Evaluates [ConstraintConfig.TimeRange] against the local time of [clock]. */
class TimeRangeEvaluator(private val clock: Clock) : ConstraintEvaluator<ConstraintConfig.TimeRange> {

    override val configType: KClass<ConstraintConfig.TimeRange> = ConstraintConfig.TimeRange::class

    override suspend fun evaluate(config: ConstraintConfig.TimeRange, context: ExecutionContext): Boolean {
        val now = LocalTime.now(clock)
        val minute = now.hour * 60 + now.minute
        val start = config.startMinuteOfDay
        val end = config.endMinuteOfDay
        return when {
            start == end -> true
            start < end -> minute in start until end
            else -> minute >= start || minute < end // wraps past midnight
        }
    }
}

/** Evaluates [ConstraintConfig.DaysOfWeek] against the local date of [clock]. */
class DaysOfWeekEvaluator(private val clock: Clock) : ConstraintEvaluator<ConstraintConfig.DaysOfWeek> {

    override val configType: KClass<ConstraintConfig.DaysOfWeek> = ConstraintConfig.DaysOfWeek::class

    override suspend fun evaluate(config: ConstraintConfig.DaysOfWeek, context: ExecutionContext): Boolean =
        Weekday.from(LocalDate.now(clock).dayOfWeek) in config.days
}

/** Evaluates [ConstraintConfig.BatteryLevelRange]. An unknown battery level never matches. */
class BatteryLevelRangeEvaluator(
    private val deviceState: DeviceStateReader,
) : ConstraintEvaluator<ConstraintConfig.BatteryLevelRange> {

    override val configType: KClass<ConstraintConfig.BatteryLevelRange> = ConstraintConfig.BatteryLevelRange::class

    override suspend fun evaluate(config: ConstraintConfig.BatteryLevelRange, context: ExecutionContext): Boolean {
        val level = deviceState.batteryLevelPercent() ?: return false
        return level in config.minPercent..config.maxPercent
    }
}

/** Evaluates [ConstraintConfig.ChargingState]. */
class ChargingStateEvaluator(
    private val deviceState: DeviceStateReader,
) : ConstraintEvaluator<ConstraintConfig.ChargingState> {

    override val configType: KClass<ConstraintConfig.ChargingState> = ConstraintConfig.ChargingState::class

    override suspend fun evaluate(config: ConstraintConfig.ChargingState, context: ExecutionContext): Boolean =
        deviceState.isCharging() == config.charging
}

/** Evaluates [ConstraintConfig.WifiNetwork] (any network, no location permission needed). */
class WifiNetworkEvaluator(
    private val deviceState: DeviceStateReader,
) : ConstraintEvaluator<ConstraintConfig.WifiNetwork> {

    override val configType: KClass<ConstraintConfig.WifiNetwork> = ConstraintConfig.WifiNetwork::class

    override suspend fun evaluate(config: ConstraintConfig.WifiNetwork, context: ExecutionContext): Boolean =
        deviceState.isWifiConnected() == config.connected
}
