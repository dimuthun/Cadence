package com.cadence.di

import com.cadence.domain.constraint.BatteryLevelRangeEvaluator
import com.cadence.domain.constraint.ChargingStateEvaluator
import com.cadence.domain.constraint.DaylightEvaluator
import com.cadence.domain.constraint.DaysOfMonthEvaluator
import com.cadence.domain.constraint.DaysOfWeekEvaluator
import com.cadence.domain.constraint.MonthsEvaluator
import com.cadence.domain.constraint.NetworkTypeIsEvaluator
import com.cadence.domain.constraint.RingerModeIsEvaluator
import com.cadence.domain.constraint.SwitchStateEvaluator
import com.cadence.platform.device.AppInForegroundEvaluator
import com.cadence.platform.device.PositionIsEvaluator
import com.cadence.platform.trigger.AppPackageChangedTriggerHandler
import com.cadence.platform.trigger.BatteryTemperatureTriggerHandler
import com.cadence.platform.trigger.DayOfMonthTriggerHandler
import com.cadence.platform.trigger.IpAddressChangedTriggerHandler
import com.cadence.platform.trigger.LightLevelTriggerHandler
import com.cadence.platform.trigger.MusicTrackChangedTriggerHandler
import com.cadence.platform.trigger.NetworkTypeChangedTriggerHandler
import com.cadence.platform.trigger.PositionChangedTriggerHandler
import com.cadence.platform.trigger.ProximityTriggerHandler
import com.cadence.platform.trigger.RingerModeChangedTriggerHandler
import com.cadence.platform.trigger.ShakeTriggerHandler
import com.cadence.platform.trigger.SunTriggerHandler
import com.cadence.platform.trigger.SwitchChangedTriggerHandler
import com.cadence.domain.constraint.TimeRangeEvaluator
import com.cadence.domain.constraint.WifiNetworkEvaluator
import com.cadence.domain.device.DeviceStateReader
import com.cadence.domain.plugin.ActionHandler
import com.cadence.domain.plugin.ConstraintEvaluator
import com.cadence.domain.plugin.TriggerHandler
import com.cadence.domain.engine.EngineHost
import com.cadence.domain.plugin.LogMessageActionHandler
import com.cadence.domain.plugin.MacroEnabledEvaluator
import com.cadence.domain.plugin.SetMacroEnabledActionHandler
import com.cadence.domain.plugin.TimeSinceLastRunEvaluator
import com.cadence.domain.repository.ExecutionLogRepository
import com.cadence.domain.repository.MacroRepository
import com.cadence.domain.variable.GlobalVariableRepository
import com.cadence.platform.action.ShowToastActionHandler
import dagger.Lazy
import com.cadence.domain.variable.SetVariableActionHandler
import com.cadence.domain.variable.VariableCompareEvaluator
import com.cadence.platform.action.LaunchAppActionHandler
import com.cadence.platform.http.HttpRequestActionHandler
import com.cadence.platform.location.RefreshLocationActionHandler
import com.cadence.platform.action.OpenUrlActionHandler
import com.cadence.platform.action.SetBluetoothActionHandler
import com.cadence.platform.action.SetBrightnessActionHandler
import com.cadence.platform.action.SetFlashlightActionHandler
import com.cadence.platform.action.SetRingerModeActionHandler
import com.cadence.platform.action.SetVolumeActionHandler
import com.cadence.platform.action.SetWifiActionHandler
import com.cadence.platform.action.ShowNotificationActionHandler
import com.cadence.platform.action.SpeakTextActionHandler
import com.cadence.platform.action.VibrateActionHandler
import com.cadence.platform.trigger.AppLifecycleTriggerHandler
import com.cadence.platform.trigger.BatteryLevelTriggerHandler
import com.cadence.platform.trigger.BluetoothConnectionTriggerHandler
import com.cadence.platform.trigger.BootCompletedTriggerHandler
import com.cadence.platform.trigger.DateTimeTriggerHandler
import com.cadence.platform.trigger.LocationAreaTriggerHandler
import com.cadence.platform.trigger.LocationMovedTriggerHandler
import com.cadence.platform.trigger.NotificationPostedTriggerHandler
import com.cadence.platform.trigger.PowerConnectionTriggerHandler
import com.cadence.platform.trigger.ScreenStateTriggerHandler
import com.cadence.platform.trigger.TimeOfDayTriggerHandler
import com.cadence.platform.trigger.WifiConnectionTriggerHandler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Clock

