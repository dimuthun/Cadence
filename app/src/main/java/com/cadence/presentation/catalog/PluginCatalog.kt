package com.cadence.presentation.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.RingerMode
import com.cadence.domain.action.ToggleState
import com.cadence.domain.action.VolumeStream
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.constraint.DeviceConstraint
import com.cadence.domain.device.DevicePosition
import com.cadence.domain.device.DeviceSwitch
import com.cadence.domain.trigger.DeviceTrigger
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import com.cadence.domain.trigger.ConnectionEvent
import com.cadence.domain.trigger.TriggerConfig
import kotlin.reflect.KClass

/** Groups in the editor's pick lists. */
enum class CatalogCategory(val label: String) {
    TIME("Date & time"),
    LOCATION("Location"),
    POWER("Battery & power"),
    CONNECTIVITY("Connectivity"),
    DEVICE("Device"),
    SENSORS("Sensors"),
    APPS("Apps & notifications"),
    SOUND("Sound"),
    DISPLAY("Display"),
    WEB("Web"),
    VARIABLES("Variables"),
    FLOW("Flow"),
    MACROS("Macros"),
}

/**
 * How one config type appears in the editor.
 *
 * @property create the default config inserted when the user picks this entry.
 */
data class CatalogEntry<out C : Any>(
    val type: KClass<out C>,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: CatalogCategory,
    val create: () -> C,
)

