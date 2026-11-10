package com.cadence.platform.trigger

import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.plugin.TriggerHandler
import com.cadence.domain.trigger.DeviceTrigger
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import com.cadence.platform.alarm.AlarmReceiver
import com.cadence.platform.alarm.AlarmScheduler
import com.cadence.platform.alarm.DayOfMonthSchedule
import com.cadence.platform.alarm.SunSchedule
import com.cadence.platform.common.positionChangesTo
import com.cadence.platform.common.shakeThresholdG
import com.cadence.platform.common.shakes
import com.cadence.platform.common.stablePositions
import com.cadence.platform.common.thresholdCrossings
import com.cadence.platform.common.transitionsTo
import com.cadence.platform.common.zipWithPrevious
import com.cadence.platform.events.ExternalEventKind
import com.cadence.platform.events.ExternalTriggerBus
import com.cadence.platform.monitor.AppPackageMonitor
import com.cadence.platform.monitor.BatteryMonitor
import com.cadence.platform.monitor.DeviceSwitchMonitor
import com.cadence.platform.monitor.MediaSessionMonitor
import com.cadence.platform.monitor.NetworkStateMonitor
import com.cadence.platform.monitor.SensorMonitor
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.sample

// Handlers for DeviceTrigger configs. Each registration's lifetime is the collection of its
// flow; shared monitors release their system listener once the last trigger stops.

/** [DeviceTrigger.SwitchChanged]. The state when registering is a baseline and never fires. */
class SwitchChangedTriggerHandler @Inject constructor(
    private val switches: DeviceSwitchMonitor,
    private val clock: Clock,
) : TriggerHandler<DeviceTrigger.SwitchChanged> {
    override val configType: KClass<DeviceTrigger.SwitchChanged> = DeviceTrigger.SwitchChanged::class

    override fun observe(config: DeviceTrigger.SwitchChanged): Flow<TriggerEvent> = switches.states(config.switch)
        .transitionsTo(config.turnedOn)
        .map { TriggerEvent(config, clock.instant(), mapOf("state" to if (config.turnedOn) "on" else "off")) }
}

/** [DeviceTrigger.RingerModeChanged]. */
class RingerModeChangedTriggerHandler @Inject constructor(
    private val switches: DeviceSwitchMonitor,
    private val clock: Clock,
) : TriggerHandler<DeviceTrigger.RingerModeChanged> {
    override val configType: KClass<DeviceTrigger.RingerModeChanged> = DeviceTrigger.RingerModeChanged::class

    override fun observe(config: DeviceTrigger.RingerModeChanged): Flow<TriggerEvent> = switches.ringerModes
        .zipWithPrevious()
        .filter { (_, current) -> current == config.mode }
        .map { (previous, _) -> TriggerEvent(config, clock.instant(), mapOf("previousMode" to previous.name.lowercase(Locale.ROOT))) }
}

/** [DeviceTrigger.Shake]. */
class ShakeTriggerHandler @Inject constructor(
    private val sensors: SensorMonitor,
    private val clock: Clock,
) : TriggerHandler<DeviceTrigger.Shake> {
    override val configType: KClass<DeviceTrigger.Shake> = DeviceTrigger.Shake::class

    override fun observe(config: DeviceTrigger.Shake): Flow<TriggerEvent> = sensors.acceleration
        .shakes(shakeThresholdG(config.sensitivity))
        .map { TriggerEvent(config, clock.instant()) }
}

/** [DeviceTrigger.PositionChanged]. The position when registering is a baseline. */
class PositionChangedTriggerHandler @Inject constructor(
    private val sensors: SensorMonitor,
    private val clock: Clock,
) : TriggerHandler<DeviceTrigger.PositionChanged> {
    override val configType: KClass<DeviceTrigger.PositionChanged> = DeviceTrigger.PositionChanged::class

    override fun observe(config: DeviceTrigger.PositionChanged): Flow<TriggerEvent> = sensors.acceleration
        .stablePositions()
        .positionChangesTo(config.position)
        .map { TriggerEvent(config, clock.instant()) }
}

