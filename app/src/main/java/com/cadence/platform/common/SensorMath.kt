package com.cadence.platform.common

import com.cadence.domain.device.DevicePosition
import com.cadence.domain.trigger.DeviceTrigger
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

// Pure sensor interpretation, free of Android types so it can be unit tested.

/** One accelerometer reading in m/s², with the sensor timestamp in nanoseconds. */
data class AccelerationSample(val x: Float, val y: Float, val z: Float, val timeNanos: Long)

private const val GRAVITY = 9.81f
private const val NANOS_PER_MILLI = 1_000_000L

/** Peak acceleration, in multiples of gravity, that counts as one jolt of a shake. */
fun shakeThresholdG(sensitivity: DeviceTrigger.Shake.Sensitivity): Float = when (sensitivity) {
    DeviceTrigger.Shake.Sensitivity.LOW -> 2.7f
    DeviceTrigger.Shake.Sensitivity.MEDIUM -> 2.2f
    DeviceTrigger.Shake.Sensitivity.HIGH -> 1.7f
}

/**
 * Emits once per shake: at least [jolts] readings above [thresholdG] within [windowMillis],
 * each at least 80 ms apart, then stays quiet for [cooldownMillis] so one shake fires once.
 */
fun Flow<AccelerationSample>.shakes(
    thresholdG: Float,
    jolts: Int = 3,
    windowMillis: Long = 1_200,
    cooldownMillis: Long = 2_000,
): Flow<Unit> = flow {
    val joltTimes = ArrayDeque<Long>()
    var quietUntil = Long.MIN_VALUE
    collect { sample ->
        val g = sqrt(sample.x * sample.x + sample.y * sample.y + sample.z * sample.z) / GRAVITY
        val now = sample.timeNanos
        if (now < quietUntil || g < thresholdG) return@collect
        if (joltTimes.isNotEmpty() && now - joltTimes.last() < 80 * NANOS_PER_MILLI) return@collect
        joltTimes.addLast(now)
        while (now - joltTimes.first() > windowMillis * NANOS_PER_MILLI) joltTimes.removeFirst()
        if (joltTimes.size >= jolts) {
            joltTimes.clear()
            quietUntil = now + cooldownMillis * NANOS_PER_MILLI
            emit(Unit)
        }
    }
}

/**
 * The position gravity points to, or `null` in between (tilted, or moving). Flat means within
 * about 30° of horizontal; upright means within about 45° of vertical.
 */
fun classifyPosition(x: Float, y: Float, z: Float): DevicePosition? = when {
    z > 8.5f -> DevicePosition.FACE_UP
    z < -8.5f -> DevicePosition.FACE_DOWN
    abs(y) > 7f && abs(y) > abs(x) -> DevicePosition.PORTRAIT
    abs(x) > 7f && abs(x) > abs(y) -> DevicePosition.LANDSCAPE
    else -> null
}

/**
 * Positions that have held for at least [holdMillis], each emitted once when reached. `null`
 * means "in between" (held at an angle or moving) for that long, so putting the phone face
 * down, picking it up and putting it down again counts twice. Includes the starting position;
 * [positionChangesTo] ignores it.
 */
fun Flow<AccelerationSample>.stablePositions(holdMillis: Long = 800): Flow<DevicePosition?> = flow {
    var candidate: DevicePosition? = null
    var since = Long.MIN_VALUE
    var emitted: Any? = Unemitted
    collect { sample ->
        val position = classifyPosition(sample.x, sample.y, sample.z)
        if (since == Long.MIN_VALUE || position != candidate) {
            candidate = position
            since = sample.timeNanos
        }
        if (position != emitted && sample.timeNanos - since >= holdMillis * NANOS_PER_MILLI) {
            emitted = position
            emit(position)
        }
    }
}

private object Unemitted

/** Emits each time the stable position changes to [target]; the starting position never fires. */
fun Flow<DevicePosition?>.positionChangesTo(target: DevicePosition): Flow<Unit> = distinctUntilChanged()
    .zipWithPrevious()
    .filter { (_, current) -> current == target }
    .map { }
