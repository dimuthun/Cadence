package com.cadence.platform.trigger

import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.plugin.TriggerHandler
import com.cadence.domain.trigger.ConnectionEvent
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import com.cadence.platform.common.appTransitions
import com.cadence.platform.common.thresholdCrossings
import com.cadence.platform.common.transitionsTo
import com.cadence.platform.events.ExternalEventKind
import com.cadence.platform.events.ExternalTriggerBus
import com.cadence.platform.monitor.BatteryMonitor
import com.cadence.platform.monitor.BluetoothMonitor
import com.cadence.platform.monitor.ForegroundAppMonitor
import com.cadence.platform.monitor.ScreenStateMonitor
import com.cadence.platform.monitor.WifiMonitor
import java.time.Clock
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

// Triggers driven by shared device-state monitors. Each one's registration lifetime is the
// collection of its flow; the monitor's system listener is released once the last trigger stops.

/** [TriggerConfig.BatteryLevel]. The level when registering is a baseline and never fires. */
class BatteryLevelTriggerHandler @Inject constructor(
    private val battery: BatteryMonitor,
    private val clock: Clock,
) : TriggerHandler<TriggerConfig.BatteryLevel> {
    override val configType: KClass<TriggerConfig.BatteryLevel> = TriggerConfig.BatteryLevel::class

    override fun observe(config: TriggerConfig.BatteryLevel): Flow<TriggerEvent> = battery.snapshots
        .map { it.levelPercent }
        .thresholdCrossings(config.thresholdPercent, config.direction)
        .map { level -> TriggerEvent(config, clock.instant(), mapOf("battery" to level.toString())) }
}

/** [TriggerConfig.PowerConnection]. */
class PowerConnectionTriggerHandler @Inject constructor(
    private val battery: BatteryMonitor,
    private val clock: Clock,
) : TriggerHandler<TriggerConfig.PowerConnection> {
    override val configType: KClass<TriggerConfig.PowerConnection> = TriggerConfig.PowerConnection::class

    override fun observe(config: TriggerConfig.PowerConnection): Flow<TriggerEvent> = battery.snapshots
        .map { it.plugged }
        .transitionsTo(config.event == ConnectionEvent.CONNECTED)
        .map { TriggerEvent(config, clock.instant()) }
}

/** [TriggerConfig.WifiConnection], for any network. */
class WifiConnectionTriggerHandler @Inject constructor(
    private val wifi: WifiMonitor,
    private val clock: Clock,
) : TriggerHandler<TriggerConfig.WifiConnection> {
    override val configType: KClass<TriggerConfig.WifiConnection> = TriggerConfig.WifiConnection::class

    override fun observe(config: TriggerConfig.WifiConnection): Flow<TriggerEvent> = wifi.connected
        .transitionsTo(config.event == ConnectionEvent.CONNECTED)
        .map { TriggerEvent(config, clock.instant()) }
}

/** [TriggerConfig.BluetoothConnection], optionally for one device address. */
class BluetoothConnectionTriggerHandler @Inject constructor(
    private val bluetooth: BluetoothMonitor,
    private val clock: Clock,
) : TriggerHandler<TriggerConfig.BluetoothConnection> {
    override val configType: KClass<TriggerConfig.BluetoothConnection> = TriggerConfig.BluetoothConnection::class

    override fun requiredPermissions(config: TriggerConfig.BluetoothConnection) =
        setOf(PermissionRequirement.BLUETOOTH_CONNECT)

    override fun observe(config: TriggerConfig.BluetoothConnection): Flow<TriggerEvent> = bluetooth.changes
        .filter { change ->
            change.connected == (config.event == ConnectionEvent.CONNECTED) &&
                (config.deviceAddress == null || change.address.equals(config.deviceAddress, ignoreCase = true))
        }
        .map { change ->
            TriggerEvent(
                source = config,
                occurredAt = clock.instant(),
                data = buildMap {
                    put("deviceAddress", change.address)
                    change.name?.let { put("deviceName", it) }
                },
            )
        }
}

