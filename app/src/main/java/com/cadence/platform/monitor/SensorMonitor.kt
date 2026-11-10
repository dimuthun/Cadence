package com.cadence.platform.monitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.platform.common.AccelerationSample
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/** Thrown when a trigger needs a sensor this phone doesn't have. The engine logs it. */
class MissingSensorException(name: String) : IllegalStateException("This phone has no $name sensor")

/**
 * Shared accelerometer, proximity and light sensor streams. Each sensor is registered only
 * while some trigger collects it, at a modest sampling rate.
 *
 * Most phones pause the accelerometer and light sensor while the screen is off, so shake,
 * position and light triggers mainly work with the screen on. The proximity sensor usually
 * keeps working.
 */
@Singleton
class SensorMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val sensors = context.getSystemService(SensorManager::class.java)

    /** Raw accelerometer readings, about 15 per second. */
    val acceleration: SharedFlow<AccelerationSample> = sensorFlow(Sensor.TYPE_ACCELEROMETER, "motion", ACCELEROMETER_PERIOD_MICROS)
        .map { AccelerationSample(it.values[0], it.values[1], it.values[2], it.timestamp) }
        .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0))

    /** `true` while something is near the proximity sensor; the current state first. */
    val proximityNear: SharedFlow<Boolean> = run {
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val range = sensor?.maximumRange ?: 5f
        sensorFlow(Sensor.TYPE_PROXIMITY, "proximity", SensorManager.SENSOR_DELAY_NORMAL)
            // Many sensors only report 0 (near) or their maximum range (far).
            .map { it.values[0] < minOf(NEAR_CENTIMETERS, range) }
            .distinctUntilChanged()
            .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)
    }

    /** Ambient light in lux, rounded. */
    val lightLux: SharedFlow<Int> = sensorFlow(Sensor.TYPE_LIGHT, "light", SensorManager.SENSOR_DELAY_NORMAL)
        .map { it.values[0].toInt() }
        .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    /** `true` when the phone has a sensor of [type]. */
    fun hasSensor(type: Int): Boolean = sensors.getDefaultSensor(type) != null

    /** A copy of one sensor event; Android reuses [SensorEvent] objects after the callback returns. */
    private class Reading(val values: FloatArray, val timestamp: Long)

    private fun sensorFlow(type: Int, name: String, samplingPeriod: Int): Flow<Reading> = callbackFlow {
        val sensor = sensors.getDefaultSensor(type) ?: throw MissingSensorException(name)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(Reading(event.values.copyOf(), event.timestamp))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, sensor, samplingPeriod)
        awaitClose { sensors.unregisterListener(listener) }
    }

    private companion object {
        const val ACCELEROMETER_PERIOD_MICROS = 66_000
        const val NEAR_CENTIMETERS = 3f
    }
}