/**
 * The plugin registry. To add a trigger, action, or constraint: implement its handler and add
 * one `@Binds @IntoSet` (or `@Provides @IntoSet`) line here. The engine needs no changes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PluginModule {

    // region Triggers
    @Binds @IntoSet abstract fun timeOfDay(handler: TimeOfDayTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun batteryLevel(handler: BatteryLevelTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun powerConnection(handler: PowerConnectionTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun wifiConnection(handler: WifiConnectionTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun bluetoothConnection(handler: BluetoothConnectionTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun appLifecycle(handler: AppLifecycleTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun bootCompleted(handler: BootCompletedTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun screenState(handler: ScreenStateTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun notificationPosted(handler: NotificationPostedTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun dateTime(handler: DateTimeTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun locationArea(handler: LocationAreaTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun locationMoved(handler: LocationMovedTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun switchChanged(handler: SwitchChangedTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun ringerModeChanged(handler: RingerModeChangedTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun shake(handler: ShakeTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun positionChanged(handler: PositionChangedTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun proximity(handler: ProximityTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun lightLevel(handler: LightLevelTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun batteryTemperature(handler: BatteryTemperatureTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun appPackageChanged(handler: AppPackageChangedTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun networkTypeChanged(handler: NetworkTypeChangedTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun ipAddressChanged(handler: IpAddressChangedTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun musicTrackChanged(handler: MusicTrackChangedTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun sun(handler: SunTriggerHandler): TriggerHandler<*>
    @Binds @IntoSet abstract fun dayOfMonth(handler: DayOfMonthTriggerHandler): TriggerHandler<*>
    // endregion

    // region Platform constraints
    @Binds @IntoSet abstract fun positionIs(evaluator: PositionIsEvaluator): ConstraintEvaluator<*>
    @Binds @IntoSet abstract fun appInForeground(evaluator: AppInForegroundEvaluator): ConstraintEvaluator<*>
    // endregion

    // region Actions
    @Binds @IntoSet abstract fun showNotification(handler: ShowNotificationActionHandler): ActionHandler<*>
    @Binds @IntoSet abstract fun setWifi(handler: SetWifiActionHandler): ActionHandler<*>
    @Binds @IntoSet abstract fun setBluetooth(handler: SetBluetoothActionHandler): ActionHandler<*>
    @Binds @IntoSet abstract fun setRingerMode(handler: SetRingerModeActionHandler): ActionHandler<*>
    @Binds @IntoSet abstract fun setVolume(handler: SetVolumeActionHandler): ActionHandler<*>
    @Binds @IntoSet abstract fun setBrightness(handler: SetBrightnessActionHandler): ActionHandler<*>
    @Binds @IntoSet abstract fun launchApp(handler: LaunchAppActionHandler): ActionHandler<*>
    @Binds @IntoSet abstract fun openUrl(handler: OpenUrlActionHandler): ActionHandler<*>
    @Binds @IntoSet abstract fun speakText(handler: SpeakTextActionHandler): ActionHandler<*>
    @Binds @IntoSet abstract fun vibrate(handler: VibrateActionHandler): ActionHandler<*>
    @Binds @IntoSet abstract fun setFlashlight(handler: SetFlashlightActionHandler): ActionHandler<*>
    @Binds @IntoSet abstract fun httpRequest(handler: HttpRequestActionHandler): ActionHandler<*>
    @Binds @IntoSet abstract fun showToast(handler: ShowToastActionHandler): ActionHandler<*>
    @Binds @IntoSet abstract fun refreshLocation(handler: RefreshLocationActionHandler): ActionHandler<*>
    // endregion

    companion object {
        // region Constraints (pure domain evaluators, so they're provided rather than bound)
        @Provides @IntoSet fun timeRange(clock: Clock): ConstraintEvaluator<*> = TimeRangeEvaluator(clock)
        @Provides @IntoSet fun daysOfWeek(clock: Clock): ConstraintEvaluator<*> = DaysOfWeekEvaluator(clock)
        @Provides @IntoSet fun batteryRange(state: DeviceStateReader): ConstraintEvaluator<*> = BatteryLevelRangeEvaluator(state)
        @Provides @IntoSet fun charging(state: DeviceStateReader): ConstraintEvaluator<*> = ChargingStateEvaluator(state)
        @Provides @IntoSet fun wifiNetwork(state: DeviceStateReader): ConstraintEvaluator<*> = WifiNetworkEvaluator(state)
        @Provides @IntoSet fun variableCompare(): ConstraintEvaluator<*> = VariableCompareEvaluator()
        @Provides @IntoSet fun switchState(state: DeviceStateReader): ConstraintEvaluator<*> = SwitchStateEvaluator(state)
        @Provides @IntoSet fun ringerModeIs(state: DeviceStateReader): ConstraintEvaluator<*> = RingerModeIsEvaluator(state)
        @Provides @IntoSet fun networkTypeIs(state: DeviceStateReader): ConstraintEvaluator<*> = NetworkTypeIsEvaluator(state)
        @Provides @IntoSet fun daylight(clock: Clock): ConstraintEvaluator<*> = DaylightEvaluator(clock)
        @Provides @IntoSet fun daysOfMonth(clock: Clock): ConstraintEvaluator<*> = DaysOfMonthEvaluator(clock)
        @Provides @IntoSet fun months(clock: Clock): ConstraintEvaluator<*> = MonthsEvaluator(clock)
        @Provides @IntoSet fun macroEnabled(macros: MacroRepository): ConstraintEvaluator<*> = MacroEnabledEvaluator(macros)
        @Provides @IntoSet fun timeSinceLastRun(log: ExecutionLogRepository, clock: Clock): ConstraintEvaluator<*> =
            TimeSinceLastRunEvaluator(log, clock)
        // endregion

        // region Domain actions
        @Provides @IntoSet fun setVariable(globals: GlobalVariableRepository): ActionHandler<*> = SetVariableActionHandler(globals)
        @Provides @IntoSet fun logMessage(): ActionHandler<*> = LogMessageActionHandler()

        /** Lazy: the engine host depends on the engine, which depends on this registry. */
        @Provides @IntoSet fun setMacroEnabled(macros: MacroRepository, host: Lazy<EngineHost>): ActionHandler<*> =
            SetMacroEnabledActionHandler(macros) { host.get().startIfNeeded() }
        // endregion
    }
}