/** Pickable triggers. [TriggerConfig.Manual] is internal and not listed. */
val TriggerCatalog: List<CatalogEntry<TriggerConfig>> = listOf(
    CatalogEntry(TriggerConfig.TimeOfDay::class, "Time of day", "At a set time, optionally only on chosen days", Icons.Filled.Schedule, CatalogCategory.TIME) {
        TriggerConfig.TimeOfDay(hour = 8, minute = 0)
    },
    CatalogEntry(TriggerConfig.DateTime::class, "Date, time or interval", "Once at a date and time, or repeating every few minutes, hours or days", Icons.Filled.Event, CatalogCategory.TIME) {
        // Default: repeat every hour, starting at the next full hour.
        val nextHour = (System.currentTimeMillis() / 3_600_000L + 1) * 3_600_000L
        TriggerConfig.DateTime(startAtEpochMillis = nextHour, repeatIntervalMinutes = 60)
    },
    CatalogEntry(TriggerConfig.LocationArea::class, "Arrive at or leave a place", "When the phone enters or leaves an area around a location", Icons.Filled.Place, CatalogCategory.LOCATION) {
        TriggerConfig.LocationArea(latitude = 0.0, longitude = 0.0, radiusMeters = 200, event = TriggerConfig.LocationArea.Event.ENTER)
    },
    CatalogEntry(TriggerConfig.LocationMoved::class, "Distance moved", "Each time the phone has moved a set distance", Icons.Filled.NearMe, CatalogCategory.LOCATION) {
        TriggerConfig.LocationMoved(distanceMeters = 1_000)
    },
    CatalogEntry(TriggerConfig.BatteryLevel::class, "Battery level", "When the battery falls below or rises above a level", Icons.Filled.BatteryAlert, CatalogCategory.POWER) {
        TriggerConfig.BatteryLevel(thresholdPercent = 20, direction = TriggerConfig.BatteryLevel.Direction.FALLS_BELOW)
    },
    CatalogEntry(TriggerConfig.PowerConnection::class, "Charger", "When a charger is connected or disconnected", Icons.Filled.Power, CatalogCategory.POWER) {
        TriggerConfig.PowerConnection(ConnectionEvent.CONNECTED)
    },
    CatalogEntry(TriggerConfig.WifiConnection::class, "Wi-Fi", "When the phone connects to or leaves any Wi-Fi network", Icons.Filled.Wifi, CatalogCategory.CONNECTIVITY) {
        TriggerConfig.WifiConnection(ConnectionEvent.CONNECTED)
    },
    CatalogEntry(TriggerConfig.BluetoothConnection::class, "Bluetooth device", "When a Bluetooth device connects or disconnects", Icons.Filled.Bluetooth, CatalogCategory.CONNECTIVITY) {
        TriggerConfig.BluetoothConnection(ConnectionEvent.CONNECTED)
    },
    CatalogEntry(TriggerConfig.AppLifecycle::class, "App opened or closed", "When a chosen app comes to the front or leaves it", Icons.Filled.Apps, CatalogCategory.APPS) {
        TriggerConfig.AppLifecycle(packageName = "", event = TriggerConfig.AppLifecycle.Event.LAUNCHED)
    },
    CatalogEntry(TriggerConfig.NotificationPosted::class, "Notification received", "When a chosen app posts a notification", Icons.Filled.Notifications, CatalogCategory.APPS) {
        TriggerConfig.NotificationPosted(packageName = "")
    },
    CatalogEntry(TriggerConfig.ScreenState::class, "Screen on or off", "When the screen turns on or off", Icons.Filled.Smartphone, CatalogCategory.DEVICE) {
        TriggerConfig.ScreenState(TriggerConfig.ScreenState.Event.ON)
    },
    CatalogEntry(TriggerConfig.BootCompleted::class, "Device started", "Once the phone finishes booting", Icons.Filled.RestartAlt, CatalogCategory.DEVICE) {
        TriggerConfig.BootCompleted
    },
    CatalogEntry(DeviceTrigger.SwitchChanged::class, "Device state changes", "Airplane mode, battery saver, dark theme, headphones, Do Not Disturb and more turn on or off", Icons.Filled.Tune, CatalogCategory.DEVICE) {
        DeviceTrigger.SwitchChanged(DeviceSwitch.HEADPHONES, turnedOn = true)
    },
    CatalogEntry(DeviceTrigger.RingerModeChanged::class, "Ringer mode changes", "When the phone switches to sound, vibrate or silent", Icons.Filled.NotificationsOff, CatalogCategory.SOUND) {
        DeviceTrigger.RingerModeChanged(RingerMode.SILENT)
    },
    CatalogEntry(DeviceTrigger.MusicTrackChanged::class, "New track playing", "When a music or podcast app starts a new track", Icons.Filled.MusicNote, CatalogCategory.SOUND) {
        DeviceTrigger.MusicTrackChanged()
    },
    CatalogEntry(DeviceTrigger.Shake::class, "Shake", "When you shake the phone", Icons.Filled.Vibration, CatalogCategory.SENSORS) {
        DeviceTrigger.Shake()
    },
    CatalogEntry(DeviceTrigger.PositionChanged::class, "Face up, face down or turned", "When the phone is laid face up or down, held upright or turned sideways", Icons.Filled.ScreenRotation, CatalogCategory.SENSORS) {
        DeviceTrigger.PositionChanged(DevicePosition.FACE_DOWN)
    },
    CatalogEntry(DeviceTrigger.Proximity::class, "Proximity sensor", "When something covers the top of the screen (a hand, a pocket) or moves away", Icons.Filled.Sensors, CatalogCategory.SENSORS) {
        DeviceTrigger.Proximity(near = true)
    },
    CatalogEntry(DeviceTrigger.LightLevel::class, "Light level", "When the surroundings get darker or brighter than a level", Icons.Filled.LightMode, CatalogCategory.SENSORS) {
        DeviceTrigger.LightLevel(thresholdLux = 10, direction = TriggerConfig.BatteryLevel.Direction.FALLS_BELOW)
    },
    CatalogEntry(DeviceTrigger.BatteryTemperature::class, "Battery temperature", "When the battery gets hotter or cooler than a temperature", Icons.Filled.Thermostat, CatalogCategory.POWER) {
        DeviceTrigger.BatteryTemperature(thresholdCelsius = 40, direction = TriggerConfig.BatteryLevel.Direction.RISES_ABOVE)
    },
    CatalogEntry(DeviceTrigger.AppPackageChanged::class, "App installed, removed or updated", "When any app, or a chosen one, is installed, removed or updated", Icons.Filled.InstallMobile, CatalogCategory.APPS) {
        DeviceTrigger.AppPackageChanged(DeviceTrigger.AppPackageChanged.Event.INSTALLED)
    },
    CatalogEntry(DeviceTrigger.NetworkTypeChanged::class, "Network type changes", "When the phone switches between Wi-Fi, mobile data and offline", Icons.Filled.NetworkCheck, CatalogCategory.CONNECTIVITY) {
        DeviceTrigger.NetworkTypeChanged()
    },
    CatalogEntry(DeviceTrigger.IpAddressChanged::class, "IP address changes", "When the phone gets a new local IP address", Icons.Filled.Lan, CatalogCategory.CONNECTIVITY) {
        DeviceTrigger.IpAddressChanged
    },
    CatalogEntry(DeviceTrigger.Sun::class, "Sunrise or sunset", "At sunrise or sunset at a place, or a set time before or after", Icons.Filled.WbTwilight, CatalogCategory.TIME) {
        DeviceTrigger.Sun(DeviceTrigger.Sun.Event.SUNSET)
    },
    CatalogEntry(DeviceTrigger.DayOfMonth::class, "Day of the month", "At a set time on chosen days of the month, like the 1st or the last day", Icons.Filled.CalendarMonth, CatalogCategory.TIME) {
        DeviceTrigger.DayOfMonth(days = setOf(1))
    },
)

