package com.cadence.domain.device

/**
 * A config that stores a fixed place (for sunrise and sunset), so the editor can fill it with
 * the phone's current location. The place is saved in the config, so these items never need
 * location access while running.
 */
interface Located {
    val latitude: Double
    val longitude: Double

    /** `false` while the coordinates are still the 0,0 "not set" placeholder. */
    val hasLocation: Boolean get() = latitude != 0.0 || longitude != 0.0

    /** A copy of this config at the given coordinates. */
    fun at(latitude: Double, longitude: Double): Located

    companion object {
        fun requireValid(latitude: Double, longitude: Double) {
            require(latitude in -90.0..90.0) { "latitude must be -90..90, was $latitude" }
            require(longitude in -180.0..180.0) { "longitude must be -180..180, was $longitude" }
        }
    }
}
