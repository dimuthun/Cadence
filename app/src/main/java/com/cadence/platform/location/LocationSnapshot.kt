package com.cadence.platform.location

/** A location with every detail Android reported; missing values are `null`. */
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val accuracyMeters: Float?,
    val timeMillis: Long,
)