/** Pickable actions. */
val ActionCatalog: List<CatalogEntry<ActionConfig>> = listOf(
    CatalogEntry(ActionConfig.ShowNotification::class, "Show notification", "Post a notification with your text", Icons.Filled.NotificationsActive, CatalogCategory.APPS) {
        ActionConfig.ShowNotification(title = "", text = "")
    },
    CatalogEntry(ActionConfig.LaunchApp::class, "Launch app", "Open an installed app", Icons.Filled.RocketLaunch, CatalogCategory.APPS) {
        ActionConfig.LaunchApp(packageName = "")
    },
    CatalogEntry(ActionConfig.OpenUrl::class, "Open URL", "Open a web link", Icons.Filled.Language, CatalogCategory.APPS) {
        ActionConfig.OpenUrl(url = "https://")
    },
    CatalogEntry(ActionConfig.SetWifi::class, "Wi-Fi on/off", "Turn Wi-Fi on or off (opens the Wi-Fi panel on Android 10+)", Icons.Filled.Wifi, CatalogCategory.CONNECTIVITY) {
        ActionConfig.SetWifi(ToggleState.OFF)
    },
    CatalogEntry(ActionConfig.SetBluetooth::class, "Bluetooth on/off", "Turn Bluetooth on or off (opens settings on Android 13+)", Icons.Filled.Bluetooth, CatalogCategory.CONNECTIVITY) {
        ActionConfig.SetBluetooth(ToggleState.OFF)
    },
    CatalogEntry(ActionConfig.SetRingerMode::class, "Ringer mode", "Switch between sound, vibrate and silent", Icons.Filled.DoNotDisturbOn, CatalogCategory.SOUND) {
        ActionConfig.SetRingerMode(RingerMode.VIBRATE)
    },
    CatalogEntry(ActionConfig.SetVolume::class, "Volume", "Set the volume of a sound stream", Icons.AutoMirrored.Filled.VolumeUp, CatalogCategory.SOUND) {
        ActionConfig.SetVolume(VolumeStream.MEDIA, levelPercent = 50)
    },
    CatalogEntry(ActionConfig.SpeakText::class, "Speak text", "Read text aloud with text-to-speech", Icons.Filled.RecordVoiceOver, CatalogCategory.SOUND) {
        ActionConfig.SpeakText(text = "")
    },
    CatalogEntry(ActionConfig.Vibrate::class, "Vibrate", "Vibrate the phone", Icons.Filled.Vibration, CatalogCategory.DEVICE) {
        ActionConfig.Vibrate(durationMillis = 500)
    },
    CatalogEntry(ActionConfig.SetFlashlight::class, "Flashlight", "Turn the flashlight on, off, or toggle it", Icons.Filled.FlashlightOn, CatalogCategory.DEVICE) {
        ActionConfig.SetFlashlight(ToggleState.TOGGLE)
    },
    CatalogEntry(ActionConfig.SetBrightness::class, "Screen brightness", "Set brightness or switch to adaptive", Icons.Filled.BrightnessMedium, CatalogCategory.DISPLAY) {
        ActionConfig.SetBrightness(levelPercent = 50)
    },
    CatalogEntry(ActionConfig.HttpRequest::class, "HTTP request", "Call a web API or webhook, and use the response in later actions", Icons.Filled.Http, CatalogCategory.WEB) {
        ActionConfig.HttpRequest(url = "https://")
    },
    CatalogEntry(ActionConfig.RefreshLocation::class, "Refresh location", "Get a fresh location so later {location.*} values are current", Icons.Filled.MyLocation, CatalogCategory.LOCATION) {
        ActionConfig.RefreshLocation()
    },
    CatalogEntry(ActionConfig.SetVariable::class, "Set variable", "Store text or a number, add to a counter, or append to a value", Icons.Filled.DataObject, CatalogCategory.VARIABLES) {
        ActionConfig.SetVariable(name = "", value = "")
    },
    CatalogEntry(ActionConfig.Wait::class, "Wait", "Pause before the next action", Icons.Filled.Timer, CatalogCategory.FLOW) {
        ActionConfig.Wait(durationMillis = 5_000)
    },
    CatalogEntry(ActionConfig.IfElse::class, "If / else", "Run different actions depending on conditions", Icons.AutoMirrored.Filled.CallSplit, CatalogCategory.FLOW) {
        ActionConfig.IfElse(branches = listOf(com.cadence.domain.action.ConditionalBlock()))
    },
    CatalogEntry(ActionConfig.Repeat::class, "Repeat", "Repeat actions a number of times, or while conditions hold", Icons.Filled.Repeat, CatalogCategory.FLOW) {
        ActionConfig.Repeat()
    },
    CatalogEntry(ActionConfig.ExitLoop::class, "Exit loop", "Leave the repeat this action is in", Icons.AutoMirrored.Filled.ExitToApp, CatalogCategory.FLOW) {
        ActionConfig.ExitLoop
    },
    CatalogEntry(ActionConfig.NextIteration::class, "Next iteration", "Skip the rest of this repeat iteration", Icons.Filled.SkipNext, CatalogCategory.FLOW) {
        ActionConfig.NextIteration
    },
    CatalogEntry(ActionConfig.StopRun::class, "Stop this run", "Don't run any more actions this time", Icons.Filled.Stop, CatalogCategory.FLOW) {
        ActionConfig.StopRun
    },
    CatalogEntry(ActionConfig.WaitForTrigger::class, "Wait for trigger", "Pause until something happens, with a time limit", Icons.Filled.HourglassTop, CatalogCategory.FLOW) {
        ActionConfig.WaitForTrigger(TriggerConfig.ScreenState(TriggerConfig.ScreenState.Event.ON))
    },
    CatalogEntry(ActionConfig.RunMacro::class, "Run another macro", "Run a macro's actions and wait for it to finish", Icons.Filled.PlayCircle, CatalogCategory.MACROS) {
        ActionConfig.RunMacro(macroId = 0)
    },
    CatalogEntry(ActionConfig.SetMacroEnabled::class, "Enable or disable a macro", "Turn a macro (or this one) on, off, or toggle it", Icons.Filled.ToggleOn, CatalogCategory.MACROS) {
        ActionConfig.SetMacroEnabled()
    },
    CatalogEntry(ActionConfig.LogMessage::class, "Write to log", "Add a message, with variables, to this run's log entry", Icons.AutoMirrored.Filled.Notes, CatalogCategory.MACROS) {
        ActionConfig.LogMessage(text = "")
    },
    CatalogEntry(ActionConfig.ShowToast::class, "Show toast", "Show a short message at the bottom of the screen", Icons.Filled.ChatBubbleOutline, CatalogCategory.APPS) {
        ActionConfig.ShowToast(text = "")
    },
)