/** [DeviceTrigger.Proximity]. */
class ProximityTriggerHandler @Inject constructor(
    private val sensors: SensorMonitor,
    private val clock: Clock,
) : TriggerHandler<DeviceTrigger.Proximity> {
    override val configType: KClass<DeviceTrigger.Proximity> = DeviceTrigger.Proximity::class

    override fun observe(config: DeviceTrigger.Proximity): Flow<TriggerEvent> = sensors.proximityNear
        .transitionsTo(config.near)
        .map { TriggerEvent(config, clock.instant()) }
}

/** [DeviceTrigger.LightLevel]. Sampled every couple of seconds so flicker doesn't fire repeatedly. */
@OptIn(FlowPreview::class)
class LightLevelTriggerHandler @Inject constructor(
    private val sensors: SensorMonitor,
    private val clock: Clock,
) : TriggerHandler<DeviceTrigger.LightLevel> {
    override val configType: KClass<DeviceTrigger.LightLevel> = DeviceTrigger.LightLevel::class

    override fun observe(config: DeviceTrigger.LightLevel): Flow<TriggerEvent> = sensors.lightLux
        .sample(2.seconds)
        .thresholdCrossings(config.thresholdLux, config.direction)
        .map { lux -> TriggerEvent(config, clock.instant(), mapOf("lux" to lux.toString())) }
}

/** [DeviceTrigger.BatteryTemperature]. */
class BatteryTemperatureTriggerHandler @Inject constructor(
    private val battery: BatteryMonitor,
    private val clock: Clock,
) : TriggerHandler<DeviceTrigger.BatteryTemperature> {
    override val configType: KClass<DeviceTrigger.BatteryTemperature> = DeviceTrigger.BatteryTemperature::class

    override fun observe(config: DeviceTrigger.BatteryTemperature): Flow<TriggerEvent> = battery.snapshots
        .mapNotNull { it.temperatureCelsius }
        .thresholdCrossings(config.thresholdCelsius, config.direction)
        .map { celsius -> TriggerEvent(config, clock.instant(), mapOf("temperature" to celsius.toString())) }
}

/** [DeviceTrigger.AppPackageChanged]. */
class AppPackageChangedTriggerHandler @Inject constructor(
    private val packages: AppPackageMonitor,
    private val clock: Clock,
) : TriggerHandler<DeviceTrigger.AppPackageChanged> {
    override val configType: KClass<DeviceTrigger.AppPackageChanged> = DeviceTrigger.AppPackageChanged::class

    override fun observe(config: DeviceTrigger.AppPackageChanged): Flow<TriggerEvent> = packages.changes
        .filter { it.event == config.event && (config.packageName == null || it.packageName == config.packageName) }
        .map { change ->
            TriggerEvent(
                source = config,
                occurredAt = clock.instant(),
                data = buildMap {
                    put("package", change.packageName)
                    change.label?.let { put("app", it) }
                },
            )
        }
}

/** [DeviceTrigger.NetworkTypeChanged]. */
class NetworkTypeChangedTriggerHandler @Inject constructor(
    private val network: NetworkStateMonitor,
    private val clock: Clock,
) : TriggerHandler<DeviceTrigger.NetworkTypeChanged> {
    override val configType: KClass<DeviceTrigger.NetworkTypeChanged> = DeviceTrigger.NetworkTypeChanged::class

    override fun observe(config: DeviceTrigger.NetworkTypeChanged): Flow<TriggerEvent> = network.state
        .distinctUntilChanged { old, new -> old.type == new.type }
        .zipWithPrevious()
        .filter { (_, current) -> config.to == null || current.type == config.to }
        .map { (previous, current) ->
            TriggerEvent(
                source = config,
                occurredAt = clock.instant(),
                data = mapOf(
                    "networkType" to current.type.name.lowercase(Locale.ROOT),
                    "previousType" to previous.type.name.lowercase(Locale.ROOT),
                    "ip" to current.ip,
                ),
            )
        }
}

/** [DeviceTrigger.IpAddressChanged]. Going offline (no address) doesn't fire. */
class IpAddressChangedTriggerHandler @Inject constructor(
    private val network: NetworkStateMonitor,
    private val clock: Clock,
) : TriggerHandler<DeviceTrigger.IpAddressChanged> {
    override val configType: KClass<DeviceTrigger.IpAddressChanged> = DeviceTrigger.IpAddressChanged::class

    override fun observe(config: DeviceTrigger.IpAddressChanged): Flow<TriggerEvent> = network.state
        .map { it.ip }
        .filter { it.isNotEmpty() }
        .distinctUntilChanged()
        .zipWithPrevious()
        .map { (previous, current) -> TriggerEvent(config, clock.instant(), mapOf("ip" to current, "previousIp" to previous)) }
}

