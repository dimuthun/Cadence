package com.cadence.platform.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.cadence.platform.common.GeoFix
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Whether Google Play services (needed by location triggers) is usable on this device. */
@Singleton
class PlayServicesAvailability @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun isAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** Throws a readable error for engine diagnostics when Play services is missing. */
    fun requireAvailable() {
        check(isAvailable()) { "Google Play services isn't available, so location triggers can't run on this device" }
    }
}

/**
 * Registers geofences with Google Play services. Areas are reference counted per key, so
 * identical areas in several macros share one geofence. Play services watches them even while
 * Cadence's process is asleep and wakes it through [GeofenceReceiver].
 *
 * Geofences don't survive a reboot or Play services data reset. The engine registers them
 * again when it restarts.
 */
@Singleton
class GeofenceRegistrar @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val client = LocationServices.getGeofencingClient(context)
    private val mutex = Mutex()
    private val refCounts = HashMap<String, Int>()

    /** Callers must hold fine + background location (checked by the engine via requirements). */
    @SuppressLint("MissingPermission")
    suspend fun acquire(key: String, latitude: Double, longitude: Double, radiusMeters: Int) = mutex.withLock {
        val count = refCounts[key] ?: 0
        if (count == 0) {
            val geofence = Geofence.Builder()
                .setRequestId(key)
                .setCircularRegion(latitude, longitude, radiusMeters.toFloat())
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
            val request = GeofencingRequest.Builder()
                // No initial trigger: being inside the area when the macro is enabled isn't an "arrival".
                .setInitialTrigger(0)
                .addGeofence(geofence)
                .build()
            client.addGeofences(request, pendingIntent()).await()
        }
        refCounts[key] = count + 1
    }

    suspend fun release(key: String) = mutex.withLock {
        val remaining = (refCounts[key] ?: 0) - 1
        if (remaining > 0) {
            refCounts[key] = remaining
        } else if (refCounts.remove(key) != null) {
            client.removeGeofences(listOf(key))
        }
    }

    /** One shared, mutable PendingIntent: Play services adds the transition details to it. */
    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, GeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    companion object {
        /** Stable key for an area; ~1 m precision is plenty for geofences. */
        fun key(latitude: Double, longitude: Double, radiusMeters: Int): String =
            String.format(Locale.ROOT, "geo:%.5f,%.5f,%d", latitude, longitude, radiusMeters)
    }
}

/** Fused location updates and one-off fixes. */
@Singleton
class FusedLocationSource @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Balanced-power updates (Wi-Fi and cell, GPS only when needed). Registered while collected,
     * removed on cancellation.
     */
    @SuppressLint("MissingPermission") // Requirements are enforced by the engine before collection.
    fun updates(intervalMillis: Long, minDistanceMeters: Float): Flow<GeoFix> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(GeoFix(it.latitude, it.longitude, it.accuracy)) }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper()).await()
        awaitClose { client.removeLocationUpdates(callback) }
    }

    /**
     * The last known location with all details, or a quick balanced-power fix if none is cached.
     * `null` without permission or when location is off.
     */
    @SuppressLint("MissingPermission") // Callers check fine location first; SecurityException handled.
    suspend fun lastSnapshot(): LocationSnapshot? = try {
        val location = client.lastLocation.await()
            ?: withTimeoutOrNull(10.seconds) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
            }
        location?.toSnapshot()
    } catch (e: SecurityException) {
        null
    }

    /**
     * A brand-new fix (never a cached one), or `null` if none arrives within [timeoutMillis],
     * permission is missing, or location is off. Play services also updates its last-known
     * location with it, so later readings benefit too.
     */
    @SuppressLint("MissingPermission") // Callers check fine location first; SecurityException handled.
    suspend fun freshSnapshot(highAccuracy: Boolean, timeoutMillis: Long): LocationSnapshot? {
        val cancellation = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .setDurationMillis(timeoutMillis)
            .build()
        return try {
            withTimeoutOrNull(timeoutMillis + 2_000) {
                client.getCurrentLocation(request, cancellation.token).await()
            }?.toSnapshot()
        } catch (e: SecurityException) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    private fun android.location.Location.toSnapshot() = LocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = if (hasAltitude()) altitude else null,
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        timeMillis = time,
    )

    /** A fresh high-accuracy fix, falling back to the last known one. `null` if unavailable. */
    @SuppressLint("MissingPermission") // Callers check fine location first; SecurityException handled.
    suspend fun currentFix(): GeoFix? = try {
        val cancellation = CancellationTokenSource()
        val location = withTimeoutOrNull(20.seconds) {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
        } ?: run {
            cancellation.cancel()
            client.lastLocation.await()
        }
        location?.let { GeoFix(it.latitude, it.longitude, it.accuracy) }
    } catch (e: SecurityException) {
        null
    }
}
