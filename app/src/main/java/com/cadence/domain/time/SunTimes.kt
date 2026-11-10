package com.cadence.domain.time

import java.time.Instant
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Sunrise and sunset on one day at one place. */
sealed interface SunDay {
    /** The sun rises at [sunrise] and sets at [sunset]. */
    data class Normal(val sunrise: Instant, val sunset: Instant) : SunDay

    /** Midnight sun: the sun stays up all day. */
    data object AlwaysUp : SunDay

    /** Polar night: the sun stays down all day. */
    data object AlwaysDown : SunDay
}

/**
 * Sunrise and sunset from the standard sunrise equation, accurate to about a minute away from
 * the poles. Pure math, no location or network needed.
 */
object SunTimes {

    private const val J2000 = 2_451_545.0
    private const val EPOCH_DAY_2000_01_01 = 10_957L
    private const val UNIX_EPOCH_JULIAN = 2_440_587.5
    private const val MILLIS_PER_DAY = 86_400_000.0

    /** Altitude of the sun's center at rise and set: refraction plus the sun's radius. */
    private const val HORIZON_DEGREES = -0.833
    private const val EARTH_TILT_DEGREES = 23.4397

    /**
     * Sunrise and sunset for the local solar day closest to [date] at the place
     * ([latitude], [longitude] east-positive).
     */
    fun on(date: LocalDate, latitude: Double, longitude: Double): SunDay {
        val n = (date.toEpochDay() - EPOCH_DAY_2000_01_01).toDouble()
        val meanSolarNoon = n + 0.0009 - longitude / 360.0
        val anomaly = normalizeDegrees(357.5291 + 0.98560028 * meanSolarNoon)
        val m = anomaly.rad
        val center = 1.9148 * sin(m) + 0.0200 * sin(2 * m) + 0.0003 * sin(3 * m)
        val eclipticLongitude = normalizeDegrees(anomaly + center + 180.0 + 102.9372).rad
        val transit = J2000 + meanSolarNoon + 0.0053 * sin(m) - 0.0069 * sin(2 * eclipticLongitude)
        val declination = asin(sin(eclipticLongitude) * sin(EARTH_TILT_DEGREES.rad))
        val phi = latitude.rad
        val cosHourAngle = (sin(HORIZON_DEGREES.rad) - sin(phi) * sin(declination)) / (cos(phi) * cos(declination))
        return when {
            cosHourAngle > 1.0 -> SunDay.AlwaysDown
            cosHourAngle < -1.0 -> SunDay.AlwaysUp
            else -> {
                val halfDay = acos(cosHourAngle).deg / 360.0
                SunDay.Normal(sunrise = julianToInstant(transit - halfDay), sunset = julianToInstant(transit + halfDay))
            }
        }
    }

    /** `true` when the sun is above the horizon at [at] (using [date]'s sunrise and sunset). */
    fun isDaylight(at: Instant, date: LocalDate, latitude: Double, longitude: Double): Boolean =
        when (val day = on(date, latitude, longitude)) {
            is SunDay.Normal -> !at.isBefore(day.sunrise) && at.isBefore(day.sunset)
            SunDay.AlwaysUp -> true
            SunDay.AlwaysDown -> false
        }

    private fun julianToInstant(julian: Double): Instant =
        Instant.ofEpochMilli(((julian - UNIX_EPOCH_JULIAN) * MILLIS_PER_DAY).toLong())

    private fun normalizeDegrees(value: Double): Double = value - 360.0 * floor(value / 360.0)

    private val Double.rad: Double get() = this * PI / 180.0
    private val Double.deg: Double get() = this * 180.0 / PI
}
