package com.cadence.platform.events

import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow

/** Sources of events delivered by Android components rather than in-process listeners. */
enum class ExternalEventKind { ALARM, BOOT, NOTIFICATION, GEOFENCE }

/**
 * An event posted by a manifest component (alarm/boot receiver, notification listener).
 *
 * @property id unique id. Trigger handlers pass it on as `TriggerEvent.id` so the engine runs a
 * macro at most once per event, even when it's replayed.
 */
data class ExternalTriggerEvent(
    val id: String,
    val kind: ExternalEventKind,
    val occurredAt: Instant,
    val data: Map<String, String>,
)

/**
 * Hands events from Android components to trigger handlers.
 *
 * Recent events are replayed to new subscribers: when an alarm or boot wakes the process, the
 * receiver posts before the engine has registered any trigger. Each subscriber picks how old a
 * replayed event may be (`maxReplayAge`), and the engine skips ids it has already handled.
 */
@Singleton
class ExternalTriggerBus @Inject constructor(private val clock: Clock) {

    private val events = MutableSharedFlow<ExternalTriggerEvent>(
        replay = REPLAY_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Publishes an event. Never suspends; the oldest replayed event is dropped when full. */
    fun post(kind: ExternalEventKind, data: Map<String, String> = emptyMap()): ExternalTriggerEvent {
        val event = ExternalTriggerEvent(UUID.randomUUID().toString(), kind, clock.instant(), data)
        events.tryEmit(event)
        return event
    }

    /**
     * Events of [kind], including replayed ones no older than [maxReplayAge] at subscription
     * time. Pass `Duration.ZERO` to receive only new events.
     */
    fun observe(kind: ExternalEventKind, maxReplayAge: Duration): Flow<ExternalTriggerEvent> = flow {
        val since = clock.instant().minusMillis(maxReplayAge.inWholeMilliseconds)
        emitAll(events.filter { it.kind == kind && !it.occurredAt.isBefore(since) })
    }

    private companion object {
        const val REPLAY_CAPACITY = 32
    }
}
