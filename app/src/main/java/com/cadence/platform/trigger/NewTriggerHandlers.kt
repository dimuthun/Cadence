package com.cadence.platform.trigger

import com.cadence.domain.engine.EngineDiagnostics
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.plugin.TriggerHandler
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import com.cadence.platform.alarm.AlarmReceiver
import com.cadence.platform.alarm.AlarmScheduler
import com.cadence.platform.alarm.DateTimeSchedule
import com.cadence.platform.common.movements
import com.cadence.platform.events.ExternalEventKind
import com.cadence.platform.events.ExternalTriggerBus
import com.cadence.platform.location.FusedLocationSource
import com.cadence.platform.location.GeofenceReceiver
import com.cadence.platform.location.GeofenceRegistrar
import com.cadence.platform.location.PlayServicesAvailability
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext

private val LOCATION_PERMISSIONS = setOf(PermissionRequirement.FINE_LOCATION, PermissionRequirement.BACKGROUND_LOCATION)

/** Covers the gap between Android waking the process and the engine registering triggers. */
private val WAKE_REPLAY_WINDOW = 2.minutes

/**
 * Retries transient failures (location turned off, Play services busy) with backoff from 1 to
 * 30 minutes. A revoked permission isn't retried; the engine re-registers once it's granted.
 */
private fun <T> Flow<T>.retryWithBackoff(diagnostics: EngineDiagnostics, what: String): Flow<T> =
    retryWhen { cause, attempt ->
        if (cause is SecurityException) return@retryWhen false
        val wait: Duration = (1L shl attempt.coerceAtMost(5).toInt()).minutes.coerceAtMost(30.minutes)
        diagnostics.onError(null, "$what failed; retrying in $wait", cause)
        delay(wait)
        true
    }

/** [TriggerConfig.DateTime]: a one-off date and time, or a regular interval, using exact alarms. */
class DateTimeTriggerHandler @Inject constructor(
    private val scheduler: AlarmScheduler,
    private val bus: ExternalTriggerBus,
    private val clock: Clock,
) : TriggerHandler<TriggerConfig.DateTime> {

    override val configType: KClass<TriggerConfig.DateTime> = TriggerConfig.DateTime::class

    override fun requiredPermissions(config: TriggerConfig.DateTime) = setOf(PermissionRequirement.EXACT_ALARM)

    override fun observe(config: TriggerConfig.DateTime): Flow<TriggerEvent> = flow {
        val key = DateTimeSchedule.alarmKey(config)
        val first = DateTimeSchedule.nextOccurrence(config, clock.instant())
        // Even with nothing left to schedule, listen for a replayed alarm: a one-off alarm that
        // just woke the process is already in the past by the time the trigger registers.
        if (first != null) scheduler.acquire(key, first)
        try {
            emitAll(
                bus.observe(ExternalEventKind.ALARM, WAKE_REPLAY_WINDOW)
                    .filter { it.data[AlarmReceiver.DATA_KEY] == key }
                    .map { event ->
                        val scheduledAt = event.data[AlarmReceiver.DATA_SCHEDULED_AT]?.toLongOrNull()
                            ?.takeIf { it > 0 }
                            ?.let(Instant::ofEpochMilli)
                            ?: event.occurredAt
                        DateTimeSchedule.nextOccurrence(config, maxOf(scheduledAt, clock.instant()))
                            ?.let { next -> scheduler.reschedule(key, next) }
                        TriggerEvent(config, event.occurredAt, mapOf("scheduledAt" to scheduledAt.toString()), id = event.id)
                    },
            )
        } finally {
            if (first != null) scheduler.release(key)
        }
    }
}

/** [TriggerConfig.LocationArea], backed by a Play services geofence. */
class LocationAreaTriggerHandler @Inject constructor(
    private val registrar: GeofenceRegistrar,
    private val playServices: PlayServicesAvailability,
    private val bus: ExternalTriggerBus,
    private val diagnostics: EngineDiagnostics,
) : TriggerHandler<TriggerConfig.LocationArea> {

    override val configType: KClass<TriggerConfig.LocationArea> = TriggerConfig.LocationArea::class

    override fun requiredPermissions(config: TriggerConfig.LocationArea) = LOCATION_PERMISSIONS

    override fun observe(config: TriggerConfig.LocationArea): Flow<TriggerEvent> = flow {
        playServices.requireAvailable()
        val key = GeofenceRegistrar.key(config.latitude, config.longitude, config.radiusMeters)
        val wanted = when (config.event) {
            TriggerConfig.LocationArea.Event.ENTER -> GeofenceReceiver.TRANSITION_ENTER
            TriggerConfig.LocationArea.Event.EXIT -> GeofenceReceiver.TRANSITION_EXIT
        }
        registrar.acquire(key, config.latitude, config.longitude, config.radiusMeters)
        try {
            emitAll(
                bus.observe(ExternalEventKind.GEOFENCE, WAKE_REPLAY_WINDOW)
                    .filter { it.data[GeofenceReceiver.DATA_KEY] == key && it.data[GeofenceReceiver.DATA_TRANSITION] == wanted }
                    .map { event ->
                        TriggerEvent(
                            source = config,
                            occurredAt = event.occurredAt,
                            data = buildMap {
                                put("transition", wanted)
                                config.label?.let { put("place", it) }
                            },
                            id = event.id,
                        )
                    },
            )
        } finally {
            withContext(NonCancellable) { registrar.release(key) }
        }
    }.retryWithBackoff(diagnostics, "Location area trigger")
}

/** [TriggerConfig.LocationMoved], from balanced-power fused location updates. */
class LocationMovedTriggerHandler @Inject constructor(
    private val locations: FusedLocationSource,
    private val playServices: PlayServicesAvailability,
    private val diagnostics: EngineDiagnostics,
    private val clock: Clock,
) : TriggerHandler<TriggerConfig.LocationMoved> {

    override val configType: KClass<TriggerConfig.LocationMoved> = TriggerConfig.LocationMoved::class

    override fun requiredPermissions(config: TriggerConfig.LocationMoved) = LOCATION_PERMISSIONS

    override fun observe(config: TriggerConfig.LocationMoved): Flow<TriggerEvent> = flow {
        playServices.requireAvailable()
        emitAll(
            locations.updates(UPDATE_INTERVAL_MILLIS, minDistanceMeters = config.distanceMeters / 4f)
                .movements(config.distanceMeters)
                .map { moved -> TriggerEvent(config, clock.instant(), mapOf("movedMeters" to moved.roundToInt().toString())) },
        )
    }.retryWithBackoff(diagnostics, "Distance moved trigger")

    private companion object {
        /** About one update a minute; Android may batch them further in Doze. */
        const val UPDATE_INTERVAL_MILLIS = 60_000L
    }
}
