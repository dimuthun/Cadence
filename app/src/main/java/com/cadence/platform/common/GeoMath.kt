package com.cadence.platform.common

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A location fix. [accuracyMeters] is the radius of 68% confidence; 0 when unknown. */
data class GeoFix(val latitude: Double, val longitude: Double, val accuracyMeters: Float = 0f)

/** Great-circle distance in meters (haversine; within ~0.5% of the WGS84 ellipsoid). */
fun distanceMeters(from: GeoFix, to: GeoFix): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

private const val EARTH_RADIUS_METERS = 6_371_008.8

/**
 * Emits the distance travelled each time a fix is at least [thresholdMeters] from the anchor,
 * then makes that fix the new anchor. The first usable fix is the anchor and never emits.
 *
 * Fixes less accurate than half the threshold are ignored, so GPS jitter can't fake movement.
 */
fun Flow<GeoFix>.movements(thresholdMeters: Int): Flow<Double> = flow {
    val maxAccuracy = thresholdMeters / 2f
    var anchor: GeoFix? = null
    collect { fix ->
        if (fix.accuracyMeters > maxAccuracy) return@collect
        val start = anchor
        if (start == null) {
            anchor = fix
            return@collect
        }
        val moved = distanceMeters(start, fix)
        if (moved >= thresholdMeters) {
            anchor = fix
            emit(moved)
        }
    }
}
