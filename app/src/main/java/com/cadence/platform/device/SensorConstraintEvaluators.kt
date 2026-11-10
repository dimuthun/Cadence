package com.cadence.platform.device

import com.cadence.domain.constraint.DeviceConstraint
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.plugin.ConstraintEvaluator
import com.cadence.platform.common.classifyPosition
import com.cadence.platform.monitor.ForegroundAppMonitor
import com.cadence.platform.monitor.SensorMonitor
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [DeviceConstraint.PositionIs], from the average of a few accelerometer readings (about half a
 * second). If the sensor doesn't respond in time, for example while paused with the screen
 * off, the constraint isn't met.
 */
class PositionIsEvaluator @Inject constructor(
    private val sensors: SensorMonitor,
) : ConstraintEvaluator<DeviceConstraint.PositionIs> {

    override val configType: KClass<DeviceConstraint.PositionIs> = DeviceConstraint.PositionIs::class

    override suspend fun evaluate(config: DeviceConstraint.PositionIs, context: ExecutionContext): Boolean {
        val samples = withTimeoutOrNull(3.seconds) { sensors.acceleration.take(SAMPLES).toList() } ?: return false
        val position = classifyPosition(
            samples.map { it.x }.average().toFloat(),
            samples.map { it.y }.average().toFloat(),
            samples.map { it.z }.average().toFloat(),
        )
        return position == config.position
    }

    private companion object {
        const val SAMPLES = 8
    }
}

/** [DeviceConstraint.AppInForeground], from usage stats. With the screen off, no app counts as in front. */
class AppInForegroundEvaluator @Inject constructor(
    private val apps: ForegroundAppMonitor,
) : ConstraintEvaluator<DeviceConstraint.AppInForeground> {

    override val configType: KClass<DeviceConstraint.AppInForeground> = DeviceConstraint.AppInForeground::class

    override fun requiredPermissions(config: DeviceConstraint.AppInForeground) = setOf(PermissionRequirement.USAGE_ACCESS)

    override suspend fun evaluate(config: DeviceConstraint.AppInForeground, context: ExecutionContext): Boolean =
        (apps.currentPackage() == config.packageName) == config.inForeground
}
