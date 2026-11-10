package com.cadence.platform.variable

import android.content.Context
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.variable.SystemVariableSource
import com.cadence.platform.device.AndroidDeviceStateReader
import com.cadence.platform.location.FusedLocationSource
import com.cadence.platform.location.LocationSnapshot
import com.cadence.platform.monitor.MediaSessionMonitor
import com.cadence.platform.monitor.MediaTrack
import com.cadence.platform.permission.AndroidPermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in variables read from the device, dispatched by namespace to small readers.
 *
 * Conventions: booleans are `true`/`false`; numbers use `Locale.ROOT` (always a dot), because
 * they're often sent in HTTP requests; a value Android can't provide right now is `""`; an
 * unknown key is `null` so the placeholder stays visible. Nothing here needs a permission
 * beyond those Cadence already declares. Location and Bluetooth device names are empty
 * without their permissions.
 */
@Singleton
class AndroidSystemVariableSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val permissionChecker: AndroidPermissionChecker,
    private val locationSource: FusedLocationSource,
    private val media: MediaSessionMonitor,
    private val clock: Clock,
) : SystemVariableSource {

    private val battery = BatteryReader(context)
    private val network = NetworkReader(context)
    private val radios = RadioReader(context, permissionChecker)
    private val displayAndSound = DisplaySoundReader(context)
    private val device = DeviceInfoReader(context)
    private val time = TimeReader(clock)

    override suspend fun value(key: String, context: ExecutionContext): String? = when {
        key.startsWith("battery.") -> battery.read(key)
        key == "wifi.connected" -> AndroidDeviceStateReader.isWifiConnectedNow(this.context).toString()
        key.startsWith("wifi.") || key.startsWith("network.") -> network.read(key)
        key.startsWith("bluetooth.") || key.startsWith("nfc.") -> radios.read(key)
        key.startsWith("display.") || key.startsWith("audio.") -> displayAndSound.read(key)
        key.startsWith("device.") || key.startsWith("storage.") || key.startsWith("memory.") -> device.read(key)
        key.startsWith("time.") -> time.read(key)
        key.startsWith("location.") -> location(key, context)
        key.startsWith("music.") -> music(key, context)
        else -> null
    }

    /** Track details, read once per run so title and artist always belong together. */
    private fun music(key: String, run: ExecutionContext): String? {
        if (key !in MUSIC_KEYS) return null
        val track = run.runCache.getOrPut(MUSIC_CACHE_KEY) { media.currentTrack() ?: NoTrack } as? MediaTrack ?: return ""
        return when (key) {
            "music.title" -> track.title
            "music.artist" -> track.artist
            "music.album" -> track.album
            else -> track.packageName
        }
    }

    private object NoTrack

    private suspend fun location(key: String, run: ExecutionContext): String? {
        if (key !in LOCATION_KEYS) return null
        if (!permissionChecker.isGranted(PermissionRequirement.FINE_LOCATION)) return ""
        // One reading per run, so latitude and longitude always belong together.
        val snapshot = run.runCache.getOrPut(LOCATION_CACHE_KEY) { locationSource.lastSnapshot() } as? LocationSnapshot
            ?: return ""
        return when (key) {
            "location.latitude" -> fmt("%.6f", snapshot.latitude)
            "location.longitude" -> fmt("%.6f", snapshot.longitude)
            "location.altitude" -> snapshot.altitudeMeters?.let { fmt("%.1f", it) }.orEmpty()
            "location.speed" -> snapshot.speedMetersPerSecond?.let { fmt("%.1f", it) }.orEmpty()
            "location.speed_kmh" -> snapshot.speedMetersPerSecond?.let { fmt("%.1f", it * 3.6) }.orEmpty()
            "location.bearing" -> snapshot.bearingDegrees?.let { fmt("%.0f", it) }.orEmpty()
            "location.accuracy" -> snapshot.accuracyMeters?.let { fmt("%.0f", it) }.orEmpty()
            "location.time" -> Instant.ofEpochMilli(snapshot.timeMillis).toString()
            "location.age_seconds" -> ((clock.millis() - snapshot.timeMillis) / 1_000).coerceAtLeast(0).toString()
            "location.map_url" -> {
                val lat = fmt("%.6f", snapshot.latitude)
                val lon = fmt("%.6f", snapshot.longitude)
                "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=17/$lat/$lon"
            }
            else -> null
        }
    }

    companion object {
        /** Run-cache key for the run's location reading; Refresh location replaces it. */
        const val LOCATION_CACHE_KEY = "location.snapshot"
        private const val MUSIC_CACHE_KEY = "music.track"
        private val MUSIC_KEYS = setOf("music.title", "music.artist", "music.album", "music.app")

        private val LOCATION_KEYS = setOf(
            "location.latitude", "location.longitude", "location.altitude", "location.speed",
            "location.speed_kmh", "location.bearing", "location.accuracy", "location.time",
            "location.age_seconds", "location.map_url",
        )
    }
}