/** [DeviceTrigger.MusicTrackChanged]. Only the player's package is recorded, never the track. */
class MusicTrackChangedTriggerHandler @Inject constructor(
    private val media: MediaSessionMonitor,
    private val clock: Clock,
) : TriggerHandler<DeviceTrigger.MusicTrackChanged> {
    override val configType: KClass<DeviceTrigger.MusicTrackChanged> = DeviceTrigger.MusicTrackChanged::class

    override fun requiredPermissions(config: DeviceTrigger.MusicTrackChanged) = setOf(PermissionRequirement.NOTIFICATION_LISTENER)

    override fun observe(config: DeviceTrigger.MusicTrackChanged): Flow<TriggerEvent> = media.trackChanges
        .filter { config.packageName == null || it.packageName == config.packageName }
        .map { track -> TriggerEvent(config, clock.instant(), mapOf("package" to track.packageName)) }
}

/** [DeviceTrigger.Sun], with an exact alarm at each sunrise or sunset. */
class SunTriggerHandler @Inject constructor(
    private val scheduler: AlarmScheduler,
    private val bus: ExternalTriggerBus,
    private val clock: Clock,
) : TriggerHandler<DeviceTrigger.Sun> {
    override val configType: KClass<DeviceTrigger.Sun> = DeviceTrigger.Sun::class

    override fun requiredPermissions(config: DeviceTrigger.Sun) = setOf(PermissionRequirement.EXACT_ALARM)

    override fun observe(config: DeviceTrigger.Sun): Flow<TriggerEvent> =
        alarmTrigger(scheduler, bus, clock, SunSchedule.alarmKey(config), config) { after ->
            SunSchedule.nextOccurrence(config, after, clock.zone)
        }
}

/** [DeviceTrigger.DayOfMonth], with an exact alarm. */
class DayOfMonthTriggerHandler @Inject constructor(
    private val scheduler: AlarmScheduler,
    private val bus: ExternalTriggerBus,
    private val clock: Clock,
) : TriggerHandler<DeviceTrigger.DayOfMonth> {
    override val configType: KClass<DeviceTrigger.DayOfMonth> = DeviceTrigger.DayOfMonth::class

    override fun requiredPermissions(config: DeviceTrigger.DayOfMonth) = setOf(PermissionRequirement.EXACT_ALARM)

    override fun observe(config: DeviceTrigger.DayOfMonth): Flow<TriggerEvent> =
        alarmTrigger(scheduler, bus, clock, DayOfMonthSchedule.alarmKey(config), config) { after ->
            DayOfMonthSchedule.nextOccurrence(config, ZonedDateTime.ofInstant(after, clock.zone)).toInstant()
        }
}

/**
 * A trigger that fires at the times [next] computes: schedules the first alarm on collection,
 * the following one at each firing, and releases it on cancellation. Late or early wake-ups
 * never fire the same occurrence twice.
 */
private fun alarmTrigger(
    scheduler: AlarmScheduler,
    bus: ExternalTriggerBus,
    clock: Clock,
    key: String,
    source: TriggerConfig,
    next: (after: Instant) -> Instant?,
): Flow<TriggerEvent> = flow {
    val first = next(clock.instant())
    if (first != null) scheduler.acquire(key, first)
    try {
        emitAll(
            bus.observe(ExternalEventKind.ALARM, maxReplayAge = 2.minutes)
                .filter { it.data[AlarmReceiver.DATA_KEY] == key }
                .map { event ->
                    val scheduledAt = event.data[AlarmReceiver.DATA_SCHEDULED_AT]?.toLongOrNull()
                        ?.takeIf { it > 0 }
                        ?.let(Instant::ofEpochMilli)
                        ?: event.occurredAt
                    next(maxOf(scheduledAt, clock.instant()))?.let { scheduler.reschedule(key, it) }
                    TriggerEvent(source, event.occurredAt, mapOf("scheduledAt" to scheduledAt.toString()), id = event.id)
                },
        )
    } finally {
        if (first != null) scheduler.release(key)
    }
}
