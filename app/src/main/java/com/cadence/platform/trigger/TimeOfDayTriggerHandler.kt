package com.cadence.platform.trigger

import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.plugin.TriggerHandler
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import com.cadence.platform.alarm.AlarmReceiver
import com.cadence.platform.alarm.AlarmScheduler
import com.cadence.platform.alarm.TimeOfDaySchedule
import com.cadence.platform.events.ExternalEventKind
import com.cadence.platform.events.ExternalTriggerBus
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Time-of-day trigger backed by exact alarms. Registering schedules the next occurrence. Each
 * firing schedules the one after, and unregistering releases the alarm.
 */
class TimeOfDayTriggerHandler @Inject constructor(
    private val scheduler: AlarmScheduler,
    private val bus: ExternalTriggerBus,
    private val clock: Clock,
) : TriggerHandler<TriggerConfig.TimeOfDay> {

    override val configType: KClass<TriggerConfig.TimeOfDay> = TriggerConfig.TimeOfDay::class

    override fun requiredPermissions(config: TriggerConfig.TimeOfDay) = setOf(PermissionRequirement.EXACT_ALARM)

    override fun observe(config: TriggerConfig.TimeOfDay): Flow<TriggerEvent> = flow {
        val key = TimeOfDaySchedule.alarmKey(config)
        scheduler.acquire(key, nextAfter(config, clock.instant()))
        try {
            emitAll(
                bus.observe(ExternalEventKind.ALARM, maxReplayAge = ALARM_REPLAY_WINDOW)
                    .filter { it.data[AlarmReceiver.DATA_KEY] == key }
                    .map { event ->
                        val scheduledAt = event.data[AlarmReceiver.DATA_SCHEDULED_AT]?.toLongOrNull()
                            ?.takeIf { it > 0 }
                            ?.let(Instant::ofEpochMilli)
                            ?: event.occurredAt
                        // Base the next alarm on the later of the planned and actual time, so an
                        // early wake-up can't fire the same occurrence twice.
                        scheduler.reschedule(key, nextAfter(config, maxOf(scheduledAt, clock.instant())))
                        TriggerEvent(
                            source = config,
                            occurredAt = event.occurredAt,
                            data = mapOf("scheduledAt" to scheduledAt.toString()),
                            id = event.id,
                        )
                    },
            )
        } finally {
            scheduler.release(key)
        }
    }

    private fun nextAfter(config: TriggerConfig.TimeOfDay, instant: Instant): Instant =
        TimeOfDaySchedule.nextOccurrence(config, ZonedDateTime.ofInstant(instant, clock.zone)).toInstant()

    private companion object {
        /** Covers the gap between an alarm waking the process and the engine registering triggers. */
        val ALARM_REPLAY_WINDOW = 2.minutes
    }
}