/** Pickable constraints. */
val ConstraintCatalog: List<CatalogEntry<ConstraintConfig>> = listOf(
    CatalogEntry(ConstraintConfig.TimeRange::class, "Time range", "Only between two times of day", Icons.Filled.Schedule, CatalogCategory.TIME) {
        ConstraintConfig.TimeRange(startMinuteOfDay = 9 * 60, endMinuteOfDay = 17 * 60)
    },
    CatalogEntry(ConstraintConfig.DaysOfWeek::class, "Days of week", "Only on chosen days", Icons.Filled.DateRange, CatalogCategory.TIME) {
        ConstraintConfig.DaysOfWeek(com.cadence.domain.model.Weekday.entries.take(5).toSet())
    },
    CatalogEntry(ConstraintConfig.BatteryLevelRange::class, "Battery level range", "Only when the battery is within a range", Icons.Filled.BatteryStd, CatalogCategory.POWER) {
        ConstraintConfig.BatteryLevelRange(minPercent = 20, maxPercent = 100)
    },
    CatalogEntry(ConstraintConfig.ChargingState::class, "Charging", "Only while charging, or only while not charging", Icons.Filled.BatteryChargingFull, CatalogCategory.POWER) {
        ConstraintConfig.ChargingState(charging = true)
    },
    CatalogEntry(ConstraintConfig.WifiNetwork::class, "Wi-Fi connected", "Only while connected to Wi-Fi, or only while not", Icons.Filled.Wifi, CatalogCategory.CONNECTIVITY) {
        ConstraintConfig.WifiNetwork(connected = true)
    },
    CatalogEntry(ConstraintConfig.VariableCompare::class, "Variable value", "Only when a variable (yours or built-in, like battery level) matches", Icons.Filled.DataObject, CatalogCategory.VARIABLES) {
        ConstraintConfig.VariableCompare(name = "")
    },
    CatalogEntry(ConstraintConfig.Group::class, "Any / all / none of", "Combine several conditions with OR, AND, or NOT", Icons.AutoMirrored.Filled.AltRoute, CatalogCategory.FLOW) {
        ConstraintConfig.Group()
    },
    CatalogEntry(ConstraintConfig.TimeSinceLastRun::class, "Cooldown", "Only if the macro hasn't run in the last few minutes", Icons.Filled.HourglassTop, CatalogCategory.MACROS) {
        ConstraintConfig.TimeSinceLastRun()
    },
    CatalogEntry(ConstraintConfig.MacroEnabled::class, "Macro enabled", "Only while another macro is enabled or disabled", Icons.Filled.ToggleOn, CatalogCategory.MACROS) {
        ConstraintConfig.MacroEnabled(macroId = 0)
    },
    CatalogEntry(ConstraintConfig.MacroRunning::class, "Macro running", "Only while another macro is (or isn't) running", Icons.Filled.PlayCircle, CatalogCategory.MACROS) {
        ConstraintConfig.MacroRunning(macroId = 0)
    },
    CatalogEntry(DeviceConstraint.SwitchState::class, "Device state", "Only while headphones, airplane mode, dark theme, the screen and more are on or off", Icons.Filled.Tune, CatalogCategory.DEVICE) {
        DeviceConstraint.SwitchState(DeviceSwitch.HEADPHONES)
    },
    CatalogEntry(DeviceConstraint.RingerModeIs::class, "Ringer mode", "Only while the ringer is on sound, vibrate or silent", Icons.Filled.NotificationsOff, CatalogCategory.SOUND) {
        DeviceConstraint.RingerModeIs(setOf(RingerMode.NORMAL))
    },
    CatalogEntry(DeviceConstraint.NetworkTypeIs::class, "Network type", "Only while on Wi-Fi, mobile data, or offline", Icons.Filled.NetworkCheck, CatalogCategory.CONNECTIVITY) {
        DeviceConstraint.NetworkTypeIs(setOf(com.cadence.domain.device.NetworkType.WIFI))
    },
    CatalogEntry(DeviceConstraint.PositionIs::class, "Phone position", "Only while the phone is face up, face down, upright or sideways", Icons.Filled.ScreenRotation, CatalogCategory.SENSORS) {
        DeviceConstraint.PositionIs(DevicePosition.FACE_DOWN)
    },
    CatalogEntry(DeviceConstraint.AppInForeground::class, "App on screen", "Only while a chosen app is (or isn't) in front", Icons.Filled.Apps, CatalogCategory.APPS) {
        DeviceConstraint.AppInForeground(packageName = "")
    },
    CatalogEntry(DeviceConstraint.Daylight::class, "Daylight or dark", "Only between sunrise and sunset, or only after dark", Icons.Filled.WbSunny, CatalogCategory.TIME) {
        DeviceConstraint.Daylight()
    },
    CatalogEntry(DeviceConstraint.DaysOfMonth::class, "Days of the month", "Only on chosen days of the month", Icons.Filled.CalendarMonth, CatalogCategory.TIME) {
        DeviceConstraint.DaysOfMonth(days = setOf(1))
    },
    CatalogEntry(DeviceConstraint.Months::class, "Months", "Only in chosen months", Icons.Filled.DateRange, CatalogCategory.TIME) {
        DeviceConstraint.Months(setOf(java.time.LocalDate.now().monthValue))
    },
)

/** Catalog lookups by config instance. */
object Catalog {
    fun entryFor(config: TriggerConfig): CatalogEntry<TriggerConfig>? = TriggerCatalog.firstOrNull { it.type == config::class }
    fun entryFor(config: ActionConfig): CatalogEntry<ActionConfig>? = ActionCatalog.firstOrNull { it.type == config::class }
    fun entryFor(config: ConstraintConfig): CatalogEntry<ConstraintConfig>? = ConstraintCatalog.firstOrNull { it.type == config::class }
}