/** [TriggerConfig.AppLifecycle]. Polls usage stats while the screen is on. */
class AppLifecycleTriggerHandler @Inject constructor(
    private val apps: ForegroundAppMonitor,
    private val clock: Clock,
) : TriggerHandler<TriggerConfig.AppLifecycle> {
    override val configType: KClass<TriggerConfig.AppLifecycle> = TriggerConfig.AppLifecycle::class

    override fun requiredPermissions(config: TriggerConfig.AppLifecycle) = setOf(PermissionRequirement.USAGE_ACCESS)

    override fun observe(config: TriggerConfig.AppLifecycle): Flow<TriggerEvent> = apps.foregroundPackage
        .appTransitions(config.packageName)
        .filter { it == config.event }
        .map { TriggerEvent(config, clock.instant(), mapOf("package" to config.packageName)) }
}

/** [TriggerConfig.ScreenState]. */
class ScreenStateTriggerHandler @Inject constructor(
    private val screen: ScreenStateMonitor,
    private val clock: Clock,
) : TriggerHandler<TriggerConfig.ScreenState> {
    override val configType: KClass<TriggerConfig.ScreenState> = TriggerConfig.ScreenState::class

    override fun observe(config: TriggerConfig.ScreenState): Flow<TriggerEvent> = screen.events
        .filter { it == config.event }
        .map { TriggerEvent(config, clock.instant()) }
}

/** [TriggerConfig.BootCompleted], posted by the system event receiver. */
class BootCompletedTriggerHandler @Inject constructor(
    private val bus: ExternalTriggerBus,
) : TriggerHandler<TriggerConfig.BootCompleted> {
    override val configType: KClass<TriggerConfig.BootCompleted> = TriggerConfig.BootCompleted::class

    override fun observe(config: TriggerConfig.BootCompleted): Flow<TriggerEvent> = bus
        .observe(ExternalEventKind.BOOT, maxReplayAge = BOOT_REPLAY_WINDOW)
        .map { TriggerEvent(config, it.occurredAt, id = it.id) }

    private companion object {
        /** The engine starts within seconds of boot; this also tolerates slow devices. */
        val BOOT_REPLAY_WINDOW = 2.minutes
    }
}

/**
 * [TriggerConfig.NotificationPosted]. Notification title and text are only used for matching
 * and are never written to the log: only the package name is recorded.
 */
class NotificationPostedTriggerHandler @Inject constructor(
    private val bus: ExternalTriggerBus,
) : TriggerHandler<TriggerConfig.NotificationPosted> {
    override val configType: KClass<TriggerConfig.NotificationPosted> = TriggerConfig.NotificationPosted::class

    override fun requiredPermissions(config: TriggerConfig.NotificationPosted) =
        setOf(PermissionRequirement.NOTIFICATION_LISTENER)

    override fun observe(config: TriggerConfig.NotificationPosted): Flow<TriggerEvent> = bus
        // The listener runs in-process alongside the engine, so replay isn't needed.
        .observe(ExternalEventKind.NOTIFICATION, maxReplayAge = Duration.ZERO)
        .filter { event ->
            event.data[DATA_PACKAGE] == config.packageName &&
                (config.textContains.isNullOrBlank() || event.matchesText(config.textContains))
        }
        .map { event ->
            TriggerEvent(config, event.occurredAt, mapOf("package" to config.packageName), id = event.id)
        }

    private fun com.cadence.platform.events.ExternalTriggerEvent.matchesText(needle: String): Boolean =
        data[DATA_TITLE].orEmpty().contains(needle, ignoreCase = true) ||
            data[DATA_TEXT].orEmpty().contains(needle, ignoreCase = true)

    companion object {
        const val DATA_PACKAGE = "package"
        const val DATA_TITLE = "title"
        const val DATA_TEXT = "text"
    }
}
