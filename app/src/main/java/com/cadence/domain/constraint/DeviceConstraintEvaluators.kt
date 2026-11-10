package com.cadence.domain.constraint

import com.cadence.domain.device.DeviceStateReader
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.plugin.ConstraintEvaluator
import com.cadence.domain.time.SunTimes
import java.time.Clock
import java.time.LocalDate
import kotlin.reflect.KClass

/** Evaluates [DeviceConstraint.SwitchState]. A state the phone can't report never matches. */
class SwitchStateEvaluator(
    private val deviceState: DeviceStateReader,
) : ConstraintEvaluator<DeviceConstraint.SwitchState> {

    override val configType: KClass<DeviceConstraint.SwitchState> = DeviceConstraint.SwitchState::class

    override suspend fun evaluate(config: DeviceConstraint.SwitchState, context: ExecutionContext): Boolean {
        val on = deviceState.switchState(config.switch) ?: return false
        return on == config.on
    }
}

/** Evaluates [DeviceConstraint.RingerModeIs]. */
class RingerModeIsEvaluator(
    private val deviceState: DeviceStateReader,
) : ConstraintEvaluator<DeviceConstraint.RingerModeIs> {

    override val configType: KClass<DeviceConstraint.RingerModeIs> = DeviceConstraint.RingerModeIs::class

    override suspend fun evaluate(config: DeviceConstraint.RingerModeIs, context: ExecutionContext): Boolean =
        deviceState.ringerMode() in config.modes
}

/** Evaluates [DeviceConstraint.NetworkTypeIs]. */
class NetworkTypeIsEvaluator(
    private val deviceState: DeviceStateReader,
) : ConstraintEvaluator<DeviceConstraint.NetworkTypeIs> {

    override val configType: KClass<DeviceConstraint.NetworkTypeIs> = DeviceConstraint.NetworkTypeIs::class

    override suspend fun evaluate(config: DeviceConstraint.NetworkTypeIs, context: ExecutionContext): Boolean =
        deviceState.networkType() in config.types
}

/** Evaluates [DeviceConstraint.Daylight] for today's sunrise and sunset at the saved place. */
class DaylightEvaluator(private val clock: Clock) : ConstraintEvaluator<DeviceConstraint.Daylight> {

    override val configType: KClass<DeviceConstraint.Daylight> = DeviceConstraint.Daylight::class

    override suspend fun evaluate(config: DeviceConstraint.Daylight, context: ExecutionContext): Boolean =
        SunTimes.isDaylight(clock.instant(), LocalDate.now(clock), config.latitude, config.longitude) == config.daylight
}

/** Evaluates [DeviceConstraint.DaysOfMonth] against the local date of [clock]. */
class DaysOfMonthEvaluator(private val clock: Clock) : ConstraintEvaluator<DeviceConstraint.DaysOfMonth> {

    override val configType: KClass<DeviceConstraint.DaysOfMonth> = DeviceConstraint.DaysOfMonth::class

    override suspend fun evaluate(config: DeviceConstraint.DaysOfMonth, context: ExecutionContext): Boolean {
        val today = LocalDate.now(clock)
        return today.dayOfMonth in config.days || (config.lastDay && today.dayOfMonth == today.lengthOfMonth())
    }
}

/** Evaluates [DeviceConstraint.Months] against the local date of [clock]. */
class MonthsEvaluator(private val clock: Clock) : ConstraintEvaluator<DeviceConstraint.Months> {

    override val configType: KClass<DeviceConstraint.Months> = DeviceConstraint.Months::class

    override suspend fun evaluate(config: DeviceConstraint.Months, context: ExecutionContext): Boolean =
        LocalDate.now(clock).monthValue in config.months
}
